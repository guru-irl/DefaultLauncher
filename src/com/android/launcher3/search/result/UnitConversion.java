/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.search.result;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.List;

/**
 * A unit conversion result showing conversions for a given input value and unit.
 */
public class UnitConversion implements Launchable {

    public final double inputValue;
    public final String inputUnit;
    public final String dimension;
    public final List<ConvertedValue> conversions;

    public UnitConversion(double inputValue, String inputUnit, String dimension,
            List<ConvertedValue> conversions) {
        this.inputValue = inputValue;
        this.inputUnit = inputUnit;
        this.dimension = dimension;
        this.conversions = conversions;
    }

    @Override
    public boolean launch(Context context) {
        // Copy all conversions to clipboard
        StringBuilder sb = new StringBuilder();
        sb.append(formatValue(inputValue)).append(" ").append(inputUnit).append(" =\n");
        for (ConvertedValue cv : conversions) {
            sb.append(cv.formatted()).append("\n");
        }
        ClipboardManager cm = context.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Unit Conversion", sb.toString().trim()));
        Toast.makeText(context, "Copied conversions", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public String getLabel() {
        return formatValue(inputValue) + " " + inputUnit;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null;
    }

    public static String formatValue(double value) {
        return NumberFormatUtil.format(value, 6);
    }

    /** A single converted value with its unit. */
    public static class ConvertedValue {
        public final double value;
        public final String unit;

        public ConvertedValue(double value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        public String formatted() {
            return formatValue(value) + " " + unit;
        }
    }
}
