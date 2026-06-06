# DefaultLauncher e2e Test Suite

End-to-end UI tests driving the launcher on a real device or AVD via uiautomator2.

## Quick start

```bash
# 1. Build the debug APK once
cd ..
/opt/android-studio/jbr/bin/java -Xmx2g -Xms256m \
  -Dorg.gradle.appname=gradlew \
  -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain assembleDebug

# 2. Start the AVD (or attach a phone) and confirm
adb devices

# 3. Run smokes (auto-creates venv on first run)
cd tests-e2e
./run.sh                # smoke only
./run.sh --full         # smoke + regression
./run.sh --stress       # opt-in soak tests
./run.sh -k workspace   # by keyword
```

Smokes take <5 minutes. Regressions take ~15 minutes. Stress is opt-in.

## Layout

```
tests-e2e/
├── README.md            ← this file
├── pyproject.toml       ← deps + pytest config
├── conftest.py          ← session/test fixtures
├── run.sh               ← entry point
├── lib/
│   ├── launcher.py      ← LauncherDriver facade
│   ├── selectors.py     ← all resource IDs / descs / timeouts
│   └── adb_setup.py     ← install + reset helpers
├── smoke/               ← golden paths, ~5 min
├── regression/          ← exhaustive, ~15 min
├── stress/              ← soak, opt-in
└── visuals/baseline/    ← reference screenshots for diffs
```

## How to write a test

Smokes assert one user-visible thing. Regressions can string several together.

```python
import pytest

@pytest.mark.smoke
@pytest.mark.drawer
def test_drawer_opens_via_swipe_up(launcher):
    launcher.go_home()
    assert not launcher.drawer_open()
    launcher.open_drawer()
    assert launcher.drawer_open()
    launcher.close_drawer()
    assert not launcher.drawer_open()
```

Rules:
1. **Use `LauncherDriver` methods. Never inline selectors.** If you need a new
   selector or interaction, add it to `lib/selectors.py` + `lib/launcher.py` first.
2. **No `time.sleep` in tests.** Use uiautomator2 wait semantics. If a timed
   wait is truly needed, name the duration in `lib/selectors.py`.
3. **One fixture per test stage.** `launcher` for stateless tests, `clean_launcher`
   for tests that mutate persistent state (drag, settings, folder create).
4. **Mark your test.** `@pytest.mark.smoke|regression|stress` + a feature mark
   (`drawer|workspace|search|settings|grid|prefs|model`).

## When tests fail

Artifacts dumped automatically to `tests-e2e/artifacts/<test_name>.{png,xml,logcat.txt}`.

- `.png` — final-frame screenshot
- `.xml` — UI hierarchy at failure
- `.logcat.txt` — last 500 logcat lines

## CI integration (future)

`run.sh --full` exits non-zero on any failure. JUnit XML available via
`--junitxml=report.xml` for upstream parsing. HTML report at `--html=report.html`.

## Adding a new feature area

If you're adding a major feature, also add:
1. A new smoke file (`smoke/test_<feature>_basics.py`) covering 2-4 golden paths.
2. A new regression file if the feature has non-trivial state machines.
3. Update this README's coverage table.

## Coverage commitments

| Tier | Smoke | Regression | Stress |
|------|-------|-----------|--------|
| Phase 0 | 20 | 0 | 0 |
| T0 (hotfixes) | +2 | +2 | 0 |
| T1 (small plans) | +3 | +4 | 0 |
| T2 (medium plans) | +6 | +10 | +2 |
| T3 (architecture) | +10 | +15 | +3 |
| Clock widget (091) | +2 | +1 | 0 |

The clock widget also adds 1 visual test (`visuals/test_clock_widget_paint.py`).
