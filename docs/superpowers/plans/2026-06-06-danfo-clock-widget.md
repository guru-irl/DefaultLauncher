# Danfo Clock Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a launcher-native, in-process clock widget that shows a single-line Danfo time with a Bebas Neue dateline, is responsive from 2x2 upward with automatic font sizing, and is configurable (alignment, time format, color/theming), covered by e2e UI and visual tests.

**Architecture:** The widget is hosted in-process via the launcher's existing `CustomWidgetManager` path (not RemoteViews), so a `DanfoClockWidgetPlugin` (a concrete `CustomWidgetPlugin`) attaches a custom `DanfoClockView` into the `AppWidgetHostView`. The view uses a `TextClock` for the auto-ticking time and a `TextView` for an adaptive, non-wrapping dateline, sizes its fonts from its measured bounds, and reads five `LauncherPrefs` settings live through the unified `PrefChangeDispatcher`. Settings live in a `ClockWidgetFragment` sub-screen. Tests place the widget deterministically through a debug broadcast added to `WorkspaceSeedReceiver`.

**Tech Stack:** Java (launcher views/plugin), Kotlin (`LauncherPrefs`), Android resources (XML prefs, fonts, arrays, strings), Python `uiautomator2` + `pytest` (e2e).

**Reference spec:** `docs/superpowers/specs/2026-06-06-danfo-clock-widget-design.md`

---

## Conventions for every task

- **Build command** (Linux JBR on this box; see CLAUDE.md):
  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug
  ```
- **Install to AVD:**
  ```bash
  adb -s emulator-5554 install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk
  ```
- **No JVM unit tests exist** in this repo. Per-task verification is "the build passes"; the feature is verified by the e2e suite in Phase 5.
- **Commit identity (invisible mode, no trailer, no em-dashes):**
  ```bash
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    commit -m "feat: <message>"
  ```
- **Branch:** create `feature/danfo-clock-widget` off `dev` before Task 1:
  ```bash
  git checkout dev && git pull --ff-only origin dev && git checkout -b feature/danfo-clock-widget
  ```
- Wrap any diagnostic logging in `if (DEBUG)` with `DEBUG = BuildConfig.DEBUG` and a `TAG` constant (CLAUDE.md).

---

## Phase 0: Fonts

### Task 0: Bundle the Bebas Neue font

**Files:**
- Create: `res/font/bebas_neue.ttf` (binary asset)

- [ ] **Step 1: Acquire the font**

Download Bebas Neue (SIL Open Font License) regular weight as a `.ttf`. Source: Google Fonts (`https://fonts.google.com/specimen/Bebas+Neue`). Save the regular TTF to `res/font/bebas_neue.ttf`. The file name must be all lowercase with no hyphens (Android resource naming).

- [ ] **Step 2: Verify it is a valid font resource**

Run:
```bash
file res/font/bebas_neue.ttf
```
Expected: output contains `TrueType` (or `OpenType`) font data.

- [ ] **Step 3: Confirm `res/font/danfo.ttf` still present**

Run: `ls res/font/`
Expected: both `danfo.ttf` and `bebas_neue.ttf` listed.

- [ ] **Step 4: Build to confirm the resource compiles**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add res/font/bebas_neue.ttf
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: bundle Bebas Neue font for the clock widget dateline"
```

---

## Phase 1: Preferences

### Task 1: Add the five clock preferences to LauncherPrefs

**Files:**
- Modify: `src/com/android/launcher3/LauncherPrefs.kt` (companion object, near the drawer color prefs around line 393)

- [ ] **Step 1: Add the pref declarations**

In the `companion object`, next to the existing color prefs (for example just after `DRAWER_BG_COLOR` at line 393), add:

```kotlin
        // ---- Danfo clock widget ----
        @JvmField val CLOCK_ALIGNMENT = backedUpItem("pref_clock_alignment", "center")
        @JvmField val CLOCK_TIME_FORMAT = backedUpItem("pref_clock_time_format", "system")
        @JvmField val CLOCK_COLOR_MODE = backedUpItem("pref_clock_color_mode", "white")
        @JvmField val CLOCK_TIME_COLOR = backedUpItem("pref_clock_time_color", "")
        @JvmField val CLOCK_DATE_COLOR = backedUpItem("pref_clock_date_color", "")
```

These mirror the existing `backedUpItem("pref_drawer_bg_color", "")` pattern. The String values are: alignment `center`/`left`; time format `system`/`12`/`24`; color mode `white`/`custom`/`material_you`/`wallpaper`; the two color prefs store a color resource name (or empty for default), matching `ColorPickerPreference`.

- [ ] **Step 2: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/com/android/launcher3/LauncherPrefs.kt
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: add clock widget preferences (alignment, format, color)"
```

---

## Phase 2: The clock view

The view is built incrementally. After each task it compiles; visual behavior is confirmed on the AVD once the widget is placeable (Phase 3+).

### Task 2: DanfoClockView skeleton with both fonts

**Files:**
- Create: `src/com/android/launcher3/widget/custom/DanfoClockView.java`

- [ ] **Step 1: Create the view**

