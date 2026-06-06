package com.android.launcher3.widget.custom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.SimpleDateFormat;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PrefSubscriber;
import com.android.launcher3.R;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * In-process clock widget view: a single-line Danfo time over a Bebas Neue
 * dateline, lock-screen style. Hosted via {@link DanfoClockWidgetPlugin}.
 *
 * <p>Font sizes are derived from the measured bounds (see {@link #onSizeChanged}),
 * so the widget is responsive from 2x2 upward with no hardcoded sizes.
 */
public class DanfoClockView extends LinearLayout {

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "DanfoClockView";

    /** Date format skeletons, longest first. The widest that fits is used. */
    private static final String[] DATE_SKELETONS = {
            "EEEEMMMMd", // SATURDAY, JUNE 6
            "EEEEMMMd",  // SATURDAY, JUN 6
            "EEEMMMd",   // SAT, JUN 6
            "EEEd"       // SAT 6
    };

    /** Date font as a fraction of the resolved time font. */
    private static final float DATE_RATIO = 0.32f;
    private static final float MIN_TIME_SP = 10f;
    private static final float MAX_TIME_PX = 600f;

    private float mAvailWidthPx = 0f;

    private AutoCloseable mPrefSubscription;
    private BroadcastReceiver mTimeReceiver;
    private com.android.launcher3.util.OnColorHintListener mWallpaperHintListener;

    private final TextClock mTime;
    private final TextView mDate;

    public DanfoClockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClipChildren(false);
        setClipToPadding(false);

        Typeface danfo = context.getResources().getFont(R.font.danfo);
        Typeface bebas = context.getResources().getFont(R.font.bebas_neue);

        mTime = new TextClock(context);
        mTime.setTypeface(danfo);
        mTime.setIncludeFontPadding(false);
        mTime.setMaxLines(1);
        mTime.setGravity(Gravity.CENTER);
        mTime.setFormat12Hour("h:mm");
        mTime.setFormat24Hour("HH:mm");
        addView(mTime, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mDate = new TextView(context);
        mDate.setTypeface(bebas);
        mDate.setIncludeFontPadding(false);
        mDate.setMaxLines(1);
        mDate.setLetterSpacing(0.04f);
        mDate.setGravity(Gravity.CENTER);
        addView(mDate, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        setContentDescription(context.getString(R.string.clock_widget_content_description));
        setOnClickListener(v -> {
            try {
                Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (Exception e) {
                if (DEBUG) android.util.Log.w(TAG, "no clock app to open", e);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        int padX = (int) (w * 0.10f);
        int padY = (int) (h * 0.10f);
        if (padX != getPaddingLeft() || padY != getPaddingTop()) {
            setPadding(padX, padY, padX, padY);
        }
        float availW = w - 2 * padX;
        float availH = h - 2 * padY;
        mAvailWidthPx = availW;

        // Size from ACTUAL measured glyph bounds, not a cap-height heuristic,
        // so digits never clip at small sizes. Use the worst-case (widest,
        // tallest) time string "00:00" so a later wide time also fits.
        final float GAP_RATIO = 0.12f;       // inter-line gap as a fraction of timeSize
        float timeWidthRatio = measureRatio(mTime.getPaint(), "00:00");
        float timeHeightRatio = measureBoundsHeightRatio(mTime.getPaint(), "00:00");
        float dateWidthRatio = measureRatio(mDate.getPaint(), "SATURDAY, JUNE 6");
        float dateHeightRatio = measureBoundsHeightRatio(mDate.getPaint(), "SATURDAY, JUNE 6");

        // Width limit: time alone must fit availW.
        float widthLimit = timeWidthRatio > 0 ? availW / timeWidthRatio : MAX_TIME_PX;
        // Height limit: time bounds + gap + date bounds must fit availH, where
        // dateSize = timeSize * DATE_RATIO. Solve for timeSize:
        //   timeSize*(timeHeightRatio + GAP_RATIO + DATE_RATIO*dateHeightRatio) <= availH
        float heightFactor = timeHeightRatio + GAP_RATIO + DATE_RATIO * dateHeightRatio;
        float heightLimit = heightFactor > 0 ? availH / heightFactor : MAX_TIME_PX;

        float timeSize = Math.min(widthLimit, heightLimit);
        // Safety margin so anti-aliased edges never touch the bounds.
        timeSize *= 0.96f;
        timeSize = Math.max(MIN_TIME_SP, Math.min(timeSize, MAX_TIME_PX));

        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize);
        float dateSize = timeSize * DATE_RATIO;
        // Date never forces the time smaller: it shrinks independently to fit.
        if (dateWidthRatio > 0 && dateSize * dateWidthRatio > availW) {
            dateSize = availW / dateWidthRatio;
        }
        mDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize);

        refreshDate();
        if (DEBUG) {
            android.util.Log.d(TAG, "size " + w + "x" + h + " time=" + timeSize
                    + " date=" + dateSize);
        }
    }

    /** Reads all clock prefs and restyles. Safe to call repeatedly. */
    private void applyPrefs() {
        LauncherPrefs prefs = LauncherPrefs.get(getContext());

        // Alignment.
        boolean left = "left".equals(prefs.get(LauncherPrefs.CLOCK_ALIGNMENT));
        int gravity = (left ? Gravity.START : Gravity.CENTER_HORIZONTAL) | Gravity.CENTER_VERTICAL;
        setGravity(gravity);
        mTime.setGravity(gravity);
        mDate.setGravity(gravity);

        // Time format (12h: h:mm no leading zero; 24h: HH:mm zero-padded).
        String fmt = prefs.get(LauncherPrefs.CLOCK_TIME_FORMAT);
        if ("12".equals(fmt)) {
            mTime.setFormat12Hour("h:mm");
            mTime.setFormat24Hour("h:mm");
        } else if ("24".equals(fmt)) {
            mTime.setFormat12Hour("HH:mm");
            mTime.setFormat24Hour("HH:mm");
        } else { // follow system
            mTime.setFormat12Hour("h:mm");
            mTime.setFormat24Hour("HH:mm");
        }

        // Color.
        int timeColor = resolveTimeColor(prefs);
        int dateColor = resolveDateColor(prefs, timeColor);
        mTime.setTextColor(timeColor);
        mDate.setTextColor(dateColor);

        refreshDate();
    }

    private int resolveTimeColor(LauncherPrefs prefs) {
        switch (prefs.get(LauncherPrefs.CLOCK_COLOR_MODE)) {
            case "custom":
                return resolveNamedColor(prefs.get(LauncherPrefs.CLOCK_TIME_COLOR), Color.WHITE);
            case "material_you":
                return materialYouColor();
            case "wallpaper":
                return wallpaperContrastColor();
            case "white":
            default:
                return Color.WHITE;
        }
    }

    private int resolveDateColor(LauncherPrefs prefs, int timeColor) {
        if ("custom".equals(prefs.get(LauncherPrefs.CLOCK_COLOR_MODE))) {
            return resolveNamedColor(prefs.get(LauncherPrefs.CLOCK_DATE_COLOR), timeColor);
        }
        return timeColor;
    }

    /** Resolves a color-resource name (as stored by ColorPickerPreference) to an int. */
    private int resolveNamedColor(String name, int fallback) {
        if (name == null || name.isEmpty()) return fallback;
        int id = getResources().getIdentifier(name, "color", getContext().getPackageName());
        return id != 0 ? getContext().getColor(id) : fallback;
    }

    private int materialYouColor() {
        try {
            return getContext().getColor(android.R.color.system_accent1_100);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    private int wallpaperContrastColor() {
        int hints = com.android.launcher3.util.WallpaperColorHints.get(getContext()).getHints();
        return (hints & android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                ? 0xFF111111 : android.graphics.Color.WHITE;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyPrefs();

        mPrefSubscription = LauncherPrefs.get(getContext()).getPrefChanges().subscribe(
                mPrefSubscriber,
                LauncherPrefs.CLOCK_ALIGNMENT,
                LauncherPrefs.CLOCK_TIME_FORMAT,
                LauncherPrefs.CLOCK_COLOR_MODE,
                LauncherPrefs.CLOCK_TIME_COLOR,
                LauncherPrefs.CLOCK_DATE_COLOR);

        mWallpaperHintListener = new com.android.launcher3.util.OnColorHintListener() {
            @Override public void onColorHintsChanged(int colorHints) { applyPrefs(); }
        };
        com.android.launcher3.util.WallpaperColorHints.get(getContext())
                .registerOnColorHintsChangedListener(mWallpaperHintListener);

        mTimeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                    applyPrefs();
                } else {
                    refreshDate();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        getContext().registerReceiver(mTimeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPrefSubscription != null) {
            try { mPrefSubscription.close(); } catch (Exception ignored) { }
            mPrefSubscription = null;
        }
        if (mWallpaperHintListener != null) {
            com.android.launcher3.util.WallpaperColorHints.get(getContext())
                    .unregisterOnColorsChangedListener(mWallpaperHintListener);
            mWallpaperHintListener = null;
        }
        if (mTimeReceiver != null) {
            try { getContext().unregisterReceiver(mTimeReceiver); } catch (Exception ignored) { }
            mTimeReceiver = null;
        }
    }

    private final PrefSubscriber mPrefSubscriber = new PrefSubscriber() {
        @Override public void onPrefsChanged(Set<? extends Item> changes) {
            applyPrefs();
        }
    };

    /** Width of {@code text} per 1px of font size for the given paint's typeface. */
    private static float measureRatio(Paint paint, String text) {
        float ref = 100f;
        float saved = paint.getTextSize();
        paint.setTextSize(ref);
        float w = paint.measureText(text);
        paint.setTextSize(saved);
        return w / ref;
    }

    /**
     * Real rendered glyph height of {@code text} per 1px of font size, taken
     * from {@link Paint#getTextBounds} (actual ink bounds, not font metrics),
     * so callers reserve exactly the space the glyphs occupy.
     */
    private static float measureBoundsHeightRatio(Paint paint, String text) {
        float ref = 100f;
        float saved = paint.getTextSize();
        paint.setTextSize(ref);
        android.graphics.Rect bounds = new android.graphics.Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        paint.setTextSize(saved);
        return bounds.height() / ref;
    }

    /** Picks the widest date format that fits {@code availWidthPx} at the date text size. */
    private void refreshDate() {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        Date now = new Date();
        Paint paint = mDate.getPaint();
        String chosen = null;
        for (String skeleton : DATE_SKELETONS) {
            String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
            String text = new SimpleDateFormat(pattern, locale).format(now)
                    .toUpperCase(locale);
            if (mAvailWidthPx <= 0f || paint.measureText(text) <= mAvailWidthPx) {
                chosen = text;
                break;
            }
            chosen = text; // keep the shortest as a fallback
        }
        mDate.setText(chosen);
    }
}
