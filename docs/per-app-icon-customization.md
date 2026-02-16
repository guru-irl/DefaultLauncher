# Per-App Icon Customization

How individual app icons can be overridden with a specific icon from any installed icon pack, independently for the home screen and app drawer.

---

## Architecture Overview

The per-app icon system adds a customization layer that sits between the global icon pack setting and the final icon rendered on screen. It lets users pick a *specific drawable* from *any installed pack* for *any individual app*, with separate overrides for home screen and app drawer.

```
Long-press app → "Customize" shortcut
        |
        v
AppCustomizeFragment (preferences screen)
        |
        v
PerAppIconSheet (two-page bottom sheet)
  Page 1: Pick an icon pack
  Page 2: Pick a specific icon from that pack
        |
        v
PerAppIconOverrideManager (JSON storage)
        |
        v
LauncherIconProvider / DrawerIconResolver (icon resolution)
        |
        v
Icon rendered on home screen / app drawer
```

The system is designed so that per-app overrides always take priority over global pack settings, and even over the "Match home screen icons" drawer switch. This means a user can set a global pack for all apps and then fine-tune specific apps individually.

---

## Data Model: `PerAppIconOverrideManager`

**File:** [`src/.../icons/pack/PerAppIconOverrideManager.java`](../src/com/android/launcher3/icons/pack/PerAppIconOverrideManager.java)

### Storage

Overrides are stored in a dedicated SharedPreferences file (`"per_app_icon_overrides"`), separate from the main launcher prefs. Two JSON strings:

- `"home_overrides"` — overrides for the home screen (workspace + hotseat)
- `"drawer_overrides"` — overrides for the app drawer

Each is a JSON object mapping flattened `ComponentName` strings to override objects:

```json
{
  "com.android.chrome/com.google.android.apps.chrome.Main": {
    "pack": "org.niceiconpack.flavors",
    "drawable": "chrome_alt_2"
  },
  "com.whatsapp/.Main": {
    "pack": "",
    "drawable": ""
  }
}
```

### `IconOverride` Data Class

```java
public static class IconOverride {
    public final String packPackage;   // icon pack package, "" = system default
    public final String drawableName;  // specific drawable, "" = auto-resolve

    public boolean isSystemDefault()     // packPackage is empty
    public boolean hasSpecificDrawable() // drawableName is non-empty
}
```

**Override semantics:**

| `packPackage` | `drawableName` | Meaning |
|---------------|----------------|---------|
| absent (null) | — | No override: use global pack, auto-resolve |
| `""` | `""` | Use system default icon (ignore global pack) |
| `"com.pack"` | `""` | Use this pack, auto-resolve by component name |
| `"com.pack"` | `"ic_name"` | Use this exact drawable from this pack |

### Singleton Pattern

Volatile double-checked locking (same pattern as `DrawerIconResolver`):

```java
private static volatile PerAppIconOverrideManager sInstance;

public static PerAppIconOverrideManager getInstance(Context context) {
    if (sInstance == null) {
        synchronized (PerAppIconOverrideManager.class) {
            if (sInstance == null) {
                sInstance = new PerAppIconOverrideManager(context.getApplicationContext());
            }
        }
    }
    return sInstance;
}
```

### In-Memory Cache

Two `HashMap<String, IconOverride>` maps are loaded lazily from JSON on first access. All reads go through the in-memory maps. Writes update both the map and SharedPreferences atomically.

### Cache Invalidation Hash

`getOverridesHash()` returns a deterministic hash of both maps' contents. This is appended to `LauncherIconProvider.updateSystemState()` so the icon cache knows to rebuild when overrides change.

---

## Icon Resolution Pipeline

Per-app overrides are checked **before** the global icon pack in both resolution paths.

### Home Screen: `LauncherIconProvider.getIcon()`

**File:** [`src/.../icons/LauncherIconProvider.java`](../src/com/android/launcher3/icons/LauncherIconProvider.java)

The `getIcon(ComponentInfo, int)` method (and its `ApplicationInfo` overload) follows this resolution order:

