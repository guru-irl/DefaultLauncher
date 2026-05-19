/*
 * Copyright (C) 2026 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;

/**
 * Service-state introspection helpers used to distinguish a transient lookup
 * failure (system service returning null while recovering from memory pressure
 * after a fullscreen app exits) from a genuine "package is gone" answer.
 *
 * A transient null from PackageManager or AppWidgetManager would otherwise
 * cascade into permanent DB deletion of every launcher item that touches that
 * service. The user-visible result is missing pages and widgets after closing
 * fullscreen games or videos.
 */
public final class ServiceReadiness {

    private static final String TAG = "ServiceReadiness";

    private ServiceReadiness() {}

    /**
     * Performs a secondary, IPC-distinct lookup to confirm a package really is
     * uninstalled before agreeing to delete items that reference it.
     *
     * Returns false only when we have a strong negative signal (PM.getPackageInfo
     * returns NameNotFoundException with the most permissive flags). A null or
     * exception result from an unhealthy service is treated as "probably still
     * installed" so callers defer the deletion rather than commit it.
     */
    public static boolean isPackageProbablyInstalled(Context context, String pkg,
            UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            int flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
                    | PackageManager.MATCH_DISABLED_COMPONENTS;
            context.getPackageManager().getPackageInfo(pkg, flags);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            FileLog.w(TAG, "PM probe threw, deferring deletion for pkg=" + pkg
                    + " err=" + e);
            return true;
        }
    }

    /** Compact state snapshot for diagnostic logs at deletion sites. */
    public static String snapshot(Context context) {
        return "boot=" + Utilities.isBootCompleted();
    }
}
