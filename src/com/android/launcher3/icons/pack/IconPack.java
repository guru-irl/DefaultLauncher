/*
 * Copyright (C) 2024 DefaultLauncher Contributors
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
package com.android.launcher3.icons.pack;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Model representing a parsed ADW-format icon pack.
 * Lazily parses appfilter.xml on first use.
 */
public class IconPack {

    private static final String TAG = "IconPack";

    public final String packageName;
    public final CharSequence label;

    // Parsed from appfilter.xml
    private Map<ComponentName, String> mComponentToDrawable;
    private final List<Bitmap> mBackImages = new ArrayList<>();
    private Bitmap mMaskImage;
    private Bitmap mFrontImage;
    private float mScale = 1.0f;
    private Map<ComponentName, String> mCalendarPrefixes;
    private boolean mParsed = false;

    private final Random mRandom = new Random();

    public IconPack(String packageName, CharSequence label) {
        this.packageName = packageName;
        this.label = label;
    }

    /** Parse appfilter.xml on first use. Thread-safe. */
    public synchronized void ensureParsed(PackageManager pm) {
        if (mParsed) return;
        mComponentToDrawable = new HashMap<>();
        mCalendarPrefixes = new HashMap<>();

        try {
            Resources res = pm.getResourcesForApplication(packageName);
            // Try compiled XML resource first (res/xml/appfilter.xml)
            boolean parsed = tryParseXmlResource(res);
            if (!parsed) {
                // Fall back to assets/appfilter.xml
                tryParseAssets(pm, res);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Icon pack not found: " + packageName, e);
        }

        mParsed = true;
    }

    private boolean tryParseXmlResource(Resources res) {
        int xmlId = res.getIdentifier("appfilter", "xml", packageName);
        if (xmlId == 0) return false;

        try (XmlResourceParser parser = res.getXml(xmlId)) {
            parseAppFilter(parser, res);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse res/xml/appfilter.xml for " + packageName, e);
            return false;
        }
    }

    private void tryParseAssets(PackageManager pm, Resources res) {
        try {
            Resources packRes = pm.getResourcesForApplication(packageName);
            InputStream is = packRes.getAssets().open("appfilter.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");
            parseAppFilter(parser, res);
            is.close();
        } catch (IOException e) {
            Log.w(TAG, "No appfilter.xml in assets for " + packageName);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse assets/appfilter.xml for " + packageName, e);
        }
    }

    private void parseAppFilter(XmlPullParser parser, Resources res)
            throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                switch (tag) {
                    case "item":
                        parseItem(parser);
                        break;
                    case "calendar":
                        parseCalendar(parser);
                        break;
                    case "iconback":
                        parseIconBack(parser, res);
                        break;
                    case "iconmask":
                        parseIconMask(parser, res);
                        break;
                    case "iconupon":
                        parseIconUpon(parser, res);
                        break;
                    case "scale":
                        parseScale(parser);
                        break;
                }
            }
            eventType = parser.next();
        }
    }

    private void parseItem(XmlPullParser parser) {
        String component = parser.getAttributeValue(null, "component");
        String drawable = parser.getAttributeValue(null, "drawable");
        if (component == null || drawable == null) return;

        ComponentName cn = parseComponentName(component);
        if (cn != null) {
            mComponentToDrawable.put(cn, drawable);
        }
    }

    private void parseCalendar(XmlPullParser parser) {
        String component = parser.getAttributeValue(null, "component");
        String prefix = parser.getAttributeValue(null, "prefix");
        if (component == null || prefix == null) return;

        ComponentName cn = parseComponentName(component);
        if (cn != null) {
            mCalendarPrefixes.put(cn, prefix);
        }
    }

    private void parseIconBack(XmlPullParser parser, Resources res) {
        for (int i = 0; ; i++) {
            String attr = parser.getAttributeValue(null, "img" + (i == 0 ? "" : i));
            if (attr == null) {
                attr = parser.getAttributeValue(null, "img" + (i + 1));
                if (attr == null) break;
            }
            Bitmap bmp = loadBitmapFromDrawableName(res, attr);
            if (bmp != null) {
                mBackImages.add(bmp);
            }
        }
    }

    private void parseIconMask(XmlPullParser parser, Resources res) {
        String attr = parser.getAttributeValue(null, "img1");
        if (attr == null) attr = parser.getAttributeValue(null, "img");
        if (attr != null) {
            mMaskImage = loadBitmapFromDrawableName(res, attr);
        }
    }

    private void parseIconUpon(XmlPullParser parser, Resources res) {
        String attr = parser.getAttributeValue(null, "img1");
        if (attr == null) attr = parser.getAttributeValue(null, "img");
        if (attr != null) {
            mFrontImage = loadBitmapFromDrawableName(res, attr);
        }
    }

    private void parseScale(XmlPullParser parser) {
        String factor = parser.getAttributeValue(null, "factor");
        if (factor != null) {
            try {
                mScale = Float.parseFloat(factor);
            } catch (NumberFormatException e) {
                // keep default 1.0
            }
        }
    }

    @Nullable
    private Bitmap loadBitmapFromDrawableName(Resources res, String drawableName) {
        int id = res.getIdentifier(drawableName, "drawable", packageName);
        if (id == 0) return null;
        try {
            Drawable d = res.getDrawable(id, null);
            return drawableToBitmap(d);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    /**
     * Parse ADW component string format:
     * "ComponentInfo{com.package/com.package.Activity}" or
     * "ComponentInfo{com.package/.Activity}"
     */
    @Nullable
    static ComponentName parseComponentName(String componentStr) {
        if (componentStr == null) return null;
        // Strip "ComponentInfo{" prefix and "}" suffix
        componentStr = componentStr.trim();
        if (componentStr.startsWith("ComponentInfo{") && componentStr.endsWith("}")) {
            componentStr = componentStr.substring(14, componentStr.length() - 1);
        }
        ComponentName cn = ComponentName.unflattenFromString(componentStr);
        return cn;
    }

    /**
     * Returns the icon pack's own application icon.
     */
    @Nullable
    public Drawable getPackIcon(PackageManager pm) {
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Well-known preview components — 5 categories, each with multiple fallback ComponentNames.
     */
    private static final ComponentName[][] PREVIEW_COMPONENTS = {
        { // Phone
            new ComponentName("com.google.android.dialer", "com.google.android.dialer.extensions.GoogleDialtactsActivity"),
            new ComponentName("com.android.dialer", "com.android.dialer.DialtactsActivity"),
            new ComponentName("com.samsung.android.dialer", "com.samsung.android.dialer.DialtactsActivity"),
        },
        { // Messages
            new ComponentName("com.google.android.apps.messaging", "com.google.android.apps.messaging.ui.ConversationListActivity"),
            new ComponentName("com.android.mms", "com.android.mms.ui.ConversationList"),
            new ComponentName("com.samsung.android.messaging", "com.samsung.android.messaging.ui.view.setting.MainSettingActivity"),
        },
        { // Instagram
            new ComponentName("com.instagram.android", "com.instagram.android.activity.MainTabActivity"),
        },
        { // Camera
            new ComponentName("com.google.android.GoogleCamera", "com.android.camera.CameraLauncher"),
            new ComponentName("com.android.camera2", "com.android.camera.CameraActivity"),
            new ComponentName("com.samsung.android.camera", "com.samsung.android.camera.CameraEntry"),
        },
        { // YouTube
            new ComponentName("com.google.android.youtube", "com.google.android.youtube.HomeActivity"),
            new ComponentName("com.google.android.youtube", "com.google.android.youtube.app.honeycomb.Shell$HomeActivity"),
            new ComponentName("com.google.android.youtube", "com.google.android.apps.youtube.app.WatchWhileActivity"),
        },
    };

    /**
     * Returns up to 5 preview drawables for well-known apps from this icon pack.
     * Must call ensureParsed() first.
     */
    public List<Drawable> getPreviewIcons(PackageManager pm) {
        List<Drawable> previews = new ArrayList<>();
        if (mComponentToDrawable == null) return previews;

        for (ComponentName[] category : PREVIEW_COMPONENTS) {
            if (previews.size() >= 5) break;
            for (ComponentName cn : category) {
                String drawableName = mComponentToDrawable.get(cn);
                if (drawableName != null) {
                    Drawable d = loadDrawableByName(pm, drawableName);
                    if (d != null) {
                        previews.add(d);
                        break; // got one for this category, next category
                    }
                }
            }
        }
        return previews;
    }

    /** Get the exact icon pack drawable for a given component, or null. */
    @Nullable
    public Drawable getIconForComponent(ComponentName cn, PackageManager pm) {
        ensureParsed(pm);
        String drawableName = mComponentToDrawable.get(cn);
        if (drawableName == null) return null;
        return loadDrawableByName(pm, drawableName);
    }

    /** Get calendar icon for today's date, or null. */
    @Nullable
    public Drawable getCalendarIcon(ComponentName cn, PackageManager pm) {
        ensureParsed(pm);
        String prefix = mCalendarPrefixes.get(cn);
        if (prefix == null) return null;

        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        String drawableName = prefix + day;
        return loadDrawableByName(pm, drawableName);
    }

    /** Returns true if this pack defines iconback/iconmask for fallback masking. */
    public boolean hasFallbackMask() {
        return !mBackImages.isEmpty() || mMaskImage != null;
    }

    /**
     * Apply iconback/iconmask/iconupon fallback masking to an unmapped app icon.
     * Returns the masked icon as a BitmapDrawable, or null if no masking is available.
     */
    @Nullable
    public Drawable applyFallbackMask(Drawable original, int iconSize) {
        if (!hasFallbackMask() || original == null) return null;

        Bitmap result = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // 1. Draw background (iconback) — pick one at random
        if (!mBackImages.isEmpty()) {
            Bitmap back = mBackImages.get(mRandom.nextInt(mBackImages.size()));
            canvas.drawBitmap(back, null,
                    new android.graphics.Rect(0, 0, iconSize, iconSize), null);
        }

        // 2. Draw scaled original icon centered
        Bitmap originalBmp = drawableToBitmap(original);
        int scaledSize = Math.round(iconSize * mScale);
        int offset = (iconSize - scaledSize) / 2;
        canvas.drawBitmap(originalBmp, null,
                new android.graphics.Rect(offset, offset,
                        offset + scaledSize, offset + scaledSize), null);

        // 3. Apply mask via DST_IN
        if (mMaskImage != null) {
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            canvas.drawBitmap(mMaskImage, null,
                    new android.graphics.Rect(0, 0, iconSize, iconSize), maskPaint);
        }

        // 4. Draw overlay (iconupon) on top
        if (mFrontImage != null) {
            canvas.drawBitmap(mFrontImage, null,
                    new android.graphics.Rect(0, 0, iconSize, iconSize), null);
        }

        return new BitmapDrawable(null, result);
    }

    @Nullable
    private Drawable loadDrawableByName(PackageManager pm, String drawableName) {
        try {
            Resources res = pm.getResourcesForApplication(packageName);
            int id = res.getIdentifier(drawableName, "drawable", packageName);
            if (id != 0) {
                return res.getDrawable(id, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Icon pack resources not found: " + packageName, e);
        }
        return null;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            if (bmp != null) return bmp;
        }
        int width = Math.max(drawable.getIntrinsicWidth(), 1);
        int height = Math.max(drawable.getIntrinsicHeight(), 1);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
