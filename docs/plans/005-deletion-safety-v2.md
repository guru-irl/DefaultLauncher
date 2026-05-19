# Plan 005 — Workspace-Item Deletion Safety v2

Supersedes the v1 deletion-safety bullet in `docs/plans/000-architectural-refactor-superplan.md` (Tier 3, T3.2).

## Mapping note

The superplan references "drop `isLauncherAppsHealthy`." That literal identifier does **not** exist in the source. The actual guard that conflates "service flaky" with "package gone" is `ServiceReadiness.isPackageProbablyInstalled(Context, String)` at `src/com/android/launcher3/util/ServiceReadiness.java:52-66`, called from:

- `src/com/android/launcher3/model/WorkspaceItemProcessor.kt:247` (restore-pending branch)
- `src/com/android/launcher3/model/WorkspaceItemProcessor.kt:286` (final pre-delete escape)
- `src/com/android/launcher3/widget/WidgetInflater.kt:104` (restored widget, transient null)
- `src/com/android/launcher3/widget/WidgetInflater.kt:204` (RESTORE_COMPLETED, transient null)

For the remainder of this plan "the legacy guard" = `ServiceReadiness.isPackageProbablyInstalled`.

## 1. Audit finding

The deletion path in `WorkspaceItemProcessor.processAppOrDeepShortcut()` decides a workspace item is dead based on a single `LauncherApps.isPackageEnabled(targetPkg, c.user)` call at line 168. Before committing the deletion, it short-circuits through `ServiceReadiness.isPackageProbablyInstalled`, which performs a one-shot `PackageManager.getPackageInfo` with `MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS`. Both probes can return false simultaneously when the `LauncherApps` binder is mid-rebind (memory-pressure reclaim after a fullscreen game/video exits) — the `getPackageInfo` call hits the same service tier and can also be transient. Single-IPC PM probe is **not independent** of the LauncherApps probe under the failure mode we actually see, so the guard fires only against the strongest negatives and lets real false positives slip through into permanent DB deletes. The `commitDeleted()` circuit breaker (`LoaderCursor.java:496-515`, floor=5, ratio=20%) absorbs *mass* failure but leaves single-digit losses invisible.

## 2. Decision tree

Inputs evaluated when an item would be marked deleted because `validTarget = launcherApps.isPackageEnabled(...)` was false (`WorkspaceItemProcessor.kt:168`):

| LauncherApps probe #1 | Short delay (50 ms) + probe #2 | PM cross-check | Action |
|---|---|---|---|
| installed | — | — | keep (no deletion attempt) |
| not installed | installed (recovered) | n/a | **defer** (return early, retry next bind) |
| not installed | not installed | installed | **defer** (PM contradicts; same-tier transient) |
| not installed | not installed | not installed | **delete** (genuine uninstall) |
| not installed | service threw / null | installed | **defer** |
| not installed | service threw / null | not installed | **defer** (refuse to delete on probe failure) |
| service threw / null | any | any | **defer** (cannot prove uninstall) |

Widget paths follow the same tree but probe `AppWidgetManager`-derived `providerName.packageName` via `LauncherApps.isPackageEnabled` (cross-IPC: widget provider null + package present ⇒ defer).

## 3. API changes

New helper `PackagePresenceVerifier` (under `com.android.launcher3.util`):

```kotlin
class PackagePresenceVerifier(
    private val launcherApps: LauncherApps,
    private val pmHelper: PackageManagerHelper,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    enum class Verdict { PRESENT, ABSENT, INCONCLUSIVE }

    /** Two LauncherApps queries separated by [retryDelayMs]; falls back to PM on ambiguity. */
    fun verifyAbsent(
        pkg: String,
        user: UserHandle,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    ): Verdict
}
```

`PackagePresenceVerifier.verifyAbsent` returns `ABSENT` only when both `isPackageEnabled` calls return false AND `pmHelper.getApplicationInfo(...) == null`. `INCONCLUSIVE` ⇒ defer.

Constants: `DEFAULT_RETRY_DELAY_MS = 50` (small; runs on `MODEL_EXECUTOR`, never main).

`WorkspaceItemProcessor` constructor gains `private val presenceVerifier: PackagePresenceVerifier`. Injected at `LoaderTask.java:504` (already constructs the processor).

