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
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;

public class ColorDebugPreference extends Preference {

    private static final String GRID_TAG = "color_debug_grid";

    private static final int[] COLOR_RESOURCES = {
        R.color.materialColorPrimary,
        R.color.materialColorOnPrimary,
        R.color.materialColorPrimaryContainer,
        R.color.materialColorOnPrimaryContainer,
        R.color.materialColorSecondary,
        R.color.materialColorOnSecondary,
        R.color.materialColorTertiary,
        R.color.materialColorOnTertiary,
        R.color.materialColorSurface,
        R.color.materialColorSurfaceContainer,
        R.color.materialColorSurfaceContainerHigh,
        R.color.materialColorOnSurface,
        R.color.materialColorOnSurfaceVariant,
        R.color.materialColorOutline,
        R.color.materialColorOutlineVariant,
        R.color.materialColorError,
    };

    private static final String[] COLOR_NAMES = {
        "Primary", "OnPrimary", "PrimaryContainer", "OnPrimaryContainer",
        "Secondary", "OnSecondary", "Tertiary", "OnTertiary",
        "Surface", "SurfaceContainer", "SurfaceContainerHigh",
        "OnSurface", "OnSurfaceVariant", "Outline", "OutlineVariant", "Error",
    };

    public ColorDebugPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        // Work with whatever layout the adapter provides
        ViewGroup root = (ViewGroup) holder.itemView;

        // Reuse cached grid if present — just update color values
        View existingGrid = root.findViewWithTag(GRID_TAG);
        if (existingGrid instanceof GridLayout) {
            updateGridColors((GridLayout) existingGrid);
            return;
        }

        // Hide default preference views since the category provides the title
        SettingsSheetBuilder.hideDefaultViews(holder);

        Context ctx = getContext();
        Resources res = ctx.getResources();

        GridLayout grid = new GridLayout(ctx);
        grid.setTag(GRID_TAG);
        grid.setColumnCount(2);
        int vertPad = res.getDimensionPixelSize(R.dimen.settings_vertical_pad_large);
        grid.setPadding(0, vertPad, 0, vertPad);
        grid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int pad = res.getDimensionPixelSize(R.dimen.settings_vertical_pad_small);
        int swatchSize = res.getDimensionPixelSize(R.dimen.settings_swatch_size);
        float cornerRadius = res.getDimension(R.dimen.settings_card_small_corner_radius);

        for (int i = 0; i < COLOR_RESOURCES.length; i++) {
            int color = ctx.getColor(COLOR_RESOURCES[i]);

            LinearLayout swatch = new LinearLayout(ctx);
            swatch.setOrientation(LinearLayout.HORIZONTAL);
            swatch.setGravity(Gravity.CENTER_VERTICAL);
            swatch.setPadding(pad, pad, pad, pad);

            View colorRect = new View(ctx);
            LinearLayout.LayoutParams rectParams =
                    new LinearLayout.LayoutParams(swatchSize, swatchSize);
            rectParams.setMarginEnd(pad * 2);
            colorRect.setLayoutParams(rectParams);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(cornerRadius);
            bg.setColor(color);
            bg.setStroke(1, ctx.getColor(R.color.materialColorOutlineVariant));
            colorRect.setBackground(bg);
            swatch.addView(colorRect);

            TextView tv = new TextView(ctx);
            tv.setText(COLOR_NAMES[i] + "\n" + String.format("#%06X", 0xFFFFFF & color));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            swatch.addView(tv);

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.columnSpec = GridLayout.spec(i % 2, 1f);
            glp.rowSpec = GridLayout.spec(i / 2);
            glp.width = 0;
            swatch.setLayoutParams(glp);
            grid.addView(swatch);
        }

        root.addView(grid);
    }

    private void updateGridColors(GridLayout grid) {
        Context ctx = getContext();
        for (int i = 0; i < grid.getChildCount() && i < COLOR_RESOURCES.length; i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout swatch = (LinearLayout) child;
            int color = ctx.getColor(COLOR_RESOURCES[i]);
            // Update color rect background
            View colorRect = swatch.getChildAt(0);
            if (colorRect != null && colorRect.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) colorRect.getBackground()).setColor(color);
            }
            // Update label text
            View labelChild = swatch.getChildAt(1);
            if (labelChild instanceof TextView) {
                ((TextView) labelChild).setText(
                        COLOR_NAMES[i] + "\n" + String.format("#%06X", 0xFFFFFF & color));
            }
        }
    }
}
