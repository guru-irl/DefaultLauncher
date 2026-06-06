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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.WindowInsets;

import com.android.launcher3.DeviceProfile;

/**
 * Owns inset state for the all-apps drawer: {@code mInsets},
 * {@code mNavBarScrimHeight}, side/bottom padding propagation, and
 * the nav-bar scrim draw call.
 *
 * <p>Construction order (inside ActivityAllAppsContainerView): colors → fabs → insets.
 * The {@code host} reference is safe to hold because the controller's lifetime is
 * strictly bounded by the host view's lifetime.
 *
 * <p>T3.1 Phase 2 — docs/plans/004-drawer-decomposition-v2.md
 */
class DrawerInsetsController {

    private final ActivityAllAppsContainerView<?> mHost;
    private final DrawerColorController mColors;
    private final Rect mInsets = new Rect();
    private int mNavBarScrimHeight = 0;

    DrawerInsetsController(ActivityAllAppsContainerView<?> host, DrawerColorController colors) {
        mHost = host;
        mColors = colors;
    }

    /** Called from {@code setInsets()} to record insets and refresh adapter paddings. */
    void applyInsets(Rect insets, DeviceProfile dp) {
        mInsets.set(insets);
        applyAdapterSideAndBottomPaddings(dp);
    }

    /**
     * Called from {@code dispatchApplyWindowInsets()} to refresh the nav-bar scrim height
     * and re-apply adapter paddings. The FAB positioning is handled separately by the
     * container which delegates to {@code SearchFabController}.
     */
    void onDispatchApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = mHost.computeNavBarScrimHeight(insets);
        applyAdapterSideAndBottomPaddings(mHost.mActivityContext.getDeviceProfile());
    }

    /**
     * Draws the nav-bar scrim rect with counter-scale correction for predictive back.
     * Called from {@code dispatchDraw()} when the nav bar scrim height is non-zero.
     */
    void drawNavBarScrim(Canvas canvas, float scaleX, float scaleY, int width, int height) {
        if (mNavBarScrimHeight <= 0) return;
        float left = (width - width / scaleX) / 2;
        float top = height / 2f + (height / 2f - mNavBarScrimHeight) / scaleY;
        canvas.drawRect(left, top, width / scaleX,
                top + mNavBarScrimHeight / scaleY, mColors.getNavBarScrimPaint());
    }

    int getNavBarScrimHeight() {
        return mNavBarScrimHeight;
    }

    /** Returns the inset rect — callers must not mutate it. */
    Rect getInsets() {
        return mInsets;
    }

    private void applyAdapterSideAndBottomPaddings(DeviceProfile grid) {
        int bottomPadding = Math.max(mInsets.bottom, mNavBarScrimHeight);
        mHost.mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.bottom = bottomPadding;
            adapterHolder.mPadding.left = grid.allAppsPadding.left;
            adapterHolder.mPadding.right = grid.allAppsPadding.right;
            adapterHolder.applyPadding();
        });
    }
}
