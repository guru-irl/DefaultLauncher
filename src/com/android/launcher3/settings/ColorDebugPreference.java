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
import android.graphics.Color;
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

        // Remove previously added grid (handles view recycling)
        View existingGrid = root.findViewWithTag(GRID_TAG);
        if (existingGrid != null) {
            root.removeView(existingGrid);
        }

        // Hide default preference title/summary since the category provides the title
        View title = holder.findViewById(android.R.id.title);
        if (title != null) title.setVisibility(View.GONE);
        View summary = holder.findViewById(android.R.id.summary);
        if (summary != null) summary.setVisibility(View.GONE);
        View iconFrame = holder.findViewById(
                androidx.preference.R.id.icon_frame);
        if (iconFrame != null) iconFrame.setVisibility(View.GONE);

        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        GridLayout grid = new GridLayout(ctx);
        grid.setTag(GRID_TAG);
        grid.setColumnCount(2);
        int vertPad = (int) (12 * density);
        grid.setPadding(0, vertPad, 0, vertPad);
        grid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int pad = (int) (4 * density);
        int swatchSize = (int) (24 * density);
        float cornerRadius = 4 * density;

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
            bg.setStroke(1, Color.GRAY);
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
}
