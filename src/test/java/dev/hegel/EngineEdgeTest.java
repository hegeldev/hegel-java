package dev.hegel;

import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Real-engine tests for edge behaviours: overrun handling and targeting. */
class EngineEdgeTest {
  @Test
  void largeNonBasicCollectionOverrunsGracefully() {
    // A non-basic element (filter) forces the collection API; a large minimum size
    // exhausts the per-case choice budget, so the engine abandons cases (STOP_TEST
    // from collection_more) and the run still completes. Deliberately generating a lot
    // is the point here, so suppress the health checks that would otherwise flag it —
    // their thresholds are engine-version-specific and not what this test is about.
    Hegel.with()
        .testCases(10)
        .noDatabase()
        .suppressHealthCheck(
            HealthCheck.LARGE_INITIAL_TEST_CASE,
            HealthCheck.TEST_CASES_TOO_LARGE,
            HealthCheck.TOO_SLOW)
        .check(
            tc -> {
              // Many non-basic collections so the budget runs out across both element
              // draws and collection_more decisions.
              for (int i = 0; i < 5000; i++) {
                tc.draw(lists(integers(0, 100).filter(x -> true), 0, 100));
              }
            });
  }

  @Test
  void targetGuidesTheSearch() {
    Hegel.with()
        .testCases(50)
        .noDatabase()
        .check(
            tc -> {
              int x = tc.draw(integers(0, 1000));
              tc.target(x, "magnitude");
              tc.target(x); // unlabelled overload
              assertTrue(x >= 0);
            });
  }
}
