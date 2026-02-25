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

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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

import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.shape.ShapeAppearanceModel;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Extracted shared dialog logic for icon pack, shape, and size settings.
 * Used by both HomeScreenFragment and AppDrawerFragment.
 */
public class IconSettingsHelper {

    private static final String[] SIZE_PRESETS = {"0.8", "0.863", "0.92", "1.0"};
    private static final String[] SIZE_LABELS = {"S (80%)", "M (86%)", "L (92%)", "XL (100%)"};
    private static final long CORNER_ANIM_DURATION = 250L;

    /**
     * Show the icon pack selection bottom sheet dialog.
     * Each pack is a card with the pack icon + name on top and real-size
     * preview icons below. The selected pack gets a tinted border + fill.
     */
    public static void showIconPackDialog(PreferenceFragmentCompat fragment,
            ConstantItem<String> prefItem, Preference pref, IconPackManager mgr) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        mgr.refreshPackList();
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

        Resources res = ctx.getResources();
        int cornerPx = res.getDimensionPixelSize(R.dimen.settings_card_corner_radius);
        int marginH = res.getDimensionPixelSize(R.dimen.settings_card_margin_horizontal);
        int marginV = res.getDimensionPixelSize(R.dimen.settings_card_margin_vertical);
        int cardPad = res.getDimensionPixelSize(R.dimen.settings_card_padding);
        int previewGap = res.getDimensionPixelSize(R.dimen.settings_preview_gap);

