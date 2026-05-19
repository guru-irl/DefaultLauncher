# Plan 004 — All-Apps Drawer Decomposition v2

Supersedes T3.1 of `docs/plans/000-architectural-refactor-superplan.md`.

Decomposes `ActivityAllAppsContainerView` (2168 LOC) into collaborator classes while preserving the 14 load-bearing invariants in `docs/architecture/drawer-invariants.md`. Five phases, each shipping behind the smoke + regression + visuals gate (currently 25 tests, ~5 min). Phases are ordered lowest-risk → highest-risk and are independently revertable.

## Goals / non-goals

- Goal: cut `ActivityAllAppsContainerView` to under ~800 LOC of "container + delegation", with five named collaborators owning distinct concerns.
- Goal: every invariant 1–14 maps to a specific phase's regression test.
- Non-goal: no AOSP-divergence increase beyond what is already justified per change-doc precedent. `FloatingHeaderView` (AOSP-origin) keeps its file identity; only the container side moves.
- Non-goal: no new product features.
- Non-goal: no behavior change visible to the user. Visual baselines at `tests-e2e/visuals/baseline/` are byte-exact gates.

## Decomposition target

| Collaborator | Owns | Phase introduced |
|---|---|---|
| `DrawerColorController` | `mHeaderPaint`, `mNavBarScrimPaint`, `mScrimColor`, `mHeaderColor`, `mBottomSheetBackgroundColor`, `mBottomSheetBackgroundAlpha`, `mTabsProtectionAlpha`, `refreshCustomColors()`, `applyCustomTabColors()`, `getHeaderColor()`, `drawOnScrimWithScaleAndBottomOffset()` helpers | 1 |
| `SearchFabController` | `mFabContainer`, `mAiSearchFab`, `mSearchOnlineFab`, `mCurrentSearchQuery`, `updateSearchFabs()`, `loadAiAppIcon()`, `launchAiSearch()`, `launchWebSearch()`, `resolveAiPackage()`, IME-inset FAB margin from `dispatchApplyWindowInsets` | 1 |
| `DrawerInsetsController` | `mInsets`, `mNavBarScrimHeight`, `setInsets()`, `applyAdapterSideAndBottomPaddings()`, `computeNavBarScrimHeight()`, `dispatchApplyWindowInsets` body (minus FAB hand-off) | 2 |
| `ProfileCoordinator` | `mWorkManager`, `mPrivateProfileManager`, `mHasWorkApps`, `mHasPrivateApps`, `mPersonalMatcher`, profile-aware adapter holder lookups, `resetAndScrollToPrivateSpaceHeader()`, work/private signals into `rebindAdapters()` | 3 |
| `SearchLifecycle` | `mSearchState`, `mSuppressSetupHeader`, `mKeepKeyboardOnSearchExit`, `mPendingSearchExitWork`, `mRebindAdaptersAfterSearchAnimation`, `mSearchTransitionController` orchestration, `animateToSearchState()`, `isSearching()`, `setSearchResults()`, `onClearSearchResult()`, `showAppsWhileSearchActive()`, `reset(animate, exitSearch)` SearchState-reset block | 4 |
| `HeaderCoordinator` | `mHeader` lifecycle, `setupHeader()`, `updateRVContainerRules()`, `replaceAppsRVContainer()`, `mUsingTabs`, `mDrawerHideTabs`, header-padding propagation to adapter holders, scroll/header coupling via `mScrollListener` + `updateHeaderScroll()` | 5 |

Container retains: `mAH`/`AdapterHolder`, `mAllAppsStore`, lifecycle (`onFinishInflate`/`onAttached`/`onDetached`), touch dispatch, `getActiveRecyclerView()`, draw-time scrim composition (delegating paint).

## Phase 1 — Extract `DrawerColorController` + `SearchFabController` (lowest risk)

**Goal:** lift two non-stateful concerns off the container.

