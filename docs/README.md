# DefaultLauncher

Custom Android launcher based on AOSP Launcher3 (Android 16 s2-release).

## Architecture

| Doc | Description |
|-----|-------------|
| [Grid System](grid-system.md) | Three-layer grid pipeline: XML profiles, InvariantDeviceProfile, DeviceProfile |
| [Grid Reflow](grid-reflow.md) | Item preservation when workspace columns decrease |
| [Hotseat Architecture](hotseat-architecture.md) | Hotseat-as-workspace-row design |
| [Settings System](settings.md) | Preferences, change propagation, and SettingsActivity |
| [Search System](search-system.md) | Universal search implementation |
| [Icon Shapes & Packs](icon-shapes-and-packs.md) | ADW icon pack support and shape picker |
| [Per-App Icon Customization](per-app-icon-customization.md) | Individual icon overrides per app |
| [Folders](folders.md) | Cover icons, expanded grid, resize, shapes, colors |
| [Widget Stacks](widget-stack.md) | Multiple widgets in a single cell with horizontal swipe navigation |

## Guides

| Guide | Description |
|-------|-------------|
| [Adding Settings](guides/adding-settings.md) | Step-by-step guide for adding new preferences |

## Change Log

Numbered implementation docs tracking each feature and fix:

| # | Change |
|---|--------|
| [045](changes/045-bug-fixes-and-folder-cover-icon-color.md) | Bug fixes (search, ripple, icon size) and folder cover icon color setting |
| [044](changes/044-dpi-independent-grid.md) | DPI-independent grid: Display Size changes no longer break layout |
| [043](changes/043-timezone-date-clarity-and-timed-place-queries.md) | Timezone date clarity, timed-place queries, search card light mode fix, debug color categories |
| [042](changes/042-search-enhancements.md) | Search enhancements: timezone provider, AI FAB, fuzzy scoring, progressive delivery, animations |
| [041](changes/041-settings-code-review-fixes.md) | Settings code review fixes: deduplication, M3 compliance, lifecycle bug, dp→dimens |
| [040](changes/040-search-quality-fixes.md) | Search quality fixes: FileProvider crash, localization, threading, DiffUtil |
| [039](changes/039-orthogonal-icon-toggles-provenance-detection.md) | Orthogonal icon toggles & provenance-based detection |
| [038](changes/038-widget-stack-data-integrity.md) | Widget stack data integrity & restore fixes |
| [037](changes/037-settings-duplication-consolidation.md) | Settings component deduplication (~600+ lines eliminated) |
| [036](changes/036-widget-stack-touch-and-visibility-fixes.md) | Widget stack touch & visibility fixes (Samsung disappearing bug) |
| [035](changes/035-widget-stack-editor.md) | Widget stack editor (bottom sheet reorder/remove/add) |
| [034](changes/034-widget-stack-code-quality.md) | Widget stack code quality refactoring |
| [033](changes/033-folder-and-icon-picker-fixes.md) | Folder & icon picker fixes |
| [032](changes/032-r8-minification.md) | R8 minification (APK 34 MB → 8.6 MB) |
| [031](changes/031-app-icon-refresh-and-workspace-clipping-fix.md) | App icon refresh & workspace clipping fix |
| [030](changes/030-folder-refactoring.md) | Folder refactoring |
| [029](changes/029-folder-features.md) | Folder features |
| [028](changes/028-settings-visual-polish-and-fixes.md) | Settings visual polish |
| [027](changes/027-m3-expressive-migration.md) | M3 Expressive migration |
| [026](changes/026-code-quality-refactoring.md) | Code quality refactoring |
| [025](changes/025-wallpaper-scroll-toggle-adaptive-refresh-close-anim.md) | Wallpaper scroll, adaptive refresh, close animation |
| [024](changes/024-dead-code-cleanup.md) | Dead code cleanup |
| [023](changes/023-search-perf-drawer-cache-crash-fixes.md) | Search performance & crash fixes |
| [022](changes/022-settings-surface-colors-and-ui-polish.md) | Settings surface colors & UI polish |
| [021](changes/021-per-app-render-overrides-and-sentinel-redesign.md) | Per-app render overrides & sentinel redesign |
| [020](changes/020-per-app-icon-customization.md) | Per-app icon customization |
| [019](changes/019-settings-toolbar-grid-preview.md) | Settings toolbar & grid preview |
| [018](changes/018-colors-page-reorganization.md) | Colors page reorganization |
| [017](changes/017-search-animation-home-transition-work-fab.md) | Search animation & home transition |
| [016](changes/016-universal-search.md) | Universal search |
| [015](changes/015-app-visibility-default-banner-restart.md) | App visibility & default banner |
| [014](changes/014-settings-ui-polish.md) | Settings UI polish |
| [013](changes/013-app-drawer-colors-fixes.md) | App drawer colors fixes |
| [012](changes/012-app-drawer-colors.md) | App drawer colors |
| [011](changes/011-swipe-down-notification-shade.md) | Swipe down notification shade |
| [010](changes/010-app-drawer-label-settings.md) | App drawer label settings |
| [009](changes/009-m3-app-drawer-polish.md) | M3 app drawer polish |
| [008](changes/008-m3-settings-redesign.md) | M3 settings redesign |
| [007](changes/007-icon-size-selector.md) | Icon size selector |
| [006](changes/006-adw-icon-pack-support.md) | ADW icon pack support |
| [005](changes/005-app-drawer-cleanup.md) | App drawer cleanup |
| [004](changes/004-opinionated-defaults-and-gap-constants.md) | Opinionated defaults & gap constants |
| [003](changes/003-grid-reflow-on-column-decrease.md) | Grid reflow on column decrease |
| [002](changes/002-hotseat-as-workspace-row.md) | Hotseat as workspace row |
| [001](changes/001-square-grid-system.md) | Square grid system |
