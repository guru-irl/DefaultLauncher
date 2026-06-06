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

import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.util.ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE;

import android.content.Context;
import android.graphics.Outline;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.views.ActivityContext;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;

/**
 * Collaborator for {@link ActivityAllAppsContainerView} that owns the drawer header and tab
 * layout: tab visibility, RV container layout rules, header scroll animation, and the scroll
 * listener wiring.
 *
 * <p>Extracted in T3.1 Phase 5 (docs/plans/004-drawer-decomposition-v2.md, Phase 5).
 * This is the riskiest phase — it touches {@link FloatingHeaderView} (AOSP-origin) and moves
 * {@code setupHeader()} call sites.
 *
 * <p><b>Construction order (per plan risk note):</b>
 * colors → fabs → insets → profiles → search-lifecycle → <b>header</b>.
 * The coordinator must be the LAST collaborator created so that {@link SearchLifecycle}
 * is already initialised when {@link #setupHeader()} is first called.
 *
 * <p>Invariants preserved (docs/architecture/drawer-invariants.md):
 * <ul>
 *   <li>#1 {@code mSuppressSetupHeader}: every entry to {@link #setupHeader()} MUST call
 *       {@code mSearchLifecycle.isSuppressingSetupHeader()} first (line is the FIRST line of
 *       the method). Phase 5 is the consumer phase — Phase 4 moved the field; Phase 5 moves
 *       the call sites. Any new code path that calls setupHeader() must also check this.
 *       </li>
 *   <li>#2 {@code mPendingSearchExitWork}: the deferred runnable now calls
 *       {@code setupHeader()} via {@link SearchLifecycle.HeaderCallbacks#setupHeader()} →
 *       this method. The invariant is preserved because the check is here.</li>
 *   <li>#3 {@code mKeepKeyboardOnSearchExit}: owned by SearchLifecycle; this coordinator
 *       does not read it.</li>
 *   <li>#6 {@code mHeaderCollapsed} scroll-derived: {@link FloatingHeaderView#moved()} is
 *       untouched. The coordinator adds a {@code onScrolled(int)} entry point for the
 *       {@code updateHeaderScroll} path; FHV's own scroll listener path is unchanged.</li>
 *   <li>#7 {@code getMaxTranslation()} formula: untouched. {@link #setupHeader()} calls
 *       {@code mHost.mHeader.getMaxTranslation()} exactly as the container did.</li>
 *   <li>#8 {@code mUsingTabs} + {@code DRAWER_HIDE_TABS} co-read: this coordinator holds
 *       both; {@link #onDrawerHideTabsChanged(boolean)} is the single mutation site for
 *       {@code mDrawerHideTabs} and triggers both {@link #updateRVContainerRules()} and
 *       {@link #setupHeader()} synchronously so the two reads in setupHeader() agree.</li>
 *   <li>#13 {@code AllAppsRecyclerViewPool} preinflation cancel: {@code setUpCustomRecyclerViewPool}
 *       stays in the container as a static helper; not extracted here.</li>
 * </ul>
 */
class HeaderCoordinator<T extends Context & ActivityContext> {

    private static final boolean DEBUG_HEADER = com.android.launcher3.BuildConfig.DEBUG;
    private static final String TAG = "HeaderCoordinator";

    /** The host container; used for view manipulation and public method delegation. */
    private final ActivityAllAppsContainerView<T> mHost;
    /**
     * Lifecycle reference for invariant #1: setupHeader() checks
     * {@link SearchLifecycle#isSuppressingSetupHeader()} before any layout work.
     */
    private final SearchLifecycle<T> mSearchLifecycle;

    /**
     * Whether the drawer is currently showing personal/work tabs.
     * Moved from container (was {@code protected boolean mUsingTabs}).
     * Written only from {@link ActivityAllAppsContainerView#rebindAdapters(boolean)}.
     */
    boolean mUsingTabs;

    /**
     * Cached value of {@link com.android.launcher3.LauncherPrefs#DRAWER_HIDE_TABS}.
     * Invariant #8: the SINGLE mutation site is {@link #onDrawerHideTabsChanged(boolean)}.
     * Both {@link #setupHeader()} and {@link #updateRVContainerRules()} read this field in
     * the same call stack, so they necessarily see the same value.
     */
    private boolean mDrawerHideTabs;

