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

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide directory of every [Item] ever constructed. Populated by
 * `Item`'s `init` block; consulted by [PrefChangeDispatcher] to resolve a
 * SharedPreferences key back to its typed item(s) and to enumerate items
 * matching a given [SettingImpact].
 *
 * Multiple [Item] instances may share a `sharedPrefKey` (e.g., parallel
 * declarations in different companion objects); [byKey] returns all of them.
 */
object ItemRegistry {

    private val items = ConcurrentHashMap.newKeySet<Item>()
    private val byKey = ConcurrentHashMap<String, MutableSet<Item>>()

    internal fun register(item: Item) {
        if (items.add(item)) {
            byKey.computeIfAbsent(item.sharedPrefKey) { ConcurrentHashMap.newKeySet() }
                .add(item)
        }
    }

    /** Returns every Item sharing [key]. Empty if no Item is registered for that key. */
    fun byKey(key: String): Set<Item> = byKey[key] ?: emptySet()

    /** Returns a snapshot of every registered Item. Safe to iterate. */
    fun all(): Set<Item> = HashSet(items)
}
