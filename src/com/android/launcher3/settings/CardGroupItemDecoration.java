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
import android.graphics.Rect;
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
 * Items are separated by a 4dp gap (no divider lines).
 */
public class CardGroupItemDecoration extends BaseCardItemDecoration {

    private final int mGroupTopMargin;
    private final int mGroupBottomMargin;

    public CardGroupItemDecoration(Context ctx) {
        super(resolveCardColor(ctx),
                ctx.getResources().getDimension(R.dimen.settings_card_corner_radius),
                ctx.getResources().getDimension(R.dimen.settings_card_small_corner_radius),
                ctx.getResources().getDimensionPixelSize(R.dimen.settings_horizontal_margin),
                ctx.getResources().getDimensionPixelSize(R.dimen.settings_item_gap),
                Themes.getAttrColor(ctx, android.R.attr.colorControlHighlight),
                R.id.card_ripple_tag);
        Resources res = ctx.getResources();
        mGroupTopMargin = res.getDimensionPixelSize(R.dimen.settings_item_gap);
        mGroupBottomMargin = res.getDimensionPixelSize(R.dimen.settings_item_gap);
    }

    private static int resolveCardColor(Context ctx) {
        boolean isDark = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return Themes.getAttrColor(ctx, isDark
                ? com.google.android.material.R.attr.colorSurfaceContainer
                : com.google.android.material.R.attr.colorSurface);
    }

    @Override
    protected boolean shouldSkipItem(RecyclerView parent, View child, int pos) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return true;
        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;
        if (pga.getItem(pos) instanceof PreferenceCategory) return true;
        return "no_card".equals(child.getTag());
    }

    @Override
    protected boolean isFirstInGroup(RecyclerView parent, int pos, int totalItems) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return false;
        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;
        return (pos == 0)
                || (pga.getItem(pos - 1) instanceof PreferenceCategory)
                || isNoCardItem(parent, pos - 1);
    }

    @Override
    protected boolean isLastInGroup(RecyclerView parent, int pos, int totalItems) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return false;
        PreferenceGroupAdapter pga = (PreferenceGroupAdapter) adapter;
        return (pos == totalItems - 1)
                || (pos + 1 < totalItems
                    && pga.getItem(pos + 1) instanceof PreferenceCategory)
                || (pos + 1 < totalItems
                    && isNoCardItem(parent, pos + 1));
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
            outRect.left = mHorizontalMargin;
            outRect.right = mHorizontalMargin;
            return;
        }

        if ("no_card".equals(view.getTag())) return;

        outRect.left = mHorizontalMargin;
        outRect.right = mHorizontalMargin;

        int totalItems = pga.getItemCount();
        boolean isFirst = isFirstInGroup(parent, pos, totalItems);
        boolean isLast = isLastInGroup(parent, pos, totalItems);

        if (isFirst) {
            outRect.top = mGroupTopMargin;
        } else {
            outRect.top = mItemGap;
        }

        if (isLast) {
            outRect.bottom = mGroupBottomMargin;
        }
    }

    /** Check whether the item at adapter position {@code pos} has the "no_card" tag. */
    private boolean isNoCardItem(RecyclerView parent, int pos) {
        RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(pos);
        return vh != null && "no_card".equals(vh.itemView.getTag());
    }
}
