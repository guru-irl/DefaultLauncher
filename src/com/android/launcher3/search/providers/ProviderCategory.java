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

/**
 * Identity of a {@link SearchProvider}. Used by the universal-search dispatcher
 * to look up the user preference that gates the provider and to route incoming
 * results into the matching {@code SearchResult} bucket.
 *
 * <p>Closed enumeration — every new provider must add its identifier here and
 * every dispatch switch must handle it. Compiler exhaustiveness catches
 * missing additions.
 */
public enum ProviderCategory {
    APPS,
    SHORTCUTS,
    CONTACTS,
    CALENDAR,
    FILES,
    CALCULATOR,
    UNIT_CONVERTER,
    TIMEZONE,
    QUICK_ACTIONS,
}
