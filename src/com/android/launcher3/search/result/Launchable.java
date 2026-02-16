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
import android.graphics.drawable.Drawable;

/**
 * Common interface for launchable search results.
 */
public interface Launchable {
    /** Launch this result (e.g., open app, dial number, open file). */
    boolean launch(Context context);

    /** Display label for this result. */
    String getLabel();

    /** Icon drawable (may be null if not yet loaded). */
    Drawable getIcon(Context context);
}
