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

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.ShapeAppearanceModel;
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
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.util.Executors;

/**
 * Fragment for the App Drawer settings sub-page.
 * Contains: icons (pack/shape/size), labels, layout, and colors sub-page.
 */
public class AppDrawerFragment extends PreferenceFragmentCompat {

    private static final String[] SIZE_PRESETS = {"0.8", "0.863", "0.92", "1.0"};
    private static final String[] SIZE_LABELS = {"S (80%)", "M (86%)", "L (92%)", "XL (100%)"};
    private static final long CORNER_ANIM_DURATION = 250L;

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

        // Icon shape picker — drawer-specific pref key
        Preference iconShapePref = findPreference("pref_icon_shape");
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
        SwitchPreference matchHomePref = findPreference("pref_drawer_match_home");
        boolean isMatching = LauncherPrefs.get(getContext()).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (iconPackPref != null) iconPackPref.setVisible(!isMatching);
        Preference iconShapePrefRef = findPreference("pref_icon_shape");
        if (iconShapePrefRef != null) iconShapePrefRef.setVisible(!isMatching);
        if (iconSizePref != null) iconSizePref.setVisible(!isMatching);

        if (matchHomePref != null) {
            matchHomePref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean matching = (boolean) newValue;
                if (iconPackPref != null) iconPackPref.setVisible(!matching);
                Preference shapePref = findPreference("pref_icon_shape");
                if (shapePref != null) shapePref.setVisible(!matching);
                Preference sizePref = findPreference("pref_icon_size_scale");
                if (sizePref != null) sizePref.setVisible(!matching);

                // Invalidate drawer cache and force reload on toggle change.
                // Don't clear the drawer pack — preserve it so turning
                // match-home off restores the previous drawer settings.
                DrawerIconResolver.getInstance().invalidate();
                getListView().post(() -> {
                    LauncherAppState.INSTANCE.get(getContext()).getModel().forceReload();
                });
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

        MaterialButtonToggleGroup toggleGroup =
                child.findViewById(R.id.size_toggle_group);

        String current = LauncherPrefs.get(getContext())
                .get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);

        int[] btnIds = {R.id.btn_size_s, R.id.btn_size_m,
                R.id.btn_size_l, R.id.btn_size_xl};
        boolean isPreset = false;
        for (int j = 0; j < SIZE_PRESETS.length; j++) {
            if (SIZE_PRESETS[j].equals(current)) {
                toggleGroup.check(btnIds[j]);
                mLastPresetButtonId = btnIds[j];
                isPreset = true;
                break;
            }
        }
        if (!isPreset) {
            toggleGroup.check(R.id.btn_size_custom);
        }

        // Set pill shape on initial selection
        toggleGroup.post(() -> {
            for (int i = 0; i < toggleGroup.getChildCount(); i++) {
                View c = toggleGroup.getChildAt(i);
                if (c instanceof MaterialButton && ((MaterialButton) c).isChecked()) {
                    float pill = c.getHeight() / 2f;
                    if (pill > 0) {
                        ((MaterialButton) c).setShapeAppearanceModel(
                                ShapeAppearanceModel.builder()
                                        .setAllCornerSizes(pill)
                                        .build());
                    }
                }
            }
        });

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            MaterialButton btn = group.findViewById(checkedId);
            if (btn != null) {
                animateButtonCorners(btn, isChecked);
            }

            if (!isChecked) return;

            if (checkedId == R.id.btn_size_custom) {
                showCustomIconSizeDialog(toggleGroup, iconSizePref);
                return;
            }

            mLastPresetButtonId = checkedId;

            if (btn != null) {
                String value = (String) btn.getTag();
                LauncherPrefs.get(getContext())
                        .put(LauncherPrefs.ICON_SIZE_SCALE_DRAWER, value);
                updateIconSizeSummary(iconSizePref);
                getListView().post(() ->
                    InvariantDeviceProfile.INSTANCE.get(getContext())
                            .onConfigChanged(getContext()));
            }
        });
    }

    private void animateButtonCorners(MaterialButton btn, boolean toPill) {
        float density = getResources().getDisplayMetrics().density;
        float innerRadius = 8 * density;

        btn.post(() -> {
            float pillRadius = btn.getHeight() / 2f;
            if (pillRadius <= 0) pillRadius = 20 * density;

            float startRadius = toPill ? innerRadius : pillRadius;
            float endRadius = toPill ? pillRadius : innerRadius;

            ValueAnimator anim = ValueAnimator.ofFloat(startRadius, endRadius);
            anim.setDuration(CORNER_ANIM_DURATION);
            anim.setInterpolator(new FastOutSlowInInterpolator());
            anim.addUpdateListener(a -> {
                float r = (float) a.getAnimatedValue();
                btn.setShapeAppearanceModel(
                        btn.getShapeAppearanceModel().toBuilder()
                                .setAllCornerSizes(r)
                                .build());
            });
            anim.start();
        });
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
        for (int i = 0; i < SIZE_PRESETS.length; i++) {
            if (SIZE_PRESETS[i].equals(current)) {
                pref.setSummary(SIZE_LABELS[i]);
                return;
            }
        }
        try {
            float pct = Float.parseFloat(current) * 100f;
            pref.setSummary(getString(R.string.icon_size_custom)
                    + " (" + String.format("%.0f%%", pct) + ")");
        } catch (NumberFormatException e) {
            pref.setSummary(getString(R.string.icon_size_custom) + " (" + current + ")");
        }
    }
}
