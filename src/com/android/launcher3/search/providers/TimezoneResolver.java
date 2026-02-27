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

import android.icu.text.TimeZoneNames;
import android.icu.util.TimeZone.SystemTimeZoneType;

import androidx.annotation.Nullable;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Singleton that auto-generates timezone lookup data from platform APIs plus a small manual layer.
 *
 * <p>Four layers, applied in order (later layers overwrite on conflict):
 * <ul>
 *   <li><b>Layer A — IANA city names (~400)</b>: city segment from {@link ZoneId#getAvailableZoneIds()}</li>
 *   <li><b>Layer B — CLDR exemplar cities</b>: ICU {@link TimeZoneNames} localized city names</li>
 *   <li><b>Layer C — Country names (~250)</b>: {@link Locale#getISOCountries()} → first canonical zone</li>
 *   <li><b>Layer D — Manual abbreviations + aliases (~60)</b>: EST, PST, NYC, etc.</li>
 * </ul>
 */
public class TimezoneResolver {

    private static volatile TimezoneResolver sInstance;

    private final Map<String, ZoneId> mLookup;

    public static TimezoneResolver getInstance() {
        if (sInstance == null) {
            synchronized (TimezoneResolver.class) {
                if (sInstance == null) {
                    sInstance = new TimezoneResolver();
                }
            }
        }
        return sInstance;
    }

    /** Resolves a location name, country, or abbreviation to a ZoneId. */
    @Nullable
    public ZoneId resolve(String input) {
        String key = input.toLowerCase(Locale.ENGLISH)
                .replaceAll("\\s+time$", "")
                .trim();
        return mLookup.get(key);
    }

    private TimezoneResolver() {
        mLookup = new HashMap<>(800);
        loadIanaCities();              // Layer A
        loadCldrExemplarCities();      // Layer B
        loadCountries();               // Layer C
        loadAbbreviationsAndAliases(); // Layer D (overwrites on conflict)
    }

    /** Layer A: Extract city names from IANA zone IDs (e.g. "America/New_York" → "new york"). */
    private void loadIanaCities() {
        for (String id : ZoneId.getAvailableZoneIds()) {
            int slash = id.lastIndexOf('/');
            if (slash < 0) continue;
            String city = id.substring(slash + 1).replace('_', ' ').toLowerCase(Locale.ENGLISH);
            if (city.isEmpty()) continue;
            mLookup.put(city, ZoneId.of(id));
        }
    }

    /** Layer B: CLDR exemplar city names via ICU (localized names that differ from IANA paths). */
    private void loadCldrExemplarCities() {
        TimeZoneNames tzNames = TimeZoneNames.getInstance(Locale.ENGLISH);
        for (String id : ZoneId.getAvailableZoneIds()) {
            String exemplar = tzNames.getExemplarLocationName(id);
            if (exemplar == null || exemplar.isEmpty()) continue;
            String key = exemplar.toLowerCase(Locale.ENGLISH);
            // Don't overwrite existing — IANA cities are authoritative
            mLookup.putIfAbsent(key, ZoneId.of(id));
        }
    }

    /** Layer C: Country display names → first canonical timezone for that country. */
    private void loadCountries() {
        for (String code : Locale.getISOCountries()) {
            String country = Locale.of("", code)
                    .getDisplayCountry(Locale.ENGLISH)
                    .toLowerCase(Locale.ENGLISH);
            if (country.isEmpty()) continue;
            // Already have this key from Layer A/B — skip
            if (mLookup.containsKey(country)) continue;

            Set<String> zones = android.icu.util.TimeZone.getAvailableIDs(
                    SystemTimeZoneType.CANONICAL_LOCATION, code, null);
            if (zones == null || zones.isEmpty()) continue;

            // Pick the first canonical zone
            String firstZone = zones.iterator().next();
            mLookup.put(country, ZoneId.of(firstZone));
        }
    }

    /**
     * Layer D: Manual abbreviations and city aliases that can't be derived from APIs.
     * These overwrite any conflicting auto-generated entries.
     */
    private void loadAbbreviationsAndAliases() {
        // -- Timezone abbreviations (ambiguous ones default to US context) --
        put("est", "America/New_York");
        put("edt", "America/New_York");
        put("cst", "America/Chicago");
        put("cdt", "America/Chicago");
        put("mst", "America/Denver");
        put("mdt", "America/Denver");
        put("pst", "America/Los_Angeles");
        put("pdt", "America/Los_Angeles");
        put("akst", "America/Anchorage");
        put("hst", "Pacific/Honolulu");
        put("gmt", "GMT");
        put("utc", "UTC");
        put("bst", "Europe/London");
        put("cet", "Europe/Paris");
        put("eet", "Europe/Athens");
        put("ist", "Asia/Kolkata");
        put("jst", "Asia/Tokyo");
        put("kst", "Asia/Seoul");
        put("cst china", "Asia/Shanghai");
        put("hkt", "Asia/Hong_Kong");
        put("sgt", "Asia/Singapore");
        put("aest", "Australia/Sydney");
        put("acst", "Australia/Adelaide");
        put("awst", "Australia/Perth");
        put("nzst", "Pacific/Auckland");
        put("pkt", "Asia/Karachi");
        put("bdt", "Asia/Dhaka");
        put("npt", "Asia/Kathmandu");
        put("ict", "Asia/Bangkok");
        put("wib", "Asia/Jakarta");
        put("msk", "Europe/Moscow");
        put("gst", "Asia/Dubai");
        put("ast", "Asia/Riyadh");
        put("brt", "America/Sao_Paulo");
        put("art", "America/Argentina/Buenos_Aires");

        // -- City aliases (no IANA zone ID of their own) --
        put("nyc", "America/New_York");
        put("la", "America/Los_Angeles");
        put("sf", "America/Los_Angeles");
        put("san francisco", "America/Los_Angeles");
        put("houston", "America/Chicago");
        put("dallas", "America/Chicago");
        put("miami", "America/New_York");
        put("boston", "America/New_York");
        put("atlanta", "America/New_York");
        put("seattle", "America/Los_Angeles");
        put("detroit", "America/Detroit");
        put("phoenix", "America/Phoenix");
        put("mumbai", "Asia/Kolkata");
        put("delhi", "Asia/Kolkata");
        put("new delhi", "Asia/Kolkata");
        put("bangalore", "Asia/Kolkata");
        put("bengaluru", "Asia/Kolkata");
        put("hyderabad", "Asia/Kolkata");
        put("chennai", "Asia/Kolkata");
        put("lahore", "Asia/Karachi");
        put("beijing", "Asia/Shanghai");
        put("osaka", "Asia/Tokyo");
        put("abu dhabi", "Asia/Dubai");
        put("doha", "Asia/Qatar");
        put("wellington", "Pacific/Auckland");

        // -- Multi-timezone country overrides (Layer C picks arbitrarily) --
        put("australia", "Australia/Sydney");
        put("russia", "Europe/Moscow");
        put("brazil", "America/Sao_Paulo");
        put("canada", "America/Toronto");
        put("mexico", "America/Mexico_City");
        put("indonesia", "Asia/Jakarta");

        // -- Country aliases / informal names --
        put("uk", "Europe/London");
        put("england", "Europe/London");
        put("uae", "Asia/Dubai");
        put("usa", "America/New_York");
        put("us", "America/New_York");
        put("korea", "Asia/Seoul");
        put("south korea", "Asia/Seoul");
    }

    private void put(String name, String zoneId) {
        mLookup.put(name, ZoneId.of(zoneId));
    }
}
