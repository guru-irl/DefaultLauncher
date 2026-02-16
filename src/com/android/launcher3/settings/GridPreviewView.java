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
package com.android.launcher3.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;

/**
 * Custom View that draws a scaled-down phone frame with an animated grid preview.
 * Uses real device parameters from {@link DeviceProfile} for accurate row computation.
 */
public class GridPreviewView extends View {

    private static final PathInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final long ANIM_DURATION_MS = 350;

    // Phone frame drawing constants (dp)
    private static final float PHONE_CORNER_DP = 10f;
    private static final float PHONE_STROKE_DP = 1.5f;
    private static final float CELL_CORNER_DP = 6f;
    private static final float LABEL_TEXT_SP = 9f;
    private static final float LABEL_MARGIN_DP = 12f;
    private static final float LABEL_GAP_DP = 8f;

    private final float mDensity;

    // Real device values for grid computation (portrait)
    private final int mScreenWidthPx;
    private final int mScreenHeightPx;
    private int mStatusBarPx;
    private int mBottomMarginPx;
    private int mInterGapPx;
    private int mEdgeGapPx;
    private boolean mDeviceParamsReady;

    private final Paint mFrameFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFrameStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mPhoneRect = new RectF();
    private final RectF mTempRect = new RectF();

    private GridState mFromState;
    private GridState mToState;
    private float mAnimFraction = 1f;
    private ValueAnimator mAnimator;
    private int mPendingColumns = -1;

    public GridPreviewView(Context context) {
        this(context, null);
    }

    public GridPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;

