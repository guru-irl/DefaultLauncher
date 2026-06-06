# 049 — Orphaned preference triage (no-op)

## Summary

The tertiary audit (`docs/plans/000-architectural-refactor-superplan.md`,
finding #3) flagged a list of preferences as "orphaned" — written by settings
UI but never read by code. T0.2 was scoped to either wire readers or remove
writers + UI for each.

A re-verification grep against `src/` confirmed that **none of the flagged
prefs are orphans**. Every one of them has at least one reader. The
tertiary audit's grep was incomplete (likely missed by feature-scoped search
windows). This change documents the corrected status.

## Per-pref status (verified by grep over `src/`)

| Pref | Reader site | Writer site | Status |
|------|-------------|-------------|--------|
| `DRAWER_SEARCH_BG_COLOR` | `AppsSearchContainerLayout.java:137` | `AppDrawerColorsFragment.java:47` | Wired |
| `DRAWER_SEARCH_BG_OPACITY` | `AppsSearchContainerLayout.java:138` | `AppDrawerColorsFragment.java:79` | Wired |
| `DRAWER_SCROLLBAR_COLOR` | `RecyclerViewFastScroller.java:188,226` | `AppDrawerColorsFragment.java:49` | Wired |
| `DRAWER_TAB_SELECTED_COLOR` | `ActivityAllAppsContainerView.java:893` | `AppDrawerColorsFragment.java:51` | Wired |
| `DRAWER_TAB_UNSELECTED_COLOR` | `ActivityAllAppsContainerView.java:894` | `AppDrawerColorsFragment.java:53` | Wired |
| `ICON_WRAP_BG_COLOR` | `ThemeManager.kt:199` | `ColorPickerPreference` via `HomeScreenFragment.java:84` | Wired |
| `ICON_WRAP_BG_COLOR_DRAWER` | `ThemeManager.kt:202` | `ColorPickerPreference` via `AppDrawerFragment.java:84` | Wired |
| `ICON_WRAP_BG_OPACITY` | `ThemeManager.kt:200` | none yet | Stub (UI hidden behind adaptive-shape gating; opacity slider not wired) |
| `ICON_WRAP_BG_OPACITY_DRAWER` | `ThemeManager.kt:203` | none yet | Stub (same) |
| `FOLDER_ICON_SHAPE` | `FolderSettingsHelper.java:109` | none | Stub for planned UI; currently always falls through to ThemeManager default |

## Real follow-up (handled elsewhere)

Two minor propagation inconsistencies surfaced during this verification:

1. The drawer / folder color prefs trigger `IDP.onConfigChanged()` from
   their settings fragment (heavy cascade — `dispatchDeviceProfileChanged`,
   `mModel.rebindCallbacks`, etc.) just to apply a paint change. The
   icon-wrap-bg color prefs instead use the lightweight pattern: writer
   persists, `ThemeManager`'s `LauncherPrefChangeListener` rebuilds icons.
   The lightweight pattern is the better default for view-only state and is
   the model that the unified-prefs-framework plan (T2.3) will adopt.

2. `ICON_WRAP_BG_OPACITY*` declarations exist but no slider in the icon
   settings fragments writes them. Leaving as a stub is intentional — the
   adaptive-shape feature gating is still being designed. No regression.

## Action

No code change. The "orphan" claim was wrong. Verification recorded here so
future audits don't re-litigate.

## References

- Superplan tertiary finding #3.
- T2.3 (unified prefs framework v2) will address the propagation
  inconsistency systematically.
