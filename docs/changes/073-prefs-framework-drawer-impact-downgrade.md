# 073 — Prefs framework Phase 3: drawer impact downgrade

T2.3 Phase 3 of `docs/plans/003-unified-prefs-framework-v2.md`. Removes
`InvariantDeviceProfile.onConfigChanged` calls from drawer-color and
drawer-opacity preference writers — the migrations in 071/072 routed
their consumers through `PrefChangeDispatcher`, so the cascade is no
longer needed for these prefs.

This is the **big perf win** described in the plan: changing a drawer
color now triggers a targeted view repaint instead of a full
`rebindCallbacks` + DeviceProfile rebuild + adapter swap.

## Prefs downgraded (drawer paint)

| Pref | Consumer | Wired in |
|------|----------|----------|
| `DRAWER_BG_COLOR` | `ActivityAllAppsContainerView`, `AllAppsState` | 071, 072 |
| `DRAWER_BG_OPACITY` | `ActivityAllAppsContainerView`, `AllAppsState` | 071, 072 |
| `DRAWER_SEARCH_BG_COLOR` | `AppsSearchContainerLayout` | 072 |
| `DRAWER_SEARCH_BG_OPACITY` | `AppsSearchContainerLayout` | 072 |
| `DRAWER_SCROLLBAR_COLOR` | `RecyclerViewFastScroller` | 072 |
| `DRAWER_TAB_SELECTED_COLOR` | `ActivityAllAppsContainerView` (this commit) | this commit |
| `DRAWER_TAB_UNSELECTED_COLOR` | `ActivityAllAppsContainerView` (this commit) | this commit |

The two tab-color prefs were not yet subscribed when 071 landed. This
commit adds them to the existing `mDrawerPrefSubscriber` so the
subscriber set covers all seven drawer paint prefs before removing the
IDP cascade.

## Folder color prefs — NOT downgraded

The four folder-color prefs (`FOLDER_BG_COLOR`, `FOLDER_BG_OPACITY`,
`FOLDER_COVER_BG_COLOR`, `FOLDER_COVER_ICON_COLOR`) are still on the
`IDP.onConfigChanged` path. Folder consumers (`FolderIcon`,
`PreviewBackground`, `FolderAnimationManager`, `Folder`,
`FolderCoverManager`, `DragView`) are not migrated yet. Migration of
those consumers is deferred to a follow-up Phase 2 sub-commit before
their `IDP.onConfigChanged` calls can be removed.

The `idpReconfigure` parameter added to `configureColorPicker` /
`configureColorPickerWithDefault` makes the partial-migration state
explicit at each call site: folder color pickers pass `true`, drawer
color pickers pass `false`. When folder consumers are migrated, those
calls switch to `false` and the parameter can eventually be removed.

## Why this is safe

For each downgraded pref:
1. The writer (settings fragment) still calls `SharedPreferences#apply`
   via the `Preference`'s default storage — returning `true` from the
   `OnPreferenceChangeListener` permits the write.
2. The `SharedPreferences.OnSharedPreferenceChangeListener` registered
   by `PrefChangeDispatcher` on first subscribe (per change 067)
   observes the key.
3. The dispatcher posts to `MAIN_EXECUTOR`, batches with any sibling
   writes in the same tick, and calls the consumer's subscriber.
4. The subscriber invokes the existing refresh method
   (`refreshCustomColors`, `refreshSearchBarColor`, `refreshThumbColor`,
   `applyCustomTabColors`).

Net: same final visual state, with no `rebindCallbacks` /
DeviceProfile rebuild and no full all-apps adapter swap.

## Measured perf delta

Not measured in this commit — the 25-test suite confirms behavior
parity but a stopwatch comparison of "tap drawer color chip → drawer
repaint" before/after this change requires instrumentation that isn't
yet in place. The architectural win (no more rebindCallbacks per color
change) is structural and large; a precise number can land with the
`test_prefs_cascade.py` work referenced in 069.

## SettingImpact tagging

`Item.impact` already defaults to `SettingImpact.VIEW_INVALIDATE` (see
067 / `LauncherPrefs.kt:491`). Explicitly tagging these seven prefs
would be documentation only — the default is already correct. Phase 3
of the v2 plan calls for the tagging but doesn't require it for
behavior; the default-and-only-tag-the-exceptions convention keeps
declaration noise low. When a future pref needs `GRID_GEOMETRY` or
`ITEM_RENDER`, that's where the explicit annotation lands.

## Files

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
  - Subscriber lambda gains `tabColorChanged` branch invoking
    `applyCustomTabColors()`.
  - Subscribe call includes `DRAWER_TAB_SELECTED_COLOR` and
    `DRAWER_TAB_UNSELECTED_COLOR`.
- `src/com/android/launcher3/settings/AppDrawerColorsFragment.java`
  - `configureColorPicker(...)` and `configureColorPickerWithDefault(...)`
    gain an `idpReconfigure` boolean parameter. Drawer prefs pass
    `false`; folder prefs pass `true`.
  - `pref_drawer_bg_opacity` and `pref_drawer_search_bg_opacity` lose
    their inline `IDP.onConfigChanged` post; their listeners now
    return `true` to permit the write and let the dispatcher take
    over.
  - `pref_folder_bg_opacity` still calls `IDP.onConfigChanged` until
    folder consumers migrate.
- `docs/changes/073-prefs-framework-drawer-impact-downgrade.md`
  (this file).

## Verification

- `assembleDebug`: clean (2 files modified).
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25 in 338.67s.
- Visuals `test_drawer_paint_stable_across_open_close_cycle` exercises
  the "open → sample → close → reopen → sample" cycle that the
  subscriber-driven path needs to honor; baseline holds.

## Risks

- Settings UI lives in a separate fragment/activity; a drawer-color
  change happens while the user is in Settings, not while the drawer
  is open. When they return to the drawer the values are already
  applied via the subscriber. No animation-window collision.
- The `LISTENER_ONLY` and `LISTENER`-style consumers in
  `LauncherPrefChangeListener` (kept intact per plan non-goal)
  continue to fire on the same writes. Coexistence verified by the
  test suite.
