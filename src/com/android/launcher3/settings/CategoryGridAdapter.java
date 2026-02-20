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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic RecyclerView adapter for categorized grids in bottom sheets.
 * Used by both the emoji cover picker and per-app icon picker.
 *
 * @param <T> the item data type (e.g. String for emojis, IconEntry for icon packs)
 */
public class CategoryGridAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int VIEW_TYPE_HEADER = 0;
    static final int VIEW_TYPE_ITEM = 1;

    /**
     * A single entry in the flat list — either a category header or an item.
     */
    static class ListItem<T> {
        final int type;
        final String headerTitle;
        final T item;

        /** Create a header item. */
        ListItem(String headerTitle) {
            this.type = VIEW_TYPE_HEADER;
            this.headerTitle = headerTitle;
            this.item = null;
        }

        /** Create a data item. */
        ListItem(T item) {
            this.type = VIEW_TYPE_ITEM;
            this.headerTitle = null;
            this.item = item;
        }
    }

    /**
     * Callback interface for binding and interacting with items.
     */
    interface ItemBinder<T> {
        /** Bind item data into the ImageView cell. */
        void bind(ImageView view, T item, int position);

        /** Handle item click. */
        void onItemClick(T item);

        /** Return true if this item matches the search query. */
        boolean matchesQuery(T item, String query);

        /** Optional content description for accessibility. */
        @Nullable
        String getContentDescription(T item);
    }

    private final List<ListItem<T>> mAllItems;
    private final List<ListItem<T>> mFilteredItems;
    private final ItemBinder<T> mBinder;

    CategoryGridAdapter(List<ListItem<T>> allItems, ItemBinder<T> binder) {
        mAllItems = allItems;
        mFilteredItems = new ArrayList<>(allItems);
        mBinder = binder;
    }

    /** Replace all items and reset the filter. */
    void setItems(List<ListItem<T>> items) {
        mAllItems.clear();
        mAllItems.addAll(items);
        mFilteredItems.clear();
        mFilteredItems.addAll(items);
        notifyDataSetChanged();
    }

    /** Filter displayed items by query. Empty/null query shows all. */
    void filter(String query) {
        mFilteredItems.clear();
        if (query == null || query.trim().isEmpty()) {
            mFilteredItems.addAll(mAllItems);
        } else {
            String lowerQuery = query.trim().toLowerCase();
            String lastHeader = null;
            List<ListItem<T>> pendingItems = new ArrayList<>();

            for (ListItem<T> item : mAllItems) {
                if (item.type == VIEW_TYPE_HEADER) {
                    // Flush previous category if it had matches
                    if (lastHeader != null && !pendingItems.isEmpty()) {
                        mFilteredItems.add(new ListItem<>(lastHeader));
                        mFilteredItems.addAll(pendingItems);
                    }
                    lastHeader = item.headerTitle;
                    pendingItems = new ArrayList<>();
                } else {
                    if (mBinder.matchesQuery(item.item, lowerQuery)) {
                        pendingItems.add(item);
                    }
                }
            }
            // Flush last category
            if (lastHeader != null && !pendingItems.isEmpty()) {
                mFilteredItems.add(new ListItem<>(lastHeader));
                mFilteredItems.addAll(pendingItems);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mFilteredItems.get(position).type;
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_icon_picker_header, parent, false);
            return new HeaderHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_icon_picker, parent, false);
            return new ItemHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem<T> listItem = mFilteredItems.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(listItem.headerTitle);
        } else if (holder instanceof ItemHolder) {
            ItemHolder itemHolder = (ItemHolder) holder;
            itemHolder.image.setImageDrawable(null);
            mBinder.bind(itemHolder.image, listItem.item, position);

            String desc = mBinder.getContentDescription(listItem.item);
            if (desc != null) {
                itemHolder.itemView.setContentDescription(desc);
            }

            itemHolder.itemView.setOnClickListener(v -> mBinder.onItemClick(listItem.item));
        }
    }

    /**
     * Creates a GridLayoutManager that sizes columns to the given cell size
     * and makes header rows span the full width.
     */
    static <T> GridLayoutManager createGridLayoutManager(
            Context ctx, RecyclerView rv, int cellSizePx,
            CategoryGridAdapter<T> adapter) {
        int width = rv.getWidth();
        int spanCount = Math.max(4, width / cellSizePx);

        GridLayoutManager layoutManager = new GridLayoutManager(ctx, spanCount);
        final int finalSpanCount = spanCount;
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position < adapter.getItemCount()
                        && adapter.getItemViewType(position) == VIEW_TYPE_HEADER) {
                    return finalSpanCount;
                }
                return 1;
            }
        });
        return layoutManager;
    }

    // ---- ViewHolders ----

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.category_title);
        }
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        ItemHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.icon_image);
        }
    }
}
