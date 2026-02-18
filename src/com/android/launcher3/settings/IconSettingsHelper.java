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

import androidx.annotation.NonNull;
import androidx.core.graphics.PathParser;
import androidx.core.widget.NestedScrollView;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
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

    private static final int PACK_ICON_SIZE_DP = 40;
    private static final int CARD_CORNER_DP = 16;
    private static final int CARD_MARGIN_H_DP = 16;
    private static final int CARD_MARGIN_V_DP = 6;
    private static final int CARD_PAD_DP = 16;
    private static final int PREVIEW_GAP_DP = 8;
    private static final int SHAPE_PREVIEW_DP = 36;

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
        dismissOnDestroy(fragment, sheet);

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

            app.getModel().forceReload();
            if (onComplete != null) onComplete.run();
        } else {
            // Home pack changed — clear main cache + drawer cache + full reload
            Executors.MODEL_EXECUTOR.execute(() -> {
                mgr.getCurrentPack();

                // Auto-detect adaptive on background thread
                autoDetectAdaptiveAsync(ctx, mgr, false);

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
     * Auto-detect whether the current pack is adaptive and set the corresponding pref.
     * Safe to call from the main thread for drawer packs (re-uses cached result).
     */
    private static void autoDetectAdaptive(Context ctx, IconPackManager mgr,
            boolean isDrawer) {
        IconPack pack = isDrawer ? mgr.getDrawerPack() : mgr.getCurrentPack();
        if (pack != null) {
            boolean isAdaptive = pack.isAdaptivePack(ctx.getPackageManager());
            LauncherPrefs.get(ctx).put(
                    isDrawer ? LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER
                             : LauncherPrefs.APPLY_ADAPTIVE_SHAPE,
                    isAdaptive);
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
        float density = ctx.getResources().getDisplayMetrics().density;
        int cornerPx = (int) (CARD_CORNER_DP * density);
        int marginH = (int) (CARD_MARGIN_H_DP * density);
        int marginV = (int) (CARD_MARGIN_V_DP * density);
        int cardPad = (int) (CARD_PAD_DP * density);

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);

        BottomSheetDialog sheet = new BottomSheetDialog(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, (int) (24 * density));

        addSheetHandle(root, ctx, density);

        TextView titleView = new TextView(ctx);
        titleView.setText(R.string.choose_icon_pack);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTextColor(colorOnSurface);
        titleView.setPadding(
                (int) (24 * density), (int) (16 * density),
                (int) (24 * density), (int) (16 * density));
        root.addView(titleView);

        LinearLayout items = new LinearLayout(ctx);
        items.setOrientation(LinearLayout.VERTICAL);

        // "Follow global setting"
        items.addView(createPerAppCard(ctx, density, cornerPx, marginH, marginV, cardPad,
                colorSurfaceVar, colorOnSurface,
                ctx.getString(R.string.customize_follow_global), null, v -> {
                    sheet.dismiss();
                    callback.onFollowGlobal();
                }));

        // "System default"
        items.addView(createPerAppCard(ctx, density, cornerPx, marginH, marginV, cardPad,
                colorSurfaceVar, colorOnSurface,
                ctx.getString(R.string.customize_system_default), null, v -> {
                    sheet.dismiss();
                    callback.onSystemDefault();
                }));

        // Each installed pack
        int previewSize = (int) (PACK_ICON_SIZE_DP * density);
        for (Map.Entry<String, IconPack> entry : packs.entrySet()) {
            String pkg = entry.getKey();
            IconPack pack = entry.getValue();

            LinearLayout card = createPerAppCard(ctx, density, cornerPx, marginH, marginV,
                    cardPad, colorSurfaceVar, colorOnSurface,
                    pack.label.toString(), pack.getPackIcon(pm), v -> {
                        sheet.dismiss();
                        callback.onPackSelected(pkg, pack.label);
                    });

            // Add app preview icon from this pack (async)
            ImageView preview = new ImageView(ctx);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    previewSize, previewSize);
            previewLp.setMarginStart((int) (8 * density));
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
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (sheet.isShowing()) {
                            preview.setImageDrawable(finalIcon);
                        }
                    });
                }
            });

            items.addView(card);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.setClipToPadding(false);
        scroll.addView(items);
        root.addView(scroll);

        sheet.setContentView(root);
        sheet.show();
        dismissOnDestroy(fragment, sheet);
    }

    private static LinearLayout createPerAppCard(Context ctx, float density,
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

        // Ripple
        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true);
        card.setForeground(ctx.getDrawable(ripple.resourceId));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        if (packIcon != null) {
            ImageView icon = new ImageView(ctx);
            int iconPx = (int) (PACK_ICON_SIZE_DP * density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.setMarginEnd((int) (12 * density));
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
        card.setOnClickListener(listener);
        return card;
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

        IconShapeModel[] shapes = ShapesProvider.INSTANCE.getIconShapes();

        List<CharSequence> labels = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> pathStrings = new ArrayList<>();
        labels.add(ctx.getString(R.string.icon_shape_default));
        keys.add("");
        pathStrings.add(""); // no preview for system default
        for (IconShapeModel shape : shapes) {
            // Filter out "none" — handled by adaptive shape switch
            if (ShapesProvider.NONE_KEY.equals(shape.getKey())) continue;
            labels.add(getShapeDisplayName(ctx, shape.getKey()));
            keys.add(shape.getKey());
            pathStrings.add(shape.getPathString());
        }

        String current = LauncherPrefs.get(ctx).get(prefItem);
        int selected = Math.max(0, keys.indexOf(current));

        float density = ctx.getResources().getDisplayMetrics().density;
        int previewSizePx = (int) (SHAPE_PREVIEW_DP * density);
        int colorFill = ctx.getColor(R.color.materialColorSurfaceContainerHighest);

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
            boolean isSelected = (i == selected);

            // Row: [shape preview] [shape name]
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (56 * density));
            row.setPadding((int) (24 * density), 0, (int) (24 * density), 0);
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            // Shape preview
            String pathStr = pathStrings.get(i);
            if (!pathStr.isEmpty()) {
                ImageView shapePreview = new ImageView(ctx);
                LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                previewLp.setMarginEnd((int) (16 * density));
                shapePreview.setLayoutParams(previewLp);
                shapePreview.setImageDrawable(new ShapePreviewDrawable(
                        pathStr, previewSizePx, colorFill));
                row.addView(shapePreview);
            } else {
                // System default: add spacer so text aligns
                View spacer = new View(ctx);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                spacerLp.setMarginEnd((int) (16 * density));
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
                String key = keys.get(idx);
                LauncherPrefs.get(ctx).put(prefItem, key);
                updateIconShapeSummary(ctx, pref, prefItem);
                sheet.dismiss();
            });
            itemsContainer.addView(row);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        sheet.setContentView(root);
        sheet.show();
        dismissOnDestroy(fragment, sheet);
    }

    /**
     * A simple Drawable that renders a filled shape preview from an SVG path string.
     * The path is in the standard 100x100 coordinate system used by ShapesProvider.
     */
    private static class ShapePreviewDrawable extends Drawable {
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
     * Does not include "System default" — per-app shapes are explicit.
     */
    public static void showPerAppShapeDialog(PreferenceFragmentCompat fragment,
            ComponentName componentName, boolean isHome, Consumer<String> onShapeSelected) {
        Context ctx = fragment.getContext();
        if (ctx == null) return;

        IconShapeModel[] shapes = ShapesProvider.INSTANCE.getIconShapes();

        List<CharSequence> labels = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> pathStrings = new ArrayList<>();

        // Per-app: include "System default" as the first option (follow global shape)
        labels.add(ctx.getString(R.string.icon_shape_default));
        keys.add("");
        pathStrings.add("");

        for (IconShapeModel shape : shapes) {
            if (ShapesProvider.NONE_KEY.equals(shape.getKey())) continue;
            labels.add(getShapeDisplayName(ctx, shape.getKey()));
            keys.add(shape.getKey());
            pathStrings.add(shape.getPathString());
        }

        // Determine currently selected shape from override
        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride override = isHome
                ? mgr.getHomeOverride(componentName)
                : mgr.getDrawerOverride(componentName);
        String currentKey = override != null ? override.shapeKey : "";
        int selected = Math.max(0, keys.indexOf(currentKey));

        float density = ctx.getResources().getDisplayMetrics().density;
        int previewSizePx = (int) (SHAPE_PREVIEW_DP * density);
        int colorFill = ctx.getColor(R.color.materialColorSurfaceContainerHighest);

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
            boolean isSelected = (i == selected);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight((int) (56 * density));
            row.setPadding((int) (24 * density), 0, (int) (24 * density), 0);
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            String pathStr = pathStrings.get(i);
            if (!pathStr.isEmpty()) {
                ImageView shapePreview = new ImageView(ctx);
                LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                previewLp.setMarginEnd((int) (16 * density));
                shapePreview.setLayoutParams(previewLp);
                shapePreview.setImageDrawable(new ShapePreviewDrawable(
                        pathStr, previewSizePx, colorFill));
                row.addView(shapePreview);
            } else {
                View spacer = new View(ctx);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                        previewSizePx, previewSizePx);
                spacerLp.setMarginEnd((int) (16 * density));
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
                onShapeSelected.accept(keys.get(idx));
                sheet.dismiss();
            });
            itemsContainer.addView(row);
        }

        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        sheet.setContentView(root);
        sheet.show();
        dismissOnDestroy(fragment, sheet);
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
        float density = parent.getResources().getDisplayMetrics().density;
        float innerRadius = 8 * density;

        btn.post(() -> {
            float pillRadius = btn.getHeight() / 2f;
            if (pillRadius <= 0) pillRadius = 20 * density;

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

    private static void dismissOnDestroy(PreferenceFragmentCompat fragment,
            BottomSheetDialog sheet) {
        fragment.getLifecycle().addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source,
                    @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    if (sheet.isShowing()) sheet.dismiss();
                    source.getLifecycle().removeObserver(this);
                }
            }
        });
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
