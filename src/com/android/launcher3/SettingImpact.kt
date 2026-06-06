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
 * Classification of how a preference change must propagate through the launcher
 * stack. Used by the [PrefChangeDispatcher] (and ultimately by the settings UI
 * and IDP) to choose the minimum reconfiguration path.
 *
 * Levels are ordered from most-invasive to least-invasive. A subscriber that
 * registers for [FULL_RECONFIGURE] does **not** automatically receive
 * [GRID_GEOMETRY] notifications — `subscribeByImpact` callers must list every
 * impact they care about explicitly.
 *
 * See `docs/plans/003-unified-prefs-framework-v2.md` for the per-pref mapping
 * and migration guide.
 */
enum class SettingImpact {
    /**
     * Triggers full `IDP.onConfigChanged` → `Launcher.onIdpChanged` →
     * `dispatchDeviceProfileChanged` + `reapplyUi` + `mModel.rebindCallbacks`.
     * Required when the grid topology or the on-disk DB layout changes.
     */
    FULL_RECONFIGURE,

    /**
     * Triggers `initDeviceProfile` + `dispatchDeviceProfileChanged` + `reapplyUi`,
     * but **not** `mModel.rebindCallbacks` — model state is unchanged. Used for
     * pixel-level layout changes (header padding, RV padding, label size).
     */
    GRID_GEOMETRY,

    /**
     * Triggers a `ThemeManager` re-skin only. No DeviceProfile rebuild. Used for
     * icon-pack swaps, adaptive-shape toggles, icon scale.
     */
    ITEM_RENDER,

    /**
     * Default level. Triggers `view.invalidate()` on subscribers — paint, color,
     * opacity, alpha. No measure/layout pass.
     */
    VIEW_INVALIDATE,

    /**
     * Framework dispatches the notification; no cascade is implied. Subscribers
     * (e.g., the universal search algorithm) decide what to do.
     */
    LISTENER_ONLY,
}
