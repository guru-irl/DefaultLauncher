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
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Process;
import android.os.UserManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.views.ActivityContext;

import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Collaborator for {@link ActivityAllAppsContainerView} that owns work-profile and private-profile
 * management: manager instances, has-apps flags, and profile-related scroll/adapter operations.
 *
 * <p>Extracted in T3.1 Phase 3 (docs/plans/004-drawer-decomposition-v2.md, Phase 3).
 *
 * <p>Invariants preserved (docs/architecture/drawer-invariants.md):
 * <ul>
 *   <li>#9: Three orthogonal booleans in PrivateProfileManager remain inside that class; this
 *       coordinator is a passthrough for the manager instance.</li>
 *   <li>#10: {@code mReadyToAnimate} set-before-unlock ordering is entirely intra-PPM; not
 *       touched here.</li>
 *   <li>#11: {@code MAIN_EXECUTOR.post(::exitSearchAndExpand)} guarded by {@code isSearching()}
 *       remains inside PrivateProfileManager.postUnlock(); ProfileCoordinator does not
 *       interpose on that path.</li>
 *   <li>#12: {@code notifyDataSetChanged()} after PS state transition at
 *       PrivateProfileManager.java:704 is preserved verbatim; not touched here.</li>
 * </ul>
 *
 * <p>Risk flag: {@link PrivateProfileManager} already holds a {@code mAllApps} back-reference to
 * the container view (needed for {@code isSearching()} and adapter access). Do NOT create a
 * second back-channel from this class — use only the public/package-private API of the host
 * or pass required data via method parameters.
 */
class ProfileCoordinator<T extends Context & ActivityContext> {

    private static final boolean DEBUG_PROFILE = com.android.launcher3.BuildConfig.DEBUG;
    private static final String TAG = "ProfileCoordinator";

    private final T mActivityContext;
    /** The host container — used for calling back to container methods in resetAndScroll. */
    private final ActivityAllAppsContainerView<T> mHost;

    /** Work-profile manager; mutable so @VisibleForTesting setter can swap it. */
    WorkProfileManager mWorkManager;
    /** Private-profile manager; immutable after construction. */
    final PrivateProfileManager mPrivateProfileManager;
    /**
     * Predicate matching items owned by the personal (primary) user.
     * Moved from ActivityAllAppsContainerView Phase 3.
     */
    final Predicate<ItemInfo> mPersonalMatcher;

    private boolean mHasWorkApps;
    private boolean mHasPrivateApps;

    ProfileCoordinator(T activityContext, ActivityAllAppsContainerView<T> host) {
        mActivityContext = activityContext;
        mHost = host;

        mPersonalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle());

        UserManager userManager = activityContext.getSystemService(UserManager.class);
        UserCache userCache = UserCache.INSTANCE.get(activityContext);

        mWorkManager = new WorkProfileManager(
                userManager,
                host,
                activityContext.getStatsLogManager(),
                userCache);

        mPrivateProfileManager = new PrivateProfileManager(
                userManager,
                host,
                activityContext.getStatsLogManager(),
                userCache);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    WorkProfileManager getWorkManager() {
        return mWorkManager;
    }

    PrivateProfileManager getPrivateProfileManager() {
        return mPrivateProfileManager;
    }

    /** Returns true if any app in the store belongs to a work profile. */
    boolean hasWorkApps() {
        return mHasWorkApps;
    }

    /** Returns true if any app in the store belongs to the private space. */
    boolean hasPrivateApps() {
        return mHasPrivateApps;
    }

    /**
     * Whether the drawer should show personal/work tabs.
     * Invariant: equivalent to hasWorkApps().
     */
    boolean shouldShowTabs() {
        return mHasWorkApps;
    }

    /** Predicate for the personal (primary-user) apps filter passed to AdapterHolder.setup(). */
    Predicate<ItemInfo> personalMatcher() {
        return mPersonalMatcher;
    }

    /** Predicate for the work-profile apps filter passed to AdapterHolder.setup(). */
    Predicate<ItemInfo> workMatcher() {
        return mWorkManager.getItemInfoMatcher();
    }

