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

/**
 * A calculator result from evaluating a math expression.
 */
public class CalculatorResult implements Launchable {

    public final String expression;
    public final double result;
    public final String formattedResult;

    public CalculatorResult(String expression, double result) {
        this.expression = expression;
        this.result = result;
        this.formattedResult = formatResult(result);
    }

    @Override
    public boolean launch(Context context) {
        // Copy result to clipboard
        ClipboardManager cm = context.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Calculator Result", formattedResult));
        Toast.makeText(context, "Copied " + formattedResult, Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public String getLabel() {
        return expression + " = " + formattedResult;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null;
    }

    /** Returns hex representation if result is an integer. */
    public String getHex() {
        if (isInteger()) {
            return "0x" + Long.toHexString((long) result).toUpperCase();
        }
        return null;
    }

    /** Returns binary representation if result is an integer. */
    public String getBinary() {
        if (isInteger() && Math.abs(result) < 1_000_000) {
            return "0b" + Long.toBinaryString((long) result);
        }
        return null;
    }

    /** Returns octal representation if result is an integer. */
    public String getOctal() {
        if (isInteger()) {
            return "0o" + Long.toOctalString((long) result);
        }
        return null;
    }

    public boolean isInteger() {
        return result == Math.floor(result) && !Double.isInfinite(result);
    }

    private static String formatResult(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        // Use up to 10 significant digits
        String formatted = String.format("%.10g", value);
        // Strip trailing zeros after decimal point
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }
}
