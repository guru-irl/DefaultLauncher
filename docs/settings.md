# Settings System

How the launcher's Settings page is structured, navigated, and how preference changes propagate back to the launcher.

## Entry Point

Users reach Settings by **long-pressing the workspace** background, which opens an options popup. The "Home settings" item is wired up in [`OptionsPopupView.java`](../src/com/android/launcher3/views/OptionsPopupView.java):

```java
// OptionsPopupView.java, getOptions()
options.add(new OptionItem(launcher,
        R.string.settings_button_text,
        R.drawable.ic_setting,
        LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS,
        OptionsPopupView::startSettings));
```

The `startSettings()` method fires an `ACTION_APPLICATION_PREFERENCES` intent scoped to the launcher's own package. This is the standard Android mechanism for opening app preferences.

## SettingsActivity

**File:** [`src/com/android/launcher3/settings/SettingsActivity.java`](../src/com/android/launcher3/settings/SettingsActivity.java)

The activity extends `FragmentActivity` (not the deprecated `PreferenceActivity`) and implements:
- `OnPreferenceStartFragmentCallback` -- handles clicks on preferences that specify a `fragment` attribute
- `OnPreferenceStartScreenCallback` -- handles drilling into nested `PreferenceScreen` nodes

### Manifest Declaration

Declared in [`AndroidManifest-common.xml`](../AndroidManifest-common.xml) (the shared manifest merged into all build variants):

```xml
<activity
    android:name="com.android.launcher3.settings.SettingsActivity"
    android:label="@string/settings_button_text"
    android:theme="@style/HomeSettings.Theme"
    android:exported="true"
    android:autoRemoveFromRecents="true">
    <intent-filter>
        <action android:name="android.intent.action.APPLICATION_PREFERENCES" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

- `exported="true"` so system Settings search can launch it directly
- `autoRemoveFromRecents="true"` keeps the recents list clean

### Layout

| API Level | Layout File | Design |
|-----------|------------|--------|
| Default | [`res/layout/settings_activity.xml`](../res/layout/settings_activity.xml) | `LinearLayout` with a `Toolbar` + `FrameLayout` content area |
| 31+ | [`res/layout-v31/settings_activity.xml`](../res/layout-v31/settings_activity.xml) | `CoordinatorLayout` with collapsing `AppBarLayout` / `CollapsingToolbarLayout` for Material-style header |

### Fragment Instantiation

The activity does **not** hardcode its fragment class. Instead it reads from a string resource:

```java
// SettingsActivity.java, onCreate()
final Fragment f = fm.getFragmentFactory().instantiate(
    getClassLoader(),
    getString(R.string.settings_fragment_name));
```

The resource `settings_fragment_name` is defined in [`res/values/config.xml`](../res/values/config.xml):

```xml
<string name="settings_fragment_name" translatable="false">
    com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment
</string>
```

This is a deliberate extension point -- derivative launchers (Go edition, OEM forks) can override this resource to swap in a different fragment without changing Java code.

## LauncherSettingsFragment

An inner static class inside `SettingsActivity.java`. Extends `PreferenceFragmentCompat` and implements `SettingsCache.OnChangeListener`.

### Preference XML

Preferences are defined in [`res/xml/launcher_preferences.xml`](../res/xml/launcher_preferences.xml):

| Key | Type | Description | Persistent |
|-----|------|-------------|------------|
| `pref_icon_badging` | `NotificationDotsPreference` (custom) | Notification dots on/off status | No (reads system setting) |
| `pref_add_icon_to_home` | `SwitchPreference` | Auto-add new app icons to home screen | Yes |
| `pref_allowRotation` | `SwitchPreference` | Allow home screen rotation | Yes |

Two additional preferences are handled in code but not in the base XML (added via overlays or build variants):
- `pref_developer_options` -- visible only on debug builds with system developer options enabled
- `pref_fixed_landscape_mode` -- visible only when `Flags.oneGridSpecs()` is true and device is not a tablet

### Conditional Preference Removal

The `initPreference()` method iterates every preference from the XML and returns `false` to remove ones that don't apply:

| Key | Removed When |
|-----|-------------|
| `pref_icon_badging` | `NOTIFICATION_DOTS_ENABLED` build config is `false` |
| `pref_allowRotation` | Device is a tablet, OR `Flags.oneGridSpecs()` is true |
| `pref_developer_options` | Not a debug device, OR system developer options disabled |
| `pref_fixed_landscape_mode` | `Flags.oneGridSpecs()` is false, OR device is tablet/multi-display |

### SharedPreferences File

The fragment explicitly sets its preferences file:

```java
getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
```

Where `SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs"` (from [`LauncherFiles.java`](../src/com/android/launcher3/LauncherFiles.java)).

