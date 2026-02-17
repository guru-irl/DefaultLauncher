/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_OFF_WORK_APPS_TAP;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;

import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage the work profile FAB
 * and work tab state.
 */
public class WorkProfileManager extends UserProfileManager
        implements PersonalWorkSlidingTabStrip.OnActivePageChangedListener {
    private static final String TAG = "WorkProfileManager";
    private final ActivityAllAppsContainerView<?> mAllApps;
    private WorkUtilityView mWorkUtilityView;
    private final Predicate<UserHandle> mWorkProfileMatcher;

    public WorkProfileManager(
            UserManager userManager, ActivityAllAppsContainerView allApps,
            StatsLogManager statsLogManager, UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mAllApps = allApps;
        mWorkProfileMatcher = (user) -> userCache.getUserInfo(user).isWork();
    }

    /** Posts quiet mode enable/disable call for work profile user. */
    public void setWorkProfileEnabled(boolean enabled) {
        updateCurrentState(STATE_TRANSITION);
        setQuietMode(!enabled, mAllApps.mActivityContext);
    }

    @Override
    public void onActivePageChanged(int page) {
        updateWorkFabVisibility(page);
    }

    /**
     * Show the FAB on the work tab and in no-tabs mode (where work apps are in the
     * combined list). Hide on the personal tab and during search.
     */
    private void updateWorkFabVisibility(int page) {
        if (mWorkUtilityView == null) return;
        boolean show = false;
        if (page == WORK) {
            show = true;
        } else if (page != SEARCH && !mAllApps.isUsingTabs()) {
            // No-tabs mode — work apps are in the combined list, show FAB.
            show = true;
        }
        if (show && (getCurrentState() == STATE_ENABLED
                || getCurrentState() == STATE_DISABLED)) {
            mWorkUtilityView.animateVisibility(true);
        } else {
            mWorkUtilityView.animateVisibility(false);
        }
    }

    /** Reads work profile state from the model and updates views. */
    public void reset() {
        int quietModeFlag;
        if (Flags.enablePrivateSpace()) {
            quietModeFlag = FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
        } else {
            quietModeFlag = FLAG_QUIET_MODE_ENABLED;
        }
        boolean isEnabled = !mAllApps.getAppsStore().hasModelFlag(quietModeFlag);
        updateCurrentState(isEnabled ? STATE_ENABLED : STATE_DISABLED);
        if (mWorkUtilityView != null) {
            mWorkUtilityView.getImeInsets().setEmpty();
            mWorkUtilityView.updateTranslationY();
        }
    }

    private void updateCurrentState(@UserProfileState int currentState) {
        setCurrentState(currentState);
        if (getAH() != null) {
            getAH().mAppsList.updateAdapterItems();
        }
        if (getCurrentState() == STATE_ENABLED || getCurrentState() == STATE_DISABLED) {
            attachWorkUtilityViews();
        }
        if (mWorkUtilityView != null) {
            mWorkUtilityView.updateForState(currentState);
            updateWorkFabVisibility(mAllApps.getCurrentPage());
        }
    }

    /** Creates and attaches the work FAB to the AllApps container. */
    public boolean attachWorkUtilityViews() {
        if (!mAllApps.getAppsStore().hasModelFlag(
                FLAG_HAS_SHORTCUT_PERMISSION | FLAG_QUIET_MODE_CHANGE_PERMISSION)) {
            Log.e(TAG, "unable to attach work mode switch; Missing required permissions");
            return false;
        }
        if (mWorkUtilityView == null) {
            mWorkUtilityView = (WorkUtilityView) mAllApps.getLayoutInflater().inflate(
                    R.layout.work_mode_utility_view, mAllApps, false);
        }
        if (mWorkUtilityView.getParent() == null) {
            mAllApps.addView(mWorkUtilityView);
        }
        if (getAH() != null) {
            getAH().applyPadding();
        }
        mWorkUtilityView.getWorkFAB().setOnClickListener(this::onWorkFabClicked);
        return true;
    }

    /** Removes the work FAB from the AllApps container. */
    public void detachWorkUtilityViews() {
        if (mWorkUtilityView != null && mWorkUtilityView.getParent() == mAllApps) {
            mAllApps.removeView(mWorkUtilityView);
        }
        mWorkUtilityView = null;
    }

    @Nullable
    public WorkUtilityView getWorkUtilityView() {
        return mWorkUtilityView;
    }

    private ActivityAllAppsContainerView.AdapterHolder getAH() {
        return mAllApps.mAH.get(WORK);
    }

    /**
     * Always true — when paused, icons are shown grayed-out instead of hidden.
     */
    public boolean shouldShowWorkApps() {
        return true;
    }

    public boolean hasWorkApps() {
        return Stream.of(mAllApps.getAppsStore().getApps()).anyMatch(getItemInfoMatcher());
    }

    /**
     * Adds work profile specific adapter items.
     * No disabled card — grayed icons are shown instead when paused.
     */
    public int addWorkItems(ArrayList<AdapterItem> adapterItems) {
        if (getCurrentState() == STATE_ENABLED && !isEduSeen()) {
            adapterItems.add(new AdapterItem(VIEW_TYPE_WORK_EDU_CARD));
        }
        return adapterItems.size();
    }

    private boolean isEduSeen() {
        return LauncherPrefs.get(mAllApps.getContext()).get(WORK_EDU_STEP) != 0;
    }

    /**
     * Single click handler for both pause and enable.
     * STATE_ENABLED → pause (disable), STATE_DISABLED → enable.
     */
    private void onWorkFabClicked(View view) {
        if (mWorkUtilityView == null || !mWorkUtilityView.isEnabled()) return;
        if (getCurrentState() == STATE_ENABLED) {
            Log.d(TAG, "Work FAB clicked: pausing.");
            logEvents(LAUNCHER_TURN_OFF_WORK_APPS_TAP);
            setWorkProfileEnabled(false);
        } else if (getCurrentState() == STATE_DISABLED) {
            Log.d(TAG, "Work FAB clicked: enabling.");
            setWorkProfileEnabled(true);
        }
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mWorkProfileMatcher;
    }
}
