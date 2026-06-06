# 067 — Unified preference-change framework: foundation

T2.3 Phase 1 of `docs/plans/003-unified-prefs-framework-v2.md`. No behavior
change — adds API surface only.

## What ships

Four new top-level types in `com.android.launcher3`:

- `SettingImpact` — five-level enum: `FULL_RECONFIGURE`, `GRID_GEOMETRY`,
  `ITEM_RENDER`, `VIEW_INVALIDATE` (default), `LISTENER_ONLY`. Documents
  the minimum cascade each pref class requires.
- `PrefSubscriber` — `fun interface` taking `Set<Item>`.
- `ItemRegistry` — process-wide `key → Set<Item>` directory populated by
  every `ConstantItem` / `ContextualItem` `init` block. Backs the
  dispatcher's key→item resolution and `subscribeByImpact` enumeration.
- `PrefChangeDispatcher` — the listener/router. Subscribe by item set or
  by impact set; AutoCloseable return for symmetric unregister; main-
  thread delivery; coalesces multiple writes per tick; per-subscriber
  identity-dedup via `IdentityHashMap`.

Two existing types gain fields:

- `Item.impact: SettingImpact` (open, defaults to `VIEW_INVALIDATE`).
- `ConstantItem.impact` and `ContextualItem.impact` mirror the abstract.

One internal hook on `LauncherPrefs`:

- `getSharedPrefsForListening(item)` exposes the existing `getSharedPrefs`
  to the dispatcher without widening the protected accessor's visibility
  to subclasses. `ProxyPrefs` continues to override `getSharedPrefs` and
  its bypass remains the source of truth for which file backs which item.

One lazy accessor:

- `LauncherPrefs.prefChanges: PrefChangeDispatcher` constructed on first
  read. Phase 2/3 callers obtain the dispatcher this way; Phase 1 has
  none.

## What the dispatcher guarantees

| Property | Mechanism |
|----------|-----------|
| Main-thread delivery | `MAIN_EXECUTOR.execute(::dispatch)` regardless of writer thread (covers `RestoreDbTask` background-thread `apply()`). |
| Per-tick coalescing | `pending: HashSet<Item>` accumulates between schedule and dispatch; one main-thread post per coalesce window. |
| Per-subscriber dedup | `IdentityHashMap` ⇒ double-subscribe overwrites prior item set; one callback per subscriber per tick. |
| Subscriber isolation | Each callback wrapped in try/catch — a throwing subscriber doesn't poison siblings or the dispatcher. |
| Lazy listener registration | `OnSharedPreferenceChangeListener` is attached to each backing `SharedPreferences` only on first subscribe touching that file. Re-attach is no-op (CopyOnWriteArraySet dedup). |
| `ProxyPrefs` compatibility | `getSharedPrefsForListening` delegates to the existing `getSharedPrefs` override, so ProxyPrefs-backed items observe their proxied file. |

## What's deliberately NOT in Phase 1

- No `IDP.onConfigChanged` migration (Phase 3).
- No Settings-fragment caller changes (Phase 3).
- No production subscribers wired up. The framework is dormant.
- No `LauncherPrefChangeListener` replacement — kept intact per the plan's
  non-goal.
- No DI / Coroutines / Flow.

## Verification

- `assembleDebug` clean (4 new files, 1 modified file).
- `tests-e2e/smoke/` + `regression/`: 21/21 in 58.71s. The framework is
  dormant; tests pass because nothing wires it up yet — confirms no
  behavior change.
- ItemRegistry registration via `init` block — every existing
  LauncherPrefs companion-object `Item` is now in the registry (~80
  items), validated by the build passing without `NoClassDefFoundError`
  during the launcher process bring-up that registers every item.

## Risks (deferred to Phase 2/3 callouts)

- Restore-from-backup background-thread writes still post to MAIN_EXECUTOR
  via the dispatcher — no unit test in this PR but the design is
  exercised by `tests-e2e/regression/test_prefs_cascade.py` once
  consumers land in Phase 2/3.
- Double-subscribe semantics: overwrite-not-additive. Documented in
  `PrefChangeDispatcher.subscribe` kdoc.

## Files

- `src/com/android/launcher3/SettingImpact.kt` (new, 55 lines)
- `src/com/android/launcher3/PrefSubscriber.kt` (new, 24 lines)
- `src/com/android/launcher3/ItemRegistry.kt` (new, 41 lines)
- `src/com/android/launcher3/PrefChangeDispatcher.kt` (new, 153 lines)
- `src/com/android/launcher3/LauncherPrefs.kt`
  - `Item.impact` (open, default VIEW_INVALIDATE).
  - `ConstantItem.impact`, `ContextualItem.impact` (passthrough with
    default).
  - `ItemRegistry.register(this)` in each `init` block.
  - `getSharedPrefsForListening` internal hook.
  - `prefChanges` lazy accessor.
- `docs/changes/067-prefs-framework-foundation.md` (this file)
