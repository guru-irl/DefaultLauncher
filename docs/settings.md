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

The activity extends `AppCompatActivity` (migrated from AOSP's `FragmentActivity`) and implements:
- `OnPreferenceStartFragmentCallback` -- handles clicks on preferences that specify a `fragment` attribute
- `OnPreferenceStartScreenCallback` -- handles drilling into nested `PreferenceScreen` nodes

### Theme & Dynamic Colors

The activity theme is `HomeSettings.Theme`, which extends `Theme.Material3.DayNight.NoActionBar`. `DynamicColors.applyToActivityIfAvailable(this)` is called before `super.onCreate()` to pick up the device's wallpaper-extracted color palette.

### Manifest Declaration

Declared in [`AndroidManifest-common.xml`](../AndroidManifest-common.xml) (the shared manifest merged into all build variants):

```xml
<activity
    android:name="com.android.launcher3.settings.SettingsActivity"
    android:label="@string/settings_title"
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
| Default | [`res/layout/settings_activity.xml`](../res/layout/settings_activity.xml) | `LinearLayout` with `MaterialToolbar` + `FrameLayout` content area |
| 31+ | [`res/layout-v31/settings_activity.xml`](../res/layout-v31/settings_activity.xml) | `CoordinatorLayout` with collapsing `AppBarLayout` / `CollapsingToolbarLayout` for M3 header |

Both layouts use `com.google.android.material.appbar.MaterialToolbar` (not the legacy `Toolbar`), paired with `setSupportActionBar()`.

### Collapsing Toolbar (API 31+)

The `CollapsingToolbarLayout` displays "Default" in Danfo font:

```java
CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
collapsingToolbar.setTitle(getString(R.string.settings_title));
Typeface danfo = getResources().getFont(R.font.danfo);
collapsingToolbar.setCollapsedTitleTypeface(danfo);
collapsingToolbar.setExpandedTitleTypeface(danfo);
```

Key: the font is set via `setCollapsedTitleTypeface()` / `setExpandedTitleTypeface()`, not via text appearance styles or `SpannableString`. `CollapsingTextHelper` (the internal renderer) ignores `TypefaceSpan` and `android:fontFamily` from text appearances -- only the dedicated typeface setters work.

The background transition uses the CTL's native `contentScrim`:
```xml
app:contentScrim="@color/materialColorSurfaceContainer"
```
No custom `OnOffsetChangedListener` needed -- the CTL handles the fade between transparent (expanded) and surface container (collapsed). `app:liftOnScroll="false"` is set on the `AppBarLayout` to prevent M3's default elevation tint from conflicting.

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

| Key | Type | Description |
|-----|------|-------------|
| `pref_icon_badging` | `NotificationDotsPreference` (custom) | Notification dots on/off status |
| `pref_icon_pack` | `Preference` | Icon pack picker (opens bottom sheet) |
| `pref_icon_shape` | `Preference` | Icon shape picker (opens bottom sheet) |
| `pref_icon_size_scale` | `Preference` (custom layout) | Icon size with inline toggle group |
| `pref_grid_columns` | `M3SliderPreference` (custom) | Grid column count (4-10) |
| `pref_allapps_row_gap` | `M3SliderPreference` (custom) | App drawer row gap in dp |

Preferences are organized into categories:
- **Appearance** -- notification dots, icon pack, icon shape, icon size
- **Grid** -- column count, app drawer row gap
- **Theme colors** -- color debug swatch grid

### Settings UI Pattern (Card Groups)

The settings list uses a Lawnchair-style card group layout via [`CardGroupItemDecoration`](../src/com/android/launcher3/settings/CardGroupItemDecoration.java), a `RecyclerView.ItemDecoration` that:
- Draws individual rounded-rect backgrounds per preference item
- Uses position-aware corner radii (large top corners for first item, large bottom for last)
- Leaves category headers outside the cards with no background
- Separates items with a 4dp gap (no divider lines)

### Custom Preferences

| Class | Purpose |
|-------|---------|
| [`M3SliderPreference`](../src/com/android/launcher3/settings/M3SliderPreference.java) | Replaces `SeekBarPreference` with a Material 3 `Slider`. Hides the default widget area and injects its own `Slider` + value label into `onBindViewHolder`. |
| [`ColorDebugPreference`](../src/com/android/launcher3/settings/ColorDebugPreference.java) | Displays a grid of dynamic color swatches using `materialColor*` resources. Useful for verifying M3 dynamic color integration. |
| `NotificationDotsPreference` | Reflects system `Settings.Secure.notification_badging` state (not persisted locally). |

### Icon Size Inline Binding

The icon size preference uses a custom layout (`res/layout/preference_icon_size.xml`) with a `MaterialButtonToggleGroup` embedded directly in the preference row. The toggle group is bound via `RecyclerView.OnChildAttachStateChangeListener` in `onViewCreated()`:

```java
rv.addOnChildAttachStateChangeListener(new OnChildAttachStateChangeListener() {
    @Override
    public void onChildViewAttachedToWindow(View child) {
        if (child.findViewById(R.id.size_toggle_group) != null) {
            bindIconSizeInline(child);
        }
    }
});
```

Presets: **S** (80%), **M** (86%), **L** (92%), **XL** (100%). A star button opens a `MaterialAlertDialogBuilder` dialog for custom percentage input. Button selection animates corner radii from inner-group shape to pill shape.

### Conditional Preference Removal

The `initPreference()` method iterates every preference from the XML and returns `false` to remove ones that don't apply:

| Key | Removed When |
|-----|-------------|
| `pref_icon_badging` | `NOTIFICATION_DOTS_ENABLED` build config is `false` |
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

## M3 Color Resources

Dynamic M3 colors are defined in [`res/values-v31/colors.xml`](../res/values-v31/colors.xml) (light) and [`res/values-night-v31/colors.xml`](../res/values-night-v31/colors.xml) (dark), mapped to Android's `system_accent1/2/3` and `system_neutral1/2` palette slots. Key resources used throughout settings and launcher themes:

| Resource | Light Source | Usage |
|----------|-------------|-------|
| `materialColorSurfaceContainer` | `system_neutral1_100` | Toolbar collapsed bg, search bar bg, popup bg |
| `materialColorOnSurface` | `system_neutral1_900` | Primary text color |
| `materialColorOnSurfaceVariant` | `system_neutral2_700` | Search hint text, search icon tint |
| `materialColorPrimary` | `system_accent1_600` | Category title color, accent |
| `materialColorOutlineVariant` | `system_neutral2_200` | Popup tertiary color |

## Typography

Settings UI uses M3 Expressive type tokens:

| Element | Style | Specs |
|---------|-------|-------|
| Preference title | `TextAppearance.Material3.BodyLarge` | 16sp / 400 weight |
| Preference summary | (default) `TextAppearance.Material3.BodyMedium` | 14sp / 400 weight |
| Category header | `TextAppearance.Material3.TitleSmall` | colored with `?attr/colorPrimary` |
| Toolbar collapsed | `TextAppearance.Material3.TitleLarge` | 20sp, Danfo |
| Toolbar expanded | `TextAppearance.Material3.DisplaySmall` | Danfo |

## Key Files Reference

| File | Role |
|------|------|
| [`SettingsActivity.java`](../src/com/android/launcher3/settings/SettingsActivity.java) | Activity + LauncherSettingsFragment |
| [`CardGroupItemDecoration.java`](../src/com/android/launcher3/settings/CardGroupItemDecoration.java) | Lawnchair-style rounded card groups |
| [`M3SliderPreference.java`](../src/com/android/launcher3/settings/M3SliderPreference.java) | Material 3 slider replacing SeekBarPreference |
| [`ColorDebugPreference.java`](../src/com/android/launcher3/settings/ColorDebugPreference.java) | Dynamic color swatch grid |
| [`NotificationDotsPreference.java`](../src/com/android/launcher3/settings/NotificationDotsPreference.java) | Custom preference for notification dots |
| [`PreferenceHighlighter.java`](../src/com/android/launcher3/settings/PreferenceHighlighter.java) | Visual highlight animation for deep-linked preferences |
| [`launcher_preferences.xml`](../res/xml/launcher_preferences.xml) | Preference hierarchy definition |
| [`preference_icon_size.xml`](../res/layout/preference_icon_size.xml) | Inline icon size toggle layout |
| [`settings_activity.xml`](../res/layout/settings_activity.xml) | Base layout |
| [`settings_activity.xml` (v31)](../res/layout-v31/settings_activity.xml) | API 31+ layout with collapsing toolbar |
| [`dancing_script.ttf`](../res/font/dancing_script.ttf) | Toolbar title font |
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
