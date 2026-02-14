# 011: Swipe Down to Open Notification Shade

## Summary

Swiping down on the homescreen opens the system notification shade. This is standard behavior in third-party launchers (Nova, Lawnchair, etc.).

## Implementation

### Permission

Added `android.permission.EXPAND_STATUS_BAR` to `AndroidManifest-common.xml`. This is a normal permission (auto-granted, no user prompt).

### StatusBarSwipeController

New `TouchController` + `SingleAxisSwipeDetector.Listener` at `src/com/android/launcher3/touch/StatusBarSwipeController.java`.

- Uses `SingleAxisSwipeDetector` (VERTICAL, `DIRECTION_NEGATIVE` = downward only)
- `canIntercept()` guards: only in NORMAL state with no floating views open
- On fling end, calls `StatusBarManager.expandNotificationsPanel()` via reflection
- Calls `mDetector.finishedScrolling()` after each gesture to reset detector state back to IDLE (prevents the controller from stealing all subsequent touches)
- No launcher state transition needed -- just a fire-and-forget system call

### Controller Registration

Added as the last entry in `Launcher.createTouchControllers()` so `DragController` and `AllAppsSwipeController` get first priority. The touch controller array is checked in order by `BaseDragLayer.findControllerToHandleTouch()`.

## Key Design Decisions

- **Reflection for StatusBarManager**: `expandNotificationsPanel()` is not in the public SDK but is safely callable with the `EXPAND_STATUS_BAR` permission. All major third-party launchers use this approach.
- **Controller ordering**: StatusBarSwipeController is last in the array. `AllAppsSwipeController` only listens for upward swipes in NORMAL state, so downward swipes fall through to our controller.
- **No visual feedback during drag**: The notification shade expansion is triggered on fling end, not during the drag. This keeps the implementation simple and avoids partial-state issues.

## Files Changed

| File | Change |
|------|--------|
| `AndroidManifest-common.xml` | Added `EXPAND_STATUS_BAR` permission |
| `src/com/android/launcher3/touch/StatusBarSwipeController.java` | New file |
| `src/com/android/launcher3/Launcher.java` | Added controller to `createTouchControllers()` |
