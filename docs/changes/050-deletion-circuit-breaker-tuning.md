# 050 — Deletion circuit-breaker tuning

## Summary

The mass-deletion circuit breaker in `LoaderCursor.commitDeleted()` introduced
by change 046 used `MASS_DELETE_FLOOR = 3` and a 25% ratio. The tertiary
audit (`docs/plans/000-architectural-refactor-superplan.md` finding #7) noted
that `floor=3` is too aggressive for normal user activity — a user
uninstalling three apps in one session legitimately would trip the breaker
and have their deletions deferred to "next bind", confusing them.

## Change

```java
- private static final int MASS_DELETE_FLOOR = 3;
- private static final int MASS_DELETE_RATIO_DIVISOR = 4;  // 25%
+ private static final int MASS_DELETE_FLOOR = 5;
+ private static final int MASS_DELETE_RATIO_DIVISOR = 5;  // 20%
```

- `floor` raised from 3 to 5: a user triple-uninstall is no longer blocked.
  Five-item single-pass deletions are still unusual enough to warrant the
  ratio check.
- `ratio` tightened from 25% to 20%: more conservative on large home
  screens; small DB users still gated by `floor`.

## Trigger matrix (post-change)

| total items | deletions | triggers? |
|-------------|-----------|-----------|
| 5 | 3 | no (floor) |
| 10 | 4 | no (floor) |
| 10 | 5 | yes (5 ≥ 5 and 5·5 = 25 ≥ 10) |
| 25 | 5 | yes (boundary) |
| 100 | 19 | no (19·5 = 95 < 100) |
| 100 | 20 | yes (boundary) |
| 500 | 100 | yes (100·5 = 500 ≥ 500) |

## Verification

- Smoke suite (19 tests) green.
- No new regression test — the breaker only fires in scenarios that require
  injecting a `LauncherApps` or `AppWidgetManager` failure, which is
  difficult to reproduce in e2e and is covered by manual repro per change
  046. The tuning itself is one-line code and reviewable by inspection.

## References

- Change 046 — defensive deletion guards (introduced the breaker).
- Superplan tertiary finding #7.
