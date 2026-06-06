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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Handler;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.search.providers.AppSearchProvider;
import com.android.launcher3.search.providers.CalendarSearchProvider;
import com.android.launcher3.search.providers.CalculatorProvider;
import com.android.launcher3.search.providers.ContactSearchProvider;
import com.android.launcher3.search.providers.FileSearchProvider;
import com.android.launcher3.search.providers.ProviderCategory;
import com.android.launcher3.search.providers.QuickActionProvider;
import com.android.launcher3.search.providers.SearchProvider;
import com.android.launcher3.search.providers.ShortcutSearchProvider;
import com.android.launcher3.search.providers.TimezoneProvider;
import com.android.launcher3.search.providers.UnitConverterProvider;
import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.search.result.CalculatorResult;
import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.search.result.QuickAction;
import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.search.result.TimezoneResult;
import com.android.launcher3.search.result.UnitConversion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Universal search algorithm that dispatches to multiple providers in parallel
 * and aggregates results into categorized AdapterItems.
 * Inspired by Kvaesitso's multi-source search pattern.
 *
 * <p>Concurrency model: every {@link #doSearch} call mints a {@link SearchSession}
 * that captures the query, callback, enabled-provider set, accumulator, and
 * pending-provider counter. Provider lambdas close over the session by reference,
 * not over {@code this}'s fields. Each callback re-checks
 * {@code s.abandoned || s != mActiveSession} before mutating the accumulator,
 * so late-arriving I/O results from a superseded query cannot corrupt the
 * active state.
 */
public class UniversalSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    private final Context mContext;
    private final Handler mResultHandler;
    private final SearchFilters mFilters;

    // Providers
    private final AppSearchProvider mAppProvider;
    private final List<SearchProvider<?>> mExtraProviders = new ArrayList<>();

    // Active session. Replaced atomically by doSearch; consulted by every
    // provider callback to short-circuit if a newer query has superseded it.
    private volatile SearchSession mActiveSession;
    private final AtomicLong mNextVersion = new AtomicLong(0);

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
        mExtraProviders.add(new TimezoneProvider());
        mExtraProviders.add(new ContactSearchProvider(context));
        mExtraProviders.add(new CalendarSearchProvider(context));
        mExtraProviders.add(new FileSearchProvider());

        mFilters.setOnFilterChangedListener(() -> {
            // Re-render the active session's accumulator with the new filter
            // mask. No new dispatch — just a UI-only re-conversion.
            SearchSession s = mActiveSession;
            if (s == null || s.abandoned) return;
            deliverResults(s, SearchCallback.FINAL);
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

    /**
     * Snapshots enabled-provider categories from prefs. Called once at the top
     * of {@link #doSearch} so a pref toggle landing mid-flight doesn't reshape
     * the active session's provider set.
     */
    private EnumSet<ProviderCategory> snapshotEnabledProviders() {
        EnumSet<ProviderCategory> enabled = EnumSet.noneOf(ProviderCategory.class);
        LauncherPrefs prefs = LauncherPrefs.get(mContext);
        if (prefs.get(LauncherPrefs.SEARCH_APPS)) enabled.add(ProviderCategory.APPS);
        if (prefs.get(LauncherPrefs.SEARCH_SHORTCUTS)) enabled.add(ProviderCategory.SHORTCUTS);
        if (prefs.get(LauncherPrefs.SEARCH_CONTACTS)) enabled.add(ProviderCategory.CONTACTS);
        if (prefs.get(LauncherPrefs.SEARCH_CALENDAR)) enabled.add(ProviderCategory.CALENDAR);
        if (prefs.get(LauncherPrefs.SEARCH_FILES)) enabled.add(ProviderCategory.FILES);
        if (prefs.get(LauncherPrefs.SEARCH_CALCULATOR)) enabled.add(ProviderCategory.CALCULATOR);
        if (prefs.get(LauncherPrefs.SEARCH_UNIT_CONVERTER)) {
            enabled.add(ProviderCategory.UNIT_CONVERTER);
        }
        if (prefs.get(LauncherPrefs.SEARCH_TIMEZONE)) enabled.add(ProviderCategory.TIMEZONE);
        // Quick actions are always enabled.
        enabled.add(ProviderCategory.QUICK_ACTIONS);
        return enabled;
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        // Abandon the previous session (if any) so its in-flight provider
        // callbacks short-circuit before mutating any shared state.
        SearchSession previous = mActiveSession;
        if (previous != null) {
            previous.abandoned = true;
        }
        cancel(true);

        EnumSet<ProviderCategory> enabledProviders = snapshotEnabledProviders();
        SearchSession s = new SearchSession(
                mNextVersion.incrementAndGet(), query, enabledProviders, callback);
        mActiveSession = s;

        if (query == null || query.isEmpty()) {
            callback.onSearchResult(query, new ArrayList<>());
            return;
        }

        // Count how many providers will run under this session's snapshot.
        int providerCount = enabledProviders.contains(ProviderCategory.APPS) ? 1 : 0;
        for (SearchProvider<?> provider : mExtraProviders) {
            if (query.length() >= provider.minQueryLength()
                    && enabledProviders.contains(provider.category())) {
                providerCount++;
            }
        }
        if (providerCount == 0) {
            // No providers enabled — emit an empty FINAL.
            deliverResults(s, SearchCallback.FINAL);
            return;
        }
        s.pendingProviders.set(providerCount);

        // Dispatch app search — INTERMEDIATE delivery so app results paint
        // before slower I/O providers complete.
        if (enabledProviders.contains(ProviderCategory.APPS)) {
            mAppProvider.search(query, apps -> {
                if (s.abandoned || s != mActiveSession) return;
                synchronized (s.accumulator) {
                    s.accumulator.apps.clear();
                    s.accumulator.apps.addAll(apps);
                }
                int remaining = s.pendingProviders.decrementAndGet();
                deliverResults(s, remaining <= 0 ? SearchCallback.FINAL : SearchCallback.INTERMEDIATE);
            });
        }

        // Dispatch extra providers under the session's snapshotted enabled-set.
        for (SearchProvider<?> provider : mExtraProviders) {
            if (query.length() >= provider.minQueryLength()
                    && enabledProviders.contains(provider.category())) {
                dispatchProvider(s, provider);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchProvider(SearchSession s, SearchProvider<?> provider) {
        ProviderCategory cat = provider.category();
        provider.search(s.query, results -> {
            if (s.abandoned || s != mActiveSession) return;
            synchronized (s.accumulator) {
                switch (cat) {
                    case SHORTCUTS:
                        s.accumulator.shortcuts.clear();
                        s.accumulator.shortcuts.addAll((List<ShortcutResult>) (List<?>) results);
                        break;
                    case CONTACTS:
                        s.accumulator.contacts.clear();
                        s.accumulator.contacts.addAll((List<ContactResult>) (List<?>) results);
                        break;
                    case CALENDAR:
                        s.accumulator.calendarEvents.clear();
                        s.accumulator.calendarEvents.addAll(
                                (List<CalendarResult>) (List<?>) results);
                        break;
                    case FILES:
                        s.accumulator.files.clear();
                        s.accumulator.files.addAll((List<FileResult>) (List<?>) results);
                        break;
                    case QUICK_ACTIONS:
                        s.accumulator.quickActions.clear();
                        s.accumulator.quickActions.addAll((List<QuickAction>) (List<?>) results);
                        break;
                    case CALCULATOR:
                        if (!results.isEmpty()) {
                            s.accumulator.calculator = (CalculatorResult) results.get(0);
                        }
                        break;
                    case UNIT_CONVERTER:
                        if (!results.isEmpty()) {
                            s.accumulator.unitConversion = (UnitConversion) results.get(0);
                        }
                        break;
                    case TIMEZONE:
                        if (!results.isEmpty()) {
                            s.accumulator.timezone = (TimezoneResult) results.get(0);
                        }
                        break;
                    case APPS:
                        // AppSearchProvider has its own dedicated dispatch path
                        // in doSearch() with progressive INTERMEDIATE delivery —
                        // never routed here.
                        break;
                }
            }
            int remaining = s.pendingProviders.decrementAndGet();
            if (remaining <= 0) {
                deliverResults(s, SearchCallback.FINAL);
            }
        });
    }

    /** Converts the session's SearchResult into adapter items and dispatches via the callback. */
    private void deliverResults(SearchSession s, int resultCode) {
        if (s.abandoned || s != mActiveSession) return;
        ArrayList<AdapterItem> items = UniversalSearchAdapterProvider.convertResults(
                mContext, s.accumulator, mFilters, s.query, resultCode);
        s.callback.onSearchResult(s.query, items, resultCode);
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
        SearchSession s = mActiveSession;
        if (s != null) {
            s.abandoned = true;
        }
        mActiveSession = null;
    }
}
