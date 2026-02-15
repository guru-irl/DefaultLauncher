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
package com.android.launcher3.allapps;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps tonal palette color resource names to android.R.color IDs.
 * Uses the system dynamic color palette (accent1=Primary, accent2=Secondary,
 * accent3=Tertiary, neutral1=Neutral, neutral2=Neutral Variant) with all
 * 13 tonal steps (0-1000). These colors adapt to wallpaper and theme.
 */
public class AllAppsColorResolver {

    /** A group of tonal swatches under a section header. */
    public static class PaletteGroup {
        public final String label;
        public final List<SwatchEntry> swatches;

        PaletteGroup(String label, List<SwatchEntry> swatches) {
            this.label = label;
            this.swatches = swatches;
        }
    }

    public static class SwatchEntry {
        public final String displayName;
        public final String resourceName;
        public final int androidColorResId;

        SwatchEntry(String displayName, String resourceName, int androidColorResId) {
            this.displayName = displayName;
            this.resourceName = resourceName;
            this.androidColorResId = androidColorResId;
        }
    }

    // Tonal steps available in the Android system palette (API 31+)
    private static final int[] TONES = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

    // Complete map of resource name → android.R.color ID
    private static final LinkedHashMap<String, Integer> COLOR_MAP = new LinkedHashMap<>();

    static {
        // Primary (accent1)
        COLOR_MAP.put("system_accent1_0", android.R.color.system_accent1_0);
        COLOR_MAP.put("system_accent1_10", android.R.color.system_accent1_10);
        COLOR_MAP.put("system_accent1_50", android.R.color.system_accent1_50);
        COLOR_MAP.put("system_accent1_100", android.R.color.system_accent1_100);
        COLOR_MAP.put("system_accent1_200", android.R.color.system_accent1_200);
        COLOR_MAP.put("system_accent1_300", android.R.color.system_accent1_300);
        COLOR_MAP.put("system_accent1_400", android.R.color.system_accent1_400);
        COLOR_MAP.put("system_accent1_500", android.R.color.system_accent1_500);
        COLOR_MAP.put("system_accent1_600", android.R.color.system_accent1_600);
        COLOR_MAP.put("system_accent1_700", android.R.color.system_accent1_700);
        COLOR_MAP.put("system_accent1_800", android.R.color.system_accent1_800);
        COLOR_MAP.put("system_accent1_900", android.R.color.system_accent1_900);
        COLOR_MAP.put("system_accent1_1000", android.R.color.system_accent1_1000);

        // Secondary (accent2)
        COLOR_MAP.put("system_accent2_0", android.R.color.system_accent2_0);
        COLOR_MAP.put("system_accent2_10", android.R.color.system_accent2_10);
        COLOR_MAP.put("system_accent2_50", android.R.color.system_accent2_50);
        COLOR_MAP.put("system_accent2_100", android.R.color.system_accent2_100);
        COLOR_MAP.put("system_accent2_200", android.R.color.system_accent2_200);
        COLOR_MAP.put("system_accent2_300", android.R.color.system_accent2_300);
        COLOR_MAP.put("system_accent2_400", android.R.color.system_accent2_400);
        COLOR_MAP.put("system_accent2_500", android.R.color.system_accent2_500);
        COLOR_MAP.put("system_accent2_600", android.R.color.system_accent2_600);
        COLOR_MAP.put("system_accent2_700", android.R.color.system_accent2_700);
        COLOR_MAP.put("system_accent2_800", android.R.color.system_accent2_800);
        COLOR_MAP.put("system_accent2_900", android.R.color.system_accent2_900);
        COLOR_MAP.put("system_accent2_1000", android.R.color.system_accent2_1000);

        // Tertiary (accent3)
        COLOR_MAP.put("system_accent3_0", android.R.color.system_accent3_0);
        COLOR_MAP.put("system_accent3_10", android.R.color.system_accent3_10);
        COLOR_MAP.put("system_accent3_50", android.R.color.system_accent3_50);
        COLOR_MAP.put("system_accent3_100", android.R.color.system_accent3_100);
        COLOR_MAP.put("system_accent3_200", android.R.color.system_accent3_200);
        COLOR_MAP.put("system_accent3_300", android.R.color.system_accent3_300);
        COLOR_MAP.put("system_accent3_400", android.R.color.system_accent3_400);
        COLOR_MAP.put("system_accent3_500", android.R.color.system_accent3_500);
        COLOR_MAP.put("system_accent3_600", android.R.color.system_accent3_600);
        COLOR_MAP.put("system_accent3_700", android.R.color.system_accent3_700);
        COLOR_MAP.put("system_accent3_800", android.R.color.system_accent3_800);
        COLOR_MAP.put("system_accent3_900", android.R.color.system_accent3_900);
        COLOR_MAP.put("system_accent3_1000", android.R.color.system_accent3_1000);

        // Neutral (neutral1)
        COLOR_MAP.put("system_neutral1_0", android.R.color.system_neutral1_0);
        COLOR_MAP.put("system_neutral1_10", android.R.color.system_neutral1_10);
        COLOR_MAP.put("system_neutral1_50", android.R.color.system_neutral1_50);
        COLOR_MAP.put("system_neutral1_100", android.R.color.system_neutral1_100);
        COLOR_MAP.put("system_neutral1_200", android.R.color.system_neutral1_200);
        COLOR_MAP.put("system_neutral1_300", android.R.color.system_neutral1_300);
        COLOR_MAP.put("system_neutral1_400", android.R.color.system_neutral1_400);
        COLOR_MAP.put("system_neutral1_500", android.R.color.system_neutral1_500);
        COLOR_MAP.put("system_neutral1_600", android.R.color.system_neutral1_600);
        COLOR_MAP.put("system_neutral1_700", android.R.color.system_neutral1_700);
        COLOR_MAP.put("system_neutral1_800", android.R.color.system_neutral1_800);
        COLOR_MAP.put("system_neutral1_900", android.R.color.system_neutral1_900);
        COLOR_MAP.put("system_neutral1_1000", android.R.color.system_neutral1_1000);

        // Neutral Variant (neutral2)
        COLOR_MAP.put("system_neutral2_0", android.R.color.system_neutral2_0);
        COLOR_MAP.put("system_neutral2_10", android.R.color.system_neutral2_10);
        COLOR_MAP.put("system_neutral2_50", android.R.color.system_neutral2_50);
        COLOR_MAP.put("system_neutral2_100", android.R.color.system_neutral2_100);
        COLOR_MAP.put("system_neutral2_200", android.R.color.system_neutral2_200);
        COLOR_MAP.put("system_neutral2_300", android.R.color.system_neutral2_300);
        COLOR_MAP.put("system_neutral2_400", android.R.color.system_neutral2_400);
        COLOR_MAP.put("system_neutral2_500", android.R.color.system_neutral2_500);
        COLOR_MAP.put("system_neutral2_600", android.R.color.system_neutral2_600);
        COLOR_MAP.put("system_neutral2_700", android.R.color.system_neutral2_700);
        COLOR_MAP.put("system_neutral2_800", android.R.color.system_neutral2_800);
        COLOR_MAP.put("system_neutral2_900", android.R.color.system_neutral2_900);
        COLOR_MAP.put("system_neutral2_1000", android.R.color.system_neutral2_1000);
    }

