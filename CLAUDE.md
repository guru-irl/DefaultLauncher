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
- **Java path is platform-specific.** On Windows/git-bash: `/c/Program Files/Android/Android Studio/jbr`. On this Linux dev/AVD box the JBR is at `/opt/android-studio/jbr` -- use `/opt/android-studio/jbr/bin/java` (the Windows path above does not exist on Linux). The `tests-e2e/` runbook and `docs/plans/000-*` resume scripts use the Linux path.
- Output APK: `build/outputs/apk/debug/DefaultLauncher-debug.apk`

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

### Writing Style (all output: commits, docs, code comments, chat)
- **No em-dashes.** Never use the long dash (em-dash, Unicode U+2014) anywhere. Use a plain hyphen, comma, colon, parentheses, or separate sentences. (The double-hyphen `--` used throughout this file is a hyphen, not an em-dash, and is fine.)
- **No AI/agent attribution.** Do not sign work as Claude/an agent. In particular, commits carry NO `Co-Authored-By` trailer (see Process > commit identity).

## Known Build Issues

- `framework-16.jar` is applied to all subprojects via `subprojects {}` block in root `build.gradle`
- `Flags.java` needed 59+ manually added methods to match `FeatureFlags` interface
- WMShared uses hidden framework APIs resolved via `HiddenApiCompat` reflection layer
- See `docs/` directory for detailed architecture docs

## Testing (AVD + e2e suite)

A permanent end-to-end suite lives in `tests-e2e/` (uiautomator2 + pytest). It is a first-class deliverable, not scaffolding -- every change must keep it green. Full detail: `tests-e2e/README.md` (run + write) and `docs/plans/000-architectural-refactor-superplan.md` (resume runbook, known-good baselines).

### Device & environment
- An AVD is normally already running as **`emulator-5554`** -- Pixel 7 Pro (`sdk_gphone16k_x86_64`), Android 17 (SDK 37), 1440×3120 @ 560dpi, with DefaultLauncher set as the default home activity.
- `adb` is at `$HOME/Android/Sdk/platform-tools/adb` (not on PATH by default):
  ```bash
  export ANDROID_HOME=$HOME/Android/Sdk
  export PATH=$ANDROID_HOME/platform-tools:$PATH
  adb devices            # expect: emulator-5554  device
  ```
- **A physical Samsung phone (`RFCX712ZQDT`) may ALSO be attached.** It is the user's daily driver and was wiped once by a stray reset. NEVER run destructive ops (`pm clear`, `install`, factory reset, workspace edits) against it. Always target the emulator explicitly: `export ANDROID_SERIAL=emulator-5554`. `run.sh` auto-selects the emulator when multiple devices are present; the direct `pytest` path does NOT, so set `ANDROID_SERIAL` yourself there.

### Build → install → test loop
```bash
# build (see Build section; Linux JBR)
/opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
  -Dorg.gradle.appname=gradlew -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain assembleDebug
adb -s emulator-5554 install -r -d -g build/outputs/apk/debug/DefaultLauncher-debug.apk

cd tests-e2e
./run.sh                 # smoke only (<5 min); venv auto-created on first run
./run.sh --full          # smoke + regression (~15 min)
./run.sh --stress        # opt-in soak tests
./run.sh -k workspace    # any pytest selector
# direct invocation (must set ANDROID_SERIAL yourself):
export ANDROID_SERIAL=emulator-5554
.venv/bin/pytest smoke/ regression/ visuals/ -v --tb=short
```
- **Expected green baseline:** ~40 passed, 2 xfailed, 9 skipped, 0 failed. Full suite is 25-30 min (the emulator degrades over a long run); a targeted ~25-test run is ~10-15 min.
- Failures auto-dump `tests-e2e/artifacts/<test>.{png,xml,logcat.txt}` (screenshot, UI hierarchy, last 500 logcat lines).

### Writing tests
- Drive everything through the **`LauncherDriver`** facade (`tests-e2e/lib/launcher.py`). Never inline selectors -- add new ones to `tests-e2e/lib/selectors.py` first. Tests describe user intent (`launcher.open_drawer()`), not UI structure.
- **No `time.sleep`** -- use uiautomator2 wait semantics; if a timed wait is unavoidable, name the constant in `lib/selectors.py`.
- Fixtures: `launcher` (stateless tests), `clean_launcher` (tests that mutate persistent state). `conftest.py` seeds the workspace so every test starts from a populated home, even after `pm clear` -- do not bypass it.
- **Mark every test:** `@pytest.mark.smoke|regression|stress` + a feature mark (`drawer|workspace|search|settings|grid|prefs|model`). Smokes assert one thing; regressions can chain steps; stress asserts no crashes/leaks.
- Adding a feature area → add `smoke/test_<feature>_basics.py` (2-4 golden paths), a regression file if it has real state, and update the README coverage table.

