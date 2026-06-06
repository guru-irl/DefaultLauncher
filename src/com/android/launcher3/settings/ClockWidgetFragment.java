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
import android.widget.LinearLayout;

import androidx.preference.Preference;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Settings sub-screen for the Danfo clock widget. */
public class ClockWidgetFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.clock_widget_preferences, rootKey);

        bindChoice("pref_clock_alignment", LauncherPrefs.CLOCK_ALIGNMENT,
                R.string.clock_alignment_title,
                R.array.clock_alignment_entries, R.array.clock_alignment_values);
        bindChoice("pref_clock_time_format", LauncherPrefs.CLOCK_TIME_FORMAT,
                R.string.clock_time_format_title,
                R.array.clock_time_format_entries, R.array.clock_time_format_values);
        bindChoice("pref_clock_color_mode", LauncherPrefs.CLOCK_COLOR_MODE,
                R.string.clock_color_mode_title,
                R.array.clock_color_mode_entries, R.array.clock_color_mode_values);

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

    /**
     * Binds a plain {@link Preference} row to an M3 single-choice bottom sheet.
     * The row summary always reflects the stored value's human label, and tapping
     * the row opens a sheet with one card per option (the selected one highlighted).
     */
    private void bindChoice(String key, ConstantItem<String> item, int titleResId,
            int entriesArrayResId, int valuesArrayResId) {
        Preference pref = findPreference(key);
        if (pref == null) return;
        Context ctx = getContext();

        String[] labels = ctx.getResources().getStringArray(entriesArrayResId);
        String[] values = ctx.getResources().getStringArray(valuesArrayResId);

        pref.setSummary(labelFor(item, labels, values));
        pref.setOnPreferenceClickListener(p -> {
            showChoiceSheet(item, titleResId, labels, values, pref);
            return true;
        });
    }

    /** Returns the human label for the currently stored value of {@code item}. */
    private String labelFor(ConstantItem<String> item, String[] labels, String[] values) {
        String current = LauncherPrefs.get(getContext()).get(item);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return labels[i];
        }
        return labels.length > 0 ? labels[0] : current;
    }

    /** Shows the M3 single-choice bottom sheet for a clock choice setting. */
    private void showChoiceSheet(ConstantItem<String> item, int titleResId,
            String[] labels, String[] values, Preference pref) {
        Context ctx = getContext();
        if (ctx == null) return;

        String current = LauncherPrefs.get(ctx).get(item);

        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int colorSurfaceVar = ctx.getColor(R.color.materialColorSurfaceContainerHigh);
        int colorSelectedFill = ctx.getColor(R.color.materialColorPrimaryContainer);
        int colorOnSelected = ctx.getColor(R.color.materialColorOnPrimaryContainer);

        SettingsSheetBuilder.SheetComponents components =
                new SettingsSheetBuilder(ctx)
                        .setTitle(titleResId)
                        .dismissOnDestroy(this)
                        .build();
        BottomSheetDialog sheet = components.sheet;

        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            boolean selected = value.equals(current);
            LinearLayout card = SettingsSheetBuilder.createCard(ctx, labels[i], null,
                    selected ? colorSelectedFill : colorSurfaceVar,
                    selected ? colorOnSelected : colorOnSurface,
                    v -> {
                        sheet.dismiss();
                        LauncherPrefs.get(ctx).put(item, value);
                        pref.setSummary(labelFor(item, labels, values));
                        if (item == LauncherPrefs.CLOCK_COLOR_MODE) {
                            updateColorPickerVisibility(value);
                        }
                    });
            components.contentArea.addView(card);
        }

        components.showScrollable();
    }

    private void updateColorPickerVisibility(String mode) {
        boolean custom = "custom".equals(mode);
        ColorPickerPreference timeColor = findPreference("pref_clock_time_color");
        ColorPickerPreference dateColor = findPreference("pref_clock_date_color");
        if (timeColor != null) timeColor.setVisible(custom);
        if (dateColor != null) dateColor.setVisible(custom);
    }
}
