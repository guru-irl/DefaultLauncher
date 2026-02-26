---
name: commit
description: >
  Commit and push current changes to dev with documentation.
  Analyzes changes, creates/updates docs and a numbered change file,
  commits everything, and pushes to dev.
disable-model-invocation: false
argument-hint: "[optional summary]"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash(git *)
---

# Commit & Ship — Documentation-Aware Commit

You are committing and pushing code changes for a custom Android launcher based on AOSP
Launcher3. Your job is to analyze the current changes, write appropriate documentation,
commit everything, and push to `dev`.

If `$ARGUMENTS` is provided, use it as a hint for the feature/change summary.

---

## Step 1: Gather Context

Run these commands to understand the current state:

```
git status
git log origin/dev..HEAD --oneline
git diff HEAD --stat
git diff --cached --stat
```

Also read the actual changed files (use `git diff HEAD` or read modified files directly)
to deeply understand what was implemented. You need this understanding for documentation.

If there are no changes (nothing uncommitted, nothing unpushed), stop and tell the user
there's nothing to commit.

---

## Step 2: Determine the Next Change Number

Glob `docs/changes/*.md` and find the highest-numbered file. The next change file will
use that number + 1, zero-padded to 3 digits.

Example: if `037-settings-duplication-consolidation.md` is the highest, the next file
is `038-something.md`.

---

## Step 3: Assess Scope

Categorize the change:

- **Small fix / polish / refactoring**: Only create a change file (Step 4). Skip Step 5.
- **New feature or significant system change**: Create a change file (Step 4) AND update
  or create architecture documentation (Step 5).

Indicators of a "new feature or significant system change":
- New user-facing functionality (settings, UI components, gestures)
- New data model or DB schema changes
- New system (e.g., search, icon packs, widget stacks)
- Architectural refactoring that changes how subsystems interact

Indicators of "small fix / polish":
- Bug fixes
- Code quality improvements (deduplication, renaming, cleanup)
- Build system fixes
- Minor UI tweaks

---

## Step 4: Create Change File

Create `docs/changes/NNN-kebab-case-title.md` following this format:

```markdown
# NNN: Human-Readable Title

## Summary

2-3 sentences explaining what changed and why. Focus on the "what" and "why",
not the "how".

## [Additional Sections As Needed]

Pick from these based on relevance (don't include empty sections):

- **New Files** — list new files with brief descriptions
- **Modified Files** — per-file breakdown of what changed
- **Settings Added** — table with Setting, Key, Type, Options, Default columns
- **Design Decisions** — rationale for architectural choices
- **Known Limitations** — honest about edge cases or future work
```

**Style guidelines:**
- Title in the `# NNN:` header should be concise (3-8 words)
- kebab-case filename should match the title
- Summary should be understandable without reading the code
- For "Modified Files" sections, group by theme or phase if there are many files
- Include code snippets only when they clarify a non-obvious design decision
- Don't include trivially obvious information ("updated imports")

---

## Step 5: Update Architecture Docs (Large Changes Only)

Skip this step for small fixes.

For new features or significant changes:

1. Check if an existing doc in `docs/` covers this area (read `docs/README.md` for the
   index). Prefer **updating** an existing doc over creating a new one.
2. If creating a new doc, follow the style of existing architecture docs:
   - System overview with ASCII diagram showing component relationships
   - Per-component explanation
   - Integration points
   - Key files reference table
3. If creating a new doc, also add it to the Architecture table in `docs/README.md`.

Existing architecture docs:
- `docs/grid-system.md` — grid pipeline
- `docs/grid-reflow.md` — grid reflow
- `docs/hotseat-architecture.md` — hotseat
- `docs/settings.md` — settings system
- `docs/search-system.md` — search
- `docs/icon-shapes-and-packs.md` — icon packs
- `docs/per-app-icon-customization.md` — per-app icons
- `docs/folders.md` — folders
- `docs/widget-stack.md` — widget stacks

---

## Step 6: Update docs/README.md Change Log

Insert the new change file entry at the **top** of the Change Log table in
`docs/README.md`, immediately after the header rows:

```markdown
| [NNN](changes/NNN-kebab-title.md) | Short description |
```

The table header looks like:
```
| # | Change |
|---|--------|
```

Insert the new row right after `|---|--------|`.

---

## Step 7: Stage and Commit

Stage all relevant files:
- Source code changes (the implementation files)
- Documentation (change file, README.md, any updated architecture docs)

Do NOT stage:
- `.env`, credentials, or secrets
- Build outputs or generated files
- IDE configuration files

Create the commit using this exact format:

```bash
git commit -m "$(cat <<'EOF'
Concise summary of the change (imperative mood, under 72 chars)

Optional 1-2 sentence body explaining the "why" if the title isn't enough.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

**Commit message guidelines:**
- First line: imperative mood, under 72 characters, describes the overall change
- If there are both implementation changes AND doc updates, the first line should
  describe the implementation (not "add docs")
- Body is optional — use it if the title alone doesn't explain the "why"

---

## Step 8: Push to dev

```bash
git push origin dev
```

If the push fails (e.g., rejected due to remote changes), tell the user and suggest
`git pull --rebase origin dev` rather than force-pushing.

---

## Rules

- NEVER push to `main`. Always push to `dev`.
- NEVER force push.
- NEVER skip the change file — every commit gets one.
- NEVER commit files that contain secrets or credentials.
- If there are uncommitted changes AND unpushed commits, combine them all into a single
  new commit (don't amend previous commits).
- If `$ARGUMENTS` is provided, use it to inform the change title and summary, but still
  read the actual changes to write accurate documentation.
- Use the actual diff to write documentation — don't guess or hallucinate file changes.
