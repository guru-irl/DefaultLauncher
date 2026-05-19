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

import android.content.SharedPreferences
import android.util.Log
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Routes [SharedPreferences] change events to typed [PrefSubscriber]s with
 * impact-level batching and main-thread delivery.
 *
 * Design points:
 *
 * - **Dedup**: subscribers are keyed by `IdentityHashMap<PrefSubscriber, _>`.
 *   A double-`subscribe` for the same subscriber instance overwrites the
 *   previous registration (item set + impact set). Dispatch iterates
 *   subscribers, so each subscriber receives at most one callback per tick
 *   even when it would have matched multiple changed items by multiple
 *   subscription paths.
 * - **Threading**: every dispatch posts to [MAIN_EXECUTOR], including writes
 *   coming from `RestoreDbTask` on a background thread.
 * - **Coalescing**: writes inside one main-thread tick are batched into one
 *   delivery. The pending set is drained when the dispatch runnable fires;
 *   later writes inside the same `dispatch()` call accumulate against the
 *   next post.
 * - **Item resolution by key**: an [ItemRegistry] of all `Item` instances
 *   constructed via the [LauncherPrefs] factories provides key → Item lookup.
 *   `subscribeByImpact` is expressed as `subscribe(items where impact in set)`
 *   evaluated against the registry at subscribe time. Items registered after
 *   `subscribeByImpact` will not auto-route to that subscriber — re-subscribe
 *   if you add new pref definitions at runtime (no such code today).
 *
 * Phase 1 of the unified preference framework, per
 * `docs/plans/003-unified-prefs-framework-v2.md`. No production callers yet;
 * callers move in Phases 2 and 3.
 */
class PrefChangeDispatcher internal constructor(
    private val launcherPrefs: LauncherPrefs,
) {

    private data class Subscription(
        val subscriber: PrefSubscriber,
        val items: Set<Item>,
    )

    private val subscriptions = IdentityHashMap<PrefSubscriber, Subscription>()

    /** SharedPreferences instances we've already registered a listener on. */
    private val registeredPrefs = CopyOnWriteArraySet<SharedPreferences>()

    /** Pending changes accumulated since the last delivery. Guarded by [pendingLock]. */
    private val pending = HashSet<Item>()
    private val pendingLock = Any()
    private var dispatchScheduled = false

    /** Underlying listener forwarded to every observed SharedPreferences file. */
    private val sharedPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            // Resolve the changed key to one or more Item instances via the registry.
            val matched = ItemRegistry.byKey(key)
            if (matched.isEmpty()) return@OnSharedPreferenceChangeListener
            scheduleDispatch(matched)
        }

    /**
     * Subscribe [subscriber] to changes on any of [items]. Returns an
     * [AutoCloseable] that unsubscribes when closed; subscribers are also free
     * to call [unsubscribe] directly with the same subscriber instance.
     *
     * Double-subscribing the same instance overwrites the prior item set.
     */
    @Synchronized
    fun subscribe(subscriber: PrefSubscriber, vararg items: Item): AutoCloseable {
        val itemSet = items.toSet()
        subscriptions[subscriber] = Subscription(subscriber, itemSet)
        // Make sure we have a listener attached to every SharedPreferences file
        // backing any subscribed item.
        for (item in itemSet) {
            val sp = launcherPrefs.getSharedPrefsForListening(item)
            if (registeredPrefs.add(sp)) {
                sp.registerOnSharedPreferenceChangeListener(sharedPrefListener)
            }
        }
        return AutoCloseable { unsubscribe(subscriber) }
    }

    /**
     * Subscribe [subscriber] to all [Item]s whose [Item.impact] is in [impacts].
     * Resolved against [ItemRegistry] at subscribe time; new Items constructed
     * later will not auto-route to this subscriber.
     */
    fun subscribeByImpact(
        subscriber: PrefSubscriber,
        vararg impacts: SettingImpact,
    ): AutoCloseable {
        val impactSet = impacts.toSet()
        val items = ItemRegistry.all().filter { it.impact in impactSet }
        return subscribe(subscriber, *items.toTypedArray())
    }

    /** Unsubscribe [subscriber]. No-op if not subscribed. */
    @Synchronized
    fun unsubscribe(subscriber: PrefSubscriber) {
        subscriptions.remove(subscriber)
    }

    private fun scheduleDispatch(matched: Set<Item>) {
        val needsPost: Boolean
        synchronized(pendingLock) {
            pending.addAll(matched)
            needsPost = !dispatchScheduled
            if (needsPost) dispatchScheduled = true
        }
        if (needsPost) {
            MAIN_EXECUTOR.execute(::dispatch)
        }
    }

    private fun dispatch() {
        val changes: Set<Item>
        synchronized(pendingLock) {
            changes = HashSet(pending)
            pending.clear()
            dispatchScheduled = false
        }
        if (changes.isEmpty()) return
        // Snapshot the subscription set so callbacks can safely modify the
        // dispatcher (subscribe/unsubscribe) from within their callback.
        val snapshot: List<Subscription>
        synchronized(this) {
            snapshot = subscriptions.values.toList()
        }
        for (sub in snapshot) {
            val matched = changes.intersect(sub.items)
            if (matched.isNotEmpty()) {
                try {
                    sub.subscriber.onPrefsChanged(matched)
                } catch (t: Throwable) {
                    // Don't let one rogue subscriber poison the rest of the tick.
                    Log.e(TAG, "Subscriber threw in onPrefsChanged", t)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PrefChangeDispatcher"
    }
}
