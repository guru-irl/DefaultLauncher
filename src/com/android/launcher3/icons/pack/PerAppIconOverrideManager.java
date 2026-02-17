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
package com.android.launcher3.icons.pack;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages per-app icon overrides stored in SharedPreferences as JSON maps.
 * Supports independent home screen and app drawer overrides.
 *
 * Each override stores an icon pack package name, optional specific drawable name,
 * and optional shape/size/adaptive overrides for per-app rendering customization.
 */
public class PerAppIconOverrideManager {

    private static final String TAG = "PerAppIconOverride";
    private static final String PREFS_FILE = "per_app_icon_overrides";
    private static final String KEY_HOME = "home_overrides";
    private static final String KEY_DRAWER = "drawer_overrides";

    private static volatile PerAppIconOverrideManager sInstance;

    private final SharedPreferences mPrefs;
    private Map<String, IconOverride> mHomeOverrides;
    private Map<String, IconOverride> mDrawerOverrides;

    /**
     * Represents a per-app icon override: which pack and which drawable to use,
     * plus optional shape, size, and adaptive icon shape overrides.
     *
     * Uses sentinel constants instead of empty strings to disambiguate
     * "follow global" from "system default" and other states.
     */
    public static class IconOverride {

        /** Sentinel: field follows the corresponding global setting. */
        public static final String FOLLOW_GLOBAL = "__follow_global__";

        /** Source of the icon pack. */
        public enum PackSource {
            /** Follow global pack setting (no pack override). */
            FOLLOW_GLOBAL("__follow_global__"),
            /** User explicitly chose system default icon. */
            SYSTEM_DEFAULT("__system_default__"),
            /** User picked a specific icon pack. */
            CUSTOM(null);

            public final String key;
            PackSource(String key) { this.key = key; }
        }

        /** Per-app adaptive shape behavior. */
        public enum AdaptiveOverride {
            /** Follow global adaptive setting. */
            FOLLOW_GLOBAL("__follow_global__"),
            /** Force adaptive shaping ON. */
            ON("true"),
            /** Force adaptive shaping OFF. */
            OFF("false");

            public final String key;
            AdaptiveOverride(String key) { this.key = key; }

            /** Convert to nullable Boolean: null=follow global, true=on, false=off. */
            @Nullable
            public Boolean toBool() {
                if (this == ON) return true;
                if (this == OFF) return false;
                return null;
            }
        }

        public final String packPackage;
        public final String drawableName;
        public final String shapeKey;
        public final String sizeScale;
        public final String adaptiveShape;

        /** Pack-only override: follow global for all render fields. */
        public IconOverride(String packPackage, String drawableName) {
            this(packPackage, drawableName,
                    FOLLOW_GLOBAL, FOLLOW_GLOBAL, AdaptiveOverride.FOLLOW_GLOBAL.key);
        }

        /** Convenience: create from PackSource enum with follow-global render. */
        public IconOverride(PackSource source) {
            this(source == PackSource.SYSTEM_DEFAULT
                            ? PackSource.SYSTEM_DEFAULT.key
                            : PackSource.FOLLOW_GLOBAL.key,
                    FOLLOW_GLOBAL);
        }

        /** Full constructor. */
        public IconOverride(String packPackage, String drawableName,
                String shapeKey, String sizeScale, String adaptiveShape) {
            this.packPackage = packPackage != null ? packPackage : PackSource.FOLLOW_GLOBAL.key;
            this.drawableName = drawableName != null ? drawableName : FOLLOW_GLOBAL;
            this.shapeKey = shapeKey != null ? shapeKey : FOLLOW_GLOBAL;
            this.sizeScale = sizeScale != null ? sizeScale : FOLLOW_GLOBAL;
            this.adaptiveShape = adaptiveShape != null ? adaptiveShape : AdaptiveOverride.FOLLOW_GLOBAL.key;
        }

        /** Get the pack resolution strategy. */
        public PackSource getPackSource() {
            if (PackSource.SYSTEM_DEFAULT.key.equals(packPackage)) return PackSource.SYSTEM_DEFAULT;
            if (PackSource.FOLLOW_GLOBAL.key.equals(packPackage)) return PackSource.FOLLOW_GLOBAL;
            return PackSource.CUSTOM;
        }

