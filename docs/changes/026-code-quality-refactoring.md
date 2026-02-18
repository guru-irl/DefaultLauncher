# 026 — Code Quality Refactoring

Non-color code quality pass based on a full audit of settings, lifecycle, and
icon-resolution code. Addresses handler leaks, copy-paste duplication, missing
lifecycle guards, scattered density calculations, and dead code.

## Phase 1: Lifecycle & Handler Safety

### 1A — Fragment lifecycle guards on handler posts
| File | Change |
|------|--------|
| `HomeScreenFragment.java` | Replaced inline `new Handler()` with `MAIN_EXECUTOR` + `isAdded()` guard in reset-all-icons flow |
| `AppCustomizeFragment.java` | Added `isAdded()` check to both `sMainHandler.post()` call sites; added `onDestroyView()` to clear pending callbacks |
| `IconPickerFragment.java` | Added `isAdded()` guard to 3 handler posts (loadIcons, applyOverrideChange, adapter bind); added `onDestroyView()` cleanup |

### 1B — Inline handler cleanup
| File | Change |
|------|--------|
| `IconSettingsHelper.java:401,557` | Replaced `new Handler(Looper.getMainLooper()).post()` with `Executors.MAIN_EXECUTOR.execute()` |
| `HomeScreenFragment.java:138` | Same — replaced inline handler with `MAIN_EXECUTOR` |

## Phase 2: Eliminate Code Duplication

### 2A — Icon resolution helper (`DrawerIconResolver.java`)
Extracted `resolveFromPack(IconPack, ComponentName, PackageManager)`:
- `getCalendarIcon → getIconForComponent → applyFallbackMask`
- Replaced 4 identical copy-paste blocks (State C, State D, standard, preCacheIcons)

### 2B — Shared settings dimensions (`res/values/dimens_settings.xml`)
New resource file with 18 shared dp values (corner radii, margins, slider
dimensions, padding). Replaced hardcoded `density * N` calculations in:
- `CardGroupItemDecoration.java` — card radii, margins, gaps
- `M3SliderPreference.java` — all slider layout constants
- `ColorDebugPreference.java` — grid padding, swatch size, corner radius
- `HomeScreenFragment.java` — dialog padding
- `AppCustomizeFragment.java` — dialog padding, footer padding

### 2C — Boundary check deduplication (`CardGroupItemDecoration.java`)
Extracted `isFirstInGroup()` / `isLastInGroup()` private methods from verbatim
duplicated 8-line blocks in `onDraw()` and `getItemOffsets()`.

## Phase 4: Polish & Dead Code

### 4A — Remove unused `HighlightDelegate` interface
`PreferenceHighlighter.java`: removed dead `HighlightDelegate` interface and
the `instanceof` check that always returned false.

### 4B — Cache views in `M3SliderPreference.onBindViewHolder`
On rebind, reuses the existing tagged view hierarchy and updates only dynamic
values (slider position, title text, value label) instead of removing and
recreating the entire view tree.

### 4C — Cache views in `ColorDebugPreference.onBindViewHolder`
On rebind, calls `updateGridColors()` to update existing swatch backgrounds
and labels instead of rebuilding 16 swatches + TextViews.

### 4D — Remove `WsPadDebug` log calls
Removed 6 unguarded `Log.d("WsPadDebug", ...)` calls from:
- `Launcher.java` (2 calls)
- `LauncherRootView.java` (2 calls)
- `Workspace.java` (1 call)
- Removed now-unused `Log` import from `LauncherRootView.java`

## Files Changed

| File | Phases |
|------|--------|
| `src/.../Launcher.java` | 4D |
| `src/.../LauncherRootView.java` | 4D |
| `src/.../Workspace.java` | 4D |
| `src/.../icons/DrawerIconResolver.java` | 2A |
| `src/.../settings/AppCustomizeFragment.java` | 1A, 2B |
| `src/.../settings/CardGroupItemDecoration.java` | 2B, 2C |
| `src/.../settings/ColorDebugPreference.java` | 2B, 4C |
| `src/.../settings/HomeScreenFragment.java` | 1A, 1B, 2B |
| `src/.../settings/IconPickerFragment.java` | 1A |
| `src/.../settings/IconSettingsHelper.java` | 1B |
| `src/.../settings/M3SliderPreference.java` | 2B, 4B |
| `src/.../settings/PreferenceHighlighter.java` | 4A |
| `res/values/dimens_settings.xml` | 2B (new) |

## Not Changed (by design)

- Color/theming — deferred to separate step
- AOSP-origin code (DeviceProfile, FeatureFlags, Launcher AOSP TODOs)
- `IconSettingsHelper` god class split — works correctly, split is high risk
- Kotlin migration — out of scope
