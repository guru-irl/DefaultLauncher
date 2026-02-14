/*
 * Copyright (C) 2026 DefaultLauncher Contributors
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
package com.android.launcher3.touch;

import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.util.TouchController;

/**
 * TouchController that detects downward swipes on the workspace in NORMAL state
 * and expands the system notification shade.
 */
public class StatusBarSwipeController implements TouchController, SingleAxisSwipeDetector.Listener {

    private static final String TAG = "StatusBarSwipeCtrl";

    private final Launcher mLauncher;
    private final SingleAxisSwipeDetector mDetector;
    private boolean mNoIntercept;

    public StatusBarSwipeController(Launcher launcher) {
        mLauncher = launcher;
        mDetector = new SingleAxisSwipeDetector(launcher, this, SingleAxisSwipeDetector.VERTICAL);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canIntercept();
            if (!mNoIntercept) {
                mDetector.setDetectableScrollConditions(
                        SingleAxisSwipeDetector.DIRECTION_NEGATIVE, false);
            }
        }
        if (mNoIntercept) {
            return false;
        }
        mDetector.onTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        mDetector.onTouchEvent(ev);
        return true;
    }

    private boolean canIntercept() {
        return mLauncher.isInState(LauncherState.NORMAL)
                && AbstractFloatingView.getTopOpenView(mLauncher) == null;
    }

    // -- SingleAxisSwipeDetector.Listener --

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        // nothing to do on drag start
    }

    @Override
    public boolean onDrag(float displacement) {
        // consume the drag but don't need to do anything visual
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        // velocity > 0 means downward for VERTICAL DIRECTION_NEGATIVE
        if (velocity > 0) {
            expandNotificationPanel();
        }
        mDetector.finishedScrolling();
    }

    private void expandNotificationPanel() {
        try {
            Object service = mLauncher.getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(service);
        } catch (Exception e) {
            Log.e(TAG, "Failed to expand notifications panel", e);
        }
    }
}
