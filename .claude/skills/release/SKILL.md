---
name: release
description: >
  Create a release from dev to main. Opens a PR, merges it, tags, pushes
  the tag (triggering the CI release workflow), writes release notes on
  the draft release, and publishes it.
disable-model-invocation: false
argument-hint: "[version e.g. v0.6.0]"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash(git *), Bash(gh *)
---

# Release — PR, Merge, Tag, Release Notes, Publish

You are releasing a new version of DefaultLauncher. This skill handles the full flow:
PR creation, merge, tagging, release notes, and publishing.

If `$ARGUMENTS` is provided, use it as the version tag (e.g. `v0.6.0`).
If not provided, auto-determine the version (see Step 2).

---

## Step 1: Pre-flight Checks

Run these checks and **stop with a clear error** if any fail:

```bash
git fetch origin
git status
git log origin/main..origin/dev --oneline
```

- Current branch must be `dev`
- Working tree must be clean (no uncommitted changes)
- `origin/dev` must be ahead of `origin/main` (there must be something to release)

If the working tree is dirty, tell the user to commit or stash first.
If dev is not ahead of main, tell the user there's nothing to release.

---

## Step 2: Determine Version

**If `$ARGUMENTS` specifies a version** (e.g. `v0.6.0` or `0.6.0`):
- Normalize to `vMAJOR.MINOR.PATCH` format
- Validate it's newer than the latest tag

**If no version specified**, auto-determine:

```bash
git tag --sort=-v:refname | grep '^v' | head -1
```

Parse the latest tag (e.g. `v0.5.3` → major=0, minor=5, patch=3).

Then classify the change scope by reading the change files added since the last release:

```bash
git diff origin/main..origin/dev --name-only -- docs/changes/
```

Read each new change file to understand the scope. Also check the commit log for
additional context:

```bash
git log origin/main..origin/dev --oneline
```

- If changes include new user-facing features (new settings, new UI, new capabilities): **minor bump** (0.5.3 → 0.6.0)
- If changes are only bug fixes, polish, refactoring, infrastructure: **patch bump** (0.5.3 → 0.5.4)

Present the chosen version to the user and ask for confirmation before proceeding.

---

## Step 3: Create PR from dev to main

```bash
gh pr create --base main --head dev --title "Release vX.Y.Z" --body "$(cat <<'EOF'
## Summary
<Brief description of what's in this release — 2-4 bullet points of the highlights>

## Changes
<List the key changes, grouped by category>

**Full diff**: https://github.com/guru-irl/DefaultLauncher/compare/main...dev
EOF
)"
```

Write a real summary by reading the commit log. Don't use placeholder text.

---

## Step 4: Merge the PR

```bash
gh pr merge --admin --merge
```

Use `--admin` to bypass branch protection rules.
Use `--merge` (not squash or rebase) to preserve commit history.

Wait for the merge to complete, then verify:

```bash
git fetch origin
git log origin/main -1 --oneline
```

---

## Step 5: Tag and Push

Create the tag on the **merged main branch**:

```bash
git tag vX.Y.Z origin/main
git push origin vX.Y.Z
```

This triggers the release workflow (`.github/workflows/release.yml`) which:
1. Builds debug + release APKs
2. Creates a **draft** GitHub release with template-based notes
3. Attaches both APKs to the release

---

## Step 6: Wait for the Release Workflow

Poll the workflow run until it completes:

```bash
gh run list --workflow=release.yml --limit=1
```

Check the status. If it's still running, wait and check again (use `gh run watch` if
available, otherwise poll with `gh run view <id>`). Do NOT proceed until the workflow
succeeds.

If the workflow fails, tell the user and stop. Don't try to fix it.

---

## Step 7: Write Release Notes

Once the draft release exists, read the changes to write proper release notes.

### 7a: Identify what changed

Get the previous version tag:

```bash
git tag --sort=-v:refname | grep '^v' | sed -n '2p'
```

Get the commit list for a high-level overview:

```bash
git log vPREVIOUS..vCURRENT --oneline
```

### 7b: Read the change files (primary source of truth)

The `docs/changes/` directory contains numbered change files that are curated summaries
of each implementation. These are far more accurate than raw commit messages.

Find which change files were added or modified since the last release:

```bash
git diff vPREVIOUS..vCURRENT --name-only -- docs/changes/
```

**Read each new/modified change file in full.** These contain:
- A `## Summary` with 2-3 sentences explaining the change
- Sections like `## Modified Files`, `## Design Decisions`, `## New Files`
- The right level of detail for writing user-facing release notes

### 7c: Read source files for additional clarity (if needed)

If a change file is unclear or missing context, read the actual source files it
references. For example, if a change mentions "added widget restore handling" but
doesn't explain the user impact, read the relevant source to understand what was
broken before and what works now.

### 7d: Write the notes

Follow the template format from `.github/release-notes-template.md`:

```markdown
## What's New in vX.Y.Z

### Features
- Describe new user-facing features (or delete section if none)

### Bug Fixes
- Describe bug fixes (or delete section if none)

### Improvements
- Describe enhancements to existing features (or delete section if none)

### Infrastructure
- Describe build, CI, tooling, docs changes (or delete section if none)

**Full Changelog**: https://github.com/guru-irl/DefaultLauncher/compare/vPREVIOUS...vCURRENT
```

**Style guidelines:**
- Write for **end users**, not developers. Focus on what changed from the user's
  perspective. "Widget stacks now remember which widget you were viewing" not
  "debounced active index persistence in WidgetStackView".
- Each bullet should be one line, concise.
- Delete empty sections entirely (don't leave "None" or empty headings).
- Group related changes into a single bullet when it makes sense.
- The Full Changelog link is always included.

### 7e: Update the draft release

```bash
gh release edit vX.Y.Z --notes "$(cat <<'EOF'
<release notes content>
EOF
)"
```

---

## Step 8: Publish the Release

```bash
gh release edit vX.Y.Z --draft=false
```

Verify it's published:

```bash
gh release view vX.Y.Z
```

---

## Step 9: Sync dev with main

After the merge, dev and main should be in sync. Fast-forward the local dev branch:

```bash
git pull origin dev
```

---

## Rules

- NEVER force push any branch.
- NEVER delete tags or releases.
- NEVER create a tag on `dev` — always tag `origin/main` after the merge.
- NEVER publish a release if the workflow failed.
- ALWAYS ask the user to confirm the version before proceeding (Step 2).
- ALWAYS use `--admin --merge` for the PR merge.
- The version format is strictly `vMAJOR.MINOR.PATCH` (e.g. `v0.6.0`).
- We're in beta (v0.x.x) — no major version bumps.
- If anything fails, stop and report clearly. Don't retry destructive operations.