## Navigation

### Sub-preference Navigation

When a preference has a `fragment` attribute, the activity's `onPreferenceStartFragment()` handles it. The `startPreference()` method has a key design decision:

```java
private boolean startPreference(String fragment, Bundle args, String key) {
    if (f instanceof DialogFragment) {
        ((DialogFragment) f).show(fm, key);        // dialog overlay
    } else {
        startActivity(new Intent(this, SettingsActivity.class)  // new activity instance
                .putExtra(EXTRA_FRAGMENT_ARGS, args));
    }
}
```

- **DialogFragments** are shown as overlays on the current activity
- **Everything else** launches a new `SettingsActivity` instance. This works with Activity Embedding (configured in [`res/xml/split_configuration.xml`](../res/xml/split_configuration.xml)) to show a side-by-side split on large screens.

### Deep-linking

The activity supports jumping directly to a specific preference via intent extras:
- `EXTRA_FRAGMENT_ROOT_KEY` -- which `PreferenceScreen` to show as root
- `EXTRA_FRAGMENT_HIGHLIGHT_KEY` -- which preference to scroll to and highlight

The visual highlight is handled by [`PreferenceHighlighter.java`](../src/com/android/launcher3/settings/PreferenceHighlighter.java), which draws a pulsing accent-colored rectangle over the target preference using `RecyclerView.ItemDecoration`.

## Individual Preferences

### Notification Dots (`pref_icon_badging`)

**File:** [`NotificationDotsPreference.java`](../src/com/android/launcher3/settings/NotificationDotsPreference.java)

This custom `Preference` does **not** store its own value. It reflects the system setting `Settings.Secure.notification_badging` via [`SettingsCache`](../src/com/android/launcher3/util/SettingsCache.java).

Behavior:
- Displays "On" or "Off" based on the system setting
- If notification listener access is missing, shows a warning icon and opens `NotificationAccessConfirmation` (an inner `DialogFragment`) that directs the user to system notification listener settings
- When clicked normally, opens Android's `Settings.ACTION_NOTIFICATION_SETTINGS`

### Add Icons to Home Screen (`pref_add_icon_to_home`)

A standard `SwitchPreference` stored directly in SharedPreferences. Read on-demand by [`SessionCommitReceiver.java`](../src/com/android/launcher3/SessionCommitReceiver.java) when a new app is installed:

```java
public static boolean isEnabled(Context context, UserHandle user) {
    return LauncherPrefs.getPrefs(context).getBoolean(ADD_ICON_PREFERENCE_KEY, true);
}
```

No active listener -- value is checked each time a package installation completes.

### Allow Rotation (`pref_allowRotation`)

A standard `SwitchPreference`. Consumed by [`RotationHelper.java`](../src/com/android/launcher3/states/RotationHelper.java), which registers as a `LauncherPrefChangeListener`:

```java
LauncherPrefs.get(mActivity).addListener(this, ALLOW_ROTATION);
```

On change, it recomputes the appropriate `SCREEN_ORIENTATION_*` flag and applies it via `setRequestedOrientation()`.

### Fixed Landscape Mode (`pref_fixed_landscape_mode`)

