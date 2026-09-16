package dev.hegel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One distinct counterexample of a failed run, as observed on its final replay.
 *
 * <p>After the engine shrinks a failure it hands back a reproduce blob; the runner replays that blob
 * once more with reporting on, capturing the exception the body threw and every top-level draw and
 * note along the way. That capture is what this class carries. A replay that no longer fails is
 * {@linkplain #flaky() flaky}: the test's outcome depends on something other than its generated
 * data.
 */
public final class Failure {
    private final String origin;
    private final String reproduceBlob;
    private final Throwable exception;
    private final Map<String, Object> draws;
    private final List<String> notes;

    Failure(String origin, String reproduceBlob, Throwable exception, Map<String, Object> draws, List<String> notes) {
        this.origin = origin;
        this.reproduceBlob = reproduceBlob;
        this.exception = exception;
        this.draws = Collections.unmodifiableMap(new LinkedHashMap<>(draws));
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    /**
     * The origin the engine grouped this bug under — the exception's type and the user frame it was
     * thrown from, e.g. {@code AssertionFailedError at SortTest.java:23}. Two failures with different
     * origins are reported as distinct bugs.
     *
     * @return the origin string
     */
    public String origin() {
        return origin;
    }

    /**
     * The base64 blob that replays this counterexample exactly, via {@link
     * Settings#reproduceFailure(String)}. Only guaranteed to reproduce under the Hegel version that
     * produced it.
     *
     * @return the reproduce blob
     */
    public String reproduceBlob() {
        return reproduceBlob;
    }

    /**
     * The exception the test body threw on the final replay.
     *
     * @return the exception, or empty if the replay unexpectedly passed (see {@link #flaky()})
     */
    public Optional<Throwable> exception() {
        return Optional.ofNullable(exception);
    }

    /**
     * Whether the final replay failed to reproduce the failure. The engine only reports a
     * counterexample after re-running it, so a passing replay means the body's outcome depends on
     * state outside its generated data (globals, time, an external RNG).
     *
     * @return {@code true} if the replay passed
     */
    public boolean flaky() {
        return exception == null;
    }

    /**
     * Every top-level draw of the final replay, in draw order, keyed by the label passed to {@link
     * TestCase#draw(Generator, String)} — numbered from its second use in the case ({@code x},
     * {@code x_2}, ...) so repeated draws are all kept — or {@code draw_N} for the N-th unlabelled
     * draw.
     *
     * @return an unmodifiable, insertion-ordered map of label to generated value
     */
    public Map<String, Object> draws() {
        return draws;
    }

    /**
     * Every {@link TestCase#note(String) note} recorded during the final replay, in order.
     *
     * @return an unmodifiable list of notes
     */
    public List<String> notes() {
        return notes;
    }
}
