package com.android.launcher3.widget.custom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PrefSubscriber;
import com.android.launcher3.R;

import java.util.Set;

/**
 * In-process "SEARCH" pill widget: a rounded-rectangle background filling the
 * widget with the word SEARCH centered in Bebas Neue. Hosted via
 * {@link SearchWidgetPlugin}.
 *
 * <p>Fixed 2x1 size (NOT resizable). The pill is fully rounded (corner radius =
 * 50% of the view height, capped) and the text auto-sizes to a comfortable
 * fraction of the view height on a single line with horizontal padding.
 *
 * <p>Colors mirror {@link DanfoClockView}: a stored color-resource NAME is
 * resolved via {@code getIdentifier}; when unset, the background defaults to the
 * M3 Surface color and the text to OnSurface. A live pref subscription restyles
 * a placed widget the instant the user changes a color.
 */
public class SearchWidgetView extends FrameLayout {

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "SearchWidgetView";

    /** Pill corner radius is capped so very tall cells stay sane. */
    private static final float MAX_CORNER_RADIUS_DP = 48f;
    /** Text fills this fraction of the inner (padded) height. */
    private static final float TEXT_HEIGHT_FRACTION = 0.42f;
    /** Text size clamp (px). */
    private static final float TEXT_MIN_PX = 12f;
    private static final float TEXT_MAX_PX = 200f;

    private final TextView mLabel;
    private final GradientDrawable mPill;

    private AutoCloseable mPrefSubscription;

    public SearchWidgetView(Context context) {
        super(context);
        setClipToPadding(false);

        mPill = new GradientDrawable();
        mPill.setShape(GradientDrawable.RECTANGLE);
        setBackground(mPill);

        Typeface bebas = context.getResources().getFont(R.font.bebas_neue);

        mLabel = new TextView(context);
        mLabel.setTypeface(bebas);
        mLabel.setIncludeFontPadding(false);
        mLabel.setMaxLines(1);
        mLabel.setGravity(Gravity.CENTER);
        // Tighter than default Bebas Neue: pull the letters closer together for
        // the morphed, condensed look.
        mLabel.setLetterSpacing(-0.05f);
        mLabel.setText(R.string.search_widget_text);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(mLabel, lp);

        setContentDescription(context.getString(R.string.search_widget_content_description));
        setOnClickListener(v -> openSearch());
    }

