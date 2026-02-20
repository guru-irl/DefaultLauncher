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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;
import com.google.android.material.slider.Slider;

public class M3SliderPreference extends Preference {

    public interface OnTrackingStopListener {
        void onStopTracking(int finalValue);
    }

    private float mMin = 0f;
    private float mMax = 100f;
    private float mStepSize = 1f;
    private float mValue;
    private boolean mShowValue = true;
    private OnTrackingStopListener mTrackingStopListener;

    private static final String TAG_SLIDER = "m3_slider_container";
    private static final String TAG_SLIDER_WIDGET = "m3_slider_widget";
    private static final String TAG_SLIDER_TITLE = "m3_slider_title";
    private static final String TAG_SLIDER_VALUE = "m3_slider_value";

    public M3SliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs,
                androidx.preference.R.styleable.SeekBarPreference);
        try {
            mMin = a.getInt(androidx.preference.R.styleable.SeekBarPreference_min, 0);
            mShowValue = a.getBoolean(
                    androidx.preference.R.styleable.SeekBarPreference_showSeekBarValue, true);
        } finally {
            a.recycle();
        }

        TypedArray b = context.obtainStyledAttributes(attrs,
                new int[]{android.R.attr.max, android.R.attr.defaultValue});
        try {
            mMax = b.getInt(0, 100);
            mValue = b.getInt(1, (int) mMin);
        } finally {
            b.recycle();
        }

        mStepSize = 1f;
    }

    public void setMin(int min) { mMin = min; }
    public void setMax(int max) { mMax = max; }
    public void setStepSize(float stepSize) { mStepSize = stepSize; }
    public int getValue() { return (int) mValue; }

    public void setValue(int value) {
        mValue = value;
        persistInt(value);
        notifyChanged();
    }

    public void setOnTrackingStopListener(OnTrackingStopListener listener) {
        mTrackingStopListener = listener;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, (int) mMin);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        int def = defaultValue != null ? (int) defaultValue : (int) mMin;
        mValue = getPersistedInt(def);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        ViewGroup root = (ViewGroup) holder.itemView;

        // Reuse cached view hierarchy if present — only update dynamic values
        View existing = root.findViewWithTag(TAG_SLIDER);
        if (existing != null) {
            Slider slider = existing.findViewWithTag(TAG_SLIDER_WIDGET);
            TextView titleView = existing.findViewWithTag(TAG_SLIDER_TITLE);
            if (slider != null && titleView != null) {
                slider.setValue(Math.max(mMin, Math.min(mMax, mValue)));
                titleView.setText(getTitle());
                TextView valueView = existing.findViewWithTag(TAG_SLIDER_VALUE);
                if (valueView != null) valueView.setText(String.valueOf((int) mValue));
                return;
            }
            // Stale container — remove and rebuild
            root.removeView(existing);
        }

        for (int i = 0; i < root.getChildCount(); i++) {
            root.getChildAt(i).setVisibility(View.GONE);
        }
        root.setMinimumHeight(0);

        Context ctx = getContext();
        Resources res = ctx.getResources();

        // Root already has 16dp horizontal padding from listPreferredItemPaddingStart/End.

        LinearLayout outer = new LinearLayout(ctx);
        outer.setTag(TAG_SLIDER);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        outer.setPadding(0,
                res.getDimensionPixelSize(R.dimen.settings_slider_outer_pad_top), 0,
                res.getDimensionPixelSize(R.dimen.settings_slider_outer_pad_bottom));
        outer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Title
        TextView titleView = new TextView(ctx);
        titleView.setTag(TAG_SLIDER_TITLE);
        titleView.setText(getTitle());
        android.util.TypedValue tv = new android.util.TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.textAppearanceListItem, tv, true);
        titleView.setTextAppearance(tv.resourceId);
        titleView.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(titleView);

        // Slider row: [Slider (weight 1)] + [Value label]
        LinearLayout sliderRow = new LinearLayout(ctx);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        sliderRow.setClipChildren(false);
        sliderRow.setClipToPadding(false);
        LinearLayout.LayoutParams sliderRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderRowParams.topMargin = res.getDimensionPixelSize(
                R.dimen.settings_vertical_pad_medium);
        sliderRow.setLayoutParams(sliderRowParams);

        // Slider — use native thumb sizing to preserve built-in animations
        Slider slider = new Slider(ctx);
        slider.setTag(TAG_SLIDER_WIDGET);
        slider.setValueFrom(mMin);
        slider.setValueTo(mMax);
        slider.setStepSize(mStepSize);
        slider.setValue(Math.max(mMin, Math.min(mMax, mValue)));
        slider.setTickVisible(true);
        slider.setTrackHeight(res.getDimensionPixelSize(
                R.dimen.settings_slider_track_height));
        slider.setThumbWidth(res.getDimensionPixelSize(
                R.dimen.settings_slider_thumb_width));
        slider.setThumbHeight(res.getDimensionPixelSize(
                R.dimen.settings_slider_thumb_height));
        slider.setThumbTintList(ColorStateList.valueOf(
                ctx.getColor(R.color.materialColorPrimary)));
        slider.setThumbElevation(0f);

        slider.setPadding(0, slider.getPaddingTop(), 0, slider.getPaddingBottom());

        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int trackOffset = res.getDimensionPixelSize(
                R.dimen.settings_slider_track_offset);
        sliderParams.setMarginStart(-trackOffset);
        sliderParams.setMarginEnd(0);
        slider.setLayoutParams(sliderParams);
        sliderRow.addView(slider);

        // Value label (right of slider, bold)
        TextView valueText = null;
        if (mShowValue) {
            valueText = new TextView(ctx);
            valueText.setTag(TAG_SLIDER_VALUE);
            valueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            valueText.setTypeface(Typeface.DEFAULT_BOLD);
            valueText.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
            valueText.setText(String.valueOf((int) mValue));
            valueText.setMinWidth(res.getDimensionPixelSize(
                    R.dimen.settings_value_label_min_width));
            valueText.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams valParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            valParams.setMarginStart(res.getDimensionPixelSize(
                    R.dimen.settings_vertical_pad_small));
            valueText.setLayoutParams(valParams);
            sliderRow.addView(valueText);
        }
        outer.addView(sliderRow);

        final TextView valueFinal = valueText;
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                mValue = value;
                persistInt((int) value);
                if (valueFinal != null) {
                    valueFinal.setText(String.valueOf((int) value));
                }
                callChangeListener((int) value);
            }
        });

        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider s) {}

            @Override
            public void onStopTrackingTouch(Slider s) {
                if (mTrackingStopListener != null) {
                    mTrackingStopListener.onStopTracking((int) s.getValue());
                }
            }
        });

        root.addView(outer);
    }
}
