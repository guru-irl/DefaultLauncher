/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.util.Log;

import com.android.launcher3.util.Preconditions;
import com.android.launcher3.views.ActivityContext;

/**
 * Collaborator for {@link ActivityAllAppsContainerView} that owns the search lifecycle:
 * the {@link SearchState} enum, all state fields, and the search-animation machinery.
 *
 * <p>Extracted in T3.1 Phase 4 (docs/plans/004-drawer-decomposition-v2.md, Phase 4).
 *
 * <p>Invariants preserved (docs/architecture/drawer-invariants.md):
 * <ul>
 *   <li>#1 {@code mSuppressSetupHeader}: field lives here; read by the container's
 *       {@code setupHeader()} via {@link #isSuppressingSetupHeader()}.  Any code path that
 *       calls {@code setupHeader()} after Phase 5 must check this method first.</li>
 *   <li>#2 {@code mPendingSearchExitWork}: cancelled before any new animation starts;
 *       posted to the host View so {@code removeCallbacks} is symmetric.</li>
 *   <li>#3 {@code mKeepKeyboardOnSearchExit}: set in {@link #showAppsWhileSearchActive()}
 *       BEFORE the animation starts; consumed in the deferred exit runnable AFTER the
 *       animation ends.  This temporal asymmetry is intentional — collapsing it into a
 *       synchronous state would lose the "keyboard stays up after backspace-to-empty"
 *       behaviour.  See docs/architecture/drawer-invariants.md.</li>
 *   <li>#4 {@code mImmediateRestart}: lives in RecyclerViewAnimationController, untouched.</li>
 *   <li>#5 hardware-layer toggle: lives in SearchTransitionController:87-92, untouched.</li>
 * </ul>
 *
 * <p>Change-070 reset hook: {@link #onContainerReset(boolean)} is the single named entry
 * point for the search-state reset that fires when the container returns to the home screen
 * (or any other IDLE-guaranteeing transition).  Callers MUST use this method, not set
 * {@code mSearchState} directly, so the invariants above are preserved atomically.
 */
class SearchLifecycle<T extends Context & ActivityContext> {

    private static final boolean DEBUG_SEARCH = com.android.launcher3.BuildConfig.DEBUG;
    private static final String TAG = "SearchLifecycle";

    // -------------------------------------------------------------------------
    // HeaderCallbacks — implemented by the container
    // -------------------------------------------------------------------------

    /**
     * Callbacks the lifecycle invokes on the container for header/visibility operations.
     * The container implements this interface and passes {@code this} to the constructor.
     *
     * <p>All methods are called on the main thread.
     */
    interface HeaderCallbacks {
        /**
         * Calls the container's {@code setupHeader()}, which first checks
         * {@link SearchLifecycle#isSuppressingSetupHeader()} before proceeding — invariant #1.
         */
        void setupHeader();

        /** Returns the current active page index (MAIN / WORK / SEARCH). */
        int getCurrentPage();

        /**
         * Sets the ViewPager current page. No-op if no ViewPager is active.
         *
         * @param page target page index (e.g. {@code AdapterHolder.MAIN}).
         */
        void setCurrentPage(int page);

        /**
         * Updates search/apps recycler-view visibility based on the current search state.
         * Called at animation-end and on state transitions to ensure the correct RV is visible.
         */
        void updateSearchResultsVisibility();
    }

    // -------------------------------------------------------------------------
    // SearchState enum
    // -------------------------------------------------------------------------

