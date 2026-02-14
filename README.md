<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/banner-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset=".github/banner.svg">
    <img src=".github/banner.svg" alt="Default Launcher" width="720">
  </picture>
</p>

<p align="center">
  A custom Android launcher built on <strong>AOSP Launcher3</strong> (Android 16).<br>
  Stripped of clutter. Tuned for phones. Styled with Material 3.
</p>

<p align="center">
  <img alt="API 33+" src="https://img.shields.io/badge/API-33%2B-brightgreen">
  <img alt="Android 16" src="https://img.shields.io/badge/AOSP-Android%2016-blue">
  <img alt="Material 3" src="https://img.shields.io/badge/Material%203-Dynamic%20Colors-purple">
  <img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-orange">
</p>

---

## What is this?

Default Launcher takes stock Android's home screen and makes the choices Google won't. No search bar welded to the top. No wasted space between icons. No settings hidden behind flags. Just a clean grid that fills your screen, with every knob exposed in a proper settings page.

It's built directly on top of AOSP Launcher3 — the same code that ships on Pixel phones — so everything that works on stock Android works here. We just removed the parts that get in the way and added the parts that should have been there.

## The grid

Most launchers give you a fixed grid — 5 columns, 5 rows, take it or leave it. Default Launcher flips this around:

1. **You pick the columns** (4 to 10). Want a dense grid with tiny icons? Go for 10. Want big, easy-to-tap icons? Pick 4.
2. **Rows figure themselves out.** The launcher measures your screen, makes each cell a perfect square, and calculates how many rows actually fit. A tall phone might get 7 rows where a shorter one gets 5.
3. **Gaps are uniform everywhere.** The space between icons, and between icons and the screen edge, is exactly the same — no awkward padding at the top or bottom.

The hotseat (the row of apps at the bottom) is part of the same grid — same icon size, same spacing. It's just the last row, not a separate thing floating in space.

When you change the column count, your existing icons aren't thrown away. A [reflow system](docs/grid-reflow.md) repacks them into the new layout, row by row, preserving their relative order.

## Features

### Home screen
- **Square grid** with user-configurable columns (4-10) and derived rows
- **Uniform gap distribution** — edges match inter-cell spacing, no dead zones
- **Hotseat as grid row** — same sizing and gaps as the workspace
- **Icon size selector** — four presets (S/M/L/XL) or a custom percentage (50-100%)
- **Grid reflow** — icons repack automatically when columns change
- **No QSB, no page dots** — removed by default

### App drawer
- **Label size slider** — adjust text from 10sp to 18sp
- **Two-line labels** — show full app names when one line isn't enough
- **Row gap control** — choose between 16dp, 24dp, or 32dp spacing
- **M3 search bar** — 56dp pill with dynamic colors, proper icon placement
- **Smooth scrolling** — fixed AOSP's per-frame letter sidebar rebuild

### Icons
- **ADW icon pack support** — works with thousands of existing icon packs
- **Icon shape picker** — circle, square, 4-sided cookie, 7-sided cookie, arch, or none (raw icons)
- **Fallback masking** — apps without pack icons get the pack's background/mask treatment
- **Cache-clearing apply flow** — icons update instantly, no stale bitmaps

### Settings
- **Material 3 throughout** — dynamic colors from your wallpaper, dark mode support
- **Collapsing toolbar** with Dancing Script branding
- **Card-grouped preferences** — Lawnchair-style rounded sections with dividers
- **M3 slider** — custom Material 3 slider replacing the stock SeekBar
- **Color debug swatch** — see your entire dynamic color palette at a glance
- **Inline icon size toggle** — pick a size right in the settings row, no dialog needed

### Under the hood
- **Three-layer grid system**: static XML definitions, parsed config (survives rotation), pixel-level calculations (per orientation) — [detailed docs](docs/grid-system.md)
- **Typed preferences** via `LauncherPrefs` with backup support and change listeners
- **Hidden API compat layer** for WMShared desktop-mode APIs via reflection

## Screenshots

*Coming soon*

## Building

### Android Studio
1. Open the project in Android Studio (Meerkat 2024.3.1+)
2. Set Gradle JDK to **jbr-21** (Settings > Build Tools > Gradle)
3. Set build variant to **`aospWithoutQuickstepDebug`**
4. Set Launch Activity to `com.android.launcher3.Launcher` (uses `HOME` category, not `LAUNCHER`)
5. Run on a device/emulator with API 33+

### Command line
```bash
"/c/Program Files/Android/Android Studio/jbr/bin/java" \
  -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain assembleAospWithoutQuickstepDebug
```

## Project structure

| Module | Directory | Description |
|--------|-----------|-------------|
| Root app | `/` | Main launcher application |
| `:IconLoader` | `iconloaderlib/` | Icon loading, caching, and pack integration |
| `:Animation` | `animationlib/` | Shared animation utilities |
| `:Shared` | `shared/` | Shared test utilities |
| `:WMShared` | `wm_shared/` | Window Manager shell shared code |
| `:msdl` | `msdllib/` | Motion Signal Description Language |
| `:flags` | `flagslib/` | Feature flags |
| `:systemUIPluginCore` | `systemUIPluginCore/` | SystemUI plugin interfaces |
| `:androidx-lib` | `androidx-lib/` | Custom AndroidX dynamic animation |

## Documentation

| Doc | What it covers |
|-----|---------------|
| [Grid system](docs/grid-system.md) | Three-layer architecture, square cell math, DeviceProfile calculations |
| [Grid reflow](docs/grid-reflow.md) | How icons are preserved when columns change |
| [Settings system](docs/settings.md) | SettingsActivity, preference patterns, change propagation |
| [Icon shapes & packs](docs/icon-shapes-and-packs.md) | Shape rendering pipeline, ADW pack format, cache invalidation |
| [Hotseat architecture](docs/hotseat-architecture.md) | Hotseat sizing, DB model, square grid integration |
| [Adding settings guide](docs/guides/adding-settings.md) | Step-by-step guide for new preferences |

### Change log

Implementation details for each feature are tracked in [`docs/changes/`](docs/changes/):

| # | Feature |
|---|---------|
| [001](docs/changes/001-square-grid-system.md) | Square grid system |
| [002](docs/changes/002-hotseat-as-workspace-row.md) | Hotseat as workspace row |
| [003](docs/changes/003-grid-reflow-on-column-decrease.md) | Grid reflow on column decrease |
| [004](docs/changes/004-opinionated-defaults-and-gap-constants.md) | Opinionated defaults and gap constants |
| [005](docs/changes/005-app-drawer-cleanup.md) | App drawer cleanup |
| [006](docs/changes/006-adw-icon-pack-support.md) | ADW icon pack support |
| [007](docs/changes/007-icon-size-selector.md) | Icon size selector |
| [008](docs/changes/008-m3-settings-redesign.md) | M3 settings redesign |
| [009](docs/changes/009-m3-app-drawer-polish.md) | M3 app drawer polish |
| [010](docs/changes/010-app-drawer-label-settings.md) | App drawer label settings |

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Release |
| `dev` | Active development |
| `launcher3-base` | Clean AOSP Launcher3 before customizations |

## Credits

- [AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3/) — the foundation
- [yuchuangu85/Launcher3](https://github.com/yuchuangu85/Launcher3) — Gradle port with prebuilt framework stubs

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
