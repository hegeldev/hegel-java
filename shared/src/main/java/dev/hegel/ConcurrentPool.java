package dev.hegel;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Pool} for concurrent state machines: previously generated values that rules running on
 * different worker threads can add, reuse and consume, with the engine choosing and shrinking over
 * which value a rule gets.
 *
 * <p>Create one per test case on the test case the machine is run under, and have rules go through
 * the {@link TestCase} they were handed: {@link #add(TestCase, Object)} records a value through the
 * calling rule's handle, and the generators from {@link #reusable()} and {@link #consuming()} draw
 * through whichever handle draws them. Every operation holds the pool's lock across the engine
 * call and the bookkeeping, so the engine's choice and the Java-side values never disagree even
 * when workers race. Drawing from an empty pool rejects the current rule (as if by {@code
 * assume(false)}), so the engine retries the slot with another rule.
 *
 * <p>A {@link #reusable()} draw hands out the stored reference itself; do not mutate a pooled value
 * from a rule, since another worker may be reading it at the same time.
 *
 * @param <T> the type of pooled values
 * @see Stateful
 */
public final class ConcurrentPool<T> {
    private final long poolId;
    private final Map<Long, T> values = new HashMap<>();

    /**
     * Create a pool tracked by the current test case.
     *
     * @param tc the test case the machine is run under
     */
    public ConcurrentPool(TestCase tc) {
        this.poolId = tc.newPool();
    }

    /**
     * @return whether no values are in the pool
     */
    public synchronized boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @return the number of values currently in the pool
     */
    public synchronized int size() {
        return values.size();
    }

    /**
     * Add a value to the pool.
     *
     * @param tc the test case of the rule adding the value: its own worker's handle
     * @param value the value to add
     */
    public synchronized void add(TestCase tc, T value) {
        values.put(tc.poolAdd(poolId), value);
    }

    /**
     * A generator over the values in the pool that yields a value without removing it.
     *
     * @return the reusing generator
     */
    public Generator<T> reusable() {
        return new PoolGenerator(false);
    }

    /**
     * A generator that consumes values from the pool: it removes the value it yields, so once
     * consumed a value is never drawn again.
     *
     * @return the consuming generator
     */
    public Generator<T> consuming() {
        return new PoolGenerator(true);
    }

    private final class PoolGenerator implements Generator<T> {
        private final boolean consume;

        PoolGenerator(boolean consume) {
            this.consume = consume;
        }

        @Override
        public T doDraw(TestCase tc) {
            synchronized (ConcurrentPool.this) {
                tc.assume(!values.isEmpty());
                long variableId = tc.poolGenerate(poolId, consume);
                return consume ? values.remove(variableId) : values.get(variableId);
            }
        }
    }
}
