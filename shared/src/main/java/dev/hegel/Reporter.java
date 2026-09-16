package dev.hegel;

import java.io.PrintStream;

/**
 * Receives everything a run has to say: engine output, per-case progress, the draws and notes of
 * each minimal counterexample, and the final verdict.
 *
 * <p>Hegel itself never writes to {@code System.out} or {@code System.err}; all output goes through
 * the run's reporter. The default, {@link #printing(PrintStream) Reporter.printing(System.err)},
 * prints the classic report (each top-level draw as {@code x = 42;}, notes, and reproduce blobs).
 * A frontend built on top of Hegel — another JVM language, a custom test runner — supplies its own
 * implementation to route output through its logging, or {@link #silent()} to consume the {@link
 * RunReport} returned by {@link Hegel#run} instead.
 *
 * <p>Every method has a no-op default, so an implementation overrides only what it needs. Callbacks
 * are invoked on the thread that drives the run, in this order: {@link #runStarted}, then for each
 * generated case {@link #caseStarted} / {@link #caseFinished} (with {@link #engineOutput} lines
 * interleaved as the engine emits them, and the case's {@link #draw}s and {@link #note}s in between
 * when the run is {@linkplain Verbosity#VERBOSE verbose}); on a failed run {@link #failuresFound}, then for each
 * distinct counterexample a final replay ({@link #caseStarted} with {@code finalReplay = true}, its
 * {@link #draw}s and {@link #note}s, {@link #caseFinished}) followed by {@link #failure}; and
 * finally {@link #runFinished}. A reporter is per run: two concurrent runs never share one unless
 * the caller passes the same instance to both.
 */
public interface Reporter {
    /**
     * The run is about to start under {@code settings}.
     *
     * @param settings the run's configuration
     */
    default void runStarted(Settings settings) {}

    /**
     * One line of engine output: progress at higher {@link Verbosity} levels, health-check notices,
     * shrinker traces. Lines arrive without a trailing newline.
     *
     * @param line the line
     */
    default void engineOutput(String line) {}

    /**
     * The test body is about to run against a case.
     *
     * @param finalReplay {@code true} for the replay of a minimal counterexample (or of a {@link
     *     Settings#reproduceFailure} blob), {@code false} during generation and shrinking
     */
    default void caseStarted(boolean finalReplay) {}

    /**
     * A top-level {@link TestCase#draw(Generator, String) draw} completed. Called on final replays,
     * and on every case when the run's {@link Verbosity} is {@code VERBOSE} or higher; never for
     * draws nested inside another generator.
     *
     * @param label the label passed to {@code draw} (numbered from its second use in a case: {@code
     *     x}, {@code x_2}, ...), or {@code draw_N} for the N-th unlabelled draw
     * @param value the generated value
     * @param finalReplay as in {@link #caseStarted}
     */
    default void draw(String label, Object value, boolean finalReplay) {}

    /**
     * The test body recorded a {@link TestCase#note(String) note}. Called under the same conditions
     * as {@link #draw}. A note made inside a composite generator arrives after the enclosing draw.
     *
     * @param message the note
     * @param finalReplay as in {@link #caseStarted}
     */
    default void note(String message, boolean finalReplay) {}

    /**
     * The test body finished against a case and the outcome was reported to the engine.
     *
     * @param outcome how the case concluded
     * @param finalReplay as in {@link #caseStarted}
     */
    default void caseFinished(CaseOutcome outcome, boolean finalReplay) {}

    /**
     * The run's verdict is FAILED. Called once, before the counterexamples are replayed.
     *
     * @param count the number of distinct failures (by origin) that will be replayed
     */
    default void failuresFound(int count) {}

    /**
     * A counterexample's final replay finished.
     *
     * @param failure the failure, carrying the replay's exception, draws, notes, and blob
     */
    default void failure(Failure failure) {}

    /**
     * The run is over. Called exactly once per run, last, whether it passed, failed, or errored.
     *
     * @param report the report {@link Hegel#run} is about to return
     */
    default void runFinished(RunReport report) {}

    /**
     * The classic printed report: engine output, each reported draw as {@code label = value;},
     * notes, a header when several distinct failures are reported, and a copy-pasteable reproducer
     * when {@link Settings#printBlob(boolean)} is on. Honours {@link Settings#verbosity}: {@link
     * Verbosity#QUIET} prints no draws or notes at all, {@link Verbosity#VERBOSE} prints them for
     * every case.
     *
     * @param out where to print
     * @return a printing reporter
     */
    static Reporter printing(PrintStream out) {
        return new PrintingReporter(out);
    }

    /**
     * A reporter that ignores everything. Pair it with {@link Hegel#run} to consume the {@link
     * RunReport} programmatically.
     *
     * @return a no-op reporter
     */
    static Reporter silent() {
        return new Reporter() {};
    }
}
