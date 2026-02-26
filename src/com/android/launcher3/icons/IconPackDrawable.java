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
package com.android.launcher3.icons;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Thin marker wrapper around drawables resolved from an icon pack.
 * Allows downstream icon factories to distinguish pack-sourced icons
 * from system-sourced icons by provenance rather than Drawable type.
 *
 * Call {@link #wrap(Drawable)} at resolution time and
 * {@link #unwrap(Drawable)} at render time.
 */
public class IconPackDrawable extends DrawableWrapper {

    public IconPackDrawable(@NonNull Drawable drawable) {
        super(drawable);
    }

    /** Wrap a pack-resolved drawable. Returns null if input is null. */
    @Nullable
    public static Drawable wrap(@Nullable Drawable d) {
        if (d == null) return null;
        return new IconPackDrawable(d);
    }

    /** Unwrap to get the real drawable for rendering. */
    @NonNull
    public static Drawable unwrap(@NonNull Drawable d) {
        if (d instanceof IconPackDrawable) return ((IconPackDrawable) d).getDrawable();
        return d;
    }

    /** Check if a drawable came from an icon pack. */
    public static boolean isFromPack(@Nullable Drawable d) {
        return d instanceof IconPackDrawable;
    }
}
