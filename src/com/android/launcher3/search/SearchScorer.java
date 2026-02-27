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
package com.android.launcher3.search;

/**
 * Search scoring using Jaro-Winkler similarity with prefix/substring bonuses.
 * Inspired by Kvaesitso's scoring model. Zero external dependencies.
 */
public final class SearchScorer {

    private static final float PREFIX_BONUS = 0.8f;
    private static final float SUBSTRING_BONUS = 0.4f;
    private static final float THRESHOLD = 0.8f;

    private SearchScorer() {}

    /**
     * Computes the Jaro-Winkler similarity between two strings (0.0–1.0).
     */
    public static float jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0f;
        if (s1.equals(s2)) return 1f;

        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0f;

        int matchWindow = Math.max(len1, len2) / 2 - 1;
        if (matchWindow < 0) matchWindow = 0;

        boolean[] matched1 = new boolean[len1];
        boolean[] matched2 = new boolean[len2];

        int matches = 0;
        int transpositions = 0;

        // Find matching characters
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            for (int j = start; j < end; j++) {
                if (matched2[j] || s1.charAt(i) != s2.charAt(j)) continue;
                matched1[i] = true;
                matched2[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0f;

        // Count transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!matched1[i]) continue;
            while (!matched2[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        float jaro = ((float) matches / len1
                + (float) matches / len2
                + (float) (matches - transpositions / 2) / matches) / 3f;

        // Winkler bonus: boost for common prefix (up to 4 chars)
        int prefixLen = 0;
        int maxPrefix = Math.min(4, Math.min(len1, len2));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLen++;
            } else {
                break;
            }
        }

        return jaro + prefixLen * 0.1f * (1f - jaro);
    }

    /**
     * Scores a query against a target string using Jaro-Winkler plus
     * prefix and substring bonuses (Kvaesitso approach).
     *
     * @return Score &gt; 0 if above the minimum threshold, 0 otherwise.
     *         Not capped at 1.0 so that prefix matches always outrank substring matches.
     */
    public static float score(String query, String target) {
        if (query == null || target == null) return 0f;

        String queryLower = query.toLowerCase();
        String targetLower = target.toLowerCase();

        float jw = jaroWinklerSimilarity(queryLower, targetLower);

        float bonus = 0f;
        if (targetLower.startsWith(queryLower)) {
            bonus = PREFIX_BONUS;
        } else if (targetLower.contains(queryLower)) {
            bonus = SUBSTRING_BONUS;
        }

        float total = jw + bonus;
        return total >= THRESHOLD ? total : 0f;
    }
}