```
1. Per-app override check
   └─ overrideMgr.getHomeOverride(componentName)
      ├─ isSystemDefault() → super.getIcon() (skip all packs)
      ├─ hasSpecificDrawable() → pack.getDrawableForEntry(name)
      └─ pack only → resolveFromPack(overridePack, cn)

2. Global icon pack (existing logic)
   └─ packMgr.getCurrentPack()
      ├─ Calendar icon check
      ├─ Component-mapped icon
      ├─ Fallback mask
      └─ System icon
```

The extracted helper `resolveFromPack(IconPack, ComponentName, PackageManager, int, Supplier<Drawable>)` encapsulates the calendar → component → fallback → system chain and is shared between the override path and the global pack path.

**System state hash:**

```java
public String updateSystemState(String systemState) {
    return systemState + "," + packId + ",shape:" + shapeKey
            + ",perapp:" + overrideMgr.getOverridesHash();
}
```

This ensures icons with per-app overrides are not served from stale cache entries.

### App Drawer: `DrawerIconResolver.getDrawerIcon()`

**File:** [`src/.../icons/DrawerIconResolver.java`](../src/com/android/launcher3/icons/DrawerIconResolver.java)

```
1. Per-app drawer override check
   └─ overrideMgr.getDrawerOverride(componentName)
      ├─ isSystemDefault() → system icon via DrawerIconFactory
      ├─ hasSpecificDrawable() → pack.getDrawableForEntry(name)
      └─ pack only → pack.getIconForComponent(cn)
      → create BitmapInfo via DrawerIconFactory → cache in LRU → return

2. Match-home check
   └─ if DRAWER_MATCH_HOME is true → return null (use home icon)

3. Distinct drawer pack (existing logic)
   └─ hasDistinctDrawerPack() ? getDrawerPack() : getCurrentPack()
```

The per-app override check happens **before** the match-home check, which means per-app drawer overrides work even when the user has "Match home screen icons" enabled.

---

## Match Home Screen Icons

### Preference

```kotlin
// LauncherPrefs.kt
@JvmField
val DRAWER_MATCH_HOME = backedUpItem("pref_drawer_match_home", true)
```

Default is `true` — new installs have the drawer matching the home screen out of the box.

### AppDrawerFragment Wiring

When the switch is toggled:

1. Icon pack, shape, and size preferences become visible/hidden
2. Drawer icon cache is invalidated
3. Model is force-reloaded

**Important:** Toggling the switch does NOT clear the drawer pack preference. This preserves the user's previous drawer settings so turning the switch off restores them immediately.

### Interaction with Per-App Overrides

Per-app overrides take absolute priority. The visibility of the "App drawer icon" row in `AppCustomizeFragment` is always enabled regardless of the match-home state. The resolution pipeline checks per-app overrides before checking `DRAWER_MATCH_HOME`.

---

## System Shortcuts

**File:** [`src/.../popup/SystemShortcut.java`](../src/com/android/launcher3/popup/SystemShortcut.java)

### `CUSTOMIZE_ICON`

Factory that creates a "Customize" shortcut in the long-press popup menu for any app.

**Conditions:**
- `originalView` is non-null
- `itemInfo.getTargetComponent()` is non-null
- Item is an application (`ITEM_TYPE_APPLICATION` or `AppInfo`)

**Action:** Launches `SettingsActivity` with `AppCustomizeFragment`, passing:
- `EXTRA_COMPONENT_NAME` — flattened component name
- `EXTRA_APP_LABEL` — app display name (used as toolbar title via `EXTRA_FRAGMENT_TITLE`)

**Icon:** `ic_customize.xml` — Material Symbols brush vector (24dp)

### `UNINSTALL_APP_GENERAL`

Factory that creates an "Uninstall" shortcut in the app drawer long-press popup.

**Conditions:**
- Only shown for all-apps container items (`hasAllAppsContainer()`)
- Not shown for private space apps
- Not shown if `SecondaryDropTarget.getUninstallTarget()` returns null (system apps)

**Action:** Calls `SecondaryDropTarget.performUninstall()` to launch the Android uninstall confirmation dialog.

### Registration in `Launcher.java`

```java
return Stream.of(APP_INFO, WIDGETS, CUSTOMIZE_ICON, UNINSTALL_APP_GENERAL, INSTALL);
```

---

## AppCustomizeFragment

