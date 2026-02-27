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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses timezone conversion queries (e.g. "5pm India to Chicago",
 * "time in Tokyo", "chicago time", "3:30pm IST to PST") using java.time APIs.
 *
 * <p>Zone resolution is delegated to {@link TimezoneResolver}, which auto-generates
 * ~650+ location lookups from IANA, ICU, and Locale platform APIs.
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

    // "chicago time", "india time", "tokyo time"
    private static final Pattern PLACE_TIME_PATTERN = Pattern.compile(
            "(.+?)\\s+time", Pattern.CASE_INSENSITIVE);

    // "4pm chicago time", "4pm chicago time tuesday", "4pm chicago time tue to tokyo"
    private static final Pattern TIMED_PLACE_PATTERN = Pattern.compile(
            "(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\s+(.+?)\\s+time"
            + "(?:\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday"
            + "|mon|tue|wed|thu|fri|sat|sun))?"
            + "(?:\\s+(?:to|in|>)\\s+(.+))?$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, DayOfWeek> DAY_OF_WEEK_MAP = Map.ofEntries(
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("mon", DayOfWeek.MONDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("tue", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("fri", DayOfWeek.FRIDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sat", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("sun", DayOfWeek.SUNDAY));

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d");

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
                result = tryTimedPlace(trimmed);
            }
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

        ZoneId sourceZone = TimezoneResolver.getInstance().resolve(m.group(2).trim());
        ZoneId targetZone = TimezoneResolver.getInstance().resolve(m.group(3).trim());
        if (sourceZone == null || targetZone == null) return null;

        ZonedDateTime sourceDateTime = ZonedDateTime.of(
                LocalDate.now(), time, sourceZone);
        ZonedDateTime targetDateTime = sourceDateTime.withZoneSameInstant(targetZone);

        return new TimezoneResult(
                sourceDateTime.format(TIME_FORMAT),
                formatZoneName(sourceZone),
                sourceDateTime.format(DATE_FORMAT),
                targetDateTime.format(TIME_FORMAT),
                formatZoneName(targetZone),
                computeRelativeDay(sourceDateTime.toLocalDate(),
                        targetDateTime.toLocalDate()),
                targetDateTime.format(DATE_FORMAT),
                false);
    }

    private TimezoneResult tryCurrentTime(String query) {
        // Try "time in X" patterns first, then "X time"
        Matcher m = CURRENT_TIME_PATTERN.matcher(query);
        if (!m.matches()) {
            m = PLACE_TIME_PATTERN.matcher(query);
            if (!m.matches()) return null;
        }

        ZoneId zone = TimezoneResolver.getInstance().resolve(m.group(1).trim());
        if (zone == null) return null;

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime localNow = ZonedDateTime.now();

        return new TimezoneResult(
                localNow.format(TIME_FORMAT),
                formatZoneName(localNow.getZone()),
                localNow.format(DATE_FORMAT),
                now.format(TIME_FORMAT),
                formatZoneName(zone),
                computeRelativeDay(localNow.toLocalDate(), now.toLocalDate()),
                now.format(DATE_FORMAT),
                true);
    }

    private TimezoneResult tryTimedPlace(String query) {
        Matcher m = TIMED_PLACE_PATTERN.matcher(query);
        if (!m.matches()) return null;

        LocalTime time = parseTime(m.group(1).trim());
        if (time == null) return null;

        ZoneId sourceZone = TimezoneResolver.getInstance().resolve(m.group(2).trim());
        if (sourceZone == null) return null;

        // Optional day-of-week
        LocalDate sourceDate = LocalDate.now(sourceZone);
        if (m.group(3) != null) {
            DayOfWeek dow = DAY_OF_WEEK_MAP.get(m.group(3).trim().toLowerCase());
            if (dow != null) {
                sourceDate = sourceDate.with(TemporalAdjusters.nextOrSame(dow));
            }
        }

        // Optional target zone (defaults to device local)
        ZoneId targetZone;
        if (m.group(4) != null) {
            targetZone = TimezoneResolver.getInstance().resolve(m.group(4).trim());
            if (targetZone == null) return null;
        } else {
            targetZone = ZoneId.systemDefault();
        }

        ZonedDateTime sourceDateTime = ZonedDateTime.of(sourceDate, time, sourceZone);
        ZonedDateTime targetDateTime = sourceDateTime.withZoneSameInstant(targetZone);

        return new TimezoneResult(
                sourceDateTime.format(TIME_FORMAT),
                formatZoneName(sourceZone),
                sourceDateTime.format(DATE_FORMAT),
                targetDateTime.format(TIME_FORMAT),
                formatZoneName(targetZone),
                computeRelativeDay(sourceDateTime.toLocalDate(),
                        targetDateTime.toLocalDate()),
                targetDateTime.format(DATE_FORMAT),
                false);
    }

    private static String computeRelativeDay(LocalDate source, LocalDate target) {
        long dayDiff = target.toEpochDay() - source.toEpochDay();
        if (dayDiff == 0) return "Same day";
        if (dayDiff == 1) return "Next day";
        if (dayDiff == -1) return "Previous day";
        if (dayDiff > 0) return "+" + dayDiff + " days";
        return dayDiff + " days";
    }

    static String formatZoneName(ZoneId zone) {
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