    /**
     * Lifecycle state for the search drawer. Transition table:
     * <ul>
     *  <li>IDLE → ENTERING: first non-empty query (setSearchResults non-null).
     *  <li>ENTERING → SEARCHING: SearchTransitionController animation completes.
     *  <li>SEARCHING → EXITING: animateToSearch(false).
     *  <li>EXITING → IDLE: deferred exit work runs with mKeepKeyboardOnSearchExit=false.
     *  <li>EXITING → ACTIVE_EMPTY: deferred exit work runs with mKeepKeyboardOnSearchExit=true.
     *  <li>ACTIVE_EMPTY → ENTERING (then SEARCHING): user types into the still-focused bar.
     *  <li>ACTIVE_EMPTY → EXITING (then IDLE): back/scroll/home dismissal.
     * </ul>
     * The boolean {@link #isSearching()} contract returns true for
     * {@code SEARCHING}, {@code ACTIVE_EMPTY}, and {@code ENTERING} — the UI is
     * showing or transitioning to a search-visible state.
     */
    enum SearchState {
        /** No search shown; A-Z apps list visible. */
        IDLE,
        /** Animation to search mode is running. */
        ENTERING,
        /** Search results visible, non-empty query. */
        SEARCHING,
        /**
         * Search bar focused with keyboard up, but the query is empty so the
         * A-Z apps list is rendered. New keystroke returns to SEARCHING.
         */
        ACTIVE_EMPTY,
        /** Animation back to apps list is running. */
        EXITING,
    }

    // -------------------------------------------------------------------------
    // Owned fields
    // -------------------------------------------------------------------------

    private final ActivityAllAppsContainerView<T> mHost;
    private final HeaderCallbacks mHeaderCallbacks;
    final SearchTransitionController mSearchTransitionController;

    /** Current search lifecycle state. Written only on the main thread. */
    SearchState mSearchState = SearchState.IDLE;

    /**
     * Suppresses {@link HeaderCallbacks#setupHeader()} during search animation.
     * Invariant #1: any new code path calling setupHeader() MUST check
     * {@link #isSuppressingSetupHeader()} first.
     */
    private boolean mSuppressSetupHeader;

    /**
     * True when rebindAdapters() was deferred because a search animation was running.
     * Checked (and cleared) in the animation-end callback.
     */
    boolean mRebindAdaptersAfterSearchAnimation;

    /**
     * Invariant #3: set BEFORE the exit animation begins (in showAppsWhileSearchActive),
     * consumed AFTER the exit animation ends (in the deferred runnable). This temporal
     * asymmetry is load-bearing — do not collapse into the SearchState machine.
     */
    private boolean mKeepKeyboardOnSearchExit;

    /**
     * Deferred runnable posted to the host View after a search-exit animation ends.
     * Cancelled if a new animation starts before it fires (invariant #2).
     */
    private Runnable mPendingSearchExitWork;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    SearchLifecycle(
            ActivityAllAppsContainerView<T> host,
            HeaderCallbacks headerCallbacks,
            SearchTransitionController searchTransitionController) {
        mHost = host;
        mHeaderCallbacks = headerCallbacks;
        mSearchTransitionController = searchTransitionController;
    }

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    /**
     * True when search UI is visible or transitioning in (SEARCHING, ACTIVE_EMPTY, ENTERING).
     * EXITING returns false: by the time onEndRunnable sets EXITING, the search recycler
     * is hidden and the apps view is restored.
     */
    boolean isSearching() {
        return mSearchState == SearchState.SEARCHING
                || mSearchState == SearchState.ACTIVE_EMPTY
                || mSearchState == SearchState.ENTERING;
    }

    /** True while the search enter/exit animation is running. */
    boolean isSearchTransitionRunning() {
        return mSearchTransitionController.isRunning();
    }

    /**
     * Read accessor for invariant #1. The container's {@code setupHeader()} checks this.
     *
     * @return true if {@code setupHeader()} should return immediately without laying out.
     */
    boolean isSuppressingSetupHeader() {
        return mSuppressSetupHeader;
    }

    SearchTransitionController getSearchTransitionController() {
        return mSearchTransitionController;
    }

    // -------------------------------------------------------------------------
    // rebindAdapters helper
    // -------------------------------------------------------------------------

