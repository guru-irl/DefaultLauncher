# 052 — Grid init-ordering guard + nav-mode invalidation key

## Summary

Two related grid-system hardening changes that the audit chain converged on:

1. **Init-ordering guard.** `DeviceProfile.getCellSize()` reads
   `workspacePadding`, which is finalized only after
   `updateWorkspacePadding()` returns. CLAUDE.md documents this hazard
   prominently. Add a debug-only `Log.w` in the non-square branch of
   `getCellSize()` when called before readiness. Square-grid devices
   (our shipping config) bypass the warning since their branch reads
   only `cellWidthPx` / `cellHeightPx`.
2. **Nav-mode invalidation.** The DPI-independent grid (change 044) uses
   `GRID_ROWS_NAV_HEIGHT` as part of the invalidation key. nav-height
   is a proxy that can collapse to the same value across different
   navigation modes on some devices. Add `GRID_ROWS_NAV_MODE` (ordinal
   of `displayInfo.getNavigationMode()`) as a sibling key.

## Why the nav-mode key is safe to ship

The secondary audit (Phase 2 review) flagged a concern that
`CHANGE_NAVIGATION_MODE` might fire to `IDP`'s priority listener before
`displayInfo.supportedBounds` reflected the new insets — which would
cause us to persist wrong geometry against the new mode ordinal. The
tertiary audit traced the actual pipeline:

```
DisplayController.notifyConfigChangeForDisplay
  → getNewInfo(oldInfo, ctx)
      → new Info(...)   // reads fresh navigationMode (line 523)
      → if (newInfo.navMode != oldInfo.navMode)
          newInfo = new Info(..., estimateInternalDisplayBounds(ctx))
          // ↑ recomputes bounds atomically with the new nav mode
  → MAIN_EXECUTOR.execute(() -> {
      perDisplayInfo.mInfo = newInfo;       // fresh info set FIRST
      mPriorityListener.onDisplayInfoChanged(...);
                                            // ← reads fresh info
    });
```

`displayInfo.supportedBounds` is computed inside the same `new Info(...)`
construction that observes the fresh nav mode. No race. The
secondary's drop-recommendation was based on a misreading; the
tertiary's full trace confirms the change is safe.

## Files

- `src/com/android/launcher3/LauncherPrefs.kt`
  - Add `GRID_ROWS_NAV_MODE` (`nonRestorableItem`, default `-1`).
- `src/com/android/launcher3/InvariantDeviceProfile.java`
  - Read `savedNavMode` + `currentNavMode = displayInfo.getNavigationMode().ordinal()`.
  - Include `savedNavMode == currentNavMode` in the match condition.
  - Persist `currentNavMode` alongside the other geometry keys.
- `src/com/android/launcher3/DeviceProfile.java`
  - Add `private boolean mWorkspacePaddingReady` field.
  - Reset to `false` at top of `updateAvailableDimensions()`.
  - Set `true` at the end of `updateWorkspacePadding()`.
  - In `getCellSize(Point)`, non-square branch: `Log.w` (debug-only) when
    called before readiness. Includes a stack trace for diagnosis.

## Verification

- Build green; smoke + regression suite 21/21 passing.
- On fresh install, `GRID_ROWS_NAV_MODE` writes the current ordinal on
  first geometry computation. Subsequent boots in the same nav mode are
  cache hits. Toggling system nav-mode triggers `CHANGE_NAVIGATION_MODE`
  → `IDP.onConfigChanged` → `initGrid` sees mismatch and recomputes
  geometry against the new mode + new nav-height.
- No `getCellSize()` warning fires on Pixel 7 Pro AVD with square grid
  (the only code path reading the warning lives in the AOSP-default
  non-square branch).

## References

- Tertiary finding #1 — disproves the secondary audit's race claim.
- Change 044 — DPI-independent grid (introduced nav-height key).
- Change 031 — workspace bottom-row clipping fix (related inset
  propagation history).
