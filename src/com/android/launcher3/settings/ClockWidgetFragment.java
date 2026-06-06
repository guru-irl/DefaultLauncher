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

import androidx.preference.ListPreference;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/** Settings sub-screen for the Danfo clock widget. */
public class ClockWidgetFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.clock_widget_preferences, rootKey);

        bindList("pref_clock_alignment", LauncherPrefs.CLOCK_ALIGNMENT);
        bindList("pref_clock_time_format", LauncherPrefs.CLOCK_TIME_FORMAT);

        ListPreference colorMode = findPreference("pref_clock_color_mode");
        if (colorMode != null) {
            colorMode.setValue(LauncherPrefs.get(getContext()).get(LauncherPrefs.CLOCK_COLOR_MODE));
            colorMode.setOnPreferenceChangeListener((p, v) -> {
                LauncherPrefs.get(getContext()).put(LauncherPrefs.CLOCK_COLOR_MODE, (String) v);
                ((ListPreference) p).setValue((String) v);
                updateColorPickerVisibility((String) v);
                return false; // persisted via LauncherPrefs above
            });
        }

        ColorPickerPreference timeColor = findPreference("pref_clock_time_color");
        if (timeColor != null) {
            timeColor.setColorPrefItem(LauncherPrefs.CLOCK_TIME_COLOR, 0,
                    android.graphics.Color.WHITE);
        }
        ColorPickerPreference dateColor = findPreference("pref_clock_date_color");
        if (dateColor != null) {
            dateColor.setColorPrefItem(LauncherPrefs.CLOCK_DATE_COLOR, 0,
                    android.graphics.Color.WHITE);
        }

        updateColorPickerVisibility(
                LauncherPrefs.get(getContext()).get(LauncherPrefs.CLOCK_COLOR_MODE));
    }

    private void bindList(String key, ConstantItem<String> item) {
        ListPreference pref = findPreference(key);
        if (pref == null) return;
        pref.setValue(LauncherPrefs.get(getContext()).get(item));
        pref.setOnPreferenceChangeListener((p, v) -> {
            LauncherPrefs.get(getContext()).put(item, (String) v);
            ((ListPreference) p).setValue((String) v);
            return false;
        });
    }

    private void updateColorPickerVisibility(String mode) {
        boolean custom = "custom".equals(mode);
        ColorPickerPreference timeColor = findPreference("pref_clock_time_color");
        ColorPickerPreference dateColor = findPreference("pref_clock_date_color");
        if (timeColor != null) timeColor.setVisible(custom);
        if (dateColor != null) dateColor.setVisible(custom);
    }
}
