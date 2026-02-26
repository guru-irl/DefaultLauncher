---
name: slop-detect
description: >
  Run this after completing any code implementation task to review the changes for
  code deduplication, M3 Expressive compliance, technical quality, and architecture issues
  before presenting the result to the user.
disable-model-invocation: false
argument-hint: "[--full] [file-or-path]"
allowed-tools: Read, Grep, Glob, Bash(git diff*), Bash(git log*), Bash(git show*)
---

# Slop Detector — Architectural Code Review

You are an experienced Android developer performing a thorough code review on an AOSP
Launcher3-based custom launcher. Your job is to identify real, actionable problems — not
nitpicks. Focus on things that will cause bugs, confuse future developers, or violate the
project's established patterns.

## Scope

Parse `$ARGUMENTS` for flags and targets:
- `--full` flag: review entire files, not just changes. Flag issues anywhere in the file,
  not just in recently changed code.
- A **file path** (contains `/` or `.java`/`.kt`/`.xml`): review that specific file.
- A **feature name** (plain text, no path separators): search the codebase for files
  implementing that feature, then review them.

**Modes:**

| Arguments | Mode | What to review |
|-----------|------|----------------|
| *(none)* | Diff-only | Uncommitted changes (`git diff HEAD` + `git diff --cached`) |
| `src/Foo.java` | Diff-only for file | Changes in that file only |
| `--full` | Full scan | All files with uncommitted changes, reviewed in their entirety |
| `--full src/Foo.java` | Full scan for file | The entire file, flag issues anywhere |
| `widget stack` | Feature scan | Find all files implementing the widget stack feature, full review |
| `--full drag reorder` | Feature scan | Same as above (`--full` is implied for feature scans) |

**Step 1**: Gather what to review:

- **Diff-only mode** (no `--full`, no feature name):
  - If file paths given: read those files, but focus analysis on changed lines
  - If no file paths: run `git diff HEAD` and `git diff --cached` to get current changes

- **Full mode** (`--full`, no feature name):
  - If file paths given: read those files in their entirety
  - If no file paths: identify files with uncommitted changes from `git status`, then
    read each file in its entirety

- **Feature mode** (a feature name like `widget stack`, `search`, `folder`, `settings`):
  - This is always a full review (the `--full` flag is implied).
  - **Discovery**: Find all files that implement the named feature. Use multiple strategies:
    1. Check `docs/` for an architecture doc about the feature — it will list key files
       (e.g., `docs/widget-stack.md` has a File Manifest section)
    2. Check `docs/changes/` for change files related to the feature
    3. Use Grep/Glob to find classes, layouts, and resources with matching names
       (e.g., `WidgetStack*`, `widget_stack_*`)
    4. Follow references from the discovered files to find related code
       (e.g., callers, data models, helpers)
  - **Scoping**: Focus on the files that are core to the feature. Don't review every file
    that happens to mention it in passing. Aim for 5-15 core files.
  - Read each discovered file in its entirety, then analyze.

**Step 2**: For each file, also read surrounding context and related files so you
understand how the code fits into the existing codebase.

**Step 3**: Analyze against the categories below.

---

## Review Categories

### 1. Code Deduplication
Look for:
- New code that reimplements logic already present elsewhere in the codebase
- Copy-pasted blocks that should be extracted into a shared method/utility
- Multiple classes solving the same problem differently (e.g., two different approaches to
  reading preferences, two different icon-loading patterns)
- Constants or magic numbers that duplicate values defined elsewhere

**Search strategy**: When you see a pattern in new code, use Grep to check if similar
patterns exist in `src/com/android/launcher3/` before flagging.

### 2. M3 Expressive Compliance
This project uses Material 3 with Dynamic Colors. Check for:

