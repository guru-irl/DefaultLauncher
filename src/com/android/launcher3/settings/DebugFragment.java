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
package com.android.launcher3.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

/**
 * Fragment for the Debug settings sub-page.
 * Contains: theme color debug swatches.
 */
public class DebugFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.debug_preferences, rootKey);

        Preference versionPref = findPreference("pref_app_version");
        if (versionPref != null) {
            versionPref.setSummary(BuildConfig.VERSION_NAME
                    + " (" + BuildConfig.VERSION_CODE + ")");
        }

        Preference restartPref = findPreference("pref_restart_launcher");
        if (restartPref != null) {
            restartPref.setOnPreferenceClickListener(pref -> {
                android.os.Process.killProcess(android.os.Process.myPid());
                return true;
            });
        }
    }
}
