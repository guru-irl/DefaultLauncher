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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracted shared dialog logic for icon pack, shape, and size settings.
 * Used by both HomeScreenFragment and AppDrawerFragment.
 */
public class IconSettingsHelper {

    private static final int PACK_ICON_SIZE_DP = 40;
    private static final int CARD_CORNER_DP = 16;
    private static final int CARD_MARGIN_H_DP = 16;
    private static final int CARD_MARGIN_V_DP = 6;
    private static final int CARD_PAD_DP = 16;
    private static final int PREVIEW_GAP_DP = 8;

    /**
     * Show the icon pack selection bottom sheet dialog.
     * Each pack is a card with the pack icon + name on top and real-size
     * preview icons below. The selected pack gets a tinted border + fill.
     */
    public static void showIconPackDialog(PreferenceFragmentCompat fragment,
            ConstantItem<String> prefItem, Preference pref, IconPackManager mgr) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        Map<String, IconPack> packs = mgr.getInstalledPacks();
        PackageManager pm = ctx.getPackageManager();

        List<CharSequence> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        List<IconPack> packObjects = new ArrayList<>();
        labels.add(ctx.getString(R.string.icon_pack_default));
        pkgs.add("");
        packObjects.add(null);
        for (Map.Entry<String, IconPack> entry : packs.entrySet()) {
            labels.add(entry.getValue().label);
            pkgs.add(entry.getKey());
            packObjects.add(entry.getValue());
        }

        String current = LauncherPrefs.get(ctx).get(prefItem);
        int selected = Math.max(0, pkgs.indexOf(current));

        float density = ctx.getResources().getDisplayMetrics().density;
        int cornerPx = (int) (CARD_CORNER_DP * density);
        int marginH = (int) (CARD_MARGIN_H_DP * density);
        int marginV = (int) (CARD_MARGIN_V_DP * density);
        int cardPad = (int) (CARD_PAD_DP * density);
        int previewGap = (int) (PREVIEW_GAP_DP * density);

