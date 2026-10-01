package dev.hegel;

import static dev.hegel.Generators.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The concurrent stateful example from the release notes and the {@link Stateful} Javadoc, as a
 * runnable demonstration. It is meant to fail — the counter loses updates under contention — so it
 * is skipped unless asked for:
 *
 * <pre>
 * mvn test -pl hegel -am -Dtest=ConcurrentCounterExample -Dhegel.examples=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * (or {@code -pl hegel-jna -am} for the JNA frontend). The report shows the round headers and the
 * worker-stamped rule and draw lines that led to the lost update.
 */
@EnabledIfSystemProperty(named = "hegel.examples", matches = "true")
class ConcurrentCounterExample {
    static final class Counter {
        private final Map<String, Integer> store = new ConcurrentHashMap<>();
        private final AtomicInteger increments = new AtomicInteger();
        private final ConcurrentPool<String> keys;

        Counter(TestCase tc) {
            keys = new ConcurrentPool<>(tc);
        }

        @Rule(group = "ops")
        void register(TestCase tc) {
            String key = tc.draw(text().minSize(1).maxSize(3));
            store.putIfAbsent(key, 0);
            keys.add(tc, key);
        }

        @Rule(group = "ops", weight = 3)
        void increment(TestCase tc) {
            String key = tc.draw(keys.reusable());
            store.put(key, store.get(key) + 1); // racy: a lost update
            increments.incrementAndGet();
        }

        @Rule(group = "audit")
        void audit(TestCase tc) {
            tc.note("store holds " + store.size() + " keys");
        }

        @Invariant
        void noLostUpdates(TestCase tc) {
            assertEquals(
                    increments.get(),
                    store.values().stream().mapToInt(Integer::intValue).sum());
        }
    }

    @HegelTest(database = Database.DISABLED)
    void counterUnderContention(TestCase tc) {
        Stateful.run(new Counter(tc), tc, Stateful.options().maxConcurrency(4));
    }
}
