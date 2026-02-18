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
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;

/**
 * Resolves app drawer icons on-demand when the drawer has different icon settings
 * (pack, shape, or size) than the home screen. Uses an in-memory LRU cache to avoid
 * re-resolving icons on every scroll.
 *
 * The main icon cache always stores HOME icon pack/shape/size icons. This resolver
 * provides drawer-specific overrides that are checked at display time in BubbleTextView.
 */
public class DrawerIconResolver {

    private static final int CACHE_SIZE = 500;
    private static final int FALLBACK_ICON_SIZE = 192;

    private static volatile DrawerIconResolver sInstance;

    private final LruCache<ComponentName, BitmapInfo> mCache = new LruCache<>(CACHE_SIZE);
    private volatile Boolean mHasDistinctSettings;

    private DrawerIconResolver() { }

    public static DrawerIconResolver getInstance() {
        if (sInstance == null) {
            synchronized (DrawerIconResolver.class) {
                if (sInstance == null) {
                    sInstance = new DrawerIconResolver();
                }
            }
        }
        return sInstance;
    }

    /**
     * Check if the drawer needs its own icons (any drawer-specific setting differs from home).
     * Checks icon pack, shape, and size.
     */
    public boolean hasDistinctDrawerSettings(Context context) {
        Boolean cached = mHasDistinctSettings;
        if (cached != null) return cached;

        boolean matchHome = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (matchHome) {
            mHasDistinctSettings = false;
            return false;
        }

        IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
        if (mgr.hasDistinctDrawerPack()) {
            mHasDistinctSettings = true;
            return true;
        }

        ThemeManager.IconState state = ThemeManager.INSTANCE.get(context).getIconState();
        boolean distinct = !state.getIconMask().equals(state.getIconMaskDrawer())
                || state.getIconSizeScale() != state.getIconSizeScaleDrawer()
                || state.getIconScale() != state.getIconScaleDrawer();
        mHasDistinctSettings = distinct;
        return distinct;
    }

