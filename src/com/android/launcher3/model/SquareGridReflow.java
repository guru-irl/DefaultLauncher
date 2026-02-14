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
package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CELLX;
import static com.android.launcher3.LauncherSettings.Favorites.CELLY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.SCREEN;
import static com.android.launcher3.LauncherSettings.Favorites.SPANX;
import static com.android.launcher3.LauncherSettings.Favorites.SPANY;
import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites._ID;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.GridOccupancy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles in-place database reflow when square grid columns decrease.
 *
 * When the user decreases the column count, items that no longer fit at their
 * current positions are rearranged (moved to available cells or overflow screens)
 * rather than being deleted by {@code checkItemPlacement()}.
 */
public class SquareGridReflow {

    private static final String TAG = "SquareGridReflow";

    /** Lightweight representation of a favorites row. */
    private static class ReflowEntry {
        final long id;
        int screenId;
        int cellX;
        int cellY;
        int spanX;
        int spanY;
        boolean moved;

        ReflowEntry(Cursor c) {
            id = c.getLong(c.getColumnIndexOrThrow(_ID));
            screenId = c.getInt(c.getColumnIndexOrThrow(SCREEN));
            cellX = c.getInt(c.getColumnIndexOrThrow(CELLX));
            cellY = c.getInt(c.getColumnIndexOrThrow(CELLY));
            spanX = c.getInt(c.getColumnIndexOrThrow(SPANX));
            spanY = c.getInt(c.getColumnIndexOrThrow(SPANY));
        }

        boolean fitsInGrid(int cols, int rows) {
            return cellX + spanX <= cols && cellY + spanY <= rows;
        }
    }

    /**
     * Checks whether a reflow is needed and performs it if so.
     * Must be called on a worker thread before items are loaded.
     */
    public static void reflowIfNeeded(Context context, SQLiteDatabase db,
            InvariantDeviceProfile idp) {
        DeviceGridState persisted = new DeviceGridState(context);
        DeviceGridState current = new DeviceGridState(idp);

        int oldCols = persisted.getColumns();
        int newCols = current.getColumns();

        // First load or empty state: just save and return.
        if (oldCols <= 0) {
            current.writeToPrefs(context);
            Log.d(TAG, "reflowIfNeeded: first load, saving initial state " + current);
            return;
        }

        // Always persist the current state so we don't get stale.
        current.writeToPrefs(context);

        if (newCols >= oldCols) {
            Log.d(TAG, "reflowIfNeeded: columns same or increased ("
                    + oldCols + " -> " + newCols + "), no reflow needed");
            return;
        }

        // Use the maximum visible rows across all supported profiles.
        // DeviceProfile.deriveSquareGridRows() has already run by this point.
        int visibleRows = getMaxVisibleRows(idp);

        Log.d(TAG, "reflowIfNeeded: columns decreased " + oldCols + " -> " + newCols
                + ", visibleRows=" + visibleRows + ", starting reflow");
        reflow(context, db, newCols, visibleRows);
    }

    /**
     * Returns the maximum visible row count across all supported device profiles.
     * Falls back to idp.numRows (DB capacity) if no profiles are available.
     */
    private static int getMaxVisibleRows(InvariantDeviceProfile idp) {
        int maxRows = 0;
        if (idp.supportedProfiles != null) {
            for (DeviceProfile dp : idp.supportedProfiles) {
                maxRows = Math.max(maxRows, dp.numRows);
            }
        }
        return maxRows > 0 ? maxRows : idp.numRows;
    }