        /** Get the adaptive shape override. */
        public AdaptiveOverride getAdaptiveOverride() {
            if (AdaptiveOverride.ON.key.equals(adaptiveShape)) return AdaptiveOverride.ON;
            if (AdaptiveOverride.OFF.key.equals(adaptiveShape)) return AdaptiveOverride.OFF;
            return AdaptiveOverride.FOLLOW_GLOBAL;
        }

        /** True if user explicitly chose the system default icon (no pack). */
        public boolean isSystemDefault() { return getPackSource() == PackSource.SYSTEM_DEFAULT; }

        /** True if user picked a specific icon pack. */
        public boolean hasPackOverride() { return getPackSource() == PackSource.CUSTOM; }

        /** True if pack follows global setting. */
        public boolean isFollowGlobalPack() { return getPackSource() == PackSource.FOLLOW_GLOBAL; }

        /** True if user picked a specific drawable (not auto-resolved by component). */
        public boolean hasSpecificDrawable() { return !FOLLOW_GLOBAL.equals(drawableName); }

        /** True if a per-app shape override is set. */
        public boolean hasShapeOverride() { return !FOLLOW_GLOBAL.equals(shapeKey); }

        /** True if a per-app size override is set. */
        public boolean hasSizeOverride() { return !FOLLOW_GLOBAL.equals(sizeScale); }

        /** True if a per-app adaptive shape override is set. */
        public boolean hasAdaptiveOverride() {
            return getAdaptiveOverride() != AdaptiveOverride.FOLLOW_GLOBAL;
        }

        /** Returns null (follow global), true, or false for the adaptive shape setting. */
        @Nullable
        public Boolean getAdaptiveShapeBool() {
            return getAdaptiveOverride().toBool();
        }

        /** True if any render-affecting override (shape, size, or adaptive) is set. */
        public boolean hasAnyRenderOverride() {
            return hasShapeOverride() || hasSizeOverride() || hasAdaptiveOverride();
        }

        /** Create a copy with only pack/drawable fields, clearing render overrides. */
        public IconOverride withoutRenderOverrides() {
            return new IconOverride(packPackage, drawableName,
                    FOLLOW_GLOBAL, FOLLOW_GLOBAL, AdaptiveOverride.FOLLOW_GLOBAL.key);
        }

        /** Create a copy with updated render fields. */
        public IconOverride withRenderOverrides(String shapeKey, String sizeScale,
                String adaptiveShape) {
            return new IconOverride(packPackage, drawableName, shapeKey, sizeScale,
                    adaptiveShape);
        }
    }

