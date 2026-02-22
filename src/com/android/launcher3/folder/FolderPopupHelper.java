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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
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

        FolderCoverManager coverMgr = FolderCoverManager.getInstance(launcher);
        boolean hasCover = coverMgr.getCover(info.id) != null;

        // Custom cover icon (dynamic label: "Set cover" / "Change cover")
        shortcuts.add(new CustomCover(launcher, info, anchor, folderIcon, hasCover));

        // Remove cover — only when a cover is set
        if (hasCover) {
            shortcuts.add(new RemoveCover(launcher, info, anchor, folderIcon));
        }

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
        // Position popup relative to the FolderIcon view (not the BubbleTextView label)
        container.setPositionAnchor(folderIcon);
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

    static class CustomCover extends SystemShortcut<Launcher> {

        private final FolderIcon mFolderIcon;
        private final int mLabelRes;

        CustomCover(Launcher target, ItemInfo itemInfo, View originalView,
                FolderIcon folderIcon, boolean hasCover) {
            super(R.drawable.ic_apps, R.string.folder_set_cover,
                    target, itemInfo, originalView);
            mFolderIcon = folderIcon;
            mLabelRes = hasCover ? R.string.folder_change_cover : R.string.folder_set_cover;
        }

        @Override
        public void setIconAndLabelFor(View iconView, TextView labelView) {
            iconView.setBackground(createEmojiDrawable(iconView.getContext()));
            iconView.setBackgroundTintList(null);
            labelView.setText(mLabelRes);
        }

        @Override
        public void setIconAndContentDescriptionFor(ImageView view) {
            view.setImageDrawable(createEmojiDrawable(view.getContext()));
            view.setImageTintList(null);
            view.setContentDescription(view.getContext().getText(mLabelRes));
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

    static class RemoveCover extends SystemShortcut<Launcher> {

        private final FolderIcon mFolderIcon;

        RemoveCover(Launcher target, ItemInfo itemInfo, View originalView,
                FolderIcon folderIcon) {
            super(R.drawable.ic_remove_no_shadow, R.string.folder_cover_remove,
                    target, itemInfo, originalView);
            mFolderIcon = folderIcon;
        }

        @Override
        public void onClick(View view) {
            long folderId = mFolderIcon.mInfo.id;
            FolderCoverManager.getInstance(mTarget).removeCover(folderId);
            mFolderIcon.updateCoverDrawable();
            mFolderIcon.invalidate();
            AbstractFloatingView.closeAllOpenViews(mTarget);
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
}
