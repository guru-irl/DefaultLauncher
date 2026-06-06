# 089: Agent Docs - AVD Testing, Workflow, and Commit Style

## Summary

Expands `CLAUDE.md` (the default doc every agent loads) so agents can run and
write the e2e test suite against the AVD, follow the superpowers + subagent
workflow, and honor two new style rules: no em-dashes anywhere and no AI
co-author attribution in commits (invisible mode). The `/commit` skill template
is updated to match. No application code changes.

## Modified Files

- **`CLAUDE.md`**
  - Build: flagged that the Java path is platform-specific. The checked-in
    Windows path does not exist on this Linux box; the working path here is
    `/opt/android-studio/jbr/bin/java`. Added the debug APK output path.
  - New `## Testing (AVD + e2e suite)` section: device/env (AVD is
    `emulator-5554`, Pixel 7 Pro, Android 17), the destructive-ops guard for the
    attached physical Samsung, the build/install/test loop, expected green
    baseline, how to write tests, and AVD caveats (`app_current()` staleness;
    which suites skip on the AVD). The AVD does have a widget picker; only work
    profile and private space are absent.
  - Expanded `## Process` into an agent workflow: which superpowers skills to use
    when, subagent-driven development guidance, the per-change execution loop
    (next change number, branch, change-doc-first, tests, build, install+smoke),
    and the standing invariants.
  - New `### Writing Style` convention: no em-dashes; no AI/agent attribution.
  - Documentation index updated to list `docs/plans/`, `docs/superpowers/`, and
    `tests-e2e/README.md`.

- **`.claude/skills/commit/SKILL.md`**
  - Commit template now uses the pinned per-command identity and carries no
    `Co-Authored-By` trailer.
  - Added guidelines: no co-author/AI attribution, no em-dashes in messages.

## Design Decisions

- Distilled durable facts into `CLAUDE.md` and left the long-form runbook,
  per-tier coverage, and known-good baselines in
  `docs/plans/000-architectural-refactor-superplan.md` and `tests-e2e/README.md`
  rather than duplicating them.
- Surfaced the Windows-vs-Linux Java path discrepancy instead of silently
  rewriting the existing Build block, since the repo is used across machines.

## Known Limitations

- The superplan's historical log still contains the stale "emulator has no widget
  picker" note and an Opus co-author convention reference. Those are point-in-time
  log entries and were left as-is; the authoritative guidance now lives in
  `CLAUDE.md` and the `/commit` skill.
