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
import android.util.DisplayMetrics;

import androidx.preference.Preference;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/**
 * Fragment for the Grids settings sub-page.
 * Contains: grid columns slider.
 */
public class GridsFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.grids_preferences, rootKey);

        GridPreviewPreference previewPref = findPreference("pref_grid_preview");
        M3SliderPreference columnsPref = findPreference("pref_grid_columns");
        M3SliderPreference topPadPref = findPreference("pref_workspace_top_padding_dp");
        M3SliderPreference bottomPadPref = findPreference("pref_workspace_bottom_padding_dp");

        // Snap padding sliders to multiples of 8 dp so the grid math hits
        // round numbers (4dp design grid × 2). The shared step is exposed
        // as InvariantDeviceProfile.PAD_STEP_DP.
        final float density = getResources().getDisplayMetrics().density;
        if (topPadPref != null) {
            topPadPref.setStepSize(InvariantDeviceProfile.PAD_STEP_DP);
        }
        if (bottomPadPref != null) {
            bottomPadPref.setStepSize(InvariantDeviceProfile.PAD_STEP_DP);
        }

        if (columnsPref != null) {
            // Real-time preview on every slider tick
            columnsPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if (previewPref != null) {
                    previewPref.updateColumns((int) newValue);
                }
                return true;
            });

            // Commit grid change only on finger release
            columnsPref.setOnTrackingStopListener(finalValue -> {
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
            });
        }

        // Live preview updates while the user drags the padding sliders.
        // We push the in-flight value into the GridPreviewView via the
        // onPreferenceChange callback (fires on every tick).
        if (topPadPref != null && previewPref != null) {
            topPadPref.setOnPreferenceChangeListener((pref, newValue) -> {
                previewPref.updateTopPaddingPx(
                        Math.round(((int) newValue) * density));
                return true;
            });
        }
        if (bottomPadPref != null && previewPref != null) {
            bottomPadPref.setOnPreferenceChangeListener((pref, newValue) -> {
                previewPref.updateBottomPaddingPx(
                        Math.round(((int) newValue) * density));
                return true;
            });
        }

        // On finger-release, commit the change by rebuilding the IDP so the
        // workspace row count re-derives against the new padding (which is
        // part of the GRID_ROWS_TOP_PAD / GRID_ROWS_BOTTOM_PAD invalidation
        // key). No launcher restart needed — onConfigChanged is the same
        // live-rebuild path used by the existing grid-columns slider.
        M3SliderPreference.OnTrackingStopListener paddingTrackingStop = finalValue ->
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
        if (topPadPref != null) {
            topPadPref.setOnTrackingStopListener(paddingTrackingStop);
        }
        if (bottomPadPref != null) {
            bottomPadPref.setOnTrackingStopListener(paddingTrackingStop);
        }

        // Seed the preview with the persisted padding values so it opens at
        // the correct layout (otherwise it would render with whatever DeviceProfile
        // was built at fragment-create time, which may lag the pref).
        if (previewPref != null) {
            int topDp = LauncherPrefs.get(getContext()).get(
                    LauncherPrefs.WORKSPACE_TOP_PADDING_DP);
            int bottomDp = LauncherPrefs.get(getContext()).get(
                    LauncherPrefs.WORKSPACE_BOTTOM_PADDING_DP);
            // Auto sentinel resolves to the computed value persisted by IDP
            // before the user reached settings. If somehow still -1 here,
            // fall back to a neutral value.
            if (topDp < 0) topDp = 32;
            if (bottomDp < 0) bottomDp = 16;
            previewPref.updateTopPaddingPx(Math.round(topDp * density));
            previewPref.updateBottomPaddingPx(Math.round(bottomDp * density));
        }
    }

}
