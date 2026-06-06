# 088 — T2.3 Phase 4: migrate remaining `LauncherPrefChangeListener` consumers

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-06-06
**Type:** Refactor (last open Phase of T2.3)

## Context

`docs/plans/003-unified-prefs-framework-v2.md` Phase 4 finishes the
T2.3 migration to the unified prefs framework. After Phases 1-3, the
following classes still held legacy `LauncherPrefChangeListener` /
`prefs.addListener(...)` call sites:

- `SysUiScrim` (SHOW_TOP_SHADOW)
- `RotationHelper` (ALLOW_ROTATION)
- `ThemeManager` (icon-pack / shape / wrap pref bundle)
- `DisplayController` (TASKBAR_PINNING, TASKBAR_PINNING_IN_DESKTOP_MODE)
- `InvariantDeviceProfile` (FIXED_LANDSCAPE_MODE, ENABLE_TWOLINE_ALLAPPS_TOGGLE)

Each now uses the same `getPrefChanges().subscribe(...)` /
`AutoCloseable.close()` pattern that drawer colors and `AllAppsState`
have used since Phase 2/3. Behavior is preserved — every callback path
still drives the same downstream notification — but all five now go
through the dedup + coalescing dispatcher.

## Migration pattern

Two flavors used, depending on whether the original code differentiated
by key:

### Flavor A — single-key, no membership check needed

`SysUiScrim` / `ThemeManager`. Original code already reacted the same
way to any subscribed key, so the new subscriber drops the
`equals(key)` filter — the dispatcher already filters by subscribed
items so any callback means *one of our keys fired*.

```java
mPrefSubscription = LauncherPrefs.get(ctx)
        .getPrefChanges()
        .subscribe(changes -> refreshSomething(), KEY1, KEY2, ...);
```

### Flavor B — multi-key, per-key branch

`DisplayController` / `InvariantDeviceProfile`. Original code branched
on `equals(key)`; the new subscriber inspects the `Set<Item> changes`
parameter via `contains()`.

```java
AutoCloseable sub = prefs.getPrefChanges().subscribe(changes -> {
    if (changes.contains(KEY_A) && /* state-actually-changed */) { ... }
    else if (changes.contains(KEY_B) && /* ... */) { ... }
}, KEY_A, KEY_B);
```

### `RotationHelper` — toggleable subscription

`RotationHelper` is special: it adds the listener only when not in
"ignore auto-rotate" mode (large devices skip the toggle entirely).
The migration tracks the `AutoCloseable` in a `@Nullable` field and
re-subscribes when ignore-mode flips off, closing-and-nulling when it
flips on:

```java
@Nullable private AutoCloseable mAllowRotationSubscription;

private void setIgnoreAutoRotateSettings(boolean ignore) {
    if (!ignore) {
        if (mAllowRotationSubscription == null) {
            mAllowRotationSubscription = prefs.getPrefChanges()
                    .subscribe(mAllowRotationSubscriber, ALLOW_ROTATION);
        }
    } else {
        closeAllowRotationSubscription();
    }
}
```

## Init-order pitfall (documented for future migrations)

`SysUiScrim` and `RotationHelper` both have lambdas that capture a
`final` instance field (`mContainer` / `mActivity`) which is itself
assigned in the constructor body. Defining the `PrefSubscriber` as a
field initializer fails to compile (`variable might not have been
initialized`). The fix is to assign the subscriber inside the
constructor *after* the captured field is set:

```java
private final PrefSubscriber mPrefSubscriber;  // declared

public Class(...) {
    mField = ...;
    mPrefSubscriber = changes -> useMField();  // assigned post-mField
}
```

## SafeCloseable vs AutoCloseable

`DaggerSingletonTracker.addCloseable(SafeCloseable)` accepts only
`SafeCloseable`, but `PrefChangeDispatcher.subscribe(...)` returns
`AutoCloseable`. `DisplayController` and `InvariantDeviceProfile` wrap
the subscription with a SAM lambda that swallows the (unthrown)
checked exception:

```java
AutoCloseable sub = prefs.getPrefChanges().subscribe(...);
lifecycle.addCloseable(() -> {
    try { sub.close(); } catch (Exception ignored) {}
});
```

A follow-up could narrow `PrefChangeDispatcher.subscribe(...)`'s return
type to `SafeCloseable` to drop this boilerplate, but that touches the
framework's public API and is out of scope for Phase 4.

## Files changed

| File | Change |
|---|---|
| `src/com/android/launcher3/graphics/SysUiScrim.java` | Replace `LauncherPrefChangeListener mPrefListener` with `PrefSubscriber mPrefSubscriber` + `AutoCloseable mPrefSubscription`. |
| `src/com/android/launcher3/states/RotationHelper.java` | Drop `implements LauncherPrefChangeListener`. Migrate ALLOW_ROTATION to toggleable subscription. |
| `src/com/android/launcher3/graphics/ThemeManager.kt` | Single `prefs.prefChanges.subscribe(...)` call covering the full icon-pref bundle; drop legacy listener + import. |
| `src/com/android/launcher3/util/DisplayController.java` | Subscribe with per-key `changes.contains(...)` branches. |
| `src/com/android/launcher3/InvariantDeviceProfile.java` | Same pattern as DisplayController for FIXED_LANDSCAPE_MODE + ENABLE_TWOLINE_ALLAPPS_TOGGLE. |

## Verification

- Build: `assembleDebug` passes.
- Tests: full smoke + regression suite on `emulator-5554` — see test
  command in the commit body.
- No new tests required — the migration preserves observable behavior;
  existing regression tests (rotation, taskbar pinning is a
  Quickstep-only feature pruned from this fork, theme refresh on icon
  pack change, fixed-landscape toggle) cover the surfaces touched.

## State of T2.3

Phase 4 was tagged "deferred, optional" in plan 003 — completed here
because the user requested it. With this change, **all five originally
identified Phase-4 targets are migrated**. The only remaining legacy
`LauncherPrefChangeListener` consumer in the tree (per grep) is the
interface declaration itself in `LauncherPrefChangeListener.java`,
which `LauncherPrefs.addListener` / `removeListener` still expose for
back-compat. Removing the legacy API is *not* part of plan 003 — it's
called out as a non-goal there. T2.3 is now complete.
