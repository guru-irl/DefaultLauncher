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
package com.android.launcher3.search.result;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

/**
 * A search result wrapping an app shortcut from LauncherApps.
 */
public class ShortcutResult implements Launchable {

    public final ShortcutInfo shortcutInfo;
    public final String appName;

    public ShortcutResult(ShortcutInfo shortcutInfo, String appName) {
        this.shortcutInfo = shortcutInfo;
        this.appName = appName;
    }

    @Override
    public boolean launch(Context context) {
        try {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            launcherApps.startShortcut(shortcutInfo, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLabel() {
        CharSequence label = shortcutInfo.getShortLabel();
        if (label == null) label = shortcutInfo.getLongLabel();
        return label != null ? label.toString() : "";
    }

    @Override
    public Drawable getIcon(Context context) {
        try {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            return launcherApps.getShortcutIconDrawable(shortcutInfo,
                    context.getResources().getDisplayMetrics().densityDpi);
        } catch (Exception e) {
            return null;
        }
    }
}
