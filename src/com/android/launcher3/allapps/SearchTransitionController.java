/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.View.VISIBLE;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILTER_BAR;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;

import android.animation.TimeInterpolator;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.android.launcher3.R;

import java.util.List;

/** Coordinates the transition between Search and A-Z in All Apps. */
public class SearchTransitionController extends RecyclerViewAnimationController {

    // Interpolator when the user taps the QSB while already in All Apps.
    private static final Interpolator INTERPOLATOR_WITHIN_ALL_APPS = EMPHASIZED;
    // Interpolator when the user taps the QSB from home screen, so transition to all apps is
    // happening simultaneously.
    private static final Interpolator INTERPOLATOR_TRANSITIONING_TO_ALL_APPS = INSTANT;

    /** Slide distance for content items (16dp, matching {@link SearchItemAnimator}). */
    private static final float SLIDE_OFFSET_DP = 16f;

    private boolean mSkipNextAnimationWithinAllApps;
    private final int mTabsMarginTop;
    private final float mSlideOffsetPx;

    public SearchTransitionController(ActivityAllAppsContainerView<?> allAppsContainerView) {
        super(allAppsContainerView);
        mTabsMarginTop = allAppsContainerView.getResources()
                .getDimensionPixelOffset(R.dimen.all_apps_tabs_margin_top);
        mSlideOffsetPx = SLIDE_OFFSET_DP
                * allAppsContainerView.getResources().getDisplayMetrics().density;
    }

    /**
     * Starts the transition to or from search state. If a transition is already in progress, the
     * animation will start from that point with the new duration, and the previous onEndRunnable
     * will not be called.
     *
     * @param goingToSearch true if will be showing search results, otherwise will be showing a-z
     * @param duration time in ms for the animation to run
     * @param onEndRunnable will be called when the animation finishes, unless another animation is
     *                      scheduled in the meantime
     */
    @Override
    protected void animateToState(boolean goingToSearch, long duration, Runnable onEndRunnable) {
        super.animateToState(goingToSearch, duration, onEndRunnable);

        FloatingHeaderView headerView = mAllAppsContainerView.getFloatingHeaderView();
        View appsContainer = mAllAppsContainerView.getAppsRecyclerViewContainer();

        headerView.setFloatingRowsCollapsed(true);
        headerView.setVisibility(VISIBLE);
        headerView.maybeSetTabVisibility(VISIBLE);
        appsContainer.setVisibility(VISIBLE);
        getRecyclerView().setVisibility(VISIBLE);

        // GPU-accelerated transforms for views animated with translationY + alpha.
        // Set AFTER super so the previous animation's end callback doesn't clear them.
        if (mAnimator != null) {
            headerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            appsContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mAnimator.addListener(forEndCallback(() -> {
                headerView.setLayerType(View.LAYER_TYPE_NONE, null);
                appsContainer.setLayerType(View.LAYER_TYPE_NONE, null);
            }));
        }
    }

    @Override
    protected SearchRecyclerView getRecyclerView() {
        return mAllAppsContainerView.getSearchRecyclerView();
    }

    /**
     * Replaces the base class's scaleY "unsqueeze from top" with a translationY slide-up
     * for content items, matching {@link SearchItemAnimator}'s visual. The filter bar
     * retains its scaleY unsqueeze behavior.
     */
    @Override
    protected int onProgressUpdated(float searchToAzProgress) {
        SearchRecyclerView rv = getRecyclerView();
        List<BaseAllAppsAdapter.AdapterItem> adapterItems =
                rv.getApps().getAdapterItems();

        int numContentItems = 0;
        int totalHeight = 0;

        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child == null) continue;

            int adapterPos = rv.getChildAdapterPosition(child);
            if (adapterPos < 0 || adapterPos >= adapterItems.size()) continue;

            BaseAllAppsAdapter.AdapterItem adapterItem = adapterItems.get(adapterPos);
            boolean isFilterBar =
                    adapterItem.viewType == VIEW_TYPE_SEARCH_FILTER_BAR;

