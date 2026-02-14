/*
 * Copyright (C) 2024 DefaultLauncher Contributors
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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Singleton manager for discovering, selecting, and caching icon packs.
 * Discovers installed ADW-format icon packs via standard intent queries.
 */
@LauncherAppSingleton
public class IconPackManager {

    private static final String[] ICON_PACK_INTENTS = {
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
    };

    private final Context mContext;
    private final LauncherPrefs mPrefs;

    private Map<String, IconPack> mInstalledPacks;
    private IconPack mCurrentPack;
    private boolean mCurrentPackResolved = false;

    private static final String TAG = "IconPackManager";

    @Inject
    public IconPackManager(@ApplicationContext Context context, LauncherPrefs prefs) {
        mContext = context;
        mPrefs = prefs;

        // Register broadcast receiver for icon pack install/uninstall/update
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(new IconPackReceiver(this), filter,
                Context.RECEIVER_EXPORTED);
    }

    /** Discover all installed icon packs. Result is cached until invalidated. */
    public synchronized Map<String, IconPack> getInstalledPacks() {
        if (mInstalledPacks != null) return mInstalledPacks;

        PackageManager pm = mContext.getPackageManager();
        Map<String, IconPack> packs = new LinkedHashMap<>();

        for (String action : ICON_PACK_INTENTS) {
            Intent intent = new Intent(action);
            try {
                List<ResolveInfo> infos = pm.queryIntentActivities(intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA));
                for (ResolveInfo ri : infos) {
                    String pkg = ri.activityInfo.packageName;
                    if (!packs.containsKey(pkg) && !pkg.equals(mContext.getPackageName())) {
                        CharSequence label = ri.loadLabel(pm);
                        packs.put(pkg, new IconPack(pkg, label));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query icon packs for " + action, e);
            }
        }

        mInstalledPacks = packs;
        return mInstalledPacks;
    }

    /** Get the currently selected icon pack, or null for system default. */
    @Nullable
    public synchronized IconPack getCurrentPack() {
        if (mCurrentPackResolved) return mCurrentPack;

        String pkg = mPrefs.get(LauncherPrefs.ICON_PACK);
        if (pkg == null || pkg.isEmpty()) {
            mCurrentPack = null;
        } else {
            Map<String, IconPack> packs = getInstalledPacks();
            mCurrentPack = packs.get(pkg);
            if (mCurrentPack != null) {
                mCurrentPack.ensureParsed(mContext.getPackageManager());
            }
        }
        mCurrentPackResolved = true;
        return mCurrentPack;
    }

    /** Unique ID for cache key. Empty string = no pack. */
    public String getCurrentPackId() {
        IconPack pack = getCurrentPack();
        return pack != null ? pack.packageName : "";
    }

    /** Check if a package is a known icon pack. */
    public boolean isIconPack(String packageName) {
        return getInstalledPacks().containsKey(packageName);
    }

    /** Clear cached state. Call on pack change or pack uninstall. */
    public synchronized void invalidate() {
        mInstalledPacks = null;
        mCurrentPack = null;
        mCurrentPackResolved = false;
    }

    Context getContext() {
        return mContext;
    }
}