**Colors**:
- Hardcoded colors (`#RRGGBB`, `Color.BLACK`, etc.) instead of M3 color tokens
- Using wrong color attribute namespace (`com.google.android.material.R.attr.colorPrimary`
  doesn't exist — use `R.color.materialColor*` resources or `android.R.attr.colorPrimary`)
- Missing dark theme support (colors that don't adapt)

**Typography**:
- Hardcoded text sizes instead of M3 type tokens (`TextAppearance.Material3.*`)
- Project convention: titles = Body Large (16sp/400), summaries = Body Medium (14sp/400),
  categories = Label Medium (12sp/500)

**Shape**:
- Hardcoded corner radii instead of M3 shape tokens
- Inconsistent rounding across similar components

**Motion/Animation**:
- Linear interpolators where M3 Expressive specifies easing curves
  (M3 uses `EmphasizedInterpolator`, `StandardDecelerateInterpolator`, etc.)
- Hardcoded duration values instead of M3 motion tokens
- Missing enter/exit transitions on new screens or dialogs
- Animations that don't follow M3 Expressive duration guidelines:
  - Small transitions: 150-200ms
  - Medium transitions: 250-350ms
  - Large/emphasized: 400-500ms with emphasis easing

**Components**:
- Using non-M3 components (`AlertDialog` instead of `MaterialAlertDialogBuilder`,
  `Toolbar` instead of `MaterialToolbar`)
- This project uses `BottomSheetDialog` for dialogs — check new dialogs follow this pattern

### 3. Technical Implementation Quality
Look for:

**Correctness**:
- Missing null checks on values that can genuinely be null (not paranoid null checks)
- Race conditions in async code (handler posts, coroutine scope, callback ordering)
- Resource leaks (unclosed streams, unregistered receivers/listeners, leaked contexts)
- Off-by-one errors in grid/layout calculations

**Launcher-Specific Gotchas** (from CLAUDE.md):
- `getCellLayoutHeight()`/`getCellLayoutWidth()` called before `updateWorkspacePadding()`
- Preference changes that affect grid not using `onConfigChanged()`
- Missing `getListView().post()` before `onConfigChanged()` in SettingsActivity
- References to deleted code (plugin system, quickstep, Go, test infrastructure)

**Android Best Practices**:
- View operations on wrong thread
- `Context` leaks (storing Activity context in long-lived objects)
- Missing `@SuppressLint` or `@RequiresApi` annotations where needed
- Hardcoded strings that should be in `strings.xml`

**Style & Consistency**:
- Debug logging not guarded with `BuildConfig.DEBUG` pattern
- TAG not defined as `private static final String TAG = "ClassName";`
- Naming conventions inconsistent with surrounding AOSP code
- Public methods/fields that should be package-private or private

### 4. Architecture & Design
Look for:
- God classes (new code dumping everything into one class)
- Wrong layer for the logic (UI logic in data layer, data logic in UI)
- Breaking the established three-layer grid system (XML → InvariantDeviceProfile → DeviceProfile)
- Settings not following the established preference patterns (see CLAUDE.md Conventions)
- Missing or incorrect lifecycle handling

---

## Output Format

Organize findings by severity, not by category. For each finding:

```
### [HIGH/MEDIUM/LOW] Brief title

**Category**: Deduplication | M3 Compliance | Technical | Architecture
**File**: `path/to/file.java:123`

**Problem**: What's wrong and why it matters (1-2 sentences).

**Existing pattern**: If relevant, point to where the codebase already does this correctly.

**Suggested fix**: Concrete recommendation (code snippet if helpful, but keep it brief).
```

**Severity guide**:
- **HIGH**: Will cause bugs, crashes, or significant UX issues. Violations of established
  patterns that will confuse other developers. Security issues.
- **MEDIUM**: Works but is clearly wrong — duplicated code, wrong M3 tokens, missing
  lifecycle handling. Should be fixed before merge.
- **LOW**: Style inconsistencies, minor optimization opportunities, things that technically
  work but could be cleaner.

## Rules

- In **diff-only mode**: do NOT flag issues in code that wasn't changed (unless the change
  introduces a dependency on broken existing code)
- In **full mode** (`--full`) and **feature mode**: flag issues anywhere in the reviewed
  files, even in code that wasn't recently changed
- Do NOT suggest adding comments, documentation, or type annotations unless something is
  genuinely confusing
- Do NOT suggest error handling for impossible cases
- Do NOT flag things that are standard AOSP Launcher3 patterns, even if they look unusual
- Be specific: cite file paths and line numbers, not vague guidance
- If you find zero issues, say so — don't manufacture problems
- Limit to the top 10 most impactful findings to keep the review actionable