**File:** [`src/.../settings/AppCustomizeFragment.java`](../src/com/android/launcher3/settings/AppCustomizeFragment.java)

A `PreferenceFragmentCompat` launched via `SettingsActivity`'s subpage pattern. The toolbar title shows the app name (passed via `EXTRA_FRAGMENT_TITLE` from the system shortcut).

### Preferences (Programmatic)

Built in `onCreatePreferences()` rather than from XML:

| Key | Type | Description |
|-----|------|-------------|
| `customize_home_icon` | Preference + icon widget | Home screen icon preview + override summary |
| `customize_drawer_icon` | Preference + icon widget | Drawer icon preview + override summary |
| `customize_reset` | Preference | Clears both overrides |
| `customize_footer` | Preference | Component ID (10sp, centered, 50% alpha, `"no_card"` tag) |

### Icon Preview Loading

Each icon row uses `R.layout.preference_icon_widget` — a `FrameLayout` containing a `ProgressBar` (loading spinner) and an `ImageView` (icon preview).

Previews are loaded via `OnChildAttachStateChangeListener` on the RecyclerView:

1. Identify which preference the row belongs to via `ViewHolder.getBindingAdapterPosition()` → `getPreferenceScreen().getPreference(pos)`
2. Show spinner, hide stale preview
3. On `MODEL_EXECUTOR`: resolve the correct icon via `resolvePreviewIcon()`
4. Post to main thread: set drawable, hide spinner

### Effective Pack Resolution

The `getEffectivePack()` method mirrors the actual `DrawerIconResolver` logic for consistency between the preview and the real icon:

```java
@Nullable
private IconPack getEffectivePack(IconPackManager packMgr, boolean isHome, Context ctx) {
    if (isHome) return packMgr.getCurrentPack();

    boolean matchHome = LauncherPrefs.get(ctx).get(LauncherPrefs.DRAWER_MATCH_HOME);
    if (matchHome) return packMgr.getCurrentPack();

    if (packMgr.hasDistinctDrawerPack()) return packMgr.getDrawerPack();

    return packMgr.getCurrentPack();
}
```

This is critical for accurate summaries — the drawer row must show the same pack that the drawer would actually use, respecting the match-home switch and distinct drawer pack settings.

### Summary Text

| State | Summary |
|-------|---------|
| No override | `"Global (Pack Name)"` |
| System default | `"System default"` |
| Specific pack + drawable | `"Pack Name — drawable_name"` |
| Specific pack, auto-resolve | Pack name |

---

## Two-Page Bottom Sheet: `PerAppIconSheet`

**File:** [`src/.../settings/PerAppIconSheet.java`](../src/com/android/launcher3/settings/PerAppIconSheet.java)

A single `BottomSheetDialog` that transitions between two pages with a card-expansion animation.

### Page 1: Pack Selection

Layout (built programmatically):

```
FrameLayout (root)
└── LinearLayout (packPage)
    ├── Drag handle (4dp × 32dp pill)
    ├── Title "Choose icon pack" (22sp)
    └── NestedScrollView
        └── LinearLayout
            ├── Card: "Follow global setting"
            ├── Card: "System default"
            ├── Card: Pack A  [app preview →]
            ├── Card: Pack B  [app preview →]
            └── ...
```

Each installed pack card shows the pack icon + name on the left and an async-loaded preview of **this app's icon from that pack** on the right (using `getIconForComponent()` with `applyFallbackMask()` as fallback).

### Card Expansion Transition

When a pack card is tapped:

1. **Measure card position** — `getLocationInWindow()` relative to root `FrameLayout`
2. **Create overlay** — a `View` with `GradientDrawable` background matching the card color (`materialColorSurfaceContainerHigh`), positioned exactly over the card
3. **Build icon picker page** — added to root as `INVISIBLE`
4. **Animate** (350ms, `FastOutSlowInInterpolator`):
   - Overlay expands from card bounds to fill the entire root (margins → 0, size → root size)
   - Corner radius morphs from 16dp (card) → 28dp (M3 bottom sheet radius)
   - Proper shape clipping via `ViewOutlineProvider` + `setClipToOutline(true)` + `invalidateOutline()` each frame
   - Pack page alpha fades to 0 simultaneously
