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

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.search.result.CalculatorResult;
import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.search.result.QuickAction;
import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.search.result.TimezoneResult;
import com.android.launcher3.search.result.UnitConversion;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated container for all search result categories.
 * Inspired by Kvaesitso's SearchResults pattern.
 */
public class SearchResult {

    public final List<AppInfo> apps = new ArrayList<>();
    public final List<ShortcutResult> shortcuts = new ArrayList<>();
    public final List<ContactResult> contacts = new ArrayList<>();
    public final List<CalendarResult> calendarEvents = new ArrayList<>();
    public final List<FileResult> files = new ArrayList<>();
    public final List<QuickAction> quickActions = new ArrayList<>();
    public CalculatorResult calculator;
    public UnitConversion unitConversion;
    public TimezoneResult timezone;

    /** Returns true if all result categories are empty. */
    public boolean isEmpty() {
        return apps.isEmpty()
                && shortcuts.isEmpty()
                && contacts.isEmpty()
                && calendarEvents.isEmpty()
                && files.isEmpty()
                && quickActions.isEmpty()
                && calculator == null
                && unitConversion == null
                && timezone == null;
    }
}
