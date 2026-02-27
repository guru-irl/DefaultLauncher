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
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/**
 * Custom Preference that displays a {@link GridPreviewView} with an animated
 * phone frame showing the grid layout, plus an explanation text below.
 * Tagged "no_card" so {@link CardGroupItemDecoration} skips it.
 */
public class GridPreviewPreference extends Preference {

    private static final String TAG_PREVIEW = "grid_preview_container";
    private static final int PREVIEW_HEIGHT_DP = 460;
    private static final float EXPLANATION_TEXT_SP = 12f;
    private static final int EXPLANATION_TOP_MARGIN_DP = 12;

    private GridPreviewView mPreviewView;
    private int mPendingColumns = -1;

    public GridPreviewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Animate the preview to a new column count. */
    public void updateColumns(int columns) {
        if (mPreviewView != null) {
            mPreviewView.animateToColumns(columns);
        } else {
            mPendingColumns = columns;
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        ViewGroup root = (ViewGroup) holder.itemView;
        root.setTag("no_card");

        // Remove previously added container (handles view recycling)
        View existing = root.findViewWithTag(TAG_PREVIEW);
        if (existing != null) {
            root.removeView(existing);
        }

        // Hide all default preference views
        SettingsSheetBuilder.hideDefaultViews(holder);

        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        // Container
        LinearLayout container = new LinearLayout(ctx);
        container.setTag(TAG_PREVIEW);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        // Minimal top padding (sits just below the toolbar), generous bottom for slider spacing
        container.setPadding(0, 0, 0, (int) (24 * density));
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Preview view
        mPreviewView = new GridPreviewView(ctx);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (PREVIEW_HEIGHT_DP * density));
        mPreviewView.setLayoutParams(previewParams);

        int currentCols = LauncherPrefs.get(ctx).get(LauncherPrefs.GRID_COLUMNS);
        mPreviewView.setColumns(currentCols);

        container.addView(mPreviewView);

        // Explanation text
        TextView explanation = new TextView(ctx);
        explanation.setText(R.string.grid_preview_explanation);
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, EXPLANATION_TEXT_SP);
        explanation.setTextColor(ctx.getColor(R.color.materialColorOnSurfaceVariant));
        explanation.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = (int) (EXPLANATION_TOP_MARGIN_DP * density);
        explanation.setLayoutParams(textParams);
        container.addView(explanation);

        root.addView(container);

        // Apply any pending column update
        if (mPendingColumns > 0) {
            mPreviewView.animateToColumns(mPendingColumns);
            mPendingColumns = -1;
        }
    }
}
