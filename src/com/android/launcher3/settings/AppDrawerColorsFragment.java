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
import android.os.UserManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/**
 * Fragment for the App Drawer Colors settings sub-page.
 */
public class AppDrawerColorsFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.drawer_colors_preferences, rootKey);

        // Configure color picker preferences — drawer bg default matches allAppsScrimColor
        int scrimDefault = getContext().getColor(R.color.materialColorSurfaceContainerLow);
        configureColorPickerWithDefault("pref_drawer_bg_color",
                LauncherPrefs.DRAWER_BG_COLOR, scrimDefault);
        configureColorPicker("pref_drawer_search_bg_color",
                LauncherPrefs.DRAWER_SEARCH_BG_COLOR, R.color.materialColorSurfaceContainer);
        configureColorPicker("pref_drawer_scrollbar_color",
                LauncherPrefs.DRAWER_SCROLLBAR_COLOR, R.color.materialColorPrimary);
        configureColorPicker("pref_drawer_tab_selected_color",
                LauncherPrefs.DRAWER_TAB_SELECTED_COLOR, R.color.materialColorPrimary);
        configureColorPicker("pref_drawer_tab_unselected_color",
                LauncherPrefs.DRAWER_TAB_UNSELECTED_COLOR, R.color.materialColorOnSurfaceVariant);

        // Folder color pickers — defaults from FolderSettingsHelper (single source of truth)
        configureColorPickerWithDefault("pref_folder_icon_color",
                LauncherPrefs.FOLDER_COVER_BG_COLOR,
                FolderSettingsHelper.getDefaultCoverBgColor(getContext()));
        configureColorPickerWithDefault("pref_folder_cover_icon_color",
                LauncherPrefs.FOLDER_COVER_ICON_COLOR,
                FolderSettingsHelper.getDefaultCoverIconColor(getContext()));
        configureColorPickerWithDefault("pref_folder_bg_color",
                LauncherPrefs.FOLDER_BG_COLOR,
                FolderSettingsHelper.getDefaultFolderBgColor(getContext()));

        // Wire opacity slider changes to trigger recreation
        Preference opacityPref = findPreference("pref_drawer_bg_opacity");
        if (opacityPref != null) {
            opacityPref.setOnPreferenceChangeListener((pref, newValue) -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            });
        }
        Preference searchOpacityPref = findPreference("pref_drawer_search_bg_opacity");
        if (searchOpacityPref != null) {
            searchOpacityPref.setOnPreferenceChangeListener((pref, newValue) -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            });
        }
        Preference folderOpacityPref = findPreference("pref_folder_bg_opacity");
        if (folderOpacityPref != null) {
            folderOpacityPref.setOnPreferenceChangeListener((pref, newValue) -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            });
        }

        // Hide tabs category if no work profile exists
        UserManager um = getContext().getSystemService(UserManager.class);
        PreferenceCategory tabsCategory = findPreference("category_tabs");
        if (tabsCategory != null) {
            if (um == null || um.getUserProfiles().size() <= 1) {
                // No work profile — tabs never appear
                getPreferenceScreen().removePreference(tabsCategory);
            } else if (LauncherPrefs.get(getContext()).get(LauncherPrefs.DRAWER_HIDE_TABS)) {
                // Work profile exists but tabs are hidden
                Preference selPref = findPreference("pref_drawer_tab_selected_color");
                Preference unselPref = findPreference("pref_drawer_tab_unselected_color");
                String hint = getString(R.string.drawer_tab_color_hint);
                if (selPref != null) selPref.setSummary(hint);
                if (unselPref != null) unselPref.setSummary(hint);
            }
        }
    }

    private void configureColorPickerWithDefault(String key,
            com.android.launcher3.ConstantItem<String> prefItem, int defaultColor) {
        ColorPickerPreference pref = findPreference(key);
        if (pref != null) {
            pref.setColorPrefItem(prefItem, 0, defaultColor);
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            });
        }
    }

    private void configureColorPicker(String key,
            com.android.launcher3.ConstantItem<String> prefItem, int defaultColorResId) {
        ColorPickerPreference pref = findPreference(key);
        if (pref != null) {
            pref.setColorPrefItem(prefItem, defaultColorResId);
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            });
        }
    }

}
