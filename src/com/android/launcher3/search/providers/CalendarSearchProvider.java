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

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Handler;
import android.provider.CalendarContract;
import android.util.Log;

import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searches calendar events via ContentResolver and CalendarContract.
 * Searches the next 365 days. Requires READ_CALENDAR permission.
 */
public class CalendarSearchProvider implements SearchProvider<CalendarResult> {

    private static final String TAG = "CalendarSearchProvider";
    private static final int MAX_RESULTS = 3;
    private static final long DAYS_365_MS = 365L * 24 * 60 * 60 * 1000;

    private final Context mContext;
    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    public CalendarSearchProvider(Context context) {
        mContext = context.getApplicationContext();
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<CalendarResult>> callback) {
        if (mContext.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            mResultHandler.post(() -> callback.accept(Collections.emptyList()));
            return;
        }

        mCancelled = false;
        Executors.MODEL_EXECUTOR.execute(() -> {
            List<CalendarResult> results = queryCalendar(query);
            if (!mCancelled) {
                mResultHandler.post(() -> callback.accept(results));
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
        return "calendar";
    }

    @Override
    public int minQueryLength() {
        return 2;
    }

    private List<CalendarResult> queryCalendar(String query) {
        List<CalendarResult> results = new ArrayList<>();
        ContentResolver resolver = mContext.getContentResolver();

        long now = System.currentTimeMillis();
        long end = now + DAYS_365_MS;

        try (Cursor cursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{
                        CalendarContract.Events._ID,
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.DTEND,
                        CalendarContract.Events.EVENT_LOCATION,
                        CalendarContract.Events.CALENDAR_COLOR,
                        CalendarContract.Events.ALL_DAY
                },
                CalendarContract.Events.TITLE + " LIKE ? AND " +
                        CalendarContract.Events.DTSTART + " >= ? AND " +
                        CalendarContract.Events.DTSTART + " <= ?",
                new String[]{"%" + query + "%", String.valueOf(now), String.valueOf(end)},
                CalendarContract.Events.DTSTART + " ASC")) {

            if (cursor == null) return results;

            while (cursor.moveToNext() && results.size() < MAX_RESULTS) {
                if (mCancelled) break;

                long eventId = cursor.getLong(0);
                String title = cursor.getString(1);
                long startTime = cursor.getLong(2);
                long endTime = cursor.isNull(3) ? startTime : cursor.getLong(3);
                String location = cursor.getString(4);
                int color = cursor.isNull(5) ? 0 : cursor.getInt(5);
                boolean allDay = cursor.getInt(6) != 0;

                results.add(new CalendarResult(
                        eventId, title, startTime, endTime, location, color, allDay));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error searching calendar", e);
        }

        return results;
    }
}
