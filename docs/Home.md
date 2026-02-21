# DefaultLauncher

Custom Android launcher based on AOSP Launcher3 (Android 16 s2-release).

## Architecture

| Doc | Description |
|-----|-------------|
| [Grid System](grid-system) | Three-layer grid pipeline: XML profiles, InvariantDeviceProfile, DeviceProfile |
| [Grid Reflow](grid-reflow) | Item preservation when workspace columns decrease |
| [Hotseat Architecture](hotseat-architecture) | Hotseat-as-workspace-row design |
| [Settings System](settings) | Preferences, change propagation, and SettingsActivity |
| [Search System](search-system) | Universal search implementation |
| [Icon Shapes & Packs](icon-shapes-and-packs) | ADW icon pack support and shape picker |
| [Per-App Icon Customization](per-app-icon-customization) | Individual icon overrides per app |
| [Folders](folders) | Cover icons, expanded grid, resize, shapes, colors |

## Guides

| Guide | Description |
|-------|-------------|
| [Adding Settings](adding-settings) | Step-by-step guide for adding new preferences |

## Change Log

Numbered implementation docs tracking each feature and fix:

| # | Change |
|---|--------|
| [031](031-app-icon-refresh-and-workspace-clipping-fix) | App icon refresh & workspace clipping fix |
| [030](030-folder-refactoring) | Folder refactoring |
| [029](029-folder-features) | Folder features |
| [028](028-settings-visual-polish-and-fixes) | Settings visual polish |
| [027](027-m3-expressive-migration) | M3 Expressive migration |
| [026](026-code-quality-refactoring) | Code quality refactoring |
| [025](025-wallpaper-scroll-toggle-adaptive-refresh-close-anim) | Wallpaper scroll, adaptive refresh, close animation |
| [024](024-dead-code-cleanup) | Dead code cleanup |
| [023](023-search-perf-drawer-cache-crash-fixes) | Search performance & crash fixes |
| [022](022-settings-surface-colors-and-ui-polish) | Settings surface colors & UI polish |
| [021](021-per-app-render-overrides-and-sentinel-redesign) | Per-app render overrides & sentinel redesign |
| [020](020-per-app-icon-customization) | Per-app icon customization |
| [019](019-settings-toolbar-grid-preview) | Settings toolbar & grid preview |
| [018](018-colors-page-reorganization) | Colors page reorganization |
| [017](017-search-animation-home-transition-work-fab) | Search animation & home transition |
| [016](016-universal-search) | Universal search |
| [015](015-app-visibility-default-banner-restart) | App visibility & default banner |
| [014](014-settings-ui-polish) | Settings UI polish |
| [013](013-app-drawer-colors-fixes) | App drawer colors fixes |
| [012](012-app-drawer-colors) | App drawer colors |
| [011](011-swipe-down-notification-shade) | Swipe down notification shade |
| [010](010-app-drawer-label-settings) | App drawer label settings |
| [009](009-m3-app-drawer-polish) | M3 app drawer polish |
| [008](008-m3-settings-redesign) | M3 settings redesign |
| [007](007-icon-size-selector) | Icon size selector |
| [006](006-adw-icon-pack-support) | ADW icon pack support |
| [005](005-app-drawer-cleanup) | App drawer cleanup |
| [004](004-opinionated-defaults-and-gap-constants) | Opinionated defaults & gap constants |
| [003](003-grid-reflow-on-column-decrease) | Grid reflow on column decrease |
| [002](002-hotseat-as-workspace-row) | Hotseat as workspace row |
| [001](001-square-grid-system) | Square grid system |
