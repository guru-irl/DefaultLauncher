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
 * Each override stores an icon pack package name and optional specific drawable name,
 * allowing users to assign any icon from any installed pack to any app.
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
     * Represents a per-app icon override: which pack and which drawable to use.
     */
    public static class IconOverride {
        public final String packPackage;
        public final String drawableName;

        public IconOverride(String packPackage, String drawableName) {
            this.packPackage = packPackage != null ? packPackage : "";
            this.drawableName = drawableName != null ? drawableName : "";
        }

        /** True if user explicitly chose the system default icon (no pack). */
        public boolean isSystemDefault() {
            return packPackage.isEmpty();
        }

        /** True if user picked a specific drawable (not auto-resolved by component). */
        public boolean hasSpecificDrawable() {
            return !drawableName.isEmpty();
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
        }
        for (Map.Entry<String, IconOverride> e : mDrawerOverrides.entrySet()) {
            hash = 31 * hash + e.getKey().hashCode();
            hash = 31 * hash + e.getValue().packPackage.hashCode();
            hash = 31 * hash + e.getValue().drawableName.hashCode();
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
                map.put(key, new IconOverride(
                        value.optString("pack", ""),
                        value.optString("drawable", "")));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse overrides", e);
        }
        return map;
    }
}