When toggled, triggers a grid reconfiguration in [`InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java):
- ON: saves the current grid name, calls `onConfigChanged()` to reload the device profile
- OFF: restores the previously saved grid name

## Change Propagation

Settings changes reach the launcher through four mechanisms:

### 1. Direct SharedPreferences Read (On-Demand)

No listener. Value is read when needed.
- Example: `pref_add_icon_to_home` checked by `SessionCommitReceiver`

### 2. LauncherPrefs Listeners

[`LauncherPrefs.kt`](../src/com/android/launcher3/LauncherPrefs.kt) provides typed preference access with listener management:

```kotlin
fun addListener(listener: LauncherPrefChangeListener, vararg items: Item)
```

The [`LauncherPrefChangeListener`](../src/com/android/launcher3/LauncherPrefChangeListener.java) interface wraps Android's `OnSharedPreferenceChangeListener`.

Consumers:
- `RotationHelper` listens for `ALLOW_ROTATION`
- `InvariantDeviceProfile` listens for `FIXED_LANDSCAPE_MODE`

### 3. SettingsCache (System Settings Observer)

[`SettingsCache.java`](../src/com/android/launcher3/util/SettingsCache.java) is a `ContentObserver` singleton that caches boolean values from `Settings.Secure`, `Settings.System`, and `Settings.Global`, and dispatches `OnChangeListener` callbacks when they change.

Monitored URIs include:
- `NOTIFICATION_BADGING_URI` (`Settings.Secure.notification_badging`)
- `ROTATION_SETTING_URI` (system auto-rotate)
- Developer options state (`Settings.Global.development_settings_enabled`)

### 4. Activity Recreation

When developer options state changes while the settings activity is open, `tryRecreateActivity()` calls `activity.recreate()` to reflect the new state (show/hide the developer options entry).

## Key Files Reference

| File | Role |
|------|------|
| [`SettingsActivity.java`](../src/com/android/launcher3/settings/SettingsActivity.java) | Activity + LauncherSettingsFragment |
| [`NotificationDotsPreference.java`](../src/com/android/launcher3/settings/NotificationDotsPreference.java) | Custom preference for notification dots |
| [`PreferenceHighlighter.java`](../src/com/android/launcher3/settings/PreferenceHighlighter.java) | Visual highlight animation for deep-linked preferences |
| [`launcher_preferences.xml`](../res/xml/launcher_preferences.xml) | Preference hierarchy definition |
| [`settings_activity.xml`](../res/layout/settings_activity.xml) | Base layout |
| [`settings_activity.xml` (v31)](../res/layout-v31/settings_activity.xml) | API 31+ layout with collapsing toolbar |
| [`AndroidManifest-common.xml`](../AndroidManifest-common.xml) | Activity declaration |
| [`split_configuration.xml`](../res/xml/split_configuration.xml) | Activity Embedding rules for large screens |
| [`config.xml`](../res/values/config.xml) | `settings_fragment_name` extension point |
| [`LauncherPrefs.kt`](../src/com/android/launcher3/LauncherPrefs.kt) | Typed SharedPreferences with listener support |
| [`LauncherPrefChangeListener.java`](../src/com/android/launcher3/LauncherPrefChangeListener.java) | Preference change callback interface |
| [`SettingsCache.java`](../src/com/android/launcher3/util/SettingsCache.java) | ContentObserver cache for system settings |
| [`RotationHelper.java`](../src/com/android/launcher3/states/RotationHelper.java) | Consumes allow-rotation preference |
| [`SessionCommitReceiver.java`](../src/com/android/launcher3/SessionCommitReceiver.java) | Consumes add-icon-to-home preference |
| [`InvariantDeviceProfile.java`](../src/com/android/launcher3/InvariantDeviceProfile.java) | Consumes fixed-landscape-mode preference |
| [`OptionsPopupView.java`](../src/com/android/launcher3/views/OptionsPopupView.java) | Long-press menu that launches settings |
| [`LauncherFiles.java`](../src/com/android/launcher3/LauncherFiles.java) | SharedPreferences file name constants |
