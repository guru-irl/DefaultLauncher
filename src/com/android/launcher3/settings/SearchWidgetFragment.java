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

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/** Settings sub-screen for the Search pill widget (two color pickers). */
public class SearchWidgetFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.search_widget_preferences, rootKey);

        ColorPickerPreference bgColor = findPreference("pref_search_widget_bg_color");
        if (bgColor != null) {
            bgColor.setColorPrefItem(LauncherPrefs.SEARCH_WIDGET_BG_COLOR,
                    R.color.materialColorSurface);
        }
        ColorPickerPreference textColor = findPreference("pref_search_widget_text_color");
        if (textColor != null) {
            textColor.setColorPrefItem(LauncherPrefs.SEARCH_WIDGET_TEXT_COLOR,
                    R.color.materialColorOnSurface);
        }
    }
}
