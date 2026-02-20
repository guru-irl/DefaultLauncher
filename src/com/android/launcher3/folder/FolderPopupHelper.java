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
package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.settings.FolderCoverPickerHelper;
import com.android.launcher3.settings.FolderSettingsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to show a popup menu for folder long-press actions:
 * Expand/Collapse and Rename.
 */
public class FolderPopupHelper {

    private static final String TAG = "FolderPopupHelper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * Shows a popup with folder-specific actions.
     * @return true if popup was shown.
     */
    public static boolean showForFolder(FolderIcon folderIcon, Launcher launcher) {
        return showForFolderWithDrag(folderIcon, launcher) != null;
    }

    /**
     * Shows a popup with folder-specific actions and registers it as a drag listener
     * so the popup auto-closes when dragging starts (same pattern as app icon popups).
     * @return the popup container, or null if it couldn't be shown.
     */
    public static PopupContainerWithArrow<Launcher> showForFolderWithDrag(
            FolderIcon folderIcon, Launcher launcher) {
        if (PopupContainerWithArrow.getOpen(launcher) != null) {
            return null;
        }

        FolderInfo info = folderIcon.mInfo;
        if (info == null) return null;

        if (DEBUG) Log.d(TAG, "showForFolderWithDrag: id=" + info.id
                + " title=" + info.title
                + " spanX=" + info.spanX + " spanY=" + info.spanY
                + " container=" + info.container
                + " options=" + info.options);

        BubbleTextView anchor = folderIcon.getFolderName();
        // The popup system reads the anchor's tag to generate system shortcuts
        // (e.g. PopupContainerWithArrow.populateAndShowRows uses it for shortcut
        // creation). FolderName doesn't normally carry the FolderInfo tag, so we
        // must set it explicitly here.
        anchor.setTag(info);

        List<SystemShortcut> shortcuts = new ArrayList<>();

        // Custom cover icon
        shortcuts.add(new CustomCover(launcher, info, anchor, folderIcon));

        // Per-folder icon shape — only when collapsed, or when covered
        boolean isExpandedUncovered = folderIcon.isExpanded()
                && folderIcon.mCoverDrawable == null;
        if (!isExpandedUncovered) {
            shortcuts.add(new FolderShape(launcher, info, anchor, folderIcon));
        }

        if (shortcuts.isEmpty()) return null;

        PopupContainerWithArrow<Launcher> container =
                (PopupContainerWithArrow<Launcher>) launcher.getLayoutInflater()
                        .inflate(R.layout.popup_container, launcher.getDragLayer(), false);
        // Use the two-arg populateAndShowRows: anchor as BubbleTextView, itemInfo separate
        container.populateAndShowRows(anchor, info, 0, shortcuts);
        // Register as drag listener so popup auto-closes when drag begins
        launcher.getDragController().addDragListener(container);
        container.requestFocus();

        // Show resize handles alongside popup for workspace folders
        if (info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            View parentView = (View) folderIcon.getParent();
            if (parentView != null && parentView.getParent() instanceof CellLayout cellLayout) {
                FolderResizeFrame.showAlongsidePopup(folderIcon, cellLayout);
            }
        }

        return container;
    }

    // ---- System shortcuts ----

    static final int MAX_FOLDER_SPAN = 3;

    static class CustomCover extends SystemShortcut<Launcher> {

        private final FolderIcon mFolderIcon;

        CustomCover(Launcher target, ItemInfo itemInfo, View originalView,
                FolderIcon folderIcon) {
            super(R.drawable.ic_apps, R.string.folder_custom_cover,
                    target, itemInfo, originalView);
            mFolderIcon = folderIcon;
        }

        @Override
        public void setIconAndLabelFor(View iconView, TextView labelView) {
            iconView.setBackground(createEmojiDrawable(iconView.getContext()));
            iconView.setBackgroundTintList(null);
            labelView.setText(R.string.folder_custom_cover);
        }

        @Override
        public void setIconAndContentDescriptionFor(ImageView view) {
            view.setImageDrawable(createEmojiDrawable(view.getContext()));
            view.setImageTintList(null);
            view.setContentDescription(view.getContext().getText(R.string.folder_custom_cover));
        }

