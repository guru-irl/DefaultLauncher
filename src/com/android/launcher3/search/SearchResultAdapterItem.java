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

import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.search.result.QuickAction;

/**
 * Extended AdapterItem for non-app search results (contacts, calendar, files, etc.).
 */
public class SearchResultAdapterItem extends AdapterItem {

    /** The actual result data (ContactResult, CalendarResult, ShortcutResult, etc.). */
    public Object resultData;

    /** Section title (for section header items). */
    public String sectionTitle;

    /** Filter state (for filter bar item). */
    public SearchFilters filters;

    /** Quick action (for quick action items). */
    public QuickAction quickAction;

    public SearchResultAdapterItem(int viewType) {
        super(viewType);
    }

    /** Factory for section header items. */
    public static SearchResultAdapterItem asSectionHeader(int viewType, String title) {
        SearchResultAdapterItem item = new SearchResultAdapterItem(viewType);
        item.sectionTitle = title;
        return item;
    }

    /** Factory for filter bar items. */
    public static SearchResultAdapterItem asFilterBar(int viewType, SearchFilters filters) {
        SearchResultAdapterItem item = new SearchResultAdapterItem(viewType);
        item.filters = filters;
        return item;
    }

    /** Factory for result items (shortcuts, contacts, calendar, files, calculator, unit). */
    public static SearchResultAdapterItem asResult(int viewType, Object data) {
        SearchResultAdapterItem item = new SearchResultAdapterItem(viewType);
        item.resultData = data;
        return item;
    }

    /** Factory for quick action items. */
    public static SearchResultAdapterItem asQuickAction(int viewType, QuickAction action) {
        SearchResultAdapterItem item = new SearchResultAdapterItem(viewType);
        item.quickAction = action;
        return item;
    }

    @Override
    public boolean isSameAs(AdapterItem other) {
        if (!(other instanceof SearchResultAdapterItem otherItem)) return false;
        if (viewType != other.viewType) return false;
        // Section headers match by title
        if (sectionTitle != null) return sectionTitle.equals(otherItem.sectionTitle);
        // Filter bars are always the same
        if (filters != null) return otherItem.filters != null;
        // Quick actions match by type
        if (quickAction != null && otherItem.quickAction != null) {
            return quickAction.type == otherItem.quickAction.type;
        }
        return resultData == otherItem.resultData;
    }

    @Override
    public boolean isContentSame(AdapterItem other) {
        return false; // Always rebind for simplicity
    }
}
