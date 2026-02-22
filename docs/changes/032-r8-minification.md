# 032: R8 Minification for Release Builds

**Branch:** `dev`

## Summary

Enabled R8 code shrinking and resource shrinking for release builds to reduce APK size
from 34.24 MB to 8.6 MB, bringing it under the 30 MB IzzyOnDroid limit. Debug builds
remain unminified.

## Changes

### build.gradle

Enabled `minifyEnabled true`, `shrinkResources true`, and added ProGuard file references
to the release build type. Debug build type unchanged.

### proguard.flags

Appended keep rules for:

- **Protobuf Lite** — `GeneratedMessageLite` subclasses use reflection to access fields
  by name; R8 was stripping fields like `parentContainer_` causing runtime crashes in
  the app drawer.
- **Dagger components** — generated `Dagger*` classes in a different package than the
  broad `com.android.launcher3.**` rules cover.
- **Custom Preference subclasses** — instantiated by AndroidX from XML `android:name`.
- **CustomWidgetPlugin implementations** — loaded via `Class.forName`.
- **Parcelable CREATOR fields** and **enum methods** — standard Android keep rules.
- **Framework stub dontwarn rules** — `compileOnly` classes not present in the APK
  (aconfig annotations, WM shell, CloseGuard, DeviceConfig, FontResourcesParser).

## Files Modified

| File | Change |
|------|--------|
| `build.gradle` | Enable R8 + resource shrinking for release build type |
| `proguard.flags` | Append protobuf, Dagger, preference, parcelable, enum, and dontwarn rules |

## APK Size

| | Before | After |
|---|--------|-------|
| Release APK | 34.24 MB | 8.6 MB |
| Reduction | — | 75% |
