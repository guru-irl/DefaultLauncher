# All-Apps Drawer — Load-Bearing Invariants

This document catalogues the non-obvious invariants embedded in
`ActivityAllAppsContainerView`, `FloatingHeaderView`,
`PrivateProfileManager`, and the search transition path. Each entry exists
because of a specific incident or bug fix; removing or refactoring the
mechanism without understanding the precedent will likely reintroduce that
bug.

**Use this document when:** refactoring the drawer container, writing
plans that touch drawer state, reviewing PRs that delete what looks like
"redundant defensive code", or designing a new pref-propagation path that
might interact with these flags.

## Read this first

Every entry below is supported by a numbered change document under
`docs/changes/`. Verify before mutating.

## The 14 invariants

### Search animation suppression

#### 1. `mSuppressSetupHeader` (boolean) — gate setupHeader during search anim
**Site:** `ActivityAllAppsContainerView.java:189` (declaration),
`:606` (set true), `:617` (cleared), `:1048` (early-return).
**Established by:** change 023 (search-perf, drawer-cache, crash-fixes).
**Prevents:** 6+ layout passes per search animation frame.
`setFloatingRowsCollapsed()` triggers `setupHeader()` twice per call;
during animation that becomes O(frames) layout thrash.
**Rule:** any new code path that calls `setupHeader()` MUST check
`mSuppressSetupHeader` first, or be invoked outside the animation window.

#### 2. `mPendingSearchExitWork` (deferred runnable) — split exit work across frames
**Site:** `ActivityAllAppsContainerView.java:192` (declaration),
`:642-656` (post body), `:606` (cancel before new anim).
**Established by:** change 023.
**Prevents:** jank on the animation-end frame. Defers `setupHeader()`,
`setSearchResults(null)`, page-restoration to the next frame after the
search-exit animation completes.
**Rule:** must be cancelled before starting a new `animateToSearchState()`
or the deferred work collides with the new animation's first frame.

#### 3. `mKeepKeyboardOnSearchExit` — asymmetric set/check timing
**Site:** `ActivityAllAppsContainerView.java:441` (set before enter anim),
`:650` (checked after exit anim).
**Established by:** change 023 / persistent-search-mode work.
**Prevents:** IME dismissal during backspace-to-empty + scroll cycle.
**Rule:** This is NOT a state enum candidate. The flag is intentionally
set before animation starts and checked AFTER animation ends — collapsing
it into a synchronous state machine loses the temporal semantics.

#### 4. `mImmediateRestart` — skip end-callback reset on cancel-then-restart
**Site:** `RecyclerViewAnimationController.java:76, 185-187`.
**Established by:** change 023.
**Prevents:** one-frame flash when a search animation is cancelled and
immediately restarted in the same frame.

#### 5. `SearchTransitionController` hardware-layer toggle
**Site:** `SearchTransitionController.java:87-92`.
**Established by:** change 023.
**Prevents:** GPU compositing overhead persisting after animation. Layer
is set to `LAYER_TYPE_HARDWARE` at animation start, reverted at end.

### Floating header geometry

#### 6. `FloatingHeaderView.mHeaderCollapsed` is derived from scroll, not animator
**Site:** `FloatingHeaderView.java:257-283` (`moved()`).
**Established by:** AOSP base behavior preserved through customization.
**Prevents:** state divergence when user scrolls during a collapse/expand
animation. The animator cancels and `moved()` re-derives state from
`mTranslationY` / `mSnappedScrolledY`.
**Rule:** if you introduce an explicit state enum, scroll input must STILL
be authoritative — the enum should be a label of the current scroll-derived
state, not a primary source of truth.

#### 7. `getMaxTranslation()` conflates clip offset and scroll snap distance
**Site:** `FloatingHeaderView.java:237-255`.
**Established by:** changes 005 (pinned tabs) + 012 (tab hide pref).
**Loosely-load-bearing:** the branches depend on `mFloatingRowsCollapsed`,
tab visibility, and resource gaps. Splitting into two methods is a
candidate for T3.1 but until then, every caller relies on this specific
formula.