    /** Scroll listener posted to each RV via {@link ActivityAllAppsContainerView#onInitializeRecyclerView}. */
    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(recyclerView.computeVerticalScrollOffset());
                }
            };

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    HeaderCoordinator(
            ActivityAllAppsContainerView<T> host,
            SearchLifecycle<T> searchLifecycle,
            boolean initialDrawerHideTabs) {
        mHost = host;
        mSearchLifecycle = searchLifecycle;
        mDrawerHideTabs = initialDrawerHideTabs;
    }

    // -------------------------------------------------------------------------
    // Scroll listener accessor
    // -------------------------------------------------------------------------

    /** Returns the scroll listener to register with each RV in {@code onInitializeRecyclerView}. */
    RecyclerView.OnScrollListener getScrollListener() {
        return mScrollListener;
    }

    // -------------------------------------------------------------------------
    // Tab / drawer-hide-tabs state
    // -------------------------------------------------------------------------

    /** Whether the drawer shows personal/work tabs. */
    boolean isUsingTabs() {
        return mUsingTabs;
    }

    /**
     * Updates the using-tabs flag. Called from {@link ActivityAllAppsContainerView#rebindAdapters}
     * immediately after {@link #replaceAppsRVContainer(boolean)}.
     */
    void setUsingTabs(boolean value) {
        mUsingTabs = value;
    }

    /**
     * Handles a DRAWER_HIDE_TABS pref change.
     *
     * <p>Invariant #8: this is the SINGLE mutation site for {@code mDrawerHideTabs}. Callers
     * must not modify the flag directly. Triggers {@link #updateRVContainerRules()} and
     * {@link #setupHeader()} synchronously so the two reads in those methods agree.
     *
     * @param newValue the new pref value.
     */
    void onDrawerHideTabsChanged(boolean newValue) {
        mDrawerHideTabs = newValue;
        updateRVContainerRules();
        setupHeader();
    }

    // -------------------------------------------------------------------------
    // Header layout methods
    // -------------------------------------------------------------------------

    /**
     * Re-lays out the floating header and applies top padding to each adapter holder.
     *
     * <p><b>INVARIANT #1:</b> This method MUST check
     * {@link SearchLifecycle#isSuppressingSetupHeader()} as its very first line. Any new
     * code path that calls this method — now or in the future — must preserve this guard.
     * Suppression is active during search enter/exit animations to prevent 6+ layout passes
     * per animation frame. See docs/architecture/drawer-invariants.md #1.
     */
    void setupHeader() {
        // *** INVARIANT #1 — DO NOT REORDER THIS CHECK ***
        if (mSearchLifecycle.isSuppressingSetupHeader()) return;

        mHost.mHeader.setVisibility(View.VISIBLE);
        boolean tabsHidden = !mUsingTabs || mDrawerHideTabs;
        mHost.mHeader.setup(
                mHost.mAH.get(MAIN).mRecyclerView,
                mHost.mAH.get(WORK).mRecyclerView,
                (SearchRecyclerView) mHost.mAH.get(SEARCH).mRecyclerView,
                mHost.getCurrentPage(),
                tabsHidden);

        int padding = mHost.mHeader.getMaxTranslation();
        mHost.mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.top = padding;
            adapterHolder.applyPadding();
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.scrollToTop();
            }
        });
        removeCustomRules(mHost.mHeader);
        if (mHost.isSearchBarFloating()) {
            alignParentTop(mHost.mHeader, false /* includeTabsMargin */);
        } else {
            layoutBelowSearchContainer(mHost.mHeader, false /* includeTabsMargin */);
        }
    }

    /**
     * Updates the layout rules of the RV container and search RV to match the current tab
     * visibility. Must be called whenever tab visibility might change.
     */
    void updateRVContainerRules() {
        View rvContainer = mHost.getAppsRecyclerViewContainer();
        if (rvContainer == null) return;

        removeCustomRules(rvContainer);
        removeCustomRules(mHost.getSearchRecyclerView());

        boolean tabsVisible = mHost.mViewPager != null && !mDrawerHideTabs;
        if (mHost.isSearchBarFloating()) {
            alignParentTop(rvContainer, tabsVisible);
            alignParentTop(mHost.getSearchRecyclerView(), /* tabs= */ false);
        } else {
            layoutBelowSearchContainer(rvContainer, tabsVisible);
            layoutBelowSearchContainer(mHost.getSearchRecyclerView(), /* tabs= */ false);
        }
    }

    /**
     * Replaces the apps recycler-view container (ViewPager for tabs or single RV) with a fresh
     * inflation, then re-registers the work manager and updates layout rules.
     *
     * <p>Called from {@link ActivityAllAppsContainerView#rebindAdapters(boolean)}.
     * {@link ActivityAllAppsContainerView#setUsingTabs} / {@link #setUsingTabs(boolean)} MUST
     * be called AFTER this method returns (so both the old and new values are available for the
     * remove-old / create-new logic).
     */
    void replaceAppsRVContainer(boolean showTabs) {
        if (DEBUG_HEADER) Log.d(TAG, "replaceAppsRVContainer: showTabs=" + showTabs);
        for (int i = MAIN; i <= WORK; i++) {
            ActivityAllAppsContainerView<T>.AdapterHolder holder = mHost.mAH.get(i);
            if (holder.mRecyclerView != null) {
                holder.mRecyclerView.setLayoutManager(null);
                holder.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = mHost.getAppsRecyclerViewContainer();
        int index = mHost.indexOfChild(oldView);
        mHost.removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        final View rvContainer = mHost.getLayoutInflater().inflate(layout, mHost, false);
        mHost.addView(rvContainer, index);
        if (showTabs) {
            mHost.mViewPager = (AllAppsPagedView) rvContainer;
            // Remove the XML paddingTop so getMaxTranslation() is the sole spacing source.
            mHost.mViewPager.setPadding(
                    mHost.mViewPager.getPaddingLeft(), 0,
                    mHost.mViewPager.getPaddingRight(), mHost.mViewPager.getPaddingBottom());
            mHost.mViewPager.initParentViews(mHost);
            mHost.mViewPager.getPageIndicator().setOnActivePageChangedListener(mHost);
            mHost.mViewPager.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    @Px final int bottomOffsetPx =
                            (int) (mHost.getMeasuredHeight() * PREDICTIVE_BACK_MIN_SCALE);
                    outline.setRect(0, 0, view.getMeasuredWidth(),
                            view.getMeasuredHeight() + bottomOffsetPx);
                }
            });

            mHost.mProfileCoordinator.getWorkManager().reset();
            mHost.post(() -> mHost.mAH.get(WORK).applyPadding());
        } else {
            // Don't detach the work FAB — it stays visible even without tabs.
            mHost.mProfileCoordinator.getWorkManager().reset();
            mHost.mViewPager = null;
        }

        updateRVContainerRules();
        mHost.updateSearchResultsVisibility();
    }

    // -------------------------------------------------------------------------
    // Header scroll
    // -------------------------------------------------------------------------

    /**
     * Updates the header color and search-bar visibility based on the recycler-view scroll offset.
     * Called by the scroll listener whenever the active RV scrolls.
     */
    void updateHeaderScroll(int scrolledOffset) {
        float prog1 = Utilities.boundToRange(
                (float) scrolledOffset / mHost.mHeaderThreshold, 0f, 1f);
        int headerColor = mHost.getHeaderColor(prog1);
        int tabsAlpha = mHost.mHeader.getPeripheralProtectionHeight(/* expectedHeight */ false) == 0
                ? 0
                : (int) (Utilities.boundToRange(
                        (scrolledOffset + mHost.mHeader.mSnappedScrolledY)
                                / mHost.mHeaderThreshold, 0f, 1f) * 255);
        if (mHost.mDrawerColorController.updateHeaderColorState(headerColor, tabsAlpha)) {
            mHost.invalidateHeader();
        }
        if (mHost.mSearchUiManager.getEditText() == null) {
            return;
        }

        float prog = Utilities.boundToRange(
                (float) scrolledOffset / mHost.mHeaderThreshold, 0f, 1f);
        boolean bgVisible = mHost.mSearchUiManager.getBackgroundVisibility();
        if (scrolledOffset == 0 && !mHost.isSearching()) {
            bgVisible = true;
        } else if (scrolledOffset > mHost.mHeaderThreshold) {
            bgVisible = false;
        }
        mHost.mSearchUiManager.setBackgroundVisibility(bgVisible, 1 - prog);
    }

    // -------------------------------------------------------------------------
    // Tab management helpers (moved from container)
    // -------------------------------------------------------------------------

    /**
     * Applies the enterprise-branded tab label strings from default resources.
     * Called from {@link #replaceAppsRVContainer(boolean)} and
     * {@link ActivityAllAppsContainerView#updateWorkUI()}.
     */
    void setDeviceManagementResources() {
        Button personalTab = mHost.findViewById(R.id.tab_personal);
        if (personalTab != null) {
            personalTab.setText(R.string.all_apps_personal_tab);
        }
        Button workTab = mHost.findViewById(R.id.tab_work);
        if (workTab != null) {
            workTab.setText(R.string.all_apps_work_tab);
        }
    }

    /**
     * Applies custom tab colors from the active theme.
     * Called from {@link ActivityAllAppsContainerView#rebindAdapters(boolean)} when tabs are
     * shown, and from {@link ActivityAllAppsContainerView#onDeviceProfileChanged}.
     */
    void applyCustomTabColors() {
        View personalTab = mHost.findViewById(R.id.tab_personal);
        View workTab = mHost.findViewById(R.id.tab_work);
        mHost.mDrawerColorController.applyTabColors(personalTab, workTab);
    }

    // -------------------------------------------------------------------------
    // Layout helpers (moved from container)
    // -------------------------------------------------------------------------

    private void layoutBelowSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_TOP, R.id.search_container_all_apps);
        int topMargin = mHost.getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_header_top_margin);
        if (includeTabsMargin) {
            topMargin += mHost.getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height);
        }
        lp.topMargin = topMargin;
    }

    private void alignParentTop(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.topMargin = includeTabsMargin
                ? mHost.getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_header_pill_height)
                : 0;
    }

    private void removeCustomRules(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
        lp.removeRule(RelativeLayout.ABOVE);
        lp.removeRule(RelativeLayout.BELOW);
        lp.removeRule(RelativeLayout.ALIGN_TOP);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
    }
}
