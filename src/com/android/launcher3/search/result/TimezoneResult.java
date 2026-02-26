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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.android.launcher3.R;

/**
 * A timezone conversion or current time result.
 */
public class TimezoneResult implements Launchable {

    public final String sourceTimeFormatted;
    public final String sourceZoneName;
    public final String targetTimeFormatted;
    public final String targetZoneName;
    /** Non-null if the target date differs from the source date (e.g. crosses midnight). */
    public final String targetDate;
    public final boolean isCurrentTimeQuery;

    public TimezoneResult(String sourceTimeFormatted, String sourceZoneName,
            String targetTimeFormatted, String targetZoneName,
            String targetDate, boolean isCurrentTimeQuery) {
        this.sourceTimeFormatted = sourceTimeFormatted;
        this.sourceZoneName = sourceZoneName;
        this.targetTimeFormatted = targetTimeFormatted;
        this.targetZoneName = targetZoneName;
        this.targetDate = targetDate;
        this.isCurrentTimeQuery = isCurrentTimeQuery;
    }

    @Override
    public boolean launch(Context context) {
        StringBuilder sb = new StringBuilder();
        if (isCurrentTimeQuery) {
            sb.append(targetTimeFormatted).append(" ").append(targetZoneName);
        } else {
            sb.append(sourceTimeFormatted).append(" ").append(sourceZoneName)
                    .append(" = ")
                    .append(targetTimeFormatted).append(" ").append(targetZoneName);
        }
        if (targetDate != null) {
            sb.append(" (").append(targetDate).append(")");
        }
        ClipboardManager cm = context.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("Timezone", sb.toString()));
        Toast.makeText(context, R.string.search_result_copied, Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public String getLabel() {
        return targetTimeFormatted + " " + targetZoneName;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null;
    }
}
