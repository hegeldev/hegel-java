package dev.hegel;

/**
 * How many test cases a run executed, by outcome.
 *
 * <p>The counts cover every case the test body ran against — generation, targeting, and shrinking
 * alike, plus the final replays of any counterexamples — so on a failing run they exceed the
 * {@link Settings#testCases(long) test-case budget}, which bounds valid generated cases only.
 */
public final class RunStatistics {
    private final long valid;
    private final long invalid;
    private final long overrun;
    private final long interesting;

    RunStatistics(long valid, long invalid, long overrun, long interesting) {
        this.valid = valid;
        this.invalid = invalid;
        this.overrun = overrun;
        this.interesting = interesting;
    }

    /**
     * Every case executed, whatever its outcome.
     *
     * @return the total number of cases
     */
    public long total() {
        return valid + invalid + overrun + interesting;
    }

    /**
     * Cases for which the property held.
     *
     * @return the number of {@link CaseOutcome#VALID} cases
     */
    public long valid() {
        return valid;
    }

    /**
     * Cases rejected by {@link TestCase#assume(boolean)}.
     *
     * @return the number of {@link CaseOutcome#INVALID} cases
     */
    public long invalid() {
        return invalid;
    }

    /**
     * Cases that drew more data than the engine allowed.
     *
     * @return the number of {@link CaseOutcome#OVERRUN} cases
     */
    public long overrun() {
        return overrun;
    }

    /**
     * Cases for which the property failed.
     *
     * @return the number of {@link CaseOutcome#INTERESTING} cases
     */
    public long interesting() {
        return interesting;
    }

    @Override
    public String toString() {
        return "RunStatistics{total=" + total() + ", valid=" + valid + ", invalid=" + invalid + ", overrun=" + overrun
                + ", interesting=" + interesting + "}";
    }

    /** Mutable accumulator the runner counts into; one per run. */
    static final class Counter {
        private final long[] counts = new long[CaseOutcome.values().length];

        void record(CaseOutcome outcome) {
            counts[outcome.ordinal()]++;
        }

        RunStatistics snapshot() {
            return new RunStatistics(
                    counts[CaseOutcome.VALID.ordinal()],
                    counts[CaseOutcome.INVALID.ordinal()],
                    counts[CaseOutcome.OVERRUN.ordinal()],
                    counts[CaseOutcome.INTERESTING.ordinal()]);
        }
    }
}
