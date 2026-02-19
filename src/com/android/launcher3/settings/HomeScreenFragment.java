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
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.preference.Preference;
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

        boolean adaptiveOn = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
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
                () -> showCustomIconSizeDialog(
                        child.findViewById(R.id.size_toggle_group), iconSizePref));
    }

    private void showCustomIconSizeDialog(
            MaterialButtonToggleGroup toggleGroup, Preference iconSizePref) {
        Context ctx = getContext();
        android.content.res.Resources res = ctx.getResources();

        TextInputLayout inputLayout = new TextInputLayout(ctx,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint("Icon size (50\u2013100%)");
        int hPad = res.getDimensionPixelSize(R.dimen.settings_dialog_horizontal_pad);
        int tPad = res.getDimensionPixelSize(R.dimen.settings_card_padding);
        inputLayout.setPadding(hPad, tPad, hPad, 0);

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputLayout.addView(editText);

        // Pre-fill with current value as percentage
        try {
            String cur = LauncherPrefs.get(ctx)
                    .get(LauncherPrefs.ICON_SIZE_SCALE);
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
                                .put(LauncherPrefs.ICON_SIZE_SCALE, value);
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
        String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE);
        pref.setSummary(IconSettingsHelper.getIconSizeSummary(getContext(), current));
    }

    /** Re-reads the adaptive shape pref and updates the switch + shape visibility. */
    public void refreshAdaptiveShapeState() {
        SwitchPreferenceCompat adaptivePref = findPreference("pref_apply_adaptive_shape");
        Preference iconShapePref = findPreference("pref_icon_shape");
        if (adaptivePref == null) return;
        boolean on = LauncherPrefs.get(getContext()).get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
        adaptivePref.setChecked(on);
        if (iconShapePref != null) iconShapePref.setVisible(on);
    }
}
