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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Represents a stack of widgets occupying a single grid cell group.
 * Users can swipe left/right to cycle through the stacked widgets.
 */
public class WidgetStackInfo extends CollectionInfo {

    /** Maximum number of widgets that can be stacked together. */
    public static final int MAX_STACK_SIZE = 6;

    private final ArrayList<ItemInfo> contents = new ArrayList<>();
    private int activeIndex = 0;

    public WidgetStackInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_WIDGET_STACK;
    }

    @Override
    public void add(@NonNull ItemInfo item) {
        if (!willAcceptItemType(item.itemType)) {
            throw new RuntimeException("tried to add a non-widget type into a widget stack");
        }
        if (contents.size() >= MAX_STACK_SIZE) {
            throw new RuntimeException("widget stack is full (max " + MAX_STACK_SIZE + ")");
        }
        contents.add(item);
    }

    @NonNull
    @Override
    public ArrayList<ItemInfo> getContents() {
        return contents;
    }

    @Override
    public ArrayList<WorkspaceItemInfo> getAppContents() {
        return new ArrayList<>();
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int i) {
        if (contents.isEmpty()) {
            activeIndex = 0;
            return;
        }
        activeIndex = Math.max(0, Math.min(i, contents.size() - 1));
    }

    /**
     * Returns the active widget, or {@code null} if the stack is empty or the active
     * item is not a {@link LauncherAppWidgetInfo}.
     */
    @Nullable
    public LauncherAppWidgetInfo getActiveWidget() {
        if (contents.isEmpty()) return null;
        ItemInfo item = contents.get(activeIndex);
        return item instanceof LauncherAppWidgetInfo lawi ? lawi : null;
    }

    public int getWidgetCount() {
        return contents.size();
    }

    /**
     * Removes all items from this stack that match the given predicate and clamps the
     * active index so it stays within bounds.
     *
     * @return the number of items removed
     */
    public int removeMatching(Predicate<ItemInfo> matcher) {
        int removed = 0;
        for (int i = contents.size() - 1; i >= 0; i--) {
            if (matcher.test(contents.get(i))) {
                contents.remove(i);
                removed++;
            }
        }
        if (!contents.isEmpty()) {
            activeIndex = Math.max(0, Math.min(activeIndex, contents.size() - 1));
        } else {
            activeIndex = 0;
        }
        return removed;
    }

    /**
     * Sorts contents by rank to ensure consistent ordering.
     */
    public void sortByRank() {
        contents.sort(Comparator.comparingInt(a -> a.rank));
    }

    /**
     * Assigns a widget into this stack at the given rank. Sets all container/position
     * fields but does NOT persist to the database — callers are responsible for calling
     * the appropriate {@link ModelWriter} method after this returns.
     */
    public void assignWidget(LauncherAppWidgetInfo widget, int rank) {
        widget.container = this.id;
        widget.screenId = this.screenId;
        widget.cellX = -1;
        widget.cellY = -1;
        widget.rank = rank;
        add(widget);
    }

    /**
     * Checks if {@code itemType} is a widget type that can be placed in stacks.
     */
    public static boolean willAcceptItemType(int itemType) {
        return itemType == ITEM_TYPE_APPWIDGET || itemType == ITEM_TYPE_CUSTOM_APPWIDGET;
    }

    @Override
    public void onAddToDatabase(@NonNull ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.OPTIONS, activeIndex);
    }

    @NonNull
    @Override
    public ItemInfo makeShallowCopy() {
        WidgetStackInfo copy = new WidgetStackInfo();
        copy.copyFrom(this);
        return copy;
    }

    @Override
    public void copyFrom(@NonNull ItemInfo info) {
        super.copyFrom(info);
        if (info instanceof WidgetStackInfo wsi) {
            contents.addAll(wsi.getContents());
            activeIndex = wsi.activeIndex;
        }
    }
}