**Files added**
- `src/com/android/launcher3/allapps/DrawerColorController.java`
- `src/com/android/launcher3/allapps/SearchFabController.java`

**Files modified**
- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java` — replace fields and bodies with delegate calls; `mDrawerPrefSubscriber.onPrefsChanged` routes color/tab-color changes to `DrawerColorController`; FAB block in `initContent` and `dispatchApplyWindowInsets` moves to `SearchFabController`.

**Files deleted:** none.

**Public API**
- `DrawerColorController(Context, T activityContext)`
  - `void onAttach(ActivityAllAppsContainerView<?>)`, `void onDetach()`
  - `void refresh()` (replaces `refreshCustomColors()`)
  - `void applyTabColors(View personalTab, View workTab)` (replaces `applyCustomTabColors()`)
  - `int getHeaderColor(float blendRatio)`, `int getBottomSheetBackgroundColor()`, `float getBottomSheetBackgroundAlpha()`, `int getTabsProtectionAlpha()`, `void setTabsProtectionAlpha(int)`, `Paint getHeaderPaint()`, `Paint getNavBarScrimPaint()`
- `SearchFabController(Context materialCtx, LauncherPrefs prefs)`
  - `View buildContainer()` returning the `LinearLayout`
  - `void onQueryChanged(@Nullable String query, boolean inSearch)` (replaces `updateSearchFabs`)
  - `void applyImeInsets(int imeBottom, int navBottom)`

**Invariants preserved**
- **#14 SysUiScrim hint:** untouched — separate file. Phase 1 does NOT extract `SysUiScrim` logic.
- **#1 / #8 (DRAWER_HIDE_TABS, mSuppressSetupHeader):** the `mDrawerPrefSubscriber` listener still calls `setupHeader()` from the container; only color paths leave. Verified by keeping `mDrawerHideTabs` cache + `setupHeader()`/`updateRVContainerRules()` calls in the container.

**Regression tests** in `tests-e2e/regression/test_drawer_decomposition.py`
- `test_drawer_color_pref_invalidates_paint_without_relayout` — toggle `DRAWER_BG_OPACITY`, assert `drawOnScrim` repaints (visual diff vs baseline) and `mModel.rebindCallbacks` count unchanged (subscribe to logcat for `rebindAdapters` log).
- `test_drawer_tab_color_pref_no_setup_header_storm` — toggle `DRAWER_TAB_SELECTED_COLOR` with work profile installed, count `rebindAdapters: force` log lines = 0.
- `test_search_fabs_appear_when_query_nonempty` — open search, type "c", assert FAB container visibility flips to `VISIBLE`.
- `test_search_fabs_disappear_on_query_clear` — clear query, assert FAB container animates out and ends `GONE`.
- `test_search_fab_ime_margin_tracks_keyboard` — open IME, capture FAB bottom margin; close IME, capture again; assert delta ≈ IME height.

**Smoke gate:** 25-test suite green.

**Manual pass (4 min):** open drawer, toggle drawer bg color in settings (verify paint), toggle drawer opacity (verify), open search, type a query (verify both FABs appear), tap web FAB (verify intent), backspace (verify FABs collapse).

**Risk flags:** color paint already migrated to pref subscriber framework (per change 071) — the field move is mechanical. Lowest-risk phase by design.

## Phase 2 — Extract `DrawerInsetsController`

**Goal:** isolate inset + nav-bar-scrim plumbing.

**Files added**
- `src/com/android/launcher3/allapps/DrawerInsetsController.java`

**Files modified**
- `ActivityAllAppsContainerView.java` — `setInsets()`, `dispatchApplyWindowInsets()`, `applyAdapterSideAndBottomPaddings()`, `dispatchDraw()` nav-bar-scrim block delegate to controller. `computeNavBarScrimHeight()` becomes a hook the controller calls.

**Public API**
- `DrawerInsetsController(ActivityAllAppsContainerView<?> host, DrawerColorController colors)`
  - `void applyInsets(Rect insets, DeviceProfile dp)`
  - `WindowInsets onDispatchApplyWindowInsets(WindowInsets insets)`
  - `void drawNavBarScrim(Canvas c, float scaleX, float scaleY, int width, int height)`
  - `int getNavBarScrimHeight()`

**Invariants preserved**
- **#14 SysUiScrim:** still untouched.
- Insets path was not previously cited in any invariant; correctness gated by smoke (`test_drawer_basics.test_drawer_opens_and_shows_icons`).

**Regression tests**
- `test_drawer_inset_landscape_phone` — rotate to landscape, assert padding.right >= insets.right (skipped on tablets).
- `test_navbar_scrim_alpha_during_predictive_back` — initiate three-button-nav predictive back, assert scrim rect visible (probe via screenshot diff at known-y coord).
- `test_padding_recomputes_on_dp_change` — change icon size pref (GRID_GEOMETRY), assert `mAH[MAIN].mPadding.bottom` updates within one frame.

**Smoke gate:** 25 + Phase 1's 5 = 30 tests green.

**Manual pass (3 min):** open drawer; rotate device; verify left/right margins; open IME via search; verify FAB stays above IME; close drawer, swipe up from a landscape orientation.

**Risk flags:** `dispatchApplyWindowInsets` has dual ownership (controller wants insets, Phase 1's `SearchFabController` wants IME bottom). Resolved by having the container route insets to both in known order: insets → fab.

## Phase 3 — Extract `ProfileCoordinator`

**Goal:** consolidate work + private profile management behind one collaborator. Honor invariant #12's pending DiffUtil hold-back.

**Files added**
- `src/com/android/launcher3/allapps/ProfileCoordinator.java`

**Files modified**
- `ActivityAllAppsContainerView.java` — `mWorkManager`/`mPrivateProfileManager` fields move; `onAppsUpdated()` work/private branches move; `resetAndScrollToPrivateSpaceHeader()` moves; `shouldShowTabs()` delegates; `inflateWorkCardsIfNeeded()` moves.
- `PrivateProfileManager.java` — public surface unchanged; only the back-reference to `mAllApps` is preserved (still needed for `isSearching()` and adapter access).

**Public API**
- `ProfileCoordinator(T activityContext, ActivityAllAppsContainerView<?> host)`
  - `WorkProfileManager getWorkManager()`, `PrivateProfileManager getPrivateProfileManager()`
  - `boolean hasWorkApps()`, `boolean hasPrivateApps()`, `boolean shouldShowTabs()`
  - `void onAppsUpdated(AppInfo[] apps)`
  - `void resetAndScrollToPrivateSpaceHeader()`
  - `Predicate<ItemInfo> personalMatcher()`, `Predicate<ItemInfo> workMatcher()`

**Invariants preserved**
- **#9 Three orthogonal booleans:** the booleans stay inside `PrivateProfileManager` (unchanged file). `ProfileCoordinator` is a passthrough.
- **#10 `mReadyToAnimate`:** also intra-file; not touched.
- **#11 `MAIN_EXECUTOR.post(::exitSearchAndExpand)` guarded by `isSearching()`:** `PrivateProfileManager.postUnlock()` keeps the `mAllApps.isSearching()` read. `ProfileCoordinator` does not interpose.
- **#12 `notifyDataSetChanged()` after PS state transition:** `PrivateProfileManager.java:704` line preserved verbatim. Phase 3 PR description must reference invariant #12 and confirm the line still exists. Blocked from removal until T1.1 (already shipped, change 051) is verified to make AdapterItem.isContentSame compare decorationInfo — but invariants doc still flags it; do not touch until a separate cleanup PR audits whether T1.1 fully obviates it.

**Regression tests**
- `test_work_tab_appears_when_work_profile_present` — install work profile (mock via test fixture), open drawer, assert tabs visible.
- `test_private_space_unlock_during_search_defers_via_exitSearchAndExpand` — enter search, trigger PS unlock, assert search exits before PS expand (verify only one `setupHeader` log line in the window).
- `test_private_space_lock_notifies_data_set_changed` — assert that after lock, `notifyDataSetChanged` logcat marker appears (instrument via a `Log.d` retained for this test).
- `test_apps_updated_skips_rebind_when_searching` — enter search, push an `onAppsUpdated` from test, assert `rebindAdapters` call count = 0.

**Smoke gate:** 30 + 4 = 34 green.

**Manual pass (5 min):** install/uninstall work profile (or simulate); lock/unlock private space while in search; lock/unlock private space while on workspace; verify section decoration colors don't ghost after lock.

**Risk flags:** `PrivateProfileManager` already holds a `mAllApps` back-reference. Be careful that `ProfileCoordinator` doesn't accidentally become a second back-channel — keep `PrivateProfileManager.mAllApps` as the single source.

## Phase 4 — Extract `SearchLifecycle` (HIGHER RISK)

**Goal:** move the SearchState machine and exit-deferred-work plumbing into a dedicated collaborator. Per invariant doc #1–#5 + change 070 lesson.

**Files added**
- `src/com/android/launcher3/allapps/SearchLifecycle.java` — owns `SearchState` enum, `mSearchState`, `mSuppressSetupHeader`, `mKeepKeyboardOnSearchExit`, `mPendingSearchExitWork`, `mRebindAdaptersAfterSearchAnimation`. Constructor takes the host container + a `HeaderCallbacks` interface the container implements (`setupHeader()`, `getCurrentPage()`, `setCurrentPage(int)`, `updateSearchResultsVisibility()`).

**Files modified**
- `ActivityAllAppsContainerView.java` — `animateToSearchState`, `setSearchResults`, `isSearching`, `showAppsWhileSearchActive`, `onClearSearchResult`, and the `reset(...)` SearchState-reset block become delegations. `mSearchTransitionController` ownership moves to `SearchLifecycle`.
- `SearchTransitionController.java` — `onEndRunnable` plumbing unchanged; only the wiring site moves.
- `RecyclerViewAnimationController.java` — `mImmediateRestart` (invariant #4) and hardware-layer toggle (invariant #5) unchanged.

**Public API**
- `enum SearchState { IDLE, ENTERING, SEARCHING, ACTIVE_EMPTY, EXITING }` — moved here.
- `SearchLifecycle(ActivityAllAppsContainerView<?> host, HeaderCallbacks header, SearchTransitionController stc)`
  - `void setSearchResults(ArrayList<AdapterItem>, int resultCode)`
  - `void animateToSearch(boolean goingToSearch, long durationMs)`
  - `void onClearSearchResult()`, `void showAppsWhileSearchActive()`
  - `boolean isSearching()`, `boolean isSearchTransitionRunning()`
  - `void onContainerReset(boolean exitSearch)` — encapsulates the change-070 reset hook (state→IDLE, `mKeepKeyboardOnSearchExit=false`, cancel `mPendingSearchExitWork`)
  - `void onActivePageChanged(int page)` — handles the "rebind after animation" path

**Invariants preserved**
| # | How |
|---|---|
| 1 `mSuppressSetupHeader` | field moves but stays read by `HeaderCallbacks.setupHeader()` impl via short-circuit check (`if (mLifecycle.isSuppressingSetupHeader()) return`). Public read added: `boolean isSuppressingSetupHeader()`. |
| 2 `mPendingSearchExitWork` | the `removeCallbacks(...)` site at start of `animateToSearch` moves intact; runnable still `post()`ed to the host View so `removeCallbacks` is symmetric. |
| 3 `mKeepKeyboardOnSearchExit` | asymmetric set/check preserved — set in `showAppsWhileSearchActive`, consumed in the deferred exit runnable. Documented inline. |
| 4 `mImmediateRestart` | not touched — lives in `RecyclerViewAnimationController`. |
| 5 hardware-layer toggle | not touched — lives in `SearchTransitionController.java:87-92`. |

**State-reset hooks** (explicit per change 070 lesson):
- `SearchLifecycle.onContainerReset(true)` — called from `ActivityAllAppsContainerView.reset(animate, exitSearch=true)` whenever exitSearch and not mid-animation; preserves IDLE reset.
- `SearchLifecycle.onContainerReset(false)` — no-op; keep the no-op explicit so future callers don't assume it.
- `SearchLifecycle.onActivePageChanged` — already routes through `reset(true, !isSearching())` per existing container code; the call chain is preserved.

**Regression tests** (these go in `tests-e2e/regression/test_search_lifecycle.py`)
- `test_search_state_resets_to_idle_on_home_return` — direct port of the change-070 manual repro: open drawer, search "chr", backspace to empty, press back, press back, re-open drawer, assert apps grid visible (not empty `search_results_list_view`). This test already implied by change 070 but should be explicitly named here.
- `test_search_animation_in_flight_reset_lets_onend_win` — start search animation, call `reset(true, true)` mid-animation (via test hook), assert no `IllegalStateException` and final state is IDLE.
- `test_rapid_enter_exit_no_setup_header_storm` — type/clear/type/clear rapidly, count `setupHeader` invocations: must be ≤ 2× the number of stable transitions (suppression invariant #1).
- `test_pending_exit_work_cancelled_on_restart` — start exit animation, before deferred runs, start enter; assert no stale ACTIVE_EMPTY transition.
- `test_keep_keyboard_on_soft_exit` — type "c", backspace to empty (triggers `showAppsWhileSearchActive`), assert IME stays visible after exit animation completes.

**Smoke gate:** 34 + 5 = 39 green.

**Manual pass (5 min):** open search; type 3 chars; backspace to empty (verify ACTIVE_EMPTY: keyboard up, apps visible); type again; back to dismiss; home; reopen drawer (verify IDLE — apps grid, not empty search list); rapid type/clear cycle; verify no flash.

**Risk flags:** State-machine extraction sits between `SearchTransitionController` (animation source of truth) and `setupHeader()` (consumer). The `mSuppressSetupHeader` cross-collaborator read is the weakest link — must be a method call on `SearchLifecycle`, not a duplicated field.

## Phase 5 — Extract `HeaderCoordinator` + finalize `FloatingHeaderView` boundary (RISKIEST)

**Goal:** move `setupHeader`, `updateRVContainerRules`, `replaceAppsRVContainer`, `updateHeaderScroll`, scroll-listener wiring, and `mUsingTabs`/`mDrawerHideTabs` cache into a dedicated coordinator. Tighten `FloatingHeaderView`'s container coupling without rewriting it.

**Per superplan risk flag: this phase must explicitly preserve `mSuppressSetupHeader` + `mPendingSearchExitWork` + `mKeepKeyboardOnSearchExit` invariants, even though Phase 4 already moved them.** The reason: `setupHeader()` is the consumer of `mSuppressSetupHeader`, and this phase moves the call sites. A typo could route a setupHeader call around the suppression check.

**Files added**
- `src/com/android/launcher3/allapps/HeaderCoordinator.java`

**Files modified**
- `ActivityAllAppsContainerView.java` — `setupHeader`, `updateRVContainerRules`, `replaceAppsRVContainer`, `mScrollListener`, `updateHeaderScroll`, `mUsingTabs`, `mDrawerHideTabs`, `applyTabColors` wiring delegate.
- `FloatingHeaderView.java` — minimal: add a package-private `setHostCoordinator(HeaderCoordinator)` callback so the `mOnScrollListener.onScrolled` "headerCollapsed changed → parent.invalidateHeader()" path can route via the coordinator instead of an `(ActivityAllAppsContainerView<?>) getParent()` cast. Cast is preserved as fallback. **Do NOT touch `moved()` (invariant #6) or `getMaxTranslation()` (invariant #7).**

**Public API**
- `HeaderCoordinator(ActivityAllAppsContainerView<?> host, SearchLifecycle searchLifecycle, FloatingHeaderView header, List<AdapterHolder> ah, LauncherPrefs prefs)`
  - `void setupHeader()` — checks `searchLifecycle.isSuppressingSetupHeader()` first
  - `void updateRVContainerRules()`
  - `void replaceAppsRVContainer(boolean showTabs)`
  - `void onScrolled(int verticalOffset)` (host's `mScrollListener` delegates here)
  - `void onDrawerHideTabsChanged(boolean newValue)` — invariant #8: this is the single mutation site for `mDrawerHideTabs`; both `setupHeader()` and `updateRVContainerRules()` read from this cached field within one frame.
  - `boolean isUsingTabs()`
  - `int getHeaderProtectionHeight()` (delegating to existing container logic that needs `mHeader`)

**Invariants preserved**
| # | How |
|---|---|
| 1 `mSuppressSetupHeader` | `HeaderCoordinator.setupHeader()` first call: `if (mSearchLifecycle.isSuppressingSetupHeader()) return;` — line copied verbatim from the old setupHeader body. PR description must point to this line. |
| 2 `mPendingSearchExitWork` | Phase 4's deferred runnable still calls `setupHeader()` via `HeaderCallbacks.setupHeader()` → `HeaderCoordinator.setupHeader()`. No new posting added. |
| 3 `mKeepKeyboardOnSearchExit` | unchanged — `SearchLifecycle` owns it; `HeaderCoordinator` does not read it. |
| 6 `mHeaderCollapsed` scroll-derived | `FloatingHeaderView.moved()` untouched. Only the parent-callback path (`parent.invalidateHeader()`) is rerouted via coordinator; the source-of-truth derivation remains scroll-driven. |
| 7 `getMaxTranslation()` formula | untouched. `HeaderCoordinator.setupHeader()` calls `mHeader.getMaxTranslation()` exactly as the container did. |
| 8 `mUsingTabs` + `DRAWER_HIDE_TABS` co-read | `HeaderCoordinator` holds both as fields; `onDrawerHideTabsChanged` is the single update entrypoint and triggers `updateRVContainerRules()` + `setupHeader()` synchronously, so the two reads in `setupHeader` necessarily agree. |
| 13 `AllAppsRecyclerViewPool` preinflation cancel | `setUpCustomRecyclerViewPool` stays where it is (static helper); not part of this extraction. |

**Regression tests** in `tests-e2e/regression/test_header_coordinator.py`
- `test_setup_header_suppressed_during_search_anim` — start search animation, fire a pref change that would normally call setupHeader (e.g., DRAWER_HIDE_TABS toggle), assert setupHeader returns early (count = 0 during anim, 1 after).
- `test_max_translation_unchanged_with_tabs_hidden` — record `getMaxTranslation()` value before refactor (baseline value 0x_____ from change 005 / 012); assert unchanged. (Compute baseline via instrumented log line in current build; encode literal in test.)
- `test_header_collapse_state_scroll_derived_after_animator_cancel` — start a header animator, scroll during it, assert `mHeaderCollapsed` matches scroll position (not animator endpoint).
- `test_drawer_hide_tabs_toggle_no_stale_frame` — toggle DRAWER_HIDE_TABS pref, assert RV padding.top and VP topMargin match within the same frame (visual diff via screenshot).
- `test_replace_rv_container_preserves_pool` — switch work profile on/off, assert `AllAppsRecyclerViewPool` is not re-created (probe via identity log).

**Smoke gate:** 39 + 5 = 44 green.

**Manual pass (5 min):** open drawer; scroll up + down; verify floating-rows row reappears at top; toggle "Hide tabs" pref while work profile is active; verify no flash; open search; verify setupHeader doesn't fire mid-animation (logcat); private space lock with tabs hidden.

**Risk flags:**
- **Highest cross-coupling phase.** `HeaderCoordinator` reads from `SearchLifecycle`, calls into `ProfileCoordinator` (via host) for `shouldShowTabs`, and is the consumer of every paint/inset state. Order of construction in the container's constructor matters: colors → fabs → insets → profiles → search-lifecycle → header. Document the order in the class javadoc.
- The `FloatingHeaderView.mOnScrollListener` parent cast (line 71–73) is the only AOSP file change; justify in change doc.
- If any regression test fails, revert just this phase — Phases 1–4 are independently shippable.

## Non-goals

- No removal of invariant #12's `notifyDataSetChanged()` line. That is a separate cleanup gated on a deeper T1.1 audit.
- No `FloatingHeaderView` rewrite. `moved()` (invariant #6) and `getMaxTranslation()` (invariant #7) stay byte-identical. Splitting #7 into two methods is deferred.
- No removal of `SysUiScrim` work (invariant #14).
- No `RecyclerViewAnimationController` / `SearchTransitionController` refactor — invariants #4 / #5 stay.
- No new search state enum entries.
- No conversion of `AdapterHolder` into a top-level class.

## Critical files

- `src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java`
- `src/com/android/launcher3/allapps/FloatingHeaderView.java`
- `src/com/android/launcher3/allapps/PrivateProfileManager.java`
- `src/com/android/launcher3/allapps/SearchTransitionController.java`
- `src/com/android/launcher3/allapps/RecyclerViewAnimationController.java`

## Test plan

All new tests live under `tests-e2e/regression/`:
- `test_drawer_decomposition.py` (Phase 1: 5 tests)
- (Phase 2 tests appended to same file or `test_drawer_insets.py`: 3 tests)
- `test_profile_coordinator.py` (Phase 3: 4 tests)
- `test_search_lifecycle.py` (Phase 4: 5 tests)
- `test_header_coordinator.py` (Phase 5: 5 tests)

Smoke + visuals + existing regression suite must remain green each phase. Total regression count grows from 3 → 25 across the five phases.

Per-phase ship checklist: build clean → smoke green → phase regressions green → visuals green → manual exploratory → change doc → PR.

## Risks

- **Phase 5 (HeaderCoordinator) is the riskiest single phase.** It is the only phase that touches `FloatingHeaderView.java` (AOSP-origin) and the only phase that re-routes `setupHeader()` call sites. Invariants 1, 6, 7, 8 all touch it. Mitigation: ship Phase 4's state-machine extraction first so `mSuppressSetupHeader` already has a clean public method (`isSuppressingSetupHeader()`) before Phase 5 starts; revert Phase 5 in isolation if any regression appears.
- Phase 4 carries the secondary risk that the change-070 reset hook silently moves to the wrong site. Mitigation: `SearchLifecycle.onContainerReset(true)` is the single named entrypoint and is referenced by the test `test_search_state_resets_to_idle_on_home_return`.
- Cross-phase construction order in the container constructor (colors → fabs → insets → profiles → search-lifecycle → header) is brittle. Mitigation: encode as class-level javadoc and a `@VisibleForTesting` `getConstructionOrder()` enum.

## Open follow-ups (from drafting)

1. `ActivityAllAppsContainerView` was cited as "1900+ lines" in the superplan; current measurement is 2168 lines. Plan still fits.
2. Invariant doc line refs (e.g., `ActivityAllAppsContainerView.java:189`/`:606`/`:617`/`:1048` for invariant #1) drifted post-068/070; declarations now at ~222/677/694/1176. The plan leans on field/method names not line numbers; invariants doc should be refreshed in a separate doc-only PR.
3. Whether `AllAppsColorResolver` should also move into `DrawerColorController` is unaddressed — currently lives elsewhere. Phase 1 follow-up.
4. Whether `test_drawer_hide_tabs_toggle_no_stale_frame` (Phase 5) should be a visual diff (compares two PNGs via `tests-e2e/visuals/`) or structural (compares paddings via `dump_hierarchy`). Recommend visual-diff for the strongest gate.
