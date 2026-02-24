/*
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

package com.android.launcher3.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * Draws position-aware rounded-rect card backgrounds for widget stack editor items.
 * Mirrors the pattern from {@link com.android.launcher3.settings.CardGroupItemDecoration}
 * but simplified — all items belong to a single group (no category headers).
 *
 * <ul>
 *   <li>First item: large top corners, small bottom corners</li>
 *   <li>Middle items: small corners all around</li>
 *   <li>Last item: small top corners, large bottom corners</li>
 *   <li>Solo item: large corners all around</li>
 * </ul>
 *
 * Items are separated by 4dp gaps (no divider lines). Items currently being
 * dragged (tagged with {@link R.id#editor_drag_state_tag}) are skipped —
 * the ItemTouchHelper callback draws their elevated appearance instead.
 */
public class WidgetStackEditorItemDecoration extends RecyclerView.ItemDecoration {

    private final Paint mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mLargeRadius;
    private final float mSmallRadius;
    private final int mHorizontalMargin;
    private final int mItemGap;
    private final Path mTempPath = new Path();
    private final RectF mTempRect = new RectF();
    private final int mRippleColor;

    public WidgetStackEditorItemDecoration(Context ctx) {
        Resources res = ctx.getResources();
        mLargeRadius = res.getDimension(R.dimen.m3_shape_extra_large);
        mSmallRadius = res.getDimension(R.dimen.m3_shape_extra_small);
        mHorizontalMargin = res.getDimensionPixelSize(R.dimen.settings_horizontal_margin);
        mItemGap = res.getDimensionPixelSize(R.dimen.settings_item_gap);

        mCardPaint.setStyle(Paint.Style.FILL);
        mCardPaint.setColor(Themes.getAttrColor(ctx, R.attr.widgetPickerSecondarySurfaceColor));
        mRippleColor = Themes.getAttrColor(ctx, android.R.attr.colorControlHighlight);
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

            // Skip items currently being dragged — the drag callback draws their bg
            if (Boolean.TRUE.equals(child.getTag(R.id.editor_drag_state_tag))) continue;

            boolean isFirst = pos == 0;
            boolean isLast = pos == totalItems - 1;

            float topLeft, topRight, bottomLeft, bottomRight;
            if (isFirst && isLast) {
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

            applyRipple(child, topLeft, topRight, bottomRight, bottomLeft);
        }
    }

    private void applyRipple(View child,
            float topLeft, float topRight, float bottomRight, float bottomLeft) {
        Object tag = child.getTag(R.id.editor_ripple_tag);
        float[] radii = {
                topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft
        };
        if (tag instanceof RippleDrawable) {
            RippleDrawable rd = (RippleDrawable) tag;
            GradientDrawable mask = (GradientDrawable) rd.findDrawableByLayerId(
                    android.R.id.mask);
            if (mask != null) {
                mask.setCornerRadii(radii);
            }
            return;
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadii(radii);
        mask.setColor(0xFFFFFFFF);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(mRippleColor), null, mask);
        child.setBackground(ripple);
        child.setTag(R.id.editor_ripple_tag, ripple);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return;

        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;

        int totalItems = adapter.getItemCount();
        boolean isLast = pos == totalItems - 1;

        outRect.left = mHorizontalMargin;
        outRect.right = mHorizontalMargin;
        outRect.top = mItemGap;
        if (isLast) {
            outRect.bottom = mItemGap;
        }
    }
}
