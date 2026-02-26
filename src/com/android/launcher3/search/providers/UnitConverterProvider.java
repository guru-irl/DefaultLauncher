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
package com.android.launcher3.search.providers;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.os.Handler;

import com.android.launcher3.search.result.UnitConversion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses unit conversion queries (e.g. "100 km to miles", "50 kg in lbs")
 * and returns conversion results using hardcoded unit tables.
 */
public class UnitConverterProvider implements SearchProvider<UnitConversion> {

    // Pattern: "100 km to miles" or "100 km in miles"
    private static final Pattern CONVERT_TO_PATTERN =
            Pattern.compile("(\\d+\\.?\\d*)\\s*(\\w+)\\s+(?:to|in|>|=)\\s+(\\w+)",
                    Pattern.CASE_INSENSITIVE);
    // Pattern: "100 km" (show all conversions for that unit)
    private static final Pattern UNIT_PATTERN =
            Pattern.compile("^(\\d+\\.?\\d*)\\s*(\\w+)$", Pattern.CASE_INSENSITIVE);

    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    // Unit tables: map unit name -> (base factor, dimension)
    // All units are stored relative to a base unit per dimension
    private static final Map<String, UnitDef> UNITS = new HashMap<>();

    static {
        // Length (base: meter)
        addUnit("m", "meter", "meters", 1.0, "Length");
        addUnit("km", "kilometer", "kilometers", 1000.0, "Length");
        addUnit("cm", "centimeter", "centimeters", 0.01, "Length");
        addUnit("mm", "millimeter", "millimeters", 0.001, "Length");
        addUnit("mi", "mile", "miles", 1609.344, "Length");
        addUnit("yd", "yard", "yards", 0.9144, "Length");
        addUnit("ft", "foot", "feet", 0.3048, "Length");
        addUnit("in", "inch", "inches", 0.0254, "Length");

        // Mass (base: kilogram)
        addUnit("kg", "kilogram", "kilograms", 1.0, "Mass");
        addUnit("g", "gram", "grams", 0.001, "Mass");
        addUnit("mg", "milligram", "milligrams", 0.000001, "Mass");
        addUnit("lb", "pound", "pounds", 0.453592, "Mass");
        addUnit("lbs", null, null, 0.453592, "Mass");
        addUnit("oz", "ounce", "ounces", 0.0283495, "Mass");
        addUnit("ton", "tonne", "tonnes", 1000.0, "Mass");

        // Temperature (special handling)
        addUnit("c", "celsius", null, 1.0, "Temperature");
        addUnit("f", "fahrenheit", null, 1.0, "Temperature");
        addUnit("k", "kelvin", null, 1.0, "Temperature");

        // Volume (base: liter)
        addUnit("l", "liter", "liters", 1.0, "Volume");
        addUnit("ml", "milliliter", "milliliters", 0.001, "Volume");
        addUnit("gal", "gallon", "gallons", 3.78541, "Volume");
        addUnit("qt", "quart", "quarts", 0.946353, "Volume");
        addUnit("pt", "pint", "pints", 0.473176, "Volume");
        addUnit("cup", "cups", null, 0.236588, "Volume");
        addUnit("floz", null, null, 0.0295735, "Volume");
        addUnit("tbsp", "tablespoon", "tablespoons", 0.0147868, "Volume");
        addUnit("tsp", "teaspoon", "teaspoons", 0.00492892, "Volume");

        // Data (base: byte)
        addUnit("b", "byte", "bytes", 1.0, "Data");
        addUnit("kb", "kilobyte", "kilobytes", 1024.0, "Data");
        addUnit("mb", "megabyte", "megabytes", 1048576.0, "Data");
        addUnit("gb", "gigabyte", "gigabytes", 1073741824.0, "Data");
        addUnit("tb", "terabyte", "terabytes", 1099511627776.0, "Data");

        // Speed (base: m/s)
        addUnit("mps", null, null, 1.0, "Speed");
        addUnit("kph", "kmh", null, 0.277778, "Speed");
        addUnit("mph", null, null, 0.44704, "Speed");
        addUnit("knot", "knots", null, 0.514444, "Speed");

        // Area (base: m²)
        addUnit("sqm", null, null, 1.0, "Area");
        addUnit("sqkm", null, null, 1000000.0, "Area");
        addUnit("sqmi", null, null, 2589988.0, "Area");
        addUnit("sqft", null, null, 0.092903, "Area");
        addUnit("acre", "acres", null, 4046.86, "Area");
        addUnit("hectare", "hectares", "ha", 10000.0, "Area");

        // Time (base: second)
        addUnit("s", "sec", "second", 1.0, "Time");
        addUnit("seconds", null, null, 1.0, "Time");
        addUnit("min", "minute", "minutes", 60.0, "Time");
        addUnit("hr", "hour", "hours", 3600.0, "Time");
        addUnit("day", "days", null, 86400.0, "Time");
        addUnit("week", "weeks", null, 604800.0, "Time");
        addUnit("month", "months", null, 2629746.0, "Time");
        addUnit("year", "years", null, 31556952.0, "Time");
    }