### AVD caveats
- `app_current()` is **stale on Android 17** after launching an app (returns a background task). `LauncherDriver.is_home()` falls back to a workspace-visibility probe -- do not reintroduce `app_current()`-based home checks.
- The AVD **does** have a widget picker. It has no **work profile** or **private space** configured, so `work_profile` and `private_space` tests **skip on the AVD**. `deletion_safety` scenarios (widget-stack provider blackouts) are validated on the physical phone -- carefully, per the destructive-ops rule above. (The superplan's older note that "the emulator has no widget picker" is stale; widget UI paths can be exercised on the AVD.)

## Documentation

- `docs/settings.md` -- Settings system architecture
- `docs/grid-system.md` -- Grid calculation pipeline (detailed)
- `docs/grid-reflow.md` -- Grid reflow system (item preservation on column decrease)
- `docs/guides/adding-settings.md` -- Practical guide for adding new preferences
- `docs/changes/` -- Implementation tracking for features (numbered: `001-*.md`, `002-*.md`, etc.)
- `docs/plans/` -- Implementation plans (numbered). `docs/plans/000-architectural-refactor-superplan.md` is the canonical execution runbook, tier log, and AVD/test reference.
- `docs/superpowers/specs/` & `docs/superpowers/plans/` -- specs and plans produced by the superpowers brainstorming → writing-plans flow.
- `tests-e2e/README.md` -- how to run and write e2e tests.

## Process

The project uses a **plan-then-implement** workflow: understand existing code deeply before modifying, with explicit consideration for AOSP's initialization ordering.

### Use the superpowers skills
Agents working in this repo are expected to use the superpowers skills as their default operating discipline (they override default behavior, but these CLAUDE.md instructions take precedence over the skills):

- **`brainstorming`** -- BEFORE any feature/creative work (new widgets, settings, behavior changes). Produces a spec in `docs/superpowers/specs/`. Do not write code until the design is approved.
- **`writing-plans`** -- turn an approved spec into a numbered implementation plan (`docs/plans/NNN-*.md` or `docs/superpowers/plans/`).
- **`test-driven-development`** -- before writing implementation code; add the e2e/regression test first when a path is observable (see the Testing section).
- **`systematic-debugging`** -- for any bug, test failure, or unexpected behavior, before proposing a fix.
- **`verification-before-completion`** -- before claiming anything is done/fixed/passing: build `assembleDebug` AND run the relevant `tests-e2e/` suite, and report the actual output.
- **`requesting-code-review`** / **`receiving-code-review`** -- when a major step is done or before merging.
- **`finishing-a-development-branch`** -- to decide merge/PR/cleanup once work is complete.

### Subagent-driven development
For plans with independent steps, use **`subagent-driven-development`** (same-session) or **`dispatching-parallel-agents`** (2+ tasks with no shared state / sequential dependency) to parallelize. Keep each subagent's task well-bounded with a clear interface and success criteria; the orchestrator owns integration, the build, and the test gate. Use **`executing-plans`** when running a written plan with review checkpoints.

### Per-change execution loop (from the superplan)
1. **Branch off `dev`**: `git checkout -b feature/<short-name>` (or `refactor/<tier>-<name>`).
2. **Write the change doc first** in `docs/changes/NNN-<name>.md` -- sets intent before code. **Next number: `089`.**
3. **Add tests first** when reasonable (TDD), per the Testing section.
4. **Implement** per the plan.
5. **Build** `assembleDebug` -- must pass. Never `--no-verify`.
6. **Install** to the AVD and **run smoke** (+ regression for non-trivial change) -- must pass.
7. **Manual exploratory pass** (5-10 min) against the AVD for the touched feature.
8. **Commit + open PR** against `dev`. Prefer the `/commit` skill (writes/updates the change doc, commits, pushes `dev`); `/release` handles `dev`→`main`. `/slop-detect` after an implementation, `/code-review` before merge.

### Invariants for any change
- **Every change carries a `docs/changes/0NN-*.md` entry.** Build must compile (`assembleDebug`) and the relevant e2e suite must stay green before commit.
- **AOSP-origin file edits** (e.g. `BaseAllAppsAdapter`, `FloatingHeaderView`, `LoaderCursor`, `WorkspaceLayoutManager`, `DeviceProfile`, `InvariantDeviceProfile`, `Workspace`, `Folder`, `AllAppsStore`) require explicit justification in the change doc -- minimize divergence from upstream.
- **`docs/architecture/drawer-invariants.md` is required reading** before any all-apps / drawer refactor.
- **Commit identity:** use the project's pinned author identity via `git -c user.name=... -c user.email=...` -- NEVER change git config permanently. Commits carry **no `Co-Authored-By` trailer and no AI/agent attribution** (invisible mode). The `/commit` skill handles both.
