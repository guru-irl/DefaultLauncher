# DefaultLauncher

Custom Android launcher based on AOSP Launcher3 (Android 16 s2-release).

## Build

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/java" -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain assembleAospWithoutQuickstepDebug
```

- **gradlew/gradlew.bat are gitignored** -- always use the wrapper jar directly as above
- Build target: `assembleAospWithoutQuickstepDebug`
- AGP 8.13.1, Gradle 9.2.1, Java 21 (Android Studio JBR)
- `JAVA_HOME` path on this machine: `/c/Program Files/Android/Android Studio/jbr`

## Package Structure

- Internal package: `com.android.launcher3` (kept from AOSP to avoid renaming 1000+ files)
- ApplicationId: `com.guru.defaultlauncher`
- Build flavor: `aospWithoutQuickstep` (no recents/system-level permissions)

## Key Branches

- `main` -- release branch
- `dev` -- active development
- `launcher3-base` -- clean AOSP Launcher3 integration before customizations

## Architecture

Three-layer grid system:
1. `device_profiles.xml` (static XML grid definitions)
2. `InvariantDeviceProfile.java` (parsed config, survives rotation, singleton)
3. `DeviceProfile.java` (pixel-level calculations, per-orientation)

Submodules: IconLoader, Animation, Shared, WMShared, msdl, flags, systemUIPluginCore, androidx-lib

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

## Known Build Issues

- `framework-16.jar` is applied to all subprojects via `subprojects {}` block in root `build.gradle`
- `Flags.java` needed 59+ manually added methods to match `FeatureFlags` interface
- WMShared uses hidden framework APIs resolved via `HiddenApiCompat` reflection layer
- See `docs/` directory for detailed architecture docs

## Documentation

- `docs/settings.md` -- Settings system architecture
- `docs/grid-system.md` -- Grid calculation pipeline (detailed)
- `docs/guides/adding-settings.md` -- Practical guide for adding new preferences
- `docs/changes/` -- Implementation tracking for features (numbered: `001-*.md`, `002-*.md`, etc.)

## Process

- Features are planned in detail before implementation, with consideration for AOSP's initialization ordering
- Implementation changes are documented in `docs/changes/` with numbered files
- Always verify builds compile after changes: run the full `assembleAospWithoutQuickstepDebug` target
- The project uses a plan-then-implement workflow: understand existing code deeply before modifying
