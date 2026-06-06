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
package com.android.launcher3.testing;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.util.Executors;

/**
 * DEBUG-only broadcast receiver that resets the workspace to the canonical
 * two-icon test fixture (Settings at (0,2), Chrome at (1,2)).
 *
 * <p>Used by {@code tests-e2e/lib/adb_setup.py::seed_workspace()} to give
 * every test session a deterministic starting state without dragging icons.
 *
 * <p>Activated by:
 * {@code adb shell am broadcast -n com.guru.defaultlauncher/.testing.WorkspaceSeedReceiver
 *     -a com.guru.defaultlauncher.test.SEED_WORKSPACE}
 *
 * <p>Only present in debug builds. The receiver is not exported to other apps;
 * it is invoked via {@code -n} (explicit component name) which bypasses the
 * exported flag when the caller has the same UID (shell/root always qualifies).
 */
public class WorkspaceSeedReceiver extends BroadcastReceiver {

    public static final String ACTION_SEED_WORKSPACE =
            "com.guru.defaultlauncher.test.SEED_WORKSPACE";

    /** Toggles WidgetInflater.sSimulateNullProvider (debug builds only). */
    public static final String ACTION_SIMULATE_NULL_PROVIDER =
            "com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER";

    /** Places the Danfo clock widget at (0,0), span 2x2 (debug builds only). */
    public static final String ACTION_PLACE_CLOCK_WIDGET =
            "com.guru.defaultlauncher.test.PLACE_CLOCK_WIDGET";

    private static final String CLOCK_PROVIDER =
            "com.guru.defaultlauncher/#custom-widget-com.android.launcher3.widget.custom.DanfoClockWidgetPlugin";

    private static final String SETTINGS_INTENT =
            "#Intent;action=android.intent.action.MAIN;"
            + "category=android.intent.category.LAUNCHER;"
            + "launchFlags=0x10200000;"
            + "component=com.android.settings/.Settings;"
            + "end";

    // Try com.android.chrome first (AOSP/Pixel), fall back to com.google.android.chrome
    private static final String CHROME_INTENT =
            "#Intent;action=android.intent.action.MAIN;"
            + "category=android.intent.category.LAUNCHER;"
            + "launchFlags=0x10200000;"
            + "package=com.android.chrome;"
            + "component=com.android.chrome/com.google.android.apps.chrome.Main;"
            + "end";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) return;

        if (ACTION_SIMULATE_NULL_PROVIDER.equals(intent.getAction())) {
            boolean enable = !intent.getBooleanExtra("disable", false);
            com.android.launcher3.widget.WidgetInflater.sSimulateNullProvider = enable;
            android.util.Log.d("WorkspaceSeedReceiver", "sSimulateNullProvider = " + enable);
            return;
        }

        if (ACTION_PLACE_CLOCK_WIDGET.equals(intent.getAction())) {
            Executors.MODEL_EXECUTOR.execute(() -> {
                com.android.launcher3.model.ModelDbController db =
                        LauncherAppState.getInstance(context).getModel().getModelDbController();
                android.content.ComponentName cn =
                        android.content.ComponentName.unflattenFromString(CLOCK_PROVIDER);
                int widgetId = com.android.launcher3.widget.custom.CustomWidgetManager.INSTANCE
                        .get(context).allocateCustomAppWidgetId(cn);

                // Idempotency: drop any prior clock-widget row so a repeated
                // placement (test ran twice without a reset) cannot hit the
                // UNIQUE constraint on the fixed _ID.
                db.delete("_id = 200", null);

                ContentValues cv = new ContentValues();
                cv.put(LauncherSettings.Favorites._ID, 200);
                cv.put(LauncherSettings.Favorites.CONTAINER,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP);
                cv.put(LauncherSettings.Favorites.SCREEN, 0);
                cv.put(LauncherSettings.Favorites.CELLX, 0);
                cv.put(LauncherSettings.Favorites.CELLY, 0);
                cv.put(LauncherSettings.Favorites.SPANX, 2);
                cv.put(LauncherSettings.Favorites.SPANY, 2);
                cv.put(LauncherSettings.Favorites.ITEM_TYPE,
                        LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET);
                cv.put(LauncherSettings.Favorites.APPWIDGET_ID, widgetId);
                cv.put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, CLOCK_PROVIDER);
                cv.put(LauncherSettings.Favorites.PROFILE_ID, 0);
                cv.put(LauncherSettings.Favorites.RANK, 0);
                db.insert(cv);

                Executors.MAIN_EXECUTOR.execute(
                        () -> LauncherAppState.getInstance(context).getModel().forceReload());
            });
            return;
        }

        if (!ACTION_SEED_WORKSPACE.equals(intent.getAction())) return;

        // Use MODEL_EXECUTOR + ModelDbController to bypass the ContentProvider
        // same-process restriction (LauncherProvider.executeControllerTask throws
        // IllegalArgumentException for same-UID callers).
        Executors.MODEL_EXECUTOR.execute(() -> {
            com.android.launcher3.model.ModelDbController db =
                    LauncherAppState.getInstance(context).getModel().getModelDbController();

            // Remove all existing desktop workspace items to avoid accumulation.
            db.delete("container = " + LauncherSettings.Favorites.CONTAINER_DESKTOP, null);

            // Insert Settings at (0, 2) — primary test anchor.
            db.insert(buildShortcut(100, "Settings", SETTINGS_INTENT, 0, 2));

            // Insert Chrome at (1, 2) — adjacent to Settings for folder creation tests.
            db.insert(buildShortcut(101, "Chrome", CHROME_INTENT, 1, 2));

            // Reload the launcher model so the new items appear immediately.
            Executors.MAIN_EXECUTOR.execute(
                    () -> LauncherAppState.getInstance(context).getModel().forceReload());
        });
    }

    private static ContentValues buildShortcut(
            int id, String title, String intentStr, int cellX, int cellY) {
        ContentValues cv = new ContentValues();
        cv.put(LauncherSettings.Favorites._ID, id);
        cv.put(LauncherSettings.Favorites.TITLE, title);
        cv.put(LauncherSettings.Favorites.INTENT, intentStr);
        cv.put(LauncherSettings.Favorites.CONTAINER,
                LauncherSettings.Favorites.CONTAINER_DESKTOP);
        cv.put(LauncherSettings.Favorites.SCREEN, 0);
        cv.put(LauncherSettings.Favorites.CELLX, cellX);
        cv.put(LauncherSettings.Favorites.CELLY, cellY);
        cv.put(LauncherSettings.Favorites.SPANX, 1);
        cv.put(LauncherSettings.Favorites.SPANY, 1);
        cv.put(LauncherSettings.Favorites.ITEM_TYPE,
                LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
        cv.put(LauncherSettings.Favorites.PROFILE_ID, 0);
        cv.put(LauncherSettings.Favorites.RANK, 0);
        return cv;
    }
}