            float contentAlpha;
            float backgroundAlpha;

            if (isFilterBar) {
                // Filter bar: scaleY "unsqueeze from top" (pivotY=0 via onChildAttached)
                float scaleY = 1f - searchToAzProgress;
                child.setScaleY(scaleY);
                child.setTranslationY(0);
                totalHeight += (int) (child.getHeight() * scaleY);
                contentAlpha = scaleY;
                backgroundAlpha = scaleY;
            } else {
                // Content items: slide-up + fade (matching SearchItemAnimator)
                child.setScaleY(1f);
                child.setTranslationY(searchToAzProgress * mSlideOffsetPx);
                totalHeight += (int) (child.getHeight() * (1f - searchToAzProgress));

                // Staggered alpha (same constants as base class)
                float fadeStart = Math.max(0,
                        TOP_CONTENT_FADE_PROGRESS_START
                                - CONTENT_STAGGER * numContentItems);
                float fadeEnd = Math.min(1,
                        fadeStart + CONTENT_FADE_PROGRESS_DURATION);
                contentAlpha = 1f - clampToProgress(
                        searchToAzProgress, fadeStart, fadeEnd);

                float bgFadeStart = Math.max(0,
                        TOP_BACKGROUND_FADE_PROGRESS_START
                                - CONTENT_STAGGER * numContentItems);
                float bgFadeEnd = Math.min(1,
                        bgFadeStart + BACKGROUND_FADE_PROGRESS_DURATION);
                backgroundAlpha = 1f - clampToProgress(
                        searchToAzProgress, bgFadeStart, bgFadeEnd);

                numContentItems++;
            }

            // Apply alpha — handle ViewGroup-with-background case (same as base class)
            Drawable bg = child.getBackground();
            if (bg != null && child instanceof ViewGroup vg) {
                child.setAlpha(1f);
                for (int j = 0; j < vg.getChildCount(); j++) {
                    vg.getChildAt(j).setAlpha(contentAlpha);
                }
                bg.setAlpha((int) (255 * backgroundAlpha));
            } else {
                child.setAlpha(contentAlpha);
                adapterItem.setDecorationFillAlpha(
                        (int) (255 * backgroundAlpha));
                if (bg != null) {
                    bg.setAlpha((int) (255 * backgroundAlpha));
                }
            }
        }

        // Header and apps container positioning (unchanged)
        FloatingHeaderView headerView = mAllAppsContainerView.getFloatingHeaderView();
        int appsTranslationY = totalHeight + headerView.getFloatingRowsHeight();

        if (headerView.usingTabs()) {
            headerView.setTranslationY(totalHeight);
            headerView.setAlpha(clampToProgress(searchToAzProgress, 0.8f, 1f));

            float searchProgress = 1f - searchToAzProgress;
            appsTranslationY += (int) (searchProgress * (
                    headerView.getTabsAdditionalPaddingBottom()
                            + mTabsMarginTop
                            - headerView.getPaddingTop()));
        }

        View appsContainer = mAllAppsContainerView.getAppsRecyclerViewContainer();
        appsContainer.setTranslationY(appsTranslationY);
        appsContainer.setAlpha(clampToProgress(searchToAzProgress, 0.8f, 1f));

        return totalHeight;
    }

    @Override
    protected TimeInterpolator getInterpolator() {
        TimeInterpolator timeInterpolator =
                mAllAppsContainerView.isInAllApps()
                        ? INTERPOLATOR_WITHIN_ALL_APPS : INTERPOLATOR_TRANSITIONING_TO_ALL_APPS;
        if (mSkipNextAnimationWithinAllApps) {
            timeInterpolator = INSTANT;
            mSkipNextAnimationWithinAllApps = false;
        }
        return timeInterpolator;
    }

    /**
     * This should only be called from {@code LauncherSearchSessionManager} when app restarts due to
     * theme changes.
     */
    public void setSkipAnimationWithinAllApps(boolean skip) {
        mSkipNextAnimationWithinAllApps = skip;
    }
}
