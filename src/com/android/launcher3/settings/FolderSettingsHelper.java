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
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.DynamicColors;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsColorResolver;
import com.android.launcher3.folder.FolderCoverManager;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;

import java.util.function.Consumer;

/**
 * Dialog helpers and resolvers for folder icon shape, icon color, and open-folder
 * background color settings. Follows the same patterns as {@link IconSettingsHelper}.
 */
public class FolderSettingsHelper {

    private static final String TAG = "FolderSettingsHelper";

    // ---- Default color resources (single source of truth) ----
    // We use M3 color resources directly so they work in both the launcher and settings contexts.

    /** Default cover bg = materialColorSurface (matches folderPreviewColor attr). */
    static final int DEFAULT_COVER_BG_COLOR_RES = R.color.materialColorSurface;

    /** Default folder bg = materialColorSurfaceContainerLow (matches drawer bg default). */
    static final int DEFAULT_FOLDER_BG_COLOR_RES = R.color.materialColorSurfaceContainerLow;

    /** Default open folder panel bg = materialColorSurfaceContainerLow (matches drawer bg default). */
    static final int DEFAULT_FOLDER_PANEL_COLOR_RES = R.color.materialColorSurfaceContainerLow;

    /** Default cover icon color = materialColorOnSurface (monochrome emoji fill). */
    static final int DEFAULT_COVER_ICON_COLOR_RES = R.color.materialColorOnSurface;

    /** Returns the default cover background color. */
    public static int getDefaultCoverBgColor(Context ctx) {
        return ctx.getColor(DEFAULT_COVER_BG_COLOR_RES);
    }

    /** Returns the default cover icon color (materialColorOnSurface). */
    public static int getDefaultCoverIconColor(Context ctx) {
        return ctx.getColor(DEFAULT_COVER_ICON_COLOR_RES);
    }

    /** Returns the default uncovered folder icon background color. */
    public static int getDefaultFolderBgColor(Context ctx) {
        return ctx.getColor(DEFAULT_FOLDER_BG_COLOR_RES);
    }

    /** Returns the default open folder panel background color. */
    public static int getDefaultFolderPanelColor(Context ctx) {
        return ctx.getColor(DEFAULT_FOLDER_PANEL_COLOR_RES);
    }

    // ---- Resolvers (called at draw time) ----

    /**
     * Resolves the cover-specific background color from prefs.
     * Used for folders that have a custom cover icon set.
     * @return the color int, or 0 to use theme default.
     */
    public static int resolveFolderCoverBgColor(Context ctx) {
        String key = LauncherPrefs.get(ctx).get(LauncherPrefs.FOLDER_COVER_BG_COLOR);
        return AllAppsColorResolver.resolveColorByName(ctx, key);
    }

    /**
     * Resolves the folder background opacity (0-100).
     * Applied to uncovered folder icons and the open folder panel.
     */
    public static int resolveFolderBgOpacity(Context ctx) {
        return LauncherPrefs.get(ctx).get(LauncherPrefs.FOLDER_BG_OPACITY);
    }

    /**
     * Resolves the folder icon shape from prefs.
     * Uses ShapesProvider's folderPathString and ShapeDelegate.pickBestShape.
     * @return a ShapeDelegate, or null to use the ThemeManager default.
     */
    public static ShapeDelegate resolveFolderIconShape(Context ctx) {
        String key = LauncherPrefs.get(ctx).get(LauncherPrefs.FOLDER_ICON_SHAPE);
        return resolveShapeKey(key);
    }

    /**
     * Resolves the open folder background color from prefs.
     * Uses the full tonal palette via AllAppsColorResolver.
     * @return the color int, or 0 to use theme default.
     */
    public static int resolveFolderBgColor(Context ctx) {
        String key = LauncherPrefs.get(ctx).get(LauncherPrefs.FOLDER_BG_COLOR);
        return AllAppsColorResolver.resolveColorByName(ctx, key);
    }

    // ---- Effective colors (centralized resolve + fallback + opacity) ----

    /**
     * Resolves the cover icon color (emoji tint) from prefs.
     * @return the color int, or 0 to use theme default (onSurface).
     */
    public static int resolveFolderCoverIconColor(Context ctx) {
        String key = LauncherPrefs.get(ctx).get(LauncherPrefs.FOLDER_COVER_ICON_COLOR);
        return AllAppsColorResolver.resolveColorByName(ctx, key);
    }

    /** Returns the effective cover icon color (custom or materialColorOnSurface). */
    public static int getEffectiveCoverIconColor(Context ctx) {
        int color = resolveFolderCoverIconColor(ctx);
        return color != 0 ? color : getDefaultCoverIconColor(ctx);
    }

    /** Returns the effective cover background color (custom or default). */
    public static int getEffectiveCoverBgColor(Context ctx) {
        int color = resolveFolderCoverBgColor(ctx);
        return color != 0 ? color : getDefaultCoverBgColor(ctx);
    }

