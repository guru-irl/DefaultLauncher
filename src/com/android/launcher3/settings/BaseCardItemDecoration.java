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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import com.android.launcher3.util.Themes;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Abstract base class for position-aware rounded-rect card decorations.
 * Handles shared paint/path/ripple setup and the draw loop; subclasses
 * supply the logic for skipping items and determining group boundaries.
 *
 * <ul>
 *   <li>First item in group: large top corners, small bottom corners</li>
 *   <li>Middle items: small corners all around</li>
 *   <li>Last item in group: small top corners, large bottom corners</li>
 *   <li>Solo item (only one in group): large corners all around</li>
 * </ul>
 *
 * @see CardGroupItemDecoration
 * @see com.android.launcher3.widget.WidgetStackEditorItemDecoration
 */
public abstract class BaseCardItemDecoration extends RecyclerView.ItemDecoration {

    protected final Paint mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final float mLargeRadius;
    protected final float mSmallRadius;
    protected final int mHorizontalMargin;
    protected final int mItemGap;
    private final Path mTempPath = new Path();
    private final RectF mTempRect = new RectF();
    private final int mRippleColor;
    private final int mRippleTagId;

    protected BaseCardItemDecoration(int cardColor, float largeRadius, float smallRadius,
            int horizontalMargin, int itemGap, int rippleColor, int rippleTagId) {
        mCardPaint.setStyle(Paint.Style.FILL);
        mCardPaint.setColor(cardColor);
        mLargeRadius = largeRadius;
        mSmallRadius = smallRadius;
        mHorizontalMargin = horizontalMargin;
        mItemGap = itemGap;
        mRippleColor = rippleColor;
        mRippleTagId = rippleTagId;
    }

    /**
     * Computes an 8-element corner radii array suitable for
     * {@link Path#addRoundRect} and {@link GradientDrawable#setCornerRadii}.
     */
    public static float[] computeCornerRadii(boolean isFirst, boolean isLast,
            float largeR, float smallR) {
        float topLeft, topRight, bottomLeft, bottomRight;
        if (isFirst && isLast) {
            topLeft = topRight = bottomLeft = bottomRight = largeR;
        } else if (isFirst) {
            topLeft = topRight = largeR;
            bottomLeft = bottomRight = smallR;
        } else if (isLast) {
            topLeft = topRight = smallR;
            bottomLeft = bottomRight = largeR;
        } else {
            topLeft = topRight = bottomLeft = bottomRight = smallR;
        }
        return new float[]{
                topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft
        };
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return;

        int childCount = parent.getChildCount();
        int totalItems = adapter.getItemCount();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) continue;
            if (shouldSkipItem(parent, child, pos)) continue;

            boolean isFirst = isFirstInGroup(parent, pos, totalItems);
            boolean isLast = isLastInGroup(parent, pos, totalItems);
            float[] radii = computeCornerRadii(isFirst, isLast, mLargeRadius, mSmallRadius);

            float top = child.getTop() + child.getTranslationY();
            float bottom = child.getBottom() + child.getTranslationY();

            mTempRect.set(mHorizontalMargin, top,
                    parent.getWidth() - mHorizontalMargin, bottom);

            mTempPath.reset();
            mTempPath.addRoundRect(mTempRect, radii, Path.Direction.CW);
            c.drawPath(mTempPath, mCardPaint);

            applyRipple(child, radii);
        }
    }

    private void applyRipple(View child, float[] radii) {
        Object tag = child.getTag(mRippleTagId);
        if (tag instanceof RippleDrawable && child.getBackground() == tag) {
            RippleDrawable rd = (RippleDrawable) tag;
            GradientDrawable mask = (GradientDrawable) rd.findDrawableByLayerId(
                    android.R.id.mask);
            if (mask != null) {
                mask.setCornerRadii(radii);
            }
            return;
        }
        RippleDrawable ripple = Themes.createRoundedRipple(mRippleColor, radii);
        child.setBackground(ripple);
        child.setTag(mRippleTagId, ripple);
    }

    /** Return true to skip drawing a card background for this item. */
    protected abstract boolean shouldSkipItem(RecyclerView parent, View child, int pos);

    /** Return true if the item at {@code pos} is the first in its card group. */
    protected abstract boolean isFirstInGroup(RecyclerView parent, int pos, int totalItems);

    /** Return true if the item at {@code pos} is the last in its card group. */
    protected abstract boolean isLastInGroup(RecyclerView parent, int pos, int totalItems);
}
