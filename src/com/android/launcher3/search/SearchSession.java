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
import com.android.launcher3.search.providers.ProviderCategory;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Immutable per-query state for one universal-search dispatch.
 *
 * <p>Every {@link UniversalSearchAlgorithm#doSearch} call creates a fresh
 * SearchSession. Provider callbacks capture the session by reference and use
 * its version + abandoned flag to decide whether their result is still
 * relevant. The previous-generation problem this avoids is the late-arriving
 * I/O provider (e.g. ContentResolver on Contacts) racing the next keystroke:
 * the late callback would mutate the active query's SearchResult and corrupt
 * the rendered output. With sessions, every late callback short-circuits
 * before touching the accumulator.
 *
 * <p>Provider-enablement is snapshotted into {@link #enabledProviders} at
 * session creation so a pref toggle landing mid-flight (e.g. from a Settings
 * panel open in the background) does not change the set of active providers
 * for a session already in motion.
 */
final class SearchSession {

    /** Monotonic version. Used by callbacks to identity-check the session. */
    final long version;

    /** Original query string that opened this session. */
    final String query;

    /** Provider categories enabled at session creation; immutable. */
    final EnumSet<ProviderCategory> enabledProviders;

    /** Output sink for FINAL/INTERMEDIATE result deliveries. */
    final SearchCallback<AdapterItem> callback;

    /** Mutable accumulator written by provider callbacks; guarded by {@code synchronized(accumulator)}. */
    final SearchResult accumulator;

    /** Remaining providers expected to complete. Reaches 0 → FINAL delivery. */
    final AtomicInteger pendingProviders;

    /**
     * Set true when a newer session opens or {@code cancel(true)} fires. Late
     * callbacks must re-read this and bail before mutating {@link #accumulator}.
     */
    volatile boolean abandoned;

    SearchSession(long version, String query, EnumSet<ProviderCategory> enabledProviders,
            SearchCallback<AdapterItem> callback) {
        this.version = version;
        this.query = query;
        this.enabledProviders = enabledProviders;
        this.callback = callback;
        this.accumulator = new SearchResult();
        this.pendingProviders = new AtomicInteger(0);
        this.abandoned = false;
    }
}