        // Get real device dimensions (always portrait: min x max)
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context);
        mScreenWidthPx = Math.min(dp.widthPx, dp.heightPx);
        mScreenHeightPx = Math.max(dp.widthPx, dp.heightPx);

        // Read grid spacing from DeviceProfile
        mInterGapPx = dp.cellLayoutBorderSpacePx.x;
        mEdgeGapPx = pxFromDp(InvariantDeviceProfile.SQUARE_GRID_EDGE_GAP_DP);
        mBottomMarginPx = dp.hotseatBarBottomSpacePx;

        initPaints(context);

        // Get real status bar height from window insets when available
        setOnApplyWindowInsetsListener((v, insets) -> {
            mStatusBarPx = insets.getInsets(WindowInsets.Type.statusBars()).top;
            if (!mDeviceParamsReady) {
                mDeviceParamsReady = true;
                if (mPendingColumns > 0) {
                    setColumns(mPendingColumns);
                    mPendingColumns = -1;
                }
            }
            return insets;
        });
    }

    private int pxFromDp(float dp) {
        return Math.round(dp * mDensity);
    }

    private void initPaints(Context ctx) {
        mFrameFillPaint.setStyle(Paint.Style.FILL);
        mFrameFillPaint.setColor(ctx.getColor(R.color.materialColorSurfaceContainerHigh));

        mFrameStrokePaint.setStyle(Paint.Style.STROKE);
        mFrameStrokePaint.setStrokeWidth(PHONE_STROKE_DP * mDensity);
        mFrameStrokePaint.setColor(ctx.getColor(R.color.materialColorOutlineVariant));

        mCellPaint.setStyle(Paint.Style.FILL);
        mCellPaint.setColor(ctx.getColor(R.color.materialColorPrimary));

        mLabelPaint.setTextSize(LABEL_TEXT_SP * mDensity);
        mLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLabelPaint.setColor(ctx.getColor(R.color.materialColorOnSurfaceVariant));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Set the initial column count (no animation). */
    public void setColumns(int columns) {
        if (!mDeviceParamsReady && mStatusBarPx == 0) {
            // Insets not yet available; use a sensible fallback and recompute later
            mStatusBarPx = pxFromDp(24);
            mDeviceParamsReady = true;
        }
        mFromState = computeGridState(columns);
        mToState = mFromState;
        mAnimFraction = 1f;
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        invalidate();
    }

    /** Animate from the current state to a new column count. */
    public void animateToColumns(int newColumns) {
        if (mFromState == null) {
            setColumns(newColumns);
            return;
        }
        GridState newState = computeGridState(newColumns);

        // Capture current interpolated state as the "from" (handles rapid scrubbing)
        if (mAnimator != null && mAnimator.isRunning()) {
            mFromState = interpolateState(mFromState, mToState, mAnimFraction);
            mAnimator.cancel();
        }

        mToState = newState;
        mAnimFraction = 0f;

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIM_DURATION_MS);
        mAnimator.setInterpolator(EMPHASIZED_DECELERATE);
        mAnimator.addUpdateListener(animation -> {
            mAnimFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFromState == null) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        // Reserve space: left for row numbers, bottom for column numbers
        float labelGap = LABEL_GAP_DP * mDensity;
        float rowLabelWidth = mLabelPaint.measureText("88") + labelGap;
        float colLabelHeight = mLabelPaint.getTextSize() + labelGap;

        // Compute phone rect (centered on screen, accounting for label gutters)
        float deviceAspect = (float) mScreenHeightPx / mScreenWidthPx;
        float margin = LABEL_MARGIN_DP * mDensity;
        float phoneHeight = viewHeight - colLabelHeight - 2 * margin;
        float phoneWidth = phoneHeight / deviceAspect;

        float maxPhoneWidth = viewWidth - rowLabelWidth - 2 * margin;
        if (phoneWidth > maxPhoneWidth) {
            phoneWidth = maxPhoneWidth;
            phoneHeight = phoneWidth * deviceAspect;
        }

        // Center the phone itself on the full view
        float phoneLeft = (viewWidth - phoneWidth) / 2f;
        float phoneTop = (viewHeight - colLabelHeight - phoneHeight) / 2f;
        mPhoneRect.set(phoneLeft, phoneTop, phoneLeft + phoneWidth, phoneTop + phoneHeight);

        float phoneCorner = PHONE_CORNER_DP * mDensity;
        float scale = phoneHeight / mScreenHeightPx;

        // 1. Phone frame fill + stroke
        canvas.drawRoundRect(mPhoneRect, phoneCorner, phoneCorner, mFrameFillPaint);
        canvas.drawRoundRect(mPhoneRect, phoneCorner, phoneCorner, mFrameStrokePaint);

        // Interpolate grid state
        GridState state = interpolateState(mFromState, mToState, mAnimFraction);
        int maxCols = Math.max(mFromState.numColumns, mToState.numColumns);
        int maxRows = Math.max(mFromState.numRows, mToState.numRows);

        // Grid area inside phone
        float statusBarH = mStatusBarPx * scale;
        float bottomMarginH = mBottomMarginPx * scale;
        float edgeGapScaled = state.edgeGap * scale;

        // Center grid vertically within available space
        float gridAvailH = phoneHeight - statusBarH - bottomMarginH;
        float cellSize = state.cellSize * scale;
        float gap = state.gap * scale;
        float gridH = state.numRows * cellSize + (state.numRows - 1) * gap;
        float gridTopOffset = (gridAvailH - gridH) / 2f;
        if (gridTopOffset < 0) gridTopOffset = 0;

        float wsLeft = mPhoneRect.left + edgeGapScaled;
        float wsTop = mPhoneRect.top + statusBarH + gridTopOffset;

        float cellCorner = CELL_CORNER_DP * mDensity;
        float maxCornerR = cellSize * 0.4f;
        if (cellCorner > maxCornerR) cellCorner = maxCornerR;

        // 3. All cells (workspace + hotseat as uniform rows)
        int baseCellAlpha = mCellPaint.getAlpha();
        for (int row = 0; row < maxRows; row++) {
            for (int col = 0; col < maxCols; col++) {
                float alpha = getCellAlpha(row, col);
                if (alpha <= 0f) continue;

                float cx = wsLeft + col * (cellSize + gap);
                float cy = wsTop + row * (cellSize + gap);

                float slideOffset = getCellSlideOffset(row, col, scale);

                mTempRect.set(cx, cy + slideOffset, cx + cellSize, cy + cellSize + slideOffset);
                mCellPaint.setAlpha((int) (baseCellAlpha * alpha));
                canvas.drawRoundRect(mTempRect, cellCorner, cellCorner, mCellPaint);
            }
        }
        mCellPaint.setAlpha(baseCellAlpha);

        // 4. Row numbers along the left, centered to each row
        float textVCenter = -(mLabelPaint.descent() + mLabelPaint.ascent()) / 2f;
        mLabelPaint.setTextAlign(Paint.Align.RIGHT);
        float rowLabelX = mPhoneRect.left - labelGap;
        for (int row = 0; row < state.numRows; row++) {
            float cy = wsTop + row * (cellSize + gap) + cellSize / 2f + textVCenter;
            canvas.drawText(String.valueOf(row + 1), rowLabelX, cy, mLabelPaint);
        }

        // 5. Column numbers along the bottom, centered to each column
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        float colLabelY = mPhoneRect.bottom + labelGap + mLabelPaint.getTextSize();
        for (int col = 0; col < state.numColumns; col++) {
            float cx = wsLeft + col * (cellSize + gap) + cellSize / 2f;
            canvas.drawText(String.valueOf(col + 1), cx, colLabelY, mLabelPaint);
        }
    }

    private float getCellAlpha(int row, int col) {
        boolean inFrom = row < mFromState.numRows && col < mFromState.numColumns;
        boolean inTo = row < mToState.numRows && col < mToState.numColumns;

        if (inFrom && inTo) return 1f;

        if (!inFrom && inTo) {
            float t = mAnimFraction;
            if (t < 0.3f) return 0f;
            return (t - 0.3f) / 0.7f;
        }

        if (inFrom && !inTo) {
            float t = mAnimFraction;
            if (t > 0.5f) return 0f;
            return 1f - (t / 0.5f);
        }

        return 0f;
    }

    private float getCellSlideOffset(int row, int col, float scale) {
        boolean inFrom = row < mFromState.numRows && col < mFromState.numColumns;
        boolean inTo = row < mToState.numRows && col < mToState.numColumns;

        if (!inFrom && inTo) {
            float t = mAnimFraction;
            float offsetPx = 4f * mDensity * scale;
            if (t < 0.4f) return offsetPx;
            float progress = (t - 0.4f) / 0.6f;
            return offsetPx * (1f - progress);
        }
        return 0f;
    }

    /**
     * Compute grid geometry for a given column count using real device parameters.
     * Matches the formula in {@link DeviceProfile#deriveSquareGridRows()}.
     */
    private GridState computeGridState(int numColumns) {
        float edgeGap = mEdgeGapPx;
        float interGap = mInterGapPx;

        // Cell size: square cells that fill the width
        float availW = mScreenWidthPx - 2 * edgeGap;
        float cellSize = (availW - (numColumns - 1) * interGap) / numColumns;

        // Available height: same formula as DeviceProfile.deriveSquareGridRows()
        float availH = mScreenHeightPx - mStatusBarPx - mBottomMarginPx;

        // Total rows (workspace + hotseat)
        int totalRows = (int) ((availH + interGap) / (cellSize + interGap));
        if (totalRows < 2) totalRows = 2;

        // Gap computation matching DeviceProfile
        float adjustedGap = (availH - totalRows * cellSize) / Math.max(totalRows - 1, 1);

        // Cap gap for horizontal fit
        int cellLayoutW = mScreenWidthPx;
        float maxGap = (cellLayoutW - numColumns * cellSize - 2 * edgeGap)
                / Math.max(numColumns - 1, 1);
        float gap = Math.max(0, Math.min(adjustedGap, maxGap));

        return new GridState(numColumns, totalRows, cellSize, gap, edgeGap);
    }

    private GridState interpolateState(GridState from, GridState to, float t) {
        return new GridState(
                t < 0.5f ? from.numColumns : to.numColumns,
                t < 0.5f ? from.numRows : to.numRows,
                lerp(from.cellSize, to.cellSize, t),
                lerp(from.gap, to.gap, t),
                lerp(from.edgeGap, to.edgeGap, t)
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    static class GridState {
        final int numColumns;
        final int numRows;
        final float cellSize;
        final float gap;
        final float edgeGap;

        GridState(int numColumns, int numRows, float cellSize, float gap, float edgeGap) {
            this.numColumns = numColumns;
            this.numRows = numRows;
            this.cellSize = cellSize;
            this.gap = gap;
            this.edgeGap = edgeGap;
        }
    }
}
