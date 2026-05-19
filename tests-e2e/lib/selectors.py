"""Central registry of selectors used by the e2e suite.

Add new resource IDs / content-descs here. Tests never inline these strings —
they reach for them through LauncherDriver methods. Keeps the maintenance
cost of a UI refactor down to one file.
"""

PACKAGE = "com.guru.defaultlauncher"
LAUNCH_ACTIVITY = "com.android.launcher3.Launcher"
SETTINGS_ACTIVITY = "com.android.launcher3.settings.SettingsActivity"

# Top-level views
ID_LAUNCHER = f"{PACKAGE}:id/launcher"
ID_DRAG_LAYER = f"{PACKAGE}:id/drag_layer"
ID_WORKSPACE = f"{PACKAGE}:id/workspace"
ID_HOTSEAT = f"{PACKAGE}:id/hotseat"
ID_SCRIM_VIEW = f"{PACKAGE}:id/scrim_view"

# App-drawer-ish
ID_ALL_APPS_CONTAINER = f"{PACKAGE}:id/apps_view"
ID_ALL_APPS_RECYCLER = f"{PACKAGE}:id/apps_list_view"
ID_SEARCH_RESULTS_LIST = f"{PACKAGE}:id/search_results_list_view"
ID_SEARCH_BAR = f"{PACKAGE}:id/search_container_all_apps"
ID_SEARCH_INPUT = f"{PACKAGE}:id/search_box_input"
ID_FLOATING_HEADER = f"{PACKAGE}:id/all_apps_header"
# Search result UI extras
ID_FILTER_CHIP_GROUP = f"{PACKAGE}:id/filter_chip_group"
ID_CHIP_ALL = f"{PACKAGE}:id/chip_all"
ID_CHIP_APPS = f"{PACKAGE}:id/chip_apps"

# Folder
ID_FOLDER_NAME = f"{PACKAGE}:id/folder_name"
ID_FOLDER_CONTAINER = f"{PACKAGE}:id/folder_content"

# Workspace items use TextView / FrameLayout with description = app label.
DESC_HOME = "Home"

# Default-installed apps on a freshly seeded AVD. Tests should NOT rely on
# specific apps existing beyond what's pre-installed by the system image.
KNOWN_APPS = {
    "phone": "Phone",
    "chrome": "Chrome",
    "camera": "Camera",
    "maps": "Maps",
    "messages": "Messages",
    "clock": "World",  # AOSP clock app shows as 'World' in some images
}

# Timeouts (seconds). Centralized so we don't sprinkle sleeps everywhere.
DEFAULT_WAIT = 5.0
ANIMATION_WAIT = 1.0  # for transitions to settle
LONG_PRESS_DURATION = 1.0
