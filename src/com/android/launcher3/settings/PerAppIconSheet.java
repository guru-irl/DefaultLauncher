/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DefaultLauncher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DefaultLauncher. If not, see <https://www.gnu.org/licenses/>.
 */
package com.android.launcher3.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.widget.NestedScrollView;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.android.launcher3.R;
import com.android.launcher3.anim.M3Durations;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-page bottom sheet for per-app icon customization.
 * <p>
 * Page 1: Icon pack selection (Follow global, System default, installed packs).
 * Page 2: Searchable icon grid with "Suggested" section showing the matched icon first.
 * <p>
 * The selected pack card animates to fill the sheet, then cross-fades to the icon grid.
 * A back button returns to the pack list.
 */
public class PerAppIconSheet {

    public interface Callback {
        void onFollowGlobal();
        void onSystemDefault();
        void onIconSelected(String packPackage, String drawableName);
    }

    private static final long SLIDE_DURATION = M3Durations.MEDIUM_4; // 400ms
    private static final long SEARCH_DEBOUNCE_MS = M3Durations.MEDIUM_2; // 300ms

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static void show(PreferenceFragmentCompat fragment,
            ComponentName appCn, IconPackManager mgr, Callback callback) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);

        BottomSheetDialog sheet = new BottomSheetDialog(ctx);

        // Root is a FrameLayout for page layering + overlay transitions.
        // MATCH_PARENT height so the bottom sheet's design_bottom_sheet measures
        // to screen height; the behavior controls how much is visible via peekHeight.
        FrameLayout root = new FrameLayout(ctx);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Track whether we're on the icon picker page (for back-key handling)
        final boolean[] onIconPage = {false};
        // Hold a reference to the current icon page for back navigation
        final View[] iconPageRef = {null};

        // ---- Page 1: Pack selection ----
        LinearLayout packPage = buildPackPage(ctx, appCn, mgr, sheet, root,
                colorOnSurface, colorSurfaceVar, callback, onIconPage, iconPageRef);
        root.addView(packPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Intercept back (gesture + hardware key) to navigate back
        // from icon picker to pack list instead of dismissing.
        sheet.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (onIconPage[0] && iconPageRef[0] != null) {
                    transitionBackToPackPage(root, iconPageRef[0],
                            packPage, onIconPage);
                } else {
                    // On pack list page — dismiss the sheet
                    setEnabled(false);
                    sheet.getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        sheet.setContentView(root);

        // Force the internal sheet container to full screen so the behavior
        // allows expanding even when content is short (e.g. only 2 icon packs).
        View sheetView = sheet.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheetView != null) {
            sheetView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        BottomSheetBehavior<?> behavior = sheet.getBehavior();
        behavior.setSkipCollapsed(false);
        sheet.show();

        // After layout, set peekHeight to pack page's natural content height.
        // At this point children are measured at natural sizes (scroll view has
        // no weight yet), so summing them gives the "rest" height for the sheet.
        root.post(() -> {
            int naturalHeight = packPage.getPaddingTop() + packPage.getPaddingBottom();
            for (int i = 0; i < packPage.getChildCount(); i++) {
                naturalHeight += packPage.getChildAt(i).getHeight();
            }
            behavior.setPeekHeight(naturalHeight);

            // Switch scroll view to weight=1 so it scrolls when constrained
            View lastChild = packPage.getChildAt(packPage.getChildCount() - 1);
            if (lastChild instanceof NestedScrollView) {
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) lastChild.getLayoutParams();
                lp.height = 0;
                lp.weight = 1f;
                lastChild.setLayoutParams(lp);
            }
        });
    }

    // ---- Page 1: Pack list ----

    private static LinearLayout buildPackPage(Context ctx,
            ComponentName appCn, IconPackManager mgr,
            BottomSheetDialog sheet, FrameLayout root,
            int colorOnSurface, int colorSurfaceVar,
            Callback callback, boolean[] onIconPage, View[] iconPageRef) {

        Resources res = ctx.getResources();
        Map<String, IconPack> packs = mgr.getInstalledPacks();
        PackageManager pm = ctx.getPackageManager();
        int cornerPx = res.getDimensionPixelSize(R.dimen.settings_card_corner_radius);
        int marginH = res.getDimensionPixelSize(R.dimen.settings_card_margin_horizontal);
        int marginV = res.getDimensionPixelSize(R.dimen.settings_card_margin_vertical);
        int cardPad = res.getDimensionPixelSize(R.dimen.settings_card_padding);

        LinearLayout packPage = new LinearLayout(ctx);
        packPage.setOrientation(LinearLayout.VERTICAL);
        packPage.setPadding(0, 0, 0,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal));

        IconSettingsHelper.addSheetHandle(packPage, ctx, res);

        // Title
        TextView title = new TextView(ctx);
        title.setText(R.string.choose_icon_pack);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                res.getDimension(R.dimen.settings_sheet_title_text_size));
        title.setTextColor(colorOnSurface);
        int titlePadH = res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal);
        int titlePadV = res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical);
        title.setPadding(titlePadH, titlePadV, titlePadH, titlePadV);
        packPage.addView(title);

        LinearLayout items = new LinearLayout(ctx);
        items.setOrientation(LinearLayout.VERTICAL);

        // "Follow global setting"
        items.addView(createPackCard(ctx, cornerPx, marginH, marginV, cardPad,
                colorSurfaceVar, colorOnSurface,
                ctx.getString(R.string.customize_follow_global), null, v -> {
                    sheet.dismiss();
                    callback.onFollowGlobal();
                }));

        // "System default"
        items.addView(createPackCard(ctx, cornerPx, marginH, marginV, cardPad,
                colorSurfaceVar, colorOnSurface,
                ctx.getString(R.string.customize_system_default), null, v -> {
                    sheet.dismiss();
                    callback.onSystemDefault();
                }));

        // Each installed pack
        int previewSize = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
        for (Map.Entry<String, IconPack> entry : packs.entrySet()) {
            String pkg = entry.getKey();
            IconPack pack = entry.getValue();

            // Click set below (needs reference to the card itself)
            LinearLayout card = createPackCard(ctx, cornerPx, marginH, marginV,
                    cardPad, colorSurfaceVar, colorOnSurface,
                    pack.label.toString(), pack.getPackIcon(pm), null);

            // Add app preview icon from this pack (async)
            ImageView preview = new ImageView(ctx);
            LinearLayout.LayoutParams previewLp =
                    new LinearLayout.LayoutParams(previewSize, previewSize);
            previewLp.setMarginStart(
                    res.getDimensionPixelSize(R.dimen.settings_item_spacing));
            preview.setLayoutParams(previewLp);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout header = (LinearLayout) card.getChildAt(0);
            header.addView(preview);

            Executors.MODEL_EXECUTOR.execute(() -> {
                pack.ensureParsed(pm);
                Drawable appIcon = pack.getIconForComponent(appCn, pm);
                if (appIcon == null && pack.hasFallbackMask()) {
                    try {
                        Drawable original = pm.getActivityIcon(appCn);
                        appIcon = pack.applyFallbackMask(original, previewSize);
                    } catch (PackageManager.NameNotFoundException ignored) { }
                }
                final Drawable finalIcon = appIcon;
                if (finalIcon != null) {
                    sMainHandler.post(() -> {
                        if (sheet.isShowing()) {
                            preview.setImageDrawable(finalIcon);
                        }
                    });
                }
            });

            card.setOnClickListener(v ->
                    transitionToIconPicker(ctx, root, packPage,
                            pkg, pack, appCn, mgr, sheet, callback,
                            colorOnSurface, colorSurfaceVar, onIconPage, iconPageRef));

            items.addView(card);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setClipToPadding(false);
        scroll.addView(items);
        packPage.addView(scroll);

        return packPage;
    }

    // ---- Transition: pack card expands → icon picker ----

    private static void transitionToIconPicker(Context ctx,
            FrameLayout root, LinearLayout packPage,
            String packPackage, IconPack pack, ComponentName appCn,
            IconPackManager mgr, BottomSheetDialog sheet,
            Callback callback, int colorOnSurface, int colorSurfaceVar,
            boolean[] onIconPage, View[] iconPageRef) {

        // Build icon picker page (starts off-screen right)
        LinearLayout iconPage = buildIconPickerPage(ctx, packPackage, pack,
                appCn, mgr, sheet, root, packPage, callback,
                colorOnSurface, colorSurfaceVar, onIconPage);

        iconPage.setAlpha(0f);
        iconPage.setTranslationX(root.getWidth() * 0.3f);

        root.addView(iconPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        iconPageRef[0] = iconPage;

        Interpolator interp = Interpolators.EMPHASIZED;

        // Slide pack page left + fade out
        packPage.animate()
                .translationX(-root.getWidth() * 0.3f)
                .alpha(0f)
                .setDuration(SLIDE_DURATION)
                .setInterpolator(interp)
                .withEndAction(() -> packPage.setVisibility(View.GONE))
                .start();

        // Slide icon page in from right + fade in
        iconPage.setVisibility(View.VISIBLE);
        iconPage.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(SLIDE_DURATION)
                .setInterpolator(interp)
                .withEndAction(() -> onIconPage[0] = true)
                .start();
    }

    // ---- Page 2: Icon picker grid ----

    private static LinearLayout buildIconPickerPage(Context ctx,
            String packPackage, IconPack pack, ComponentName appCn,
            IconPackManager mgr, BottomSheetDialog sheet,
            FrameLayout root, LinearLayout packPage,
            Callback callback, int colorOnSurface, int colorSurfaceVar,
            boolean[] onIconPage) {

        Resources res = ctx.getResources();
        int hintColor = ctx.getColor(R.color.materialColorOnSurfaceVariant);

        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);

        IconSettingsHelper.addSheetHandle(page, ctx, res);

        // Header: back button + title
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
                res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical),
                res.getDimensionPixelSize(R.dimen.settings_item_spacing),
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                res.getDimensionPixelSize(R.dimen.settings_item_spacing));

        // Back button
        ImageView backBtn = new ImageView(ctx);
        backBtn.setImageResource(R.drawable.ic_arrow_back);
        backBtn.setColorFilter(colorOnSurface);
        int backSize = res.getDimensionPixelSize(R.dimen.settings_back_button_size);
        int backPad = res.getDimensionPixelSize(R.dimen.settings_icon_padding);
        LinearLayout.LayoutParams backLp =
                new LinearLayout.LayoutParams(backSize, backSize);
        backLp.setMarginEnd(
                res.getDimensionPixelSize(R.dimen.settings_item_spacing));
        backBtn.setLayoutParams(backLp);
        backBtn.setPadding(backPad, backPad, backPad, backPad);

        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        backBtn.setBackgroundResource(ripple.resourceId);

        backBtn.setOnClickListener(v ->
                transitionBackToPackPage(root, page, packPage, onIconPage));
        header.addView(backBtn);

        // Title
        TextView title = new TextView(ctx);
        title.setText(R.string.icon_picker_choose_icon);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                res.getDimension(R.dimen.settings_sheet_title_text_size));
        title.setTextColor(colorOnSurface);
        header.addView(title);

        page.addView(header);

        // Search bar
        EditText searchInput = new EditText(ctx);
        searchInput.setHint(R.string.icon_picker_search_hint);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setMaxLines(1);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchInput.setTextColor(colorOnSurface);
        searchInput.setHintTextColor(hintColor);
        searchInput.setBackgroundResource(R.drawable.bg_widgets_searchbox);
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        int searchPadH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        searchInput.setPadding(searchPadH, 0, searchPadH, 0);
        searchInput.setCompoundDrawablePadding(
                res.getDimensionPixelSize(R.dimen.settings_search_padding));
        searchInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        Drawable searchIcon = ctx.getDrawable(R.drawable.ic_allapps_search);
        if (searchIcon != null) {
            searchIcon.setTintList(ColorStateList.valueOf(hintColor));
            int iconSize = res.getDimensionPixelSize(R.dimen.settings_search_icon_size);
            searchIcon.setBounds(0, 0, iconSize, iconSize);
            searchInput.setCompoundDrawablesRelative(searchIcon, null, null, null);
        }

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                res.getDimensionPixelSize(R.dimen.settings_row_min_height));
        int searchMarginH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        int searchMarginV = res.getDimensionPixelSize(R.dimen.settings_item_spacing);
        searchLp.setMargins(searchMarginH, searchMarginV, searchMarginH, searchMarginV);
        searchInput.setLayoutParams(searchLp);
        page.addView(searchInput);

        // RecyclerView grid
        RecyclerView rv = new RecyclerView(ctx);
        rv.setClipToPadding(false);
        int rvPadH = res.getDimensionPixelSize(R.dimen.settings_item_spacing);
        rv.setPadding(rvPadH, 0, rvPadH,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical));
        rv.setNestedScrollingEnabled(true);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);
        page.addView(rv);

        // Set up grid after layout to calculate span count
        rv.post(() -> {
            int cellSizePx = res.getDimensionPixelSize(R.dimen.settings_icon_cell_size);

            CategoryGridAdapter.ItemBinder<IconPack.IconEntry> binder =
                    new CategoryGridAdapter.ItemBinder<>() {
                @Override
                public void bind(ImageView view, IconPack.IconEntry entry, int position) {
                    view.setTag(entry.drawableName);
                    String drawableName = entry.drawableName;
                    Executors.MODEL_EXECUTOR.execute(() -> {
                        IconPack p = mgr.getPack(packPackage);
                        if (p == null) return;
                        Drawable d = p.getDrawableForEntry(
                                drawableName, ctx.getPackageManager());
                        sMainHandler.post(() -> {
                            if (drawableName.equals(view.getTag())) {
                                view.setImageDrawable(d);
                            }
                        });
                    });
                }

                @Override
                public void onItemClick(IconPack.IconEntry entry) {
                    sheet.dismiss();
                    callback.onIconSelected(packPackage, entry.drawableName);
                }

                @Override
                public boolean matchesQuery(IconPack.IconEntry entry, String query) {
                    return entry.label.toLowerCase().contains(query)
                            || entry.drawableName.toLowerCase().contains(query);
                }

                @Override
                public String getContentDescription(IconPack.IconEntry entry) {
                    return entry.label;
                }
            };

            CategoryGridAdapter<IconPack.IconEntry> adapter =
                    new CategoryGridAdapter<>(new ArrayList<>(), binder);
            rv.setLayoutManager(CategoryGridAdapter.createGridLayoutManager(
                    ctx, rv, cellSizePx, adapter));
            rv.setAdapter(adapter);

            // Load icons with "Suggested" section at top
            loadIconsWithSuggested(ctx, packPackage, pack, appCn, mgr, adapter);

            // Search with debounce
            final Runnable[] pendingSearch = {null};
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count,
                        int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before,
                        int count) {
                    if (pendingSearch[0] != null) {
                        sMainHandler.removeCallbacks(pendingSearch[0]);
                    }
                    pendingSearch[0] = () -> adapter.filter(s.toString());
                    sMainHandler.postDelayed(pendingSearch[0], SEARCH_DEBOUNCE_MS);
                }

                @Override
                public void afterTextChanged(Editable s) { }
            });
        });

        return page;
    }

    // ---- Icon loading with Suggested section ----

    private static void loadIconsWithSuggested(Context ctx, String packPackage,
            IconPack pack, ComponentName appCn, IconPackManager mgr,
            CategoryGridAdapter<IconPack.IconEntry> adapter) {

        Executors.MODEL_EXECUTOR.execute(() -> {
            PackageManager pm = ctx.getPackageManager();
            pack.ensureParsed(pm);

            // Find the primary matched drawable
            String matchedDrawable = pack.getDrawableNameForComponent(appCn, pm);

            // Get a simple name for variant matching (e.g., "chrome" from package)
            String appSimpleName = getSimpleName(appCn);

            List<IconPack.IconCategory> categories = pack.getAllIcons(pm);

            List<CategoryGridAdapter.ListItem<IconPack.IconEntry>> items = new ArrayList<>();

            // Build suggested section: matched icon + variants (name-similar drawables)
            List<IconPack.IconEntry> suggestedEntries = new ArrayList<>();
            Set<String> suggestedNames = new HashSet<>();

            // Primary matched drawable goes first
            if (matchedDrawable != null) {
                suggestedEntries.add(new IconPack.IconEntry(
                        matchedDrawable, humanize(matchedDrawable)));
                suggestedNames.add(matchedDrawable);
            }

            // Find variants: drawables whose name contains the app's simple name
            if (appSimpleName != null && appSimpleName.length() >= 3) {
                String lowerName = appSimpleName.toLowerCase();
                for (IconPack.IconCategory cat : categories) {
                    for (IconPack.IconEntry entry : cat.items) {
                        if (!suggestedNames.contains(entry.drawableName)
                                && entry.drawableName.toLowerCase().contains(lowerName)) {
                            suggestedEntries.add(entry);
                            suggestedNames.add(entry.drawableName);
                        }
                    }
                }
            }

            if (!suggestedEntries.isEmpty()) {
                items.add(new CategoryGridAdapter.ListItem<>(
                        ctx.getString(R.string.icon_picker_suggested)));
                for (IconPack.IconEntry entry : suggestedEntries) {
                    items.add(new CategoryGridAdapter.ListItem<>(entry));
                }
            }

            // Add remaining categories, excluding already-suggested items
            for (IconPack.IconCategory cat : categories) {
                List<CategoryGridAdapter.ListItem<IconPack.IconEntry>> catItems =
                        new ArrayList<>();
                for (IconPack.IconEntry entry : cat.items) {
                    if (!suggestedNames.contains(entry.drawableName)) {
                        catItems.add(new CategoryGridAdapter.ListItem<>(entry));
                    }
                }
                if (!catItems.isEmpty()) {
                    items.add(new CategoryGridAdapter.ListItem<>(cat.title));
                    catItems.forEach(items::add);
                }
            }

            sMainHandler.post(() -> adapter.setItems(items));
        });
    }

    private static String getSimpleName(ComponentName cn) {
        String pkg = cn.getPackageName();
        String[] parts = pkg.split("\\.");
        if (parts.length > 0) {
            String last = parts[parts.length - 1];
            if (!"app".equals(last) && !"apps".equals(last)
                    && !"android".equals(last) && !"com".equals(last)) {
                return last;
            }
            if (parts.length > 1) {
                return parts[parts.length - 2];
            }
        }
        return null;
    }

    private static String humanize(String drawableName) {
        return drawableName.replace('_', ' ').replace('-', ' ').trim();
    }

    // ---- Back navigation: icon picker → pack list ----

    private static void transitionBackToPackPage(FrameLayout root,
            View iconPage, LinearLayout packPage,
            boolean[] onIconPage) {
        onIconPage[0] = false;

        Interpolator interp = Interpolators.EMPHASIZED;

        // Slide icon page out to the right
        iconPage.animate()
                .translationX(root.getWidth() * 0.3f)
                .alpha(0f)
                .setDuration(SLIDE_DURATION)
                .setInterpolator(interp)
                .withEndAction(() -> root.removeView(iconPage))
                .start();

        // Slide pack page back in from the left
        packPage.setVisibility(View.VISIBLE);
        packPage.setTranslationX(-root.getWidth() * 0.3f);
        packPage.setAlpha(0f);
        packPage.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(SLIDE_DURATION)
                .setInterpolator(interp)
                .start();
    }

    // ---- Pack card builder ----

    private static LinearLayout createPackCard(Context ctx,
            int cornerPx, int marginH, int marginV, int cardPad,
            int bgColor, int textColor,
            String label, Drawable packIcon,
            View.OnClickListener listener) {

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(cornerPx);
        bg.setColor(bgColor);
        card.setBackground(bg);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(marginH, marginV, marginH, marginV);
        card.setLayoutParams(cardLp);

        // Ripple foreground
        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true);
        card.setForeground(ctx.getDrawable(ripple.resourceId));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        if (packIcon != null) {
            Resources res = ctx.getResources();
            ImageView icon = new ImageView(ctx);
            int iconPx = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
            LinearLayout.LayoutParams iconLp =
                    new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.setMarginEnd(
                    res.getDimensionPixelSize(R.dimen.settings_icon_margin_end));
            icon.setLayoutParams(iconLp);
            icon.setImageDrawable(packIcon);
            header.addView(icon);
        }

        TextView name = new TextView(ctx);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(textColor);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(name);

        card.addView(header);
        if (listener != null) card.setOnClickListener(listener);
        return card;
    }

}