    /**
     * Rearranges workspace items so they fit within {@code newCols} columns
     * and {@code numRows} visible rows.
     * Items that don't fit at their current position are moved to vacant cells
     * on the same screen, or to newly created overflow screens.
     */
    private static void reflow(Context context, SQLiteDatabase db, int newCols, int numRows) {
        // Determine if screen 0 has smartspace reserving row 0.
        boolean reserveSmartspace = FeatureFlags.QSB_ON_FIRST_SCREEN
                && (!Flags.enableSmartspaceRemovalToggle()
                    || LauncherPrefs.getPrefs(context).getBoolean(
                        LoaderTask.SMARTSPACE_ON_HOME_SCREEN, true))
                && !Utilities.SHOULD_SHOW_FIRST_PAGE_WIDGET;

        // Load all desktop items.
        List<ReflowEntry> allItems = new ArrayList<>();
        try (Cursor c = db.query(TABLE_NAME, null,
                CONTAINER + " = " + CONTAINER_DESKTOP, null, null, null, null)) {
            while (c.moveToNext()) {
                allItems.add(new ReflowEntry(c));
            }
        }

        if (allItems.isEmpty()) {
            Log.d(TAG, "reflow: no desktop items, nothing to do");
            return;
        }

        // Clamp spans that exceed the new grid dimensions.
        // Always clamp rather than drop — a slightly undersized widget is better than a
        // deleted one.  The user can manually resize afterwards if needed.
        for (ReflowEntry entry : allItems) {
            if (entry.spanX > newCols) {
                Log.d(TAG, "reflow: clamping id=" + entry.id
                        + " spanX " + entry.spanX + " -> " + newCols);
                entry.spanX = newCols;
                entry.cellX = 0; // must start at column 0 to fit full width
                entry.moved = true;
            }
            if (entry.spanY > numRows) {
                int clampedY = Math.max(numRows, 1);
                Log.d(TAG, "reflow: clamping id=" + entry.id
                        + " spanY " + entry.spanY + " -> " + clampedY);
                entry.spanY = clampedY;
                entry.moved = true;
            }
        }

        // Group by screen, preserving insertion order.
        LinkedHashMap<Integer, List<ReflowEntry>> byScreen = new LinkedHashMap<>();
        int maxScreenId = -1;
        for (ReflowEntry entry : allItems) {
            byScreen.computeIfAbsent(entry.screenId, k -> new ArrayList<>()).add(entry);
            maxScreenId = Math.max(maxScreenId, entry.screenId);
        }

        List<ReflowEntry> globalOverflow = new ArrayList<>();

        // Pass 1 & 2: per-screen placement.
        for (Map.Entry<Integer, List<ReflowEntry>> screenEntry : byScreen.entrySet()) {
            int screenId = screenEntry.getKey();
            List<ReflowEntry> items = screenEntry.getValue();
            int startRow = (screenId == 0 && reserveSmartspace) ? 1 : 0;

            GridOccupancy grid = new GridOccupancy(newCols, numRows);
            // Reserve smartspace row if needed.
            if (startRow > 0) {
                grid.markCells(0, 0, newCols, startRow, true);
            }

            List<ReflowEntry> overflow = new ArrayList<>();

            // Pass 1: place items that still fit at their original position.
            for (ReflowEntry entry : items) {
                if (entry.fitsInGrid(newCols, numRows)
                        && grid.isRegionVacant(entry.cellX, entry.cellY,
                                entry.spanX, entry.spanY)) {
                    grid.markCells(entry.cellX, entry.cellY, entry.spanX, entry.spanY, true);
                } else {
                    overflow.add(entry);
                }
            }

            // Pass 2: try to place overflow items on the same screen.
            List<ReflowEntry> stillOverflow = new ArrayList<>();
            int[] vacant = new int[2];
            for (ReflowEntry entry : overflow) {
                if (grid.findVacantCell(vacant, entry.spanX, entry.spanY)) {
                    entry.cellX = vacant[0];
                    entry.cellY = vacant[1];
                    entry.moved = true;
                    grid.markCells(entry.cellX, entry.cellY, entry.spanX, entry.spanY, true);
                } else {
                    stillOverflow.add(entry);
                }
            }

            globalOverflow.addAll(stillOverflow);
        }

        // Pass 3: place global overflow items on new screens.
        int nextScreenId = maxScreenId + 1;
        while (!globalOverflow.isEmpty()) {
            GridOccupancy grid = new GridOccupancy(newCols, numRows);
            List<ReflowEntry> nextRound = new ArrayList<>();
            int[] vacant = new int[2];

            for (ReflowEntry entry : globalOverflow) {
                if (grid.findVacantCell(vacant, entry.spanX, entry.spanY)) {
                    entry.screenId = nextScreenId;
                    entry.cellX = vacant[0];
                    entry.cellY = vacant[1];
                    entry.moved = true;
                    grid.markCells(entry.cellX, entry.cellY, entry.spanX, entry.spanY, true);
                } else {
                    nextRound.add(entry);
                }
            }

            if (nextRound.size() == globalOverflow.size()) {
                // Safety: nothing could be placed (shouldn't happen for 1x1 items).
                Log.e(TAG, "reflow: " + nextRound.size()
                        + " items could not be placed, giving up");
                break;
            }

            nextScreenId++;
            globalOverflow = nextRound;
        }

        // Write changes to DB in a single transaction.
        int movedCount = 0;
        db.beginTransaction();
        try {
            for (ReflowEntry entry : allItems) {
                if (!entry.moved) continue;
                ContentValues values = new ContentValues();
                values.put(CELLX, entry.cellX);
                values.put(CELLY, entry.cellY);
                values.put(SPANX, entry.spanX);
                values.put(SPANY, entry.spanY);
                values.put(SCREEN, entry.screenId);
                db.update(TABLE_NAME, values, _ID + " = ?",
                        new String[]{String.valueOf(entry.id)});
                movedCount++;
            }
            db.setTransactionSuccessful();
            Log.d(TAG, "reflow: moved " + movedCount + " items");
        } finally {
            db.endTransaction();
        }
    }
}