    /** Returns the effective folder icon background color with opacity applied. */
    public static int getEffectiveFolderBgColor(Context ctx) {
        int color = resolveFolderBgColor(ctx);
        if (color == 0) color = getDefaultFolderBgColor(ctx);
        return applyBgOpacity(ctx, color);
    }

    /** Returns the effective open folder panel color with opacity applied. */
    public static int getEffectivePanelColor(Context ctx) {
        int color = resolveFolderBgColor(ctx);
        if (color == 0) color = getDefaultFolderPanelColor(ctx);
        return applyBgOpacity(ctx, color);
    }

    private static int applyBgOpacity(Context ctx, int color) {
        int opacity = resolveFolderBgOpacity(ctx);
        if (opacity < 100) {
            color = ColorUtils.setAlphaComponent(color, Math.round(opacity / 100f * 255));
        }
        return color;
    }

    /**
     * Resolves a per-folder expanded shape, falling back to the default RoundedSquare.
     * @param ctx context
     * @param folderId the folder's ID
     * @return a ShapeDelegate (never null; defaults to RoundedSquare).
     */
    public static ShapeDelegate resolveExpandedFolderShape(Context ctx, long folderId) {
        String key = FolderCoverManager.getInstance(ctx).getExpandedShape(folderId);
        ShapeDelegate shape = resolveShapeKey(key);
        return shape != null ? shape : new ShapeDelegate.RoundedSquare(0.25f);
    }

    // ---- Dialogs ----

    /**
     * Shows a bottom sheet for selecting the expanded folder shape for a specific folder.
     * Selection is stored per-folder via FolderCoverManager.
     */
    public static void showExpandedShapeDialogForFolder(Context ctx, long folderId,
            FolderIcon folderIcon, Runnable onChanged) {
        String current = FolderCoverManager.getInstance(ctx).getExpandedShape(folderId);
        showShapeDialogInternal(ctx, R.string.folder_expanded_shape_title,
                ctx.getString(R.string.folder_shape_default), current,
                selectedKey -> {
                    FolderCoverManager.getInstance(ctx).setExpandedShape(folderId, selectedKey);
                    if (folderIcon != null) {
                        folderIcon.updateExpandedShape(selectedKey);
                        folderIcon.invalidate();
                    }
                    if (onChanged != null) onChanged.run();
                },
                () -> {
                    FolderCoverManager.getInstance(ctx).removeExpandedShape(folderId);
                    if (folderIcon != null) {
                        folderIcon.updateExpandedShape(null);
                        folderIcon.invalidate();
                    }
                    if (onChanged != null) onChanged.run();
                });
    }

    /**
     * Shows a bottom sheet for selecting the per-folder icon shape.
     * This shape applies to both 1x1 and expanded modes.
     * Includes a "Follow global" option that removes the per-folder override.
     */
    public static void showIconShapeDialogForFolder(Context ctx, long folderId,
            FolderIcon folderIcon, Runnable onChanged) {
        String current = FolderCoverManager.getInstance(ctx).getIconShape(folderId);
        showShapeDialogInternal(ctx, R.string.folder_icon_shape_popup,
                ctx.getString(R.string.folder_shape_follow_global), current,
                selectedKey -> {
                    FolderCoverManager.getInstance(ctx).setIconShape(folderId, selectedKey);
                    if (folderIcon != null) {
                        folderIcon.updatePerFolderShape(selectedKey);
                    }
                    if (onChanged != null) onChanged.run();
                },
                () -> {
                    FolderCoverManager.getInstance(ctx).removeIconShape(folderId);
                    if (folderIcon != null) {
                        folderIcon.updatePerFolderShape(null);
                    }
                    if (onChanged != null) onChanged.run();
                });
    }

    /**
     * Delegates to the unified shape picker in {@link IconSettingsHelper}.
     * Wraps context with M3 theme + dynamic colors since this is called
     * from the launcher context (not settings).
     */
    private static void showShapeDialogInternal(Context ctx, int titleResId,
            CharSequence defaultLabel, String current,
            Consumer<String> onSelect, Runnable onDefault) {
        Context themed = new ContextThemeWrapper(ctx, R.style.HomeSettings_Theme);
        themed = DynamicColors.wrapContextIfAvailable(themed);
        IconSettingsHelper.showShapePickerDialog(themed, titleResId, defaultLabel,
                current != null ? current : "",
                key -> {
                    if (key.isEmpty()) {
                        onDefault.run();
                    } else {
                        onSelect.accept(key);
                    }
                }, null);
    }

    // ---- Internal helpers ----

    /**
     * Maps a stored shape key to a ShapeDelegate using ShapesProvider lookup.
     * @param key the shape key stored in prefs (e.g. "circle", "squircle")
     * @return ShapeDelegate, or null for default/empty key.
     */
    public static ShapeDelegate resolveShapeKey(String key) {
        if (TextUtils.isEmpty(key)) return null;
        for (IconShapeModel shape : ShapesProvider.INSTANCE.getIconShapes()) {
            if (shape.getKey().equals(key)) {
                return ShapeDelegate.Companion.pickBestShape(shape.getPathString());
            }
        }
        return null;
    }
}
