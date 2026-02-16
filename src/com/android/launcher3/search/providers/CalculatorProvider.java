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

import android.os.Handler;

import com.android.launcher3.search.result.CalculatorResult;

import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.License;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Evaluates math expressions using mxparser (same library as Kvaesitso).
 * Supports sin/cos/log/pi/sqrt/etc.
 */
public class CalculatorProvider implements SearchProvider<CalculatorResult> {

    private final Handler mResultHandler;
    private static boolean sLicenseConfirmed = false;

    public CalculatorProvider() {
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
        if (!sLicenseConfirmed) {
            License.iConfirmNonCommercialUse("DefaultLauncher");
            sLicenseConfirmed = true;
        }
    }

    @Override
    public void search(String query, Consumer<List<CalculatorResult>> callback) {
        String trimmed = query.trim();

        // Quick pre-check: must contain at least one digit or math function
        if (!containsMathContent(trimmed)) {
            mResultHandler.post(() -> callback.accept(Collections.emptyList()));
            return;
        }

        try {
            Expression expression = new Expression(trimmed);
            if (expression.checkSyntax()) {
                double result = expression.calculate();
                if (!Double.isNaN(result) && !Double.isInfinite(result)) {
                    CalculatorResult calcResult = new CalculatorResult(trimmed, result);
                    mResultHandler.post(() -> callback.accept(List.of(calcResult)));
                    return;
                }
            }
        } catch (Exception ignored) {
            // Not a valid expression
        }

        mResultHandler.post(() -> callback.accept(Collections.emptyList()));
    }

    @Override
    public void cancel() {
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "calculator";
    }

    private static boolean containsMathContent(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '+' || c == '-' || c == '*' || c == '/'
                    || c == '^' || c == '(' || c == ')' || c == '%') {
                return true;
            }
        }
        // Check for function names
        String lower = s.toLowerCase();
        return lower.contains("sin") || lower.contains("cos") || lower.contains("tan")
                || lower.contains("log") || lower.contains("sqrt") || lower.contains("pi")
                || lower.contains("exp") || lower.contains("abs");
    }
}
