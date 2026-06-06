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
package com.android.launcher3

/**
 * Receiver for batched preference-change notifications dispatched by
 * [PrefChangeDispatcher].
 *
 * Implementations are guaranteed to be called on the main thread regardless of
 * where the underlying `SharedPreferences` write originated (model loader,
 * RestoreDbTask background writer, settings fragment on the main thread, etc.).
 *
 * The dispatcher coalesces multiple writes within one main-thread tick into a
 * single delivery; the [changes] set may contain more than one [Item].
 */
fun interface PrefSubscriber {
    /**
     * Called on the main thread with the set of items whose preference values
     * changed since the last delivery. The set is non-empty.
     */
    fun onPrefsChanged(changes: Set<Item>)
}
