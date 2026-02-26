/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.util.ApiWrapper;

import org.xmlpull.v1.XmlPullParser;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * Extension of {@link IconProvider} with support for overriding theme icons
 */
@LauncherAppSingleton
public class LauncherIconProvider extends IconProvider {

    private static final String TAG_ICON = "icon";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_DRAWABLE = "drawable";

    private static final String TAG = "LIconProvider";
    private static final Map<String, ThemeData> DISABLED_MAP = Collections.emptyMap();

    private Map<String, ThemeData> mThemedIconMap;

    private final ApiWrapper mApiWrapper;
    private final ThemeManager mThemeManager;
    private final IconPackManager mIconPackManager;

    @Inject
    public LauncherIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager,
            ApiWrapper apiWrapper,
            IconPackManager iconPackManager) {
        super(context);
        mThemeManager = themeManager;
        mApiWrapper = apiWrapper;
        mIconPackManager = iconPackManager;
        setIconThemeSupported(mThemeManager.isMonoThemeEnabled());
    }

    /**
     * Enables or disables icon theme support
     */
    public void setIconThemeSupported(boolean isSupported) {
        mThemedIconMap = isSupported && FeatureFlags.USE_LOCAL_ICON_OVERRIDES.get()
                ? null : DISABLED_MAP;
    }

    @Override
    protected ThemeData getThemeDataForPackage(String packageName) {
        return getThemedIconMap().get(packageName);
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentName cn = new ComponentName(info.packageName, info.name);
        PackageManager pm = mContext.getPackageManager();

        // Per-app override takes priority over global pack
        IconOverride override = PerAppIconOverrideManager.getInstance(mContext)
                .getHomeOverride(cn);
        if (override != null) {
            Drawable d = resolveOverride(override, cn, pm, iconDpi,
                    () -> super.getIcon(info, iconDpi));
            if (d != null) return d;
        }

        // Global icon pack
        IconPack pack = mIconPackManager.getCurrentPack();
        if (pack != null) {
            Drawable d = resolveFromPack(pack, cn, pm, iconDpi,
                    () -> super.getIcon(info, iconDpi));
            if (d != null) return d;
        }
        return super.getIcon(info, iconDpi);
    }

    @Override
    public Drawable getIcon(ApplicationInfo info, int iconDpi) {
        PackageManager pm = mContext.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(info.packageName);
        ComponentName cn = (launchIntent != null) ? launchIntent.getComponent() : null;

        // Per-app override takes priority
        if (cn != null) {
            IconOverride override = PerAppIconOverrideManager.getInstance(mContext)
                    .getHomeOverride(cn);
            if (override != null) {
                Drawable d = resolveOverride(override, cn, pm, iconDpi,
                        () -> super.getIcon(info, iconDpi));
                if (d != null) return d;
            }
        }

        // Global icon pack
        IconPack pack = mIconPackManager.getCurrentPack();
        if (pack != null) {
            if (cn != null) {
                Drawable d = resolveFromPack(pack, cn, pm, iconDpi,
                        () -> super.getIcon(info, iconDpi));
                if (d != null) return d;
            } else if (pack.hasFallbackMask()) {
                Drawable original = super.getIcon(info, iconDpi);
                int iconSize = Math.round(48 * iconDpi / 160f);
                Drawable masked = pack.applyFallbackMask(original, iconSize);
                if (masked != null) return IconPackDrawable.wrap(masked);
            }
        }
        return super.getIcon(info, iconDpi);
    }

    @Override
    public void updateSystemState() {
        super.updateSystemState();
        mSystemState += "," + mThemeManager.getIconState().toUniqueId();
        String packId = mIconPackManager.getCurrentPackId();
        if (!packId.isEmpty()) {
            mSystemState += ",iconpack:" + packId;
        }
        int perAppHash = PerAppIconOverrideManager.getInstance(mContext).getOverridesHash();
        if (perAppHash != 17) { // 17 is the hash of empty maps
            mSystemState += ",perapp:" + perAppHash;
        }
    }

    /**
     * Resolve an icon from a per-app override.
     * Returns the system icon if override is system-default, the specific drawable if set,
     * or auto-resolves from the override pack.
     */
    @Nullable
    private Drawable resolveOverride(IconOverride override, ComponentName cn,
            PackageManager pm, int iconDpi, java.util.function.Supplier<Drawable> systemFallback) {
        if (override.isSystemDefault()) {
            return systemFallback.get();
        }
        // Load from the override pack
        IconPack overridePack = mIconPackManager.getPack(override.packPackage);
        if (overridePack == null) return null;

        if (override.hasSpecificDrawable()) {
            Drawable d = overridePack.getDrawableForEntry(override.drawableName, pm);
            if (d != null) return IconPackDrawable.wrap(d);
        }
        // Auto-resolve from the override pack
        return resolveFromPack(overridePack, cn, pm, iconDpi, systemFallback);
    }

    /**
     * Resolve icon from a pack using the standard chain:
     * calendar -> component match -> fallback mask -> null.
     */
    @Nullable
    private Drawable resolveFromPack(IconPack pack, ComponentName cn,
            PackageManager pm, int iconDpi, java.util.function.Supplier<Drawable> systemFallback) {
        Drawable cal = pack.getCalendarIcon(cn, pm);
        if (cal != null) return IconPackDrawable.wrap(cal);

        Drawable icon = pack.getIconForComponent(cn, pm);
        if (icon != null) return IconPackDrawable.wrap(icon);

        if (pack.hasFallbackMask()) {
            Drawable original = systemFallback.get();
            int iconSize = Math.round(48 * iconDpi / 160f);
            Drawable masked = pack.applyFallbackMask(original, iconSize);
            if (masked != null) return IconPackDrawable.wrap(masked);
        }
        return null;
    }

    @Override
    protected String getApplicationInfoHash(@NonNull ApplicationInfo appInfo) {
        return mApiWrapper.getApplicationInfoHash(appInfo);
    }

    @Nullable
    @Override
    protected Drawable loadAppInfoIcon(ApplicationInfo info, Resources resources, int density) {
        // Tries to load the round icon res, if the app defines it as an adaptive icon
        if (mThemeManager.getIconShape() instanceof ShapeDelegate.Circle) {
            int roundIconRes = mApiWrapper.getRoundIconRes(info);
            if (roundIconRes != 0 && roundIconRes != info.icon) {
                try {
                    Drawable d = resources.getDrawableForDensity(roundIconRes, density);
                    if (d instanceof AdaptiveIconDrawable) {
                        return d;
                    }
                } catch (Resources.NotFoundException exc) { }
            }
        }
        return super.loadAppInfoIcon(info, resources, density);
    }

    private Map<String, ThemeData> getThemedIconMap() {
        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }
        ArrayMap<String, ThemeData> map = new ArrayMap<>();
        Resources res = mContext.getResources();
        try (XmlResourceParser parser = res.getXml(R.xml.grayscale_icon_map)) {
            final int depth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT);

            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                if (TAG_ICON.equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, ATTR_PACKAGE);
                    int iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0);
                    if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                        map.put(pkg, new ThemeData(res, iconId));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to parse icon map", e);
        }
        mThemedIconMap = map;
        return mThemedIconMap;
    }
}
