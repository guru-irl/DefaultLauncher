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
        Resources res = getContext().getResources();
        int color = resolveCurrentColor();
        int swatchSize = res.getDimensionPixelSize(R.dimen.settings_swatch_size);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setSize(swatchSize, swatchSize);
        int outlineColor = getContext().getColor(R.color.materialColorOutline);
        circle.setStroke(1, outlineColor);
        mSwatchView.setBackground(circle);
    }

    @Override
    protected void onClick() {
        showColorPickerSheet();
    }

    private void showColorPickerSheet() {
        Context ctx = getContext();
        Resources res = ctx.getResources();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }

        SettingsSheetBuilder.SheetComponents c = new SettingsSheetBuilder(ctx)
                .setTitle(getTitle())
                .build();
        mDialog = c.sheet;

        String currentValue = getCurrentValue();
        if (currentValue == null) currentValue = "";
        int outlineColor = ctx.getColor(R.color.materialColorOutline);

        // "Default" row
        addDefaultRow(c.contentArea, ctx, res, currentValue, outlineColor, c.sheet);

        // Tonal palette groups
        List<PaletteGroup> groups = AllAppsColorResolver.getPaletteGroups();
        for (PaletteGroup group : groups) {
            addPaletteGroupRow(c.contentArea, ctx, res, group, currentValue, outlineColor, c.sheet);
        }

        c.showScrollable();
    }

    private void addDefaultRow(LinearLayout parent, Context ctx, Resources res,
            String currentValue, int outlineColor, BottomSheetDialog sheet) {
        int padH = res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal);
        int padV = res.getDimensionPixelSize(R.dimen.settings_vertical_pad_large);
        int padGap = res.getDimensionPixelSize(R.dimen.settings_item_gap);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(padH, padV, padH, padGap);

        boolean isSelected = currentValue.isEmpty();

        // Default swatch
        View swatch = new View(ctx);
        int swatchSize = res.getDimensionPixelSize(
                R.dimen.settings_color_picker_swatch_default_size);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatch.setLayoutParams(slp);

        int defaultColor = resolveDefaultColor();
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(defaultColor);
        if (isSelected) {
            circle.setStroke(res.getDimensionPixelSize(
                    R.dimen.settings_color_picker_selected_stroke), outlineColor);
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
        llp.setMarginStart(res.getDimensionPixelSize(R.dimen.settings_icon_margin_end));
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

    private void addPaletteGroupRow(LinearLayout parent, Context ctx, Resources res,
            PaletteGroup group, String currentValue, int outlineColor,
            BottomSheetDialog sheet) {
        int padH = res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal);
        int padV = res.getDimensionPixelSize(R.dimen.settings_vertical_pad_large);
        int padGap = res.getDimensionPixelSize(R.dimen.settings_item_gap);

        // Section header
        TextView header = new TextView(ctx);
        header.setText(group.label);
        header.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                res.getDimension(R.dimen.settings_sheet_category_text_size));
        header.setTextColor(ctx.getColor(R.color.materialColorOnSurfaceVariant));
        header.setAllCaps(true);
        header.setLetterSpacing(0.1f);
        header.setPadding(padH, padV, padH, padGap);
        parent.addView(header);

        // Horizontal scrolling row of swatches — edge to edge
        int scrollPad = res.getDimensionPixelSize(R.dimen.settings_card_margin_horizontal);
        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setClipToPadding(false);
        hScroll.setPadding(scrollPad, 0, scrollPad, 0);
        hScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout swatchRow = new LinearLayout(ctx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);

        for (SwatchEntry entry : group.swatches) {
            addSwatch(swatchRow, ctx, res, entry, currentValue, outlineColor, sheet);
        }

        hScroll.addView(swatchRow);
        parent.addView(hScroll);
    }

    private void addSwatch(LinearLayout row, Context ctx, Resources res, SwatchEntry entry,
            String currentValue, int outlineColor, BottomSheetDialog sheet) {
        int swatchSize = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
        int margin = res.getDimensionPixelSize(R.dimen.settings_item_gap);

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
            circle.setStroke(res.getDimensionPixelSize(
                    R.dimen.settings_color_picker_selected_stroke), outlineColor);
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
}
