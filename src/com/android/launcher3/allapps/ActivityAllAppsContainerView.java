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

import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER;
import static com.android.window.flags.Flags.predictiveBackThreeButtonNav;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;


import com.android.app.animation.Interpolators;
import com.android.launcher3.allapps.search.AppsSearchContainerLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PrefSubscriber;
import com.android.launcher3.R;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.AllAppsSearchUiDelegate;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends Context & ActivityContext>
        extends SpringRelativeLayout implements DragSource, Insettable,
        OnDeviceProfileChangeListener, PersonalWorkSlidingTabStrip.OnActivePageChangedListener,
        ScrimView.ScrimDrawingController {


    private static final String TAG = "ActivityAllAppsContainerView";
    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;
    protected static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";
    /** Default search-enter/exit animation duration. Package-private so SearchLifecycle can use it. */
    static final long DEFAULT_SEARCH_TRANSITION_DURATION_MS = 300;
    // Render the header protection at all times to debug clipping issues.
    private static final boolean DEBUG_HEADER_PROTECTION = false;
    /** Context of an activity or window that is inflating this container. */

    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    /**
     * Profile coordinator (Phase 3). Package-private so SearchLifecycle can call
     * getWorkManager().onActivePageChanged(SEARCH) in animateToSearch().
     */
    ProfileCoordinator<T> mProfileCoordinator;
    protected final Point mFastScrollerOffset = new Point();
    protected final int mScrimColor;
    protected final float mHeaderThreshold;
    protected final AllAppsSearchUiDelegate mSearchUiDelegate;

    /** Package-private so HeaderCoordinator can access the recycled view pool. */
    final AllAppsStore<T> mAllAppsStore;
    private final int mHeaderProtectionColor;
    private final int mPrivateSpaceBottomExtraSpace;
    private final Path mTmpPath = new Path();
    private final RectF mTmpRectF = new RectF();
    protected AllAppsPagedView mViewPager;
    protected FloatingHeaderView mHeader;

    protected View mBottomSheetBackground;
    protected RecyclerViewFastScroller mFastScroller;
    private ConstraintLayout mFastScrollLetterLayout;

    /**
     * View that defines the search box. Result is rendered inside {@link #mSearchRecyclerView}.
     */
    protected View mSearchContainer;
    protected SearchUiManager mSearchUiManager;
    /**
     * Header coordinator (Phase 5). Owns mUsingTabs, mDrawerHideTabs, setupHeader(),
     * updateRVContainerRules(), replaceAppsRVContainer(), updateHeaderScroll(),
     * scroll-listener wiring.
     */
    private HeaderCoordinator<T> mHeaderCoordinator;
    protected RecyclerViewFastScroller mTouchHandler;

    /**
     * Search lifecycle — owns SearchState enum, state fields, and animation machinery.
     * Extracted in T3.1 Phase 4 (docs/plans/004-drawer-decomposition-v2.md).
     * Initialized in the constructor after {@link SearchTransitionController} is created.
     */
    /** Package-private so HeaderCoordinator can check isSuppressingSetupHeader(). */
    SearchLifecycle<T> mSearchLifecycle;
    private SearchRecyclerView mSearchRecyclerView;
    protected SearchAdapterProvider<?> mMainAdapterProvider;
    private View mBottomSheetHandleArea;
    private float[] mBottomSheetCornerRadii;
    private ScrimView mScrimView;
    /**
     * Package-private so HeaderCoordinator can access in updateHeaderScroll().
     * DrawerInsetsController also holds a reference via its constructor argument.
     */
    final DrawerColorController mDrawerColorController;
    private AutoCloseable mDrawerPrefSubscription;
    private final PrefSubscriber mDrawerPrefSubscriber = new PrefSubscriber() {
        @Override
        public void onPrefsChanged(java.util.Set<? extends Item> changes) {
            boolean colorOrOpacityChanged = changes.contains(LauncherPrefs.DRAWER_BG_COLOR)
                    || changes.contains(LauncherPrefs.DRAWER_BG_OPACITY);
            boolean hideTabsChanged = changes.contains(LauncherPrefs.DRAWER_HIDE_TABS);
            boolean tabColorChanged = changes.contains(LauncherPrefs.DRAWER_TAB_SELECTED_COLOR)
                    || changes.contains(LauncherPrefs.DRAWER_TAB_UNSELECTED_COLOR);
            if (hideTabsChanged) {
                // Delegate to HeaderCoordinator — single mutation site for mDrawerHideTabs
                // (invariant #8: updateRVContainerRules + setupHeader see the same value).
                boolean newHideTabs = LauncherPrefs.get(getContext())
                        .get(LauncherPrefs.DRAWER_HIDE_TABS);
                mHeaderCoordinator.onDrawerHideTabsChanged(newHideTabs);
            }
            if (colorOrOpacityChanged) {
                mDrawerColorController.refresh();
            }
            if (tabColorChanged) {
                View personalTab = findViewById(R.id.tab_personal);
                View workTab = findViewById(R.id.tab_work);
                mDrawerColorController.applyTabColors(personalTab, workTab);
            }
        }
    };
    /** Package-private so SearchLifecycle can call animateAllAppsToNoScale() on exit. */
    @Nullable AllAppsTransitionController mAllAppsTransitionController;
    private final SearchFabController mSearchFabController;
    private final DrawerInsetsController mInsetsController;

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        mAllAppsStore = new AllAppsStore<>(mActivityContext);

        mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        mHeaderProtectionColor = Themes.getAttrColor(context, R.attr.allappsHeaderProtectionColor);

        mProfileCoordinator = new ProfileCoordinator<>(mActivityContext, this);
        mPrivateSpaceBottomExtraSpace = context.getResources().getDimensionPixelSize(
                R.dimen.ps_extra_bottom_padding);
        mAH = Arrays.asList(null, null, null);
        mDrawerColorController = new DrawerColorController(context, mActivityContext);
        Context materialCtx = new android.view.ContextThemeWrapper(
                context, R.style.HomeSettings_Theme);
        materialCtx = com.google.android.material.color.DynamicColors
                .wrapContextIfAvailable(materialCtx);
        mSearchFabController = new SearchFabController(materialCtx);
        mInsetsController = new DrawerInsetsController(this, mDrawerColorController);

        AllAppsStore.OnUpdateListener onAppsUpdated = this::onAppsUpdated;
        mAllAppsStore.addUpdateListener(onAppsUpdated);

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });
        mSearchUiDelegate = createSearchUiDelegate();
        initContent();

        // SearchLifecycle owns the SearchTransitionController + all search-state fields.
        // Construction order: SearchTransitionController first (it takes 'this'), then
        // SearchLifecycle with both the host and the STC.
        // HeaderCallbacks is an anonymous wrapper so the container methods don't need to
        // be promoted to 'public' just to satisfy interface visibility rules.
        mSearchLifecycle = new SearchLifecycle<>(
                this,
                new SearchLifecycle.HeaderCallbacks() {
                    @Override
                    public void setupHeader() {
                        // Delegates to HeaderCoordinator (Phase 5). mHeaderCoordinator is non-null
                        // at call time (only called after full construction).
                        ActivityAllAppsContainerView.this.setupHeader();
                    }
                    @Override
                    public int getCurrentPage() {
                        return ActivityAllAppsContainerView.this.getCurrentPage();
                    }
                    @Override
                    public void setCurrentPage(int page) {
                        ActivityAllAppsContainerView.this.setCurrentPage(page);
                    }
                    @Override
                    public void updateSearchResultsVisibility() {
                        ActivityAllAppsContainerView.this.updateSearchResultsVisibility();
                    }
                },
                new SearchTransitionController(this));

        // HeaderCoordinator is the LAST collaborator created (per plan: construction order).
        // Reads mSearchLifecycle.isSuppressingSetupHeader() via its setupHeader() method.
        boolean initialDrawerHideTabs = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_HIDE_TABS);
        mHeaderCoordinator = new HeaderCoordinator<>(this, mSearchLifecycle, initialDrawerHideTabs);
    }

    /** Creates the delegate for initializing search. */
    protected AllAppsSearchUiDelegate createSearchUiDelegate() {
        return new AllAppsSearchUiDelegate(this);
    }

    public AllAppsSearchUiDelegate getSearchUiDelegate() {
        return mSearchUiDelegate;
    }

    /**
     * Initializes the view hierarchy and internal variables. Any initialization which actually uses
     * these members should be done in {@link #onFinishInflate()}.
     * In terms of subclass initialization, the following would be parallel order for activity:
     *   initContent -> onPreCreate
     *   constructor/init -> onCreate
     *   onFinishInflate -> onPostCreate
     */
    protected void initContent() {
        mMainAdapterProvider = mSearchUiDelegate.createMainAdapterProvider();

        mAH.set(AdapterHolder.MAIN, new AdapterHolder(AdapterHolder.MAIN,
                new AlphabeticalAppsList<>(mActivityContext,
                        mAllAppsStore,
                        null,
                        mProfileCoordinator.getPrivateProfileManager())));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK,
                new AlphabeticalAppsList<>(mActivityContext, mAllAppsStore,
                        mProfileCoordinator.getWorkManager(), null)));
        mAH.set(SEARCH, new AdapterHolder(SEARCH,
                new AlphabeticalAppsList<>(mActivityContext, null, null, null)));

        // Wire search apps list to the universal search adapter provider
        if (mMainAdapterProvider instanceof
                com.android.launcher3.search.UniversalSearchAdapterProvider universalProvider) {
            universalProvider.setAppsList(mAH.get(SEARCH).mAppsList);
        }

        getLayoutInflater().inflate(R.layout.all_apps_content, this);
        mHeader = findViewById(R.id.all_apps_header);
        // FloatingHeaderView callback is wired in onFinishInflate() after mHeaderCoordinator
        // is constructed (it's the last collaborator created in the constructor).
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background);
        mBottomSheetHandleArea = findViewById(R.id.bottom_sheet_handle_area);
        mSearchRecyclerView = findViewById(R.id.search_results_list_view);
        mFastScroller = findViewById(R.id.fast_scroller);
        mFastScroller.setPopupView(findViewById(R.id.fast_scroller_popup));
        mFastScrollLetterLayout = findViewById(R.id.scroll_letter_layout);
        setClipChildren(false);

        mSearchContainer = inflateSearchBar();
        if (!isSearchBarFloating()) {
            // Add the search box above everything else in this container (if the flag is enabled,
            // it's added to drag layer in onAttach instead).
            addView(mSearchContainer);
            // The search container is visually at the top of the all apps UI, and should thus be
            // focused by default. It's added to end of the children list, so it needs to be
            // explicitly marked as focused by default.
            mSearchContainer.setFocusedByDefault(true);
        }
        mSearchUiManager = (SearchUiManager) mSearchContainer;

        // Add FAB container from SearchFabController to the view hierarchy
        float density = getResources().getDisplayMetrics().density;

        LinearLayout fabContainer = mSearchFabController.buildContainer();
        RelativeLayout.LayoutParams fabLp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        fabLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        fabLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        int fabMargin = (int) (16 * density);
        fabLp.bottomMargin = fabMargin;
        fabLp.setMarginEnd(fabMargin);
        addView(fabContainer, fabLp);

        mSearchFabController.refreshAiIcon();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Wire FloatingHeaderView callback to HeaderCoordinator (Phase 5 decomposition).
        // mHeaderCoordinator is now constructed; mHeader was found by initContent().
        mHeader.setOnHeaderCollapsedChangedCallback(this::invalidateHeader);

        mAH.get(SEARCH).setup(mSearchRecyclerView,
                /* Filter out A-Z apps */ itemInfo -> false);
        mSearchRecyclerView.initSearchAnimator();
        rebindAdapters(true /* force */);
        float cornerRadius = Themes.getDialogCornerRadius(getContext());
        mBottomSheetCornerRadii = new float[]{
                cornerRadius,
                cornerRadius, // Top left radius in px
                cornerRadius,
                cornerRadius, // Top right radius in px
                0,
                0, // Bottom right
                0,
                0 // Bottom left
        };
        mDrawerColorController.refresh();
        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        mSearchUiManager.initializeSearch(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isSearchBarFloating()) {
            // Note: for Taskbar this is removed in TaskbarAllAppsController#cleanUpOverlay when the
            // panel is closed. Can't do so in onDetach because we are also a child of drag layer
            // so can't remove its views during that dispatch.
            mActivityContext.getDragLayer().addView(mSearchContainer);
            mSearchUiDelegate.onInitializeSearchBar();
        }
        mActivityContext.addOnDeviceProfileChangeListener(this);
        mDrawerColorController.onAttach(this, mScrimView);
        mDrawerPrefSubscription = LauncherPrefs.get(getContext()).getPrefChanges()
                .subscribe(mDrawerPrefSubscriber,
                        LauncherPrefs.DRAWER_BG_COLOR,
                        LauncherPrefs.DRAWER_BG_OPACITY,
                        LauncherPrefs.DRAWER_HIDE_TABS,
                        LauncherPrefs.DRAWER_TAB_SELECTED_COLOR,
                        LauncherPrefs.DRAWER_TAB_UNSELECTED_COLOR);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        mDrawerColorController.onDetach();
        if (mDrawerPrefSubscription != null) {
            try {
                mDrawerPrefSubscription.close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to close drawer pref subscription", e);
            }
            mDrawerPrefSubscription = null;
        }
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    /** Invoke when the current search session is finished. */
    public void onClearSearchResult() {
        getMainAdapterProvider().clearHighlightedItem();
        animateToSearchState(false);
        rebindAdapters();
        updateSearchFabs(null);
    }

    /**
     * Shows the A-Z app list while keeping the search bar active (keyboard up, focused).
     * Called when the user backspaces to empty text — the apps list is displayed but
     * search mode is not fully exited (back key or scroll will dismiss).
     * Delegated to {@link SearchLifecycle} (Phase 4 drawer decomposition).
     */
    public void showAppsWhileSearchActive() {
        mSearchLifecycle.showAppsWhileSearchActive();
    }

    /**
     * Shows or hides the "Search online" FAB based on current search state and query text.
     * Called from AppsSearchContainerLayout when search results change.
     */
    public void updateSearchFabs(@Nullable String query) {
        boolean inSearch = mSearchLifecycle.isSearching()
                || mSearchLifecycle.isSearchTransitionRunning();
        mSearchFabController.onQueryChanged(query, inSearch);
    }


    /**
     * Sets results list for search
     */
    public void setSearchResults(ArrayList<AdapterItem> results) {
        getMainAdapterProvider().clearHighlightedItem();
        // End running animations before DiffUtil to prevent overlap with new items
        RecyclerView.ItemAnimator animator = getSearchRecyclerView().getItemAnimator();
        if (animator != null) {
            animator.endAnimations();
        }
        if (getSearchResultList().setSearchResults(results)) {
            getSearchRecyclerView().onSearchResultsChanged();
        }
        if (results != null) {
            animateToSearchState(true);
        }
    }

    /**
     * Sets results list for search.
     *
     * @param searchResultCode indicates if the result is final or intermediate for a given query
     *                         since we can get search results from multiple sources.
     */
    public void setSearchResults(ArrayList<AdapterItem> results, int searchResultCode) {
        // Only animate the final batch — intermediate results appear instantly
        // to prevent animation overlap during progressive delivery.
        RecyclerView.ItemAnimator animator = getSearchRecyclerView().getItemAnimator();
        if (animator instanceof SearchItemAnimator) {
            ((SearchItemAnimator) animator).setAnimationsEnabled(
                    searchResultCode == SearchCallback.FINAL);
        }
        setSearchResults(results);
        mSearchUiDelegate.onSearchResultsChanged(results, searchResultCode);
    }

    private void animateToSearchState(boolean goingToSearch) {
        animateToSearchState(goingToSearch, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    public void setAllAppsTransitionController(
            AllAppsTransitionController allAppsTransitionController) {
        mAllAppsTransitionController = allAppsTransitionController;
    }

    /**
     * Animates the drawer between A-Z apps and search-results mode.
     * Delegated to {@link SearchLifecycle} (Phase 4 drawer decomposition).
     * See {@link SearchLifecycle#animateToSearch(boolean, long)} for full documentation.
     */
    void animateToSearchState(boolean goingToSearch, long durationMs) {
        mSearchLifecycle.animateToSearch(goingToSearch, durationMs);
    }

    public boolean shouldContainerScroll(MotionEvent ev) {
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // IF the MotionEvent is inside the search box or handle area, and the container keeps on
        // receiving touch input, container should move down.
        if (dragLayer.isEventOverView(mSearchContainer, ev)
                || dragLayer.isEventOverView(mBottomSheetHandleArea, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar() != null
                && rv.getScrollbar().getThumbOffsetY() >= 0
                && dragLayer.isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        // Scroll if not within the container view (e.g. over large-screen scrim).
        if (!dragLayer.isEventOverView(getVisibleContainerView(), ev)) {
            return true;
        }
        return rv.shouldContainerScroll(ev, dragLayer);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future. Exits search and returns to
     * A-Z apps list.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     */
    public void reset(boolean animate) {
        reset(animate, true);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     * @param exitSearch Whether to force exit the search state and return to A-Z apps list.
     */
    public void reset(boolean animate, boolean exitSearch) {
        // Scroll Main and Work RV to top. Search RV is done in `resetSearch`.
        for (int i = 0; i < mAH.size(); i++) {
            if (i != SEARCH && mAH.get(i).mRecyclerView != null) {
                mAH.get(i).mRecyclerView.scrollToTop();
            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.endFastScrolling();
        }
        if (mHeader != null && mHeader.getVisibility() == VISIBLE) {
            mHeader.reset(animate);
        }
        updateBackgroundVisibility(mActivityContext.getDeviceProfile());
        // Reset the base recycler view after transitioning home.
        updateHeaderScroll(0);
        if (exitSearch) {
            // Reset the search bar and search RV after transitioning home.
            MAIN_EXECUTOR.getHandler().post(mSearchUiManager::resetSearch);
            // Delegate the search-state reset to SearchLifecycle (change-070 hook).
            // The lifecycle clears mKeepKeyboardOnSearchExit unconditionally before
            // checking isRunning(), so the deferred exit runnable resolves to IDLE
            // even when reset() fires mid-animation (e.g. HOME within 300ms of
            // backspace-to-empty). See docs/changes/079 and SearchLifecycle.onContainerReset().
        }
        mSearchLifecycle.onContainerReset(exitSearch);
        if (mSearchLifecycle.isSearching()) {
            mProfileCoordinator.getWorkManager().reset();
        }
    }

    /**
     * Exits search and returns to A-Z apps list. Scroll to the private space header.
     * Delegated to {@link ProfileCoordinator} (Phase 3 drawer decomposition).
     */
    public void resetAndScrollToPrivateSpaceHeader() {
        mProfileCoordinator.resetAndScrollToPrivateSpaceHeader();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    public String getDescription() {
        if (!mHeaderCoordinator.isUsingTabs() && isSearching()) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            StringCache cache = mActivityContext.getStringCache();
            if (mHeaderCoordinator.isUsingTabs()) {
                if (cache != null) {
                    return isPersonalTab()
                            ? cache.allAppsPersonalTabAccessibility
                            : cache.allAppsWorkTabAccessibility;
                } else {
                    return isPersonalTab()
                            ? getContext().getString(R.string.all_apps_button_personal_label)
                            : getContext().getString(R.string.all_apps_button_work_label);
                }
            }
            return getContext().getString(R.string.all_apps_button_label);
        }
    }

    public boolean isSearching() {
        // Delegated to SearchLifecycle (Phase 4 drawer decomposition).
        // See SearchLifecycle.isSearching() for the SEARCHING/ACTIVE_EMPTY/ENTERING contract.
        return mSearchLifecycle.isSearching();
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mSearchLifecycle.isSearchTransitionRunning()) {
            // Will be called at the end of the animation.
            return;
        }
        if (currentActivePage != SEARCH) {
            mActivityContext.hideKeyboard();
        }
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar(mFastScroller,
                    ALL_APPS_SCROLLER);
        }
        // Header keeps track of active recycler view to properly render header protection.
        mHeader.setActiveRV(currentActivePage);
        reset(true /* animate */, !isSearching() /* exitSearch */);

        mProfileCoordinator.getWorkManager().onActivePageChanged(currentActivePage);
    }

    protected void rebindAdapters() {
        rebindAdapters(false /* force */);
    }

    protected void rebindAdapters(boolean force) {
        Log.d(TAG, "rebindAdapters: force: " + force);
        if (mSearchLifecycle.markRebindPendingIfAnimating()) {
            return;
        }
        updateSearchResultsVisibility();

        boolean showTabs = shouldShowTabs();
        if (showTabs == mHeaderCoordinator.isUsingTabs() && !force) {
            Log.d(TAG, "rebindAdapters: Not needed.");
            return;
        }

        // replaceAppsRVContainer() needs the OLD mHeaderCoordinator.isUsingTabs() value to remove the correct old view.
        // Hence setUsingTabs(showTabs) MUST happen AFTER replaceAppsRVContainer().
        replaceAppsRVContainer(showTabs);
        mHeaderCoordinator.setUsingTabs(showTabs);

        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);

        final AllAppsRecyclerView mainRecyclerView;
        final AllAppsRecyclerView workRecyclerView;
        if (mHeaderCoordinator.isUsingTabs()) {
            mainRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(0);
            workRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(1);
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView,
                    mProfileCoordinator.personalMatcher());
            mAH.get(AdapterHolder.WORK).setup(workRecyclerView,
                    mProfileCoordinator.workMatcher());
            workRecyclerView.setId(R.id.apps_list_view_work);
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: Clicked personal tab.");
                        if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                        }
                    });
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: Clicked work tab.");
                        if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                        }
                    });
            mHeaderCoordinator.setDeviceManagementResources();
            mHeaderCoordinator.applyCustomTabColors();
            if (mHeader.isSetUp()) {
                onActivePageChanged(mViewPager.getNextPage());
            }
        } else {
            mainRecyclerView = findViewById(R.id.apps_list_view);
            workRecyclerView = null;
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView,
                    mProfileCoordinator.personalMatcher());
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
        }
        setUpCustomRecyclerViewPool(
                mainRecyclerView,
                workRecyclerView,
                mAllAppsStore.getRecyclerViewPool());
        setupHeader();

        if (isSearchBarFloating()) {
            // Keep the scroller above the search bar.
            RelativeLayout.LayoutParams scrollerLayoutParams =
                    (LayoutParams) mFastScroller.getLayoutParams();
            scrollerLayoutParams.bottomMargin = mSearchContainer.getHeight()
                    + getResources().getDimensionPixelSize(
                            R.dimen.fastscroll_bottom_margin_floating_search);
        }

        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);
    }

    /**
     * If {@link ENABLE_ALL_APPS_RV_PREINFLATION} is enabled, wire custom
     * {@link RecyclerView.RecycledViewPool} to main and work {@link AllAppsRecyclerView}.
     *
     * Then if {@link ALL_APPS_GONE_VISIBILITY} is enabled, update max pool size. This is because
     * all apps rv's hidden visibility is changed to {@link View#GONE} from {@link View#INVISIBLE),
     * thus we cannot rely on layout pass to update pool size.
     */
    private static void setUpCustomRecyclerViewPool(
            @NonNull AllAppsRecyclerView mainRecyclerView,
            @Nullable AllAppsRecyclerView workRecyclerView,
            @NonNull AllAppsRecyclerViewPool recycledViewPool) {
        final boolean hasWorkProfile = workRecyclerView != null;
        recycledViewPool.setHasWorkProfile(hasWorkProfile);
        mainRecyclerView.setRecycledViewPool(recycledViewPool);
        if (workRecyclerView != null) {
            workRecyclerView.setRecycledViewPool(recycledViewPool);
        }
        mainRecyclerView.updatePoolSize(hasWorkProfile);
    }

    /**
     * Replaces the apps recycler-view container (ViewPager or single RV).
     * Delegated to {@link HeaderCoordinator} (Phase 5 drawer decomposition).
     */
    private void replaceAppsRVContainer(boolean showTabs) {
        mHeaderCoordinator.replaceAppsRVContainer(showTabs);
    }

    /**
     * Updates the layout rules of the RV container and search RV.
     * Delegated to {@link HeaderCoordinator} (Phase 5 drawer decomposition).
     */
    private void updateRVContainerRules() {
        mHeaderCoordinator.updateRVContainerRules();
    }

    /**
     * Re-lays out the floating header and applies top padding to each adapter holder.
     * Delegated to {@link HeaderCoordinator} (Phase 5 drawer decomposition).
     *
     * <p>Invariant #1 is enforced inside the coordinator: its setupHeader() checks
     * {@link SearchLifecycle#isSuppressingSetupHeader()} as its very first line.
     */
    void setupHeader() {
        mHeaderCoordinator.setupHeader();
    }

    /**
     * Force header height update with an offset. Used by {@link UniversalSearchInputView} to
     * request {@link FloatingHeaderView} to update its maxTranslation for multiline search bar.
     */
    public void forceUpdateHeaderHeight(int offset) {
        if (Flags.multilineSearchBar()) {
            mHeader.updateSearchBarOffset(offset);
        }
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> arrayList) {
        super.addChildrenForAccessibility(arrayList);
        if (!Flags.floatingSearchBar()) {
            // Searchbox container is visually at the top of the all apps UI but it's present in
            // end of the children list.
            // We need to move the searchbox to the top in a11y tree for a11y services to read the
            // all apps screen in same as visual order.
            arrayList.stream().filter(v -> v.getId() == R.id.search_container_all_apps)
                    .findFirst().ifPresent(v -> {
                        arrayList.remove(v);
                        arrayList.add(0, v);
                    });
        }
    }

    /**
     * Updates header color and search-bar visibility based on the RV scroll offset.
     * Delegated to {@link HeaderCoordinator} (Phase 5 drawer decomposition).
     */
    protected void updateHeaderScroll(int scrolledOffset) {
        mHeaderCoordinator.updateHeaderScroll(scrolledOffset);
    }

    protected int getHeaderColor(float blendRatio) {
        return mDrawerColorController.getHeaderColor(blendRatio);
    }

    /**
     * @return true if the search bar is floating above this container (at the bottom of the screen)
     */
    protected boolean isSearchBarFloating() {
        return mSearchUiDelegate.isSearchBarFloating();
    }

    /**
     * Whether the <em>floating</em> search bar should appear as a small pill when not focused.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public boolean shouldFloatingSearchBarBePillWhenUnfocused() {
        return false;
    }

    /**
     * How far from the bottom of the screen the <em>floating</em> search bar should rest when the
     * IME is not present.
     * <p>
     * To hide offscreen, use a negative value.
     * <p>
     * Note: if the provided value is non-negative but less than the current bottom insets, the
     * insets will be applied. As such, you can use 0 to default to this.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginBottom() {
        return 0;
    }

    /**
     * How far from the start of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginStart() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    /**
     * How far from the end of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginEnd() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    protected BaseAllAppsAdapter<T> createAdapter(AlphabeticalAppsList<T> appsList) {
        return new AllAppsGridAdapter<>(mActivityContext, getLayoutInflater(), appsList,
                mMainAdapterProvider);
    }

    public boolean isInAllApps() {
        // TODO: Make this abstract
        return true;
    }

    /**
     * Inflates the search bar
     */
    protected View inflateSearchBar() {
        return mSearchUiDelegate.inflateSearchBar();
    }

    /** The adapter provider for the main section. */
    public final SearchAdapterProvider<?> getMainAdapterProvider() {
        return mMainAdapterProvider;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> sparseArray) {
        try {
            // Many slice view id is not properly assigned, and hence throws null
            // pointer exception in the underneath method. Catching the exception
            // simply doesn't restore these slice views. This doesn't have any
            // user visible effect because because we query them again.
            super.dispatchRestoreInstanceState(sparseArray);
        } catch (Exception e) {
            Log.e("AllAppsContainerView", "restoreInstanceState viewId = 0", e);
        }

        Bundle state = (Bundle) sparseArray.get(R.id.work_tab_state_id, null);
        if (state != null) {
            int currentPage = state.getInt(BUNDLE_KEY_CURRENT_PAGE, 0);
            if (currentPage == AdapterHolder.WORK && mViewPager != null) {
                mViewPager.setCurrentPage(currentPage);
                rebindAdapters();
            } else {
                reset(true);
            }
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        Bundle state = new Bundle();
        state.putInt(BUNDLE_KEY_CURRENT_PAGE, getCurrentPage());
        container.put(R.id.work_tab_state_id, state);
    }

    public AllAppsStore<T> getAppsStore() {
        return mAllAppsStore;
    }

    public WorkProfileManager getWorkManager() {
        return mProfileCoordinator.getWorkManager();
    }

    /** Returns whether Private Profile has been setup. */
    public boolean hasPrivateProfile() {
        return mProfileCoordinator.hasPrivateApps();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setAppsPerRow(dp.numShownAllAppsColumns);
            holder.mAppsList.setNumAppsPerRowAllApps(dp.numShownAllAppsColumns);
            if (holder.mRecyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        refreshCustomColors();
        updateBackgroundVisibility(dp);

        // Re-apply search bar color
        if (mSearchContainer instanceof AppsSearchContainerLayout) {
            ((AppsSearchContainerLayout) mSearchContainer).refreshSearchBarColor();
        }

        // Re-apply tab colors if tabs are in use
        if (mHeaderCoordinator.isUsingTabs()) {
            mHeaderCoordinator.applyCustomTabColors();
        }

        // Update RV container margin + header padding for tab visibility changes.
        // updateRVContainerRules() must run before setupHeader() so the VP margin
        // matches the padding computed by getMaxTranslation().
        mHeaderCoordinator.updateRVContainerRules();
        if (mHeader.isSetUp()) {
            setupHeader(); // delegates to mHeaderCoordinator.setupHeader()
        }

        // Refresh scrollbar thumb color
        if (mFastScroller != null) {
            mFastScroller.refreshThumbColor();
        }

        int navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext);
        if (mDrawerColorController.getNavBarScrimPaint().getColor() != navBarScrimColor) {
            mDrawerColorController.getNavBarScrimPaint().setColor(navBarScrimColor);
            invalidate();
        }
    }

    /**
     * Re-reads custom drawer color preferences and updates the background color/opacity.
     * Called from both onFinishInflate() and onDeviceProfileChanged().
     */
    private void refreshCustomColors() {
        mDrawerColorController.refresh();
    }

    protected void updateBackgroundVisibility(DeviceProfile deviceProfile) {
        mBottomSheetBackground.setVisibility(
                deviceProfile.shouldShowAllAppsOnSheet() ? View.VISIBLE : View.GONE);
        // Note: The opaque sheet background and header protection are added in drawOnScrim.
        // For the taskbar entrypoint, the scrim is drawn by its abstract slide in view container,
        // so its header protection is derived from this scrim instead.
    }

    @VisibleForTesting
    public void onAppsUpdated() {
        Log.d(TAG, "onAppsUpdated; number of apps: " + mAllAppsStore.getApps().length);
        // Delegate work/private flag updates and manager resets to ProfileCoordinator.
        mProfileCoordinator.onAppsUpdated(mAllAppsStore.getApps());
        if (!isSearching()) {
            rebindAdapters();
        }

        mActivityContext.getStatsLogManager().logger()
                .withCardinality(mAllAppsStore.getApps().length)
                .log(LAUNCHER_ALLAPPS_COUNT);

        // Pre-cache drawer icons on background thread so the first scroll is jank-free.
        final Context ctx = getContext().getApplicationContext();
        final com.android.launcher3.model.data.AppInfo[] apps = mAllAppsStore.getApps();
        com.android.launcher3.util.Executors.MODEL_EXECUTOR.execute(() -> {
            android.content.ComponentName[] components =
                    new android.content.ComponentName[apps.length];
            for (int i = 0; i < apps.length; i++) {
                components[i] = apps[i].getTargetComponent();
            }
            com.android.launcher3.icons.DrawerIconResolver.getInstance()
                    .preCacheIcons(ctx, components);
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!isInAllApps()) {
            mTouchHandler = null;
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;

            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        if (isSearching()
                && mActivityContext.getDragLayer().isEventOverView(getVisibleContainerView(), ev)) {
            // if in search state, consume touch event.
            return true;
        }
        return false;
    }

    /** The current active recycler view (A-Z list from one of the profiles, or search results). */
    public AllAppsRecyclerView getActiveRecyclerView() {
        if (isSearching()) {
            return getSearchRecyclerView();
        }
        return getActiveAppsRecyclerView();
    }

    /** The current focus change listener in the search container. */
    public OnFocusChangeListener getSearchFocusChangeListener() {
        return mAH.get(AdapterHolder.SEARCH).mOnFocusChangeListener;
    }

    /**
     * The current apps recycler view in the container (main or work, depending on active tab).
     * Package-private so ProfileCoordinator can pass it to PrivateProfileManager's scroll method.
     */
    AllAppsRecyclerView getActiveAppsRecyclerView() {
        if (!mHeaderCoordinator.isUsingTabs() || isPersonalTab()) {
            return mAH.get(AdapterHolder.MAIN).mRecyclerView;
        } else {
            return mAH.get(AdapterHolder.WORK).mRecyclerView;
        }
    }

    /**
     * The container for A-Z apps (the ViewPager for main+work tabs, or main RV). This is currently
     * hidden while searching.
     */
    public ViewGroup getAppsRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    /** The RV for search results, which is hidden while A-Z apps are visible. */
    public SearchRecyclerView getSearchRecyclerView() {
        return mSearchRecyclerView;
    }

    protected boolean isPersonalTab() {
        return mViewPager == null || mViewPager.getNextPage() == 0;
    }

    /**
     * Switches the current page to the provided {@code tab} if tabs are supported, otherwise does
     * nothing.
     */
    public void switchToTab(int tab) {
        if (mHeaderCoordinator.isUsingTabs()) {
            mViewPager.setCurrentPage(tab);
        }
    }

    public LayoutInflater getLayoutInflater() {
        return mSearchUiDelegate.getLayoutInflater();
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {}

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        mInsetsController.applyInsets(insets, grid);

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        // Ignore left/right insets on bottom sheet because we are already centered in-screen.
        if (grid.shouldShowAllAppsOnSheet()) {
            mlp.leftMargin = mlp.rightMargin = 0;
        } else {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
        }
        setLayoutParams(mlp);

        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            int topPadding = grid.allAppsPadding.top;
            if (isSearchBarFloating() && !grid.shouldShowAllAppsOnSheet()) {
                topPadding += getResources().getDimensionPixelSize(
                        R.dimen.all_apps_additional_top_padding_floating_search);
            }
            setPadding(grid.allAppsLeftRightMargin, topPadding, grid.allAppsLeftRightMargin, 0);
        }
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    /**
     * Returns a padding in case a scrim is shown on the bottom of the view and a padding is needed.
     */
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        return 0;
    }

    /**
     * Returns the current height of nav bar scrim
     */
    public int getNavBarScrimHeight() {
        return mInsetsController.getNavBarScrimHeight();
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mInsetsController.onDispatchApplyWindowInsets(insets);

        // Position the FAB container above the IME or nav bar
        int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
        int navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        mSearchFabController.applyImeInsets(imeBottom, navBottom);

        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        mInsetsController.drawNavBarScrim(canvas, getScaleX(), getScaleY(), getWidth(), getHeight());
    }

    protected void updateSearchResultsVisibility() {
        if (isSearching()) {
            getSearchRecyclerView().setVisibility(VISIBLE);
            getAppsRecyclerViewContainer().setVisibility(GONE);
            mHeader.setVisibility(GONE);
        } else {
            getSearchRecyclerView().setVisibility(GONE);
            getAppsRecyclerViewContainer().setVisibility(VISIBLE);
            mHeader.setVisibility(VISIBLE);
        }
        if (mHeader.isSetUp()) {
            mHeader.setActiveRV(getCurrentPage());
        }
    }

    /**
     * Returns true if the container has work apps. Delegates to {@link ProfileCoordinator}.
     */
    public boolean shouldShowTabs() {
        return mProfileCoordinator.shouldShowTabs();
    }

    // Used by tests only
    private boolean isDescendantViewVisible(int viewId) {
        final View view = findViewById(viewId);
        if (view == null) return false;

        if (!view.isShown()) return false;

        return view.getGlobalVisibleRect(new Rect());
    }

    /** Called in Launcher#bindStringCache() to update the UI when cache is updated. */
    public void updateWorkUI() {
        mHeaderCoordinator.setDeviceManagementResources();
        mProfileCoordinator.inflateWorkCardsIfNeeded();
    }

    @VisibleForTesting
    public void setWorkManager(WorkProfileManager workManager) {
        mProfileCoordinator.setWorkManager(workManager);
    }

    @VisibleForTesting
    public boolean isPersonalTabVisible() {
        return isDescendantViewVisible(R.id.tab_personal);
    }

    @VisibleForTesting
    public boolean isWorkTabVisible() {
        return isDescendantViewVisible(R.id.tab_work);
    }

    public AlphabeticalAppsList<T> getSearchResultList() {
        return mAH.get(SEARCH).mAppsList;
    }

    public AlphabeticalAppsList<T> getPersonalAppList() {
        return mAH.get(MAIN).mAppsList;
    }

    public AlphabeticalAppsList<T> getWorkAppList() {
        return mAH.get(WORK).mAppsList;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    @VisibleForTesting
    public View getContentView() {
        return isSearching() ? getSearchRecyclerView() : getAppsRecyclerViewContainer();
    }

    /** The current page visible in all apps. */
    public int getCurrentPage() {
        return isSearching()
                ? SEARCH
                : mViewPager == null ? AdapterHolder.MAIN : mViewPager.getNextPage();
    }

    /**
     * Called from {@link SearchLifecycle.HeaderCallbacks#setCurrentPage(int)} to restore
     * the previous page after a search-exit animation completes.
     */
    void setCurrentPage(int page) {
        if (mViewPager != null) {
            mViewPager.setCurrentPage(page);
        }
    }

    public PrivateProfileManager getPrivateProfileManager() {
        return mProfileCoordinator.getPrivateProfileManager();
    }

    /**
     * Adds an update listener to animator that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(ValueAnimator animator,
            float velocity /* release velocity */,
            float progress /* portion of the distance to travel*/) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                float distance = (1 - progress) * getHeight(); // px
                float settleVelocity = Math.min(0, distance
                        / (AllAppsTransitionController.INTERP_COEFF * animator.getDuration())
                        + velocity);
                absorbSwipeUpVelocity(Math.max(1000, Math.abs(
                        Math.round(settleVelocity * FLING_VELOCITY_MULTIPLIER))));
            }
        });
    }

    /** Invoked when the container is pulled. */
    public void onPull(float deltaDistance, float displacement) {
        absorbPullDeltaDistance(PULL_MULTIPLIER * deltaDistance, PULL_MULTIPLIER * displacement);
        // Current motion spec is to actually push and not pull
        // on this surface. However, until EdgeEffect.onPush (b/190612804) is
        // implemented at view level, we will simply pull
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        invalidateHeader();
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        int navBarScrimHeight = mInsetsController.getNavBarScrimHeight();
        if (predictiveBackThreeButtonNav() && navBarScrimHeight > 0) {
            // Call invalidate to prevent navbar scrim from scaling. The navbar scrim is drawn
            // directly onto the canvas. To prevent it from being scaled with the canvas, there's a
            // counter scale applied in dispatchDraw.
            invalidate(20, getHeight() - navBarScrimHeight, getWidth(), getHeight());
        }
    }

    /**
     * Set {@link Animator.AnimatorListener} on {@link mAllAppsTransitionController} to observe
     * animation of backing out of all apps search view to all apps view.
     */
    public void setAllAppsSearchBackAnimatorListener(Animator.AnimatorListener listener) {
        Preconditions.assertNotNull(mAllAppsTransitionController);
        if (mAllAppsTransitionController == null) {
            return;
        }
        mAllAppsTransitionController.setAllAppsSearchBackAnimationListener(listener);
    }

    public void setScrimView(ScrimView scrimView) {
        mScrimView = scrimView;
        mDrawerColorController.onScrimViewChanged(scrimView);
    }

    @Override
    public void drawOnScrimWithScaleAndBottomOffset(
            Canvas canvas, float scale, @Px int bottomOffsetPx) {
        final View panel = mBottomSheetBackground;
        final boolean hasBottomSheet = panel.getVisibility() == VISIBLE;
        final float translationY = ((View) panel.getParent()).getTranslationY();

        final float horizontalScaleOffset = (1 - scale) * panel.getWidth() / 2;
        final float verticalScaleOffset = (1 - scale) * (panel.getHeight() - getHeight() / 2);

        final float topNoScale = panel.getTop() + translationY;
        final float topWithScale = topNoScale + verticalScaleOffset;
        final float leftWithScale = panel.getLeft() + horizontalScaleOffset;
        final float rightWithScale = panel.getRight() - horizontalScaleOffset;
        final float bottomWithOffset = panel.getBottom() + bottomOffsetPx;
        final Paint headerPaint = mDrawerColorController.getHeaderPaint();
        final int bottomSheetBgColor = mDrawerColorController.getBottomSheetBackgroundColor();
        final float bottomSheetBgAlpha = mDrawerColorController.getBottomSheetBackgroundAlpha();
        final int cachedHeaderColor = mDrawerColorController.getHeaderColor();
        final int tabsProtectionAlpha = mDrawerColorController.getTabsProtectionAlpha();

        // Draw full background panel for tablets.
        if (hasBottomSheet) {
            headerPaint.setColor(bottomSheetBgColor);
            headerPaint.setAlpha((int) (bottomSheetBgAlpha * 255));

            mTmpRectF.set(
                    leftWithScale,
                    topWithScale,
                    rightWithScale,
                    bottomWithOffset);
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, headerPaint);
        }

        // On phones, the ScrimView background already covers the header area uniformly.
        // Skip drawing a separate header protection rect to avoid color mismatches
        // (e.g., with custom drawer colors) and flash during the close animation.
        if (!hasBottomSheet) {
            return;
        }

        if (DEBUG_HEADER_PROTECTION) {
            headerPaint.setColor(Color.MAGENTA);
            headerPaint.setAlpha(255);
        } else {
            headerPaint.setColor(cachedHeaderColor);
            headerPaint.setAlpha((int) (getAlpha() * Color.alpha(cachedHeaderColor)));
        }
        if (headerPaint.getColor() == mDrawerColorController.getScrimColor()
                || headerPaint.getColor() == 0) {
            return;
        }

        headerPaint.setAlpha((int) (headerPaint.getAlpha() * bottomSheetBgAlpha));

        // Draw header on background panel
        final float headerBottomNoScale =
                getHeaderBottom() + getVisibleContainerView().getPaddingTop();
        final float headerHeightNoScale = headerBottomNoScale - topNoScale;
        final float headerBottomWithScaleOnTablet = topWithScale + headerHeightNoScale * scale;
        final FloatingHeaderView headerView = getFloatingHeaderView();
        // Start adding header protection if search bar or tabs will attach to the top.
        if (!isSearchBarFloating() || mHeaderCoordinator.isUsingTabs()) {
            mTmpRectF.set(
                    leftWithScale,
                    topWithScale,
                    rightWithScale,
                    headerBottomWithScaleOnTablet);
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, headerPaint);
        }

        // If tab exist (such as work profile), extend header with tab height
        final int tabsHeight = headerView.getPeripheralProtectionHeight(/* expectedHeight */ false);
        if (tabsProtectionAlpha > 0 && tabsHeight != 0) {
            if (DEBUG_HEADER_PROTECTION) {
                headerPaint.setColor(Color.BLUE);
                headerPaint.setAlpha(255);
            } else {
                float tabAlpha = getAlpha() * tabsProtectionAlpha * bottomSheetBgAlpha;
                headerPaint.setAlpha((int) tabAlpha);
            }
            float left = mBottomSheetBackground.getLeft() + horizontalScaleOffset;
            float right = mBottomSheetBackground.getRight() - horizontalScaleOffset;

            final float tabBottomWithScale = headerBottomWithScaleOnTablet + tabsHeight * scale;

            canvas.drawRect(
                    left,
                    headerBottomWithScaleOnTablet,
                    right,
                    tabBottomWithScale,
                    headerPaint);
        }
    }

    /**
     * The height of the header protection as if the user scrolled down the app list.
     */
    float getHeaderProtectionHeight() {
        float headerBottom = getHeaderBottom() - getTranslationY();
        if (mHeaderCoordinator.isUsingTabs()) {
            return headerBottom + mHeader.getPeripheralProtectionHeight(/* expectedHeight */ true);
        } else {
            return headerBottom;
        }
    }

    ConstraintLayout getFastScrollerLetterList() {
        return mFastScrollLetterLayout;
    }

    /**
     * redraws header protection
     */
    public void invalidateHeader() {
        if (mScrimView != null) {
            mScrimView.invalidate();
        }
    }

    /** Returns the position of the bottom edge of the header */
    public int getHeaderBottom() {
        int bottom = (int) getTranslationY() + mHeader.getClipTop();
        if (isSearchBarFloating()) {
            if (mActivityContext.getDeviceProfile().shouldShowAllAppsOnSheet()) {
                return bottom + mBottomSheetBackground.getTop();
            }
            return bottom;
        }
        return bottom + mHeader.getTop();
    }

    boolean isUsingTabs() {
        return mHeaderCoordinator.isUsingTabs();
    }

    /**
     * Returns a view that denotes the visible part of all apps container view.
     */
    public View getVisibleContainerView() {
        return mBottomSheetBackground.getVisibility() == VISIBLE ? mBottomSheetBackground : this;
    }

    protected void onInitializeRecyclerView(RecyclerView rv) {
        // Scroll listener owned by HeaderCoordinator (Phase 5 decomposition).
        rv.addOnScrollListener(mHeaderCoordinator.getScrollListener());
        mSearchUiDelegate.onInitializeRecyclerView(rv);
    }

    /** Returns the instance of @{code SearchTransitionController}. */
    public SearchTransitionController getSearchTransitionController() {
        return mSearchLifecycle.getSearchTransitionController();
    }

    /** Holds a {@link BaseAllAppsAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;
        public static final int SEARCH = 2;

        private final int mType;
        public final BaseAllAppsAdapter<T> mAdapter;
        final RecyclerView.LayoutManager mLayoutManager;
        final AlphabeticalAppsList<T> mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;
        private OnFocusChangeListener mOnFocusChangeListener;

        AdapterHolder(int type, AlphabeticalAppsList<T> appsList) {
            mType = type;
            mAppsList = appsList;
            mAdapter = createAdapter(mAppsList);
            mAppsList.setAdapter(mAdapter);
            mLayoutManager = mAdapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable Predicate<ItemInfo> matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.bindFastScrollbar(mFastScroller, ALL_APPS_SCROLLER);
            mRecyclerView.setEdgeEffectFactory(createSpringBounceEdgeEffectFactory(mRecyclerView));
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setHasFixedSize(true);
            // Cache all rows so scroll direction reversals never trigger rebinding.
            // Memory cost is ~5KB per item (bitmaps are shared from IconCache).
            mRecyclerView.setItemViewCacheSize(mAdapter.getItemCount());
            // No animations will occur when changes occur to the items in this RecyclerView.
            mRecyclerView.setItemAnimator(null);
            onInitializeRecyclerView(mRecyclerView);
            // Use ViewGroupFocusHelper for SearchRecyclerView to draw focus outline for the
            // buttons in the view (e.g. query builder button and setting button)
            FocusedItemDecorator focusedItemDecorator = isSearch() ? new FocusedItemDecorator(
                    new ViewGroupFocusHelper(mRecyclerView)) : new FocusedItemDecorator(
                    mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            mOnFocusChangeListener = focusedItemDecorator.getFocusListener();
            mAdapter.setIconFocusListener(mOnFocusChangeListener);
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                WorkProfileManager workMgr = mProfileCoordinator.getWorkManager();
                if ((isWork() || !mHeaderCoordinator.isUsingTabs()) && workMgr.getWorkUtilityView() != null) {
                    bottomOffset = mInsetsController.getInsets().bottom
                            + workMgr.getWorkUtilityView().getHeight();
                } else if (isMain()) {
                    Optional<AdapterItem> privateSpaceHeaderItem = mAppsList.getAdapterItems()
                            .stream()
                            .filter(item -> item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER)
                            .findFirst();
                    if (privateSpaceHeaderItem.isPresent()) {
                        bottomOffset = mPrivateSpaceBottomExtraSpace;
                    }
                }
                if (isSearchBarFloating()) {
                    bottomOffset += mSearchContainer.getHeight();
                }
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private boolean isWork() {
            return mType == WORK;
        }

        private boolean isSearch() {
            return mType == SEARCH;
        }

        private boolean isMain() {
            return mType == MAIN;
        }
    }
}
