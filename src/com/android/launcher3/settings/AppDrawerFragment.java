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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
public class AppDrawerFragment extends PreferenceFragmentCompat {

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

        boolean adaptiveOn = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
        if (iconShapePref != null) {
            iconShapePref.setVisible(adaptiveOn);
        }

        if (adaptivePref != null) {
            adaptivePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean on = (boolean) newValue;
                if (iconShapePref != null) iconShapePref.setVisible(on);
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
        if (iconShapePref != null) iconShapePref.setVisible(!isMatching && adaptiveOn);
        if (iconSizePref != null) iconSizePref.setVisible(!isMatching);

        if (matchHomePref != null) {
            matchHomePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean matching = (boolean) newValue;
                if (iconPackPref != null) iconPackPref.setVisible(!matching);
                SwitchPreferenceCompat adaptiveRef = findPreference("pref_apply_adaptive_shape_drawer");
                if (adaptiveRef != null) adaptiveRef.setVisible(!matching);
                Preference shapePref = findPreference("pref_icon_shape");
                boolean adaptiveIsOn = LauncherPrefs.get(getContext())
                        .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
                if (shapePref != null) shapePref.setVisible(!matching && adaptiveIsOn);
                Preference sizePref = findPreference("pref_icon_size_scale");
                if (sizePref != null) sizePref.setVisible(!matching);

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

        // Edge-to-edge insets
        View listView = getListView();
        final int bottomPadding = listView.getPaddingBottom();
        listView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    bottomPadding + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);

        // Card group decoration
        RecyclerView rv = getListView();
        rv.addItemDecoration(new CardGroupItemDecoration(getContext()));

        // Bind icon size toggle when its view attaches
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
                () -> showCustomIconSizeDialog(
                        child.findViewById(R.id.size_toggle_group), iconSizePref));
    }

    private void showCustomIconSizeDialog(
            MaterialButtonToggleGroup toggleGroup, Preference iconSizePref) {
        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        TextInputLayout inputLayout = new TextInputLayout(ctx,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint("Icon size (50\u2013100%)");
        int hPad = (int) (24 * density);
        int tPad = (int) (16 * density);
        inputLayout.setPadding(hPad, tPad, hPad, 0);

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputLayout.addView(editText);

        try {
            String cur = LauncherPrefs.get(ctx)
                    .get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);
            float pct = Float.parseFloat(cur) * 100f;
            editText.setText(String.format("%.0f", pct));
        } catch (NumberFormatException ignored) { }

        Runnable revertSelection = () -> {
            if (mLastPresetButtonId != View.NO_ID) {
                toggleGroup.check(mLastPresetButtonId);
            } else {
                toggleGroup.clearChecked();
            }
        };

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.icon_size_custom)
                .setView(inputLayout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String text = editText.getText() != null
                            ? editText.getText().toString().trim() : "";
                    try {
                        float pct = Float.parseFloat(text);
                        pct = Math.max(50f, Math.min(100f, pct));
                        String value = String.valueOf(pct / 100f);
                        LauncherPrefs.get(ctx)
                                .put(LauncherPrefs.ICON_SIZE_SCALE_DRAWER, value);
                        updateIconSizeSummary(iconSizePref);
                        getListView().post(() ->
                            InvariantDeviceProfile.INSTANCE.get(ctx)
                                    .onConfigChanged(ctx));
                    } catch (NumberFormatException ignored) {
                        revertSelection.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> revertSelection.run())
                .setOnCancelListener(dialog -> revertSelection.run())
                .show();
    }

    private void updateIconSizeSummary(Preference pref) {
        String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);
        pref.setSummary(IconSettingsHelper.getIconSizeSummary(getContext(), current));
    }
}