        // Get real icon size from device profile (92% for preview)
        int previewIconSizePx = (int) (InvariantDeviceProfile.INSTANCE.get(ctx)
                .getDeviceProfile(ctx).iconSizePx * 0.92f);

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);
        int colorSelectedFill = ctx.getColor(R.color.materialColorPrimaryContainer);
        int colorSelectedBorder = ctx.getColor(R.color.materialColorPrimary);
        int colorOnPrimaryContainer = ctx.getColor(R.color.materialColorOnPrimaryContainer);

        SettingsSheetBuilder.SheetComponents components =
                new SettingsSheetBuilder(ctx)
                        .setTitle(R.string.icon_pack_title)
                        .dismissOnDestroy(fragment)
                        .build();
        BottomSheetDialog sheet = components.sheet;
        TextView titleView = components.titleView;

        LinearLayout itemsContainer = components.contentArea;
        itemsContainer.setPadding(0, 0, 0, res.getDimensionPixelSize(R.dimen.settings_item_spacing));

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
                cardBg.setStroke(res.getDimensionPixelSize(R.dimen.settings_stroke_width), colorSelectedBorder);
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
            int packIconPx = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    packIconPx, packIconPx);
            iconLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_icon_margin_end));
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
                badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.settings_sheet_category_text_size));
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
            previewLp.topMargin = res.getDimensionPixelSize(R.dimen.settings_icon_margin_end);
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
                    // Refresh adaptive shape switch in the parent fragment
                    if (fragment instanceof HomeScreenFragment) {
                        ((HomeScreenFragment) fragment).refreshAdaptiveShapeState();
                    } else if (fragment instanceof AppDrawerFragment) {
                        ((AppDrawerFragment) fragment).refreshAdaptiveShapeState();
                    }
                    sheet.dismiss();
                });
            });

            itemsContainer.addView(card);
        }

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

        components.showScrollable();

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
     *
     * Also auto-detects adaptive icon packs and sets the adaptive shape switch.
     */
    public static void applyIconPack(Context ctx, ConstantItem<String> prefItem,
            IconPackManager mgr, Runnable onComplete) {
        boolean isDrawerPack = prefItem == LauncherPrefs.ICON_PACK_DRAWER;
        LauncherAppState app = LauncherAppState.INSTANCE.get(ctx);

        if (isDrawerPack) {
            // Drawer pack changed — only invalidate drawer cache, force reload for UI update
            DrawerIconResolver.getInstance().invalidate();

            // Auto-detect adaptive and set drawer adaptive switch
            autoDetectAdaptive(ctx, mgr, true);

            // Force ThemeManager to pick up the new adaptive shape state synchronously
            // before the model reloads (prevents stale IconState race)
            ThemeManager.INSTANCE.get(ctx).onConfigurationChanged();
            app.getModel().forceReload();
            if (onComplete != null) onComplete.run();
        } else {
            // Home pack changed — clear main cache + drawer cache + full reload
            Executors.MODEL_EXECUTOR.execute(() -> {
                mgr.getCurrentPack();

                // Auto-detect adaptive on background thread
                autoDetectAdaptiveAsync(ctx, mgr, false);

                app.getIconCache().clearAllIcons();
                Executors.MAIN_EXECUTOR.execute(() -> {
                    LauncherIcons.clearPool(ctx);
                    DrawerIconResolver.getInstance().invalidate();
                    // Force ThemeManager to pick up the new adaptive shape state synchronously
                    // before the model reloads (prevents stale IconState race)
                    ThemeManager.INSTANCE.get(ctx).onConfigurationChanged();
                    app.getModel().forceReload();
                    if (onComplete != null) onComplete.run();
                });
            });
        }
    }

    /**
     * Auto-detect whether the current pack is adaptive and flip the switch ON if so.
     * Never forces the switch OFF — the user may want adaptive shapes even for
     * non-adaptive packs (to force-wrap them).
     */
    private static void autoDetectAdaptive(Context ctx, IconPackManager mgr,
            boolean isDrawer) {
        IconPack pack = isDrawer ? mgr.getDrawerPack() : mgr.getCurrentPack();
        if (pack != null && pack.isAdaptivePack(ctx.getPackageManager())) {
            LauncherPrefs.get(ctx).put(
                    isDrawer ? LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER
                             : LauncherPrefs.APPLY_ADAPTIVE_SHAPE,
                    true);
        }
    }

    /** Background-thread version for home packs where parsing might be slow. */
    private static void autoDetectAdaptiveAsync(Context ctx, IconPackManager mgr,
            boolean isDrawer) {
        autoDetectAdaptive(ctx, mgr, isDrawer);
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
     * Callback interface for when a per-app pack is selected.
     */
    public interface PerAppPackCallback {
        /** Called when user picks "Follow global setting" (override = null). */
        void onFollowGlobal();
        /** Called when user picks "System default". */
        void onSystemDefault();
        /** Called when user picks a specific pack (to then browse icons). */
        void onPackSelected(String packPackage, CharSequence packLabel);
    }

    /**
     * Show per-app icon pack selection bottom sheet (Step 1 of the two-step flow).
     * Lists: Follow global, System default, then each installed pack with a preview
     * of this app's auto-resolved icon.
     */
    public static void showPerAppPackDialog(PreferenceFragmentCompat fragment,
            ComponentName appCn, IconPackManager mgr, PerAppPackCallback callback) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        mgr.refreshPackList();
        Map<String, IconPack> packs = mgr.getInstalledPacks();
        PackageManager pm = ctx.getPackageManager();
        Resources res = ctx.getResources();

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);

        SettingsSheetBuilder.SheetComponents components =
                new SettingsSheetBuilder(ctx)
                        .setTitle(R.string.choose_icon_pack)
                        .dismissOnDestroy(fragment)
                        .build();
        BottomSheetDialog sheet = components.sheet;

        // "Follow global setting"
        components.contentArea.addView(SettingsSheetBuilder.createCard(ctx,
                ctx.getString(R.string.customize_follow_global), null,
                colorSurfaceVar, colorOnSurface, v -> {
                    sheet.dismiss();
                    callback.onFollowGlobal();
                }));

        // "System default"
        components.contentArea.addView(SettingsSheetBuilder.createCard(ctx,
                ctx.getString(R.string.customize_system_default), null,
                colorSurfaceVar, colorOnSurface, v -> {
                    sheet.dismiss();
                    callback.onSystemDefault();
                }));

        // Each installed pack
        int previewSize = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
        for (Map.Entry<String, IconPack> entry : packs.entrySet()) {
            String pkg = entry.getKey();
            IconPack pack = entry.getValue();

            LinearLayout card = SettingsSheetBuilder.createCard(ctx,
                    pack.label.toString(), pack.getPackIcon(pm),
                    colorSurfaceVar, colorOnSurface, v -> {
                        sheet.dismiss();
                        callback.onPackSelected(pkg, pack.label);
                    });

            // Add app preview icon from this pack (async)
            ImageView preview = new ImageView(ctx);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    previewSize, previewSize);
            previewLp.setMarginStart(res.getDimensionPixelSize(R.dimen.settings_item_spacing));
            preview.setLayoutParams(previewLp);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Find the header row (first child) and add preview to it
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
                    Executors.MAIN_EXECUTOR.execute(() -> {
                        if (sheet.isShowing()) {
                            preview.setImageDrawable(finalIcon);
                        }
                    });
                }
            });

            components.contentArea.addView(card);
        }

        components.showScrollable();
    }

    /**
     * Show the icon shape selection bottom sheet dialog with visual shape previews.
     * Filters out the "None" shape — that behavior is now handled by the
     * "Apply adaptive icon shape" switch.
     */
    public static void showIconShapeDialog(PreferenceFragmentCompat fragment,
            ConstantItem<String> prefItem, Preference pref) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;
        String current = LauncherPrefs.get(ctx).get(prefItem);
        showShapePickerDialog(ctx, R.string.icon_shape_title,
                ctx.getString(R.string.icon_shape_default), current,
                key -> {
                    LauncherPrefs.get(ctx).put(prefItem, key);
                    updateIconShapeSummary(ctx, pref, prefItem);
                }, fragment);
    }

    /**
     * A simple Drawable that renders a filled shape preview from an SVG path string.
     * The path is in the standard 100x100 coordinate system used by ShapesProvider.
     */
    static class ShapePreviewDrawable extends Drawable {
        private final Path mPath;
        private final Paint mPaint;
        private final int mSize;

        ShapePreviewDrawable(String svgPathData, int sizePx, int fillColor) {
            mSize = sizePx;
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setColor(fillColor);
            mPaint.setStyle(Paint.Style.FILL);

            // Parse the SVG path and scale from 100x100 to sizePx
            Path parsed;
            try {
                parsed = PathParser.createPathFromPathData(svgPathData);
            } catch (Exception e) {
                parsed = new Path();
            }

            float scale = sizePx / 100f;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(scale, scale);
            parsed.transform(matrix);
            mPath = parsed;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(mPath, mPaint);
        }

        @Override
        public int getIntrinsicWidth() { return mSize; }

        @Override
        public int getIntrinsicHeight() { return mSize; }

        @Override
        public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * Show a per-app icon shape picker. Unlike showIconShapeDialog, this does not write
     * to LauncherPrefs — it calls the callback with the selected shape key.
     */
    public static void showPerAppShapeDialog(PreferenceFragmentCompat fragment,
            ComponentName componentName, boolean isHome, Consumer<String> onShapeSelected) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;
        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride override = isHome
                ? mgr.getHomeOverride(componentName)
                : mgr.getDrawerOverride(componentName);
        String currentKey = override != null ? override.shapeKey : "";
        showShapePickerDialog(ctx, R.string.icon_shape_title,
                ctx.getString(R.string.icon_shape_default), currentKey,
                onShapeSelected, fragment);
    }

    /**
     * Unified shape picker dialog used by global, per-app, and per-folder pickers.
     * Builds the shape list from ShapesProvider, creates rows with visual previews,
     * and calls onSelect with the chosen key ("" for default, shape key otherwise).
     */
    public static void showShapePickerDialog(Context ctx, int titleResId,
            CharSequence defaultLabel, String currentKey,
            Consumer<String> onSelect,
            @Nullable PreferenceFragmentCompat lifecycleOwner) {
        IconShapeModel[] shapes = ShapesProvider.INSTANCE.getIconShapes();

        List<CharSequence> labels = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> pathStrings = new ArrayList<>();
        labels.add(defaultLabel);
        keys.add("");
        pathStrings.add("");
        for (IconShapeModel shape : shapes) {
            if (ShapesProvider.NONE_KEY.equals(shape.getKey())) continue;
            labels.add(getShapeDisplayName(ctx, shape.getKey()));
            keys.add(shape.getKey());
            pathStrings.add(shape.getPathString());
        }

        String effectiveKey = currentKey != null ? currentKey : "";
        int selected = Math.max(0, keys.indexOf(effectiveKey));

        Resources res = ctx.getResources();
        int previewSizePx = res.getDimensionPixelSize(R.dimen.settings_shape_preview_size);
        int colorFill = ctx.getColor(R.color.materialColorSurfaceContainerHighest);

        SettingsSheetBuilder builder = new SettingsSheetBuilder(ctx).setTitle(titleResId);
        if (lifecycleOwner != null) builder.dismissOnDestroy(lifecycleOwner);
        SettingsSheetBuilder.SheetComponents components = builder.build();
        BottomSheetDialog sheet = components.sheet;

        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            boolean isSelected = (i == selected);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(res.getDimensionPixelSize(R.dimen.settings_row_min_height));
            row.setPadding(res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                    0, res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal), 0);
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            String pathStr = pathStrings.get(i);
            if (!pathStr.isEmpty()) {
                ImageView shapePreview = new ImageView(ctx);
                LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                previewLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_card_padding));
                shapePreview.setLayoutParams(previewLp);
                shapePreview.setImageDrawable(new ShapePreviewDrawable(
                        pathStr, previewSizePx, colorFill));
                row.addView(shapePreview);
            } else {
                View spacer = new View(ctx);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                spacerLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_card_padding));
                spacer.setLayoutParams(spacerLp);
                row.addView(spacer);
            }

            TextView nameView = new TextView(ctx);
            nameView.setText(labels.get(i));
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            nameView.setTextColor(isSelected
                    ? ctx.getColor(R.color.materialColorPrimary)
                    : ctx.getColor(R.color.materialColorOnSurface));
            row.addView(nameView);

            row.setOnClickListener(v -> {
                onSelect.accept(keys.get(idx));
                sheet.dismiss();
            });
            components.contentArea.addView(row);
        }

        components.showScrollable();
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

    // ---- Icon size toggle shared helper ----

    /**
     * Bind a MaterialButtonToggleGroup for icon size selection.
     * Used by HomeScreenFragment, AppDrawerFragment, and AppCustomizeFragment.
     *
     * @param toggleRow     the View containing R.id.size_toggle_group
     * @param currentValue  the current icon size scale string (e.g., "0.8", "1.0")
     * @param onValueChanged called with the new size scale string when the user picks a preset
     * @param onCustomClick  called when the user taps the custom star button
     * @return the last preset button ID that was selected, or View.NO_ID
     */
    public static int bindIconSizeToggle(View toggleRow, String currentValue,
            Consumer<String> onValueChanged, Runnable onCustomClick) {
        MaterialButtonToggleGroup toggleGroup =
                toggleRow.findViewById(R.id.size_toggle_group);
        if (toggleGroup == null) return View.NO_ID;

        int[] btnIds = {R.id.btn_size_s, R.id.btn_size_m,
                R.id.btn_size_l, R.id.btn_size_xl};
        final int[] lastPresetId = {View.NO_ID};
        boolean isPreset = false;
        for (int j = 0; j < SIZE_PRESETS.length; j++) {
            if (SIZE_PRESETS[j].equals(currentValue)) {
                toggleGroup.check(btnIds[j]);
                lastPresetId[0] = btnIds[j];
                isPreset = true;
                break;
            }
        }
        if (!isPreset) {
            toggleGroup.check(R.id.btn_size_custom);
        }

        // Set pill shape on initial selection (no animation on first load)
        toggleGroup.post(() -> {
            for (int i = 0; i < toggleGroup.getChildCount(); i++) {
                View c = toggleGroup.getChildAt(i);
                if (c instanceof MaterialButton && ((MaterialButton) c).isChecked()) {
                    float pill = c.getHeight() / 2f;
                    if (pill > 0) {
                        ((MaterialButton) c).setShapeAppearanceModel(
                                ShapeAppearanceModel.builder()
                                        .setAllCornerSizes(pill)
                                        .build());
                    }
                }
            }
        });

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            MaterialButton btn = group.findViewById(checkedId);
            if (btn != null) {
                animateButtonCorners(toggleRow, btn, isChecked);
            }

            if (!isChecked) return;

            if (checkedId == R.id.btn_size_custom) {
                if (onCustomClick != null) onCustomClick.run();
                return;
            }

            lastPresetId[0] = checkedId;

            if (btn != null) {
                String value = (String) btn.getTag();
                if (onValueChanged != null) onValueChanged.accept(value);
            }
        });

        return lastPresetId[0];
    }

    private static void animateButtonCorners(View parent, MaterialButton btn, boolean toPill) {
        Resources res = parent.getResources();
        float innerRadius = res.getDimension(R.dimen.settings_inner_radius);

        btn.post(() -> {
            float pillRadius = btn.getHeight() / 2f;
            if (pillRadius <= 0) pillRadius = res.getDimension(R.dimen.settings_pill_radius);

            float startRadius = toPill ? innerRadius : pillRadius;
            float endRadius = toPill ? pillRadius : innerRadius;

            ValueAnimator anim = ValueAnimator.ofFloat(startRadius, endRadius);
            anim.setDuration(CORNER_ANIM_DURATION);
            anim.setInterpolator(new FastOutSlowInInterpolator());
            anim.addUpdateListener(a -> {
                float r = (float) a.getAnimatedValue();
                btn.setShapeAppearanceModel(
                        btn.getShapeAppearanceModel().toBuilder()
                                .setAllCornerSizes(r)
                                .build());
            });
            anim.start();
        });
    }

    /**
     * Get a human-readable summary for an icon size scale value.
     */
    public static String getIconSizeSummary(Context ctx, String sizeValue) {
        for (int i = 0; i < SIZE_PRESETS.length; i++) {
            if (SIZE_PRESETS[i].equals(sizeValue)) {
                return SIZE_LABELS[i];
            }
        }
        try {
            float pct = Float.parseFloat(sizeValue) * 100f;
            return ctx.getString(R.string.icon_size_custom)
                    + " (" + String.format("%.0f%%", pct) + ")";
        } catch (NumberFormatException e) {
            return ctx.getString(R.string.icon_size_custom) + " (" + sizeValue + ")";
        }
    }

    static TextView createSheetItem(Context ctx, Resources res,
            CharSequence text, boolean selected) {
        TextView item = new TextView(ctx);
        item.setText(text);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        item.setTextColor(selected
                ? ctx.getColor(R.color.materialColorPrimary)
                : ctx.getColor(R.color.materialColorOnSurface));
        item.setMinHeight(res.getDimensionPixelSize(R.dimen.settings_row_min_height));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal), 0,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal), 0);
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true);
        item.setBackgroundResource(tv.resourceId);
        return item;
    }
}
