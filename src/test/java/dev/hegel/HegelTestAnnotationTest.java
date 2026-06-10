package dev.hegel;

import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

class HegelTestAnnotationTest {

    @HegelTest
    void additionCommutes(TestCase tc) {
        int x = tc.draw(integers());
        int y = tc.draw(integers());
        assertEquals(x + y, y + x);
    }

    @HegelTest(seed = 99)
    void reverseTwiceIsIdentity(TestCase tc) {
        List<Integer> xs = tc.draw(lists(integers().min(0).max(100)));
        List<Integer> twice = reverse(reverse(xs));
        assertEquals(xs, twice);
    }

    private static List<Integer> reverse(List<Integer> xs) {
        List<Integer> out = new ArrayList<>(xs);
        java.util.Collections.reverse(out);
        return out;
    }

    // A nested class with a deliberately failing property, executed via the JUnit test kit so the
    // failure is observed rather than failing this suite.
    static class FailingProperties {
        @HegelTest(seed = 1)
        void alwaysSmall(TestCase tc) {
            int x = tc.draw(integers().min(0).max(1000));
            assertTrue(x < 5, "too big");
        }
    }

    @Test
    void failingHegelTestIsReportedAsFailure() {
        Events tests = EngineTestKit.engine("junit-jupiter")
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(FailingProperties.class))
                .execute()
                .testEvents();
        tests.assertStatistics(stats -> stats.started(1).failed(1));
    }
}
