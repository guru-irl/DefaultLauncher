/*
 * Copyright (C) 2026 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;

/**
 * Placeholder shown when a widget's provider app is absent (TYPE_MISSING from
 * {@link WidgetInflater}). The widget record is kept in the database; tapping
 * the × button permanently removes it (the only way a widget is ever deleted).
 *
 * <p>Recovery: once the provider app is reinstalled and the model reloads,
 * this view is replaced by the real widget view automatically — no user action
 * needed.
 *
 * <p>See docs/changes/080.
 */
public class UnavailableWidgetView extends FrameLayout {

    private final LauncherAppWidgetInfo mItem;

    public UnavailableWidgetView(
            @NonNull Context context,
            @NonNull LauncherAppWidgetInfo item,
            @Nullable Runnable onRemoveClicked) {
        super(context);
        mItem = item;
        LayoutInflater.from(context).inflate(R.layout.unavailable_widget, this, true);

        View removeBtn = findViewById(R.id.remove_unavailable_widget);
        if (onRemoveClicked != null) {
            removeBtn.setOnClickListener(v -> onRemoveClicked.run());
        } else {
            removeBtn.setVisibility(View.GONE);
        }
    }

    /** Returns the {@link LauncherAppWidgetInfo} this placeholder represents. */
    @NonNull
    public LauncherAppWidgetInfo getWidgetItem() {
        return mItem;
    }
}
