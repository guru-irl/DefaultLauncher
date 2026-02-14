/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.icons.pack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;

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

            // If the active pack was uninstalled, revert to system default
            if (isCurrentPack
                    && Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                LauncherPrefs.get(context).put(LauncherPrefs.ICON_PACK, "");
            }

            LauncherAppState.INSTANCE.get(context).getModel().forceReload();
        }
    }
}
