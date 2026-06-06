# 074 — Folder color migration to PrefChangeDispatcher

Completes the deferred second half of T2.3 Phase 2/3 documented in
`docs/changes/073`. Migrates the four folder-color prefs off the
`IDP.onConfigChanged` path by wiring `PrefSubscriber` consumers in
`FolderIcon`, `Folder`, and `PreviewBackground`.

## Prefs migrated

| Pref | Consumers wired |
|------|----------------|
| `FOLDER_BG_COLOR` | `FolderIcon` (cached bg + preview bg), `Folder` (panel bg) |
| `FOLDER_BG_OPACITY` | `FolderIcon` (cached bg + preview bg), `Folder` (panel bg) |
| `FOLDER_COVER_BG_COLOR` | `FolderIcon` (cached cover bg) |
| `FOLDER_COVER_ICON_COLOR` | `FolderIcon` (emoji cover drawable reload) |

## Changes per file

### `PreviewBackground.java`
- `refreshBgColor(Context)` — re-reads `FolderSettingsHelper.getEffectiveFolderBgColor`
  and calls `invalidate()`. Called by `FolderIcon`'s subscriber when
  `FOLDER_BG_COLOR` or `FOLDER_BG_OPACITY` changes.

### `FolderIcon.java`
- Added `mFolderPrefSubscription: AutoCloseable` + `mFolderPrefSubscriber: PrefSubscriber`.
- Subscriber covers all four folder-color prefs:
  - `FOLDER_BG_COLOR` / `FOLDER_BG_OPACITY`: calls `refreshCachedState()` +
    `mBackground.refreshBgColor()` + `invalidate()`.
  - `FOLDER_COVER_BG_COLOR`: calls `refreshCachedState()` + `invalidate()`.
  - `FOLDER_COVER_ICON_COLOR`: reloads `mCoverDrawable` via
    `FolderCoverManager.loadCoverDrawable(mInfo.id)` + `invalidate()`.
    `renderEmoji` reads color fresh each time, so reloading is sufficient to
    pick up the new color.
- `onAttachedToWindow()` subscribes to the four prefs; `onDetachedFromWindow()`
  closes the subscription.

### `Folder.java`
- Added `mFolderPrefSubscription: AutoCloseable` + `mFolderPrefSubscriber` that
  refreshes the open-panel drawable via
  `mBackground.setColor(FolderSettingsHelper.getEffectivePanelColor(getContext()))`.
- Subscribed in existing `onAttachedToWindow()`, closed in `onDetachedFromWindow()`.

### `AppDrawerColorsFragment.java`
- All four folder color pickers (`pref_folder_icon_color`,
  `pref_folder_cover_icon_color`, `pref_folder_bg_color`): changed
  `idpReconfigure` from `true` to `false`.
- `pref_folder_bg_opacity` slider: listener changed from IDP rebuild to no-op
  (matching the pattern of drawer opacity sliders after migration).

## Why this is safe

For each pref:
1. The writer still calls `SharedPreferences#apply` via the `Preference` default
   storage — returning `true` permits the write.
2. `PrefChangeDispatcher` observes the key and posts to `MAIN_EXECUTOR`.
3. The subscriber on `FolderIcon` / `Folder` fires and repaints.

`FolderIcon` is a `View`; its lifecycle is well-defined. The subscription lives
only while the icon is attached, which is the same window as its drawing lifetime.

`FolderCoverManager.renderEmoji` reads the icon color directly from
`FolderSettingsHelper.getEffectiveCoverIconColor(mContext)` at render time — no
stale bitmap cache. Reloading `mCoverDrawable` gives a freshly rendered emoji
with the new color.

## Not changed

- `FolderAnimationManager` — only reads colors from `PreviewBackground.getBgColor()`
  (which is now refreshed by `PreviewBackground.refreshBgColor()`). No direct
  pref reads, no subscription needed.
- `DragView` — reads folder bg from `PreviewBackground` via the icon. No direct
  subscription needed.
- The `idpReconfigure` parameter in `configureColorPickerWithDefault` / 
  `configureColorPicker` remains for the grid/icon-sizing prefs that still need
  full IDP reconfiguration (none currently, but the parameter is kept for clarity).

## Verification

- `assembleDebug`: clean.
- `tests-e2e/smoke/ + regression/ + visuals/`: 25/25.
- Manual: change folder bg color in settings → open a folder → verify panel
  color updates without restarting the launcher.