    private static final String[][] PALETTE_GROUPS = {
            {"Primary", "system_accent1_"},
            {"Secondary", "system_accent2_"},
            {"Tertiary", "system_accent3_"},
            {"Neutral", "system_neutral1_"},
            {"Neutral Variant", "system_neutral2_"},
    };

    /**
     * Resolves a stored color resource name to an actual color int.
     * Returns 0 if the name is null, empty, or not found.
     */
    public static int resolveColorByName(Context ctx, String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) return 0;
        Integer resId = COLOR_MAP.get(resourceName);
        if (resId == null) return 0;
        return ctx.getColor(resId);
    }

    /**
     * Returns palette groups with section headers for the color picker UI.
     */
    public static List<PaletteGroup> getPaletteGroups() {
        List<PaletteGroup> groups = new ArrayList<>();
        for (String[] group : PALETTE_GROUPS) {
            String label = group[0];
            String prefix = group[1];
            List<SwatchEntry> swatches = new ArrayList<>();
            for (int tone : TONES) {
                String resName = prefix + tone;
                Integer resId = COLOR_MAP.get(resName);
                if (resId != null) {
                    swatches.add(new SwatchEntry(String.valueOf(tone), resName, resId));
                }
            }
            groups.add(new PaletteGroup(label, swatches));
        }
        return groups;
    }

    /**
     * Converts a stored resource name to a human-readable display name.
     * e.g., "system_accent1_600" → "Primary 600"
     */
    public static String toDisplayName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) return "Default";
        for (String[] group : PALETTE_GROUPS) {
            String prefix = group[1];
            if (resourceName.startsWith(prefix)) {
                String tone = resourceName.substring(prefix.length());
                return group[0] + " " + tone;
            }
        }
        return resourceName;
    }
}
