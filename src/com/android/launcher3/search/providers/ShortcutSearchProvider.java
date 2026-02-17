/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.search.providers;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searches app shortcuts via LauncherApps.ShortcutQuery.
 * Requires the app to be the default launcher (HOME category).
 */
public class ShortcutSearchProvider implements SearchProvider<ShortcutResult> {

    private static final String TAG = "ShortcutSearchProvider";
    private static final int MAX_RESULTS = 5;

    private final Context mContext;
    private final LauncherApps mLauncherApps;
    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    public ShortcutSearchProvider(Context context) {
        mContext = context.getApplicationContext();
        mLauncherApps = context.getSystemService(LauncherApps.class);
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<ShortcutResult>> callback) {
        mCancelled = false;
        Executors.MODEL_EXECUTOR.execute(() -> {
            List<ShortcutResult> results = searchShortcuts(query);
            if (!mCancelled) {
                mResultHandler.post(() -> callback.accept(results));
            }
        });
    }

    @Override
    public void cancel() {
        mCancelled = true;
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "shortcuts";
    }

    @Override
    public int minQueryLength() {
        return 3;
    }

    private List<ShortcutResult> searchShortcuts(String query) {
        List<ShortcutResult> results = new ArrayList<>();

        if (!mLauncherApps.hasShortcutHostPermission()) {
            return results;
        }

        try {
            String queryLower = query.toLowerCase();
            LauncherApps.ShortcutQuery shortcutQuery = new LauncherApps.ShortcutQuery();
            shortcutQuery.setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);

            List<ShortcutInfo> shortcuts = mLauncherApps.getShortcuts(
                    shortcutQuery, Process.myUserHandle());
            if (shortcuts == null) return results;

            PackageManager pm = mContext.getPackageManager();

            for (ShortcutInfo shortcut : shortcuts) {
                if (mCancelled || results.size() >= MAX_RESULTS) break;

                CharSequence label = shortcut.getShortLabel();
                if (label == null) label = shortcut.getLongLabel();
                if (label == null) continue;

                if (label.toString().toLowerCase().contains(queryLower)) {
                    String appName;
                    try {
                        appName = pm.getApplicationLabel(
                                pm.getApplicationInfo(shortcut.getPackage(), 0)).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        appName = shortcut.getPackage();
                    }
                    results.add(new ShortcutResult(shortcut, appName));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error searching shortcuts", e);
        }

        return results;
    }
}
