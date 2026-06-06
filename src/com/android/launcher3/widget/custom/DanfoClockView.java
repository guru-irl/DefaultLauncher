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
 * <p>The time is the dominant hero element and scales up to fill the widget.
 * The date is a small one-liner pinned right below the time with a fixed ~8dp
 * gap; together they form one tight block centered (or left-aligned) as a unit.
 *
 * <p>Sizing in {@link #onSizeChanged} uses FONT METRICS (ascent..descent), not
 * tight ink bounds, because a {@link TextView} lays out by font metrics. Sizing
 * from ink bounds underestimates the line box and clips the stacked block at
 * some aspect ratios. The date is sized as a reduced, capped fraction of the
 * time so the clock keeps scaling on large widgets while the date does not.
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

    /** Widest time string used to bound the time's width on every measure. */
    private static final String TIME_SAMPLE = "00:00";
    /** Widest date string used as the width reference for the date line. */
    private static final String DATE_SAMPLE = "SATURDAY, JUNE 6";

    /**
     * The date is a reduced fraction of the time and is capped, so on big
     * widgets the time keeps growing while the date stays a small subtitle.
     */
    private static final float DATE_FACTOR = 0.20f;
    private static final float DATE_CAP_SP = 22f;

    /** Time-size clamp (px) so tiny cells stay legible and huge cells stay sane. */
    private static final float TIME_MIN_PX = 18f;
    private static final float TIME_MAX_PX = 1200f;

    /**
     * Gap between the time and the date, as a fraction of the resolved time
     * size, so it stays visually consistent at every widget size. Slightly
     * negative to pull the date up into the time's descent whitespace so it
     * sits snug right below the clock. Used in BOTH the date's top margin and
     * the height budget, so shortening it lets the time grow to fill the cell.
     */
    private static final float GAP_FACTOR = -0.10f;

    /** Date cap in px, derived from {@link #DATE_CAP_SP}. */
    private final float mDateCapPx;

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

        mDateCapPx = DATE_CAP_SP * context.getResources().getDisplayMetrics().scaledDensity;

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
        // Top margin is set proportionally in onSizeChanged once the time size
        // is known (see GAP_FACTOR).
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

        // Modest padding so glyphs never touch the cell edge but stay large.
        int padX = (int) (w * 0.07f);
        int padY = (int) (h * 0.07f);
        if (padX != getPaddingLeft() || padY != getPaddingTop()) {
            setPadding(padX, padY, padX, padY);
        }
        float availW = w - 2 * padX;
        float availH = h - 2 * padY;
        if (availW <= 0f || availH <= 0f) return;

        // Reference all ratios at a fixed probe size so they are independent of
        // the current text size. We measure WIDTH from measureText and the LINE
        // HEIGHT from font metrics (descent - ascent), which is how a TextView
        // actually lays out -- this is the fix for the ink-bounds clipping.
        final float REF = 100f;
        Paint timePaint = mTime.getPaint();
        Paint datePaint = mDate.getPaint();

        float timeWidthRatio = measureTextRatio(timePaint, TIME_SAMPLE, REF);
        float timeLineRatio = lineHeightRatio(timePaint, REF);
        float dateWidthRatio = measureTextRatio(datePaint, DATE_SAMPLE, REF);
        float dateLineRatio = lineHeightRatio(datePaint, REF);

        // Time bounded by width and by the stacked block height (time line +
        // fixed gap + date line, where the date line is DATE_FACTOR of the time).
        float timeByWidth = timeWidthRatio > 0f ? availW / timeWidthRatio : TIME_MAX_PX;
        // Block height = time line + gap + date line, all as multiples of the
        // time size (gap = GAP_FACTOR * timeSize). Folding the gap in here means
        // a tighter (more negative) gap lets the time grow to fill the cell.
        float heightDenom = timeLineRatio + GAP_FACTOR + DATE_FACTOR * dateLineRatio;
        float timeByHeight = heightDenom > 0f ? availH / heightDenom : TIME_MAX_PX;

        float timeSize = Math.min(timeByWidth, timeByHeight) * 0.98f;
        timeSize = Math.max(TIME_MIN_PX, Math.min(TIME_MAX_PX, timeSize));

        // Date is a reduced, capped fraction of the time -- it scales less than
        // the clock so it stays a small subtitle on large widgets.
        float dateSize = Math.min(timeSize * DATE_FACTOR, mDateCapPx);
        // If even the chosen string is too wide, shrink the date to fit width.
        // (refreshDate already prefers a shorter abbreviation; this is a final
        // guard against overflow at extreme aspect ratios.)
        if (dateWidthRatio > 0f && dateSize * dateWidthRatio > availW) {
            dateSize = availW / dateWidthRatio;
        }
        dateSize = Math.max(1f, dateSize);

        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize);
        mDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize);

        // Pull the date snug under the time, proportional to the time size, so
        // it reads as "right below" the clock at every widget size.
        int gap = Math.round(GAP_FACTOR * timeSize);
        LayoutParams dateLp = (LayoutParams) mDate.getLayoutParams();
        if (dateLp.topMargin != gap) {
            dateLp.topMargin = gap;
            mDate.setLayoutParams(dateLp);
        }

        // Soft shadow keyed to text size so legibility scales with the glyphs.
        mTime.setShadowLayer(Math.max(1f, timeSize * 0.05f), 0f, timeSize * 0.02f, 0x66000000);
        mDate.setShadowLayer(Math.max(1f, dateSize * 0.06f), 0f, dateSize * 0.02f, 0x55000000);

        refreshDate(availW);

        // onSizeChanged runs during the layout pass, after our children were
        // already measured at their previous (smaller) text size. Setting a new
        // text size requests a re-layout, but the AppWidgetHostView can hand us
        // back the same fixed size without forcing a child re-measure, leaving
        // the time view with stale (too-small) bounds that clip the big glyphs.
        // Posting an explicit re-layout guarantees the children re-measure at
        // their final text sizes on the next frame.
        post(() -> {
            mTime.requestLayout();
            mDate.requestLayout();
            requestLayout();
        });
        if (DEBUG) {
            android.util.Log.d(TAG, "size " + w + "x" + h + " availW=" + availW
                    + " availH=" + availH + " timeSize=" + timeSize
                    + " dateSize=" + dateSize + " gap=" + gap);
        }
    }

    /** Width of {@code text} at {@code refSize}, divided by refSize. */
    private static float measureTextRatio(Paint src, String text, float refSize) {
        Paint p = new Paint(src);
        p.setTextSize(refSize);
        return p.measureText(text) / refSize;
    }

    /** Font line height (descent - ascent) at {@code refSize}, divided by refSize. */
    private static float lineHeightRatio(Paint src, float refSize) {
        Paint p = new Paint(src);
        p.setTextSize(refSize);
        Paint.FontMetrics fm = new Paint.FontMetrics();
        p.getFontMetrics(fm);
        return (fm.descent - fm.ascent) / refSize;
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

        refreshDate(currentAvailW());
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

    /** Inner content width in px (widget width minus horizontal padding). */
    private float currentAvailW() {
        int w = getWidth();
        if (w <= 0) return 0f;
        return w - getPaddingLeft() - getPaddingRight();
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
                    refreshDate(currentAvailW());
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

    /**
     * Picks the widest date abbreviation that fits the available width at the
     * date's current text size, longest-first. The shortest form is the
     * guaranteed fallback. The geometric check is frame-stable so the choice
     * does not flip-flop across re-measures.
     */
    private void refreshDate(float availW) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        Date now = new Date();
        Paint paint = new Paint(mDate.getPaint());
        String chosen = null;
        for (String skeleton : DATE_SKELETONS) {
            String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
            String text = new SimpleDateFormat(pattern, locale).format(now)
                    .toUpperCase(locale);
            chosen = text; // shortest form is the guaranteed fallback
            if (availW <= 0f) break;
            if (paint.measureText(text) <= availW) break;
        }
        mDate.setText(chosen);
    }
}
