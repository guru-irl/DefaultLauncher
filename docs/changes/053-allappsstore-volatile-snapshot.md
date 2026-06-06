# 053 — AllAppsStore volatile + snapshot

## Summary

`AllAppsStore.mApps` is mutated on the main thread via `setApps(...)` and
read from several places (icon update, search, accessibility). The
`AllAppsRecyclerViewPool.preInflateAllAppsViewHolders` path runs on a
background executor; while it does not currently read `mApps`, future
changes could. Tightening the threading contract now avoids a latent
hazard.

## Change

- `mApps` is now declared `volatile`. Java publication semantics ensure
  any reader observing a new reference sees a fully-initialized array.
- `setApps(...)` takes a local `snapshot` reference before publishing to
  the field, then uses `snapshot` for the local Log + flags + pool
  preinflate call. This insulates the rest of the method from any
  intervening write.
- A class-level Javadoc documents the threading contract: array contents
  are immutable post-publication; non-main-thread readers must capture
  a local reference first.

## Verification

- 19/19 smoke green.
- No behavior change expected for any current code path; volatile is a
  visibility guarantee with no semantic shift for single-threaded
  callers.

## References

- Tertiary audit finding #5 (AllAppsStore consistency).
- Superplan T1.3.