5. **On animation end:**
   - Pack page set to `GONE`
   - Icon picker page becomes `VISIBLE` and fades in (200ms)
   - Overlay fades out (200ms) and is removed

```java
// Outline provider for smooth corner clipping during animation
final float[] currentRadius = {cornerPx};
overlay.setOutlineProvider(new ViewOutlineProvider() {
    @Override
    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                currentRadius[0]);
    }
});
overlay.setClipToOutline(true);
```

### Page 2: Icon Picker Grid

Layout:

```
LinearLayout (iconPage)
├── Drag handle
├── Header: [← back] "Choose an icon" (22sp)
├── Search EditText (56dp, bg_widgets_searchbox)
└── RecyclerView (GridLayoutManager)
    ├── [Header] "Suggested"
    ├── [Icon] matched_icon
    ├── [Icon] variant_1
    ├── [Icon] variant_2
    ├── [Header] "Category 1"
    ├── [Icon] [Icon] [Icon] [Icon] ...
    └── ...
```

**Grid layout:** `GridLayoutManager` with span count = `floor(width / 64dp)`, minimum 4. Headers use full span via `SpanSizeLookup`.

**Two view types:**
- `VIEW_TYPE_HEADER` (0) — `item_icon_picker_header.xml` (14sp bold, primary color)
- `VIEW_TYPE_ICON` (1) — `item_icon_picker.xml` (64dp FrameLayout with ImageView)

### Suggested Section

The "Suggested" section appears at the top of the icon grid and contains:

1. **Primary match** — the drawable mapped to this component in `appfilter.xml` via `IconPack.getDrawableNameForComponent(ComponentName, PackageManager)`. This is the icon the pack author intended for this app.

2. **Variants** — other drawables in the pack whose name contains the app's "simple name". The simple name is extracted from the package name by taking the last non-generic segment:

```java
// "com.google.android.apps.chrome" → "chrome"
// "com.whatsapp" → "whatsapp"
// "org.mozilla.firefox" → "firefox"
private static String getSimpleName(ComponentName cn) {
    String[] parts = cn.getPackageName().split("\\.");
    String last = parts[parts.length - 1];
    // Skip generic: "app", "apps", "android", "com"
    if (isGeneric(last) && parts.length > 1) return parts[parts.length - 2];
    return last;
}
```

Only names with 3+ characters are searched to avoid false positives. Suggested items are de-duplicated from the remaining categories below.

### Search

A `TextWatcher` with 300ms debounce (via `Handler.postDelayed`) filters all items by matching the query against both `entry.label` (humanized name) and `entry.drawableName` (raw identifier), case-insensitive. Category headers are included only if they have matching children.

### Back Navigation

- **Back button** (← arrow) in the header — cross-fades back to pack page (200ms each direction)
- **Device back key** — intercepted via `Dialog.setOnKeyListener()`, checks a `boolean[] onIconPage` flag and delegates to the same cross-fade transition
- Pack page is preserved in memory (set to `GONE`, not removed), so returning is instant

### Icon Selection

On tap:
1. Dialog is dismissed
2. Callback fires `onIconSelected(packPackage, drawableName)`
3. `AppCustomizeFragment` saves the override, clears caches, forces model reload
4. Summaries and previews refresh on `onResume()`

---

## IconPack: Full Icon Enumeration

**File:** [`src/.../icons/pack/IconPack.java`](../src/com/android/launcher3/icons/pack/IconPack.java)

### Data Classes

```java
public static class IconCategory {
    public final String title;
    public final List<IconEntry> items;
}

public static class IconEntry {
    public final String drawableName;  // e.g., "chrome_alt_2"
    public final String label;         // e.g., "Chrome Alt 2" (humanized)
}
```

### `getAllIcons(PackageManager pm)`

Returns all icons in the pack, organized by category:

1. **Primary path:** Parse `res/xml/drawable.xml` from the icon pack APK. This is the standard ADW icon pack format where pack authors organize icons into `<category>` elements.

2. **Fallback path:** If no `drawable.xml` exists, collect unique drawable names from `mComponentToDrawable` (the `appfilter.xml` mappings) into a single "All icons" category.

Labels are humanized from drawable names: underscores/hyphens → spaces, trimmed.

### `getDrawableNameForComponent(ComponentName, PackageManager)`