        // Get real icon size from device profile (92% for preview)
        int previewIconSizePx = (int) (InvariantDeviceProfile.INSTANCE.get(ctx)
                .getDeviceProfile(ctx).iconSizePx * 0.92f);

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);
        int colorSelectedFill = ctx.getColor(R.color.materialColorPrimaryContainer);
        int colorSelectedBorder = ctx.getColor(R.color.materialColorPrimary);
        int colorOnPrimaryContainer = ctx.getColor(R.color.materialColorOnPrimaryContainer);

        BottomSheetDialog sheet = new BottomSheetDialog(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, (int) (24 * density));

        addSheetHandle(root, ctx, density);

        TextView titleView = new TextView(ctx);
        titleView.setText(R.string.icon_pack_title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTextColor(colorOnSurface);
        titleView.setPadding(
                (int) (24 * density), (int) (16 * density),
                (int) (24 * density), (int) (16 * density));
        root.addView(titleView);

        LinearLayout itemsContainer = new LinearLayout(ctx);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setPadding(0, 0, 0, (int) (8 * density));

        List<LinearLayout> previewContainers = new ArrayList<>();

        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            boolean isSelected = (i == selected);
            IconPack pack = packObjects.get(i);

            // Card wrapper
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClipToPadding(false);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(cornerPx);
            if (isSelected) {
                cardBg.setColor(colorSelectedFill);
                cardBg.setStroke((int) (2 * density), colorSelectedBorder);
            } else {
                cardBg.setColor(colorSurfaceVar);
            }
            card.setBackground(cardBg);
            card.setPadding(cardPad, cardPad, cardPad, cardPad);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(marginH, marginV, marginH, marginV);
            card.setLayoutParams(cardLp);

            // Ripple overlay
            TypedValue ripple = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true);
            // Use a FrameLayout to layer ripple over the card background
            FrameLayout rippleOverlay = new FrameLayout(ctx);
            rippleOverlay.setBackgroundResource(ripple.resourceId);
            rippleOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // --- Header row: [pack icon] [name + subtitle] ---
            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            ImageView packIcon = new ImageView(ctx);
            int packIconPx = (int) (PACK_ICON_SIZE_DP * density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    packIconPx, packIconPx);
            iconLp.setMarginEnd((int) (12 * density));
            packIcon.setLayoutParams(iconLp);
            if (pack != null) {
                Drawable appIcon = pack.getPackIcon(pm);
                if (appIcon != null) packIcon.setImageDrawable(appIcon);
            } else {
                try {
                    packIcon.setImageDrawable(pm.getApplicationIcon(ctx.getPackageName()));
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
            header.addView(packIcon);

            // Name + selected badge column
            LinearLayout labelCol = new LinearLayout(ctx);
            labelCol.setOrientation(LinearLayout.VERTICAL);
            labelCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView nameView = new TextView(ctx);
            nameView.setText(labels.get(i));
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            nameView.setTextColor(isSelected ? colorOnPrimaryContainer : colorOnSurface);
            if (isSelected) {
                nameView.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
            }
            labelCol.addView(nameView);

            if (isSelected) {
                TextView badge = new TextView(ctx);
                badge.setText("\u2713 Selected");
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                badge.setTextColor(colorOnPrimaryContainer);
                badge.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
                labelCol.addView(badge);
            }

            header.addView(labelCol);
            card.addView(header);

            // --- Preview icons row (populated async, below header) ---
            LinearLayout previewRow = new LinearLayout(ctx);
            previewRow.setOrientation(LinearLayout.HORIZONTAL);
            previewRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            previewLp.topMargin = (int) (12 * density);
            previewRow.setLayoutParams(previewLp);
            // Hidden initially — shown when preview icons load
            previewRow.setVisibility(View.GONE);
            card.addView(previewRow);
            previewContainers.add(previewRow);

            card.setOnClickListener(v -> {
                String pkg = pkgs.get(idx);
                LauncherPrefs.get(ctx).put(prefItem, pkg);
                mgr.invalidate();

                titleView.setText(R.string.icon_pack_applying);
                for (int j = 0; j < itemsContainer.getChildCount(); j++) {
                    itemsContainer.getChildAt(j).setEnabled(false);
                    itemsContainer.getChildAt(j).setAlpha(0.5f);
                }

                applyIconPack(ctx, prefItem, mgr, () -> {
                    updateIconPackSummary(ctx, pref, prefItem, mgr);
                    sheet.dismiss();
                });
            });

            itemsContainer.addView(card);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setClipToPadding(false);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        // Populate preview icons from cache (instant) or fall back to async loading
        List<Integer> uncachedIndices = new ArrayList<>();
        for (int i = 0; i < pkgs.size(); i++) {
            List<Drawable> cached = mgr.getCachedPreviews(pkgs.get(i));
            if (cached != null && !cached.isEmpty()) {
                populatePreviewRow(previewContainers.get(i), cached,
                        previewIconSizePx, previewGap, ctx, false);
            } else {
                uncachedIndices.add(i);
            }
        }

        sheet.setContentView(root);
        sheet.show();

        // Phase 2: load any uncached preview icons async
        if (!uncachedIndices.isEmpty()) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Executors.MODEL_EXECUTOR.execute(() -> {
                mgr.preParseAllPacks();

                for (int i : uncachedIndices) {
                    String pkg = pkgs.get(i);
                    IconPack pack = packObjects.get(i);
                    List<Drawable> previews;
                    if (pack != null) {
                        previews = pack.getPreviewIcons(pm);
                    } else {
                        // Default: load system icons
                        previews = new ArrayList<>();
                        for (ComponentName[] category : IconPack.PREVIEW_COMPONENTS) {
                            for (ComponentName cn : category) {
                                try {
                                    Drawable d = pm.getActivityIcon(cn);
                                    if (d != null) { previews.add(d); break; }
                                } catch (PackageManager.NameNotFoundException ignored) { }
                            }
                        }
                    }
                    final List<Drawable> finalPreviews = previews;
                    final int index = i;
                    mainHandler.post(() -> {
                        if (!sheet.isShowing()) return;
                        populatePreviewRow(previewContainers.get(index), finalPreviews,
                                previewIconSizePx, previewGap, ctx, true);
                    });
                }
            });
        }
    }

    private static void populatePreviewRow(LinearLayout container, List<Drawable> previews,
            int iconSize, int gap, Context ctx, boolean animate) {
        if (container == null || previews == null || previews.isEmpty()) return;
        container.setVisibility(View.VISIBLE);
        for (int p = 0; p < previews.size(); p++) {
            ImageView iv = new ImageView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
            if (p > 0) lp.setMarginStart(gap);
            iv.setLayoutParams(lp);
            iv.setImageDrawable(previews.get(p));
            if (animate) {
                iv.setAlpha(0f);
                iv.animate().alpha(1f).setDuration(250)
                        .setStartDelay(p * 50L).start();
            }
            container.addView(iv);
        }
    }

    /**
     * Apply icon pack change. Behavior depends on which pack changed:
     * - Home pack: clear main icon cache + invalidate drawer cache + full reload
     * - Drawer pack: invalidate drawer cache only + force reload (icons update live)
     */
    public static void applyIconPack(Context ctx, ConstantItem<String> prefItem,
            IconPackManager mgr, Runnable onComplete) {
        boolean isDrawerPack = prefItem == LauncherPrefs.ICON_PACK_DRAWER;
        LauncherAppState app = LauncherAppState.INSTANCE.get(ctx);

        if (isDrawerPack) {
            // Drawer pack changed — only invalidate drawer cache, force reload for UI update
            DrawerIconResolver.getInstance().invalidate();
            app.getModel().forceReload();
            if (onComplete != null) onComplete.run();
        } else {
            // Home pack changed — clear main cache + drawer cache + full reload
            Executors.MODEL_EXECUTOR.execute(() -> {
                mgr.getCurrentPack();
                app.getIconCache().clearAllIcons();
                new Handler(Looper.getMainLooper()).post(() -> {
                    LauncherIcons.clearPool(ctx);
                    DrawerIconResolver.getInstance().invalidate();
                    app.getModel().forceReload();
                    if (onComplete != null) onComplete.run();
                });
            });
        }
    }

    /**
     * Update the icon pack preference summary to show the current pack name.
     */
    public static void updateIconPackSummary(Context ctx, Preference pref,
            ConstantItem<String> prefItem, IconPackManager mgr) {
        String current = LauncherPrefs.get(ctx).get(prefItem);
        if (current == null || current.isEmpty()) {
            pref.setSummary(R.string.icon_pack_default);
        } else {
            Map<String, IconPack> packs = mgr.getInstalledPacks();
            IconPack pack = packs.get(current);
            pref.setSummary(pack != null ? pack.label : current);
        }
    }

    /**
     * Show the icon shape selection bottom sheet dialog.
     */
    public static void showIconShapeDialog(PreferenceFragmentCompat fragment,
            ConstantItem<String> prefItem, Preference pref) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        IconShapeModel[] shapes = ShapesProvider.INSTANCE.getIconShapes();

        List<CharSequence> labels = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        labels.add(ctx.getString(R.string.icon_shape_default));
        keys.add("");
        for (IconShapeModel shape : shapes) {
            labels.add(getShapeDisplayName(ctx, shape.getKey()));
            keys.add(shape.getKey());
        }

        String current = LauncherPrefs.get(ctx).get(prefItem);
        int selected = Math.max(0, keys.indexOf(current));

        float density = ctx.getResources().getDisplayMetrics().density;
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, (int) (24 * density));

        addSheetHandle(root, ctx, density);

        TextView titleView = new TextView(ctx);
        titleView.setText(R.string.icon_shape_title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
        titleView.setPadding(
                (int) (24 * density), (int) (16 * density),
                (int) (24 * density), (int) (16 * density));
        root.addView(titleView);

        LinearLayout itemsContainer = new LinearLayout(ctx);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            TextView item = createSheetItem(ctx, density,
                    labels.get(i), i == selected);
            item.setOnClickListener(v -> {
                String key = keys.get(idx);
                LauncherPrefs.get(ctx).put(prefItem, key);
                updateIconShapeSummary(ctx, pref, prefItem);
                sheet.dismiss();
            });
            itemsContainer.addView(item);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        sheet.setContentView(root);
        sheet.show();
    }

    /**
     * Update the icon shape preference summary.
     */
    public static void updateIconShapeSummary(Context ctx, Preference pref,
            ConstantItem<String> prefItem) {
        String current = LauncherPrefs.get(ctx).get(prefItem);
        if (current == null || current.isEmpty()) {
            pref.setSummary(R.string.icon_shape_default);
            return;
        }
        pref.setSummary(getShapeDisplayName(ctx, current));
    }

    /**
     * Get human-readable name for a shape key.
     */
    public static CharSequence getShapeDisplayName(Context ctx, String key) {
        if (key == null || key.isEmpty()) return ctx.getString(R.string.icon_shape_default);
        switch (key) {
            case ShapesProvider.CIRCLE_KEY: return ctx.getString(R.string.icon_shape_circle);
            case ShapesProvider.SQUARE_KEY: return ctx.getString(R.string.icon_shape_square);
            case ShapesProvider.FOUR_SIDED_COOKIE_KEY:
                return ctx.getString(R.string.icon_shape_four_sided_cookie);
            case ShapesProvider.SEVEN_SIDED_COOKIE_KEY:
                return ctx.getString(R.string.icon_shape_seven_sided_cookie);
            case ShapesProvider.ARCH_KEY: return ctx.getString(R.string.icon_shape_arch);
            case ShapesProvider.NONE_KEY: return ctx.getString(R.string.icon_shape_none);
            default: return key;
        }
    }

    // ---- Shared UI helpers ----

    static void addSheetHandle(LinearLayout root, Context ctx, float density) {
        View handle = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(2 * density);
        bg.setColor(ctx.getColor(R.color.materialColorOutline));
        handle.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (32 * density), (int) (4 * density));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = (int) (12 * density);
        handle.setLayoutParams(lp);
        root.addView(handle);
    }

    static TextView createSheetItem(Context ctx, float density,
            CharSequence text, boolean selected) {
        TextView item = new TextView(ctx);
        item.setText(text);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        item.setTextColor(selected
                ? ctx.getColor(R.color.materialColorPrimary)
                : ctx.getColor(R.color.materialColorOnSurface));
        item.setMinHeight((int) (56 * density));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(
                (int) (24 * density), 0,
                (int) (24 * density), 0);
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true);
        item.setBackgroundResource(tv.resourceId);
        return item;
    }
}
