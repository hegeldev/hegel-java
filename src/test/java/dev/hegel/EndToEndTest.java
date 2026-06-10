package dev.hegel;

import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EndToEndTest {
    @HegelTest
    void passingPropertyRunsAllCases(TestCase tc) {
        int x = tc.draw(integers().min(0).max(100));
        int y = tc.draw(integers().min(0).max(100));
        assertEquals(x + y, y + x);
    }

    @Test
    void imperativeEntryWithDefaultSettings() {
        // The zero-config Hegel.test(body) overload runs a passing property under default settings.
        Hegel.test(tc -> {
            int x = tc.draw(integers().min(0).max(100));
            assertTrue(x >= 0 && x <= 100);
        });
    }

    @HegelTest
    void booleansAndListsDrawNativeValues(TestCase tc) {
        boolean b = tc.draw(booleans());
        List<Integer> xs = tc.draw(lists(integers().min(0).max(10)).minSize(0).maxSize(5));
        assertTrue(xs.size() <= 5);
        for (int x : xs) {
            assertTrue(x >= 0 && x <= 10);
        }
        assertTrue(b || !b);
    }

    @Test
    void failingPropertyThrowsAssertionErrorWithCounterexample() {
        // By default Hegel rethrows the property's own assertion failure directly (no wrapper), so
        // the surfaced message is the user's, carrying the shrunk value (the engine shrinks to 11).
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> Hegel.test(
                        tc -> {
                            int x = tc.draw(integers().min(0).max(1000));
                            // Property is false for x > 10; engine should shrink toward 11.
                            assertTrue(x <= 10, "x was too big: " + x);
                        },
                        new Settings().seed(123)));
        assertTrue(err.getMessage().contains("x was too big: 11"), err.getMessage());
        assertTrue(!err.getMessage().contains("failing example"), err.getMessage());
    }

    @Test
    void reportMultipleFailuresProducesAggregateReport() {
        // Opt-in multi-failure mode wraps the result in an aggregate "Hegel found ..." report
        // (rather than the default direct rethrow), exercising the engine's failure-enumeration API.
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> Hegel.test(
                        tc -> {
                            int x = tc.draw(integers().min(0).max(1000));
                            assertTrue(x <= 10, "x too big: " + x);
                        },
                        new Settings().reportMultipleFailures(true).seed(123).database(Database.disabled())));
        assertTrue(err.getMessage().contains("failing example"), err.getMessage());
    }

    @HegelTest
    void assumeRejectsWithoutFailing(TestCase tc) {
        int x = tc.draw(integers().min(0).max(100));
        tc.assume(x % 2 == 0);
        assertEquals(0, x % 2);
    }
}