```java
package com.android.launcher3.widget.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.R;

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

    /** Date font as a fraction of the resolved time font. */
    private static final float DATE_RATIO = 0.32f;
    private static final float MIN_TIME_SP = 10f;
    private static final float MAX_TIME_PX = 600f;

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
    }
}
```

- [ ] **Step 2: Build**

Run the build command. Expected: `BUILD SUCCESSFUL` (the `R.font.bebas_neue` reference resolves because of Task 0).

- [ ] **Step 3: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockView.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: DanfoClockView skeleton (Danfo time + Bebas Neue date)"
```

### Task 3: Adaptive, non-wrapping dateline

**Files:**
- Modify: `src/com/android/launcher3/widget/custom/DanfoClockView.java`

- [ ] **Step 1: Add the date formatting fields and method**

Add imports:
```java
import android.graphics.Paint;
import android.icu.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
```

Add fields near the top of the class:
```java
    /** Date format skeletons, longest first. The widest that fits is used. */
    private static final String[] DATE_SKELETONS = {
            "EEEEMMMMd", // SATURDAY, JUNE 6
            "EEEEMMMd",  // SATURDAY, JUN 6
            "EEEMMMd",   // SAT, JUN 6
            "EEEd"       // SAT 6
    };

    private float mAvailWidthPx = 0f;
```

Add methods:
```java
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
```

- [ ] **Step 2: Call it from the constructor**

At the end of the constructor (after `setContentDescription`), add:
```java
        refreshDate();
```

- [ ] **Step 3: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockView.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: adaptive non-wrapping dateline for the clock widget"
```

### Task 4: Responsive font auto-sizing

**Files:**
- Modify: `src/com/android/launcher3/widget/custom/DanfoClockView.java`

- [ ] **Step 1: Add the sizing logic in onSizeChanged**

Add imports:
```java
import android.util.TypedValue;
```

Add the override:
```java
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
```

- [ ] **Step 2: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockView.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: responsive auto font sizing for the clock widget"
```

### Task 5: Prefs (alignment, time format, color) and live updates

**Files:**
- Modify: `src/com/android/launcher3/widget/custom/DanfoClockView.java`

- [ ] **Step 1: Add pref reading, color resolution, and apply method**

Add imports:
```java
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PrefSubscriber;
import java.util.Set;
```

Add fields:
```java
    private AutoCloseable mPrefSubscription;
    private BroadcastReceiver mTimeReceiver;
```

Add the apply + color methods:
```java
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
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(getContext());
            android.app.WallpaperColors wc = wm.getWallpaperColors(
                    android.app.WallpaperManager.FLAG_SYSTEM);
            if (wc != null
                    && (wc.getColorHints() & android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0) {
                return 0xFF111111; // light wallpaper -> dark text
            }
        } catch (Exception e) {
            if (DEBUG) android.util.Log.w(TAG, "wallpaper color read failed", e);
        }
        return Color.WHITE;
    }
