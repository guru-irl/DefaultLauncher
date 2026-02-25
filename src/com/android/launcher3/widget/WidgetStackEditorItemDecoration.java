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
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.settings.BaseCardItemDecoration;
import com.android.launcher3.util.Themes;

/**
 * Draws position-aware rounded-rect card backgrounds for widget stack editor items.
 * All items belong to a single group (no category headers). Items currently being
 * dragged (tagged with {@link R.id#editor_drag_state_tag}) are skipped — the
 * ItemTouchHelper callback draws their elevated appearance instead.
 */
public class WidgetStackEditorItemDecoration extends BaseCardItemDecoration {

    public WidgetStackEditorItemDecoration(Context ctx) {
        super(Themes.getAttrColor(ctx, R.attr.widgetPickerSecondarySurfaceColor),
                ctx.getResources().getDimension(R.dimen.m3_shape_extra_large),
                ctx.getResources().getDimension(R.dimen.m3_shape_extra_small),
                ctx.getResources().getDimensionPixelSize(R.dimen.settings_horizontal_margin),
                ctx.getResources().getDimensionPixelSize(R.dimen.settings_item_gap),
                Themes.getAttrColor(ctx, android.R.attr.colorControlHighlight),
                R.id.editor_ripple_tag);
    }

    @Override
    protected boolean shouldSkipItem(RecyclerView parent, View child, int pos) {
        return Boolean.TRUE.equals(child.getTag(R.id.editor_drag_state_tag));
    }

    @Override
    protected boolean isFirstInGroup(RecyclerView parent, int pos, int totalItems) {
        return pos == 0;
    }

    @Override
    protected boolean isLastInGroup(RecyclerView parent, int pos, int totalItems) {
        return pos == totalItems - 1;
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
