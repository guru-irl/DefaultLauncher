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
import android.graphics.Typeface;
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

    private static final String[] SECTION_NAMES = {
        "Primary", "Secondary", "Tertiary", "Error", "Surface", "Outline",
    };

    private static final int[][] SECTION_COLORS = {
        // Primary
        {
            R.color.materialColorPrimary,
            R.color.materialColorOnPrimary,
            R.color.materialColorPrimaryContainer,
            R.color.materialColorOnPrimaryContainer,
            R.color.materialColorPrimaryFixed,
            R.color.materialColorPrimaryFixedDim,
            R.color.materialColorOnPrimaryFixed,
            R.color.materialColorOnPrimaryFixedVariant,
            R.color.materialColorInversePrimary,
        },
        // Secondary
        {
            R.color.materialColorSecondary,
            R.color.materialColorOnSecondary,
            R.color.materialColorSecondaryContainer,
            R.color.materialColorOnSecondaryContainer,
            R.color.materialColorSecondaryFixed,
            R.color.materialColorSecondaryFixedDim,
            R.color.materialColorOnSecondaryFixed,
            R.color.materialColorOnSecondaryFixedVariant,
        },
        // Tertiary
        {
            R.color.materialColorTertiary,
            R.color.materialColorOnTertiary,
            R.color.materialColorTertiaryContainer,
            R.color.materialColorOnTertiaryContainer,
            R.color.materialColorTertiaryFixed,
            R.color.materialColorTertiaryFixedDim,
            R.color.materialColorOnTertiaryFixed,
            R.color.materialColorOnTertiaryFixedVariant,
        },
        // Error
        {
            R.color.materialColorError,
            R.color.materialColorOnError,
            R.color.materialColorErrorContainer,
            R.color.materialColorOnErrorContainer,
        },
        // Surface
        {
            R.color.materialColorSurface,
            R.color.materialColorSurfaceDim,
            R.color.materialColorSurfaceBright,
            R.color.materialColorSurfaceVariant,
            R.color.materialColorSurfaceContainerLowest,
            R.color.materialColorSurfaceContainerLow,
            R.color.materialColorSurfaceContainer,
            R.color.materialColorSurfaceContainerHigh,
            R.color.materialColorSurfaceContainerHighest,
            R.color.materialColorOnSurface,
            R.color.materialColorOnSurfaceVariant,
            R.color.materialColorOnBackground,
            R.color.materialColorInverseSurface,
            R.color.materialColorInverseOnSurface,
        },
        // Outline
        {
            R.color.materialColorOutline,
            R.color.materialColorOutlineVariant,
        },
    };

    private static final String[][] SECTION_LABELS = {
        // Primary
        {
            "Primary", "OnPrimary", "PrimaryContainer", "OnPrimaryContainer",
            "PrimaryFixed", "PrimaryFixedDim", "OnPrimaryFixed",
            "OnPrimaryFixedVariant", "InversePrimary",
        },
        // Secondary
        {
            "Secondary", "OnSecondary", "SecondaryContainer",
            "OnSecondaryContainer", "SecondaryFixed", "SecondaryFixedDim",
            "OnSecondaryFixed", "OnSecondaryFixedVariant",
        },
        // Tertiary
        {
            "Tertiary", "OnTertiary", "TertiaryContainer",
            "OnTertiaryContainer", "TertiaryFixed", "TertiaryFixedDim",
            "OnTertiaryFixed", "OnTertiaryFixedVariant",
        },
        // Error
        {
            "Error", "OnError", "ErrorContainer", "OnErrorContainer",
        },
        // Surface
        {
            "Surface", "SurfaceDim", "SurfaceBright", "SurfaceVariant",
            "SrfcContainerLowest", "SrfcContainerLow", "SrfcContainer",
            "SrfcContainerHigh", "SrfcContainerHighest",
            "OnSurface", "OnSurfaceVariant", "OnBackground",
            "InverseSurface", "InverseOnSurface",
        },
        // Outline
        {
            "Outline", "OutlineVariant",
        },
    };

    public ColorDebugPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

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
        int headerColor = ctx.getColor(R.color.materialColorPrimary);
        int strokeColor = ctx.getColor(R.color.materialColorOutlineVariant);

        int row = 0;
        for (int s = 0; s < SECTION_NAMES.length; s++) {
            // Section header spanning both columns
            TextView header = new TextView(ctx);
            header.setText(SECTION_NAMES[s]);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
            header.setTextColor(headerColor);
            header.setPadding(pad, s > 0 ? vertPad : 0, pad, pad / 2);

            GridLayout.LayoutParams hp = new GridLayout.LayoutParams();
            hp.columnSpec = GridLayout.spec(0, 2, 1f);
            hp.rowSpec = GridLayout.spec(row);
            hp.width = 0;
            header.setLayoutParams(hp);
            grid.addView(header);
            row++;

            // Color swatches in 2-column pairs
            int[] colors = SECTION_COLORS[s];
            String[] labels = SECTION_LABELS[s];
            for (int i = 0; i < colors.length; i++) {
                int color = ctx.getColor(colors[i]);

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
                bg.setStroke(1, strokeColor);
                colorRect.setBackground(bg);
                swatch.addView(colorRect);

                TextView tv = new TextView(ctx);
                tv.setText(labels[i] + "\n"
                        + String.format("#%06X", 0xFFFFFF & color));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                swatch.addView(tv);

                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.columnSpec = GridLayout.spec(i % 2, 1f);
                glp.rowSpec = GridLayout.spec(row + i / 2);
                glp.width = 0;
                swatch.setLayoutParams(glp);
                grid.addView(swatch);
            }
            row += (colors.length + 1) / 2;
        }

        root.addView(grid);
    }

    private void updateGridColors(GridLayout grid) {
        Context ctx = getContext();
        int childIdx = 0;
        for (int s = 0; s < SECTION_COLORS.length; s++) {
            childIdx++; // skip section header
            for (int i = 0; i < SECTION_COLORS[s].length; i++) {
                if (childIdx >= grid.getChildCount()) return;
                View child = grid.getChildAt(childIdx);
                childIdx++;
                if (!(child instanceof LinearLayout swatch)) continue;
                int color = ctx.getColor(SECTION_COLORS[s][i]);
                View colorRect = swatch.getChildAt(0);
                if (colorRect != null
                        && colorRect.getBackground() instanceof GradientDrawable gd) {
                    gd.setColor(color);
                }
                View labelView = swatch.getChildAt(1);
                if (labelView instanceof TextView tv) {
                    tv.setText(SECTION_LABELS[s][i] + "\n"
                            + String.format("#%06X", 0xFFFFFF & color));
                }
            }
        }
    }
}
