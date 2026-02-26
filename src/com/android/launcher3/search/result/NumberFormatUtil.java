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

/**
 * Shared number formatting for search results (calculator, unit converter).
 * Renders integers without decimals, and floating-point values with the specified
 * number of significant figures (trailing zeros stripped).
 */
public final class NumberFormatUtil {

    private NumberFormatUtil() {}

    /**
     * Formats a numeric value for display.
     *
     * @param value   the value to format
     * @param sigFigs significant figures for non-integer values
     * @return formatted string (e.g. "42", "3.14159")
     */
    public static String format(double value, int sigFigs) {
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String formatted = String.format("%." + sigFigs + "g", value);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }
}