        private static Drawable createEmojiDrawable(Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            int sizePx = Math.round(24 * density);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // Monochrome Noto Emoji font + M3 onSurfaceVariant (matches other popup icons)
            android.graphics.Typeface tf = FolderCoverManager.getInstance(
                    context.getApplicationContext()).getEmojiTypeface();
            if (tf != null) paint.setTypeface(tf);
            paint.setColor(context.getColor(R.color.materialColorOnSurfaceVariant));
            paint.setTextSize(sizePx * 0.75f);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("\uD83E\uDD84", sizePx / 2f, baseline, paint);
            return new BitmapDrawable(context.getResources(), bitmap);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mFolderIcon.setVisibility(View.VISIBLE);
            FolderCoverPickerHelper.showCoverPicker(
                    mTarget, mFolderIcon, null);
        }
    }

    static class FolderShape extends SystemShortcut<Launcher> {

        private final FolderIcon mFolderIcon;

        FolderShape(Launcher target, ItemInfo itemInfo, View originalView,
                FolderIcon folderIcon) {
            super(R.drawable.ic_shapes, R.string.folder_icon_shape_popup,
                    target, itemInfo, originalView);
            mFolderIcon = folderIcon;
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mFolderIcon.setVisibility(View.VISIBLE);
            FolderSettingsHelper.showIconShapeDialogForFolder(
                    mTarget, mFolderIcon.mInfo.id, mFolderIcon, null);
        }
    }

    // ---- Expand / Collapse logic (package-private for FolderResizeFrame) ----

    private static final int RESIZE_ANIM_DURATION = 250;