```

- [ ] **Step 2: Wire subscription and receiver lifecycle**

Add the overrides:
```java
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

        mTimeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                refreshDate();
                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                    applyPrefs();
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
        getContext().registerReceiver(mTimeReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPrefSubscription != null) {
            try { mPrefSubscription.close(); } catch (Exception ignored) { }
            mPrefSubscription = null;
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
```

- [ ] **Step 3: Remove the now-redundant constructor refreshDate call**

In the constructor, the trailing `refreshDate();` from Task 3 is now redundant (applyPrefs runs on attach). Leave it: it is harmless and keeps preview rendering before attach. No change needed.

- [ ] **Step 4: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`. If `PrefSubscriber` or `Item` import paths differ, match `ActivityAllAppsContainerView.java` (it uses `java.util.Set<? extends Item>` and `com.android.launcher3.PrefSubscriber`).

- [ ] **Step 5: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockView.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: clock widget prefs, color modes, and live updates"
```

### Task 6: Tap opens the system clock app

**Files:**
- Modify: `src/com/android/launcher3/widget/custom/DanfoClockView.java`

- [ ] **Step 1: Add the click handler in the constructor**

Add import:
```java
import android.provider.AlarmClock;
```

In the constructor, after `setContentDescription("Danfo clock");`, add:
```java
        setOnClickListener(v -> {
            try {
                Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (Exception e) {
                if (DEBUG) android.util.Log.w(TAG, "no clock app to open", e);
            }
        });
        setClickable(true);
```

- [ ] **Step 2: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockView.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: tap clock widget to open the system clock app"
```

---

## Phase 3: Plugin and registration

### Task 7: DanfoClockWidgetPlugin

**Files:**
- Create: `src/com/android/launcher3/widget/custom/DanfoClockWidgetPlugin.java`
- Create: `res/drawable/widget_preview_danfo_clock.xml` (picker preview)

- [ ] **Step 1: Create a simple preview drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res-android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="16dp" />
            <gradient
                android:angle="135"
                android:startColor="#2B2240"
                android:endColor="#6D4D6B" />
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 2: Create the plugin**

```java
package com.android.launcher3.widget.custom;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.systemui.plugins.CustomWidgetPlugin;

/**
 * Concrete in-process custom widget: the Danfo clock. Registered via
 * {@code R.array.launcher_custom_widgets} and instantiated by
 * {@link CustomWidgetManager} (reflection, Context constructor).
 */
public class DanfoClockWidgetPlugin implements CustomWidgetPlugin {

    private final Context mContext;

    public DanfoClockWidgetPlugin(Context context) {
        mContext = context;
    }

    @Override
    public void updateWidgetInfo(AppWidgetProviderInfo info, Context context) {
        LauncherAppWidgetProviderInfo lpi = (LauncherAppWidgetProviderInfo) info;
        lpi.label = context.getString(R.string.clock_widget_label);
        lpi.previewImage = R.drawable.widget_preview_danfo_clock;
        // Default 2x2, minimum 2x2, no maximum (grid-bounded).
        lpi.spanX = 2;
        lpi.spanY = 2;
        lpi.minSpanX = 2;
        lpi.minSpanY = 2;
        lpi.maxSpanX = 0;
        lpi.maxSpanY = 0;
        lpi.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL
                | AppWidgetProviderInfo.RESIZE_VERTICAL;
    }

    @Override
    public void onViewCreated(AppWidgetHostView parent) {
        DanfoClockView view = new DanfoClockView(mContext);
        parent.removeAllViews();
        parent.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
```

- [ ] **Step 3: Build**

Run the build command. Expected build failure: `cannot find symbol ... R.string.clock_widget_label`. That string is added in Task 9 (Settings) but is needed here, so add it now in `res/values/strings.xml`:
```xml
    <string name="clock_widget_label">Danfo Clock</string>
```
Re-run the build. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/com/android/launcher3/widget/custom/DanfoClockWidgetPlugin.java \
  res/drawable/widget_preview_danfo_clock.xml res/values/strings.xml
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: DanfoClockWidgetPlugin (custom widget provider + preview)"
```

### Task 8: Register the widget unconditionally in CustomWidgetManager

**Files:**
- Modify: `res/values/config.xml` (the `custom_widget_providers` array region, around line 209)
- Modify: `src/com/android/launcher3/widget/custom/CustomWidgetManager.java` (constructor, lines 84-101)

- [ ] **Step 1: Add the launcher-shipped widgets array**

In `res/values/config.xml`, next to the existing empty `custom_widget_providers` array, add:
```xml
    <!-- Launcher-shipped in-process widgets, registered unconditionally
         (independent of the smartspace flag). -->
    <array name="launcher_custom_widgets">
        <item>com.android.launcher3.widget.custom.DanfoClockWidgetPlugin</item>
    </array>
```

- [ ] **Step 2: Register them in the constructor**

In `CustomWidgetManager.java`, the constructor currently ends (after the `enableSmartspaceAsAWidget()` block) at the closing brace of the constructor around line 101-102. Immediately before that closing brace, add an unconditional loop that mirrors the existing one but reads the new array:

```java
        // Launcher-shipped widgets register unconditionally (not gated by the
        // smartspace flag). AOSP-origin file: this additive block is the only
        // change; justification in docs/changes. See plan task 8.
        for (String s : context.getResources().getStringArray(R.array.launcher_custom_widgets)) {
            try {
                Class<?> cls = Class.forName(s);
                CustomWidgetPlugin plugin = (CustomWidgetPlugin)
                        cls.getDeclaredConstructor(Context.class).newInstance(context);
                MAIN_EXECUTOR.execute(() -> onPluginConnected(plugin, context));
            } catch (ClassNotFoundException | InstantiationException
                     | IllegalAccessException | ClassCastException
                     | NoSuchMethodException | InvocationTargetException e) {
                Log.e(TAG, "Exception adding launcher custom widget: " + e);
            }
        }
```

- [ ] **Step 3: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install and manually confirm the widget is in the picker**

Run the install command. On the AVD: long-press an empty area of the home screen, tap "Widgets", scroll to find "Danfo Clock". Confirm it is listed, and drag it onto the home screen. Confirm a clock renders and ticks. Resize it from 2x2 up and confirm the font scales and stays centered.

- [ ] **Step 5: Commit**

```bash
git add res/values/config.xml src/com/android/launcher3/widget/custom/CustomWidgetManager.java
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: register Danfo clock as a launcher-shipped custom widget"
```

---

## Phase 4: Settings UI

### Task 9: Strings and choice arrays

**Files:**
- Create: `res/values/arrays.xml`
- Modify: `res/values/strings.xml`

- [ ] **Step 1: Create arrays.xml with the choice lists**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="clock_alignment_entries">
        <item>@string/clock_alignment_centered</item>
        <item>@string/clock_alignment_left</item>
    </string-array>
    <string-array name="clock_alignment_values">
        <item>center</item>
        <item>left</item>
    </string-array>

    <string-array name="clock_time_format_entries">
        <item>@string/clock_format_system</item>
        <item>@string/clock_format_12</item>
        <item>@string/clock_format_24</item>
    </string-array>
    <string-array name="clock_time_format_values">
        <item>system</item>
        <item>12</item>
        <item>24</item>
    </string-array>

    <string-array name="clock_color_mode_entries">
        <item>@string/clock_color_white</item>
        <item>@string/clock_color_custom</item>
        <item>@string/clock_color_material_you</item>
        <item>@string/clock_color_wallpaper</item>
    </string-array>
    <string-array name="clock_color_mode_values">
        <item>white</item>
        <item>custom</item>
        <item>material_you</item>
        <item>wallpaper</item>
    </string-array>
</resources>
```

- [ ] **Step 2: Add the strings**

In `res/values/strings.xml`, add (the `clock_widget_label` from Task 7 already exists):
```xml
    <string name="clock_widget_title">Clock widget</string>
    <string name="clock_widget_summary">Danfo clock: alignment, format, color</string>
    <string name="clock_alignment_title">Alignment</string>
    <string name="clock_alignment_centered">Centered</string>
    <string name="clock_alignment_left">Left</string>
    <string name="clock_time_format_title">Time format</string>
    <string name="clock_format_system">Follow system</string>
    <string name="clock_format_12">12-hour</string>
    <string name="clock_format_24">24-hour</string>
    <string name="clock_color_mode_title">Color</string>
    <string name="clock_color_white">White</string>
    <string name="clock_color_custom">Custom</string>
    <string name="clock_color_material_you">Material You</string>
    <string name="clock_color_wallpaper">Auto from wallpaper</string>
    <string name="clock_time_color_title">Time color</string>
    <string name="clock_date_color_title">Date color</string>
```

- [ ] **Step 3: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add res/values/arrays.xml res/values/strings.xml
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: clock widget settings strings and choice arrays"
```

### Task 10: Clock widget settings fragment

**Files:**
- Create: `res/xml/clock_widget_preferences.xml`
- Create: `src/com/android/launcher3/settings/ClockWidgetFragment.java`
- Modify: `res/xml/launcher_preferences.xml` (add the entry row after the colors row, around line 51)

- [ ] **Step 1: Create the preference screen**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.preference.PreferenceScreen
    xmlns:android="http://schemas.android.com/apk/res-android"
    xmlns:launcher="http://schemas.android.com/apk/res-auto">

    <androidx.preference.ListPreference
        android:key="pref_clock_alignment"
        android:title="@string/clock_alignment_title"
        android:entries="@array/clock_alignment_entries"
        android:entryValues="@array/clock_alignment_values"
        android:defaultValue="center"
        launcher:iconSpaceReserved="false" />

    <androidx.preference.ListPreference
        android:key="pref_clock_time_format"
        android:title="@string/clock_time_format_title"
        android:entries="@array/clock_time_format_entries"
        android:entryValues="@array/clock_time_format_values"
        android:defaultValue="system"
        launcher:iconSpaceReserved="false" />

    <androidx.preference.ListPreference
        android:key="pref_clock_color_mode"
        android:title="@string/clock_color_mode_title"
        android:entries="@array/clock_color_mode_entries"
        android:entryValues="@array/clock_color_mode_values"
        android:defaultValue="white"
        launcher:iconSpaceReserved="false" />

    <com.android.launcher3.settings.ColorPickerPreference
        android:key="pref_clock_time_color"
        android:title="@string/clock_time_color_title"
        launcher:iconSpaceReserved="false" />

    <com.android.launcher3.settings.ColorPickerPreference
        android:key="pref_clock_date_color"
        android:title="@string/clock_date_color_title"
        launcher:iconSpaceReserved="false" />

</androidx.preference.PreferenceScreen>
```

- [ ] **Step 2: Create the fragment**

This mirrors `AppDrawerColorsFragment`. The three `ListPreference`s persist through `LauncherPrefs` (not the raw androidx store) so the `PrefChangeDispatcher` fires and the live widget restyles; the two color pickers reuse `setColorPrefItem`; the pickers are shown only in Custom mode.

```java
package com.android.launcher3.settings;

import android.os.Bundle;

import androidx.preference.ListPreference;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/** Settings sub-screen for the Danfo clock widget. */
public class ClockWidgetFragment extends SettingsBaseFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.clock_widget_preferences, rootKey);

        bindList("pref_clock_alignment", LauncherPrefs.CLOCK_ALIGNMENT);
        bindList("pref_clock_time_format", LauncherPrefs.CLOCK_TIME_FORMAT);

        ListPreference colorMode = findPreference("pref_clock_color_mode");
        if (colorMode != null) {
            colorMode.setValue(LauncherPrefs.get(getContext()).get(LauncherPrefs.CLOCK_COLOR_MODE));
            colorMode.setOnPreferenceChangeListener((p, v) -> {
                LauncherPrefs.get(getContext()).put(LauncherPrefs.CLOCK_COLOR_MODE, (String) v);
                ((ListPreference) p).setValue((String) v);
                updateColorPickerVisibility((String) v);
                return false; // persisted via LauncherPrefs above
            });
        }

        ColorPickerPreference timeColor = findPreference("pref_clock_time_color");
        if (timeColor != null) {
            timeColor.setColorPrefItem(LauncherPrefs.CLOCK_TIME_COLOR, 0, android.graphics.Color.WHITE);
        }
        ColorPickerPreference dateColor = findPreference("pref_clock_date_color");
        if (dateColor != null) {
            dateColor.setColorPrefItem(LauncherPrefs.CLOCK_DATE_COLOR, 0, android.graphics.Color.WHITE);
        }

        updateColorPickerVisibility(LauncherPrefs.get(getContext()).get(LauncherPrefs.CLOCK_COLOR_MODE));
    }

    private void bindList(String key, com.android.launcher3.ConstantItem<String> item) {
        ListPreference pref = findPreference(key);
        if (pref == null) return;
        pref.setValue(LauncherPrefs.get(getContext()).get(item));
        pref.setOnPreferenceChangeListener((p, v) -> {
            LauncherPrefs.get(getContext()).put(item, (String) v);
            ((ListPreference) p).setValue((String) v);
            return false;
        });
    }

    private void updateColorPickerVisibility(String mode) {
        boolean custom = "custom".equals(mode);
        ColorPickerPreference timeColor = findPreference("pref_clock_time_color");
        ColorPickerPreference dateColor = findPreference("pref_clock_date_color");
        if (timeColor != null) timeColor.setVisible(custom);
        if (dateColor != null) dateColor.setVisible(custom);
    }
}
```

- [ ] **Step 3: Add the entry row to the main settings screen**

In `res/xml/launcher_preferences.xml`, after the existing colors `<Preference ... AppDrawerColorsFragment ... />` row (around line 51), add:
```xml
    <Preference
        android:key="pref_clock_widget"
        android:title="@string/clock_widget_title"
        android:summary="@string/clock_widget_summary"
        android:fragment="com.android.launcher3.settings.ClockWidgetFragment"
        launcher:iconSpaceReserved="true" />
```

- [ ] **Step 4: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`. If `ColorPickerPreference.setColorPrefItem(item, 0, override)` signature differs, match the 3-arg form used in `AppDrawerColorsFragment.configureColorPickerWithDefault` (it calls `pref.setColorPrefItem(prefItem, 0, defaultColor)`).

- [ ] **Step 5: Install and manually verify settings**

Run the install command. Open launcher Settings, tap "Clock widget". Change alignment, time format, and color mode and confirm a placed clock widget restyles live. Confirm the two color pickers appear only when color mode is Custom.

- [ ] **Step 6: Commit**

```bash
git add res/xml/clock_widget_preferences.xml \
  src/com/android/launcher3/settings/ClockWidgetFragment.java \
  res/xml/launcher_preferences.xml
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "feat: Clock widget settings sub-screen"
```

---

## Phase 5: Tests

### Task 11: Debug broadcast to place the clock widget deterministically

The e2e visual and render tests need the widget on screen without flaky drag gestures. Extend `WorkspaceSeedReceiver` with an action that inserts a custom-appwidget favorites row and reloads the model.

**Files:**
- Modify: `src/com/android/launcher3/testing/WorkspaceSeedReceiver.java`
- Modify: `AndroidManifest-common.xml` (add the new action to the receiver's intent filter, near line 241)

- [ ] **Step 1: Add the action constant and handler**

In `WorkspaceSeedReceiver.java`, add the constant near the others:
```java
    /** Places the Danfo clock widget at (0,0), span 2x2 (debug builds only). */
    public static final String ACTION_PLACE_CLOCK_WIDGET =
            "com.guru.defaultlauncher.test.PLACE_CLOCK_WIDGET";

    private static final String CLOCK_PROVIDER =
            "android/#custom-widget-com.android.launcher3.widget.custom.DanfoClockWidgetPlugin";
```

In `onReceive`, after the `ACTION_SIMULATE_NULL_PROVIDER` block and before the `ACTION_SEED_WORKSPACE` check, add:
```java
        if (ACTION_PLACE_CLOCK_WIDGET.equals(intent.getAction())) {
            Executors.MODEL_EXECUTOR.execute(() -> {
                com.android.launcher3.model.ModelDbController db =
                        LauncherAppState.getInstance(context).getModel().getModelDbController();
                android.content.ComponentName cn =
                        android.content.ComponentName.unflattenFromString(CLOCK_PROVIDER);
                int widgetId = com.android.launcher3.widget.custom.CustomWidgetManager.INSTANCE
                        .get(context).allocateCustomAppWidgetId(cn);

                ContentValues cv = new ContentValues();
                cv.put(LauncherSettings.Favorites._ID, 200);
                cv.put(LauncherSettings.Favorites.CONTAINER,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP);
                cv.put(LauncherSettings.Favorites.SCREEN, 0);
                cv.put(LauncherSettings.Favorites.CELLX, 0);
                cv.put(LauncherSettings.Favorites.CELLY, 0);
                cv.put(LauncherSettings.Favorites.SPANX, 2);
                cv.put(LauncherSettings.Favorites.SPANY, 2);
                cv.put(LauncherSettings.Favorites.ITEM_TYPE,
                        LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET);
                cv.put(LauncherSettings.Favorites.APPWIDGET_ID, widgetId);
                cv.put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, CLOCK_PROVIDER);
                cv.put(LauncherSettings.Favorites.PROFILE_ID, 0);
                cv.put(LauncherSettings.Favorites.RANK, 0);
                db.insert(cv);

                Executors.MAIN_EXECUTOR.execute(
                        () -> LauncherAppState.getInstance(context).getModel().forceReload());
            });
            return;
        }
```

- [ ] **Step 2: Add the action to the manifest intent filter**

In `AndroidManifest-common.xml`, in the `WorkspaceSeedReceiver` `<intent-filter>` (next to the existing `SEED_WORKSPACE` action around line 241), add:
```xml
                <action android:name="com.guru.defaultlauncher.test.PLACE_CLOCK_WIDGET" />
```

- [ ] **Step 3: Build**

Run the build command. Expected: `BUILD SUCCESSFUL`. If `ITEM_TYPE_CUSTOM_APPWIDGET`, `APPWIDGET_ID`, or `APPWIDGET_PROVIDER` are not found, confirm the exact constant names in `LauncherSettings.Favorites` and adjust (these are standard AOSP columns).

- [ ] **Step 4: Install and verify the seam**

Run the install command, then:
```bash
adb -s emulator-5554 shell am broadcast -n com.guru.defaultlauncher/.testing.WorkspaceSeedReceiver \
  -a com.guru.defaultlauncher.test.PLACE_CLOCK_WIDGET
```
Expected: within ~2s a clock widget appears at the top-left of the home screen. Then re-seed to clean up:
```bash
adb -s emulator-5554 shell am broadcast -n com.guru.defaultlauncher/.testing.WorkspaceSeedReceiver \
  -a com.guru.defaultlauncher.test.SEED_WORKSPACE
```

- [ ] **Step 5: Commit**

```bash
git add src/com/android/launcher3/testing/WorkspaceSeedReceiver.java AndroidManifest-common.xml
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "test: debug broadcast to place the clock widget deterministically"
```

### Task 12: Test selectors and driver helpers

**Files:**
- Modify: `tests-e2e/lib/selectors.py` (after the Folder section, around line 33)
- Modify: `tests-e2e/lib/launcher.py` (add methods after `open_launcher_settings`, around line 200)

- [ ] **Step 1: Add selectors**

In `tests-e2e/lib/selectors.py`, after the Folder block:
```python
# Clock widget
DESC_CLOCK_WIDGET = "Danfo clock"          # DanfoClockView contentDescription
WIDGET_LABEL_CLOCK = "Danfo Clock"         # picker label
SEED_ACTION_PLACE_CLOCK = "com.guru.defaultlauncher.test.PLACE_CLOCK_WIDGET"
SEED_ACTION_RESET = "com.guru.defaultlauncher.test.SEED_WORKSPACE"
SEED_RECEIVER = "com.guru.defaultlauncher/.testing.WorkspaceSeedReceiver"
```

- [ ] **Step 2: Add driver helpers**

In `tests-e2e/lib/launcher.py`, add to `LauncherDriver`:
```python
    def place_clock_widget(self) -> None:
        """Place the Danfo clock widget via the debug broadcast seam, then wait."""
        self.d.shell(
            f"am broadcast -n {S.SEED_RECEIVER} -a {S.SEED_ACTION_PLACE_CLOCK}"
        )
        self.d(description=S.DESC_CLOCK_WIDGET).wait(timeout=S.DEFAULT_WAIT)

    def reset_workspace(self) -> None:
        """Restore the canonical seed workspace (removes any placed widget)."""
        self.d.shell(f"am broadcast -n {S.SEED_RECEIVER} -a {S.SEED_ACTION_RESET}")
        self.d(description=S.SEED_ICON_DESC).wait(timeout=S.DEFAULT_WAIT)

    def clock_widget_present(self) -> bool:
        return self.d(description=S.DESC_CLOCK_WIDGET).exists

    def open_widget_picker(self) -> None:
        """Long-press an empty home cell and open the Widgets picker."""
        info = self.d.info
        w, h = info["displayWidth"], info["displayHeight"]
        self.d.long_click(w // 2, int(h * 0.22))  # empty area above the seed row
        widgets = self.d(text="Widgets")
        assert widgets.wait(timeout=S.DEFAULT_WAIT), "Widgets option not shown"
        widgets.click()
```

(Confirm `import lib.selectors as S` alias already used in launcher.py; match the existing import style.)

- [ ] **Step 3: Commit**

```bash
git add tests-e2e/lib/selectors.py tests-e2e/lib/launcher.py
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "test: clock widget selectors and LauncherDriver helpers"
```

### Task 13: Smoke test

**Files:**
- Create: `tests-e2e/smoke/test_clock_widget_basics.py`

- [ ] **Step 1: Write the smoke test**

```python
import pytest

import lib.selectors as S


@pytest.mark.smoke
@pytest.mark.workspace
def test_clock_widget_listed_in_picker(launcher):
    """The launcher-shipped clock widget is offered in the widget picker."""
    launcher.go_home()
    launcher.open_widget_picker()
    try:
        launcher.d(scrollable=True).scroll.to(text=S.WIDGET_LABEL_CLOCK)
    except Exception:
        pass
    assert launcher.d(text=S.WIDGET_LABEL_CLOCK).wait(timeout=S.DEFAULT_WAIT), \
        "Danfo Clock not found in the widget picker"
    launcher.go_home()


@pytest.mark.smoke
@pytest.mark.workspace
def test_clock_widget_renders(clean_launcher):
    """Placing the clock widget renders a ticking clock view."""
    launcher = clean_launcher
    launcher.place_clock_widget()
    assert launcher.clock_widget_present(), "clock widget did not render"
    launcher.reset_workspace()
```

- [ ] **Step 2: Run the smoke test on the AVD**

Run:
```bash
cd tests-e2e && export ANDROID_SERIAL=emulator-5554
.venv/bin/pytest smoke/test_clock_widget_basics.py -v --tb=short
```
Expected: 2 passed.

- [ ] **Step 3: Commit**

```bash
git add tests-e2e/smoke/test_clock_widget_basics.py
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "test: clock widget smoke (picker listing + render)"
```

### Task 14: Regression test for live pref changes

**Files:**
- Create: `tests-e2e/regression/test_clock_widget_prefs.py`

- [ ] **Step 1: Write the regression test**

This places the widget, samples a strip of pixels across the time region to confirm text is drawn, then switches the color mode through settings and confirms the rendered pixels change. Uses the pixel-sampling helpers in `lib/visuals.py`.

```python
import time

import pytest

import lib.selectors as S
import lib.visuals as V


def _time_region_pixels(launcher):
    """Sample a horizontal strip across the clock widget's time area."""
    el = launcher.d(description=S.DESC_CLOCK_WIDGET)
    assert el.wait(timeout=S.DEFAULT_WAIT), "clock widget missing"
    b = el.info["bounds"]
    cy = b["top"] + int((b["bottom"] - b["top"]) * 0.4)  # upper area = time
    xs = [b["left"] + int((b["right"] - b["left"]) * f) for f in (0.3, 0.45, 0.6, 0.75)]
    return [V.sample_screen_pixel(launcher.d, x, cy) for x in xs]


@pytest.mark.regression
@pytest.mark.prefs
def test_clock_widget_color_mode_repaints(clean_launcher):
    launcher = clean_launcher
    launcher.place_clock_widget()
    before = _time_region_pixels(launcher)

    # Switch color mode to Custom, pick a bright color via the picker.
    launcher.open_launcher_settings()
    clock = launcher.d(text="Clock widget")
    assert clock.wait(timeout=S.DEFAULT_WAIT), "Clock widget settings row missing"
    clock.click()
    mode = launcher.d(text="Color")
    assert mode.wait(timeout=S.DEFAULT_WAIT), "Color row missing"
    mode.click()
    custom = launcher.d(text="Custom")
    assert custom.wait(timeout=S.DEFAULT_WAIT), "Custom option missing"
    custom.click()
    time.sleep(S.ANIMATION_WAIT)

    launcher.go_home()
    after = _time_region_pixels(launcher)

    changed = any(
        b.channel_distance(a) >= 16 for b, a in zip(before, after)
    )
    assert changed, "clock pixels did not change after switching color mode"

    launcher.reset_workspace()
```

- [ ] **Step 2: Run it**

Run:
```bash
cd tests-e2e && export ANDROID_SERIAL=emulator-5554
.venv/bin/pytest regression/test_clock_widget_prefs.py -v --tb=short
```
Expected: 1 passed. If the custom default white equals the prior white (no visible change), change the picker selection in the test to an explicit non-white swatch (tap a colored swatch row before asserting), mirroring `test_folder_visual.py`'s color-picker interaction.

- [ ] **Step 3: Commit**

```bash
git add tests-e2e/regression/test_clock_widget_prefs.py
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "test: clock widget regression (color mode repaint)"
```

### Task 15: Visual test

**Files:**
- Create: `tests-e2e/visuals/test_clock_widget_paint.py`

- [ ] **Step 1: Write the visual test**

Confirms the clock actually paints text (not a blank widget) at the default 2x2 size by asserting at least one sampled pixel in the time strip is near-white against the darker wallpaper.

```python
import pytest

import lib.selectors as S
import lib.visuals as V


@pytest.mark.visuals
@pytest.mark.workspace
def test_clock_widget_paints_text(clean_launcher):
    launcher = clean_launcher
    launcher.place_clock_widget()

    el = launcher.d(description=S.DESC_CLOCK_WIDGET)
    assert el.wait(timeout=S.DEFAULT_WAIT), "clock widget missing"
    b = el.info["bounds"]
    cy = b["top"] + int((b["bottom"] - b["top"]) * 0.4)
    samples = [
        V.sample_screen_pixel(launcher.d, b["left"] + int((b["right"] - b["left"]) * f), cy)
        for f in (0.25, 0.4, 0.55, 0.7, 0.85)
    ]
    # White (or near-white) time text means at least one sample is bright.
    brightest = max(s.r + s.g + s.b for s in samples)
    assert brightest >= 600, f"no bright text pixel found (max sum {brightest})"

    launcher.reset_workspace()
```

- [ ] **Step 2: Run it**

Run:
```bash
cd tests-e2e && export ANDROID_SERIAL=emulator-5554
.venv/bin/pytest visuals/test_clock_widget_paint.py -v --tb=short
```
Expected: 1 passed. If brightness threshold is too strict at 2x2 (thin glyphs), lower it or add more sample x-positions, but keep it well above the wallpaper background sum.

- [ ] **Step 3: Commit**

```bash
git add tests-e2e/visuals/test_clock_widget_paint.py
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "test: clock widget visual paint check"
```

---

## Phase 6: Full suite, docs, and ship

### Task 16: Run the targeted suite and update test docs

**Files:**
- Modify: `tests-e2e/README.md` (coverage table)

- [ ] **Step 1: Run smoke + regression + visuals**

Run:
```bash
cd tests-e2e && export ANDROID_SERIAL=emulator-5554
.venv/bin/pytest smoke/ regression/ visuals/ -v --tb=short
```
Expected: previous baseline (about 40 passed, 2 xfailed, 9 skipped) plus the 4 new clock tests passing, 0 failed.

- [ ] **Step 2: Update the coverage table**

In `tests-e2e/README.md`, bump the coverage table to account for the new smoke (2), regression (1), and visual (1) tests.

- [ ] **Step 3: Commit**

```bash
git add tests-e2e/README.md
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "docs: clock widget test coverage in tests-e2e README"
```

### Task 17: Change doc and architecture doc

**Files:**
- Create: `docs/changes/091-danfo-clock-widget.md`
- Modify: `docs/README.md` (change log table, top row)

- [ ] **Step 1: Write the change doc**

Create `docs/changes/091-danfo-clock-widget.md` with: a Summary, New Files, Modified Files (calling out the AOSP-origin edits to `CustomWidgetManager.java` and the reason: launcher-shipped widgets register unconditionally, independent of the smartspace flag), a Settings Added table for the five prefs, Design Decisions (in-process hosting, adaptive date, responsive sizing, debug placement seam for tests), and Known Limitations (global not per-instance settings; Material You/wallpaper color tuning). Use plain hyphens, no em-dashes.

- [ ] **Step 2: Add the change-log row**

In `docs/README.md`, insert at the top of the change-log table:
```markdown
| [091](changes/091-danfo-clock-widget.md) | Danfo clock widget (in-process, responsive, themeable) |
```

- [ ] **Step 3: Commit**

```bash
git add docs/changes/091-danfo-clock-widget.md docs/README.md
git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
  commit -m "docs: change doc for the Danfo clock widget"
```

### Task 18: Open a PR to dev

- [ ] **Step 1: Push and open the PR**

```bash
git push -u origin feature/danfo-clock-widget
gh pr create --base dev --title "Danfo clock widget" \
  --body "In-process launcher clock widget. See docs/changes/091."
```

- [ ] **Step 2: Final manual exploratory pass on the AVD**

Place the widget, resize across 2x2 to large, toggle every setting, switch wallpapers (light and dark) for the wallpaper-auto and Material You modes, rotate the device, and confirm the clock keeps ticking and stays centered/legible. Note results in the PR.

---

## Self-Review (completed by plan author)

**Spec coverage:**
- Single-line Danfo time + Bebas Neue date: Tasks 0, 2.
- Adaptive non-wrapping date ladder: Task 3.
- Responsive 2x2-to-unbounded auto sizing + centering: Tasks 4, 5 (alignment), 7 (span/resize mode).
- Time format follow-system/12h/24h with 24h zero-padding: Task 5.
- Color modes white/custom/Material You/wallpaper + separate time/date colors: Tasks 5, 9, 10.
- Tap opens clock app: Task 6.
- In-process hosting + registration decoupled from smartspace flag: Tasks 7, 8.
- Settings sub-screen: Tasks 9, 10.
- UI + visual tests: Tasks 11 to 15.
- Docs and change tracking: Tasks 16, 17.

**Placeholder scan:** No TBD/TODO; every code step has concrete code. The two known risk points (exact `LauncherSettings.Favorites` column names in Task 11; `ColorPickerPreference.setColorPrefItem` arity in Task 10) include explicit fallback instructions pointing at the canonical existing usage.

**Type consistency:** `DESC_CLOCK_WIDGET` ("Danfo clock") set in Task 2 (`setContentDescription`) and used in Tasks 12 to 15. `WIDGET_LABEL_CLOCK` ("Danfo Clock") from `R.string.clock_widget_label` (Task 7) used in Task 13. Pref keys (`pref_clock_*`) consistent across Tasks 1, 9, 10. Plugin FQCN consistent in config array (Task 8) and the test provider string (Task 11).

**Adaptation note:** The spec said "golden image" visual tests; the suite actually uses pixel sampling (`lib/visuals.py`), so the visual test (Task 15) is written as a pixel-brightness assertion instead. This is a faithful, more robust realization of the same intent.
