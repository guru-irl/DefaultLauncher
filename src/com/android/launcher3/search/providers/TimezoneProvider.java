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

import com.android.launcher3.search.result.TimezoneResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses timezone conversion queries (e.g. "5pm India to Chicago",
 * "time in Tokyo", "3:30pm IST to PST") using java.time APIs.
 */
public class TimezoneProvider implements SearchProvider<TimezoneResult> {

    // "5pm India to Chicago", "3:30pm IST to PST", "17:00 UTC > EST"
    private static final Pattern CONVERT_PATTERN = Pattern.compile(
            "(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\s+(.+?)\\s+(?:to|in|>)\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    // "time in Chicago", "current time in India", "what time is it in Tokyo"
    private static final Pattern CURRENT_TIME_PATTERN = Pattern.compile(
            "(?:time\\s+in|current\\s+time\\s+in|what\\s+time\\s+(?:is\\s+it\\s+)?in)\\s+(.+)",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d");

    private static final Map<String, ZoneId> ZONES = new HashMap<>();

    static {
        // Major cities
        put("new york", "America/New_York");
        put("nyc", "America/New_York");
        put("los angeles", "America/Los_Angeles");
        put("la", "America/Los_Angeles");
        put("chicago", "America/Chicago");
        put("houston", "America/Chicago");
        put("dallas", "America/Chicago");
        put("denver", "America/Denver");
        put("phoenix", "America/Phoenix");
        put("san francisco", "America/Los_Angeles");
        put("seattle", "America/Los_Angeles");
        put("miami", "America/New_York");
        put("boston", "America/New_York");
        put("atlanta", "America/New_York");
        put("detroit", "America/Detroit");
        put("honolulu", "Pacific/Honolulu");
        put("anchorage", "America/Anchorage");
        put("toronto", "America/Toronto");
        put("vancouver", "America/Vancouver");
        put("montreal", "America/Montreal");
        put("mexico city", "America/Mexico_City");
        put("london", "Europe/London");
        put("paris", "Europe/Paris");
        put("berlin", "Europe/Berlin");
        put("rome", "Europe/Rome");
        put("madrid", "Europe/Madrid");
        put("amsterdam", "Europe/Amsterdam");
        put("brussels", "Europe/Brussels");
        put("zurich", "Europe/Zurich");
        put("vienna", "Europe/Vienna");
        put("stockholm", "Europe/Stockholm");
        put("oslo", "Europe/Oslo");
        put("copenhagen", "Europe/Copenhagen");
        put("helsinki", "Europe/Helsinki");
        put("warsaw", "Europe/Warsaw");
        put("prague", "Europe/Prague");
        put("athens", "Europe/Athens");
        put("istanbul", "Europe/Istanbul");
        put("moscow", "Europe/Moscow");
        put("dubai", "Asia/Dubai");
        put("abu dhabi", "Asia/Dubai");
        put("riyadh", "Asia/Riyadh");
        put("doha", "Asia/Qatar");
        put("mumbai", "Asia/Kolkata");
        put("delhi", "Asia/Kolkata");
        put("new delhi", "Asia/Kolkata");
        put("bangalore", "Asia/Kolkata");
        put("bengaluru", "Asia/Kolkata");
        put("hyderabad", "Asia/Kolkata");
        put("chennai", "Asia/Kolkata");
        put("kolkata", "Asia/Kolkata");
        put("karachi", "Asia/Karachi");
        put("lahore", "Asia/Karachi");
        put("dhaka", "Asia/Dhaka");
        put("kathmandu", "Asia/Kathmandu");
        put("colombo", "Asia/Colombo");
        put("bangkok", "Asia/Bangkok");
        put("jakarta", "Asia/Jakarta");
        put("kuala lumpur", "Asia/Kuala_Lumpur");
        put("singapore", "Asia/Singapore");
        put("hong kong", "Asia/Hong_Kong");
        put("taipei", "Asia/Taipei");
        put("shanghai", "Asia/Shanghai");
        put("beijing", "Asia/Shanghai");
        put("seoul", "Asia/Seoul");
        put("tokyo", "Asia/Tokyo");
        put("osaka", "Asia/Tokyo");
        put("manila", "Asia/Manila");
        put("sydney", "Australia/Sydney");
        put("melbourne", "Australia/Melbourne");
        put("brisbane", "Australia/Brisbane");
        put("perth", "Australia/Perth");
        put("auckland", "Pacific/Auckland");
        put("wellington", "Pacific/Auckland");
        put("fiji", "Pacific/Fiji");
        put("sao paulo", "America/Sao_Paulo");
        put("buenos aires", "America/Argentina/Buenos_Aires");
        put("lima", "America/Lima");
        put("bogota", "America/Bogota");
        put("santiago", "America/Santiago");
        put("cairo", "Africa/Cairo");
        put("johannesburg", "Africa/Johannesburg");
        put("lagos", "Africa/Lagos");
        put("nairobi", "Africa/Nairobi");
        put("casablanca", "Africa/Casablanca");

        // Countries
        put("india", "Asia/Kolkata");
        put("japan", "Asia/Tokyo");
        put("china", "Asia/Shanghai");
        put("korea", "Asia/Seoul");
        put("south korea", "Asia/Seoul");
        put("australia", "Australia/Sydney");
        put("new zealand", "Pacific/Auckland");
        put("uk", "Europe/London");
        put("united kingdom", "Europe/London");
        put("england", "Europe/London");
        put("france", "Europe/Paris");
        put("germany", "Europe/Berlin");
        put("italy", "Europe/Rome");
        put("spain", "Europe/Madrid");
        put("russia", "Europe/Moscow");
        put("brazil", "America/Sao_Paulo");
        put("argentina", "America/Argentina/Buenos_Aires");
        put("mexico", "America/Mexico_City");
        put("canada", "America/Toronto");
        put("usa", "America/New_York");
        put("us", "America/New_York");
        put("pakistan", "Asia/Karachi");
        put("bangladesh", "Asia/Dhaka");
        put("nepal", "Asia/Kathmandu");
        put("sri lanka", "Asia/Colombo");
        put("thailand", "Asia/Bangkok");
        put("indonesia", "Asia/Jakarta");
        put("malaysia", "Asia/Kuala_Lumpur");
        put("philippines", "Asia/Manila");
        put("taiwan", "Asia/Taipei");
        put("egypt", "Africa/Cairo");
        put("south africa", "Africa/Johannesburg");
        put("nigeria", "Africa/Lagos");
        put("kenya", "Africa/Nairobi");
        put("uae", "Asia/Dubai");
        put("saudi arabia", "Asia/Riyadh");
        put("qatar", "Asia/Qatar");
        put("turkey", "Europe/Istanbul");
        put("greece", "Europe/Athens");
        put("poland", "Europe/Warsaw");
        put("netherlands", "Europe/Amsterdam");
        put("switzerland", "Europe/Zurich");
        put("sweden", "Europe/Stockholm");
        put("norway", "Europe/Oslo");
        put("denmark", "Europe/Copenhagen");
        put("finland", "Europe/Helsinki");

        // Timezone abbreviations (ambiguous ones default to US)
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
    }

    private static void put(String name, String zoneId) {
        ZONES.put(name, ZoneId.of(zoneId));
    }

    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    public TimezoneProvider() {
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<TimezoneResult>> callback) {
        String trimmed = query.trim();
        mCancelled = false;

        MODEL_EXECUTOR.execute(() -> {
            if (mCancelled) return;

            TimezoneResult result = tryConvert(trimmed);
            if (result == null && !mCancelled) {
                result = tryCurrentTime(trimmed);
            }

            if (mCancelled) return;

            List<TimezoneResult> results = result != null
                    ? List.of(result) : Collections.emptyList();
            mResultHandler.post(() -> callback.accept(results));
        });
    }

    @Override
    public void cancel() {
        mCancelled = true;
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "timezone";
    }

    @Override
    public int minQueryLength() {
        return 6;
    }

    private TimezoneResult tryConvert(String query) {
        Matcher m = CONVERT_PATTERN.matcher(query);
        if (!m.matches()) return null;

        LocalTime time = parseTime(m.group(1).trim());
        if (time == null) return null;

        ZoneId sourceZone = resolveZone(m.group(2).trim());
        ZoneId targetZone = resolveZone(m.group(3).trim());
        if (sourceZone == null || targetZone == null) return null;

        ZonedDateTime sourceDateTime = ZonedDateTime.of(
                LocalDate.now(), time, sourceZone);
        ZonedDateTime targetDateTime = sourceDateTime.withZoneSameInstant(targetZone);

        String targetDate = null;
        if (!sourceDateTime.toLocalDate().equals(targetDateTime.toLocalDate())) {
            targetDate = targetDateTime.format(DATE_FORMAT);
        }

        return new TimezoneResult(
                sourceDateTime.format(TIME_FORMAT),
                formatZoneName(sourceZone),
                targetDateTime.format(TIME_FORMAT),
                formatZoneName(targetZone),
                targetDate,
                false);
    }

    private TimezoneResult tryCurrentTime(String query) {
        Matcher m = CURRENT_TIME_PATTERN.matcher(query);
        if (!m.matches()) return null;

        ZoneId zone = resolveZone(m.group(1).trim());
        if (zone == null) return null;

        ZonedDateTime now = ZonedDateTime.now(zone);

        String dateStr = null;
        if (!now.toLocalDate().equals(LocalDate.now())) {
            dateStr = now.format(DATE_FORMAT);
        }

        return new TimezoneResult(
                null, null,
                now.format(TIME_FORMAT),
                formatZoneName(zone),
                dateStr,
                true);
    }

    private static ZoneId resolveZone(String input) {
        // Strip trailing "time" suffix: "india time" → "india"
        String cleaned = input.toLowerCase().replaceAll("\\s+time$", "").trim();
        return ZONES.get(cleaned);
    }

    private static String formatZoneName(ZoneId zone) {
        String id = zone.getId();
        // Show the city part of "Region/City" or the raw ID
        int slash = id.lastIndexOf('/');
        if (slash >= 0) {
            return id.substring(slash + 1).replace('_', ' ');
        }
        return id;
    }

    /**
     * Parses time strings like "5pm", "5:30pm", "17:00", "5 pm", "5:30 PM".
     */
    static LocalTime parseTime(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase().replaceAll("\\s+", "");

        // Check for am/pm suffix
        boolean hasAmPm = s.endsWith("am") || s.endsWith("pm");
        boolean isPm = s.endsWith("pm");
        if (hasAmPm) {
            s = s.substring(0, s.length() - 2);
        }

        int hour;
        int minute = 0;

        if (s.contains(":")) {
            String[] parts = s.split(":");
            try {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            try {
                hour = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (hasAmPm) {
            if (hour == 12) {
                hour = isPm ? 12 : 0;
            } else if (isPm) {
                hour += 12;
            }
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }
}
