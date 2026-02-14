package com.android.launcher3.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;

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
        float density = ctx.getResources().getDisplayMetrics().density;
        mLargeRadius = 24 * density;
        mSmallRadius = 4 * density;
        mHorizontalMargin = (int) (16 * density);
        mItemGap = (int) (4 * density);
        mGroupTopMargin = (int) (4 * density);
        mGroupBottomMargin = (int) (4 * density);

        mCardPaint.setStyle(Paint.Style.FILL);
        mCardPaint.setColor(ctx.getColor(R.color.materialColorSurfaceContainer));
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

            boolean isFirst = (pos == 0)
                    || (pga.getItem(pos - 1) instanceof PreferenceCategory);
            boolean isLast = (pos == totalItems - 1)
                    || (pos + 1 < totalItems
                        && pga.getItem(pos + 1) instanceof PreferenceCategory);

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

        // All card items get horizontal margins
        outRect.left = mHorizontalMargin;
        outRect.right = mHorizontalMargin;

        int totalItems = pga.getItemCount();
        boolean isFirst = (pos == 0)
                || (pga.getItem(pos - 1) instanceof PreferenceCategory);
        boolean isLast = (pos == totalItems - 1)
                || (pos + 1 < totalItems
                    && pga.getItem(pos + 1) instanceof PreferenceCategory);

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
}
