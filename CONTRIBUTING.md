# Contributing to Default Launcher

Thanks for your interest in contributing! This guide covers everything you need to get started.

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

> **Note:** `gradlew` and `gradlew.bat` are gitignored. Always use the wrapper jar directly as shown above.

## Branch Workflow

| Branch | Purpose |
|--------|---------|
| `main` | Release |
| `dev` | Active development |
| `launcher3-base` | Clean AOSP Launcher3 before customizations |

To contribute:

1. Fork the repository
2. Create a feature branch from `dev`
3. Make your changes
4. Open a pull request into `dev`

## Code Conventions

- Follow existing AOSP code style
- Use `LauncherPrefs` for new settings — register a `ConstantItem` with `backedUpItem()` and read via `LauncherPrefs.get(context).get(...)`
- See [`docs/guides/adding-settings.md`](docs/guides/adding-settings.md) for the full guide on adding new preferences
- For settings that affect the grid, call `InvariantDeviceProfile.onConfigChanged()` on change

## Architecture Docs

The [`docs/`](docs/) folder has detailed documentation on the grid system, settings architecture, icon pipeline, and more. Read these before making changes to core systems.

## Reporting Issues

Open an issue on GitHub with steps to reproduce, your device model, and Android version.

## License

- AOSP Launcher3 code stays under the [Apache License 2.0](LICENSE)
- New code is licensed under the [GNU General Public License v3.0](LICENSE-GPL)

Add the appropriate license header to any new files you create.
