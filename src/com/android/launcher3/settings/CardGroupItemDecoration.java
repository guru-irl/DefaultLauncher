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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.android.launcher3.R;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.util.Themes;

/**
 * Draws individual rounded-rect backgrounds per preference item, with
 * position-aware corner radii (Lawnchair style). Category headers sit
 * outside the cards with no background.
 *
 * <ul>
 *   <li>First item in group: large top corners, small bottom corners</li>
 *   <li>Middle items: small corners all around</li>
 *   <li>Last item in group: small top corners, large bottom corners</li>
 *   <li>Solo item (only one in group): large corners all around</li>
 * </ul>
 *
 * Items are separated by a 4dp gap (no divider lines).
 */
public class CardGroupItemDecoration extends RecyclerView.ItemDecoration {

    private final Paint mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mLargeRadius;
    private final float mSmallRadius;
    private final int mHorizontalMargin;
    private final int mItemGap;
    private final int mGroupTopMargin;
    private final int mGroupBottomMargin;
    private final Path mTempPath = new Path();
    private final RectF mTempRect = new RectF();

    public CardGroupItemDecoration(Context ctx) {
        Resources res = ctx.getResources();
        mLargeRadius = res.getDimension(R.dimen.settings_card_corner_radius);
        mSmallRadius = res.getDimension(R.dimen.settings_card_small_corner_radius);
        mHorizontalMargin = res.getDimensionPixelSize(R.dimen.settings_horizontal_margin);
        mItemGap = res.getDimensionPixelSize(R.dimen.settings_item_gap);
        mGroupTopMargin = res.getDimensionPixelSize(R.dimen.settings_item_gap);
        mGroupBottomMargin = res.getDimensionPixelSize(R.dimen.settings_item_gap);

        mCardPaint.setStyle(Paint.Style.FILL);
        boolean isDark = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int cardColor = Themes.getAttrColor(ctx, isDark
                ? com.google.android.material.R.attr.colorSurfaceContainer
                : com.google.android.material.R.attr.colorSurface);
        mCardPaint.setColor(cardColor);
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return;
        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;

        int childCount = parent.getChildCount();
        int totalItems = pga.getItemCount();

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == RecyclerView.NO_POSITION) continue;
            if (pga.getItem(pos) instanceof PreferenceCategory) continue;
            if ("no_card".equals(child.getTag())) continue;

            boolean isFirst = isFirstInGroup(pga, parent, pos);
            boolean isLast = isLastInGroup(pga, parent, pos, totalItems);

            float topLeft, topRight, bottomLeft, bottomRight;
            if (isFirst && isLast) {
                // Solo item
                topLeft = topRight = bottomLeft = bottomRight = mLargeRadius;
            } else if (isFirst) {
                topLeft = topRight = mLargeRadius;
                bottomLeft = bottomRight = mSmallRadius;
            } else if (isLast) {
                topLeft = topRight = mSmallRadius;
                bottomLeft = bottomRight = mLargeRadius;
            } else {
                topLeft = topRight = bottomLeft = bottomRight = mSmallRadius;
            }

            float top = child.getTop() + child.getTranslationY();
            float bottom = child.getBottom() + child.getTranslationY();

            mTempRect.set(mHorizontalMargin, top,
                    parent.getWidth() - mHorizontalMargin, bottom);

            mTempPath.reset();
            mTempPath.addRoundRect(mTempRect, new float[]{
                    topLeft, topLeft, topRight, topRight,
                    bottomRight, bottomRight, bottomLeft, bottomLeft
            }, Path.Direction.CW);
            c.drawPath(mTempPath, mCardPaint);
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return;
        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;

        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;

        if (pga.getItem(pos) instanceof PreferenceCategory) {
            // Category headers get the same horizontal margin so the
            // section title aligns with preference titles inside cards.
            outRect.left = mHorizontalMargin;
            outRect.right = mHorizontalMargin;
            return;
        }

        if ("no_card".equals(view.getTag())) return;

        // All card items get horizontal margins
        outRect.left = mHorizontalMargin;
        outRect.right = mHorizontalMargin;

        int totalItems = pga.getItemCount();
        boolean isFirst = isFirstInGroup(pga, parent, pos);
        boolean isLast = isLastInGroup(pga, parent, pos, totalItems);

        // First item in group gets top margin
        if (isFirst) {
            outRect.top = mGroupTopMargin;
        } else {
            // Non-first items get the gap above them
            outRect.top = mItemGap;
        }

        // Last item in group gets bottom margin
        if (isLast) {
            outRect.bottom = mGroupBottomMargin;
        }
    }

    private boolean isFirstInGroup(PreferenceGroupAdapter pga, RecyclerView parent, int pos) {
        return (pos == 0)
                || (pga.getItem(pos - 1) instanceof PreferenceCategory)
                || isNoCardItem(parent, pos - 1);
    }

    private boolean isLastInGroup(
            PreferenceGroupAdapter pga, RecyclerView parent, int pos, int totalItems) {
        return (pos == totalItems - 1)
                || (pos + 1 < totalItems
                    && pga.getItem(pos + 1) instanceof PreferenceCategory)
                || (pos + 1 < totalItems
                    && isNoCardItem(parent, pos + 1));
    }

    /** Check whether the item at adapter position {@code pos} has the "no_card" tag. */
    private boolean isNoCardItem(RecyclerView parent, int pos) {
        RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(pos);
        return vh != null && "no_card".equals(vh.itemView.getTag());
    }
}
