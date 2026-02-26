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

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.SystemShortcut;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to show a popup menu for widget stack long-press actions:
 * Edit Stack and Remove Stack.
 * Mirrors the {@link com.android.launcher3.folder.FolderPopupHelper} pattern.
 */
public class WidgetStackPopupHelper {

    private static final String TAG = "WidgetStackPopupHelper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * Shows a popup with widget stack actions and registers it as a drag listener
     * so the popup auto-closes when dragging starts.
     * @return the popup container, or null if it couldn't be shown.
     */
    public static PopupContainerWithArrow<Launcher> showForWidgetStackWithDrag(
            WidgetStackView stackView, Launcher launcher) {
        if (PopupContainerWithArrow.getOpen(launcher) != null) {
            return null;
        }

        WidgetStackInfo info = stackView.getStackInfo();
        if (info == null) return null;

        if (DEBUG) Log.d(TAG, "showForWidgetStackWithDrag: id=" + info.id
                + " widgetCount=" + info.getWidgetCount()
                + " spanX=" + info.spanX + " spanY=" + info.spanY);

        // Create a temporary BubbleTextView as the anchor for the popup system.
        // The popup positions itself using mPositionAnchor (set to stackView below),
        // but populateAndShowRows requires a BubbleTextView reference.
        BubbleTextView anchor = new BubbleTextView(launcher);
        anchor.setTag(info);

        List<SystemShortcut> shortcuts = new ArrayList<>();

        // Edit stack — opens the full-screen stack editor
        shortcuts.add(new EditStack(launcher, info, anchor, stackView));

        // Remove stack — removes the entire stack from workspace + DB
        shortcuts.add(new RemoveStack(launcher, info, anchor, stackView));

        if (shortcuts.isEmpty()) return null;

        @SuppressWarnings("unchecked")
        PopupContainerWithArrow<Launcher> container =
                (PopupContainerWithArrow<Launcher>) launcher.getLayoutInflater()
                        .inflate(R.layout.popup_container, launcher.getDragLayer(), false);
        // Position popup relative to the WidgetStackView (not the dummy BubbleTextView)
        container.setPositionAnchor(stackView);
        container.populateAndShowRows(anchor, info, 0, shortcuts);
        // Register as drag listener so popup auto-closes when drag begins
        launcher.getDragController().addDragListener(container);
        container.requestFocus();

        return container;
    }

    // ---- System shortcuts ----

    static class EditStack extends SystemShortcut<Launcher> {

        private final WidgetStackView mStackView;

        EditStack(Launcher target, ItemInfo itemInfo, View originalView,
                WidgetStackView stackView) {
            super(R.drawable.ic_edit, R.string.widget_stack_edit,
                    target, itemInfo, originalView);
            mStackView = stackView;
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mStackView.setVisibility(View.VISIBLE);
            WidgetStackEditorView.show(mTarget, mStackView);
        }
    }

    static class RemoveStack extends SystemShortcut<Launcher> {

        private final WidgetStackView mStackView;

        RemoveStack(Launcher target, ItemInfo itemInfo, View originalView,
                WidgetStackView stackView) {
            super(R.drawable.ic_remove_no_shadow, R.string.widget_stack_remove,
                    target, itemInfo, originalView);
            mStackView = stackView;
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mStackView.setVisibility(View.VISIBLE);
            WidgetStackEditorView.removeEntireStack(mTarget, mStackView);
        }
    }
}
