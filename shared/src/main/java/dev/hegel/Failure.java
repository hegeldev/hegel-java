package dev.hegel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One distinct counterexample of a failed run, as observed on the freshest failing execution the
 * engine stamped for capture.
 *
 * <p>The engine runs every failure it is about to report one final time, stamped for capture ({@link
 * TestCase#isFinal()}); the runner keeps that execution's exception and every top-level draw and
 * note, and that capture is what this class carries. A {@linkplain #nondeterministic()
 * nondeterministic} failure — one whose test does not fail every time the same choices are
 * replayed — additionally carries the engine's {@linkplain #caveat() caveat} quoting how reliably it
 * reproduced, and has a reproduce blob only if the engine confirmed it.
 */
public final class Failure {
    private final String origin;
    private final String reproduceBlob;
    private final String caveat;
    private final Throwable exception;
    private final Map<String, Object> draws;
    private final List<String> notes;

    Failure(
            String origin,
            String reproduceBlob,
            String caveat,
            Throwable exception,
            Map<String, Object> draws,
            List<String> notes) {
        this.origin = origin;
        this.reproduceBlob = reproduceBlob;
        this.caveat = caveat;
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
     * The base64 blob that replays this counterexample, via {@link
     * Settings#reproduceFailure(String)}. Only guaranteed to reproduce under the Hegel version that
     * produced it.
     *
     * @return the reproduce blob, or empty when the engine produced none: for an unconfirmed
     *     {@linkplain #nondeterministic() nondeterministic} failure, and for a failure reproduced
     *     from a {@link Settings#reproduceFailure(String)} blob (the caller already holds it)
     */
    public Optional<String> reproduceBlob() {
        return Optional.ofNullable(reproduceBlob);
    }

    /**
     * The engine's confirmation caveat for a nondeterministic failure: its standing under the run's
     * nondeterministic handling, quoting the run's own replay evidence (for example {@code
     * nondeterministic failure, confirmed: failed 7 of 20 replays at confirmation and 1 of 2 at
     * report time}). Print it alongside the failure so the reader sees how reliably it reproduced.
     *
     * @return the caveat, or empty for a deterministic failure
     */
    public Optional<String> caveat() {
        return Optional.ofNullable(caveat);
    }

    /**
     * Whether the test's outcome depended on something other than its generated data: the same
     * choices did not fail every time the engine replayed them (hidden global state, time, an
     * external RNG, thread scheduling). The engine confirms such a failure by repeated replay
     * before shrinking it; {@link #caveat()} says how that went.
     *
     * @return {@code true} if the engine handled this failure as nondeterministic
     */
    public boolean nondeterministic() {
        return caveat != null;
    }

    /**
     * The exception the test body threw on the captured execution.
     *
     * @return the exception
     */
    public Throwable exception() {
        return exception;
    }

    /**
     * Every top-level draw of the captured execution, in draw order, keyed by the label passed to
     * {@link TestCase#draw(Generator, String)} — numbered from its second use in the case ({@code
     * x}, {@code x_2}, ...) so repeated draws are all kept — or {@code draw_N} for the N-th
     * unlabelled draw. Empty when the engine reported the failure without a stamped failing
     * execution to capture — an unconfirmed nondeterministic failure that never failed again after
     * its discovery.
     *
     * @return an unmodifiable, insertion-ordered map of label to generated value
     */
    public Map<String, Object> draws() {
        return draws;
    }

    /**
     * Every {@link TestCase#note(String) note} recorded during the captured execution, in order.
     *
     * @return an unmodifiable list of notes
     */
    public List<String> notes() {
        return notes;
    }
}
