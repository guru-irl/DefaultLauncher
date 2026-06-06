package com.android.launcher3.widget.custom;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.SimpleDateFormat;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.R;

import java.util.Date;
import java.util.Locale;

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

        setContentDescription("Danfo clock");
        refreshDate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float padX = w * 0.10f;
        float padY = h * 0.10f;
        setPadding((int) padX, (int) padY, (int) padX, (int) padY);
        float availW = w - 2 * padX;
        float availH = h - 2 * padY;
        mAvailWidthPx = availW;

        // Time scales to fill width, capped by a height budget that reserves
        // room for the dateline (time*0.92 + gap(0.10*t) + date(DATE_RATIO*t)).
        float ratioT = measureRatio(mTime.getPaint(), "9:41");
        float timeByW = ratioT > 0 ? availW / ratioT : MAX_TIME_PX;
        float heightFactor = 0.92f + 0.10f + DATE_RATIO * 0.95f;
        float timeByH = availH / heightFactor;
        float timeSize = Math.max(MIN_TIME_SP, Math.min(Math.min(timeByW, timeByH), MAX_TIME_PX));

        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize);
        float dateSize = timeSize * DATE_RATIO;
        // Date never forces the time smaller: it shrinks independently to fit.
        float ratioD = measureRatio(mDate.getPaint(), "SATURDAY, JUNE 6");
        if (ratioD > 0 && dateSize * ratioD > availW) {
            dateSize = availW / ratioD;
        }
        mDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize);

        refreshDate();
        if (DEBUG) {
            android.util.Log.d(TAG, "size " + w + "x" + h + " time=" + timeSize
                    + " date=" + dateSize);
        }
    }

    /** Width of {@code text} per 1px of font size for the given paint's typeface. */
    private static float measureRatio(Paint paint, String text) {
        float ref = 100f;
        float saved = paint.getTextSize();
        paint.setTextSize(ref);
        float w = paint.measureText(text);
        paint.setTextSize(saved);
        return w / ref;
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
