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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * A quick action detected from the search query (call, email, web search, open URL).
 */
public class QuickAction implements Launchable {

    public enum Type {
        CALL, EMAIL, WEB_SEARCH, URL
    }

    public final Type type;
    public final String label;
    public final int iconRes;
    public final Intent intent;

    public QuickAction(Type type, String label, int iconRes, Intent intent) {
        this.type = type;
        this.label = label;
        this.iconRes = iconRes;
        this.intent = intent;
    }

    @Override
    public boolean launch(Context context) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(iconRes);
    }
}