`ServiceReadiness.isPackageProbablyInstalled` is **deprecated** with `@Deprecated` Javadoc pointing to the new verifier; removed in Phase C. `ServiceReadiness.snapshot(Context)` is kept (diagnostic-only, used in `LoaderCursor.java:507`).

`LoaderCursor.markDeleted(String, @RestoreError String)` signature is unchanged. The decision happens before the call.

Feature flag: add a `Flags.useDoubleIpcDeletionVerify()` boolean (default true after Phase B soak) under `flagslib`.

## 4. Phased migration

### Phase A — Add verifier, no behavior change

- New files: `src/com/android/launcher3/util/PackagePresenceVerifier.kt`, `tests/src/com/android/launcher3/util/PackagePresenceVerifierTest.kt`.
- Wire DI: add `@Provides` in the existing model module that constructs `LauncherApps` (mirroring `LoaderTask.java:201`).
- Add the feature flag, defaulted OFF.
- Unit tests cover: present-present, absent-absent-PM-absent (real uninstall), absent-then-present (transient), absent-absent-PM-present (PM contradicts), throws-throws (inconclusive). Uses fake `LauncherApps` and fake clock.
- Smoke suite: unchanged.

### Phase B — Wire into deletion paths behind the flag

- `WorkspaceItemProcessor.kt:241-263` (restore-pending branch): replace the `if (ServiceReadiness.isPackageProbablyInstalled(...))` block with `if (presenceVerifier.verifyAbsent(targetPkg, c.user) != ABSENT) { return }`.
- `WorkspaceItemProcessor.kt:281-302` (final escape before SD-card-not-ready delete): same substitution.
- `WidgetInflater.kt:99-113` and `WidgetInflater.kt:194-217`: introduce a verifier overload that takes the provider package and reuses the same instance. WidgetInflater is `@Inject` constructed (`WidgetInflater.kt:32-37`); add `PackagePresenceVerifier` to its constructor.
- Behind `Flags.useDoubleIpcDeletionVerify()`: when off, legacy `ServiceReadiness.isPackageProbablyInstalled` remains in effect — fallback path for instant rollback.
- Diagnostic log lines emit `verifier=ABSENT|PRESENT|INCONCLUSIVE` alongside the existing `ServiceReadiness.snapshot(context)` string.
- Enable flag in default config after one nightly regression cycle passes clean.

### Phase C — Remove legacy guard

- Delete `ServiceReadiness.isPackageProbablyInstalled` (keep `snapshot`).
- Delete `Flags.useDoubleIpcDeletionVerify` (graduated).
- Update `WorkspaceItemProcessor` and `WidgetInflater` to call the verifier unconditionally.
- One `docs/changes/0NN-...md` entry per phase.

## 5. Regression tests

File: `tests-e2e/regression/test_deletion_safety.py`. All three are independent (separate functions, no ordering).

