package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Covers {@link Runner} branches with a fake binding (no engine). */
class RunnerTest {

    private static PrintStream capture(ByteArrayOutputStream buf) {
        return new PrintStream(buf, true, StandardCharsets.UTF_8);
    }

    private static void run(FakeLibhegel fake, Settings s, Consumer<TestCase> body) {
        Runner.run(fake, s, body, Reporter.printing(capture(new ByteArrayOutputStream())))
                .throwIfFailed();
    }

    @Test
    void happyPathMarksValidAndFreesEverything() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 3;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_VALID, Abi.STATUS_VALID, Abi.STATUS_VALID), fake.markedStatuses);
        assertEquals(3, fake.freedTestCases);
        assertTrue(fake.runFreed);
        assertTrue(fake.runResultFreed);
        assertTrue(fake.settingsFreed);
    }

    @Test
    void runStartFailurePropagatesAndFreesSettings() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStartFails = true;
        fake.lastError = "no start";
        HegelException e = assertThrows(HegelException.class, () -> run(fake, new Settings(), tc -> {}));
        assertTrue(e.getMessage().contains("no start"));
        assertTrue(fake.settingsFreed);
    }

    @Test
    void nextTestCaseFailurePropagates() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.nextTestCaseFails = true;
        fake.lastError = "explode";
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertTrue(e.getMessage().contains("explode"));
        assertTrue(fake.runFreed);
    }

    @Test
    void markCompleteErrorThrowsAndStillFreesTheCase() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.markCompleteRc = Abi.E_ALREADY_COMPLETE;
        assertThrows(HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals(1, fake.freedTestCases);
    }

    @Test
    void assumeMapsToInvalid() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false));
        assertEquals(List.of(Abi.STATUS_INVALID), fake.markedStatuses);
    }

    @Test
    void stopTestMapsToOverrun() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateIntegerRc = Abi.E_STOP_TEST;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_OVERRUN), fake.markedStatuses);
    }

    @Test
    void assertionFailureMapsToInterestingAndRecordsOrigin() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()), tc -> {
            throw new AssertionError("nope");
        });
        assertEquals(List.of(Abi.STATUS_INTERESTING), fake.markedStatuses);
        assertTrue(fake.markedOrigins.get(0) != null);
    }

    @Test
    void hegelExceptionFromBodyPropagates() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateBooleanRc = Abi.E_BACKEND;
        assertThrows(
                HegelException.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(Generators.booleans())));
        // The case was not marked complete; run_free drains it, but the handle was still freed.
        assertTrue(fake.markedStatuses.isEmpty());
        assertEquals(1, fake.freedTestCases);
    }

    @Test
    void failedRunReplaysTheBlobAndRethrowsTheOriginalException() {
        // The default (report_multiple_failures off) surfaces the body's own exception instance —
        // no "Hegel found ..." wrapper — so the stack trace and type are the user's. Covers both an
        // Error (e.g. an assertion failure) and a RuntimeException.
        AssertionError err = new AssertionError("boom-error");
        assertSame(
                err,
                assertThrows(
                        AssertionError.class,
                        () -> runFailing(tc -> {
                            throw err;
                        })));
        IllegalStateException rt = new IllegalStateException("boom-rt");
        assertSame(
                rt,
                assertThrows(
                        IllegalStateException.class,
                        () -> runFailing(tc -> {
                            throw rt;
                        })));
    }

    /** Drive a run whose result is FAILED with one blob, replaying {@code body}. */
    private static FakeLibhegel runFailing(Consumer<TestCase> body) {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        run(fake, new Settings().database(Database.disabled()), body);
        return fake;
    }

    @Test
    void failuresAreReportedFromTheCaptureWithoutAClientReplay() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-xyz");
        assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> {
                    throw new AssertionError("always");
                }));
        // The engine owns every replay: nothing is replayed from the blob client-side.
        assertTrue(fake.replayedBlobs.isEmpty());
        assertNull(fake.startedBlob);
        assertEquals(1, fake.freedTestCases);
    }

    @Test
    void failureWithoutACapturedExecutionIsAnInternalError() {
        // The engine reports a failure whose origin never failed in this process: a plumbing bug.
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureOrigins.add("AssertionError at Elsewhere.java:1");
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertTrue(e.getMessage().contains("no captured failing execution"), e.getMessage());
        assertTrue(e.getMessage().contains("Elsewhere.java:1"), e.getMessage());
    }

    @Test
    void unstampedCasesKeepOnlyTheirException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.captureSequence = new boolean[] {false};
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        AssertionError err = new AssertionError("always");
        RunReport report = Runner.run(
                fake,
                new Settings().database(Database.disabled()).printBlob(false),
                tc -> {
                    tc.draw(integers(), "x");
                    tc.note("unseen");
                    throw err;
                },
                Reporter.printing(capture(buf)));
        assertSame(err, report.failures().get(0).exception());
        assertTrue(report.failures().get(0).draws().isEmpty());
        assertTrue(report.failures().get(0).notes().isEmpty());
        assertEquals("", buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void multipleFailuresAggregateWithSuppressedOriginals() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 2; // one case per distinct bug
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureBlobs.add("blob-2");
        AtomicInteger replay = new AtomicInteger();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> Runner.run(
                                fake,
                                new Settings().database(Database.disabled()).reportMultipleFailures(true),
                                tc -> {
                                    if (replay.incrementAndGet() % 2 == 1) {
                                        throw new AssertionError("bug one");
                                    }
                                    throw new IllegalStateException("bug two");
                                },
                                Reporter.printing(capture(buf)))
                        .throwIfFailed());
        assertTrue(e.getMessage().contains("2 distinct failing examples"), e.getMessage());
        assertTrue(e.getMessage().contains("bug one"), e.getMessage());
        assertTrue(e.getMessage().contains("bug two"), e.getMessage());
        assertEquals(2, e.getSuppressed().length);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("2 distinct failures"), buf.toString());
    }

    @Test
    void aggregateMessageHandlesNullExceptionMessages() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 2;
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureBlobs.add("blob-2");
        AtomicInteger calls = new AtomicInteger();
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings(), tc -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException(); // null message
                    }
                    throw new UnsupportedOperationException(); // null message
                }));
        assertTrue(e.getMessage().contains(IllegalStateException.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains(UnsupportedOperationException.class.getName()), e.getMessage());
    }

    @Test
    void printBlobPrintsTheReproducerLine() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-b64");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertThrows(
                AssertionError.class,
                () -> Runner.run(
                                fake,
                                new Settings().database(Database.disabled()).printBlob(true),
                                tc -> {
                                    throw new AssertionError("always");
                                },
                                Reporter.printing(capture(buf)))
                        .throwIfFailed());
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("reproduceFailure = \"blob-b64\""), out);
    }

    @Test
    void healthCheckErrorSurfacesAsHealthCheckFailure() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "FailedHealthCheck: FilterTooMuch — too many rejected";
        HealthCheckFailure e = assertThrows(
                HealthCheckFailure.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false)));
        assertTrue(e.getMessage().contains("FilterTooMuch"), e.getMessage());
    }

    @Test
    void otherRunErrorsSurfaceAsHegelException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "engine exploded";
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals("engine exploded", e.getMessage());
    }

    @Test
    void nullRunErrorBecomesEmptyMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = null;
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals("", e.getMessage());
    }

    @Test
    void reproduceFailureStartsABlobRun() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add(null);
        IllegalStateException err = new IllegalStateException("reproduced");
        assertSame(
                err,
                assertThrows(
                        IllegalStateException.class,
                        () -> run(fake, new Settings().reproduceFailure("stored-blob"), tc -> {
                            throw err;
                        })));
        assertEquals("stored-blob", fake.startedBlob);
        assertTrue(fake.replayedBlobs.isEmpty());
        assertTrue(fake.runFreed);
        assertTrue(fake.settingsFreed);
    }

    @Test
    void reproduceFailureReportsAStaleBlob() {
        // The engine's blob run passed: none of its replays failed.
        FakeLibhegel fake = new FakeLibhegel();
        HegelException e =
                assertThrows(HegelException.class, () -> run(fake, new Settings().reproduceFailure("stale"), tc -> {}));
        assertEquals(Runner.STALE_BLOB, e.getMessage());
        assertTrue(e.getMessage().contains("did not reproduce"), e.getMessage());
    }

    @Test
    void reproduceFailureSurfacesAnErroredReplay() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "engine panicked";
        HegelException e =
                assertThrows(HegelException.class, () -> run(fake, new Settings().reproduceFailure("???"), tc -> {}));
        assertTrue(e.getMessage().contains("ended in an error"), e.getMessage());
        assertTrue(e.getMessage().contains("engine panicked"), e.getMessage());
    }

    @Test
    void nondeterminismStrictnessIsSentOnlyWhenSet() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()), tc -> {});
        assertNull(fake.strictness);
        run(
                fake,
                new Settings().database(Database.disabled()).nondeterminismStrictness(NondeterminismStrictness.WARN),
                tc -> {});
        assertEquals(Integer.valueOf(Abi.NONDETERMINISM_WARN), fake.strictness);
    }

    @Test
    void effectiveStrictnessIsReadBackFromTheEngine() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.resolvedStrictness = Abi.NONDETERMINISM_ERROR;
        Settings[] seen = new Settings[1];
        Reporter reporter = new Reporter() {
            @Override
            public void runStarted(Settings settings) {
                seen[0] = settings;
            }
        };
        Runner.run(fake, new Settings().database(Database.disabled()), tc -> {}, reporter);
        assertEquals(NondeterminismStrictness.ERROR, seen[0].nondeterminismStrictness);
        assertEquals(NondeterminismStrictness.DEFAULT, new Settings().nondeterminismStrictness);

        FakeLibhegel unknown = new FakeLibhegel();
        unknown.resolvedStrictness = 99;
        HegelException e = assertThrows(HegelException.class, () -> run(unknown, new Settings(), tc -> {}));
        assertTrue(e.getMessage().contains("99"), e.getMessage());
    }

    @Test
    void autoBackendLeavesTheChoiceToTheEngineProfile() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().backend(Backend.AUTO), tc -> {});
        assertNull(fake.backendCode);
        FakeLibhegel explicit = new FakeLibhegel();
        run(explicit, new Settings().backend(Backend.DEFAULT), tc -> {});
        assertEquals(Abi.BACKEND_DEFAULT, explicit.backendCode);
    }

    @Test
    void settingsBranchesAllApplied() {
        FakeLibhegel fake = new FakeLibhegel();
        Settings s = new Settings()
                .testCases(10)
                .seed(7)
                .derandomize(true)
                .reportMultipleFailures(false)
                .backend(Backend.URANDOM)
                .suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH, HealthCheck.TOO_SLOW)
                .phases(Phase.GENERATE, Phase.SHRINK)
                .verbosity(Verbosity.VERBOSE)
                .database(Database.path("/tmp/hegel-db"))
                .name("myTest");
        run(fake, s, tc -> {});
        assertEquals(List.of(Abi.STATUS_VALID), fake.markedStatuses);
        assertEquals(Phase.GENERATE.bit | Phase.SHRINK.bit, fake.phasesMask);
        assertEquals(HealthCheck.FILTER_TOO_MUCH.bit | HealthCheck.TOO_SLOW.bit, fake.suppressMask);
        assertEquals(Abi.BACKEND_URANDOM, fake.backendCode);
        assertEquals("/tmp/hegel-db", fake.databasePath);
        assertEquals("myTest", fake.databaseKey);
    }

    @Test
    void databaseDisabledAndCiDefaults() {
        // A disabled database still sends the key: the engine derives the derandomized seed from it.
        FakeLibhegel disabled = new FakeLibhegel();
        run(disabled, new Settings().database(Database.disabled()).name("d"), tc -> {});
        assertEquals("", disabled.databasePath);
        assertEquals("d", disabled.databaseKey);

        // Unset settings are not sent at all: the engine's profile (which disables the database
        // and derandomizes in CI) and the HEGEL_* variables stand. A name still derives a key.
        FakeLibhegel named = new FakeLibhegel();
        Runner.run(named, new Settings().name("t"), tc -> {}, Reporter.printing(capture(new ByteArrayOutputStream())));
        assertEquals("unset", named.databasePath);
        assertNull(named.derandomize);
        assertNull(named.testCases);
        assertEquals("t", named.databaseKey);
    }

    @Test
    void settingsConstructionFailuresAreTranslated() {
        // The engine applies the HEGEL_* variables and hegel.toml while constructing the handle: a
        // malformed one is the caller's mistake, anything else is an engine error.
        FakeLibhegel malformed = new FakeLibhegel();
        malformed.settingsNewRc = Abi.E_INVALID_ARG;
        malformed.lastError = "HEGEL_TEST_CASES must be a positive integer, got \"lots\"";
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> run(malformed, new Settings(), tc -> {}));
        assertEquals(malformed.lastError, e.getMessage());
        assertTrue(!malformed.settingsFreed);

        FakeLibhegel broken = new FakeLibhegel();
        broken.settingsNewRc = Abi.E_INTERNAL;
        HegelException h = assertThrows(HegelException.class, () -> run(broken, new Settings(), tc -> {}));
        assertTrue(h.getMessage().contains("hegel_settings_new"), h.getMessage());
    }

    @Test
    void reportersSeeTheEngineResolvedSettings() {
        Settings[] seen = new Settings[1];
        Reporter recording = new Reporter() {
            @Override
            public void runStarted(Settings settings) {
                seen[0] = settings;
            }
        };
        // Unset: the values the engine resolved (profile + HEGEL_* variables) are reported.
        FakeLibhegel fromEngine = new FakeLibhegel();
        fromEngine.resolvedTestCases = 250;
        fromEngine.resolvedPrintBlob = true;
        Runner.run(fromEngine, new Settings(), tc -> {}, recording);
        assertEquals(Long.valueOf(250), seen[0].testCases);
        assertEquals(Boolean.TRUE, seen[0].printBlob);

        // Explicit: the caller's values win, and the test-case budget reaches the engine.
        FakeLibhegel explicit = new FakeLibhegel();
        explicit.resolvedPrintBlob = true;
        Runner.run(explicit, new Settings().testCases(3).printBlob(false), tc -> {}, recording);
        assertEquals(Long.valueOf(3), explicit.testCases);
        assertEquals(Boolean.FALSE, explicit.printBlob);
        assertEquals(Long.valueOf(3), seen[0].testCases);
        assertEquals(Boolean.FALSE, seen[0].printBlob);
    }

    @Test
    void originFallsBackToClassNameWithoutUserFrame() {
        Throwable t = new RuntimeException("x");
        t.setStackTrace(new StackTraceElement[] {});
        assertEquals(RuntimeException.class.getName(), Runner.originOf(t, List.of()));
    }

    @Test
    void infrastructurePackagesAreSkippedWhenLocatingTheOrigin() {
        RuntimeException t = new RuntimeException("x");
        t.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("my.infra.Glue", "call", "Glue.java", 5),
            new StackTraceElement("com.example.Body", "prop", "Body.java", 42),
        });
        assertEquals("RuntimeException at Glue.java:5", Runner.originOf(t, List.of()));
        assertEquals("RuntimeException at Body.java:42", Runner.originOf(t, List.of("my.infra.")));

        // The setting reaches the origin handed to the engine.
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()).infrastructurePackages("my.infra."), tc -> {
            throw t;
        });
        assertEquals(List.of("RuntimeException at Body.java:42"), fake.markedOrigins);
    }

    @Test
    void caveatIsPrintedAndABloblessFailurePrintsNoReproducer() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add(null);
        fake.failureCaveats.add("nondeterministic failure, unconfirmed: failed 0 of 20 replays");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RunReport report = Runner.run(
                fake,
                new Settings().database(Database.disabled()).printBlob(true),
                tc -> {
                    tc.draw(integers().min(0), "x");
                    throw new AssertionError("sometimes");
                },
                Reporter.printing(capture(buf)));
        assertTrue(report.failures().get(0).nondeterministic());
        String out = buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals("x = 0;\nnote: nondeterministic failure, unconfirmed: failed 0 of 20 replays\n", out);
    }

    @Test
    void quietRunsPrintNoCaveat() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("nd-blob");
        fake.failureCaveats.add("nondeterministic failure, confirmed: failed 9 of 20 replays");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Runner.run(
                fake,
                new Settings().database(Database.disabled()).printBlob(true).verbosity(Verbosity.QUIET),
                tc -> {
                    throw new AssertionError("sometimes");
                },
                Reporter.printing(capture(buf)));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(!out.contains("note:"), out);
        assertTrue(out.contains("reproduceFailure = \"nd-blob\""), out);
    }
}