    static void expandFolder(Launcher launcher, FolderIcon folderIcon, int targetSpan) {
        if (DEBUG) Log.d(TAG, "expandFolder: id=" + folderIcon.mInfo.id
                + " targetSpan=" + targetSpan);
        FolderInfo info = folderIcon.mInfo;
        View parent = (View) folderIcon.getParent();
        if (!(parent instanceof ShortcutAndWidgetContainer)) return;
        CellLayout cellLayout = (CellLayout) parent.getParent();
        if (cellLayout == null) return;

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderIcon.getLayoutParams();
        int oldCellX = lp.getCellX();
        int oldCellY = lp.getCellY();
        int oldSpan = Math.max(info.spanX, 1);

        // Capture old dimensions for animation
        int oldW = folderIcon.getWidth();
        int oldH = folderIcon.getHeight();

        // Release current occupation
        cellLayout.markCellsAsUnoccupiedForView(folderIcon);

        // Check if target span fits at current position
        boolean fits = cellLayout.isRegionVacant(oldCellX, oldCellY, targetSpan, targetSpan);
        int newCellX = oldCellX;
        int newCellY = oldCellY;

        if (!fits) {
            // Use FOLDER's pixel position to find nearest vacancy
            int[] folderPixel = new int[2];
            cellLayout.regionToCenterPoint(oldCellX, oldCellY, oldSpan, oldSpan, folderPixel);
            int[] result = new int[2];
            int[] resultSpan = new int[2];
            int[] found = cellLayout.findNearestVacantArea(
                    folderPixel[0], folderPixel[1],
                    targetSpan, targetSpan, targetSpan, targetSpan,
                    result, resultSpan);
            if (found != null && resultSpan[0] >= targetSpan
                    && resultSpan[1] >= targetSpan) {
                newCellX = result[0];
                newCellY = result[1];
                fits = true;
            }
        }

        if (!fits) {
            // Re-mark the old position and show error
            cellLayout.markCellsAsOccupiedForView(folderIcon);
            Toast.makeText(launcher, R.string.folder_no_space, Toast.LENGTH_SHORT).show();
            return;
        }

        // Update the data model
        info.options |= FolderInfo.FLAG_EXPANDED;
        info.spanX = targetSpan;
        info.spanY = targetSpan;
        info.cellX = newCellX;
        info.cellY = newCellY;

        // Update the layout params
        lp.setCellX(newCellX);
        lp.setCellY(newCellY);
        lp.cellHSpan = targetSpan;
        lp.cellVSpan = targetSpan;

        // Re-mark cells as occupied with new span
        cellLayout.markCellsAsOccupiedForView(folderIcon);

        // Persist to database
        launcher.getModelWriter().updateItemInDatabase(info);

        // Notify FolderIcon of state change
        folderIcon.updateExpandedState();

        // FIX (BUG-B): Register layout listener BEFORE requestLayout
        if (oldW > 0 && oldH > 0) {
            folderIcon.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    folderIcon.removeOnLayoutChangeListener(this);
                    int newW = right - left;
                    int newH = bottom - top;
                    if (newW > 0 && newH > 0) {
                        float startScaleX = (float) oldW / newW;
                        float startScaleY = (float) oldH / newH;
                        folderIcon.setPivotX(newW / 2f);
                        folderIcon.setPivotY(newH / 2f);
                        folderIcon.setScaleX(startScaleX);
                        folderIcon.setScaleY(startScaleY);
                        // FIX (BUG-A): Use ObjectAnimator — listeners are scoped to
                        // the animator instance, not persisted on the view
                        Interpolator interp = AnimationUtils.loadInterpolator(launcher,
                                com.android.app.animation.R.interpolator
                                        .standard_decelerate_interpolator);
                        ObjectAnimator sx = ObjectAnimator.ofFloat(
                                folderIcon, View.SCALE_X, startScaleX, 1f);
                        ObjectAnimator sy = ObjectAnimator.ofFloat(
                                folderIcon, View.SCALE_Y, startScaleY, 1f);
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(sx, sy);
                        set.setDuration(RESIZE_ANIM_DURATION);
                        set.setInterpolator(interp);
                        set.start();
                    }
                }
            });
        }

        // Request layout to resize the view (after listener is registered)
        folderIcon.requestLayout();
        cellLayout.requestLayout();
    }

    static void collapseFolder(Launcher launcher, FolderIcon folderIcon) {
        if (DEBUG) Log.d(TAG, "collapseFolder: id=" + folderIcon.mInfo.id);
        FolderInfo info = folderIcon.mInfo;
        View parent = (View) folderIcon.getParent();
        if (!(parent instanceof ShortcutAndWidgetContainer)) return;
        CellLayout cellLayout = (CellLayout) parent.getParent();
        if (cellLayout == null) return;

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) folderIcon.getLayoutParams();

        // Compute target scale from actual 1x1 cell size, not hardcoded 0.5
        int curW = folderIcon.getWidth();
        int curH = folderIcon.getHeight();
        int cellW = cellLayout.getCellWidth();
        int cellH = cellLayout.getCellHeight();
        float targetScaleX = (curW > 0 && cellW > 0) ? (float) cellW / curW : 1f;
        float targetScaleY = (curH > 0 && cellH > 0) ? (float) cellH / curH : 1f;

        if (curW > 0 && curH > 0) {
            // Pivot at (0,0) so the view shrinks toward its top-left corner,
            // which matches where the 1x1 cell will be positioned after applyCollapse.
            folderIcon.setPivotX(0f);
            folderIcon.setPivotY(0f);
            Interpolator interp = AnimationUtils.loadInterpolator(launcher,
                    com.android.app.animation.R.interpolator.standard_accelerate_interpolator);
            ObjectAnimator sx = ObjectAnimator.ofFloat(
                    folderIcon, View.SCALE_X, 1f, targetScaleX);
            ObjectAnimator sy = ObjectAnimator.ofFloat(
                    folderIcon, View.SCALE_Y, 1f, targetScaleY);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(sx, sy);
            set.setDuration(RESIZE_ANIM_DURATION);
            set.setInterpolator(interp);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    folderIcon.setScaleX(1f);
                    folderIcon.setScaleY(1f);
                    applyCollapse(launcher, folderIcon, info, cellLayout, lp);
                }
            });
            set.start();
        } else {
            applyCollapse(launcher, folderIcon, info, cellLayout, lp);
        }
    }

    private static void applyCollapse(Launcher launcher, FolderIcon folderIcon,
            FolderInfo info, CellLayout cellLayout, CellLayoutLayoutParams lp) {
        // Release current 2x2 occupation
        cellLayout.markCellsAsUnoccupiedForView(folderIcon);

        // Update the data model
        info.options &= ~FolderInfo.FLAG_EXPANDED;
        info.spanX = 1;
        info.spanY = 1;
        // Keep cellX/cellY in sync with layout params
        info.cellX = lp.getCellX();
        info.cellY = lp.getCellY();

        // Update the layout params
        lp.cellHSpan = 1;
        lp.cellVSpan = 1;

        // Re-mark cells as occupied with new span
        cellLayout.markCellsAsOccupiedForView(folderIcon);

        // Persist to database
        launcher.getModelWriter().updateItemInDatabase(info);

        // Notify FolderIcon of state change
        folderIcon.updateExpandedState();

        // Request layout to resize the view
        folderIcon.requestLayout();
        cellLayout.requestLayout();
    }
}
