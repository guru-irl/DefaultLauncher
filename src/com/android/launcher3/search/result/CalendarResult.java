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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract;

/**
 * A search result representing a calendar event.
 */
public class CalendarResult implements Launchable {

    public final long eventId;
    public final String title;
    public final long startTime;
    public final long endTime;
    public final String location;
    public final int color;
    public final boolean allDay;

    public CalendarResult(long eventId, String title, long startTime, long endTime,
            String location, int color, boolean allDay) {
        this.eventId = eventId;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.color = color;
        this.allDay = allDay;
    }

    @Override
    public boolean launch(Context context) {
        try {
            Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            Intent intent = new Intent(Intent.ACTION_VIEW, eventUri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLabel() {
        return title;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null; // Uses color dot instead
    }
}
