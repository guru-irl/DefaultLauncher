package com.android.launcher3.widget.custom;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.SimpleDateFormat;
import android.text.format.DateFormat;
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
