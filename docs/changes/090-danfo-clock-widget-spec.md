# 090: Danfo Clock Widget Design Spec

## Summary

Adds the approved design spec for a launcher-native Danfo clock widget, produced
through the brainstorming flow. No application code yet; this is the design
artifact that the implementation plan will be built from. Also gitignores the
`.superpowers/` brainstorming workspace.

## New Files

- `docs/superpowers/specs/2026-06-06-danfo-clock-widget-design.md` - the full
  design: an in-process custom widget (hosted via `CustomWidgetManager`, not
  RemoteViews) showing a single-line Danfo time with a Bebas Neue dateline,
  responsive from 2x2 upward with automatic font sizing, adaptive non-wrapping
  date abbreviation, follow-system/12h/24h time format (24h zero-padded), and
  four color/theming controls. UI and visual tests are first-class deliverables.

## Modified Files

- `.gitignore` - ignore `.superpowers/` (visual-companion brainstorming output).

## Design Decisions

Captured in the spec's "Decisions made during brainstorming" section. Key ones:
single-line layout, Bebas Neue dateline, in-process hosting for full font/sizing
control, global (not per-instance) settings for v1, and registration decoupled
from the `enableSmartspaceAsAWidget()` flag so launcher-shipped widgets register
unconditionally.

## Known Limitations

Design only. Open questions for the implementation plan (font-size clamps,
Material You resource selection, picker preview asset, settings entry point) are
listed in the spec's section 9.
