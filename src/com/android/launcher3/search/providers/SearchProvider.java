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
package com.android.launcher3.search.providers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for a search provider that searches a specific category.
 * Inspired by Kvaesitso's SearchableRepository pattern.
 *
 * @param <T> The type of search result produced by this provider.
 */
public interface SearchProvider<T> {

    /**
     * Performs a search on a background thread and delivers results via callback on main thread.
     *
     * @param query    The search query string.
     * @param callback Callback to receive results; called on the main thread.
     */
    void search(String query, Consumer<List<T>> callback);

    /** Cancels any in-progress search. */
    void cancel();

    /** Returns the category name for this provider (e.g. "apps", "contacts"). */
    String category();

    /** Returns the minimum query length required for this provider to search. */
    default int minQueryLength() {
        return 1;
    }
}