Returns the raw drawable name string (not the loaded `Drawable`) for a component. Used by the "Suggested" section to identify the matched icon without loading it.

### `getDrawableForEntry(String drawableName, PackageManager)`

Public wrapper around the internal `loadDrawableByName()` method. Loads and returns the `Drawable` for a given drawable name from the pack's resources.

---

## Cache Invalidation

When an override is saved or cleared, `AppCustomizeFragment.applyOverrideChange()` runs:

```java
Executors.MODEL_EXECUTOR.execute(() -> {
    app.getIconCache().clearAllIcons();     // drop + recreate SQLite, clear memory
    DrawerIconResolver.getInstance().invalidate();  // clear drawer LRU cache
    LauncherIcons.clearPool(ctx);           // discard pooled factory instances
    sMainHandler.post(() -> app.getModel().forceReload());  // full reload
});
```

This is the nuclear option — clearing everything ensures the new override takes effect immediately. The `getOverridesHash()` appended to `updateSystemState()` provides a secondary cache key that prevents stale cache hits during normal icon loading.

---

## UI Components

### `preference_icon_widget.xml`

```xml
<FrameLayout 40dp × 40dp>
    <ProgressBar 24dp centered, indeterminate />
    <ImageView match_parent, fitCenter, GONE />
</FrameLayout>
```

Used as `widgetLayoutResource` on the home/drawer icon preferences. The ProgressBar shows while the icon loads on the background executor; the ImageView replaces it when loading completes.

### `item_icon_picker.xml`

```xml
<FrameLayout 64dp × 64dp, padding 8dp>
    <ImageView match_parent, fitCenter, selectableItemBackgroundBorderless />
</FrameLayout>
```

Each cell in the icon picker grid. The borderless ripple provides touch feedback.

### `item_icon_picker_header.xml`

```xml
<TextView match_parent, 14sp bold, materialColorPrimary, padding 8dp/16dp />
```

Category header with primary color text, full-span in the grid.

### Footer Styling

The component ID footer in `AppCustomizeFragment` is styled programmatically:

- Tagged `"no_card"` so `CardGroupItemDecoration` skips it
- Title view hidden
- Summary: 10sp, `materialColorOnSurfaceVariant`, 50% alpha, centered, 16dp horizontal padding
- Shows `ComponentName.flattenToShortString()` (e.g., `com.android.chrome/.Main`)

---

## Files Reference

| File | Role |
|------|------|
| **Data layer** | |
| `PerAppIconOverrideManager.java` | JSON-backed singleton for per-app overrides (home + drawer) |
| `IconPack.java` | `getAllIcons()`, `IconCategory`/`IconEntry`, `getDrawableNameForComponent()` |
| `LauncherPrefs.kt` | `DRAWER_MATCH_HOME` preference |
| **Resolution layer** | |
| `LauncherIconProvider.java` | Home icon resolution with per-app override check |
| `DrawerIconResolver.java` | Drawer icon resolution with per-app override + match-home check |
| **UI layer** | |
| `AppCustomizeFragment.java` | Per-app customize preferences screen |
| `PerAppIconSheet.java` | Two-page bottom sheet (pack picker + icon grid) |
| `IconSettingsHelper.java` | `showPerAppPackDialog()` utility (also used standalone) |
| `IconPickerFragment.java` | Standalone icon picker fragment (kept for reference) |
| **Entry points** | |
| `SystemShortcut.java` | `CUSTOMIZE_ICON` + `UNINSTALL_APP_GENERAL` factories |
| `Launcher.java` | Shortcut registration |
| **Settings** | |
| `AppDrawerFragment.java` | Match-home switch wiring |
| `app_drawer_preferences.xml` | `pref_drawer_match_home` SwitchPreference |
| **Layouts** | |
| `preference_icon_widget.xml` | Icon preview widget for preference rows |
| `fragment_icon_picker.xml` | Icon picker layout (search + grid) |
| `item_icon_picker.xml` | Icon grid cell (64dp square) |
| `item_icon_picker_header.xml` | Category header |
| **Drawables** | |
| `ic_customize.xml` | Brush icon for "Customize" shortcut |
| `ic_arrow_back.xml` | Back arrow for icon picker header |
