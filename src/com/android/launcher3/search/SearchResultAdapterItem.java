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
import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.search.result.CalculatorResult;
import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.search.result.QuickAction;
import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.search.result.TimezoneResult;
import com.android.launcher3.search.result.UnitConversion;

import java.util.Objects;

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

    /** Snapshot of filter version at creation time, for DiffUtil content comparison. */
    int filterVersion;

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
        item.filterVersion = filters.getVersion();
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
        if (!(other instanceof SearchResultAdapterItem otherItem)) return false;
        if (viewType != other.viewType) return false;

        // Filter bar — only rebind when filter state has actually changed
        if (filters != null) {
            return otherItem.filters != null
                    && filterVersion == otherItem.filterVersion;
        }

        // Section headers — same if title matches
        if (sectionTitle != null) {
            return sectionTitle.equals(otherItem.sectionTitle);
        }

        // Quick actions — same if type and label match
        if (quickAction != null && otherItem.quickAction != null) {
            return quickAction.type == otherItem.quickAction.type
                    && Objects.equals(quickAction.label, otherItem.quickAction.label);
        }

        // Result data comparisons
        if (resultData != null && otherItem.resultData != null) {
            if (resultData instanceof ContactResult a
                    && otherItem.resultData instanceof ContactResult b) {
                return a.contactId == b.contactId;
            }
            if (resultData instanceof CalendarResult a
                    && otherItem.resultData instanceof CalendarResult b) {
                return a.eventId == b.eventId;
            }
            if (resultData instanceof FileResult a
                    && otherItem.resultData instanceof FileResult b) {
                return Objects.equals(a.path, b.path)
                        && a.lastModified == b.lastModified;
            }
            if (resultData instanceof ShortcutResult a
                    && otherItem.resultData instanceof ShortcutResult b) {
                return Objects.equals(a.shortcutInfo.getId(), b.shortcutInfo.getId());
            }
            if (resultData instanceof CalculatorResult a
                    && otherItem.resultData instanceof CalculatorResult b) {
                return Objects.equals(a.expression, b.expression)
                        && Objects.equals(a.formattedResult, b.formattedResult);
            }
            if (resultData instanceof UnitConversion a
                    && otherItem.resultData instanceof UnitConversion b) {
                return a.inputValue == b.inputValue
                        && Objects.equals(a.inputUnit, b.inputUnit);
            }
            if (resultData instanceof TimezoneResult a
                    && otherItem.resultData instanceof TimezoneResult b) {
                return Objects.equals(a.targetTimeFormatted, b.targetTimeFormatted)
                        && Objects.equals(a.targetZoneName, b.targetZoneName);
            }
        }

        return false;
    }
}