    private PerAppIconOverrideManager(Context context) {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    public static PerAppIconOverrideManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PerAppIconOverrideManager.class) {
                if (sInstance == null) {
                    sInstance = new PerAppIconOverrideManager(context);
                }
            }
        }
        return sInstance;
    }

    /** Get the home screen override for a component, or null if none set. */
    @Nullable
    public synchronized IconOverride getHomeOverride(ComponentName cn) {
        ensureLoaded();
        return mHomeOverrides.get(cn.flattenToString());
    }

    /** Get the app drawer override for a component, or null if none set. */
    @Nullable
    public synchronized IconOverride getDrawerOverride(ComponentName cn) {
        ensureLoaded();
        return mDrawerOverrides.get(cn.flattenToString());
    }

    /** Set or remove the home screen override. Pass null to remove. */
    public synchronized void setHomeOverride(ComponentName cn,
            @Nullable IconOverride override) {
        ensureLoaded();
        String key = cn.flattenToString();
        if (override == null) {
            mHomeOverrides.remove(key);
        } else {
            mHomeOverrides.put(key, override);
        }
        persist(KEY_HOME, mHomeOverrides);
    }

    /** Set or remove the app drawer override. Pass null to remove. */
    public synchronized void setDrawerOverride(ComponentName cn,
            @Nullable IconOverride override) {
        ensureLoaded();
        String key = cn.flattenToString();
        if (override == null) {
            mDrawerOverrides.remove(key);
        } else {
            mDrawerOverrides.put(key, override);
        }
        persist(KEY_DRAWER, mDrawerOverrides);
    }

    /** Remove both home and drawer overrides for a component. */
    public synchronized void clearOverrides(ComponentName cn) {
        ensureLoaded();
        String key = cn.flattenToString();
        mHomeOverrides.remove(key);
        mDrawerOverrides.remove(key);
        mPrefs.edit()
                .putString(KEY_HOME, toJson(mHomeOverrides))
                .putString(KEY_DRAWER, toJson(mDrawerOverrides))
                .apply();
    }

    /** Clear all home screen overrides. */
    public synchronized void clearAllHomeOverrides() {
        ensureLoaded();
        mHomeOverrides.clear();
        persist(KEY_HOME, mHomeOverrides);
    }

    /** Clear all app drawer overrides. */
    public synchronized void clearAllDrawerOverrides() {
        ensureLoaded();
        mDrawerOverrides.clear();
        persist(KEY_DRAWER, mDrawerOverrides);
    }

    /** True if any home screen per-app overrides exist. */
    public synchronized boolean hasAnyHomeOverrides() {
        ensureLoaded();
        return !mHomeOverrides.isEmpty();
    }

    /** True if any app drawer per-app overrides exist. */
    public synchronized boolean hasAnyDrawerOverrides() {
        ensureLoaded();
        return !mDrawerOverrides.isEmpty();
    }

    /**
     * Returns a deterministic hash of all overrides for cache invalidation.
     * Changes when any override is added, removed, or modified.
     */
    public synchronized int getOverridesHash() {
        ensureLoaded();
        int hash = 17;
        for (Map.Entry<String, IconOverride> e : mHomeOverrides.entrySet()) {
            hash = 31 * hash + e.getKey().hashCode();
            hash = 31 * hash + e.getValue().packPackage.hashCode();
            hash = 31 * hash + e.getValue().drawableName.hashCode();
            hash = 31 * hash + e.getValue().shapeKey.hashCode();
            hash = 31 * hash + e.getValue().sizeScale.hashCode();
            hash = 31 * hash + e.getValue().adaptiveShape.hashCode();
        }
        for (Map.Entry<String, IconOverride> e : mDrawerOverrides.entrySet()) {
            hash = 31 * hash + e.getKey().hashCode();
            hash = 31 * hash + e.getValue().packPackage.hashCode();
            hash = 31 * hash + e.getValue().drawableName.hashCode();
            hash = 31 * hash + e.getValue().shapeKey.hashCode();
            hash = 31 * hash + e.getValue().sizeScale.hashCode();
            hash = 31 * hash + e.getValue().adaptiveShape.hashCode();
        }
        return hash;
    }

    private void ensureLoaded() {
        if (mHomeOverrides == null) {
            mHomeOverrides = fromJson(mPrefs.getString(KEY_HOME, "{}"));
        }
        if (mDrawerOverrides == null) {
            mDrawerOverrides = fromJson(mPrefs.getString(KEY_DRAWER, "{}"));
        }
    }

    private void persist(String key, Map<String, IconOverride> map) {
        mPrefs.edit().putString(key, toJson(map)).apply();
    }

    private static String toJson(Map<String, IconOverride> map) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, IconOverride> entry : map.entrySet()) {
                JSONObject value = new JSONObject();
                value.put("pack", entry.getValue().packPackage);
                value.put("drawable", entry.getValue().drawableName);
                value.put("shape", entry.getValue().shapeKey);
                value.put("size", entry.getValue().sizeScale);
                value.put("adaptive", entry.getValue().adaptiveShape);
                json.put(entry.getKey(), value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize overrides", e);
        }
        return json.toString();
    }

    private static Map<String, IconOverride> fromJson(String jsonStr) {
        Map<String, IconOverride> map = new HashMap<>();
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject value = json.getJSONObject(key);
                String pack = value.optString("pack", "");
                String drawable = value.optString("drawable", "");
                String shape = value.optString("shape", "");
                String size = value.optString("size", "");
                String adaptive = value.optString("adaptive", "");

                // Migrate legacy empty strings to sentinels
                if (pack.isEmpty()) {
                    boolean hasOtherData = !drawable.isEmpty() || !shape.isEmpty()
                            || !size.isEmpty() || !adaptive.isEmpty();
                    pack = hasOtherData
                            ? IconOverride.PackSource.FOLLOW_GLOBAL.key
                            : IconOverride.PackSource.SYSTEM_DEFAULT.key;
                }
                if (drawable.isEmpty()) drawable = IconOverride.FOLLOW_GLOBAL;
                if (shape.isEmpty()) shape = IconOverride.FOLLOW_GLOBAL;
                if (size.isEmpty()) size = IconOverride.FOLLOW_GLOBAL;
                if (adaptive.isEmpty()) adaptive = IconOverride.AdaptiveOverride.FOLLOW_GLOBAL.key;

                map.put(key, new IconOverride(pack, drawable, shape, size, adaptive));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse overrides", e);
        }
        return map;
    }
}