#### 8. `setupHeader()` reads `mUsingTabs` and `DRAWER_HIDE_TABS` together
**Site:** `ActivityAllAppsContainerView.java:1050-1051`.
**Brittle:** these two values are read in immediate succession with no
guard that they agree. A pref change between them produces a stale frame.
**Status:** T2.3 (prefs framework v2) will route both through one cache.

### Profile management

#### 9. `PrivateProfileManager` three orthogonal booleans
**Site:** `PrivateProfileManager.java:129` (`mIsAnimationRunning`),
`:131` (`mReadyToAnimate`), `:135` (`mIsStateTransitioning`).
**Established by:** private-space feature.
**Prevents:** state divergence when model state changes during an
animation. Each flag tracks a distinct concern: animation lifecycle,
view-bind readiness, and model state transition direction.
**Rule:** collapsing these into a single enum loses the orthogonality.

#### 10. `mReadyToAnimate` set BEFORE unlock, checked AFTER in `updateView`
**Site:** `PrivateProfileManager.java:307` (set), `:393` (checked).
**Brittle:** if layout happens between line 307 and the deferred
`postUnlock()` (line 316), views render in wrong state.
**Status:** known minor flake; T3.1 considers consolidating.

#### 11. `MAIN_EXECUTOR.post(::exitSearchAndExpand)` guarded by `isSearching()`
**Site:** `PrivateProfileManager.java:315-318`.
**Established by:** private-space + search interaction.
**Prevents:** layered animations — private-space expand + search exit
running same frame causes two setupHeader() invocations and visible jank.

#### 12. `notifyDataSetChanged()` after PS state transition
**Site:** `PrivateProfileManager.java:704`.
**Established by:** AOSP TODO `b/325455879` (DiffUtil decoration blindness).
**Prevents:** stale `SectionDecorationInfo` round-region bits / colors on
private-space lock or unlock. `AdapterItem.isContentSame()` ignores
`decorationInfo`, so DiffUtil cannot detect decoration-only changes.
**Rule:** DO NOT REMOVE this line until T1.1 lands. T1.1 will fix
`isContentSame()` to compare `decorationInfo`; only then can the
`notifyDataSetChanged()` be removed and replaced with targeted notifies.

### Recycler / pool

#### 13. `AllAppsRecyclerViewPool` preinflation cancellation on DP change
**Site:** `AllAppsRecyclerViewPool.kt:124, 168-170`.
**Established by:** change 023.
**Prevents:** inflating views with stale layout manager after a
configuration change (rotation, foldable unfold).

#### 14. SysUiScrim hint-driven visibility (cold-start race fix)
**Site:** `SysUiScrim.java:128, 159-160`.
**Established by:** change 047 (sysui-scrim color-hint race).
**Prevents:** dark scrim flashing on light wallpapers at cold start. The
listener reads `WallpaperColorHints` directly rather than relying on a
cached theme attribute that lags wallpaper update events.

## Decision criteria

Before removing or restructuring any of the above:

1. Find the referenced `docs/changes/NNN-…md` and read what it fixed.
2. Manually reproduce the bug it fixed (most have repro steps documented).
3. Show how your refactor preserves the bug-prevention property.
4. Add a regression test in `tests-e2e/regression/` that fails without
   the preserved property and passes with it.
5. Reference this invariants document in your PR description.

## Related plans

- **T1.1 (DiffUtil decoration fix)** unblocks removal of invariant #12.
- **T2.3 (prefs framework v2)** systematically addresses invariant #8.
- **T3.1 (drawer decomposition v2)** must explicitly preserve invariants
  1–14; each phase of the v2 plan is gated on regression tests for the
  affected invariants.

## Maintenance

Add to this document whenever a non-obvious bug-prevention pattern is
introduced. Each entry should reference the change doc that established it
and explain the property in one line.
