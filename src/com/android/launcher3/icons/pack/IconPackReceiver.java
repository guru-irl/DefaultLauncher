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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.icons.DrawerIconResolver;

/**
 * Handles icon pack install/uninstall/update broadcasts.
 * Auto-refreshes icons when the active pack changes.
 */
public class IconPackReceiver extends BroadcastReceiver {

    private final IconPackManager mManager;

    IconPackReceiver(IconPackManager manager) {
        mManager = manager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String pkg = intent.getData() != null
                ? intent.getData().getSchemeSpecificPart() : null;
        if (pkg == null) return;

        boolean isIconPack = mManager.isIconPack(pkg);
        boolean isCurrentPack = pkg.equals(mManager.getCurrentPackId());

        if (isIconPack || isCurrentPack) {
            mManager.invalidate();
            DrawerIconResolver.getInstance().invalidate();

            // If the active pack was uninstalled, revert to system default
            if (isCurrentPack
                    && Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                LauncherPrefs.get(context).put(LauncherPrefs.ICON_PACK, "");
            }

            LauncherAppState.INSTANCE.get(context).getModel().forceReload();
        }
    }
}
