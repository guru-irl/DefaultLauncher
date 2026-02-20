# DefaultLauncher

Custom Android launcher based on AOSP Launcher3 (Android 16 s2-release).

## Build

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/java" -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain assembleDebug
```

- **gradlew/gradlew.bat are gitignored** -- always use the wrapper jar directly as above
- Build target: `assembleDebug`
- AGP 8.13.1, Gradle 9.2.1, Java 21 (Android Studio JBR)
- `JAVA_HOME` path on this machine: `/c/Program Files/Android/Android Studio/jbr`

## Package Structure

- Internal package: `com.android.launcher3` (kept from AOSP to avoid renaming 1000+ files)
- ApplicationId: `com.guru.defaultlauncher`
- Single build variant (no flavors -- Quickstep/Go/plugin code removed)

## What Was Removed (Dead Code Cleanup)

The following AOSP code was deleted and should NOT be re-created:

- **Directories deleted**: `quickstep/`, `go/`, `tests/`, `src_flags/`, `src_shortcuts_overrides/`, `src_ui_overrides/`, `src_no_quickstep/` (merged into `src/`), `src_plugins/` (2 interfaces kept in `src/`), `checks/`, `aconfig/`, `compose/`, `secondarydisplay/`, all `Android.bp` files
- **Plugin system removed**: No `PluginManagerWrapper`, no `PluginListener` interfaces on any class, no overlay/Google Discover feed code. `DynamicResource` and `CustomWidgetManager` are simplified (no plugin loading). `ResourceProvider` and `CustomWidgetPlugin` interfaces still exist in `src/com/android/systemui/plugins/` for type compatibility.
- **Test infrastructure removed**: No `TestLogging`, `TestEventEmitter`, `TestInformationHandler`, `TestInformationProvider`. `TestProtocol` still exists in `shared/src` (used by state ordinals and accessibility).
- **Build flavors removed**: No `flavorDimensions`, no `productFlavors`, no `variantFilter`. Single default variant only.
- **Manifest consolidated**: Only `AndroidManifest-common.xml` exists (no flavor-specific `AndroidManifest.xml`).

## Key Branches

- `main` -- release branch
- `dev` -- active development
- `launcher3-base` -- clean AOSP Launcher3 integration before customizations

## Architecture

Three-layer grid system:
1. `device_profiles.xml` (static XML grid definitions)
2. `InvariantDeviceProfile.java` (parsed config, survives rotation, singleton)
3. `DeviceProfile.java` (pixel-level calculations, per-orientation)

Submodules: IconLoader, Animation, Shared, WMShared, msdl, flags

## Key Files

| File | Purpose |
|------|---------|
| `InvariantDeviceProfile.java` | Grid config, reads prefs, selects grid profile |
| `DeviceProfile.java` | All cell/icon/spacing pixel calculations |
| `CellLayout.java` | Workspace grid ViewGroup |
| `LauncherPrefs.kt` | Typed SharedPreferences with listener support |
| `SettingsActivity.java` | Settings UI (PreferenceFragment-based) |
| `launcher_preferences.xml` | Settings preference hierarchy |
| `device_profiles.xml` | Grid option definitions |

## Conventions

### Adding Settings
1. Add preference XML to `res/xml/launcher_preferences.xml`
2. Add strings to `res/values/strings.xml`
3. Register typed `ConstantItem` in `LauncherPrefs.kt` companion object using `backedUpItem()`
4. Read via `mPrefs.get(LauncherPrefs.PREF_NAME)` in Java or `LauncherPrefs.get(context).get(...)` elsewhere
5. For settings that affect the grid: call `InvariantDeviceProfile.onConfigChanged()` on change

See `docs/guides/adding-settings.md` for a full guide.

### Grid/Layout Changes
- `DeviceProfile.updateIconSize()` is where cell sizing happens -- three branches: responsive, scalable, default (phones)
- `getCellLayoutWidth()/getCellLayoutHeight()` depend on `workspacePadding` -- be careful about call ordering
- `updateWorkspacePadding()` must run before anything that reads workspace padding
- The constructor flow is: `updateAvailableDimensions()` -> `calculateAndSetWorkspaceVerticalPadding()` -> set `cellLayoutPaddingPx` -> `updateWorkspacePadding()` -> `deriveSquareGridRows()`

### DeviceProfile Initialization Ordering Gotcha
Inside `updateAvailableDimensions()`, `updateIconSize()` is called BEFORE `updateWorkspacePadding()`. This means `getCellLayoutHeight()` returns incorrect values during `updateIconSize()` because `workspacePadding` hasn't been set yet. If you need correct height values, either:
- Use `availableWidthPx`/`availableHeightPx` directly with manual offset estimates
- Defer the calculation to after `updateWorkspacePadding()` (as done with `deriveSquareGridRows()`)

### Preference Change Propagation
Three patterns used in the codebase:
1. **On-demand read** -- value checked when needed (e.g., `SessionCommitReceiver`)
2. **LauncherPrefChangeListener** -- register via `LauncherPrefs.addListener()` (e.g., `RotationHelper`)
3. **onConfigChanged** -- triggers full grid rebuild via `InvariantDeviceProfile.onConfigChanged()` (e.g., grid settings)

For settings that trigger grid reconfiguration from `SettingsActivity`, use `getListView().post()` to ensure the preference value is persisted before `onConfigChanged()` reads it.

### Debug Logging
- Guard pattern: `private static final boolean DEBUG_* = BuildConfig.DEBUG;` + `if (DEBUG_*) Log.d(TAG, ...);`
- TAG constant: `private static final String TAG = "ClassName";`
- Never use hardcoded string tags in Log calls
- Never remove diagnostic logging -- wrap it in guards instead
- Default to `BuildConfig.DEBUG` so logs auto-enable in debug builds

## Known Build Issues

- `framework-16.jar` is applied to all subprojects via `subprojects {}` block in root `build.gradle`
- `Flags.java` needed 59+ manually added methods to match `FeatureFlags` interface
- WMShared uses hidden framework APIs resolved via `HiddenApiCompat` reflection layer
- See `docs/` directory for detailed architecture docs

## Documentation

- `docs/settings.md` -- Settings system architecture
- `docs/grid-system.md` -- Grid calculation pipeline (detailed)
- `docs/grid-reflow.md` -- Grid reflow system (item preservation on column decrease)
- `docs/guides/adding-settings.md` -- Practical guide for adding new preferences
- `docs/changes/` -- Implementation tracking for features (numbered: `001-*.md`, `002-*.md`, etc.)

## Process

- Features are planned in detail before implementation, with consideration for AOSP's initialization ordering
- Implementation changes are documented in `docs/changes/` with numbered files
- Always verify builds compile after changes: run the full `assembleDebug` target
- The project uses a plan-then-implement workflow: understand existing code deeply before modifying
