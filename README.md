# Default Launcher

A custom Android launcher built on AOSP Launcher3 (Android 16).

## About

This project uses the clean AOSP Launcher3 port by [yuchuangu85](https://github.com/yuchuangu85/Launcher3) (`Launcher3-16-s2-release` branch) as its foundation. The upstream port extracts Launcher3 from [AOSP](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android16-s2-release) into a standalone Gradle project with prebuilt framework stub JARs.

## Branch: `launcher3-base`

This branch contains the unmodified Launcher3 codebase with only the changes necessary to build outside of AOSP. It serves as the integration baseline — custom features are built on top of this in other branches.

### Configuration

| Setting | Value |
|---------|-------|
| `applicationId` | `com.guru.defaultlauncher` |
| Internal package | `com.android.launcher3` (unchanged from AOSP) |
| `compileSdk` | 36 |
| `minSdk` | 33 (Android 13+) |
| `targetSdk` | 36 |
| AGP | 8.13.1 |
| Gradle | 9.2.1 |
| JDK | 21 |
| Build variant | `aospWithoutQuickstepDebug` |

### Changes from upstream port

The following changes were made on top of the [yuchuangu85/Launcher3](https://github.com/yuchuangu85/Launcher3/tree/Launcher3-16-s2-release) `Launcher3-16-s2-release` branch:

**Project configuration:**
- Changed `applicationId` to `com.guru.defaultlauncher` (aosp and l3go flavors)
- Changed `app_name` to "Default Launcher" in `res/values/strings.xml`
- Removed Aliyun Maven mirror repositories from `settings.gradle`
- Removed AOSP build system files (`Android.bp`, `CleanSpec.mk`)
- Removed `CLAUDE.md`, `image/` directory, `RecentsAnimationController分析.md`
- Created empty `src_ui_overrides/`, `src_flags/`, `src_shortcuts_overrides/` directories (referenced by `build.gradle` source sets but missing from the branch)

**Build fixes:**
- Fixed `plugin_core.jar` reference to `PluginCoreLib.jar` in `withoutQuickstep` dependency (filename mismatch)
- Added all 59 missing static delegate methods to `com.android.launcher3.Flags` facade class (the upstream `Flags.java` was incomplete relative to the `FeatureFlags` interface)
- Added missing `enableCreateAnyBubble()` and `enableBubbleToFullscreen()` to `com.android.wm.shell.Flags`
- Applied `framework-16.jar` to all subproject classpaths via root `build.gradle` `subprojects {}` block (submodules need hidden API access)
- Added `implementation(project(":flags"))` dependency to `IconLoader` and `WMShared` modules
- Added `com_android_window_flags.jar` and `com_android_wm_shell_flags.jar` to `WMShared` dependencies

**Hidden API compat layer** (`wm_shared/src/com/android/wm/shell/shared/compat/HiddenApiCompat.kt`):

Several WMShared desktop-mode files reference hidden framework APIs not present in the `framework-16.jar` stub. These were replaced with reflection-based access:
- `MinimizeAnimator.kt` — `TransitionInfo.Change.leash`, `Choreographer.vsyncId`, `InteractionJankMonitor`
- `WindowAnimator.kt` — `TransitionInfo.Change` fields, `Choreographer.vsyncId`
- `DesktopModeCompatPolicy.kt` — `TaskInfo` hidden fields, `DesktopModeFlags`, `PackageManager` hidden methods, `com.android.internal.R`
- `ManageWindowsViewContainer.kt` — `TaskSnapshot`, `SurfaceView.cornerRadius`, `Surface.attachAndQueueBufferWithColorSpace`
- `DropTargetManager.kt` — `View.isLayoutRtl` replaced with public `layoutDirection` check

## Building

### Android Studio
1. Open the project in Android Studio (Meerkat 2024.3.1+)
2. Set Gradle JDK to **jbr-21** (Settings > Build Tools > Gradle)
3. Wait for Gradle sync
4. Set build variant to **`aospWithoutQuickstepDebug`**
5. Edit Run Configuration: set Launch to **"Specified Activity"** > `com.android.launcher3.Launcher` (the app uses `HOME` category, not `LAUNCHER`)
6. Run on a device/emulator with API 33+

### Command line
```
gradlew assembleAospWithoutQuickstepDebug
```

## Project structure

This is a multi-module Gradle project. The root directory is the app module.

| Module | Directory | Description |
|--------|-----------|-------------|
| (root app) | `/` | Main launcher application |
| `:IconLoader` | `iconloaderlib/` | App icon loading and caching |
| `:Animation` | `animationlib/` | Shared animation utilities |
| `:Shared` | `shared/` | Shared test utilities |
| `:WMShared` | `wm_shared/` | Window Manager shell shared code |
| `:msdl` | `msdllib/` | Motion Signal Description Language |
| `:flags` | `flagslib/` | Feature flags (aconfig-generated) |
| `:systemUIPluginCore` | `systemUIPluginCore/` | SystemUI plugin interfaces |
| `:androidx-lib` | `androidx-lib/` | Custom AndroidX dynamic animation |

## Credits

- [AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3/) — the original source
- [yuchuangu85/Launcher3](https://github.com/yuchuangu85/Launcher3) — Gradle port of AOSP Launcher3 with prebuilt framework stubs

## License

```
Copyright (C) 2008 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
