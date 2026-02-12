# Adding Settings to the Launcher

A practical guide for adding new preferences to the launcher's Settings page. Assumes programming experience but no Android background.

> **Prerequisites**: Read [Settings System](../settings.md) first for the architectural overview.

---

## How Android Preferences Work (60-Second Crash Course)

Android has a built-in settings UI framework called the [Preference library](https://developer.android.com/develop/ui/views/components/settings). You define your settings in an XML file (like HTML for a form), and the framework generates the UI, handles clicks, and persists values to a key-value store called `SharedPreferences`.

Key concepts:
- **`PreferenceScreen`** -- the root container (think `<form>`)
- **`SwitchPreference`** -- a toggle (on/off)
- **`ListPreference`** -- a dropdown/dialog with radio buttons
- **`SeekBarPreference`** -- a slider
- **`EditTextPreference`** -- a text input field
- **`PreferenceCategory`** -- a visual group header (think `<fieldset>`)
- **`Preference`** -- a plain clickable row (no widget, used for navigation)

Each preference has a **`key`** (its unique identifier, like an HTML `name` attribute) and an optional **`defaultValue`**. When `android:persistent="true"` (the default), the framework automatically saves/loads the value.

---

## Quick Reference: What to Edit

| What you're adding | Files to touch |
|---|---|
| Simple toggle (switch) | `launcher_preferences.xml`, optionally `SettingsActivity.java` |
| Dropdown (list selection) | `launcher_preferences.xml`, `strings.xml` (for entries) |
| Slider | `launcher_preferences.xml` |
| Text input | `launcher_preferences.xml` |
| Custom dialog | `launcher_preferences.xml`, new Java class, `SettingsActivity.java` |
| Sub-page with its own preferences | `launcher_preferences.xml`, new XML file, `SettingsActivity.java` |
| Reacting to a change at runtime | `LauncherPrefs.kt` (register `Item`), consumer class |

---

## 1. Adding a Switch (Toggle)

This is the simplest case. Let's add a "Show status bar" toggle.

### Step 1: Add to preference XML

**File:** [`res/xml/launcher_preferences.xml`](../../res/xml/launcher_preferences.xml)

```xml
<SwitchPreference
    android:key="pref_show_status_bar"
    android:title="Show status bar"
    android:summary="Display the status bar on the home screen"
    android:defaultValue="true"
    android:persistent="true" />
```

Place it inside the existing `<PreferenceScreen>` tag. Order in the XML = order on screen.

That's it for a basic toggle. The value is automatically saved to SharedPreferences under the key `"pref_show_status_bar"` as a `boolean`.

### Step 2: Read the value elsewhere

**Option A -- One-time read (simplest, no live updates):**

```java
// Anywhere you have a Context
boolean showStatusBar = LauncherPrefs.getPrefs(context)
        .getBoolean("pref_show_status_bar", true);
```

**Option B -- Typed Item with live listener (recommended for runtime changes):**

1. Register the preference as a typed `Item` in [`LauncherPrefs.kt`](../../src/com/android/launcher3/LauncherPrefs.kt):

```kotlin
// In the companion object of LauncherPrefs
@JvmField
val SHOW_STATUS_BAR = backedUpItem("pref_show_status_bar", true)
```

2. Read it:

```java
boolean show = LauncherPrefs.get(context).get(LauncherPrefs.SHOW_STATUS_BAR);
```

3. Listen for changes:

```java
public class MyClass implements LauncherPrefChangeListener {

    public void init(Context context) {
        LauncherPrefs.get(context).addListener(this, LauncherPrefs.SHOW_STATUS_BAR);
    }

    @Override
    public void onPrefChanged(String key) {
        if (LauncherPrefs.SHOW_STATUS_BAR.getSharedPrefKey().equals(key)) {
            // React to the change
        }
    }

    public void destroy(Context context) {
        LauncherPrefs.get(context).removeListener(this, LauncherPrefs.SHOW_STATUS_BAR);
    }
}
```

The [`LauncherPrefChangeListener`](../../src/com/android/launcher3/LauncherPrefChangeListener.java) interface has one method: `onPrefChanged(String key)`. You get the raw string key, so filter by the key you care about.

### Step 3 (optional): Conditionally show/hide the preference

If the toggle should only appear under certain conditions, add a case to `initPreference()` in [`SettingsActivity.java`](../../src/com/android/launcher3/settings/SettingsActivity.java):

```java
// Inside LauncherSettingsFragment.initPreference()
case "pref_show_status_bar":
    // Return false to remove this preference from the screen
    return someCondition;
```

Return `true` to keep it, `false` to remove it.

---

## 2. Adding a Dropdown (List Selection)

A `ListPreference` shows a dialog with radio buttons when tapped.

### Step 1: Define the options in strings.xml

**File:** [`res/values/strings.xml`](../../res/values/strings.xml)

```xml
<!-- Icon size options -->
<string-array name="icon_size_entries">
    <item>Small</item>
    <item>Medium</item>
    <item>Large</item>
</string-array>

<string-array name="icon_size_values">
    <item>small</item>
    <item>medium</item>
    <item>large</item>
</string-array>
```

`entries` = what the user sees. `entryValues` = what gets stored. They're parallel arrays (index 0 of entries maps to index 0 of values).

### Step 2: Add to preference XML

```xml
<ListPreference
    android:key="pref_icon_size"
    android:title="Icon size"
    android:summary="Choose the size of app icons"
    android:entries="@array/icon_size_entries"
    android:entryValues="@array/icon_size_values"
    android:defaultValue="medium"
    android:persistent="true" />
```

The stored value is a `String` (from `entryValues`).

### Step 3: Show the current value as the summary

By default, the summary is static text. To make it reflect the current selection (e.g., show "Medium" instead of "Choose the size of app icons"), use `app:useSimpleSummaryProvider="true"`:

```xml
<ListPreference
    ...
    app:useSimpleSummaryProvider="true" />
```

Add the namespace to the root `PreferenceScreen` if not already there:

```xml
<PreferenceScreen
    xmlns:app="http://schemas.android.com/apk/res-auto"
    ...>
```

### Step 4: Read the value

```java
String iconSize = LauncherPrefs.getPrefs(context)
        .getString("pref_icon_size", "medium");
```

Or define a typed `Item` in `LauncherPrefs.kt`:

```kotlin
@JvmField
val ICON_SIZE = backedUpItem("pref_icon_size", "medium")
```

---

## 3. Adding a Slider

`SeekBarPreference` provides a horizontal slider. It stores an `int`.

```xml
<SeekBarPreference
    android:key="pref_grid_columns"
    android:title="Grid columns"
    android:defaultValue="5"
    android:max="8"
    app:min="3"
    app:showSeekBarValue="true" />
```

| Attribute | Meaning |
|---|---|
| `android:max` | Maximum value |
| `app:min` | Minimum value (note: `app:` namespace, not `android:`) |
| `app:showSeekBarValue` | Show the numeric value next to the slider |
| `android:defaultValue` | Initial value (must be an integer) |

Read it:

```java
int columns = LauncherPrefs.getPrefs(context).getInt("pref_grid_columns", 5);
```

---

## 4. Adding a Text Input

`EditTextPreference` shows a dialog with a text field when tapped.

```xml
<EditTextPreference
    android:key="pref_search_engine_url"
    android:title="Search engine URL"
    android:summary="Base URL for web searches"
    android:defaultValue="https://www.google.com/search?q="
    android:persistent="true"
    app:useSimpleSummaryProvider="true" />
```

The stored value is a `String`.

---

## 5. Adding a Sub-Page (Nested Settings Screen)

When you have a group of related settings, put them on their own page rather than cluttering the main list.

### Approach A: Nested PreferenceScreen (simple, same XML file)

```xml
<!-- In launcher_preferences.xml -->
<PreferenceScreen
    android:key="pref_appearance_screen"
    android:title="Appearance"
    android:summary="Icon size, grid layout, theme">

    <ListPreference
        android:key="pref_icon_size"
        android:title="Icon size"
        ... />

    <SeekBarPreference
        android:key="pref_grid_columns"
        android:title="Grid columns"
        ... />

</PreferenceScreen>
```

When the user taps "Appearance", the framework automatically navigates into the nested screen and shows only the preferences inside it. The parent activity's `onPreferenceStartScreen()` handles this (already implemented in `SettingsActivity`).

### Approach B: Separate fragment and XML file (for more complex pages)

1. **Create a new preferences XML** at `res/xml/appearance_preferences.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">

    <ListPreference
        android:key="pref_icon_size"
        android:title="Icon size"
        ... />

    <SeekBarPreference
        android:key="pref_grid_columns"
        android:title="Grid columns"
        ... />

</PreferenceScreen>
```

2. **Create a new fragment** in `src/com/android/launcher3/settings/AppearanceSettingsFragment.java`:

```java
package com.android.launcher3.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

public class AppearanceSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager()
            .setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.appearance_preferences, rootKey);
    }
}
```

The `setSharedPreferencesName` call is critical -- without it, the fragment uses a different SharedPreferences file and your preferences won't be visible to the launcher.

3. **Link to it from the main preferences** by adding a `Preference` with a `fragment` attribute:

```xml
<!-- In launcher_preferences.xml -->
<Preference
    android:key="pref_appearance"
    android:title="Appearance"
    android:summary="Icon size, grid layout, theme"
    android:fragment="com.android.launcher3.settings.AppearanceSettingsFragment" />
```

When tapped, `SettingsActivity.onPreferenceStartFragment()` picks this up and launches a new `SettingsActivity` instance with the specified fragment (or shows it as a dialog if the fragment extends `DialogFragment`).

---

## 6. Adding a Custom Dialog Preference

For anything that doesn't fit the standard preference types -- a color picker, a multi-select, a custom layout -- create a `DialogFragment` and wire it via the `fragment` attribute.

### Step 1: Create the DialogFragment

```java
package com.android.launcher3.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

public class ThemePickerDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] themes = {"System default", "Light", "Dark"};

        return new AlertDialog.Builder(requireContext())
                .setTitle("Theme")
                .setItems(themes, (dialog, which) -> {
                    String selected = new String[]{"system", "light", "dark"}[which];
                    LauncherPrefs.getPrefs(requireContext())
                        .edit()
                        .putString("pref_theme", selected)
                        .apply();
                })
                .create();
    }
}
```

### Step 2: Wire it in the preference XML

```xml
<Preference
    android:key="pref_theme"
    android:title="Theme"
    android:summary="Choose light, dark, or follow system"
    android:fragment="com.android.launcher3.settings.ThemePickerDialog" />
```

Because `ThemePickerDialog` extends `DialogFragment`, the `startPreference()` method in `SettingsActivity` automatically shows it as a dialog overlay instead of navigating to a new screen.

---

## 7. Grouping Preferences with Categories

Use `PreferenceCategory` to add a header/divider between groups:

```xml
<PreferenceCategory android:title="Home screen">

    <SwitchPreference
        android:key="pref_show_status_bar"
        android:title="Show status bar"
        ... />

    <SwitchPreference
        android:key="pref_allow_rotation"
        android:title="Allow rotation"
        ... />

</PreferenceCategory>

<PreferenceCategory android:title="App drawer">

    <SwitchPreference
        android:key="pref_two_line_labels"
        android:title="Two-line app labels"
        ... />

</PreferenceCategory>
```

---

## 8. Writing Values Programmatically

If you need to set a preference value from code (not from the settings UI):

```java
// Using LauncherPrefs (preferred)
LauncherPrefs.get(context).put(LauncherPrefs.SHOW_STATUS_BAR, false);

// Using raw SharedPreferences (avoid if possible)
LauncherPrefs.getPrefs(context)
    .edit()
    .putBoolean("pref_show_status_bar", false)
    .apply();  // async write -- use .commit() if you need it written before proceeding
```

---

## Full Walkthrough: Toggle with Runtime Reaction

Here's every file you'd touch to add a "Show search bar" toggle that hides/shows the search widget on the home screen.

### 1. `res/xml/launcher_preferences.xml`

```xml
<SwitchPreference
    android:key="pref_show_search_bar"
    android:title="@string/show_search_bar_title"
    android:summary="@string/show_search_bar_summary"
    android:defaultValue="true"
    android:persistent="true" />
```

### 2. `res/values/strings.xml`

```xml
<string name="show_search_bar_title">Show search bar</string>
<string name="show_search_bar_summary">Display search widget on the home screen</string>
```

### 3. `src/com/android/launcher3/LauncherPrefs.kt`

```kotlin
// Inside companion object
@JvmField
val SHOW_SEARCH_BAR = backedUpItem("pref_show_search_bar", true)
```

### 4. Consumer class (wherever the search bar is managed)

```java
public class SearchBarManager implements LauncherPrefChangeListener {

    private final Context mContext;

    public SearchBarManager(Context context) {
        mContext = context;
        LauncherPrefs.get(context).addListener(this, LauncherPrefs.SHOW_SEARCH_BAR);
    }

    @Override
    public void onPrefChanged(String key) {
        if (LauncherPrefs.SHOW_SEARCH_BAR.getSharedPrefKey().equals(key)) {
            boolean visible = LauncherPrefs.get(mContext)
                    .get(LauncherPrefs.SHOW_SEARCH_BAR);
            updateSearchBarVisibility(visible);
        }
    }

    private void updateSearchBarVisibility(boolean visible) {
        // Your implementation here
    }

    public void destroy() {
        LauncherPrefs.get(mContext).removeListener(this, LauncherPrefs.SHOW_SEARCH_BAR);
    }
}
```

---

## Reference: LauncherPrefs Item Types

| Factory Method | Stored Type | Backed Up | Use When |
|---|---|---|---|
| `backedUpItem(key, defaultValue)` | Inferred from `defaultValue` | Yes | Default for most settings |
| `backedUpItem(key, type, defaultFn)` | Explicit `Class` | Yes | Default depends on device state (e.g., tablet vs phone) |
| `nonRestorableItem(key, defaultValue)` | Inferred from `defaultValue` | No | Device-specific values that shouldn't transfer during backup/restore |

The `EncryptionType` parameter controls where the value is physically stored:
- `ENCRYPTED` (default) -- standard credential-encrypted storage, available after first unlock
- `DEVICE_PROTECTED` -- available before the user unlocks the device (boot-aware)

For most custom settings, `backedUpItem("key", defaultValue)` is all you need.

---

## Reference: Preference XML Attributes

Common attributes for all preference types:

| Attribute | Purpose | Example |
|---|---|---|
| `android:key` | Unique identifier / SharedPreferences key | `"pref_icon_size"` |
| `android:title` | Main label shown to user | `"Icon size"` |
| `android:summary` | Subtitle / description | `"Adjust icon size on home screen"` |
| `android:defaultValue` | Default if no value saved yet | `"true"`, `"medium"`, `"5"` |
| `android:persistent` | Auto-save to SharedPreferences | `"true"` (default) |
| `android:enabled` | Grayed out when `false` | `"true"` (default) |
| `android:dependency` | Key of another pref; disabled when that pref is off | `"pref_show_search_bar"` |
| `android:fragment` | Fragment class for sub-navigation | Full class name |
| `android:icon` | Icon shown at the start of the row | `@drawable/ic_setting` |

The `android:dependency` attribute is powerful for conditional enabling. If preference B has `android:dependency="pref_A"`, then B is grayed out whenever A is toggled off. No code needed.

---

## Pitfalls

**SharedPreferences file**: The settings fragment uses `LauncherFiles.SHARED_PREFERENCES_KEY` (`"com.android.launcher3.prefs"`). If you create a new fragment and forget `getPreferenceManager().setSharedPreferencesName(...)`, your preferences silently write to a different file and the launcher never sees them.

**Preference keys must be unique across all XML files**. If two preferences share a key, they'll overwrite each other's values.

**`initPreference()` runs once at fragment creation**. If your visibility condition can change while settings is open (like developer options toggling), you need to call `tryRecreateActivity()` to rebuild the fragment. See [`SettingsActivity.java:362`](../../src/com/android/launcher3/settings/SettingsActivity.java) for the pattern.

**String resources for user-facing text**: Always use `@string/` references instead of hardcoded strings. Android uses these for translations. The examples in this guide use inline strings for readability, but real code should use string resources.

**`launcher:logIdOn` / `launcher:logIdOff`**: The existing preferences have these custom attributes for telemetry logging. You can safely omit them for custom preferences -- they map to AOSP's `StatsLogManager` event IDs which aren't relevant outside AOSP.
