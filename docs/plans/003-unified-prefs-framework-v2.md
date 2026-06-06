# Plan 003 — Unified Preference-Change Framework v2

Supersedes the v1 framework plan in `docs/plans/000-architectural-refactor-superplan.md`.

## 5-level impact enum

| Level | Triggers | Example |
|-------|----------|---------|
| `FULL_RECONFIGURE` | `IDP.onConfigChanged` → `Launcher.onIdpChanged` → `dispatchDeviceProfileChanged` + `reapplyUi` + `mModel.rebindCallbacks` | grid topology, DB file |
| `GRID_GEOMETRY` | `initDeviceProfile` + `dispatchDeviceProfileChanged` + `reapplyUi`, NO `mModel.rebindCallbacks` | header padding, RV padding, label size |
| `ITEM_RENDER` | ThemeManager re-skin only; no DP rebuild | icon pack swap, icon scale |
| `VIEW_INVALIDATE` | `view.invalidate()` | paint, color, opacity |
| `LISTENER_ONLY` | Framework delivers; no cascade | search providers, taskbar flags |

## API

```kotlin
fun subscribe(subscriber: PrefSubscriber, vararg items: Item): AutoCloseable
fun subscribeByImpact(subscriber: PrefSubscriber, vararg impacts: SettingImpact): AutoCloseable

fun interface PrefSubscriber { fun onPrefsChanged(changes: Set<Item>) }
```

`Item.impact: SettingImpact` defaults to `VIEW_INVALIDATE`.

`LauncherPrefChangeListener` (`LauncherPrefs.kt:174`) kept untouched.

**Dedup**: `IdentityHashMap<PrefSubscriber, Subscription>`. Dispatch iterates subscribers, not registrations — double-subscribe yields one callback per tick.

**Threading**: dispatcher posts to `MAIN_EXECUTOR` regardless of writer thread (covers `RestoreDbTask` background-thread writes). Coalesces multiple writes into one delivery per tick.

**ProxyPrefs**: register listener lazily per `SharedPreferences` instance the dispatcher observes.

## Per-pref classification

(See full table in superplan tertiary cascade map; key updates from v1:)

| Pref | v1 said | v2 says | Why |
|------|---------|---------|------|
| `DRAWER_BG_COLOR` / `_OPACITY` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED from current effective FULL_RECONFIGURE) | Paint only; settings fragment OVER-EAGER calls onConfigChanged |
| `DRAWER_SEARCH_BG_*` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED) | Same |
| `DRAWER_SCROLLBAR_COLOR` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED) | Same |
| `DRAWER_TAB_*_COLOR` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED) | Same |
| `FOLDER_*_COLOR` / `_OPACITY` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED) | Same |
| `SHOW_TOP_SHADOW` | VISUAL_ONLY | `VIEW_INVALIDATE` (DOWNGRADED) | Single view alpha |
| `DRAWER_HIDE_TABS` | VISUAL_ONLY or ITEM_REBIND | `GRID_GEOMETRY` | RV padding via FloatingHeaderView.getMaxTranslation |
| `DRAWER_MATCH_HOME` | VISUAL_ONLY | `GRID_GEOMETRY` | Switches drawer grid column count |
| `ICON_SIZE_SCALE` / `_DRAWER` | GRID_GEOMETRY or FULL_RECONFIGURE | `ITEM_RENDER` | Per change 044, applied at render time |
| `ICON_PACK*`, `APPLY_ADAPTIVE_SHAPE*`, `WRAP_UNSUPPORTED_ICONS*`, `ICON_WRAP_BG_*` | varied | `ITEM_RENDER` | ThemeManager.verifyIconState |
| `SEARCH_*` (8 prefs) | varied | `LISTENER_ONLY` | One consumer in UniversalSearchAlgorithm |
| Grid topology prefs (`GRID_NAME`, `WORKSPACE_SIZE`, `DB_FILE`, etc.) | FULL_RECONFIGURE | `FULL_RECONFIGURE` | Unchanged — authoritative topology |

## Phased migration

1. **Phase 1** — Land framework. No behavior change. Add `Item.impact` field defaulting to `VIEW_INVALIDATE`. Implement dispatcher + subscribe APIs + dedup map.
2. **Phase 2** — Migrate audit-flagged on-demand-read sites to subscribe-and-cache: drawer colors in `ActivityAllAppsContainerView`, `DRAWER_HIDE_TABS` in `setupHeader()` (respecting `mSuppressSetupHeader` invariant), search provider enable set in `UniversalSearchAlgorithm`.
3. **Phase 3** — Per-pref downgrades. For each OVER-EAGER pref:
   - Add `impact = ...` to companion declaration.
   - Add subscriber on consumer side.
   - Delete the `IDP.onConfigChanged(...)` call from the settings fragment.
   - Run regression test (`tests-e2e/regression/test_prefs_cascade.py`) before next.
   - Order: drawer paint → folder paint → GRID_GEOMETRY prefs.
4. **Phase 4** (deferred, optional) — Migrate `RotationHelper`, `SysUiScrim`, `ThemeManager`, `DisplayController`, IDP's own `LauncherPrefChangeListener` consumers.

## `onConfigChanged` interaction

`IDP.onConfigChanged` is NOT replaced. In Phase 3:
- IDP subscribes via `subscribeByImpact(this::onConfigChanged, FULL_RECONFIGURE, GRID_GEOMETRY)` (refined: GRID_GEOMETRY routes through a lighter `onLayoutChanged` path that fires `OnIDPChangeListener.onIdpChanged(modelPropertiesChanged=false)`).
- Settings fragments stop calling `IDP.onConfigChanged()` directly; framework drives.

## Test plan

`tests-e2e/regression/test_prefs_cascade.py`. One parameterized test per migrated pref:
1. Record stale-state marker (DP hash for GRID_GEOMETRY; adapter identity for VIEW_INVALIDATE).
2. Change pref via settings.
3. Return to drawer/workspace/folder.
4. Assert visual change applied + stale marker matches expectation.
5. "No-regression cap": `mModel.rebindCallbacks` invocation count stays at or below baseline.

## Risks

- Restore-from-backup writer thread (Phase 1 unit test).
- Double-subscribe dedup (Phase 1 unit test).
- Animation interaction with `mSuppressSetupHeader` invariant.
- `ProxyPrefs` lazy registration.
- `commit()` re-entry — same-frame OSPCL fire; documented but enqueue-and-post defers anyway.

## Non-goals

- No replacement / deprecation of `LauncherPrefChangeListener`.
- No DI, Coroutines, Flow, LiveData.
- No settings UI restructure (only 4 writer call sites change in Phase 3).
- No `IDP.onConfigChanged` body changes; only callers.

## Critical files

- `src/com/android/launcher3/LauncherPrefs.kt`
- `src/com/android/launcher3/InvariantDeviceProfile.java`
- `src/com/android/launcher3/Launcher.java`
- `src/com/android/launcher3/settings/AppDrawerColorsFragment.java`
- `src/com/android/launcher3/graphics/ThemeManager.kt`