    /**
     * Opens the web-search provider configured in the launcher's search settings
     * ({@link LauncherPrefs#SEARCH_WEB_APP}). When that pref is a specific app
     * component, its app is launched; when it is {@code "default"} or unset, the
     * system default web-search handler is used. Mirrors
     * {@code SearchFabController.launchWebSearch} (without a query).
     */
    private void openSearch() {
        Context ctx = getContext();
        String webApp = LauncherPrefs.get(ctx).get(LauncherPrefs.SEARCH_WEB_APP);
        if (webApp != null && !webApp.isEmpty() && !"default".equals(webApp)) {
            try {
                ComponentName cn = ComponentName.unflattenFromString(webApp);
                if (cn != null) {
                    Intent launch = ctx.getPackageManager()
                            .getLaunchIntentForPackage(cn.getPackageName());
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(launch);
                        return;
                    }
                    ctx.startActivity(new Intent(Intent.ACTION_WEB_SEARCH)
                            .setComponent(cn)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
            } catch (Exception e) {
                if (DEBUG) android.util.Log.w(TAG, "could not launch configured web search", e);
            }
        }
        // Default / fallback: the system web-search handler.
        try {
            ctx.startActivity(new Intent(Intent.ACTION_WEB_SEARCH)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            if (DEBUG) android.util.Log.w(TAG, "no web search handler to open", e);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        // Pill: fully rounded ends (radius = half height), capped.
        float maxCornerPx = MAX_CORNER_RADIUS_DP
                * getResources().getDisplayMetrics().density;
        float corner = Math.min(h * 0.5f, maxCornerPx);
        mPill.setCornerRadius(corner);

        // Modest horizontal padding so the glyphs clear the rounded ends but
        // the centered word still has room (a full corner-radius pad each side
        // would swallow most of a wide-but-short 2x1 pill and clip the text).
        // A little vertical padding so the text breathes inside the pill.
        int padX = (int) (h * 0.18f);
        int padY = (int) (h * 0.12f);
        if (padX != getPaddingLeft() || padY != getPaddingTop()) {
            setPadding(padX, padY, padX, padY);
        }

        float availH = h - 2 * padY;
        float availW = w - 2 * padX;
        if (availH <= 0f || availW <= 0f) return;

        // Size the text to a comfortable fraction of the inner height, then
        // shrink to fit the inner width if the word would overflow on a narrow
        // pill (keeps SEARCH on one line, never clipped).
        float textSize = availH * TEXT_HEIGHT_FRACTION;
        textSize = Math.max(TEXT_MIN_PX, Math.min(TEXT_MAX_PX, textSize));
        android.graphics.Paint probe = new android.graphics.Paint(mLabel.getPaint());
        probe.setTextSize(textSize);
        float textW = probe.measureText(mLabel.getText().toString())
                + mLabel.getLetterSpacing() * textSize
                        * Math.max(0, mLabel.getText().length() - 1);
        if (textW > availW && textW > 0f) {
            textSize *= availW / textW;
            textSize = Math.max(TEXT_MIN_PX, textSize);
        }
        mLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        // onSizeChanged runs during layout, after the WRAP_CONTENT label was
        // already measured at its previous (default) text size. Setting a new
        // size requests a re-layout, but the AppWidgetHostView can hand us back
        // the same fixed size without forcing a child re-measure, leaving the
        // label with stale (too-narrow) bounds that clip "SEARCH" to "SEAR".
        // Posting an explicit re-layout guarantees the label re-measures at its
        // final text size on the next frame.
        post(() -> {
            mLabel.requestLayout();
            requestLayout();
        });

        if (DEBUG) {
            android.util.Log.d(TAG, "size " + w + "x" + h + " corner=" + corner
                    + " padX=" + padX + " textSize=" + textSize);
        }
    }

    /** Reads the two color prefs and restyles. Safe to call repeatedly. */
    private void applyPrefs() {
        LauncherPrefs prefs = LauncherPrefs.get(getContext());
        int bg = resolveNamedColor(prefs.get(LauncherPrefs.SEARCH_WIDGET_BG_COLOR),
                defaultColor(R.color.materialColorSurface));
        int text = resolveNamedColor(prefs.get(LauncherPrefs.SEARCH_WIDGET_TEXT_COLOR),
                defaultColor(R.color.materialColorOnSurface));
        mPill.setColor(bg);
        mLabel.setTextColor(text);
        invalidate();
    }

    private int defaultColor(int resId) {
        try {
            return getContext().getColor(resId);
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    /**
     * Resolves a color-resource name (as stored by {@link ColorPickerPreference})
     * to an int. The picker stores framework tonal-palette names
     * (e.g. {@code system_accent1_600}), so resolution goes through
     * {@link com.android.launcher3.allapps.AllAppsColorResolver} (the same map the
     * picker uses); a launcher-package {@code R.color.*} name is also accepted as
     * a fallback via {@code getIdentifier}, mirroring the clock view.
     */
    private int resolveNamedColor(String name, int fallback) {
        if (name == null || name.isEmpty()) return fallback;
        int mapped = com.android.launcher3.allapps.AllAppsColorResolver
                .resolveColorByName(getContext(), name);
        if (mapped != 0) return mapped;
        int id = getResources().getIdentifier(name, "color", getContext().getPackageName());
        return id != 0 ? getContext().getColor(id) : fallback;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyPrefs();
        mPrefSubscription = LauncherPrefs.get(getContext()).getPrefChanges().subscribe(
                mPrefSubscriber,
                LauncherPrefs.SEARCH_WIDGET_BG_COLOR,
                LauncherPrefs.SEARCH_WIDGET_TEXT_COLOR);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPrefSubscription != null) {
            try { mPrefSubscription.close(); } catch (Exception ignored) { }
            mPrefSubscription = null;
        }
    }

    private final PrefSubscriber mPrefSubscriber = new PrefSubscriber() {
        @Override public void onPrefsChanged(Set<? extends Item> changes) {
            applyPrefs();
        }
    };
}
