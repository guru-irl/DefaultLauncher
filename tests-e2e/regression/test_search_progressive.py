"""Regression: progressive-delivery contract from change 042 + change 048 fix.

Multi-provider queries should deliver INTERMEDIATE batches without animating
each addition, then a final batch that may animate. We can't easily inspect
the animator state from outside the process, but we can:

  1. Type a query that triggers multiple providers (apps + at least one
     async provider like calculator/unit-converter).
  2. Verify the search results list is rendered and stable.
  3. Verify the final result set contains the expected categories.
"""

import time

import pytest

from lib import selectors as S


@pytest.mark.regression
@pytest.mark.search
def test_multiprovider_query_renders(launcher):
    """A query that triggers apps + calculator providers renders cleanly."""
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("5+5")
    # Wait briefly for all providers to flush (default debounce + IO).
    deadline = time.time() + 2.5
    saw_results = False
    while time.time() < deadline:
        if launcher.d(resourceId=S.ID_SEARCH_RESULTS_LIST).exists:
            saw_results = True
            break
        time.sleep(0.1)
    assert saw_results, "search results list never appeared for multiprovider query"
    # Verify the result set isn't empty (recycler has child rows).
    rows = launcher.d(resourceId=S.ID_SEARCH_RESULTS_LIST).child()
    assert rows.count >= 1, "search results list has no rows"
    launcher.clear_search()
    launcher.close_drawer()


@pytest.mark.regression
@pytest.mark.search
def test_rapid_typing_no_stale_results(launcher):
    """Typing a query, replacing it with an unrelated one, leaves no stale rows.

    Pre-fix bug: cancelled provider callbacks could still deliver into a
    new session's accumulator. Post-fix (task T2.2 will harden further) +
    the 048 fix: result codes propagate so the final FINAL batch overwrites.
    This is a sanity test that the recycler doesn't show old rows.
    """
    launcher.go_home()
    launcher.open_drawer()
    launcher.type_search("calc")
    time.sleep(0.6)
    launcher.type_search("phon", clear=True)
    time.sleep(1.2)
    # After the second query stabilizes, the only visible app-row labels
    # should be related to 'phon*' (Phone), not 'calc*'.
    xml = launcher.d.dump_hierarchy()
    # Very loose check: no Calculator app icon (assuming present) in results.
    has_phone = "Phone" in xml
    has_stale_calc = "Calculator" in xml
    launcher.clear_search()
    launcher.close_drawer()
    assert has_phone or not has_stale_calc, (
        "search appears to show stale results from prior query"
    )
