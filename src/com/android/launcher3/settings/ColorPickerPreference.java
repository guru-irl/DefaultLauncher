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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsColorResolver;
import com.android.launcher3.allapps.AllAppsColorResolver.PaletteGroup;
import com.android.launcher3.allapps.AllAppsColorResolver.SwatchEntry;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

/**
 * A preference that shows a color swatch circle and opens a BottomSheet color picker.
 * The picker displays the full tonal palette organized by palette groups
 * (Primary, Secondary, Tertiary, Neutral, Neutral Variant).
 */
public class ColorPickerPreference extends Preference {

    private ConstantItem<String> mPrefItem;
    private int mDefaultColorResId;
    private int mDefaultColorOverride;
    private View mSwatchView;
    private BottomSheetDialog mDialog;

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_color_swatch);
    }

    public void setColorPrefItem(ConstantItem<String> prefItem, int defaultColorResId) {
        mPrefItem = prefItem;
        mDefaultColorResId = defaultColorResId;
        mDefaultColorOverride = 0;
        updateSummaryFromValue();
    }

    /** Set with a pre-resolved color int instead of a resource ID. */
    public void setColorPrefItem(ConstantItem<String> prefItem, int defaultColorResId,
            int defaultColorOverride) {
        mPrefItem = prefItem;
        mDefaultColorResId = defaultColorResId;
        mDefaultColorOverride = defaultColorOverride;
        updateSummaryFromValue();
    }

    private String getCurrentValue() {
        if (mPrefItem == null) return "";
        return LauncherPrefs.get(getContext()).get(mPrefItem);
    }

    private void updateSummaryFromValue() {
        String value = getCurrentValue();
        if (value == null || value.isEmpty()) {
            setSummary(R.string.drawer_color_default);
        } else {
            setSummary(AllAppsColorResolver.toDisplayName(value));
        }
    }

    private int resolveDefaultColor() {
        if (mDefaultColorOverride != 0) return mDefaultColorOverride;
        if (mDefaultColorResId != 0) return getContext().getColor(mDefaultColorResId);
        return Color.GRAY;
    }

    private int resolveCurrentColor() {
        String value = getCurrentValue();
        int color = AllAppsColorResolver.resolveColorByName(getContext(), value);
        if (color == 0) {
            color = resolveDefaultColor();
        }
        return color;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mSwatchView = holder.findViewById(R.id.color_swatch);
        if (mSwatchView != null) {
            updateSwatchColor();
        }
    }

    private void updateSwatchColor() {
        if (mSwatchView == null) return;
        int color = resolveCurrentColor();
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setSize(dp(24), dp(24));
        int outlineColor = getContext().getColor(R.color.materialColorOutline);
        circle.setStroke(dp(1), outlineColor);
        mSwatchView.setBackground(circle);
    }

    @Override
    protected void onClick() {
        showColorPickerSheet();
    }

    private void showColorPickerSheet() {
        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        mDialog = sheet;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(24));

        // Handle bar
        View handle = new View(ctx);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setShape(GradientDrawable.RECTANGLE);
        handleBg.setCornerRadius(2 * density);
        handleBg.setColor(ctx.getColor(R.color.materialColorOutline));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(12);
        handle.setLayoutParams(handleLp);
        root.addView(handle);

        // Title
        TextView titleView = new TextView(ctx);
        titleView.setText(getTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                ctx.getResources().getDimension(R.dimen.settings_sheet_title_text_size));
        titleView.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
        titleView.setPadding(dp(24), dp(16), dp(24), dp(8));
        root.addView(titleView);

        // Scrollable content with palette groups
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        String currentValue = getCurrentValue();
        if (currentValue == null) currentValue = "";
        int outlineColor = ctx.getColor(R.color.materialColorOutline);

        // "Default" row
        addDefaultRow(content, ctx, currentValue, outlineColor, sheet);

        // Tonal palette groups
        List<PaletteGroup> groups = AllAppsColorResolver.getPaletteGroups();
        for (PaletteGroup group : groups) {
            addPaletteGroupRow(content, ctx, group, currentValue, outlineColor, sheet);
        }

        scrollView.addView(content);
        root.addView(scrollView);
        sheet.setContentView(root);
        sheet.show();
    }

    private void addDefaultRow(LinearLayout parent, Context ctx,
            String currentValue, int outlineColor, BottomSheetDialog sheet) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(12), dp(24), dp(4));

        boolean isSelected = currentValue.isEmpty();

        // Default swatch
        View swatch = new View(ctx);
        int swatchSize = dp(36);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatch.setLayoutParams(slp);

        int defaultColor = resolveDefaultColor();
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(defaultColor);
        if (isSelected) {
            circle.setStroke(dp(3), outlineColor);
        }
        swatch.setBackground(circle);

        // Label
        TextView label = new TextView(ctx);
        label.setText(R.string.drawer_color_default);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMarginStart(dp(12));
        label.setLayoutParams(llp);

        row.addView(swatch);
        row.addView(label);

        row.setOnClickListener(v -> {
            if (mPrefItem != null) {
                LauncherPrefs.get(ctx).put(mPrefItem, "");
            }
            updateSummaryFromValue();
            updateSwatchColor();
            if (getOnPreferenceChangeListener() != null) {
                getOnPreferenceChangeListener().onPreferenceChange(this, "");
            }
            sheet.dismiss();
        });

        parent.addView(row);
    }

    private void addPaletteGroupRow(LinearLayout parent, Context ctx,
            PaletteGroup group, String currentValue, int outlineColor,
            BottomSheetDialog sheet) {
        // Section header
        TextView header = new TextView(ctx);
        header.setText(group.label);
        header.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                ctx.getResources().getDimension(R.dimen.settings_sheet_category_text_size));
        header.setTextColor(ctx.getColor(R.color.materialColorOnSurfaceVariant));
        header.setAllCaps(true);
        header.setLetterSpacing(0.1f);
        header.setPadding(dp(24), dp(12), dp(24), dp(4));
        parent.addView(header);

        // Horizontal scrolling row of swatches — edge to edge
        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setClipToPadding(false);
        hScroll.setPadding(dp(16), 0, dp(16), 0);
        hScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout swatchRow = new LinearLayout(ctx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);

        for (SwatchEntry entry : group.swatches) {
            addSwatch(swatchRow, ctx, entry, currentValue, outlineColor, sheet);
        }

        hScroll.addView(swatchRow);
        parent.addView(hScroll);
    }

    private void addSwatch(LinearLayout row, Context ctx, SwatchEntry entry,
            String currentValue, int outlineColor, BottomSheetDialog sheet) {
        int swatchSize = dp(40);
        int margin = dp(4);

        FrameLayout wrapper = new FrameLayout(ctx);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                swatchSize + margin * 2, swatchSize + margin * 2);
        wrapper.setLayoutParams(wlp);

        View swatch = new View(ctx);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(swatchSize, swatchSize);
        flp.gravity = Gravity.CENTER;
        swatch.setLayoutParams(flp);

        boolean isSelected = entry.resourceName.equals(currentValue);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ctx.getColor(entry.androidColorResId));
        if (isSelected) {
            circle.setStroke(dp(3), outlineColor);
        }
        swatch.setBackground(circle);
        wrapper.addView(swatch);

        wrapper.setOnClickListener(v -> {
            if (mPrefItem != null) {
                LauncherPrefs.get(ctx).put(mPrefItem, entry.resourceName);
            }
            updateSummaryFromValue();
            updateSwatchColor();
            if (getOnPreferenceChangeListener() != null) {
                getOnPreferenceChangeListener().onPreferenceChange(this, entry.resourceName);
            }
            sheet.dismiss();
        });

        row.addView(wrapper);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = null;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }
}
