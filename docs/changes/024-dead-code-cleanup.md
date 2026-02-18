# 024: Dead Code Cleanup & Project Slimming

## Summary

Removed ~313,000 lines of dead AOSP code across 1,875 files, collapsing from 4 build flavors to a single variant. The project carried ~17MB of code for Quickstep, Go, desktop, taskbar, split-screen, and plugin systems that never compiled or ran in our `aospWithoutQuickstep` user-app build.

## What Changed

### Directories deleted
- `quickstep/` (733+ files, recents/overview/taskbar/desktop mode)
- `go/` (Go Edition launcher variant)
- `tests/` (AOSP instrumentation tests and TAPL)
- `src_flags/`, `src_shortcuts_overrides/`, `src_ui_overrides/` (empty placeholders)
- `src_plugins/` (SystemUI plugin interfaces -- 2 kept in `src/`)
- `checks/` (AOSP lint checker, Soong-only)
- `aconfig/` (feature flag declarations, Soong-only)
- `compose/` (disabled Compose facade)
- `secondarydisplay/` (external display launcher, system-only)
- All `Android.bp` files (AOSP Soong build configs)

### Build simplified
- Removed `flavorDimensions`, `productFlavors` (aosp/l3go x withQuickstep/withoutQuickstep), `variantFilter`
- Removed 8 dead `sourceSets` entries and phantom directory references
- Moved `applicationId` to `defaultConfig`
- Merged `src_no_quickstep/` (5 files) into `src/`
- Merged `AndroidManifest.xml` into `AndroidManifest-common.xml`
- Build target: `assembleDebug` (was `assembleAospWithoutQuickstepDebug`)

### Plugin system removed
- Deleted `PluginManagerWrapper`, `PluginHeaderRow`, `OverlayEdgeEffect`
- Stripped `PluginListener` interfaces from: Launcher, FloatingHeaderView, DynamicResource, CustomWidgetManager
- Removed overlay/Google Discover feed code from: Launcher, Workspace, DragLayer
- Removed `mAdditionalHeaderRows` plugin infrastructure from ActivityAllAppsContainerView
- Simplified DynamicResource (plain ResourceProvider, no plugin loading)
- Simplified CustomWidgetManager (XML-based widgets only, no plugin loading)
- Updated Dagger modules (removed PluginManagerWrapperModule)
- Kept `ResourceProvider` and `CustomWidgetPlugin` interfaces in `src/com/android/systemui/plugins/`

### Dead compiled code removed
- `secondarydisplay/` (6 files + SecondaryLauncherAllAppsContainerView + layout XML)
- `createDeviceProfileForSecondaryDisplay()` from InvariantDeviceProfile
- `TestLogging.java`, `TestEventEmitter.java`, `TestInformationHandler.java`, `TestInformationProvider.java`
- 10 `TestLogging.recordEvent()` call sites across 8 files
- 2 `TestLogging.recordKeyEvent()`/`recordMotionEvent()` calls from Launcher.java
- 3 `TestEventEmitter.sendEvent()` call sites across 3 files
- Manifest entries for SecondaryDisplayLauncher and TestInformationProvider

### Files kept
- `TestProtocol.java` (in `shared/src`) -- still used for state ordinals and accessibility events
- `ResourceProvider.java`, `CustomWidgetPlugin.java` -- still referenced by compiled code

## Impact
- 1,875 files changed, 103 insertions, 312,923 deletions
- Build target changed from `assembleAospWithoutQuickstepDebug` to `assembleDebug`
- No runtime behavior changes -- all deleted code was unreachable
