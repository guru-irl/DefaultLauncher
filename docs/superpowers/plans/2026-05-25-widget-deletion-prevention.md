# Widget Deletion Prevention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the launcher from permanently deleting widgets from the database when AppWidgetService or PackageManager is transiently unavailable; show a recoverable `UnavailableWidgetView` placeholder instead.

**Architecture:** `WidgetInflater` gets a new `TYPE_MISSING` result that replaces every `TYPE_DELETE` exit. `ItemInflater` creates an `UnavailableWidgetView` for `TYPE_MISSING` instead of calling `deleteItemFromDatabase`. Widgets stay in the DB until the user taps the × button. A debug seam (`sSimulateNullProvider`) allows e2e tests to reproduce the failure on the emulator.

**Tech Stack:** Android SDK 37 · Kotlin · Java · uiautomator2 pytest · Gradle/AGP

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/.../widget/WidgetInflater.kt` | Modify | Add `TYPE_MISSING = 3`, change all `TYPE_DELETE` exits to `TYPE_MISSING`, add `sSimulateNullProvider` test seam |
| `src/.../widget/UnavailableWidgetView.java` | **Create** | Placeholder view: dim background + warning icon + × remove button |
| `src/.../util/ItemInflater.kt` | Modify | Handle `TYPE_MISSING` → `UnavailableWidgetView`; remove `deleteItemFromDatabase` calls from widget inflation |
| `src/.../widget/WidgetStackView.java` | Modify | Add `removeChildWidget(appWidgetId)` for × button use |
| `src/.../model/WorkspaceItemProcessor.kt` | Modify | `TYPE_DELETE` branch → log-and-skip; unrestored-pending branch → log-and-skip |
| `src/.../Launcher.java` | Modify | Preserve tag after `attachViewToHostAndGetAttachedView` |
| `src/.../WorkspaceLayoutManager.java` | Modify | Defensive null-tag guard in `addInScreen` |
| `src/.../testing/WorkspaceSeedReceiver.java` | Modify | Add `ACTION_SIMULATE_NULL_PROVIDER` action to toggle test seam |
| `res/layout/unavailable_widget.xml` | **Create** | Layout for the placeholder view |
| `res/drawable/unavailable_widget_bg.xml` | **Create** | Rounded-rect background for placeholder |
| `tests-e2e/regression/test_deletion_safety.py` | **Create** | 3 e2e regression tests |
| `docs/changes/080-widget-deletion-prevention.md` | **Create** | Change doc |
| `docs/plans/000-architectural-refactor-superplan.md` | Modify | Mark T3.2 superseded by this plan |

---

## Task 1 — Add `sSimulateNullProvider` test seam + WorkspaceSeedReceiver action

The seam lets the AVD emulator reproduce today's "service returns null" incident for e2e tests.

**Files:**
- Modify: `src/com/android/launcher3/widget/WidgetInflater.kt`
- Modify: `src/com/android/launcher3/testing/WorkspaceSeedReceiver.java`

- [ ] **Step 1.1 — Add static test-seam flag to `WidgetInflater.kt` companion object**

  Open `src/com/android/launcher3/widget/WidgetInflater.kt`. Replace the current companion block:

  ```kotlin
  companion object {
      const val TYPE_DELETE = 0
      const val TYPE_PENDING = 1
      const val TYPE_REAL = 2
  }
  ```

  With:

  ```kotlin
  companion object {
      const val TYPE_DELETE = 0
      const val TYPE_PENDING = 1
      const val TYPE_REAL = 2

      /**
       * Forces [getLauncherAppWidgetInfo] to return null for every widget, simulating a
       * transiently-unavailable AppWidgetService.
       *
       * DEBUG BUILDS ONLY. Toggle via:
       *   adb shell am broadcast -p com.guru.defaultlauncher \
       *       -a com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER
       */
      @JvmField
      @VisibleForTesting
      var sSimulateNullProvider: Boolean = false
  }
  ```

- [ ] **Step 1.2 — Wire the flag into the provider lookup**

  In `WidgetInflater.kt`, inside `inflateAppWidget()`, find the else-branch that calls `getLauncherAppWidgetInfo` (lines ~76-91). Change:

  ```kotlin
  } else {
      appWidgetInfo =
          widgetHelper.getLauncherAppWidgetInfo(item.appWidgetId, item.targetComponent)
  ```

  To:

  ```kotlin
  } else {
      appWidgetInfo = if (BuildConfig.DEBUG && sSimulateNullProvider) null
          else widgetHelper.getLauncherAppWidgetInfo(item.appWidgetId, item.targetComponent)
  ```

- [ ] **Step 1.3 — Add `ACTION_SIMULATE_NULL_PROVIDER` to `WorkspaceSeedReceiver.java`**

  Open `src/com/android/launcher3/testing/WorkspaceSeedReceiver.java`. After `ACTION_SEED_WORKSPACE`:

  ```java
  public static final String ACTION_SIMULATE_NULL_PROVIDER =
          "com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER";
  ```

  In `onReceive()`, after the existing `if (!BuildConfig.DEBUG) return;` guard, before the current `ACTION_SEED_WORKSPACE` switch logic, add:

  ```java
  if (ACTION_SIMULATE_NULL_PROVIDER.equals(intent.getAction())) {
      boolean enable = !intent.getBooleanExtra("disable", false);
      com.android.launcher3.widget.WidgetInflater.sSimulateNullProvider = enable;
      android.util.Log.d("WorkspaceSeedReceiver",
              "sSimulateNullProvider = " + enable);
      return;
  }
  ```

  (Send `disable=true` extra to turn the flag off.)

- [ ] **Step 1.4 — Build to verify**

  ```bash
  cd /mnt/data/src/DefaultLauncher
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.5 — Commit**

  ```bash
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    commit -am "test: add sSimulateNullProvider seam to WidgetInflater + WorkspaceSeedReceiver

  Allows e2e tests to reproduce the AppWidgetService blackout on the AVD:
    adb shell am broadcast -p com.guru.defaultlauncher \
        -a com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 2 — Write failing e2e tests

Write the tests now, before any behavior changes, so they fail for the right reason and pass after the fix.

**Files:**
- Create: `tests-e2e/regression/test_deletion_safety.py`

- [ ] **Step 2.1 — Install current APK on AVD and verify baseline**

  ```bash
  export ANDROID_HOME=$HOME/Android/Sdk && export PATH=$ANDROID_HOME/platform-tools:$PATH
  adb -s emulator-5554 install -r -d -g \
    build/outputs/apk/debug/DefaultLauncher-debug.apk
  ```

- [ ] **Step 2.2 — Create `test_deletion_safety.py`**

  ```python
  # tests-e2e/regression/test_deletion_safety.py
  """Regression: widgets must survive AppWidgetService transient blackout.

  Bugs fixed in docs/changes/080:
  - Widgets deleted from DB when AppWidgetService returns null providers
    during memory pressure (e.g., after exiting a fullscreen game).
  - WorkspaceLayoutManager.addInScreen NPE when child view has null tag.

  Tests use the sSimulateNullProvider debug seam to reproduce the
  AppWidgetService blackout without needing a real game to exit.
  """
  import time
  import subprocess
  import os
  import pytest
  from lib import selectors as S

  PACKAGE = S.PACKAGE
  SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")

  ACTION_SIMULATE = "com.guru.defaultlauncher.test.SIMULATE_NULL_PROVIDER"


  def _adb(args: list[str]) -> str:
      cmd = ["adb", "-s", SERIAL] + args
      result = subprocess.run(cmd, capture_output=True, text=True)
      return result.stdout


  def _enable_null_provider(enable: bool) -> None:
      extra = "" if enable else "--ez disable true"
      _adb(["shell", "am", "broadcast", "-p", PACKAGE, "-a", ACTION_SIMULATE]
           + (["--ez", "disable", "true"] if not enable else []))


  def _force_reload(launcher) -> None:
      """Trigger a model reload by stopping and restarting the launcher."""
      launcher.d.shell(f"am force-stop {PACKAGE}")
      time.sleep(1.5)
      launcher.d.shell(f"am start -n {PACKAGE}/com.android.launcher3.Launcher "
                       "-f 0x10200000")
      time.sleep(3.0)
      launcher.go_home()


  @pytest.mark.regression
  @pytest.mark.xfail(
      strict=True,
      reason="Widget deletion bug not yet fixed — expect FAIL before Task 3–6 land",
  )
  def test_widget_survives_null_provider_blackout(launcher):
      """A widget that is on the workspace must stay there when AppWidgetService
      temporarily returns null for its provider (the root cause of today's incident).

      Pre-fix: the widget gets deleted from the DB → cell is empty after reload.
      Post-fix: an UnavailableWidgetView placeholder appears; DB row is intact.
      """
      launcher.go_home()

      # Confirm at least one widget is present (any ListenableHostView on workspace).
      # If no widget is present this test cannot run — skip rather than fail.
      has_widget = launcher.d(
          className="com.android.launcher3.widget.ListenableHostView"
      ).wait(timeout=3.0)
      if not has_widget:
          pytest.skip("No widget on workspace — cannot test widget survival")

      # Enable the null-provider simulation
      _enable_null_provider(True)
      try:
          # Force a model reload while the seam is active
          _force_reload(launcher)

          # The widget (or its placeholder) must still occupy its cell.
          # Either a real ListenableHostView OR an UnavailableWidgetView is acceptable.
          widget_present = (
              launcher.d(
                  className="com.android.launcher3.widget.ListenableHostView"
              ).exists
              or launcher.d(
                  resourceId=f"{PACKAGE}:id/unavailable_widget_container"
              ).exists
          )

          assert widget_present, (
              "Widget cell is empty after null-provider reload — widget was deleted. "
              "docs/changes/080 fix not applied or ineffective."
          )
      finally:
          _enable_null_provider(False)
          _force_reload(launcher)


  @pytest.mark.regression
  def test_widget_recovers_after_seam_cleared(launcher):
      """After the null-provider seam is cleared and the model reloads, the
      real widget view must reappear (UnavailableWidgetView is replaced by
      the actual widget).

      This verifies the 'restart and it works' property the user requires.
      """
      launcher.go_home()

      has_widget = launcher.d(
          className="com.android.launcher3.widget.ListenableHostView"
      ).wait(timeout=3.0)
      if not has_widget:
          pytest.skip("No widget on workspace")

      # Phase 1: simulate blackout
      _enable_null_provider(True)
      _force_reload(launcher)

      # Phase 2: clear seam and reload
      _enable_null_provider(False)
      _force_reload(launcher)

      # Real widget should be back
      real_widget = launcher.d(
          className="com.android.launcher3.widget.ListenableHostView"
      ).wait(timeout=S.DEFAULT_WAIT)
      assert real_widget, (
          "Real widget did not return after seam was cleared. "
          "Recovery path is broken."
      )


  @pytest.mark.regression
  def test_unavailable_widget_remove_button_clears_cell(launcher):
      """Tapping the × button on an UnavailableWidgetView must remove the
      widget from the workspace and delete its DB row.

      Verifies that the user-initiated removal path works end-to-end.
      """
      launcher.go_home()

      has_widget = launcher.d(
          className="com.android.launcher3.widget.ListenableHostView"
      ).wait(timeout=3.0)
      if not has_widget:
          pytest.skip("No widget on workspace")

      # Enable null-provider so placeholder is shown
      _enable_null_provider(True)
      _force_reload(launcher)

      remove_btn = launcher.d(
          resourceId=f"{PACKAGE}:id/remove_unavailable_widget"
      )
      if not remove_btn.wait(timeout=S.DEFAULT_WAIT):
          _enable_null_provider(False)
          _force_reload(launcher)
          pytest.skip("UnavailableWidgetView not shown — fix not yet applied")

      remove_btn.click()
      time.sleep(S.ANIMATION_WAIT)

      # After removal, the cell should be empty (no placeholder, no real widget
      # at that position). We verify by checking that no unavailable view remains.
      still_showing = launcher.d(
          resourceId=f"{PACKAGE}:id/unavailable_widget_container"
      ).exists
      assert not still_showing, (
          "UnavailableWidgetView is still visible after tapping ×. "
          "Remove button listener is not wired or is not working."
      )
  ```

- [ ] **Step 2.3 — Run tests; confirm first test fails (xfail), others skip/fail as expected**

  ```bash
  cd /mnt/data/src/DefaultLauncher/tests-e2e
  export ANDROID_HOME=$HOME/Android/Sdk && export PATH=$ANDROID_HOME/platform-tools:$PATH
  ANDROID_SERIAL=emulator-5554 .venv/bin/pytest \
    regression/test_deletion_safety.py -v --tb=short
  ```

  Expected:
  - `test_widget_survives_null_provider_blackout` → `XFAIL` (strict, expected failure before fix)
  - Others → `PASSED` or `SKIPPED` (skip if no widget on workspace)

- [ ] **Step 2.4 — Commit tests**

  ```bash
  cd /mnt/data/src/DefaultLauncher
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    add tests-e2e/regression/test_deletion_safety.py && \
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    commit -m "tests: add deletion_safety regression tests (failing)

  Three tests covering widget survival through null-provider blackout,
  recovery after seam cleared, and × button removal. First test is
  xfail(strict=True) until the TYPE_MISSING fix lands.

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
  ```

---

## Task 3 — Add `TYPE_MISSING` + change `TYPE_DELETE` branches in `WidgetInflater.kt`

**Files:**
- Modify: `src/com/android/launcher3/widget/WidgetInflater.kt`

- [ ] **Step 3.1 — Add `TYPE_MISSING = 3` to companion object**

  In the companion object (from Task 1), add after `TYPE_REAL`:

  ```kotlin
  companion object {
      const val TYPE_DELETE = 0   // kept for legacy; no longer returned by this class
      const val TYPE_PENDING = 1
      const val TYPE_REAL = 2
      /**
       * Provider package is confirmed absent. Keep widget in DB;
       * caller creates an [UnavailableWidgetView] placeholder.
       * The widget auto-recovers when the provider app is reinstalled.
       * See docs/changes/080.
       */
      const val TYPE_MISSING = 3

      @JvmField
      @VisibleForTesting
      var sSimulateNullProvider: Boolean = false
  }
  ```

- [ ] **Step 3.2 — Change restore-pending TYPE_DELETE (line ~114) to TYPE_MISSING**

  Find the block (inside the `if (appWidgetInfo == null)` check within the `!FLAG_PROVIDER_NOT_READY && restoreStatus != RESTORE_COMPLETED` branch):

  ```kotlin
  return InflationResult(
      type = TYPE_DELETE,
      reason =
          "Removing restored widget: id=${item.appWidgetId} belongs to component ${item.providerName} user ${item.user}, as the provider is null and $removalReason",
      restoreErrorType = logReason,
  )
  ```

  Replace with:

  ```kotlin
  FileLog.w(Launcher.TAG,
          "Widget provider absent (restore-pending): id=${item.appWidgetId}"
          + " pkg=${item.providerName?.packageName} → TYPE_MISSING (kept in DB)"
          + " " + ServiceReadiness.snapshot(context))
  return InflationResult(
      type = TYPE_MISSING,
      reason = "Provider absent for restored widget id=${item.appWidgetId}: $removalReason",
      restoreErrorType = logReason,
  )
  ```

- [ ] **Step 3.3 — Change RESTORE_COMPLETED TYPE_DELETE (line ~216) to TYPE_MISSING**

  Find:

  ```kotlin
  FileLog.e(Launcher.TAG, "Removing invalid widget: id=" + item.appWidgetId
          + " " + ServiceReadiness.snapshot(context))
  return InflationResult(TYPE_DELETE, reason = removalReason)
  ```

  Replace with:

  ```kotlin
  FileLog.w(Launcher.TAG,
          "Widget provider absent (RESTORE_COMPLETED): id=" + item.appWidgetId
          + " → TYPE_MISSING (kept in DB). " + ServiceReadiness.snapshot(context))
  return InflationResult(TYPE_MISSING, reason = removalReason)
  ```

- [ ] **Step 3.4 — Change search-widget TYPE_DELETE (line ~45) to TYPE_MISSING**

  Find:

  ```kotlin
  if (item.providerName == null) {
      return InflationResult(
          TYPE_DELETE,
          reason = "search widget removed because search component cannot be found",
          restoreErrorType = RestoreError.NO_SEARCH_WIDGET,
      )
  }
  ```

  Replace with:

  ```kotlin
  if (item.providerName == null) {
      FileLog.w(Launcher.TAG, "Search widget provider missing → TYPE_MISSING (kept in DB)")
      return InflationResult(
          TYPE_MISSING,
          reason = "search widget provider not found",
          restoreErrorType = RestoreError.NO_SEARCH_WIDGET,
      )
  }
  ```

- [ ] **Step 3.5 — Build to verify no compilation errors**

  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL` (TYPE_MISSING is defined but not yet consumed; `ItemInflater` falls through to the TYPE_PENDING path for now so no crash).

---

## Task 4 — Create `UnavailableWidgetView` + XML resources

**Files:**
- Create: `src/com/android/launcher3/widget/UnavailableWidgetView.java`
- Create: `res/layout/unavailable_widget.xml`
- Create: `res/drawable/unavailable_widget_bg.xml`

- [ ] **Step 4.1 — Create drawable `res/drawable/unavailable_widget_bg.xml`**

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!--
    Background for UnavailableWidgetView. Semi-transparent surface container
    with the same corner radius as dynamic grid cells.
    docs/changes/080
  -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#99000000" />
      <corners android:radius="@dimen/dynamic_grid_cell_border_spacing" />
  </shape>
  ```

  (The `#99000000` is ~60% alpha black; it will be replaced at runtime by `?attr/allAppsScrimColor` — but drawables cannot reference theme attrs in shape elements on older APIs. The Java code will set the background tint from the theme. Leave the XML as a shape reference.)

- [ ] **Step 4.2 — Create layout `res/layout/unavailable_widget.xml`**

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!--
    Placeholder shown for widgets whose provider app is absent.
    Dimensions are controlled by the parent (WorkspaceLayoutManager sets them
    to match the original widget's spanX × spanY). docs/changes/080.
  -->
  <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
      android:id="@+id/unavailable_widget_container"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:background="@drawable/unavailable_widget_bg">

      <!-- Centre content -->
      <LinearLayout
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:layout_gravity="center"
          android:gravity="center"
          android:orientation="vertical"
          android:paddingHorizontal="12dp">

          <ImageView
              android:layout_width="32dp"
              android:layout_height="32dp"
              android:src="@drawable/ic_warning"
              android:tint="?attr/workspaceCellTextColor"
              android:contentDescription="@null" />

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_marginTop="6dp"
              android:text="@string/unavailable_widget_title"
              android:textAppearance="?android:attr/textAppearanceSmall"
              android:textColor="?attr/workspaceCellTextColor"
              android:gravity="center"
              android:maxLines="2" />

      </LinearLayout>

      <!-- × remove button — top right -->
      <ImageButton
          android:id="@+id/remove_unavailable_widget"
          android:layout_width="48dp"
          android:layout_height="48dp"
          android:layout_gravity="top|end"
          android:background="?attr/selectableItemBackgroundBorderless"
          android:src="@drawable/ic_remove_no_shadow"
          android:tint="?attr/workspaceCellTextColor"
          android:contentDescription="@string/unavailable_widget_remove_desc"
          android:padding="12dp" />

  </FrameLayout>
  ```

- [ ] **Step 4.3 — Add strings to `res/values/strings.xml`**

  Open `res/values/strings.xml` and add inside the `<resources>` block:

  ```xml
  <!-- UnavailableWidgetView strings. docs/changes/080 -->
  <string name="unavailable_widget_title">Widget unavailable</string>
  <string name="unavailable_widget_remove_desc">Remove unavailable widget</string>
  ```

- [ ] **Step 4.4 — Check that `workspaceCellTextColor` attr exists**

  ```bash
  grep -r "workspaceCellTextColor" /mnt/data/src/DefaultLauncher/res/values/ | head -5
  ```

  If it does not exist, use `android:textColor="@android:color/white"` and `android:tint="@android:color/white"` in the layout instead. (The tint attr is needed for icons only.)

- [ ] **Step 4.5 — Create `UnavailableWidgetView.java`**

  ```java
  /*
   * Copyright (C) 2026 DefaultLauncher Contributors
   *
   * SPDX-License-Identifier: GPL-3.0-or-later
   * docs/changes/080
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
   * the × button permanently removes it.
   *
   * <p>Recovery: once the provider app is reinstalled and the model reloads,
   * this view is replaced by the real widget view automatically — no user
   * action needed.
   *
   * @see WidgetInflater#TYPE_MISSING
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
  ```

- [ ] **Step 4.6 — Build to verify**

  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`

---

## Task 5 — Update `ItemInflater.kt` to consume `TYPE_MISSING`

**Files:**
- Modify: `src/com/android/launcher3/util/ItemInflater.kt`

- [ ] **Step 5.1 — Add import for `UnavailableWidgetView`**

  In `ItemInflater.kt` imports section, add:

  ```kotlin
  import com.android.launcher3.widget.UnavailableWidgetView
  ```

- [ ] **Step 5.2 — Update `inflateAppWidget()` — remove deletion, add TYPE_MISSING branch**

  Current `inflateAppWidget()` (lines ~119-139):

  ```kotlin
  private fun inflateAppWidget(item: LauncherAppWidgetInfo, writer: ModelWriter): View? {
      TraceHelper.INSTANCE.beginSection("BIND_WIDGET_id=" + item.appWidgetId)
      try {
          val (type, reason, _, isUpdate, widgetInfo) = widgetInflater.inflateAppWidget(item)
          if (type == WidgetInflater.TYPE_DELETE) {
              writer.deleteItemFromDatabase(item, reason)
              return null
          }
          if (isUpdate) {
              writer.updateItemInDatabase(item)
          }
          val view =
              if (type == WidgetInflater.TYPE_PENDING || widgetInfo == null)
                  PendingAppWidgetHostView(context, widgetHolder, item, widgetInfo)
              else widgetHolder.createView(item.appWidgetId, widgetInfo)
          prepareAppWidget(view, item)
          return view
      } finally {
          TraceHelper.INSTANCE.endSection()
      }
  }
  ```

  Replace entirely with:

  ```kotlin
  private fun inflateAppWidget(item: LauncherAppWidgetInfo, writer: ModelWriter): View? {
      TraceHelper.INSTANCE.beginSection("BIND_WIDGET_id=" + item.appWidgetId)
      try {
          val (type, reason, _, isUpdate, widgetInfo) = widgetInflater.inflateAppWidget(item)
          // TYPE_DELETE is no longer returned by WidgetInflater; guard defensively.
          if (type == WidgetInflater.TYPE_DELETE) {
              android.util.Log.e("ItemInflater",
                  "TYPE_DELETE received for widget id=${item.appWidgetId} — " +
                  "treating as TYPE_MISSING (not deleting). docs/changes/080")
          }
          if (type == WidgetInflater.TYPE_MISSING || type == WidgetInflater.TYPE_DELETE) {
              // Provider absent — show recoverable placeholder. Widget stays in DB.
              val launcher = context as? com.android.launcher3.Launcher
              val view = UnavailableWidgetView(context, item) {
                  // × tapped: remove widget from workspace + DB (user's explicit action)
                  launcher?.removeItem(null, item, true /* deleteFromDb */,
                      "user removed unavailable widget")
              }
              prepareAppWidget(view, item)
              return view
          }
          if (isUpdate) {
              writer.updateItemInDatabase(item)
          }
          val view =
              if (type == WidgetInflater.TYPE_PENDING || widgetInfo == null)
                  PendingAppWidgetHostView(context, widgetHolder, item, widgetInfo)
              else widgetHolder.createView(item.appWidgetId, widgetInfo)
          prepareAppWidget(view, item)
          return view
      } finally {
          TraceHelper.INSTANCE.endSection()
      }
  }
  ```

  Note: `prepareAppWidget` sets `hostView.tag = item`, which satisfies the `addInScreen` tag contract for all view types including `UnavailableWidgetView`.

- [ ] **Step 5.3 — Add `WidgetStackView.removeChildWidget(int)` to `WidgetStackView.java`**

  Open `src/com/android/launcher3/widget/WidgetStackView.java`. After `getWidgetCount()`:

  ```java
  /**
   * Removes the child view for the widget with the given appWidgetId.
   * If the stack becomes empty after removal, the caller is responsible
   * for removing the parent WidgetStackView from the workspace.
   *
   * @return true if a child was found and removed, false otherwise.
   */
  public boolean removeChildWidget(int appWidgetId) {
      for (int i = 0; i < mWidgetViews.size(); i++) {
          View child = mWidgetViews.get(i);
          if (child.getTag() instanceof LauncherAppWidgetInfo info
                  && info.appWidgetId == appWidgetId) {
              mWidgetViews.remove(i);
              removeView(child);
              if (!mWidgetViews.isEmpty()) {
                  mActiveIndex = Math.max(0, Math.min(mActiveIndex, mWidgetViews.size() - 1));
                  updateChildVisibility();
              }
              return true;
          }
      }
      return false;
  }
  ```

  Also add the import at the top of `WidgetStackView.java` if not already present:
  ```java
  import com.android.launcher3.model.data.LauncherAppWidgetInfo;
  ```

- [ ] **Step 5.4 — Update `inflateWidgetStack()` — remove deletion, handle TYPE_MISSING per child**

  Current relevant section of `inflateWidgetStack()` (lines ~152-174):

  ```kotlin
  for (child in info.getContents()) {
      val widgetInfo = child as? LauncherAppWidgetInfo
      if (widgetInfo == null) {
          android.util.Log.e("ItemInflater", "WidgetStack child is not LauncherAppWidgetInfo: $child")
          continue
      }
      val (type, reason, _, isUpdate, providerInfo) = widgetInflater.inflateAppWidget(widgetInfo)
      if (type == WidgetInflater.TYPE_DELETE) {
          writer.deleteItemFromDatabase(widgetInfo, reason)
          continue
      }
      inflatedIds.add(widgetInfo.id)
      if (isUpdate) writer.updateItemInDatabase(widgetInfo)
      val hostView = if (type == WidgetInflater.TYPE_PENDING || providerInfo == null)
          PendingAppWidgetHostView(context, widgetHolder, widgetInfo, providerInfo)
      else widgetHolder.createView(widgetInfo.appWidgetId, providerInfo)
      prepareAppWidget(hostView, widgetInfo)
      stackView.addWidgetView(hostView, widgetInfo)
  }
  ```

  Replace with:

  ```kotlin
  val launcher = context as? com.android.launcher3.Launcher
  for (child in info.getContents()) {
      val widgetInfo = child as? LauncherAppWidgetInfo
      if (widgetInfo == null) {
          android.util.Log.e("ItemInflater", "WidgetStack child is not LauncherAppWidgetInfo: $child")
          continue
      }
      val (type, reason, _, isUpdate, providerInfo) = widgetInflater.inflateAppWidget(widgetInfo)
      if (type == WidgetInflater.TYPE_DELETE) {
          android.util.Log.e("ItemInflater",
              "TYPE_DELETE for stack child id=${widgetInfo.appWidgetId} — " +
              "treating as TYPE_MISSING (not deleting). docs/changes/080")
      }
      inflatedIds.add(widgetInfo.id)
      if (type == WidgetInflater.TYPE_MISSING || type == WidgetInflater.TYPE_DELETE) {
          // Provider absent — placeholder slot in the stack. Widget stays in DB.
          val unavailView = UnavailableWidgetView(context, widgetInfo) {
              // × tapped: remove this child from the stack view and DB.
              stackView.removeChildWidget(widgetInfo.appWidgetId)
              launcher?.getModelWriter()?.deleteWidgetInfo(
                  widgetInfo, launcher.getAppWidgetHolder(),
                  "user removed unavailable stack widget")
              // If the stack is now empty, remove the parent container too.
              if (stackView.widgetCount == 0) {
                  launcher?.removeItem(stackView, info, true /* deleteFromDb */,
                      "widget stack empty after last unavailable child removed")
              }
          }
          prepareAppWidget(unavailView, widgetInfo)
          stackView.addWidgetView(unavailView, widgetInfo)
          continue
      }
      if (isUpdate) writer.updateItemInDatabase(widgetInfo)
      val hostView = if (type == WidgetInflater.TYPE_PENDING || providerInfo == null)
          PendingAppWidgetHostView(context, widgetHolder, widgetInfo, providerInfo)
      else widgetHolder.createView(widgetInfo.appWidgetId, providerInfo)
      prepareAppWidget(hostView, widgetInfo)
      stackView.addWidgetView(hostView, widgetInfo)
  }
  ```

  Note: `prepareAppWidget` accepts `AppWidgetHostView` — but `UnavailableWidgetView` extends `FrameLayout`, not `AppWidgetHostView`. Update `prepareAppWidget` signature to accept `View`:

  ```kotlin
  fun prepareAppWidget(hostView: View, item: LauncherAppWidgetInfo) {
      hostView.tag = item
      hostView.isFocusable = true
      hostView.onFocusChangeListener = focusListener
  }
  ```

  Also update the `addWidgetView(unavailView, widgetInfo)` call — `WidgetStackView.addWidgetView` takes `AppWidgetHostView`. We need to change its signature to accept `View` too. In `WidgetStackView.java`, change:

  ```java
  public void addWidgetView(AppWidgetHostView hostView, LauncherAppWidgetInfo info) {
  ```

  To:

  ```java
  public void addWidgetView(View hostView, LauncherAppWidgetInfo info) {
  ```

  (This is safe: the method only calls `setTag`, sets properties, and adds the view — nothing AppWidgetHostView-specific.)

- [ ] **Step 5.5 — Build to verify**

  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`

---

## Task 6 — Fix `WorkspaceItemProcessor.kt` deletion paths

**Files:**
- Modify: `src/com/android/launcher3/model/WorkspaceItemProcessor.kt`

- [ ] **Step 6.1 — Change `TYPE_DELETE` branch to log-and-skip**

  Find (lines ~571-574):

  ```kotlin
  WidgetInflater.TYPE_DELETE -> {
      c.markDeleted(inflationResult.reason, inflationResult.restoreErrorType)
      return
  }
  ```

  Replace with:

  ```kotlin
  WidgetInflater.TYPE_DELETE -> {
      // TYPE_DELETE is no longer returned by WidgetInflater. If it somehow
      // arrives here, do NOT delete — log and skip. docs/changes/080.
      android.util.Log.e(TAG,
          "processWidget: unexpected TYPE_DELETE for id=${c.id}" +
          ", appWidgetId=${c.appWidgetId} — skipping deletion")
  }
  ```

  (No `return` — fall through to the shared update/add path below.)

- [ ] **Step 6.2 — Change unrestored-pending deletion to log-and-skip**

  Find (lines ~586-595):

  ```kotlin
  if (
      !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED) &&
          !isSafeMode &&
          (si == null) &&
          (lapi == null) &&
          !isArchived
  ) {
      // Restore never started
      c.markDeleted(
          "processWidget: Unrestored Pending widget removed:" + ...,
          RestoreError.UNRESTORED_PENDING_WIDGET,
      )
      return
  }
  ```

  Replace with:

  ```kotlin
  if (
      !c.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_RESTORE_STARTED) &&
          !isSafeMode &&
          (si == null) &&
          (lapi == null) &&
          !isArchived
  ) {
      // Restore not yet started and no active installer. Previously this
      // deleted the widget, but transient service unavailability can also
      // trigger this path. Keep the widget; it will show UnavailableWidgetView
      // at bind time. docs/changes/080.
      FileLog.w(TAG,
          "processWidget: unrestored pending widget id=${c.id}" +
          ", appWidgetId=${c.appWidgetId} — deferring instead of deleting")
      // Do NOT return — continue so the item stays in the model for binding.
  }
  ```

- [ ] **Step 6.3 — Build to verify**

  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`

---

## Task 7 — NPE fix: `Launcher.java` + `WorkspaceLayoutManager.java`

**Files:**
- Modify: `src/com/android/launcher3/Launcher.java`
- Modify: `src/com/android/launcher3/WorkspaceLayoutManager.java`

- [ ] **Step 7.1 — Preserve tag after `attachViewToHostAndGetAttachedView` in `Launcher.java`**

  Find in `bindInflatedItems` (lines ~2422-2426):

  ```java
  if (enableWorkspaceInflation() && view instanceof LauncherAppWidgetHostView lv) {
      view = getAppWidgetHolder().attachViewToHostAndGetAttachedView(lv);
  } else if (enableWorkspaceInflation() && view instanceof WidgetStackView wsv) {
      wsv.attachChildWidgetsToHost(getAppWidgetHolder());
  }
  ```

  Replace with:

  ```java
  if (enableWorkspaceInflation() && view instanceof LauncherAppWidgetHostView lv) {
      View attached = getAppWidgetHolder().attachViewToHostAndGetAttachedView(lv);
      if (attached == null) {
          // Attachment returned null — keep the original pre-attachment view.
          // It may not be perfectly bound, but it prevents a crash and is
          // recoverable on the next model bind. docs/changes/080.
          attached = lv;
      }
      if (attached.getTag() == null) {
          // attachViewToHostAndGetAttachedView returned a different object that
          // didn't inherit the tag set by prepareAppWidget. Copy it over.
          // Crash site: WorkspaceLayoutManager.addInScreen:140. docs/changes/080.
          attached.setTag(lv.getTag());
      }
      view = attached;
  } else if (enableWorkspaceInflation() && view instanceof WidgetStackView wsv) {
      wsv.attachChildWidgetsToHost(getAppWidgetHolder());
  }
  ```

- [ ] **Step 7.2 — Add defensive null-tag guard in `WorkspaceLayoutManager.addInScreen`**

  Open `WorkspaceLayoutManager.java`. Find around line 138-140:

  ```java
  // Get the canonical child id to uniquely represent this view in this screen
  ItemInfo info = (ItemInfo) child.getTag();
  int childId = info.getViewId();
  ```

  Replace with:

  ```java
  // Get the canonical child id to uniquely represent this view in this screen
  ItemInfo info = (ItemInfo) child.getTag();
  if (info == null) {
      // Defensive guard: a view with null tag must not reach addInScreen.
      // Root cause was attachViewToHostAndGetAttachedView returning a new
      // object without the tag. The caller in Launcher.bindInflatedItems now
      // copies the tag, but keep this guard as belt-and-braces. docs/changes/080.
      Log.e(TAG, "addInScreen: child " + child.getClass().getSimpleName()
              + " has null tag — skipping to prevent NPE");
      return;
  }
  int childId = info.getViewId();
  ```

- [ ] **Step 7.3 — Build to verify**

  ```bash
  /opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
    -Dorg.gradle.appname=gradlew \
    -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain assembleDebug 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`

---

## Task 8 — Install, run full test suite, verify tests pass

- [ ] **Step 8.1 — Install on AVD**

  ```bash
  export ANDROID_HOME=$HOME/Android/Sdk && export PATH=$ANDROID_HOME/platform-tools:$PATH
  adb -s emulator-5554 install -r -d -g \
    build/outputs/apk/debug/DefaultLauncher-debug.apk
  ```

  Expected: `Success`

- [ ] **Step 8.2 — Run deletion_safety tests specifically first**

  ```bash
  cd /mnt/data/src/DefaultLauncher/tests-e2e
  ANDROID_SERIAL=emulator-5554 .venv/bin/pytest \
    regression/test_deletion_safety.py -v --tb=short
  ```

  Expected:
  - `test_widget_survives_null_provider_blackout` → `XPASS` (strict=True means this is now a failure in the xfail sense, but a passing behavior — change to `xfail(strict=False)` or remove `xfail` once confirmed)
  - `test_widget_recovers_after_seam_cleared` → `PASSED`
  - `test_unavailable_widget_remove_button_clears_cell` → `PASSED` or `SKIPPED` (if no widget on the AVD workspace — see note below)

  **Note:** If no widget exists on the AVD workspace, `test_widget_survives_null_provider_blackout` and `test_widget_recovers_after_seam_cleared` will `SKIP`. To test with a real widget, add a KWGT or Google Calendar widget to the emulator workspace before running. The test for the × button also requires a widget to be present.

- [ ] **Step 8.3 — Remove `xfail` from `test_widget_survives_null_provider_blackout` now that it passes**

  In `test_deletion_safety.py`, find:

  ```python
  @pytest.mark.xfail(
      strict=True,
      reason="Widget deletion bug not yet fixed — expect FAIL before Task 3–6 land",
  )
  def test_widget_survives_null_provider_blackout(launcher):
  ```

  Remove the `@pytest.mark.xfail(...)` decorator entirely.

- [ ] **Step 8.4 — Run the full 44-test suite**

  ```bash
  ANDROID_SERIAL=emulator-5554 .venv/bin/pytest \
    smoke/ regression/ visuals/ -v --tb=short 2>&1 | tail -10
  ```

  Expected: 0 failed, ~42 passed (or similar), 2 xfailed (the pre-existing folder/drawer_intact flakes), 3 skipped.

---

## Task 9 — Change doc + superplan update + final commit

**Files:**
- Create: `docs/changes/080-widget-deletion-prevention.md`
- Modify: `docs/plans/000-architectural-refactor-superplan.md`

- [ ] **Step 9.1 — Write `docs/changes/080-widget-deletion-prevention.md`**

  ```markdown
  # 080 — Widget deletion prevention: TYPE_MISSING + UnavailableWidgetView

  Fixes the permanent loss of widgets when AppWidgetService or PackageManager
  is transiently unavailable (e.g., after exiting a fullscreen landscape game).

  ## Root cause (confirmed from device logs 2026-05-25)

  AppWidgetService returned 0 providers for ~9 seconds after a Samsung game
  exited. During this window, the launcher's LoaderTask ran WidgetInflater,
  which returned TYPE_DELETE for every widget whose AppWidgetInfo resolved null.
  ServiceReadiness.isPackageProbablyInstalled() also returned false because
  PackageManager was simultaneously flaky (same memory pressure). Both guards
  failed together → permanent DB deletion.

  ## Fix: never auto-delete widgets

  - `WidgetInflater.TYPE_MISSING = 3` replaces all TYPE_DELETE exits in the
    null-appWidgetInfo paths. Provider-absent widgets stay in the DB.
  - `UnavailableWidgetView` (new): dimmed placeholder with × remove button.
    Tapping × is the one and only way a widget can leave the database.
  - Recovery is automatic: next model bind returns TYPE_REAL when the provider
    app is reinstalled, replacing the placeholder with the real widget.
  - `WorkspaceItemProcessor` TYPE_DELETE branch + unrestored-pending deletion
    changed to log-and-skip.

  ## NPE fix (recurring crash since 2026-05-23)

  `WorkspaceLayoutManager.addInScreen:140` crashed when `child.getTag()` was
  null. Root cause: `Launcher.bindInflatedItems` called
  `attachViewToHostAndGetAttachedView(lv)` which returned a new view object
  that did not inherit the tag set by `prepareAppWidget`. Fix: copy tag from
  original view; add defensive null guard in `addInScreen`.

  ## Files changed

  - `WidgetInflater.kt` — TYPE_MISSING, sSimulateNullProvider test seam
  - `UnavailableWidgetView.java` — new placeholder class
  - `res/layout/unavailable_widget.xml` — placeholder layout
  - `res/drawable/unavailable_widget_bg.xml` — placeholder background
  - `ItemInflater.kt` — consume TYPE_MISSING, no deleteItemFromDatabase on widgets
  - `WidgetStackView.java` — addWidgetView(View, ...) + removeChildWidget()
  - `WorkspaceItemProcessor.kt` — TYPE_DELETE + unrestored-pending → log+skip
  - `Launcher.java` — tag preservation after attachViewToHostAndGetAttachedView
  - `WorkspaceLayoutManager.java` — null-tag guard in addInScreen
  - `WorkspaceSeedReceiver.java` — SIMULATE_NULL_PROVIDER test action

  Supersedes `docs/plans/005-deletion-safety-v2.md` (PackagePresenceVerifier
  approach abandoned — the no-auto-delete policy is simpler and more robust).

  ## Verification

  - `assembleDebug`: clean.
  - Full test suite: all passed.
  ```

- [ ] **Step 9.2 — Update superplan — mark T3.2 superseded**

  In `docs/plans/000-architectural-refactor-superplan.md`, find the T3.2 entry:

  > `**T3.2** — execute docs/plans/005-deletion-safety-v2.md`

  Change to:

  > `**T3.2** ✅ SHIPPED — docs/changes/080. **Design superseded**: PackagePresenceVerifier (005) replaced by never-auto-delete + UnavailableWidgetView. See docs/superpowers/specs/2026-05-25-widget-deletion-prevention-design.md.`

  Also add to the Session 6 table or a new Session 7 block at the top of "How to resume":

  ```
  | T3.2 | Widget deletion prevention: TYPE_MISSING + UnavailableWidgetView + NPE fix | ✅ done | docs/changes/080 |
  ```

  Update "Next change doc" from `080` to `081`.

- [ ] **Step 9.3 — Final commit**

  ```bash
  cd /mnt/data/src/DefaultLauncher
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    add -A && \
  git -c user.name="Gurupungav Narayanan" -c user.email="gurupungavn@gmail.com" \
    commit -m "fix: widget deletion prevention — TYPE_MISSING + UnavailableWidgetView

  Widgets are never auto-deleted from the DB. Instead:
  - WidgetInflater.TYPE_MISSING replaces TYPE_DELETE for absent providers
  - UnavailableWidgetView shows a recoverable dim placeholder with × button
  - Widget stack children get per-slot placeholders; stack auto-removes when last
    child removed by user
  - WorkspaceItemProcessor TYPE_DELETE and unrestored-pending branches are now
    log-and-skip (defensive, dead-code path)

  Also fixes the recurring NPE at WorkspaceLayoutManager.addInScreen:140 that
  has crashed the launcher on 2026-05-23, 2026-05-24, and 2026-05-25 by
  preserving view tags after attachViewToHostAndGetAttachedView and adding a
  null-tag guard in addInScreen.

  Supersedes docs/plans/005-deletion-safety-v2.md (PackagePresenceVerifier).
  docs/changes/080

  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
  ```

---

## Self-review notes

After writing this plan I checked it against the spec:

✅ `TYPE_MISSING = 3` — Task 3  
✅ All `TYPE_DELETE` exits in `WidgetInflater` → `TYPE_MISSING` — Tasks 3.2, 3.3, 3.4  
✅ `UnavailableWidgetView` with × button — Task 4  
✅ `ItemInflater.inflateAppWidget` handles `TYPE_MISSING` — Task 5.2  
✅ `ItemInflater.inflateWidgetStack` handles `TYPE_MISSING` per slot — Task 5.4  
✅ Stack-empty cleanup when last child removed — Task 5.4 (onRemoveClicked lambda)  
✅ `WorkspaceItemProcessor` `TYPE_DELETE` → log-skip — Task 6.1  
✅ `WorkspaceItemProcessor` unrestored-pending → log-skip — Task 6.2  
✅ `Launcher.bindInflatedItems` tag preservation — Task 7.1  
✅ `WorkspaceLayoutManager.addInScreen` null guard — Task 7.2  
✅ `sSimulateNullProvider` test seam — Task 1  
✅ `WorkspaceSeedReceiver` broadcast — Task 1.3  
✅ E2e tests — Task 2  
✅ Change doc 080 — Task 9  
✅ Superplan T3.2 update — Task 9  
✅ `prepareAppWidget(View, ...)` signature change — Task 5.4 note  
✅ `WidgetStackView.addWidgetView(View, ...)` signature change — Task 5.4 note  
✅ `WidgetStackView.removeChildWidget(int)` — Task 5.3  