    private static void addUnit(String key, String alias1, String alias2,
            double factor, String dimension) {
        UnitDef def = new UnitDef(key, factor, dimension);
        UNITS.put(key.toLowerCase(), def);
        if (alias1 != null) UNITS.put(alias1.toLowerCase(), def);
        if (alias2 != null) UNITS.put(alias2.toLowerCase(), def);
    }

    public UnitConverterProvider() {
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<UnitConversion>> callback) {
        String trimmed = query.trim();
        mCancelled = false;

        MODEL_EXECUTOR.execute(() -> {
            if (mCancelled) return;

            // Try "100 km to miles" pattern first
            Matcher toMatcher = CONVERT_TO_PATTERN.matcher(trimmed);
            if (toMatcher.matches()) {
                double value = Double.parseDouble(toMatcher.group(1));
                String fromUnit = toMatcher.group(2).toLowerCase();
                String toUnit = toMatcher.group(3).toLowerCase();

                UnitDef from = UNITS.get(fromUnit);
                UnitDef to = UNITS.get(toUnit);

                if (from != null && to != null && from.dimension.equals(to.dimension)) {
                    double converted = convert(value, from, to);
                    UnitConversion.ConvertedValue cv =
                            new UnitConversion.ConvertedValue(converted, to.displayName);
                    UnitConversion result = new UnitConversion(
                            value, from.displayName, from.dimension, List.of(cv));
                    if (!mCancelled) {
                        mResultHandler.post(() -> callback.accept(List.of(result)));
                    }
                    return;
                }
            }

            if (mCancelled) return;

            // Try "100 km" pattern (show all conversions in same dimension)
            Matcher unitMatcher = UNIT_PATTERN.matcher(trimmed);
            if (unitMatcher.matches()) {
                double value = Double.parseDouble(unitMatcher.group(1));
                String unitStr = unitMatcher.group(2).toLowerCase();

                UnitDef from = UNITS.get(unitStr);
                if (from != null) {
                    List<UnitConversion.ConvertedValue> conversions = new ArrayList<>();
                    // Find all units in same dimension
                    Map<String, UnitDef> seenDefs = new LinkedHashMap<>();
                    for (Map.Entry<String, UnitDef> entry : UNITS.entrySet()) {
                        UnitDef def = entry.getValue();
                        if (def.dimension.equals(from.dimension)
                                && def != from
                                && !seenDefs.containsKey(def.displayName)) {
                            seenDefs.put(def.displayName, def);
                        }
                    }

                    int count = 0;
                    for (UnitDef to : seenDefs.values()) {
                        if (count >= 5) break;
                        double converted = convert(value, from, to);
                        conversions.add(
                                new UnitConversion.ConvertedValue(converted, to.displayName));
                        count++;
                    }

                    if (!conversions.isEmpty()) {
                        UnitConversion result = new UnitConversion(
                                value, from.displayName, from.dimension, conversions);
                        if (!mCancelled) {
                            mResultHandler.post(() -> callback.accept(List.of(result)));
                        }
                        return;
                    }
                }
            }

            if (!mCancelled) {
                mResultHandler.post(() -> callback.accept(Collections.emptyList()));
            }
        });
    }

    @Override
    public void cancel() {
        mCancelled = true;
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "unit_converter";
    }

    @Override
    public int minQueryLength() {
        return 2;
    }

    private static double convert(double value, UnitDef from, UnitDef to) {
        if (from.dimension.equals("Temperature")) {
            return convertTemperature(value, from.displayName, to.displayName);
        }
        // Convert from -> base -> to
        double base = value * from.factor;
        return base / to.factor;
    }

    private static double convertTemperature(double value, String from, String to) {
        // Convert to Celsius first
        double celsius;
        switch (from.toLowerCase()) {
            case "f": case "fahrenheit":
                celsius = (value - 32) * 5.0 / 9.0;
                break;
            case "k": case "kelvin":
                celsius = value - 273.15;
                break;
            default: // celsius
                celsius = value;
                break;
        }
        // Convert from Celsius to target
        switch (to.toLowerCase()) {
            case "f": case "fahrenheit":
                return celsius * 9.0 / 5.0 + 32;
            case "k": case "kelvin":
                return celsius + 273.15;
            default: // celsius
                return celsius;
        }
    }

    private static class UnitDef {
        final String displayName;
        final double factor;
        final String dimension;

        UnitDef(String displayName, double factor, String dimension) {
            this.displayName = displayName;
            this.factor = factor;
            this.dimension = dimension;
        }
    }
}
