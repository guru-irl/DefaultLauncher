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
package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.anim.M3Durations;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.FolderInfo;

/**
 * Handles folder span mutations on the workspace grid: expanding, collapsing,
 * and applying span changes. UI code (popup menus, resize frame) delegates
 * here for grid-mutation logic.
 */
class FolderSpanHelper {

    private static final String TAG = "FolderSpanHelper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * Core span-change operation. Updates the {@link FolderInfo} model,
     * {@link CellLayoutLayoutParams}, cell occupation, and expanded-state flag.
     *
     * <p><b>Precondition:</b> Caller must have called
     * {@link CellLayout#markCellsAsUnoccupiedForView(View)} before this method.
     *
     * <p><b>Postcondition:</b> Cells are re-marked as occupied at the new position.
     * Does NOT persist to DB or trigger layout — callers handle those as needed.
     */
    static void applySpanChange(FolderIcon folderIcon, CellLayout cellLayout,
            int newSpan, int newCellX, int newCellY) {
        FolderInfo info = folderIcon.mInfo;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderIcon.getLayoutParams();

        // Update expanded-state flag
        if (newSpan > 1) {
            info.options |= FolderInfo.FLAG_EXPANDED;
        } else {
            info.options &= ~FolderInfo.FLAG_EXPANDED;
        }

        // Update data model
        info.spanX = newSpan;
        info.spanY = newSpan;
        info.cellX = newCellX;
        info.cellY = newCellY;

        // Update layout params
        lp.setCellX(newCellX);
        lp.setCellY(newCellY);
        lp.cellHSpan = newSpan;
        lp.cellVSpan = newSpan;

        // Re-mark cells as occupied at new position
        cellLayout.markCellsAsOccupiedForView(folderIcon);

        // Notify FolderIcon of state change
        folderIcon.updateExpandedState();
    }

    /**
     * Expands a folder to the given target span with vacancy-finding and
     * scale-up animation.
     */
    static void expandToSpan(Launcher launcher, FolderIcon folderIcon, int targetSpan) {
        if (DEBUG) Log.d(TAG, "expandToSpan: id=" + folderIcon.mInfo.id
                + " targetSpan=" + targetSpan);
        FolderInfo info = folderIcon.mInfo;
        View parent = (View) folderIcon.getParent();
        if (!(parent instanceof ShortcutAndWidgetContainer)) return;
        CellLayout cellLayout = (CellLayout) parent.getParent();
        if (cellLayout == null) return;

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderIcon.getLayoutParams();
        int oldCellX = lp.getCellX();
        int oldCellY = lp.getCellY();
        int oldSpan = Math.max(info.spanX, 1);

        // Capture old dimensions for animation
        int oldW = folderIcon.getWidth();
        int oldH = folderIcon.getHeight();

        // Release current occupation
        cellLayout.markCellsAsUnoccupiedForView(folderIcon);

        // Check if target span fits at current position
        boolean fits = cellLayout.isRegionVacant(oldCellX, oldCellY, targetSpan, targetSpan);
        int newCellX = oldCellX;
        int newCellY = oldCellY;

        if (!fits) {
            // Use FOLDER's pixel position to find nearest vacancy
            int[] folderPixel = new int[2];
            cellLayout.regionToCenterPoint(oldCellX, oldCellY, oldSpan, oldSpan, folderPixel);
            int[] result = new int[2];
            int[] resultSpan = new int[2];
            int[] found = cellLayout.findNearestVacantArea(
                    folderPixel[0], folderPixel[1],
                    targetSpan, targetSpan, targetSpan, targetSpan,
                    result, resultSpan);
            if (found != null && resultSpan[0] >= targetSpan
                    && resultSpan[1] >= targetSpan) {
                newCellX = result[0];
                newCellY = result[1];
                fits = true;
            }
        }

        if (!fits) {
            // Re-mark the old position and show error
            cellLayout.markCellsAsOccupiedForView(folderIcon);
            Toast.makeText(launcher, R.string.folder_no_space, Toast.LENGTH_SHORT).show();
            return;
        }

        // Apply span change (model + layout params + occupation + state)
        applySpanChange(folderIcon, cellLayout, targetSpan, newCellX, newCellY);

        // Persist to database
        launcher.getModelWriter().updateItemInDatabase(info);

        // Scale-up animation from old size to new size
        if (oldW > 0 && oldH > 0) {
            folderIcon.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    folderIcon.removeOnLayoutChangeListener(this);
                    int newW = right - left;
                    int newH = bottom - top;
                    if (newW > 0 && newH > 0) {
                        float startScaleX = (float) oldW / newW;
                        float startScaleY = (float) oldH / newH;
                        folderIcon.setPivotX(newW / 2f);
                        folderIcon.setPivotY(newH / 2f);
                        folderIcon.setScaleX(startScaleX);
                        folderIcon.setScaleY(startScaleY);
                        Interpolator interp = AnimationUtils.loadInterpolator(launcher,
                                com.android.app.animation.R.interpolator
                                        .standard_decelerate_interpolator);
                        ObjectAnimator sx = ObjectAnimator.ofFloat(
                                folderIcon, View.SCALE_X, startScaleX, 1f);
                        ObjectAnimator sy = ObjectAnimator.ofFloat(
                                folderIcon, View.SCALE_Y, startScaleY, 1f);
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(sx, sy);
                        set.setDuration(M3Durations.MEDIUM_1);
                        set.setInterpolator(interp);
                        set.start();
                    }
                }
            });
        }

        // Request layout to resize the view (after listener is registered)
        folderIcon.requestLayout();
        cellLayout.requestLayout();
    }

    /**
     * Collapses an expanded folder to 1x1 with shrink animation.
     */
    static void collapseToOneByOne(Launcher launcher, FolderIcon folderIcon) {
        if (DEBUG) Log.d(TAG, "collapseToOneByOne: id=" + folderIcon.mInfo.id);
        FolderInfo info = folderIcon.mInfo;
        View parent = (View) folderIcon.getParent();
        if (!(parent instanceof ShortcutAndWidgetContainer)) return;
        CellLayout cellLayout = (CellLayout) parent.getParent();
        if (cellLayout == null) return;

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderIcon.getLayoutParams();

        // Compute target scale from actual 1x1 cell size
        int curW = folderIcon.getWidth();
        int curH = folderIcon.getHeight();
        int cellW = cellLayout.getCellWidth();
        int cellH = cellLayout.getCellHeight();
        float targetScaleX = (curW > 0 && cellW > 0) ? (float) cellW / curW : 1f;
        float targetScaleY = (curH > 0 && cellH > 0) ? (float) cellH / curH : 1f;

        if (curW > 0 && curH > 0) {
            // Pivot at (0,0) so the view shrinks toward its top-left corner,
            // which matches where the 1x1 cell will be positioned after collapse.
            folderIcon.setPivotX(0f);
            folderIcon.setPivotY(0f);
            Interpolator interp = AnimationUtils.loadInterpolator(launcher,
                    com.android.app.animation.R.interpolator.standard_accelerate_interpolator);
            ObjectAnimator sx = ObjectAnimator.ofFloat(
                    folderIcon, View.SCALE_X, 1f, targetScaleX);
            ObjectAnimator sy = ObjectAnimator.ofFloat(
                    folderIcon, View.SCALE_Y, 1f, targetScaleY);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(sx, sy);
            set.setDuration(M3Durations.MEDIUM_1);
            set.setInterpolator(interp);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    folderIcon.setScaleX(1f);
                    folderIcon.setScaleY(1f);
                    applyCollapseAndPersist(launcher, folderIcon, cellLayout, lp);
                }
            });
            set.start();
        } else {
            applyCollapseAndPersist(launcher, folderIcon, cellLayout, lp);
        }
    }

    private static void applyCollapseAndPersist(Launcher launcher, FolderIcon folderIcon,
            CellLayout cellLayout, CellLayoutLayoutParams lp) {
        // Release current occupation
        cellLayout.markCellsAsUnoccupiedForView(folderIcon);

        // Apply 1x1 span change at current position
        applySpanChange(folderIcon, cellLayout, 1, lp.getCellX(), lp.getCellY());

        // Persist to database
        launcher.getModelWriter().updateItemInDatabase(folderIcon.mInfo);

        // Request layout to resize the view
        folderIcon.requestLayout();
        cellLayout.requestLayout();
    }
}
