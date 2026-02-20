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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.R;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.dagger.LauncherComponentProvider;

/**
 * Manages per-folder custom cover icons stored in SharedPreferences.
 * Cover icon is serialized as "packPackage|drawableName".
 *
 * <p>Future improvement: shape persistence (getExpandedShape, getIconShape, etc.) and
 * emoji rendering (renderEmoji, getEmojiTypeface) should be extracted into separate
 * classes to follow single-responsibility. Not done now to avoid a large cross-file refactor.
 */
public class FolderCoverManager {

    private static final String TAG = "FolderCoverManager";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String PREFS_FILE = "folder_covers";
    private static final String SEPARATOR = "|";

    private static final String NOTO_EMOJI_FONT = "fonts/NotoEmoji-Medium.ttf";

    private static volatile FolderCoverManager sInstance;

    private final SharedPreferences mPrefs;
    private final Context mContext;
    @Nullable private Typeface mEmojiTypeface;

    /**
     * Represents a stored cover icon: the icon pack package and drawable name.
     */
    public static class CoverIcon {
        public final String packPackage;
        public final String drawableName;

        public CoverIcon(String packPackage, String drawableName) {
            this.packPackage = packPackage;
            this.drawableName = drawableName;
        }

        public String serialize() {
            return packPackage + SEPARATOR + drawableName;
        }

        @Nullable
        public static CoverIcon deserialize(String value) {
            if (android.text.TextUtils.isEmpty(value)) return null;
            int sep = value.indexOf(SEPARATOR);
            if (sep < 0) return null;
            return new CoverIcon(value.substring(0, sep), value.substring(sep + 1));
        }
    }

