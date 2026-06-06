# 048 — Search progressive-delivery contract fix

## Summary

`AppsSearchContainerLayout` was overriding only the 3-parameter
`onSearchResult(query, items)` form of `SearchCallback`. When
`UniversalSearchAlgorithm.deliverResults()` called the 4-parameter form
`onSearchResult(query, items, searchResultCode)`, the `SearchCallback`
default routed straight back to the 3-param method and discarded the
result code. As a consequence, `setSearchResults(results, searchResultCode)`
was never reached, and the animation-suppression guard introduced by
change 042 for `SearchCallback.INTERMEDIATE` was never engaged. Intermediate
result batches (from progressive multi-provider delivery) animated in the
same way as final batches.

## Fix

Override the 4-parameter form in `AppsSearchContainerLayout` and route to
the 2-arg `setSearchResults(results, searchResultCode)`. Keep the 3-param
form as a back-compat fallback that delegates to the 4-param form with
`SearchCallback.UNKNOWN`.

## Files

- `src/com/android/launcher3/allapps/search/AppsSearchContainerLayout.java`
  - Add `onSearchResult(query, items, searchResultCode)` override.
  - Make `onSearchResult(query, items)` call the 4-param overload with
    `SearchCallback.UNKNOWN` so all delivery paths converge.

## Verification

- e2e smoke suite (19 tests) green after the fix.
- New regression test `regression/test_search_progressive.py::test_multiprovider_query_renders` exercises the multi-provider path that triggers INTERMEDIATE batches.
- Manual: type a multi-provider query (e.g. `5+5` which triggers Calculator
  alongside any matching apps) and confirm no animation overlap between
  intermediate apps row and final calculator row.

## References

- Tertiary audit finding #2 in superplan 000-architectural-refactor-superplan.md
- Change 042 — search enhancements (introduced INTERMEDIATE/FINAL contract).
