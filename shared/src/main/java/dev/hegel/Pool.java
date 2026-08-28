package dev.hegel;

import java.util.HashMap;
import java.util.Map;

/**
 * A pool of previously generated values the engine can draw from and shrink over. Mostly used in
 * stateful tests ({@link Stateful}), where a rule needs to act on some value an earlier rule
 * produced.
 *
 * <p>Create one per test case, populate it with {@link #add}, and draw from it through the
 * generators it hands out rather than reading it directly: {@link #reusable()} yields a value
 * without removing it, {@link #consuming()} removes the value it yields. Both are drawn through
 * {@link TestCase#draw}, so the chosen value is recorded in the failing-test replay and the choice
 * shrinks like any other draw. Drawing from an empty pool rejects the current test case (as if by
 * {@code assume(false)}).
 *
 * @param <T> the type of pooled values
 */
public final class Pool<T> {
    private final TestCase tc;
    private final long poolId;
    private final Map<Long, T> values = new HashMap<>();

    /**
     * Create a pool tracked by the current test case.
     *
     * @param tc the current test case
     */
    public Pool(TestCase tc) {
        this.tc = tc;
        this.poolId = tc.newPool();
    }

    /**
     * @return whether no values are in the pool
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * @return the number of values currently in the pool
     */
    public int size() {
        return values.size();
    }

    /**
     * Add a value to the pool.
     *
     * @param value the value to add
     */
    public void add(T value) {
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
            tc.assume(!values.isEmpty());
            long variableId = tc.poolGenerate(poolId, consume);
            return consume ? values.remove(variableId) : values.get(variableId);
        }
    }
}
