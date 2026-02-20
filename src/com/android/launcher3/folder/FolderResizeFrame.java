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

import static com.android.launcher3.LauncherAnimUtils.LAYOUT_HEIGHT;
import static com.android.launcher3.LauncherAnimUtils.LAYOUT_WIDTH;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_X;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_Y;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.views.BaseDragLayer;

/**
 * A simplified resize frame for folders, modeled on {@code AppWidgetResizeFrame}.
 * Shows 4 corner drag handles. The user drags corners to resize
 * the folder's span from 1x1 up to 3x3 (always square).
 */
public class FolderResizeFrame extends AbstractFloatingView
        implements View.OnKeyListener, DragController.DragListener {

    private static final String TAG = "FolderResizeFrame";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final int SNAP_DURATION_MS = 150;
    private static final float DIMMED_HANDLE_ALPHA = 0f;
    private static final float RESIZE_THRESHOLD = 0.66f;

    private static final int HANDLE_COUNT = 4;
    private static final int INDEX_TL = 0;
    private static final int INDEX_TR = 1;
    private static final int INDEX_BL = 2;
    private static final int INDEX_BR = 3;

    private final Launcher mLauncher;
    private final View[] mDragHandles = new View[HANDLE_COUNT];
    private final int mBackgroundPadding;
    private final int mTouchTargetWidth;

    private FolderIcon mFolderIcon;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;

    // Which corner is being dragged
    private int mActiveCorner = -1;

    private int mRunningSpanInc;
    private ObjectAnimator mSnapAnimator;

    private int mXDown, mYDown;

    private final Rect mTmpRect = new Rect();

    public FolderResizeFrame(Context context) {
        this(context, null);
    }

    public FolderResizeFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderResizeFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mBackgroundPadding = getResources()
                .getDimensionPixelSize(R.dimen.resize_frame_background_padding);
        mTouchTargetWidth = 2 * mBackgroundPadding;
        if (DEBUG) Log.d(TAG, "init: bgPad=" + mBackgroundPadding
                + " touchTarget=" + mTouchTargetWidth);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDragHandles[INDEX_TL] = findViewById(R.id.folder_resize_tl_handle);
        mDragHandles[INDEX_TR] = findViewById(R.id.folder_resize_tr_handle);
        mDragHandles[INDEX_BL] = findViewById(R.id.folder_resize_bl_handle);
        mDragHandles[INDEX_BR] = findViewById(R.id.folder_resize_br_handle);
        setOnKeyListener(this);
    }

    /**
     * Shows the resize frame for a folder icon.
     */
    public static void showForFolder(FolderIcon folderIcon, CellLayout cellLayout) {
        if (folderIcon.getParent() == null) return;
        Launcher launcher = Launcher.getLauncher(cellLayout.getContext());
        AbstractFloatingView.closeAllOpenViews(launcher);

        DragLayer dl = launcher.getDragLayer();
        FolderResizeFrame frame = (FolderResizeFrame) launcher.getLayoutInflater()
                .inflate(R.layout.folder_resize_frame, dl, false);
        frame.setupForFolder(folderIcon, cellLayout, dl);
        ((DragLayer.LayoutParams) frame.getLayoutParams()).customPosition = true;

        dl.addView(frame);
        frame.mIsOpen = true;
        frame.requestFocus();
        frame.post(() -> frame.snapToFolder(false));

        if (DEBUG) {
            FolderInfo info = folderIcon.mInfo;
            Log.d(TAG, "showForFolder: folderId=" + (info != null ? info.id : -1)
                    + " spanX=" + (info != null ? info.spanX : -1)
                    + " spanY=" + (info != null ? info.spanY : -1)
                    + " options=" + (info != null ? info.options : -1));
        }
    }

    /**
     * Shows the resize frame alongside an already-open popup (no closeAllOpenViews).
     * When the user grabs a corner handle, the popup auto-closes.
     */
    public static void showAlongsidePopup(FolderIcon folderIcon, CellLayout cellLayout) {
        if (folderIcon.getParent() == null) return;
        Launcher launcher = Launcher.getLauncher(cellLayout.getContext());

        DragLayer dl = launcher.getDragLayer();
        FolderResizeFrame frame = (FolderResizeFrame) launcher.getLayoutInflater()
                .inflate(R.layout.folder_resize_frame, dl, false);
        frame.setupForFolder(folderIcon, cellLayout, dl);
        ((DragLayer.LayoutParams) frame.getLayoutParams()).customPosition = true;

        dl.addView(frame);
        frame.mIsOpen = true;
        // Don't request focus — let the popup keep focus
        frame.post(() -> frame.snapToFolder(false));

        // Auto-close resize frame when a drag starts
        launcher.getDragController().addDragListener(frame);

        if (DEBUG) {
            FolderInfo info = folderIcon.mInfo;
            Log.d(TAG, "showAlongsidePopup: folderId=" + (info != null ? info.id : -1));
        }
    }

    private void setupForFolder(FolderIcon folderIcon, CellLayout cellLayout, DragLayer dragLayer) {
        mFolderIcon = folderIcon;
        mCellLayout = cellLayout;
        mDragLayer = dragLayer;
    }

    private void getSnappedRectRelativeToDragLayer(Rect out) {
        mDragLayer.getViewRectRelativeToSelf(mFolderIcon, out);

        int width = 2 * mBackgroundPadding + out.width();
        int height = 2 * mBackgroundPadding + out.height();
        int x = out.left - mBackgroundPadding;
        int y = out.top - mBackgroundPadding;

        out.left = x;
        out.top = y;
        out.right = out.left + width;
        out.bottom = out.top + height;
    }

    private void snapToFolder(boolean animate) {
        getSnappedRectRelativeToDragLayer(mTmpRect);

        int newWidth = mTmpRect.width();
        int newHeight = mTmpRect.height();
        int newX = mTmpRect.left;
        int newY = mTmpRect.top;

        if (DEBUG) Log.d(TAG, "snapToFolder: animate=" + animate
                + " rect=" + mTmpRect + " newW=" + newWidth + " newH=" + newHeight);

        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();

        if (!animate) {
            lp.width = newWidth;
            lp.height = newHeight;
            lp.x = newX;
            lp.y = newY;
            requestLayout();
        } else {
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(lp,
                    PropertyValuesHolder.ofInt(LAYOUT_WIDTH, lp.width, newWidth),
                    PropertyValuesHolder.ofInt(LAYOUT_HEIGHT, lp.height, newHeight),
                    PropertyValuesHolder.ofInt(LAYOUT_X, lp.x, newX),
                    PropertyValuesHolder.ofInt(LAYOUT_Y, lp.y, newY));
            anim.addUpdateListener(animation -> requestLayout());
            anim.setDuration(SNAP_DURATION_MS);
            anim.start();
        }
    }

    /**
     * Detects which corner is being touched.
     * @return true if a corner was activated.
     */
    private boolean beginResizeIfPointInRegion(int x, int y) {
        int w = getWidth();
        int h = getHeight();

        boolean left = x < mTouchTargetWidth;
        boolean right = x > w - mTouchTargetWidth;
        boolean top = y < mTouchTargetWidth;
        boolean bottom = y > h - mTouchTargetWidth;

        mActiveCorner = -1;
        if (left && top) mActiveCorner = INDEX_TL;
        else if (right && top) mActiveCorner = INDEX_TR;
        else if (left && bottom) mActiveCorner = INDEX_BL;
        else if (right && bottom) mActiveCorner = INDEX_BR;

        if (mActiveCorner >= 0) {
            // Highlight active corner, dim others
            for (int i = 0; i < HANDLE_COUNT; i++) {
                mDragHandles[i].setAlpha(i == mActiveCorner ? 1.0f : DIMMED_HANDLE_ALPHA);
            }
            if (DEBUG) Log.d(TAG, "beginResize: corner=" + cornerName(mActiveCorner)
                    + " at x=" + x + " y=" + y + " frameW=" + w + " frameH=" + h);
        }

        return mActiveCorner >= 0;
    }

    private static String cornerName(int corner) {
        switch (corner) {
            case INDEX_TL: return "TL";
            case INDEX_TR: return "TR";
            case INDEX_BL: return "BL";
            case INDEX_BR: return "BR";
            default: return "NONE";
        }
    }

    /**
     * Converts DragLayer-space coordinates to frame-local coordinates.
     */
    private int toLocalX(int dragLayerX) {
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        return dragLayerX - lp.x;
    }

    private int toLocalY(int dragLayerY) {
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        return dragLayerY - lp.y;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int rawX = (int) ev.getX();
        int rawY = (int) ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mXDown = rawX;
                mYDown = rawY;
                if (!beginResizeIfPointInRegion(toLocalX(rawX), toLocalY(rawY))) {
                    return false;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                visualizeResizeForDelta(rawX - mXDown, rawY - mYDown);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                visualizeResizeForDelta(rawX - mXDown, rawY - mYDown);
                onTouchUp();
                break;
        }
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            int rawX = (int) ev.getX();
            int rawY = (int) ev.getY();
            DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
            Rect hitRect = new Rect(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);

            // On a corner handle → intercept for resize, close popup
            if (hitRect.contains(rawX, rawY)
                    && isOnCornerHandle(rawX - lp.x, rawY - lp.y)) {
                PopupContainerWithArrow<?> popup = PopupContainerWithArrow.getOpen(mLauncher);
                if (popup != null) popup.close(false);
                return true;
            }

            // Check if touch is inside the popup
            PopupContainerWithArrow<?> popup = PopupContainerWithArrow.getOpen(mLauncher);
            if (popup != null) {
                BaseDragLayer dl = mLauncher.getDragLayer();
                if (dl.isEventOverView(popup, ev)) {
                    // Touch is on popup — don't close anything, just let normal
                    // view dispatch reach popup's child OnClickListeners.
                    // The resize frame stays visible but harmless; when the
                    // popup's onClick calls closeAllOpenViews, both close together.
                    return false;
                }
            }

            // Touch outside both — close everything
            if (popup != null) popup.close(false);
            close(false);
            return false;
        }
        // Non-DOWN events during an active resize: always consume
        return mActiveCorner >= 0;
    }

    /**
     * Checks whether the given frame-local coordinates fall on a corner drag handle.
     */
    private boolean isOnCornerHandle(int localX, int localY) {
        int w = getWidth();
        int h = getHeight();
        boolean left = localX < mTouchTargetWidth;
        boolean right = localX > w - mTouchTargetWidth;
        boolean top = localY < mTouchTargetWidth;
        boolean bottom = localY > h - mTouchTargetWidth;
        return (left && top) || (right && top) || (left && bottom) || (right && bottom);
    }

    private void visualizeResizeForDelta(int deltaX, int deltaY) {
        if (mActiveCorner < 0) return;

        FolderInfo info = mFolderIcon.mInfo;
        CellLayoutLayoutParams folderLp = (CellLayoutLayoutParams) mFolderIcon.getLayoutParams();

        int cellW = mCellLayout.getCellWidth();
        int cellH = mCellLayout.getCellHeight();
        if (cellW == 0 || cellH == 0) return;

        // Determine drag direction based on which corner is active.
        // For each corner, "outward" drag = grow, "inward" drag = shrink.
        // We compute a combined span increment from both axes.
        float hFrac = 0f;
        float vFrac = 0f;

        switch (mActiveCorner) {
            case INDEX_BR: // drag right+down = grow
                hFrac = (float) deltaX / cellW;
                vFrac = (float) deltaY / cellH;
                break;
            case INDEX_BL: // drag left+down = grow
                hFrac = (float) -deltaX / cellW;
                vFrac = (float) deltaY / cellH;
                break;
            case INDEX_TR: // drag right+up = grow
                hFrac = (float) deltaX / cellW;
                vFrac = (float) -deltaY / cellH;
                break;
            case INDEX_TL: // drag left+up = grow
                hFrac = (float) -deltaX / cellW;
                vFrac = (float) -deltaY / cellH;
                break;
        }

        // Use the average of both axes for a combined diagonal drag, minus running total
        float combinedFrac = (hFrac + vFrac) / 2f - mRunningSpanInc;
        int spanDelta = getSpanIncrement(combinedFrac);

        if (DEBUG) Log.d(TAG, "visualizeResize: delta=" + deltaX + "," + deltaY
                + " cellW=" + cellW + " cellH=" + cellH
                + " hFrac=" + String.format("%.2f", hFrac)
                + " vFrac=" + String.format("%.2f", vFrac)
                + " combined=" + String.format("%.2f", combinedFrac)
                + " spanDelta=" + spanDelta);

        if (spanDelta == 0) return;

        // Enforce square: same delta for both X and Y
        int newSpan = info.spanX + spanDelta;
        // Since we always keep spanX == spanY, use info.spanX as the current span

        // Clamp span
        newSpan = Math.max(1, Math.min(FolderInfo.MAX_SPAN, newSpan));

        // Don't allow span larger than grid
        int numCols = mCellLayout.getCountX();
        int numRows = mCellLayout.getCountY();
        newSpan = Math.min(newSpan, Math.min(numCols, numRows));

        int actualDelta = newSpan - info.spanX;
        if (actualDelta == 0) return;

        // Determine new cell position based on which corner is being dragged.
        // When dragging TL or left/top corners, the position shifts.
        int newCellX = folderLp.getCellX();
        int newCellY = folderLp.getCellY();

        if (mActiveCorner == INDEX_TL || mActiveCorner == INDEX_BL) {
            // Left corners: shift X leftward when growing
            newCellX = folderLp.getCellX() - actualDelta;
        }
        if (mActiveCorner == INDEX_TL || mActiveCorner == INDEX_TR) {
            // Top corners: shift Y upward when growing
            newCellY = folderLp.getCellY() - actualDelta;
        }

        // Clamp position to grid
        newCellX = Math.max(0, Math.min(numCols - newSpan, newCellX));
        newCellY = Math.max(0, Math.min(numRows - newSpan, newCellY));

        if (DEBUG) Log.d(TAG, "visualizeResize: corner=" + cornerName(mActiveCorner)
                + " delta=" + deltaX + "," + deltaY
                + " spanDelta=" + actualDelta
                + " newSpan=" + newSpan
                + " newCell=" + newCellX + "," + newCellY
                + " oldSpan=" + info.spanX + " oldCell=" + info.cellX + "," + info.cellY);

        // Check vacancy (temporarily unmark current cells)
        mCellLayout.markCellsAsUnoccupiedForView(mFolderIcon);
        boolean vacant = mCellLayout.isRegionVacant(newCellX, newCellY, newSpan, newSpan);

        if (vacant) {
            // Apply span change (model + layout params + occupation + state)
            FolderSpanHelper.applySpanChange(
                    mFolderIcon, mCellLayout, newSpan, newCellX, newCellY);

            // Sync tmp coords in case useTmpCoords is true from a previous drag/reorder
            folderLp.setTmpCellX(newCellX);
            folderLp.setTmpCellY(newCellY);
            folderLp.useTmpCoords = false;

            mRunningSpanInc += actualDelta;

            if (DEBUG) Log.d(TAG, "resize applied: span=" + newSpan
                    + " cell=" + newCellX + "," + newCellY
                    + " options=" + info.options
                    + " FLAG_EXPANDED=" + ((info.options & FolderInfo.FLAG_EXPANDED) != 0));

            // Cancel any in-progress animations and reset scale to avoid
            // stale transforms affecting layout/position calculations
            mFolderIcon.animate().cancel();
            mFolderIcon.setScaleX(1f);
            mFolderIcon.setScaleY(1f);
            if (mSnapAnimator != null) {
                mSnapAnimator.cancel();
                mSnapAnimator = null;
            }

            // Force immediate re-measure and re-layout (no scale animation —
            // the frame snap animation provides visual feedback, and scale
            // causes stale rendering when shrinking because the parent
            // CellLayout doesn't redraw pixels outside child layout bounds)
            if (mFolderIcon.getParent() instanceof ShortcutAndWidgetContainer swc) {
                swc.setupLp(mFolderIcon);
                swc.measureChild(mFolderIcon);
                swc.layoutChild(mFolderIcon);
            }

            // Invalidate parent to clear old pixels in vacated area
            mFolderIcon.invalidate();
            mCellLayout.invalidate();

            // Compute snap target with correct bounds
            getSnappedRectRelativeToDragLayer(mTmpRect);

            // Animate frame to match new icon position
            final DragLayer.LayoutParams frameLp =
                    (DragLayer.LayoutParams) getLayoutParams();
            mSnapAnimator = ObjectAnimator.ofPropertyValuesHolder(frameLp,
                    PropertyValuesHolder.ofInt(LAYOUT_WIDTH, frameLp.width, mTmpRect.width()),
                    PropertyValuesHolder.ofInt(LAYOUT_HEIGHT, frameLp.height, mTmpRect.height()),
                    PropertyValuesHolder.ofInt(LAYOUT_X, frameLp.x, mTmpRect.left),
                    PropertyValuesHolder.ofInt(LAYOUT_Y, frameLp.y, mTmpRect.top));
            mSnapAnimator.addUpdateListener(a -> requestLayout());
            mSnapAnimator.setDuration(SNAP_DURATION_MS);
            mSnapAnimator.start();
        } else {
            if (DEBUG) Log.d(TAG, "resize blocked: region not vacant at "
                    + newCellX + "," + newCellY + " span=" + newSpan);
            // Re-mark original cells
            mCellLayout.markCellsAsOccupiedForView(mFolderIcon);
        }
    }

    private static int getSpanIncrement(float deltaFrac) {
        if (Math.abs(deltaFrac) > RESIZE_THRESHOLD) {
            return deltaFrac > 0 ? 1 : -1;
        }
        return 0;
    }

    private void onTouchUp() {
        if (DEBUG) {
            FolderInfo info = mFolderIcon.mInfo;
            Log.d(TAG, "onTouchUp: final span=" + (info != null ? info.spanX : -1)
                    + "x" + (info != null ? info.spanY : -1)
                    + " options=" + (info != null ? info.options : -1));
        }

        // Finish any in-progress resize animations
        mFolderIcon.animate().cancel();
        mFolderIcon.setScaleX(1f);
        mFolderIcon.setScaleY(1f);
        if (mSnapAnimator != null) {
            mSnapAnimator.cancel();
            mSnapAnimator = null;
        }

        // Persist to database
        mLauncher.getModelWriter().updateItemInDatabase(mFolderIcon.mInfo);

        // Reset handles
        for (View handle : mDragHandles) {
            handle.setAlpha(1.0f);
        }
        mActiveCorner = -1;
        mRunningSpanInc = 0;

        // Re-snap
        post(() -> snapToFolder(true));
    }

    @Override
    protected void handleClose(boolean animate) {
        mIsOpen = false;
        // Cancel any running animations
        mFolderIcon.animate().cancel();
        mFolderIcon.setScaleX(1f);
        mFolderIcon.setScaleY(1f);
        if (mSnapAnimator != null) {
            mSnapAnimator.cancel();
            mSnapAnimator = null;
        }
        // Persist final state
        mLauncher.getModelWriter().updateItemInDatabase(mFolderIcon.mInfo);

        // Also close any open popup
        PopupContainerWithArrow<?> popup = PopupContainerWithArrow.getOpen(mLauncher);
        if (popup != null) popup.close(false);

        // Unregister drag listener
        mLauncher.getDragController().removeDragListener(this);

        if (DEBUG) {
            FolderInfo info = mFolderIcon.mInfo;
            Log.d(TAG, "handleClose: final span=" + (info != null ? info.spanX : -1)
                    + "x" + (info != null ? info.spanY : -1));
        }

        if (getParent() != null) {
            ((DragLayer) getParent()).removeView(this);
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER_RESIZE_FRAME) != 0;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            close(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean canHandleBack() {
        return true;
    }

    @Override
    public void onBackInvoked() {
        close(true);
    }

    // -- DragController.DragListener --

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        close(false);
    }

    @Override
    public void onDragEnd() { }
}
