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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;

/**
 * RecyclerView adapter for the widget stack editor. Displays each widget
 * as an M3 two-line list item with widget preview thumbnail, app icon badge,
 * widget label, app name, drag handle, and remove button.
 *
 * <p>The last item is an "Add widget" action row (leading {@code +} icon,
 * single-line text, no drag handle or remove button). It is not draggable.
 */
public class WidgetStackEditorAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_WIDGET = 0;
    static final int TYPE_ADD = 1;

    /** Callback for widget removal from the editor. */
    interface OnRemoveListener {
        void onRemoveWidget(int position);
    }

    /** Callback for the "Add widget" action. */
    interface OnAddListener {
        void onAddWidget();
    }

    private final WidgetStackInfo mStackInfo;
    private final WidgetManagerHelper mWidgetManager;
    private final PackageManager mPackageManager;
    private ItemTouchHelper mItemTouchHelper;
    private OnRemoveListener mRemoveListener;
    private OnAddListener mAddListener;
    private boolean mShowAddRow = true;

    WidgetStackEditorAdapter(Context context, WidgetStackInfo stackInfo) {
        mStackInfo = stackInfo;
        mWidgetManager = new WidgetManagerHelper(context);
        mPackageManager = context.getPackageManager();
    }

    void setItemTouchHelper(ItemTouchHelper helper) {
        mItemTouchHelper = helper;
    }

    void setOnRemoveListener(OnRemoveListener listener) {
        mRemoveListener = listener;
    }

    void setOnAddListener(OnAddListener listener) {
        mAddListener = listener;
    }

    /** Updates visibility of the "Add widget" row (hidden when stack is full). */
    void setShowAddRow(boolean show) {
        if (mShowAddRow == show) return;
        int addPos = mStackInfo.getContents().size();
        mShowAddRow = show;
        if (show) {
            notifyItemInserted(addPos);
        } else {
            notifyItemRemoved(addPos);
        }
    }

    /** Returns the number of widget items (excluding the add row). */
    int getWidgetCount() {
        return mStackInfo.getContents().size();
    }

    /** Returns true if the position is the "Add widget" row. */
    boolean isAddRow(int position) {
        return mShowAddRow && position == mStackInfo.getContents().size();
    }

    @Override
    public int getItemViewType(int position) {
        return isAddRow(position) ? TYPE_ADD : TYPE_WIDGET;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            View view = inflater.inflate(R.layout.widget_stack_editor_item, parent, false);
            return new AddViewHolder(view);
        }
        View view = inflater.inflate(R.layout.widget_stack_editor_item, parent, false);
        return new WidgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddViewHolder addHolder) {
            bindAddRow(addHolder);
        } else if (holder instanceof WidgetViewHolder widgetHolder) {
            bindWidgetRow(widgetHolder, position);
        }
    }

    private void bindAddRow(AddViewHolder holder) {
        holder.widgetLabel.setText(R.string.widget_stack_add_widget);
        holder.appName.setVisibility(View.GONE);
        holder.appIconBadge.setVisibility(View.GONE);
        holder.dragHandle.setVisibility(View.GONE);
        holder.removeButton.setVisibility(View.GONE);

        // Shrink the row: M3 single-line list item (56dp min, 8dp vertical padding)
        holder.itemView.setMinimumHeight(dp(holder, 56));
        holder.itemView.setPadding(
                dp(holder, 16), dp(holder, 8), dp(holder, 12), dp(holder, 8));

        // Shrink preview frame to 40dp circle with + icon
        ViewGroup.LayoutParams previewFrameParams =
                ((View) holder.preview.getParent()).getLayoutParams();
        previewFrameParams.width = dp(holder, 40);
        previewFrameParams.height = dp(holder, 40);

        holder.preview.setImageResource(R.drawable.ic_add_widget);
        holder.preview.setScaleType(ImageView.ScaleType.CENTER);
        holder.preview.setImageTintList(android.content.res.ColorStateList.valueOf(
                holder.itemView.getContext().getColor(R.color.materialColorPrimary)));
        holder.preview.setBackground(null);
        holder.preview.setClipToOutline(false);

        holder.itemView.setOnClickListener(v -> {
            if (mAddListener != null) mAddListener.onAddWidget();
        });
    }

    private void bindWidgetRow(WidgetViewHolder holder, int position) {
        ItemInfo item = mStackInfo.getContents().get(position);

        String appName = "";
        String widgetLabel = "";
        Drawable appIcon = null;
        Drawable widgetPreview = null;

        if (item instanceof LauncherAppWidgetInfo wInfo && wInfo.providerName != null) {
            // App name and icon
            try {
                ApplicationInfo appInfo = mPackageManager.getApplicationInfo(
                        wInfo.providerName.getPackageName(), 0);
                appName = mPackageManager.getApplicationLabel(appInfo).toString();
                appIcon = mPackageManager.getApplicationIcon(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                appName = wInfo.providerName.getPackageName();
            }

            // Widget label and preview
            LauncherAppWidgetProviderInfo pInfo = mWidgetManager.getLauncherAppWidgetInfo(
                    wInfo.appWidgetId, wInfo.providerName);
            if (pInfo != null) {
                CharSequence label = pInfo.getLabel();
                if (label != null) widgetLabel = label.toString();
                widgetPreview = pInfo.loadPreviewImage(
                        holder.itemView.getContext(), 0);
            }
        }

        holder.widgetLabel.setText(widgetLabel);
        holder.appName.setVisibility(View.VISIBLE);
        holder.appName.setText(appName);

        // Widget preview thumbnail
        if (widgetPreview != null) {
            holder.preview.setImageDrawable(widgetPreview);
            holder.preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.preview.setImageTintList(null);
            holder.preview.setPadding(0, 0, 0, 0);
        } else if (appIcon != null) {
            // No declared preview — show app icon centered in the preview area
            holder.preview.setImageDrawable(appIcon);
            holder.preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.preview.setImageTintList(null);
            holder.preview.setPadding(dp(holder, 8), dp(holder, 8),
                    dp(holder, 8), dp(holder, 8));
        } else {
            holder.preview.setImageDrawable(null);
            holder.preview.setImageTintList(null);
            holder.preview.setPadding(0, 0, 0, 0);
        }

        // App icon badge — visible only when a separate preview is shown
        if (appIcon != null && widgetPreview != null) {
            holder.appIconBadge.setImageDrawable(appIcon);
            holder.appIconBadge.setVisibility(View.VISIBLE);
        } else {
            holder.appIconBadge.setVisibility(View.GONE);
        }

        // Drag handle: start drag on touch down
        holder.dragHandle.setVisibility(View.VISIBLE);
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && mItemTouchHelper != null) {
                mItemTouchHelper.startDrag(holder);
            }
            return false;
        });

        // Remove button
        holder.removeButton.setVisibility(View.VISIBLE);
        holder.removeButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && mRemoveListener != null) {
                mRemoveListener.onRemoveWidget(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mStackInfo.getContents().size() + (mShowAddRow ? 1 : 0);
    }

    private static int dp(@NonNull RecyclerView.ViewHolder holder, int dp) {
        return Math.round(dp * holder.itemView.getResources().getDisplayMetrics().density);
    }

    static class WidgetViewHolder extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final ImageView preview;
        final ImageView appIconBadge;
        final TextView widgetLabel;
        final TextView appName;
        final ImageButton removeButton;

        WidgetViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.widget_stack_editor_drag_handle);
            preview = itemView.findViewById(R.id.widget_stack_editor_preview);
            appIconBadge = itemView.findViewById(R.id.widget_stack_editor_app_icon);
            widgetLabel = itemView.findViewById(R.id.widget_stack_editor_widget_label);
            appName = itemView.findViewById(R.id.widget_stack_editor_app_name);
            removeButton = itemView.findViewById(R.id.widget_stack_editor_remove);
        }
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final ImageView preview;
        final ImageView appIconBadge;
        final TextView widgetLabel;
        final TextView appName;
        final ImageButton removeButton;

        AddViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.widget_stack_editor_drag_handle);
            preview = itemView.findViewById(R.id.widget_stack_editor_preview);
            appIconBadge = itemView.findViewById(R.id.widget_stack_editor_app_icon);
            widgetLabel = itemView.findViewById(R.id.widget_stack_editor_widget_label);
            appName = itemView.findViewById(R.id.widget_stack_editor_app_name);
            removeButton = itemView.findViewById(R.id.widget_stack_editor_remove);
        }
    }
}