    /**
     * Get a drawer-specific icon for the given app info, or null if no override is needed.
     * Called from BubbleTextView when displaying in DISPLAY_ALL_APPS mode.
     *
     * @param info    the app info (provides component name and disabled state)
     * @param context the context
     * @param flags   drawable creation flags (themed, badge, etc.)
     * @return a FastBitmapDrawable with the drawer icon, or null to use the default
     */
    @Nullable
    public FastBitmapDrawable getDrawerIcon(ItemInfoWithIcon info, Context context, int flags) {
        ComponentName cn = info.getTargetComponent();
        if (cn == null) return null;

        // Check per-app drawer override first (even when match-home is on,
        // per-app drawer overrides still apply)
        PerAppIconOverrideManager overrideMgr = PerAppIconOverrideManager.getInstance(context);
        IconOverride perAppOverride = overrideMgr.getDrawerOverride(cn);

        if (perAppOverride == null && !hasDistinctDrawerSettings(context)) return null;

        Path badgeShape = Utilities.getIconShapeOrNull(context);

        // Skip cache if there's a per-app override (cache doesn't track per-app)
        if (perAppOverride == null) {
            BitmapInfo cached = mCache.get(cn);
            if (cached != null) {
                FastBitmapDrawable drawable = cached.newIcon(context, flags, badgeShape);
                drawable.setIsDisabled(info.isDisabled());
                return drawable;
            }
        }

        PackageManager pm = context.getPackageManager();
        Drawable icon = null;

        if (perAppOverride != null) {
            if (perAppOverride.isSystemDefault()) {
                // State B: explicit system default
                try {
                    icon = pm.getActivityIcon(cn);
                } catch (PackageManager.NameNotFoundException ignored) { }
            } else if (perAppOverride.hasPackOverride()) {
                // State C: specific pack override
                IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
                IconPack overridePack = mgr.getPack(perAppOverride.packPackage);
                if (overridePack != null) {
                    if (perAppOverride.hasSpecificDrawable()) {
                        icon = overridePack.getDrawableForEntry(
                                perAppOverride.drawableName, pm);
                    }
                    if (icon == null) {
                        icon = overridePack.getCalendarIcon(cn, pm);
                    }
                    if (icon == null) {
                        icon = overridePack.getIconForComponent(cn, pm);
                    }
                    if (icon == null && overridePack.hasFallbackMask()) {
                        try {
                            Drawable original = pm.getActivityIcon(cn);
                            icon = overridePack.applyFallbackMask(original, FALLBACK_ICON_SIZE);
                        } catch (PackageManager.NameNotFoundException ignored) { }
                    }
                }
            } else {
                // State D: render-only (FOLLOW_GLOBAL pack) — use global drawer pack
                IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
                IconPack pack = mgr.hasDistinctDrawerPack()
                        ? mgr.getDrawerPack() : mgr.getCurrentPack();
                if (pack != null) {
                    icon = pack.getCalendarIcon(cn, pm);
                    if (icon == null) {
                        icon = pack.getIconForComponent(cn, pm);
                    }
                    if (icon == null && pack.hasFallbackMask()) {
                        try {
                            Drawable original = pm.getActivityIcon(cn);
                            icon = pack.applyFallbackMask(original, FALLBACK_ICON_SIZE);
                        } catch (PackageManager.NameNotFoundException ignored) { }
                    }
                }
            }
        } else {
            // Standard drawer pack resolution
            IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
            IconPack pack = mgr.hasDistinctDrawerPack()
                    ? mgr.getDrawerPack()
                    : mgr.getCurrentPack();

            if (pack != null) {
                icon = pack.getCalendarIcon(cn, pm);
                if (icon == null) {
                    icon = pack.getIconForComponent(cn, pm);
                }
                if (icon == null && pack.hasFallbackMask()) {
                    try {
                        Drawable original = pm.getActivityIcon(cn);
                        icon = pack.applyFallbackMask(original, FALLBACK_ICON_SIZE);
                    } catch (PackageManager.NameNotFoundException ignored) { }
                }
            }
        }

        // If no pack icon found, use system icon (re-rendered with drawer shape/size)
        if (icon == null) {
            try {
                icon = pm.getActivityIcon(cn);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        if (icon == null) return null;

        // Create BitmapInfo using drawer-specific factory (per-app overrides take priority)
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        ThemeManager.IconState state = ThemeManager.INSTANCE.get(context).getIconState();
        BitmapInfo bitmapInfo;
        if (perAppOverride != null && perAppOverride.hasAnyRenderOverride()) {
            try (PerAppDrawerIconFactory factory = new PerAppDrawerIconFactory(
                    context, idp.fillResIconDpi, idp.iconBitmapSize, perAppOverride, state)) {
                bitmapInfo = factory.createBadgedIconBitmap(icon);
            }
        } else {
            try (DrawerIconFactory factory = new DrawerIconFactory(
                    context, idp.fillResIconDpi, idp.iconBitmapSize, state)) {
                bitmapInfo = factory.createBadgedIconBitmap(icon);
            }
        }

        // Only cache when there's no per-app override
        if (perAppOverride == null) {
            mCache.put(cn, bitmapInfo);
        }

        FastBitmapDrawable drawable = bitmapInfo.newIcon(context, flags, badgeShape);
        drawable.setIsDisabled(info.isDisabled());
        return drawable;
    }

    /**
     * Pre-populate the drawer icon cache for all apps on a background thread.
     * After this completes, the first drawer scroll has zero cache misses.
     */
    public void preCacheIcons(Context context, ComponentName[] components) {
        if (!hasDistinctDrawerSettings(context)) return;

        PackageManager pm = context.getPackageManager();
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        ThemeManager.IconState state = ThemeManager.INSTANCE.get(context).getIconState();
        IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
        IconPack pack = mgr.hasDistinctDrawerPack()
                ? mgr.getDrawerPack() : mgr.getCurrentPack();

        for (ComponentName cn : components) {
            if (cn == null || mCache.get(cn) != null) continue;

            Drawable icon = null;
            if (pack != null) {
                icon = pack.getCalendarIcon(cn, pm);
                if (icon == null) icon = pack.getIconForComponent(cn, pm);
                if (icon == null && pack.hasFallbackMask()) {
                    try {
                        Drawable original = pm.getActivityIcon(cn);
                        icon = pack.applyFallbackMask(original, FALLBACK_ICON_SIZE);
                    } catch (PackageManager.NameNotFoundException ignored) { }
                }
            }
            if (icon == null) {
                try { icon = pm.getActivityIcon(cn); }
                catch (PackageManager.NameNotFoundException ignored) { }
            }
            if (icon == null) continue;

            try (DrawerIconFactory factory = new DrawerIconFactory(
                    context, idp.fillResIconDpi, idp.iconBitmapSize, state)) {
                mCache.put(cn, factory.createBadgedIconBitmap(icon));
            }
        }
    }

    /** Clear the drawer icon cache. Call when drawer pack, shape, or size changes. */
    public void invalidate() {
        mCache.evictAll();
        mHasDistinctSettings = null;
    }

    /**
     * Icon factory configured with drawer-specific shape, scale, and size settings.
     * Mirrors the overrides in {@link LauncherIcons} but reads from drawer fields of IconState.
     */
    private static class DrawerIconFactory extends BaseIconFactory {

        private final float mIconScale;
        private final float mIconSizeScale;
        private final boolean mIsNoneShape;
        private final ShapeDelegate mIconShape;

        DrawerIconFactory(Context context, int fillResIconDpi, int iconBitmapSize,
                ThemeManager.IconState state) {
            super(context, fillResIconDpi, iconBitmapSize);
            mIconScale = state.getIconScaleDrawer();
            mIconSizeScale = state.getIconSizeScaleDrawer();
            mIconShape = state.getIconShapeDrawer();
            mIsNoneShape = state.getIconMaskDrawer().equals(ShapesProvider.NONE_PATH);
        }

        @Override
        public Path getShapePath(AdaptiveIconDrawable drawable, Rect iconBounds) {
            if (!Flags.enableLauncherIconShapes()) return super.getShapePath(drawable, iconBounds);
            return mIconShape.getPath(iconBounds);
        }

        @Override
        public float getIconScale() {
            if (!Flags.enableLauncherIconShapes()) return super.getIconScale();
            return mIconScale;
        }

        @Nullable
        @Override
        protected AdaptiveIconDrawable normalizeAndWrapToAdaptiveIcon(
                @Nullable Drawable icon, @NonNull float[] outScale) {
            if (icon == null) return null;
            outScale[0] = mIconSizeScale;
            return wrapToAdaptiveIcon(icon);
        }

        @NonNull
        @Override
        public AdaptiveIconDrawable wrapToAdaptiveIcon(@NonNull Drawable icon) {
            mWrapperBackgroundColor = Color.TRANSPARENT;
            return super.wrapToAdaptiveIcon(icon);
        }

        @NonNull
        @Override
        public BitmapInfo createBadgedIconBitmap(@NonNull Drawable icon,
                @Nullable IconOptions options) {
            if (mIsNoneShape && !(icon instanceof AdaptiveIconDrawable)) {
                android.graphics.Bitmap bitmap = createIconBitmap(icon, mIconSizeScale,
                        MODE_DEFAULT);
                int color = ColorExtractor.findDominantColorByHue(bitmap);
                return BitmapInfo.of(bitmap, color).withFlags(getBitmapFlagOp(options));
            }
            return super.createBadgedIconBitmap(icon, options);
        }

        @Override
        protected void drawAdaptiveIcon(@NonNull Canvas canvas,
                @NonNull AdaptiveIconDrawable drawable, @NonNull Path overridePath) {
            if (!Flags.enableLauncherIconShapes()) {
                super.drawAdaptiveIcon(canvas, drawable, overridePath);
                return;
            }
            if (mIsNoneShape) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
                if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
                return;
            }

            // BLUR_FACTOR compensation (same logic as LauncherIcons)
            Rect bounds = drawable.getBounds();
            int currentSize = bounds.width();
            int size = mIconBitmapSize;
            int actualOffset = (size - currentSize) / 2;
            int desiredOffset = Math.round(size * (1 - mIconSizeScale) / 2f);

            if (desiredOffset < actualOffset) {
                int compensation = actualOffset - desiredOffset;
                int newSize = size - desiredOffset * 2;

                canvas.save();
                canvas.translate(-compensation, -compensation);
                drawable.setBounds(0, 0, newSize, newSize);
                Path compensatedPath = getShapePath(drawable, drawable.getBounds());

                canvas.clipPath(compensatedPath);
                canvas.drawColor(Color.TRANSPARENT);
                canvas.save();
                canvas.scale(mIconScale, mIconScale,
                        canvas.getWidth() / 2f, canvas.getHeight() / 2f);
                if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
                if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
                canvas.restore();

                drawable.setBounds(bounds);
                canvas.restore();
                return;
            }

            canvas.clipPath(overridePath);
            canvas.drawColor(Color.TRANSPARENT);
            canvas.save();
            canvas.scale(mIconScale, mIconScale,
                    canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
            if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Per-app drawer icon factory that reads shape/size/adaptive from the per-app override,
     * falling back to global drawer values for unset fields.
     */
    public static class PerAppDrawerIconFactory extends BaseIconFactory {

        private final float mIconScale;
        private final float mIconSizeScale;
        private final boolean mIsNoneShape;
        private final ShapeDelegate mIconShape;

        public PerAppDrawerIconFactory(Context context, int fillResIconDpi, int iconBitmapSize,
                IconOverride override, ThemeManager.IconState globalState) {
            super(context, fillResIconDpi, iconBitmapSize);

            Boolean perAppAdaptive = override.getAdaptiveShapeBool();
            boolean effectiveAdaptive = perAppAdaptive != null
                    ? perAppAdaptive : globalState.getApplyAdaptiveShapeDrawer();

            String effectiveShapeKey;
            if (!effectiveAdaptive) {
                effectiveShapeKey = ShapesProvider.NONE_KEY;
            } else if (override.hasShapeOverride()) {
                effectiveShapeKey = override.shapeKey;
            } else {
                effectiveShapeKey = "";
                for (IconShapeModel shape : ShapesProvider.INSTANCE.getIconShapes()) {
                    if (shape.getPathString().equals(globalState.getIconMaskDrawer())) {
                        effectiveShapeKey = shape.getKey();
                        break;
                    }
                }
            }

            IconShapeModel shapeModel = null;
            for (IconShapeModel shape : ShapesProvider.INSTANCE.getIconShapes()) {
                if (shape.getKey().equals(effectiveShapeKey)) {
                    shapeModel = shape;
                    break;
                }
            }

            if (shapeModel != null) {
                String maskPath = shapeModel.getPathString();
                mIconShape = ShapeDelegate.Companion.pickBestShape(maskPath);
                mIconScale = shapeModel.getIconScale();
                mIsNoneShape = maskPath.equals(ShapesProvider.NONE_PATH);
            } else {
                mIconShape = globalState.getIconShapeDrawer();
                mIconScale = globalState.getIconScaleDrawer();
                mIsNoneShape = globalState.getIconMaskDrawer().equals(ShapesProvider.NONE_PATH);
            }

            if (override.hasSizeOverride()) {
                float parsed = 1f;
                try { parsed = Float.parseFloat(override.sizeScale); } catch (NumberFormatException ignored) { }
                mIconSizeScale = Math.max(0.5f, Math.min(1.0f, parsed));
            } else {
                mIconSizeScale = globalState.getIconSizeScaleDrawer();
            }
        }

        @Override
        public Path getShapePath(AdaptiveIconDrawable drawable, Rect iconBounds) {
            if (!Flags.enableLauncherIconShapes()) return super.getShapePath(drawable, iconBounds);
            return mIconShape.getPath(iconBounds);
        }

        @Override
        public float getIconScale() {
            if (!Flags.enableLauncherIconShapes()) return super.getIconScale();
            return mIconScale;
        }

        @Nullable
        @Override
        protected AdaptiveIconDrawable normalizeAndWrapToAdaptiveIcon(
                @Nullable Drawable icon, @NonNull float[] outScale) {
            if (icon == null) return null;
            outScale[0] = mIconSizeScale;
            return wrapToAdaptiveIcon(icon);
        }

        @NonNull
        @Override
        public AdaptiveIconDrawable wrapToAdaptiveIcon(@NonNull Drawable icon) {
            mWrapperBackgroundColor = Color.TRANSPARENT;
            return super.wrapToAdaptiveIcon(icon);
        }

        @NonNull
        @Override
        public BitmapInfo createBadgedIconBitmap(@NonNull Drawable icon,
                @Nullable IconOptions options) {
            if (mIsNoneShape && !(icon instanceof AdaptiveIconDrawable)) {
                android.graphics.Bitmap bitmap = createIconBitmap(icon, mIconSizeScale,
                        MODE_DEFAULT);
                int color = ColorExtractor.findDominantColorByHue(bitmap);
                return BitmapInfo.of(bitmap, color).withFlags(getBitmapFlagOp(options));
            }
            return super.createBadgedIconBitmap(icon, options);
        }

        @Override
        protected void drawAdaptiveIcon(@NonNull Canvas canvas,
                @NonNull AdaptiveIconDrawable drawable, @NonNull Path overridePath) {
            if (!Flags.enableLauncherIconShapes()) {
                super.drawAdaptiveIcon(canvas, drawable, overridePath);
                return;
            }
            if (mIsNoneShape) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
                if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
                return;
            }

            Rect bounds = drawable.getBounds();
            int currentSize = bounds.width();
            int size = mIconBitmapSize;
            int actualOffset = (size - currentSize) / 2;
            int desiredOffset = Math.round(size * (1 - mIconSizeScale) / 2f);

            if (desiredOffset < actualOffset) {
                int compensation = actualOffset - desiredOffset;
                int newSize = size - desiredOffset * 2;

                canvas.save();
                canvas.translate(-compensation, -compensation);
                drawable.setBounds(0, 0, newSize, newSize);
                Path compensatedPath = getShapePath(drawable, drawable.getBounds());

                canvas.clipPath(compensatedPath);
                canvas.drawColor(Color.TRANSPARENT);
                canvas.save();
                canvas.scale(mIconScale, mIconScale,
                        canvas.getWidth() / 2f, canvas.getHeight() / 2f);
                if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
                if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
                canvas.restore();

                drawable.setBounds(bounds);
                canvas.restore();
                return;
            }

            canvas.clipPath(overridePath);
            canvas.drawColor(Color.TRANSPARENT);
            canvas.save();
            canvas.scale(mIconScale, mIconScale,
                    canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            if (drawable.getBackground() != null) drawable.getBackground().draw(canvas);
            if (drawable.getForeground() != null) drawable.getForeground().draw(canvas);
            canvas.restore();
        }
    }
}
