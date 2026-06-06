/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DefaultLauncher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DefaultLauncher. If not, see <https://www.gnu.org/licenses/>.
 */
package com.android.launcher3.allapps;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Owns the search FAB container (AI + web-search FABs) and its show/hide
 * logic based on the current search query.
 *
 * Extracted from {@link ActivityAllAppsContainerView} by T3.1 Phase 1
 * (docs/plans/004-drawer-decomposition-v2.md).
 *
 * <p>Usage: call {@link #buildContainer()} in the container's
 * {@code initContent} to get the LinearLayout to add to the view
 * hierarchy. Then call {@link #onQueryChanged(String, boolean)} on every
 * search query update. Call {@link #applyImeInsets(int, int)} from the
 * container's {@code dispatchApplyWindowInsets}.
 */
public class SearchFabController {

    private final Context mContext;
    private final LinearLayout mFabContainer;
    private final FloatingActionButton mAiSearchFab;
    private final ExtendedFloatingActionButton mSearchOnlineFab;

    @Nullable private String mCurrentQuery;

    public SearchFabController(Context materialCtx) {
        mContext = materialCtx;

        float density = materialCtx.getResources().getDisplayMetrics().density;
        int fabSpacing = (int) (16 * density);

        mFabContainer = new LinearLayout(materialCtx);
        mFabContainer.setOrientation(LinearLayout.VERTICAL);
        mFabContainer.setGravity(Gravity.END | Gravity.BOTTOM);
        mFabContainer.setVisibility(GONE);

        // AI search medium FAB
        mAiSearchFab = new FloatingActionButton(materialCtx);
        mAiSearchFab.setSize(FloatingActionButton.SIZE_NORMAL);
        mAiSearchFab.setImageResource(R.drawable.ic_ai_search);
        int tertiaryContainer = MaterialColors.getColor(
                mAiSearchFab, com.google.android.material.R.attr.colorTertiaryContainer);
        int onTertiaryContainer = MaterialColors.getColor(
                mAiSearchFab, com.google.android.material.R.attr.colorOnTertiaryContainer);
        mAiSearchFab.setBackgroundTintList(ColorStateList.valueOf(tertiaryContainer));
        mAiSearchFab.setImageTintList(ColorStateList.valueOf(onTertiaryContainer));
        mAiSearchFab.setOnClickListener(v -> launchAiSearch());
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        aiLp.bottomMargin = fabSpacing;
        mFabContainer.addView(mAiSearchFab, aiLp);

        // Web search Extended FAB
        mSearchOnlineFab = new ExtendedFloatingActionButton(materialCtx);
        mSearchOnlineFab.setText(R.string.search_online);
        mSearchOnlineFab.setIconResource(R.drawable.ic_web_search);
        mSearchOnlineFab.setOnClickListener(v -> launchWebSearch());
        mFabContainer.addView(mSearchOnlineFab,
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * Returns the FAB container view to be added to the host container's view
     * hierarchy with a {@link RelativeLayout.LayoutParams} pinned to the bottom-end.
     */
    public LinearLayout buildContainer() {
        return mFabContainer;
    }

    /**
     * Refreshes AI FAB visibility based on whether an AI app is installed.
     * Call once at attach time and whenever FABs are about to be shown.
     */
    public void refreshAiIcon() {
        String pkg = resolveAiPackage();
        mAiSearchFab.setVisibility(pkg != null ? VISIBLE : GONE);
    }

    /**
     * Shows or hides the FAB container based on the current query and search state.
     * Replaces {@code ActivityAllAppsContainerView.updateSearchFabs(String)}.
     *
     * @param query     current search text (null or empty = no query)
     * @param inSearch  true when the drawer is in or transitioning to search mode
     */
    public void onQueryChanged(@Nullable String query, boolean inSearch) {
        mCurrentQuery = query;
        boolean show = query != null && !query.isEmpty() && inSearch;
        if (show && mFabContainer.getVisibility() != VISIBLE) {
            refreshAiIcon();
            mFabContainer.setVisibility(VISIBLE);
            mFabContainer.setScaleX(0f);
            mFabContainer.setScaleY(0f);
            mFabContainer.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(Interpolators.EMPHASIZED_DECELERATE).start();
        } else if (!show && mFabContainer.getVisibility() == VISIBLE) {
            mFabContainer.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .setInterpolator(Interpolators.EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> mFabContainer.setVisibility(GONE)).start();
        }
    }

    /**
     * Updates the FAB container's bottom margin to stay above the IME/nav bar.
     * Call from the host container's {@code dispatchApplyWindowInsets}.
     */
    public void applyImeInsets(int imeBottom, int navBottom) {
        float density = mContext.getResources().getDisplayMetrics().density;
        int fabMargin = (int) (16 * density);
        RelativeLayout.LayoutParams fabLp =
                (RelativeLayout.LayoutParams) mFabContainer.getLayoutParams();
        if (fabLp != null) {
            fabLp.bottomMargin = Math.max(imeBottom, navBottom) + fabMargin;
            mFabContainer.setLayoutParams(fabLp);
        }
    }

    // -----------------------------------------------------------
    // Intent helpers
    // -----------------------------------------------------------

    private void launchWebSearch() {
        if (mCurrentQuery == null || mCurrentQuery.isEmpty()) return;
        String webApp = LauncherPrefs.get(mContext).get(LauncherPrefs.SEARCH_WEB_APP);
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, mCurrentQuery);
        if (!"default".equals(webApp)) {
            try {
                ComponentName cn = ComponentName.unflattenFromString(webApp);
                if (cn != null) intent.setComponent(cn);
            } catch (Exception ignored) {
                // Fall through to system default
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void launchAiSearch() {
        if (mCurrentQuery == null || mCurrentQuery.isEmpty()) return;
        String pkg = resolveAiPackage();
        if (pkg == null) return;

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, mCurrentQuery);
        sendIntent.setPackage(pkg);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            PackageManager pm = mContext.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(launchIntent);
            }
        }
    }

    @Nullable
    private String resolveAiPackage() {
        String pref = LauncherPrefs.get(mContext).get(LauncherPrefs.SEARCH_AI_APP);
        if ("none".equals(pref)) return null;

        if (pref != null && !pref.isEmpty()) {
            if (isPackageInstalled(pref)) return pref;
        }
        for (String pkg : LauncherPrefs.AI_APP_PACKAGES) {
            if (isPackageInstalled(pkg)) return pkg;
        }
        return null;
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            mContext.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
