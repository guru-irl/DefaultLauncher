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
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.util.Executors;

/**
 * Fragment for the App Drawer settings sub-page.
 * Contains: icons (pack/adaptive/shape/size), labels, layout, and colors sub-page.
 */
public class AppDrawerFragment extends SettingsBaseFragment {

    private boolean mIconSizeBound = false;
    private int mLastPresetButtonId = View.NO_ID;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.app_drawer_preferences, rootKey);

        IconPackManager mgr = LauncherComponentProvider.get(getContext())
                .getIconPackManager();

        // Preload icon pack previews in background
        Executors.MODEL_EXECUTOR.execute(() -> mgr.preloadAllPreviews());

        // Icon pack picker — uses drawer-specific pref key
        Preference iconPackPref = findPreference("pref_icon_pack");
        if (iconPackPref != null) {
            IconSettingsHelper.updateIconPackSummary(getContext(), iconPackPref,
                    LauncherPrefs.ICON_PACK_DRAWER, mgr);
            iconPackPref.setOnPreferenceClickListener(pref -> {
                IconSettingsHelper.showIconPackDialog(this,
                        LauncherPrefs.ICON_PACK_DRAWER, pref, mgr);
                return true;
            });
        }

        // Apply adaptive icon shape switch (drawer)
        SwitchPreferenceCompat adaptivePref = findPreference("pref_apply_adaptive_shape_drawer");
        Preference iconShapePref = findPreference("pref_icon_shape");
        SwitchPreferenceCompat wrapUnsupportedPref = findPreference("pref_wrap_unsupported_icons_drawer");
        ColorPickerPreference bgColorPref = findPreference("pref_icon_wrap_bg_color_drawer");
        M3SliderPreference bgOpacityPref = findPreference("pref_icon_wrap_bg_opacity_drawer");

        // Wire BG color picker
        if (bgColorPref != null) {
            bgColorPref.setColorPrefItem(LauncherPrefs.ICON_WRAP_BG_COLOR_DRAWER, 0);
        }

        boolean adaptiveOn = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
        boolean wrapOn = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS_DRAWER);
        refreshIconPrefVisibility(adaptiveOn, wrapOn,
                iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);

        if (adaptivePref != null) {
            adaptivePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean on = (boolean) newValue;
                boolean wo = LauncherPrefs.get(getContext())
                        .get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS_DRAWER);
                refreshIconPrefVisibility(on, wo,
                        iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);
                return true;
            });
        }

        if (wrapUnsupportedPref != null) {
            wrapUnsupportedPref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean wo = (boolean) newValue;
                boolean ao = LauncherPrefs.get(getContext())
                        .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
                refreshIconPrefVisibility(ao, wo,
                        iconShapePref, wrapUnsupportedPref, bgColorPref, bgOpacityPref);
                return true;
            });
        }

        // Icon shape picker — drawer-specific pref key
        if (iconShapePref != null) {
            IconSettingsHelper.updateIconShapeSummary(getContext(), iconShapePref,
                    ThemeManager.PREF_ICON_SHAPE_DRAWER);
            iconShapePref.setOnPreferenceClickListener(pref -> {
                IconSettingsHelper.showIconShapeDialog(this,
                        ThemeManager.PREF_ICON_SHAPE_DRAWER, pref);
                return true;
            });
        }

        // Icon size — summary only; inline toggle binding happens in onViewCreated
        Preference iconSizePref = findPreference("pref_icon_size_scale");
        if (iconSizePref != null) {
            updateIconSizeSummary(iconSizePref);
        }

        // Match home screen icons switch
        SwitchPreferenceCompat matchHomePref = findPreference("pref_drawer_match_home");
        boolean isMatching = LauncherPrefs.get(getContext()).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (iconPackPref != null) iconPackPref.setVisible(!isMatching);
        if (adaptivePref != null) adaptivePref.setVisible(!isMatching);
        if (iconSizePref != null) iconSizePref.setVisible(!isMatching);
        // When matching, hide all drawer-specific icon prefs
        if (isMatching) {
            if (iconShapePref != null) iconShapePref.setVisible(false);
            if (wrapUnsupportedPref != null) wrapUnsupportedPref.setVisible(false);
            if (bgColorPref != null) bgColorPref.setVisible(false);
            if (bgOpacityPref != null) bgOpacityPref.setVisible(false);
        }

        if (matchHomePref != null) {
            matchHomePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean matching = (boolean) newValue;
                if (iconPackPref != null) iconPackPref.setVisible(!matching);
                SwitchPreferenceCompat adaptiveRef = findPreference("pref_apply_adaptive_shape_drawer");
                if (adaptiveRef != null) adaptiveRef.setVisible(!matching);
                Preference sizePref = findPreference("pref_icon_size_scale");
                if (sizePref != null) sizePref.setVisible(!matching);

                if (matching) {
                    // Hide all drawer-specific icon prefs
                    Preference shapePref = findPreference("pref_icon_shape");
                    if (shapePref != null) shapePref.setVisible(false);
                    SwitchPreferenceCompat wrapRef = findPreference("pref_wrap_unsupported_icons_drawer");
                    if (wrapRef != null) wrapRef.setVisible(false);
                    ColorPickerPreference bgRef = findPreference("pref_icon_wrap_bg_color_drawer");
                    if (bgRef != null) bgRef.setVisible(false);
                    M3SliderPreference opRef = findPreference("pref_icon_wrap_bg_opacity_drawer");
                    if (opRef != null) opRef.setVisible(false);
                } else {
                    // Re-evaluate visibility based on current state
                    boolean adaptiveIsOn = LauncherPrefs.get(getContext())
                            .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
                    boolean wo = LauncherPrefs.get(getContext())
                            .get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS_DRAWER);
                    refreshIconPrefVisibility(adaptiveIsOn, wo,
                            findPreference("pref_icon_shape"),
                            findPreference("pref_wrap_unsupported_icons_drawer"),
                            findPreference("pref_icon_wrap_bg_color_drawer"),
                            findPreference("pref_icon_wrap_bg_opacity_drawer"));
                }

                // Invalidate drawer cache and force reload on toggle change.
                DrawerIconResolver.getInstance().invalidate();
                getListView().post(() -> {
                    LauncherAppState.INSTANCE.get(getContext()).getModel().forceReload();
                });
                return true;
            });
        }

        // Reset all custom icons (drawer)
        Preference resetAllPref = findPreference("pref_reset_all_custom_icons");
        if (resetAllPref != null) {
            boolean hasOverrides = PerAppIconOverrideManager.getInstance(getContext())
                    .hasAnyDrawerOverrides();
            resetAllPref.setVisible(hasOverrides);
            resetAllPref.setOnPreferenceClickListener(pref -> {
                new MaterialAlertDialogBuilder(getContext())
                        .setMessage(R.string.reset_all_custom_icons_confirm)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            PerAppIconOverrideManager.getInstance(getContext())
                                    .clearAllDrawerOverrides();
                            pref.setVisible(false);
                            DrawerIconResolver.getInstance().invalidate();
                            LauncherAppState.INSTANCE.get(getContext())
                                    .getModel().forceReload();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }

        // Wire up grid-affecting prefs
        Preference.OnPreferenceChangeListener gridChangeListener = (pref, newValue) -> {
            getListView().post(() ->
                    InvariantDeviceProfile.INSTANCE.get(getContext())
                            .onConfigChanged(getContext()));
            return true;
        };

        // Label size slider
        Preference labelSizePref = findPreference("pref_allapps_label_size");
        if (labelSizePref != null) {
            labelSizePref.setOnPreferenceChangeListener(gridChangeListener);
        }

        // Two-line labels switch
        Preference twoLinePref = findPreference("pref_enable_two_line_toggle");
        if (twoLinePref != null) {
            twoLinePref.setOnPreferenceChangeListener(gridChangeListener);
        }

        // Hide tabs switch
        Preference hideTabsPref = findPreference("pref_drawer_hide_tabs");
        if (hideTabsPref != null) {
            hideTabsPref.setOnPreferenceChangeListener(gridChangeListener);
        }

        // Row gap slider: snap to nearest valid value (16/24/32) on release
        M3SliderPreference rowGapPref = findPreference("pref_allapps_row_gap");
        if (rowGapPref != null) {
            rowGapPref.setStepSize(8f);
            rowGapPref.setOnPreferenceChangeListener((pref, newValue) -> {
                int raw = (int) newValue;
                int snapped = (int) InvariantDeviceProfile.snapToNearestGap(raw);
                if (snapped != raw) {
                    ((M3SliderPreference) pref).setValue(snapped);
                }
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return snapped == raw;
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
                .get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);

        mLastPresetButtonId = IconSettingsHelper.bindIconSizeToggle(child, current,
                value -> {
                    LauncherPrefs.get(getContext())
                            .put(LauncherPrefs.ICON_SIZE_SCALE_DRAWER, value);
                    updateIconSizeSummary(iconSizePref);
                    getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                },
                () -> IconSettingsHelper.showCustomIconSizeDialog(
                        getContext(),
                        child.findViewById(R.id.size_toggle_group),
                        LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER),
                        mLastPresetButtonId,
                        value -> {
                            LauncherPrefs.get(getContext())
                                    .put(LauncherPrefs.ICON_SIZE_SCALE_DRAWER, value);
                            updateIconSizeSummary(iconSizePref);
                            getListView().post(() ->
                                InvariantDeviceProfile.INSTANCE.get(getContext())
                                        .onConfigChanged(getContext()));
                        }));
    }

    private void updateIconSizeSummary(Preference pref) {
        String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);
        pref.setSummary(IconSettingsHelper.getIconSizeSummary(getContext(), current));
    }

    @Override
    public void onDestroyView() {
        mIconSizeBound = false;
        super.onDestroyView();
    }

    /** Re-reads the adaptive shape pref and updates the switch + all dependent visibility. */
    public void refreshAdaptiveShapeState() {
        SwitchPreferenceCompat adaptivePref = findPreference("pref_apply_adaptive_shape_drawer");
        if (adaptivePref == null) return;

        boolean on = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
        adaptivePref.setChecked(on);

        boolean isMatching = LauncherPrefs.get(getContext()).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (isMatching) return; // All drawer icon prefs hidden when matching

        boolean wrapOn = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.WRAP_UNSUPPORTED_ICONS_DRAWER);
        refreshIconPrefVisibility(on, wrapOn,
                findPreference("pref_icon_shape"),
                findPreference("pref_wrap_unsupported_icons_drawer"),
                findPreference("pref_icon_wrap_bg_color_drawer"),
                findPreference("pref_icon_wrap_bg_opacity_drawer"));
    }

}
