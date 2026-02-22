# 033 — Folder & Icon Picker Fixes

## Summary

Fixes for folder notification dots, folder popup menu positioning, unicorn icon theming, and icon picker transition animation.

## Changes

### Notification dot positioning on folders (`FolderIcon.java`)

`drawDot()` always computed icon bounds from the 1x1 preview size, which was wrong for expanded (NxN) folders and covered folders. Branched on folder state:
- **Expanded**: uses the square area centered in the view
- **Covered**: uses the preview background area
- **Default (1x1 uncovered)**: original logic unchanged

Also added a fallback for transparent dot color — `PreviewBackground.setup()` is never called for expanded/covered folders so `getDotColor()` returns 0. Falls back to `Themes.getAttrColor(context, R.attr.notificationDotColor)`.

### Folder long-press popup position (`PopupContainerWithArrow.java`, `FolderPopupHelper.java`)

The popup used `FolderIcon.getFolderName()` (a `BubbleTextView`) as the anchor. For that view, `getIcon()` returns null, so `getTargetObjectLocation()` used the full view height as the icon area — placing the popup in the middle of large NxN folders.

Added `mPositionAnchor` field to `PopupContainerWithArrow`. When set to a `FolderIcon`, `getTargetObjectLocation()` computes bounds based on `iconSizePx` at the top of the folder view. `FolderPopupHelper` calls `setPositionAnchor(folderIcon)` before `populateAndShowRows()`.

### Unicorn icon wrong color in light mode (`FolderPopupHelper.java`)

`sCachedEmojiDrawable` was a static field that persisted across light/dark theme switches. Removed the cache — `createEmojiDrawable()` always creates a fresh bitmap (negligible cost at 24dp).

### Icon picker transition animation (`PerAppIconSheet.java`)

- Replaced `FastOutSlowInInterpolator` with `Interpolators.EMPHASIZED` (M3 expressive)
- Changed `SLIDE_DURATION` from `MEDIUM_2` (300ms) to `MEDIUM_4` (400ms)
- Locked root `FrameLayout` height before adding icon page to prevent bottom sheet from expanding to fullscreen
- Unlocked root height on back transition after icon page is removed

## Files Modified

| File | Change |
|------|--------|
| `src/.../folder/FolderIcon.java` | Branched `drawDot()` by folder state, dot color fallback |
| `src/.../folder/FolderPopupHelper.java` | Removed emoji cache, added position anchor call |
| `src/.../popup/PopupContainerWithArrow.java` | Added `mPositionAnchor` + FolderIcon handling |
| `src/.../settings/PerAppIconSheet.java` | M3 expressive animation, root height locking |