    private FolderCoverManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    public static FolderCoverManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (FolderCoverManager.class) {
                if (sInstance == null) {
                    sInstance = new FolderCoverManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Gets the cover for a given folder ID.
     * @return the CoverIcon, or null if no cover is set.
     */
    @Nullable
    public CoverIcon getCover(long folderId) {
        String value = mPrefs.getString(String.valueOf(folderId), null);
        return CoverIcon.deserialize(value);
    }

    /**
     * Sets the cover for a given folder ID.
     */
    public void setCover(long folderId, CoverIcon cover) {
        if (DEBUG) Log.d(TAG, "setCover: folderId=" + folderId
                + " pack=" + cover.packPackage + " drawable=" + cover.drawableName);
        mPrefs.edit().putString(String.valueOf(folderId), cover.serialize()).apply();
    }

    /**
     * Removes the cover for a given folder ID.
     */
    public void removeCover(long folderId) {
        if (DEBUG) Log.d(TAG, "removeCover: folderId=" + folderId);
        mPrefs.edit().remove(String.valueOf(folderId)).apply();
    }

    /**
     * Gets the per-folder expanded shape key.
     * @return the shape key, or null if no per-folder shape is set.
     */
    @Nullable
    public String getExpandedShape(long folderId) {
        return mPrefs.getString("shape_" + folderId, null);
    }

    /**
     * Sets the per-folder expanded shape key.
     */
    public void setExpandedShape(long folderId, String shapeKey) {
        mPrefs.edit().putString("shape_" + folderId, shapeKey).apply();
    }

    /**
     * Removes the per-folder expanded shape.
     */
    public void removeExpandedShape(long folderId) {
        mPrefs.edit().remove("shape_" + folderId).apply();
    }

    /**
     * Gets the per-folder icon shape key (applies to both 1x1 and expanded modes).
     * @return the shape key, or null if no per-folder shape is set.
     */
    @Nullable
    public String getIconShape(long folderId) {
        return mPrefs.getString("iconshape_" + folderId, null);
    }

    /**
     * Sets the per-folder icon shape key.
     */
    public void setIconShape(long folderId, String shapeKey) {
        mPrefs.edit().putString("iconshape_" + folderId, shapeKey).apply();
    }

    /**
     * Removes the per-folder icon shape.
     */
    public void removeIconShape(long folderId) {
        mPrefs.edit().remove("iconshape_" + folderId).apply();
    }

    private static final String EMOJI_PREFIX = "emoji";

    /**
     * Loads the cover drawable for a given folder ID.
     * Supports emoji covers (prefix "emoji|") and icon pack covers.
     * @return the Drawable, or null if no cover is set or unavailable.
     */
    @Nullable
    public Drawable loadCoverDrawable(long folderId) {
        CoverIcon cover = getCover(folderId);
        if (DEBUG) Log.d(TAG, "loadCoverDrawable: folderId=" + folderId
                + " hasCover=" + (cover != null)
                + (cover != null ? " pack=" + cover.packPackage + " name=" + cover.drawableName : ""));
        if (cover == null) return null;

        // Emoji cover: "emoji|😀"
        if (EMOJI_PREFIX.equals(cover.packPackage)) {
            return renderEmoji(cover.drawableName);
        }

        // Icon pack cover
        try {
            IconPackManager mgr = LauncherComponentProvider.get(mContext).getIconPackManager();
            IconPack pack = mgr.getInstalledPacks().get(cover.packPackage);
            if (pack == null) return null;
            return pack.getDrawableForEntry(cover.drawableName,
                    mContext.getPackageManager());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the Noto Emoji monochrome typeface, loading it lazily from assets.
     */
    @Nullable
    public Typeface getEmojiTypeface() {
        if (mEmojiTypeface == null) {
            try {
                mEmojiTypeface = Typeface.createFromAsset(
                        mContext.getAssets(), NOTO_EMOJI_FONT);
                if (DEBUG) Log.d(TAG, "Loaded Noto Emoji typeface from assets");
            } catch (Exception e) {
                Log.w(TAG, "Failed to load Noto Emoji typeface, falling back to system", e);
            }
        }
        return mEmojiTypeface;
    }

    private static final float EMOJI_TEXT_SIZE_RATIO_LARGE = 0.8f;
    private static final float EMOJI_TEXT_SIZE_RATIO_SMALL = 0.7f;
    private static final int EMOJI_RENDER_SIZE_DP = 96;

    /**
     * Renders an emoji into a small BitmapDrawable for use in picker grids.
     * Guarantees monochrome rendering via the Noto Emoji font, bypassing
     * Android's emoji rendering pipeline that can override TextView typefaces.
     * @param emoji the emoji string
     * @param sizePx the bitmap size in pixels
     * @param color the fill color
     * @return a BitmapDrawable, or null if rendering fails
     */
    @Nullable
    public Drawable renderEmojiSmall(String emoji, int sizePx, int color) {
        return renderEmojiInternal(emoji, sizePx, color, EMOJI_TEXT_SIZE_RATIO_SMALL);
    }

    /**
     * Renders an emoji string into a BitmapDrawable using the Noto Emoji monochrome font.
     * The emoji is rendered in M3 onSurface color for proper theming.
     */
    private Drawable renderEmoji(String emoji) {
        int sizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                EMOJI_RENDER_SIZE_DP, mContext.getResources().getDisplayMetrics());
        int color = mContext.getColor(R.color.materialColorOnSurface);
        return renderEmojiInternal(emoji, sizePx, color, EMOJI_TEXT_SIZE_RATIO_LARGE);
    }

    private Drawable renderEmojiInternal(String emoji, int sizePx, int color,
            float textSizeRatio) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface tf = getEmojiTypeface();
        if (tf != null) {
            paint.setTypeface(tf);
        }
        paint.setColor(color);
        paint.setTextSize(sizePx * textSizeRatio);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (sizePx - fm.top - fm.bottom) / 2f;
        canvas.drawText(emoji, sizePx / 2f, y, paint);
        return new BitmapDrawable(mContext.getResources(), bitmap);
    }
}