    /**
     * If a search animation is running, records that rebindAdapters() needs to fire at
     * animation-end and returns {@code true} (caller should return early).
     * If no animation is running, returns {@code false} (caller may proceed normally).
     */
    boolean markRebindPendingIfAnimating() {
        if (mSearchTransitionController.isRunning()) {
            mRebindAdaptersAfterSearchAnimation = true;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Core search state machine
    // -------------------------------------------------------------------------

    /** Main-thread-only state mutator. */
    private void setSearchState(SearchState newState) {
        Preconditions.assertUIThread();
        if (DEBUG_SEARCH) {
            Log.d(TAG, "setSearchState: " + mSearchState + " → " + newState);
        }
        mSearchState = newState;
    }

    /**
     * Shows the A-Z app list while keeping the search bar active (keyboard up, focused).
     * Called when the user backspaces to empty text.
     *
     * <p>Sets {@link #mKeepKeyboardOnSearchExit} BEFORE starting the exit animation —
     * invariant #3.
     */
    void showAppsWhileSearchActive() {
        if (mSearchTransitionController.isRunning()) {
            return;
        }
        if (mSearchState != SearchState.SEARCHING) {
            return;
        }
        // Keep keyboard and search bar focus — only play the visual transition.
        // mKeepKeyboardOnSearchExit is set BEFORE the animation starts so the deferred
        // exit runnable (which runs AFTER the animation ends) can read the correct value.
        mKeepKeyboardOnSearchExit = true;
        animateToSearch(false, ActivityAllAppsContainerView.DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    /**
     * Animates the drawer between A-Z apps list and search-results view.
     *
     * <p>This is the central search-state-machine method.  All five invariants
     * (1–5) are upheld here:
     * <ol>
     *   <li>mSuppressSetupHeader set at start, cleared at end (invariant #1).</li>
     *   <li>mPendingSearchExitWork cancelled at start if present (invariant #2).</li>
     *   <li>mKeepKeyboardOnSearchExit consumed in the exit deferred runnable (invariant #3).</li>
     *   <li>mImmediateRestart lives in RecyclerViewAnimationController (invariant #4).</li>
     *   <li>Hardware-layer toggle lives in SearchTransitionController (invariant #5).</li>
     * </ol>
     *
     * @param goingToSearch {@code true} to animate into search mode, {@code false} to exit.
     * @param durationMs    animation duration in milliseconds.
     */
    void animateToSearch(boolean goingToSearch, long durationMs) {
        // Reset suppression from any previously cancelled animation.
        mSuppressSetupHeader = false;
        // Guard: redundant call from the same stable state. ACTIVE_EMPTY and
        // EXITING both have isSearching() reads that would mask the need for a
        // fresh transition, so the comparison is the precise SEARCHING vs.
        // not-SEARCHING boolean rather than the broader isSearching().
        if (!mSearchTransitionController.isRunning()
                && goingToSearch == (mSearchState == SearchState.SEARCHING)) {
            return;
        }
        // Cancel any pending deferred exit work so it doesn't collide with the
        // new animation's first frames and cause jank (invariant #2).
        if (mPendingSearchExitWork != null) {
            mHost.removeCallbacks(mPendingSearchExitWork);
            mPendingSearchExitWork = null;
        }
        // Suppress setupHeader() during animation — setFloatingRowsCollapsed() triggers it
        // 2× per call, causing 6+ layout passes on every animation frame (invariant #1).
        mSuppressSetupHeader = true;
        // Enter the transient animation state.
        setSearchState(goingToSearch ? SearchState.ENTERING : SearchState.EXITING);
        mHost.mFastScroller.setVisibility(goingToSearch ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        if (goingToSearch) {
            // Fade out the work-apps pause FAB while entering search.
            mHost.mProfileCoordinator.getWorkManager().onActivePageChanged(SEARCH);
        } else if (mHost.mAllAppsTransitionController != null) {
            mHost.mAllAppsTransitionController.animateAllAppsToNoScale();
        }

        mSearchTransitionController.animateToState(goingToSearch, durationMs,
                /* onEndRunnable = */ () -> {
                    mSuppressSetupHeader = false;
                    if (goingToSearch) {
                        setSearchState(SearchState.SEARCHING);
                    }
                    // For the exit case, stay in EXITING until the deferred runnable
                    // resolves the final state (IDLE vs ACTIVE_EMPTY).
                    mHeaderCallbacks.updateSearchResultsVisibility();
                    int previousPage = mHeaderCallbacks.getCurrentPage();
                    if (mRebindAdaptersAfterSearchAnimation) {
                        mHost.rebindAdapters(false);
                        mRebindAdaptersAfterSearchAnimation = false;
                    }

                    if (goingToSearch) {
                        mHeaderCallbacks.setupHeader();
                        mHost.mSearchUiDelegate.onAnimateToSearchStateCompleted();
                    } else {
                        // Reset animation-applied transforms immediately so the
                        // animation-end frame is clean.
                        mHost.getAppsRecyclerViewContainer().setTranslationY(0);
                        mHost.mHeader.setTranslationY(0);
                        mHost.mHeader.setAlpha(1f);
                        // Uncollapse floating rows + reset header position (matches AOSP).
                        mHost.mHeader.setFloatingRowsCollapsed(false);
                        mHost.mHeader.reset(false);

                        // Defer heavy work to next frame so animation-end frame stays clean.
                        // Track the runnable so it can be cancelled if a new animation starts
                        // before it runs (invariant #2).
                        mPendingSearchExitWork = () -> {
                            mPendingSearchExitWork = null;
                            // Re-entry guard: a fresher ENTERING/SEARCHING transition has
                            // already moved us off EXITING — drop the stale cleanup.
                            if (mSearchState != SearchState.EXITING) return;
                            mHeaderCallbacks.setupHeader();
                            // Clear search results from adapter (null clears, no animation fired).
                            mHost.setSearchResults(null);
                            mHeaderCallbacks.setCurrentPage(previousPage);
                            if (mKeepKeyboardOnSearchExit) {
                                // Invariant #3: flag consumed here, after animation ends.
                                mKeepKeyboardOnSearchExit = false;
                                setSearchState(SearchState.ACTIVE_EMPTY);
                            } else {
                                setSearchState(SearchState.IDLE);
                                mHost.onActivePageChanged(previousPage);
                            }
                        };
                        mHost.post(mPendingSearchExitWork);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Container reset hook (change-070 lesson)
    // -------------------------------------------------------------------------

    /**
     * Resets search state when the container exits to home or a non-search state.
     *
     * <p>This is the single named entry point for the change-070 reset hook. Callers in
     * {@link ActivityAllAppsContainerView#reset(boolean, boolean)} MUST use this method
     * rather than touching state fields directly — that's what produces the IDLE guarantee
     * that prevents ACTIVE_EMPTY bleeding across drawer open/close cycles.
     *
     * <p>Per change 079: {@link #mKeepKeyboardOnSearchExit} is cleared unconditionally BEFORE
     * checking {@link #isSearchTransitionRunning()}.  When reset() fires mid-animation
     * (user pressed HOME within the 300ms search-exit window), clearing the flag here ensures
     * the deferred runnable resolves to IDLE rather than ACTIVE_EMPTY.
     *
     * @param exitSearch if {@code true}, reset the search state machine; otherwise no-op.
     */
    void onContainerReset(boolean exitSearch) {
        if (!exitSearch) return;
        // Always clear the keyboard flag so the deferred exit runnable resolves to
        // IDLE rather than ACTIVE_EMPTY (invariant per docs/changes/079).
        mKeepKeyboardOnSearchExit = false;
        if (mSearchTransitionController.isRunning()) {
            // Mid-animation: let the onEnd runnable land the state naturally.
            // mKeepKeyboardOnSearchExit cleared above ensures it resolves to IDLE.
        } else {
            mSearchState = SearchState.IDLE;
            if (mPendingSearchExitWork != null) {
                mHost.removeCallbacks(mPendingSearchExitWork);
                mPendingSearchExitWork = null;
            }
        }
        // When mSearchState was SEARCHING, the enter animation set appsContainer.alpha=0.
        // The normal exit animation restores it via onProgressUpdated(1). But when reset()
        // is called (e.g. HOME press), mSearchState is set to IDLE synchronously, causing
        // animateToSearch(false) to return early via its guard — the exit animation never
        // runs, and appsContainer.alpha stays 0. Force-reset the visual state so the apps
        // list is visible on the next drawer open. See docs/changes/084.
        android.view.View appsContainer = mHost.getAppsRecyclerViewContainer();
        appsContainer.setAlpha(1f);
        appsContainer.setTranslationY(0f);
        // Also reset the header transforms that the search animation sets.
        mHost.mHeader.setAlpha(1f);
        mHost.mHeader.setTranslationY(0f);
    }
}
