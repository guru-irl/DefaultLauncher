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
import android.content.res.Resources;
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
 * Resolves home screen icons on-demand when a per-app override includes
 * shape, size, or adaptive rendering customization. Uses an LRU cache
 * to avoid re-resolving icons on every bind.
 *
 * Only returns an icon when a per-app render override (shape/size/adaptive)
 * is set; per-app icon pack overrides without render changes are handled
 * by the main icon cache.
 */
public class PerAppHomeIconResolver {

    private static final int CACHE_SIZE = 100;
    private static final int FALLBACK_ICON_SIZE = 192;

    private static volatile PerAppHomeIconResolver sInstance;

    private final LruCache<ComponentName, BitmapInfo> mCache = new LruCache<>(CACHE_SIZE);

    private PerAppHomeIconResolver() { }

    public static PerAppHomeIconResolver getInstance() {
        if (sInstance == null) {
            synchronized (PerAppHomeIconResolver.class) {
                if (sInstance == null) {
                    sInstance = new PerAppHomeIconResolver();
                }
            }
        }
        return sInstance;
    }

    /**
     * Get a home icon with per-app render overrides, or null if no render override is needed.
     */
    @Nullable
    public FastBitmapDrawable getHomeIcon(ItemInfoWithIcon info, Context context, int flags) {
        ComponentName cn = info.getTargetComponent();
        if (cn == null) return null;

        PerAppIconOverrideManager overrideMgr = PerAppIconOverrideManager.getInstance(context);
        IconOverride override = overrideMgr.getHomeOverride(cn);
        if (override == null) return null;

        Path badgeShape = Utilities.getIconShapeOrNull(context);

        // Per-app render overrides are not cached (each can differ)
        PackageManager pm = context.getPackageManager();
        Drawable icon = resolveIcon(cn, override, context, pm);
        if (icon == null) return null;

        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        ThemeManager.IconState state = ThemeManager.INSTANCE.get(context).getIconState();
        BitmapInfo bitmapInfo;
        try (PerAppIconFactory factory = new PerAppIconFactory(
                context, idp.fillResIconDpi, idp.iconBitmapSize, override, state)) {
            bitmapInfo = factory.createBadgedIconBitmap(icon);
        }

        FastBitmapDrawable drawable = bitmapInfo.newIcon(context, flags, badgeShape);
        drawable.setIsDisabled(info.isDisabled());
        return drawable;
    }

    /** Resolve the icon drawable from the override's pack settings, or fall back to system. */
    private Drawable resolveIcon(ComponentName cn, IconOverride override,
            Context context, PackageManager pm) {
        if (override.isSystemDefault() && !override.hasSpecificDrawable()) {
            try {
                return pm.getActivityIcon(cn);
            } catch (PackageManager.NameNotFoundException ignored) { }
            return null;
        }

        if (override.hasPackOverride()) {
            IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
            IconPack pack = mgr.getPack(override.packPackage);
            if (pack != null) {
                if (override.hasSpecificDrawable()) {
                    Drawable d = pack.getDrawableForEntry(override.drawableName, pm);
                    if (d != null) return d;
                }
                Drawable d = pack.getCalendarIcon(cn, pm);
                if (d != null) return d;
                d = pack.getIconForComponent(cn, pm);
                if (d != null) return d;
                if (pack.hasFallbackMask()) {
                    try {
                        Drawable original = pm.getActivityIcon(cn);
                        d = pack.applyFallbackMask(original, FALLBACK_ICON_SIZE);
                        if (d != null) return d;
                    } catch (PackageManager.NameNotFoundException ignored) { }
                }
            }
        }

        // Fall back to global home pack resolution
        IconPackManager mgr = LauncherComponentProvider.get(context).getIconPackManager();
        IconPack pack = mgr.getCurrentPack();
        if (pack != null) {
            Drawable d = pack.getCalendarIcon(cn, pm);
            if (d != null) return d;
            d = pack.getIconForComponent(cn, pm);
            if (d != null) return d;
        }

        try {
            return pm.getActivityIcon(cn);
        } catch (PackageManager.NameNotFoundException ignored) { }
        return null;
    }

    /** Clear the cache. Call when home screen per-app overrides change. */
    public void invalidate() {
        mCache.evictAll();
    }

    /**
     * Icon factory configured with per-app shape, scale, and size settings.
     * Falls back to global home values for unset fields.
     */
    public static class PerAppIconFactory extends BaseIconFactory {

        private final float mIconScale;
        private final float mIconSizeScale;
        private final boolean mIsNoneShape;
        private final ShapeDelegate mIconShape;

        public PerAppIconFactory(Context context, int fillResIconDpi, int iconBitmapSize,
                IconOverride override, ThemeManager.IconState globalState) {
            super(context, fillResIconDpi, iconBitmapSize);

            // Determine effective adaptive setting
            Boolean perAppAdaptive = override.getAdaptiveShapeBool();
            boolean effectiveAdaptive = perAppAdaptive != null
                    ? perAppAdaptive : globalState.getApplyAdaptiveShape();

            // Determine effective shape
            String effectiveShapeKey;
            if (!effectiveAdaptive) {
                effectiveShapeKey = ShapesProvider.NONE_KEY;
            } else if (override.hasShapeOverride()) {
                effectiveShapeKey = override.shapeKey;
            } else {
                // Find the key from global state by matching the mask path
                effectiveShapeKey = "";
                for (IconShapeModel shape : ShapesProvider.INSTANCE.getIconShapes()) {
                    if (shape.getPathString().equals(globalState.getIconMask())) {
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
                mIconShape = globalState.getIconShape();
                mIconScale = globalState.getIconScale();
                mIsNoneShape = globalState.getIconMask().equals(ShapesProvider.NONE_PATH);
            }

            // Determine effective size
            if (override.hasSizeOverride()) {
                float parsed = 1f;
                try { parsed = Float.parseFloat(override.sizeScale); } catch (NumberFormatException ignored) { }
                mIconSizeScale = Math.max(0.5f, Math.min(1.0f, parsed));
            } else {
                mIconSizeScale = globalState.getIconSizeScale();
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
