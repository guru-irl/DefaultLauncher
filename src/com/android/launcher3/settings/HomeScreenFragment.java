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
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.PerAppHomeIconResolver;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.util.Executors;

/**
 * Fragment for the Home Screen settings sub-page.
 * Contains: icon pack, adaptive shape switch, icon shape, icon size, reset all custom icons.
 */
public class HomeScreenFragment extends SettingsBaseFragment {

    private boolean mIconSizeBound = false;
    private int mLastPresetButtonId = View.NO_ID;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.home_screen_preferences, rootKey);

        IconPackManager mgr = LauncherComponentProvider.get(getContext())
                .getIconPackManager();

        // Preload icon pack previews in background
        Executors.MODEL_EXECUTOR.execute(() -> mgr.preloadAllPreviews());

        // Icon pack picker
        Preference iconPackPref = findPreference("pref_icon_pack");
        if (iconPackPref != null) {
            IconSettingsHelper.updateIconPackSummary(getContext(), iconPackPref,
                    LauncherPrefs.ICON_PACK, mgr);
            iconPackPref.setOnPreferenceClickListener(pref -> {
                IconSettingsHelper.showIconPackDialog(this,
                        LauncherPrefs.ICON_PACK, pref, mgr);
                return true;
            });
        }

        // Apply adaptive icon shape switch
        SwitchPreferenceCompat adaptivePref = findPreference("pref_apply_adaptive_shape");
        Preference iconShapePref = findPreference("pref_icon_shape");
        SwitchPreferenceCompat wrapUnsupportedPref = findPreference("pref_wrap_unsupported_icons");
        ColorPickerPreference bgColorPref = findPreference("pref_icon_wrap_bg_color");
        M3SliderPreference bgOpacityPref = findPreference("pref_icon_wrap_bg_opacity");

        // Wire BG color picker
        if (bgColorPref != null) {
            bgColorPref.setColorPrefItem(LauncherPrefs.ICON_WRAP_BG_COLOR, 0);
        }

        boolean adaptiveOn = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
        boolean wrapOn = LauncherPrefs.get(getContext()).get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS);
        refreshIconPrefVisibility(adaptiveOn, wrapOn,
                iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);

        if (adaptivePref != null) {
            adaptivePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean on = (boolean) newValue;
                boolean wo = LauncherPrefs.get(getContext()).get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS);
                refreshIconPrefVisibility(on, wo,
                        iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);
                return true;
            });
        }

        if (wrapUnsupportedPref != null) {
            wrapUnsupportedPref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean wo = (boolean) newValue;
                boolean ao = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
                refreshIconPrefVisibility(ao, wo,
                        iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);
                return true;
            });
        }

        // Icon shape picker
        if (iconShapePref != null) {
            IconSettingsHelper.updateIconShapeSummary(getContext(), iconShapePref,
                    ThemeManager.PREF_ICON_SHAPE);
            iconShapePref.setOnPreferenceClickListener(pref -> {
                IconSettingsHelper.showIconShapeDialog(this,
                        ThemeManager.PREF_ICON_SHAPE, pref);
                return true;
            });
        }

        // Icon size — summary only; inline toggle binding happens in onViewCreated
        Preference iconSizePref = findPreference("pref_icon_size_scale");
        if (iconSizePref != null) {
            updateIconSizeSummary(iconSizePref);
        }

        // Reset all custom icons
        Preference resetAllPref = findPreference("pref_reset_all_custom_icons");
        if (resetAllPref != null) {
            boolean hasOverrides = PerAppIconOverrideManager.getInstance(getContext())
                    .hasAnyHomeOverrides();
            resetAllPref.setVisible(hasOverrides);
            resetAllPref.setOnPreferenceClickListener(pref -> {
                new MaterialAlertDialogBuilder(getContext())
                        .setMessage(R.string.reset_all_custom_icons_confirm)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            PerAppIconOverrideManager.getInstance(getContext())
                                    .clearAllHomeOverrides();
                            pref.setVisible(false);
                            // Invalidate caches and reload
                            LauncherAppState app = LauncherAppState.INSTANCE.get(getContext());
                            Executors.MODEL_EXECUTOR.execute(() -> {
                                app.getIconCache().clearAllIcons();
                                PerAppHomeIconResolver.getInstance().invalidate();
                                LauncherIcons.clearPool(getContext());
                                Executors.MAIN_EXECUTOR.execute(() -> {
                                    if (isAdded()) app.getModel().forceReload();
                                });
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind icon size toggle when its view attaches
        RecyclerView rv = getListView();
        rv.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View child) {
                if (child.findViewById(R.id.size_toggle_group) != null) {
                    bindIconSizeInline(child);
                }
            }
            @Override
            public void onChildViewDetachedFromWindow(View view) { }
        });
    }

    private void bindIconSizeInline(View child) {
        if (mIconSizeBound) return;
        mIconSizeBound = true;

        Preference iconSizePref = findPreference("pref_icon_size_scale");
        if (iconSizePref == null) return;

        String current = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.ICON_SIZE_SCALE);

        mLastPresetButtonId = IconSettingsHelper.bindIconSizeToggle(child, current,
                value -> {
                    LauncherPrefs.get(getContext())
                            .put(LauncherPrefs.ICON_SIZE_SCALE, value);
                    updateIconSizeSummary(iconSizePref);
                    getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                },
                () -> IconSettingsHelper.showCustomIconSizeDialog(
                        getContext(),
                        child.findViewById(R.id.size_toggle_group),
                        LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE),
                        mLastPresetButtonId,
                        value -> {
                            LauncherPrefs.get(getContext())
                                    .put(LauncherPrefs.ICON_SIZE_SCALE, value);
                            updateIconSizeSummary(iconSizePref);
                            getListView().post(() ->
                                InvariantDeviceProfile.INSTANCE.get(getContext())
                                        .onConfigChanged(getContext()));
                        }));
    }

    private void updateIconSizeSummary(Preference pref) {
        String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE);
        pref.setSummary(IconSettingsHelper.getIconSizeSummary(getContext(), current));
    }

    @Override
    public void onDestroyView() {
        mIconSizeBound = false;
        super.onDestroyView();
    }

    /** Re-reads the adaptive shape pref and updates the switch + all dependent visibility. */
    public void refreshAdaptiveShapeState() {
        SwitchPreferenceCompat adaptivePref = findPreference("pref_apply_adaptive_shape");
        Preference iconShapePref = findPreference("pref_icon_shape");
        SwitchPreferenceCompat wrapUnsupportedPref = findPreference("pref_wrap_unsupported_icons");
        ColorPickerPreference bgColorPref = findPreference("pref_icon_wrap_bg_color");
        M3SliderPreference bgOpacityPref = findPreference("pref_icon_wrap_bg_opacity");
        if (adaptivePref == null) return;

        boolean on = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
        adaptivePref.setChecked(on);

        boolean wrapOn = LauncherPrefs.get(getContext()).get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS);
        refreshIconPrefVisibility(on, wrapOn,
                iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);
    }

}
