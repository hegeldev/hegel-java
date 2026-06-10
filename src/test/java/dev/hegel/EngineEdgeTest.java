package dev.hegel;

import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-engine tests for edge behaviours: overrun handling and targeting. */
class EngineEdgeTest {
    // A non-basic element (filter) forces the collection API; a large minimum size
    // exhausts the per-case choice budget, so the engine abandons cases (STOP_TEST
    // from collection_more) and the run still completes. Deliberately generating a lot
    // is the point here, so suppress the health checks that would otherwise flag it —
    // their thresholds are engine-version-specific and not what this test is about.
    @HegelTest(
            testCases = 10,
            database = Database.DISABLED,
            suppressHealthCheck = {
                HealthCheck.LARGE_INITIAL_TEST_CASE,
                HealthCheck.TEST_CASES_TOO_LARGE,
                HealthCheck.TOO_SLOW
            })
    void largeNonBasicCollectionOverrunsGracefully(TestCase tc) {
        // Many non-basic collections so the budget runs out across both element
        // draws and collection_more decisions.
        for (int i = 0; i < 5000; i++) {
            tc.draw(lists(integers().min(0).max(100).filter(x -> true))
                    .minSize(0)
                    .maxSize(100));
        }
    }

    @HegelTest(database = Database.DISABLED)
    void targetGuidesTheSearch(TestCase tc) {
        int x = tc.draw(integers().min(0).max(1000));
        tc.target(x, "magnitude");
        tc.target(x); // unlabelled overload
        assertTrue(x >= 0);
    }
}
