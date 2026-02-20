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
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.DynamicColors;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsColorResolver;
import com.android.launcher3.folder.FolderCoverManager;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;

import java.util.ArrayList;
import java.util.List;
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

    /** Returns the default cover background color. */
    public static int getDefaultCoverBgColor(Context ctx) {
        return ctx.getColor(DEFAULT_COVER_BG_COLOR_RES);
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
     * Shared bottom sheet builder for per-folder shape pickers. Both expanded-shape
     * and icon-shape dialogs are ~90% identical; this extracts the common layout code.
     *
     * @param titleResId   string resource for the sheet title
     * @param defaultLabel label for the "use default" first row
     * @param current      currently selected shape key (null/empty = default)
     * @param onSelect     called with the selected shape key
     * @param onDefault    called when the "use default" row is tapped
     */
    private static void showShapeDialogInternal(Context ctx, int titleResId,
            CharSequence defaultLabel, String current,
            Consumer<String> onSelect, Runnable onDefault) {
        ShapeDialogConfig config = buildShapeListNoDefault(ctx);

        Context themed = new ContextThemeWrapper(ctx, R.style.HomeSettings_Theme);
        themed = DynamicColors.wrapContextIfAvailable(themed);
        Resources res = themed.getResources();
        BottomSheetDialog sheet = new BottomSheetDialog(themed);

        LinearLayout root = new LinearLayout(themed);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal));

        IconSettingsHelper.addSheetHandle(root, themed, res);

        TextView titleView = new TextView(themed);
        titleView.setText(titleResId);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                res.getDimension(R.dimen.settings_sheet_title_text_size));
        titleView.setTextColor(themed.getColor(R.color.materialColorOnSurface));
        titleView.setPadding(
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical),
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical));
        root.addView(titleView);

        LinearLayout itemsContainer = new LinearLayout(themed);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);

        int previewSizePx = res.getDimensionPixelSize(R.dimen.settings_shape_preview_size);
        int colorFill = themed.getColor(R.color.materialColorSurfaceContainerHighest);

        // Default entry
        addShapeRow(themed, res, itemsContainer, "", "", defaultLabel,
                (current == null || current.isEmpty()), previewSizePx, colorFill,
                key -> { onDefault.run(); sheet.dismiss(); });

        for (int i = 0; i < config.keys.size(); i++) {
            final String key = config.keys.get(i);
            String pathStr = config.folderPaths.get(i);
            CharSequence label = config.labels.get(i);
            boolean isSelected = key.equals(current != null ? current : "");

            addShapeRow(themed, res, itemsContainer, key, pathStr, label, isSelected,
                    previewSizePx, colorFill, selectedKey -> {
                        onSelect.accept(selectedKey);
                        sheet.dismiss();
                    });
        }

        NestedScrollView scroll = new NestedScrollView(themed);
        scroll.addView(itemsContainer);
        root.addView(scroll);

        sheet.setContentView(root);
        sheet.show();
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

    /** Data holder for shape dialog entries. */
    private static class ShapeDialogConfig {
        final List<CharSequence> labels = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        final List<String> folderPaths = new ArrayList<>();
    }

    /** Build shape list without default entry (for per-folder pickers). */
    private static ShapeDialogConfig buildShapeListNoDefault(Context ctx) {
        ShapeDialogConfig config = new ShapeDialogConfig();
        for (IconShapeModel shape : ShapesProvider.INSTANCE.getIconShapes()) {
            if (ShapesProvider.NONE_KEY.equals(shape.getKey())) continue;
            config.labels.add(IconSettingsHelper.getShapeDisplayName(ctx, shape.getKey()));
            config.keys.add(shape.getKey());
            config.folderPaths.add(shape.getPathString());
        }
        return config;
    }

    /** Add a single shape row to the container. */
    private static void addShapeRow(Context ctx, Resources res, LinearLayout container,
            String key, String pathStr, CharSequence label, boolean isSelected,
            int previewSizePx, int colorFill,
            Consumer<String> onSelect) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(res.getDimensionPixelSize(R.dimen.settings_row_min_height));
        row.setPadding(res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                0, res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal), 0);
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        // Shape preview or spacer
        if (pathStr != null && !pathStr.isEmpty()) {
            ImageView preview = new ImageView(ctx);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                    previewSizePx, previewSizePx);
            previewLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_card_padding));
            preview.setLayoutParams(previewLp);
            preview.setImageDrawable(new IconSettingsHelper.ShapePreviewDrawable(
                    pathStr, previewSizePx, colorFill));
            row.addView(preview);
        } else {
            View spacer = new View(ctx);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                    previewSizePx, previewSizePx);
            spacerLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_card_padding));
            spacer.setLayoutParams(spacerLp);
            row.addView(spacer);
        }

        TextView nameView = new TextView(ctx);
        nameView.setText(label);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        nameView.setTextColor(isSelected
                ? ctx.getColor(R.color.materialColorPrimary)
                : ctx.getColor(R.color.materialColorOnSurface));
        row.addView(nameView);

        row.setOnClickListener(v -> onSelect.accept(key));
        container.addView(row);
    }
}