| Function | Setup | Stimulus | Assertion |
|---|---|---|---|
| `test_real_uninstall_propagates_to_workspace` | Install a sentinel APK (`com.example.deletion_canary`), pin its icon to workspace via DB seed, restart launcher | `adb uninstall com.example.deletion_canary`, force launcher reload (`am broadcast android.intent.action.PACKAGE_REMOVED` then trigger reload) | Workspace cell where icon was is empty within 5 s; Favorites row deleted (`adb shell` SQL probe via launcher's content provider). Asserts the verifier doesn't over-defer. |
| `test_force_stop_launcherapps_no_delete` | Pin 3 workspace icons whose packages are all genuinely installed | `adb shell am force-stop` on `LauncherApps` host process (or simulate via test-only seam: `LauncherApps` injected fake returning `false`/`null` for one full load), then trigger workspace reload | Zero `markDeleted` log lines for those 3 packages; workspace cells unchanged after settle. Asserts double-IPC blocks the false positive. |
| `test_stable_package_retry_no_false_positive` | Pin 1 icon. Inject a fake `LauncherApps` returning `false` on the first `isPackageEnabled` call and `true` on the second (verifier seam exposed only in test build via `BuildConfig.DEBUG` factory) | Trigger workspace reload | Icon remains; `FileLog` contains a "verifier=INCONCLUSIVE, deferring" line; DB row intact. Asserts the 50 ms re-probe path works end-to-end. |

Each test uses `conftest.py::_ensure_workspace_has_icon` baseline. Failures dump `logcat -d -s WorkspaceItemProcessor:* WidgetInflater:* LoaderCursor:*` + UI XML.

A unit test counterpart lives at `tests/src/com/android/launcher3/util/PackagePresenceVerifierTest.kt` (Phase A).

## 6. WidgetInflater guards — explicit non-touch

Read in full: `src/com/android/launcher3/widget/WidgetInflater.kt:99-113` (restored-but-not-yet-RESTORE_COMPLETED branch) and `:194-217` (RESTORE_COMPLETED branch). Both guards branch on `appWidgetInfo == null` plus `ServiceReadiness.isPackageProbablyInstalled(context, providerPkg)`, returning `InflationResult(TYPE_PENDING, …)` when the provider package is still installed. The v2 plan **does not delete these guards** — it swaps the inner check from `ServiceReadiness.isPackageProbablyInstalled` to `PackagePresenceVerifier.verifyAbsent(...) != ABSENT` (Phase B). The widget-restore deferral semantics, including emitting `TYPE_PENDING` instead of `TYPE_DELETE` on inconclusive probes, are preserved verbatim. The `data class InflationResult` and the `TYPE_DELETE / TYPE_PENDING / TYPE_REAL` constants are untouched.

## 7. Non-goals

- No change to the mass-deletion circuit breaker (`LoaderCursor.commitDeleted`, `:496-515`). The breaker stays as belt-and-braces.
- No changes to `LoaderCursor.markDeleted` signature, semantics, or `RestoreError` codes.
- No coalescing of duplicate deletion calls at the cursor layer.
- No new content-provider APIs.
- No work-profile cross-user PM probe redesign (see `ServiceReadiness.java:43-50` limitation; same trade-off retained).
- No DI framework migration; reuse the existing `@Inject` plumbing that already constructs `LauncherApps`.
- No widget-host reattach changes.

## 8. Critical files

- `src/com/android/launcher3/model/WorkspaceItemProcessor.kt`
- `src/com/android/launcher3/widget/WidgetInflater.kt`
- `src/com/android/launcher3/util/ServiceReadiness.java`
- `src/com/android/launcher3/model/LoaderTask.java`
- `src/com/android/launcher3/model/LoaderCursor.java`

(New file in Phase A: `src/com/android/launcher3/util/PackagePresenceVerifier.kt`.)

## 9. Risks

- **Loader latency.** 50 ms retry on each marginal delete adds up if many items fail simultaneously. Mitigation: bail to circuit-breaker logic first (mass failure case is already handled), so verifier only runs in the 1-to-low-single-digit deletion regime.
- **Test-only seam discipline.** Injecting a fake `LauncherApps` for `test_stable_package_retry_no_false_positive` requires either a debug-only factory or a `@VisibleForTesting` constructor on `PackagePresenceVerifier`. Prefer the latter to avoid a build-variant code path.
- **Flag-on rollout regression.** Phase B leaves the legacy guard alive behind `Flags.useDoubleIpcDeletionVerify`; if rollout reveals a false-negative regression (under-deleting), flip flag off and Phase C blocks.
- **WidgetInflater double-call cost.** Two `WidgetInflater` call sites both invoke the verifier; ensure a single verifier instance per `WidgetInflater` (constructor-injected) so the 50 ms backoff isn't paid twice for one widget — second call short-circuits via the first call's PM result. Phase A unit test covers this.
- **`PackageManagerHelper` cross-user.** `pmHelper.getApplicationInfo(pkg, user, flags)` must be the user-aware variant; verify before Phase B wiring.

## 10. Open questions

- Is `MODEL_EXECUTOR.getHandler()` the correct executor on which to schedule the 50 ms retry, or should we burn the thread synchronously (it already runs off-main)? Sync is simpler; revisit if profiling shows pause-time impact during cold start.
- Should Phase A also add a debug-only `LauncherApps` wrapper that records every `isPackageEnabled` call for the regression tests, or rely on logcat scraping? Prefer wrapper if other plans (T3.1 drawer decomposition) want similar test seams.
