# 087 — Drawer respects padding prefs + 6th default dock icon

**Branch:** `refactor/t0.1-search-4param-override`
**Date:** 2026-06-06
**Type:** Polish + UX

Builds on 086 (workspace top/bottom padding prefs) and the smart-default
follow-up commit. Two small changes the user requested after seeing the
sliders land.

## App drawer top padding follows workspace pref

**Before:** in non–bottom-sheet drawer mode, `allAppsPadding.top = 0`. The
drawer's search bar / first row sat against the synthesized inset from
`LauncherRootView.handleSystemWindowInsets()`, which is fine for the
synthesized value but inconsistent with how the workspace handles the
same value.

**After:** in square-grid mode, `allAppsPadding.top = inv.workspaceTopPaddingPx`
— the same pref value the workspace uses. The search bar and the
drawer's first row now sit at the same vertical position from the top
of the screen as the workspace's first row.

Touch point: `DeviceProfile.java` lines ~870, added a new branch for
`inv.isSquareGrid` between the bottom-sheet and the legacy zero-top
fallback.

Bottom padding flows through automatically because the
`LauncherRootView` override already synthesizes `mInsets.bottom` from
`inv.workspaceBottomPaddingPx`. The drawer's nav-bar scrim and recycler
view bottom padding already key off `mInsets.bottom`, so they pick up
the pref value without further changes.

## 6th default dock icon (Calendar)

**Before:** `default_workspace_5x5.xml` defined 5 hotseat positions
(screen 0–4): Phone, Messaging, Maps/Music, Browser, Camera. On a fresh
install with the square-grid override bumping `numShownHotseatIcons` to
the user's column count (6), the 6th hotseat slot was empty.

**After:** screen=5 is populated with an `APP_CALENDAR` intent + a
calendar content-provider URI fallback. On devices with fewer than 6
hotseat columns the entry is harmlessly ignored — out-of-range hotseat
positions are filtered at load time.

Chose Calendar specifically because:
- Universally available on Android (no OEM gap).
- Distinct category from the existing 5 (no duplication with
  bottom-row Email/Gallery/Market entries).
- Common in user expectations of a "default" dock layout.

## Files changed

| File | Change |
|---|---|
| `src/com/android/launcher3/DeviceProfile.java` | `allAppsPadding.top = inv.workspaceTopPaddingPx` in square-grid non-sheet path |
| `res/xml/default_workspace_5x5.xml` | Add `screen=5` hotseat Calendar entry |

## Verification

- AVD `emulator-5554`: pm clear → cold launch → screenshot shows 6 dock
  icons (Phone, Messages, Maps, Chrome, Camera, Calendar).
- Drawer open with default 40 dp top pad on AVD: search bar sits at the
  same Y as the workspace's first icon row.
- Test suite: 23/23 passing (4 padding regressions + 19 smoke tests).

## Not done (deferred to future change)

- Apply the 6th icon to the larger `default_workspace_6x5.xml` and
  `default_workspace_5x8.xml`. The 5x5 layout is the one used by the
  default phone grid; the others come into play for tablets / large
  screens where the dock already has more space.
- Visual regression test for drawer-respects-pref (would need a baseline
  screenshot; deferred to the visuals/ test pass).