    // -------------------------------------------------------------------------
    // App-update handling
    // -------------------------------------------------------------------------

    /**
     * Updates the work/private has-apps flags and triggers manager resets.
     *
     * <p>The {@code !isSearching() → rebindAdapters()} decision and the stats/icon-cache work
     * remain in the container's {@code onAppsUpdated()} — this method handles only the
     * work/private state branches.
     *
     * @param apps current snapshot from {@link com.android.launcher3.allapps.AllAppsStore}.
     */
    void onAppsUpdated(AppInfo[] apps) {
        mHasWorkApps = Stream.of(apps).anyMatch(mWorkManager.getItemInfoMatcher());
        mHasPrivateApps = Stream.of(apps).anyMatch(mPrivateProfileManager.getItemInfoMatcher());
        if (DEBUG_PROFILE) {
            Log.d(TAG, "onAppsUpdated: hasWork=" + mHasWorkApps
                    + " hasPrivate=" + mHasPrivateApps);
        }
        if (mHasWorkApps) {
            mWorkManager.reset();
        }
        if (mHasPrivateApps) {
            mPrivateProfileManager.reset();
        }
    }

    // -------------------------------------------------------------------------
    // Scroll / navigation
    // -------------------------------------------------------------------------

    /**
     * Exits search and returns to the A-Z apps list, then scrolls to the private-space header.
     * Moved from ActivityAllAppsContainerView (Phase 3 drawer decomposition).
     *
     * <p>Calls back to {@code host} for search-exit, tab-switch, and scroll operations.
     * {@link PrivateProfileManager}'s own {@code mAllApps} back-reference is the authoritative
     * path for adapter/scroll access inside PPM; we call {@code scrollForHeaderToBeVisibleInContainer}
     * directly on the PPM instance here only to pass the resolved recycler view and item list.
     */
    void resetAndScrollToPrivateSpaceHeader() {
        // Animate to A-Z with 0 duration so the state transitions synchronously before the
        // deferred post() runs. (Using duration > 0 would race with the following scroll.)
        mHost.animateToSearchState(false, 0);

        MAIN_EXECUTOR.getHandler().post(() -> {
            // Reset the search bar after the transition completes.
            mHost.mSearchUiManager.resetSearch();
            // Switch to the main (personal) tab.
            mHost.switchToTab(MAIN);
            // Scroll private-space header into view.
            mPrivateProfileManager.scrollForHeaderToBeVisibleInContainer(
                    mHost.getActiveAppsRecyclerView(),
                    mHost.getPersonalAppList().getAdapterItems(),
                    mPrivateProfileManager.getPsHeaderHeight(),
                    mActivityContext.getDeviceProfile().allAppsCellHeightPx);
        });
    }

    // -------------------------------------------------------------------------
    // Work-card UI updates
    // -------------------------------------------------------------------------

    /**
     * Re-inflates string resources for work edu/paused cards in the work RecyclerView.
     * Called from {@link ActivityAllAppsContainerView#updateWorkUI()} on a string-cache refresh.
     */
    void inflateWorkCardsIfNeeded() {
        AllAppsRecyclerView workRV = mHost.mAH.get(WORK).mRecyclerView;
        if (workRV == null) return;
        for (int i = 0; i < workRV.getChildCount(); i++) {
            View currentView = workRV.getChildAt(i);
            int viewType = workRV.getChildViewHolder(currentView).getItemViewType();
            if (viewType == VIEW_TYPE_WORK_EDU_CARD) {
                ((WorkEduCard) currentView).updateStringFromCache();
            } else if (viewType == VIEW_TYPE_WORK_DISABLED_CARD) {
                ((WorkPausedCard) currentView).updateStringFromCache();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Test hooks
    // -------------------------------------------------------------------------

    /**
     * Replaces the work-profile manager, for use in unit tests that mock profile state.
     * Mirrors the @VisibleForTesting setWorkManager() formerly on the container.
     */
    @VisibleForTesting
    void setWorkManager(WorkProfileManager workManager) {
        mWorkManager = workManager;
    }
}
