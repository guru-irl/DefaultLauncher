/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.search;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tracks which search categories are currently active (filter chips state).
 */
public class SearchFilters {

    public enum Category {
        APPS, SHORTCUTS, CONTACTS, CALENDAR, FILES, TOOLS
    }

    /** When true, ALL categories are shown (the "All" chip is selected). */
    private boolean mShowAll = true;

    /** The set of individually selected categories (used when mShowAll is false). */
    private final Set<Category> mSelected = EnumSet.noneOf(Category.class);

    /** Listener for filter changes. */
    public interface OnFilterChangedListener {
        void onFilterChanged();
    }

    private OnFilterChangedListener mListener;

    public void setOnFilterChangedListener(OnFilterChangedListener listener) {
        mListener = listener;
    }

    public boolean isShowAll() {
        return mShowAll;
    }

    public void setShowAll(boolean showAll) {
        mShowAll = showAll;
        if (showAll) {
            mSelected.clear();
        }
        notifyChanged();
    }

    public boolean isCategorySelected(Category category) {
        return mShowAll || mSelected.contains(category);
    }

    public void toggleCategory(Category category) {
        if (mShowAll) {
            // Switching from "All" to single category
            mShowAll = false;
            mSelected.clear();
            mSelected.add(category);
        } else if (mSelected.contains(category)) {
            mSelected.remove(category);
            if (mSelected.isEmpty()) {
                mShowAll = true;
            }
        } else {
            mSelected.add(category);
        }
        notifyChanged();
    }

    /** Returns a copy of selected categories for reading. */
    public Set<Category> getSelectedCategories() {
        if (mShowAll) return EnumSet.allOf(Category.class);
        return EnumSet.copyOf(mSelected);
    }

    private void notifyChanged() {
        if (mListener != null) {
            mListener.onFilterChanged();
        }
    }
}
