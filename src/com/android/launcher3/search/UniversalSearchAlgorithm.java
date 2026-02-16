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

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CALCULATOR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CALENDAR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CONTACT;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILE;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILTER_BAR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_QUICK_ACTION;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SECTION_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SHORTCUT;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_UNIT_CONVERTER;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Handler;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.providers.AppSearchProvider;
import com.android.launcher3.search.providers.CalendarSearchProvider;
import com.android.launcher3.search.providers.CalculatorProvider;
import com.android.launcher3.search.providers.ContactSearchProvider;
import com.android.launcher3.search.providers.FileSearchProvider;
import com.android.launcher3.search.providers.QuickActionProvider;
import com.android.launcher3.search.providers.SearchProvider;
import com.android.launcher3.search.providers.ShortcutSearchProvider;
import com.android.launcher3.search.providers.UnitConverterProvider;
import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.search.result.CalculatorResult;
import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.search.result.QuickAction;
import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.search.result.UnitConversion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Universal search algorithm that dispatches to multiple providers in parallel
 * and aggregates results into categorized AdapterItems.
 * Inspired by Kvaesitso's multi-source search pattern.
 */
public class UniversalSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    private final Context mContext;
    private final Handler mResultHandler;
    private final SearchFilters mFilters;

    // Providers
    private final AppSearchProvider mAppProvider;
    private final List<SearchProvider<?>> mExtraProviders = new ArrayList<>();

    // Current aggregated result
    private SearchResult mCurrentResult;
    private String mCurrentQuery;
    private SearchCallback<AdapterItem> mCurrentCallback;
    private final AtomicInteger mPendingProviders = new AtomicInteger(0);

    public UniversalSearchAlgorithm(Context context) {
        mContext = context;
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
        mFilters = new SearchFilters();
        mAppProvider = new AppSearchProvider(context);

        // Register built-in providers
        mExtraProviders.add(new ShortcutSearchProvider(context));
        mExtraProviders.add(new QuickActionProvider(context));
        mExtraProviders.add(new CalculatorProvider());
        mExtraProviders.add(new UnitConverterProvider());
        mExtraProviders.add(new ContactSearchProvider(context));
        mExtraProviders.add(new CalendarSearchProvider(context));
        mExtraProviders.add(new FileSearchProvider());

        mFilters.setOnFilterChangedListener(() -> {
            // Re-run current query with new filters if we have one
            if (mCurrentQuery != null && mCurrentCallback != null) {
                deliverResults();
            }
        });
    }

    /** Returns the search filters state for use by the filter bar UI. */
    public SearchFilters getFilters() {
        return mFilters;
    }

    /** Register an additional search provider. */
    public void addProvider(SearchProvider<?> provider) {
        mExtraProviders.add(provider);
    }

    /** Returns true if the provider's category is enabled in user prefs. */
    private boolean isProviderEnabled(SearchProvider<?> provider) {
        LauncherPrefs prefs = LauncherPrefs.get(mContext);
        switch (provider.category()) {
            case "shortcuts": return prefs.get(LauncherPrefs.SEARCH_SHORTCUTS);
            case "contacts": return prefs.get(LauncherPrefs.SEARCH_CONTACTS);
            case "calendar": return prefs.get(LauncherPrefs.SEARCH_CALENDAR);
            case "files": return prefs.get(LauncherPrefs.SEARCH_FILES);
            case "calculator": return prefs.get(LauncherPrefs.SEARCH_CALCULATOR);
            case "unit_converter": return prefs.get(LauncherPrefs.SEARCH_UNIT_CONVERTER);
            case "quick_actions": return true; // Always enabled
            default: return true;
        }
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        cancel(true);
        mCurrentQuery = query;
        mCurrentCallback = callback;
        mCurrentResult = new SearchResult();

        if (query == null || query.isEmpty()) {
            callback.onSearchResult(query, new ArrayList<>());
            return;
        }

        boolean appsEnabled = LauncherPrefs.get(mContext).get(LauncherPrefs.SEARCH_APPS);

        // Count how many providers will run
        int providerCount = appsEnabled ? 1 : 0;
        for (SearchProvider<?> provider : mExtraProviders) {
            if (query.length() >= provider.minQueryLength() && isProviderEnabled(provider)) {
                providerCount++;
            }
        }
        if (providerCount == 0) {
            // No providers enabled, show empty
            deliverResults();
            return;
        }
        mPendingProviders.set(providerCount);

        // Dispatch app search
        if (appsEnabled) {
            mAppProvider.search(query, apps -> {
                synchronized (mCurrentResult) {
                    mCurrentResult.apps.clear();
                    mCurrentResult.apps.addAll(apps);
                }
                onProviderComplete();
            });
        }

        // Dispatch extra providers
        for (SearchProvider<?> provider : mExtraProviders) {
            if (query.length() >= provider.minQueryLength() && isProviderEnabled(provider)) {
                dispatchProvider(provider, query);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchProvider(SearchProvider<?> provider, String query) {
        String cat = provider.category();
        provider.search(query, results -> {
            synchronized (mCurrentResult) {
                switch (cat) {
                    case "shortcuts":
                        mCurrentResult.shortcuts.clear();
                        mCurrentResult.shortcuts.addAll((List<ShortcutResult>) (List<?>) results);
                        break;
                    case "contacts":
                        mCurrentResult.contacts.clear();
                        mCurrentResult.contacts.addAll((List<ContactResult>) (List<?>) results);
                        break;
                    case "calendar":
                        mCurrentResult.calendarEvents.clear();
                        mCurrentResult.calendarEvents.addAll(
                                (List<CalendarResult>) (List<?>) results);
                        break;
                    case "files":
                        mCurrentResult.files.clear();
                        mCurrentResult.files.addAll((List<FileResult>) (List<?>) results);
                        break;
                    case "quick_actions":
                        mCurrentResult.quickActions.clear();
                        mCurrentResult.quickActions.addAll((List<QuickAction>) (List<?>) results);
                        break;
                    case "calculator":
                        if (!results.isEmpty()) {
                            mCurrentResult.calculator = (CalculatorResult) results.get(0);
                        }
                        break;
                    case "unit_converter":
                        if (!results.isEmpty()) {
                            mCurrentResult.unitConversion = (UnitConversion) results.get(0);
                        }
                        break;
                }
            }
            onProviderComplete();
        });
    }

    private void onProviderComplete() {
        int remaining = mPendingProviders.decrementAndGet();
        if (remaining <= 0) {
            // Deliver results once all providers have completed to avoid layout jumps
            deliverResults();
        }
    }

    /** Converts the current SearchResult into adapter items respecting filters. */
    private void deliverResults() {
        if (mCurrentCallback == null || mCurrentResult == null) return;

        ArrayList<AdapterItem> items = new ArrayList<>();

        // Filter bar is always first
        items.add(SearchResultAdapterItem.asFilterBar(VIEW_TYPE_SEARCH_FILTER_BAR, mFilters));

        synchronized (mCurrentResult) {
            // Quick actions (always shown regardless of filter).
            // Skip WEB_SEARCH type — replaced by the floating "Search online" FAB.
            for (QuickAction action : mCurrentResult.quickActions) {
                if (action.type == QuickAction.Type.WEB_SEARCH) continue;
                items.add(SearchResultAdapterItem.asQuickAction(
                        VIEW_TYPE_SEARCH_QUICK_ACTION, action));
            }

            // Calculator
            if (mCurrentResult.calculator != null
                    && mFilters.isCategorySelected(SearchFilters.Category.TOOLS)) {
                items.add(SearchResultAdapterItem.asResult(
                        VIEW_TYPE_SEARCH_CALCULATOR, mCurrentResult.calculator));
            }

            // Unit converter
            if (mCurrentResult.unitConversion != null
                    && mFilters.isCategorySelected(SearchFilters.Category.TOOLS)) {
                items.add(SearchResultAdapterItem.asResult(
                        VIEW_TYPE_SEARCH_UNIT_CONVERTER, mCurrentResult.unitConversion));
            }

            // Apps
            if (!mCurrentResult.apps.isEmpty()
                    && mFilters.isCategorySelected(SearchFilters.Category.APPS)) {
                items.add(SearchResultAdapterItem.asSectionHeader(
                        VIEW_TYPE_SEARCH_SECTION_HEADER, "Apps"));
                for (AppInfo app : mCurrentResult.apps) {
                    items.add(AdapterItem.asApp(app));
                }
            }

            // Shortcuts
            if (!mCurrentResult.shortcuts.isEmpty()
                    && mFilters.isCategorySelected(SearchFilters.Category.SHORTCUTS)) {
                items.add(SearchResultAdapterItem.asSectionHeader(
                        VIEW_TYPE_SEARCH_SECTION_HEADER, "Shortcuts"));
                for (ShortcutResult shortcut : mCurrentResult.shortcuts) {
                    items.add(SearchResultAdapterItem.asResult(
                            VIEW_TYPE_SEARCH_SHORTCUT, shortcut));
                }
            }

            // Contacts
            if (!mCurrentResult.contacts.isEmpty()
                    && mFilters.isCategorySelected(SearchFilters.Category.CONTACTS)) {
                items.add(SearchResultAdapterItem.asSectionHeader(
                        VIEW_TYPE_SEARCH_SECTION_HEADER, "Contacts"));
                for (ContactResult contact : mCurrentResult.contacts) {
                    items.add(SearchResultAdapterItem.asResult(
                            VIEW_TYPE_SEARCH_CONTACT, contact));
                }
            }

            // Calendar
            if (!mCurrentResult.calendarEvents.isEmpty()
                    && mFilters.isCategorySelected(SearchFilters.Category.CALENDAR)) {
                items.add(SearchResultAdapterItem.asSectionHeader(
                        VIEW_TYPE_SEARCH_SECTION_HEADER, "Calendar"));
                for (CalendarResult event : mCurrentResult.calendarEvents) {
                    items.add(SearchResultAdapterItem.asResult(
                            VIEW_TYPE_SEARCH_CALENDAR, event));
                }
            }

            // Files
            if (!mCurrentResult.files.isEmpty()
                    && mFilters.isCategorySelected(SearchFilters.Category.FILES)) {
                items.add(SearchResultAdapterItem.asSectionHeader(
                        VIEW_TYPE_SEARCH_SECTION_HEADER, "Files"));
                for (FileResult file : mCurrentResult.files) {
                    items.add(SearchResultAdapterItem.asResult(VIEW_TYPE_SEARCH_FILE, file));
                }
            }
        }

        // If only filter bar and no results, show empty message
        if (items.size() <= 1) {
            AdapterItem emptyItem = new AdapterItem(VIEW_TYPE_EMPTY_SEARCH);
            AppInfo placeholder = new AppInfo();
            placeholder.title = mCurrentQuery;
            emptyItem.itemInfo = placeholder;
            items.add(emptyItem);
        }

        mCurrentCallback.onSearchResult(mCurrentQuery, items);
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
            mAppProvider.cancel();
            for (SearchProvider<?> provider : mExtraProviders) {
                provider.cancel();
            }
        }
    }

    @Override
    public void destroy() {
        cancel(true);
    }
}
