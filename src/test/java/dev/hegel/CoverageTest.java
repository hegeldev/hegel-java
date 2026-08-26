package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Targeted tests closing remaining coverage branches. */
class CoverageTest {
    // --- Settings.isCi ---
    @Test
    void isCiDetectsEachProvider() {
        assertFalse(Settings.isCi(Map.of()));
        assertTrue(Settings.isCi(Map.of("CI", "true")));
        assertTrue(Settings.isCi(Map.of("GITHUB_ACTIONS", "true")));
        assertTrue(Settings.isCi(Map.of("GITLAB_CI", "true")));
        assertTrue(Settings.isCi(Map.of("BUILDKITE", "true")));
        assertTrue(Settings.isCi(Map.of("CIRCLECI", "true")));
        assertFalse(Settings.isCi(Map.of("CI", "")));
    }

    // --- Abi helpers ---
    @Test
    void fnv1aMatchesKnownVector() {
        // FNV-1a 64-bit of the empty string is the offset basis.
        assertEquals(0xcbf29ce484222325L, Abi.fnv1a(""));
        assertTrue(Abi.LABEL_COMPOSITE == Abi.fnv1a("dev.hegel.composite"));
    }

    // --- output-callback bridge ---
    @Test
    void emitLineDecodesAndSwallowsExceptions() {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
            MemorySegment line = arena.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, line, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, bytes.length);
            AtomicReference<String> got = new AtomicReference<>();
            RealLibhegel.emitLine(got::set, MemorySegment.NULL, line, bytes.length);
            assertEquals("hello", got.get());
            // A throwing consumer must be swallowed: an exception escaping an upcall kills the VM.
            Consumer<String> throwing = s -> {
                throw new IllegalStateException("never escapes");
            };
            RealLibhegel.emitLine(throwing, MemorySegment.NULL, line, bytes.length);
        }
    }

    // --- StringGeneratorHandle cleanup ---
    @Test
    void handleFreeReleasesThroughItsBinding() {
        FakeLibhegel fake = new FakeLibhegel();
        new StringGeneratorHandle.Free(fake, FakeLibhegel.STRING_GEN).run();
        assertEquals(1, fake.freedStringGenerators);
    }

    // --- checked exceptions from rules fail the property, wrapped with the cause preserved ---
    @Test
    void checkedExceptionsFromRulesFailTheProperty() {
        class ThrowsChecked {
            @Rule
            void io(TestCase t) throws java.io.IOException {
                throw new java.io.IOException("io failure");
            }
        }
        FakeLibhegel fake = new FakeLibhegel();
        fake.ruleSequence = new long[] {0, Abi.STATE_MACHINE_DONE};
        TestCase tc = fakeTestCase(fake);
        RuntimeException e = assertThrows(RuntimeException.class, () -> Stateful.run(new ThrowsChecked(), tc));
        assertTrue(e.getCause() instanceof java.io.IOException, String.valueOf(e.getCause()));
    }

    // --- HegelTestExtension static helpers ---
    static final class Holder {
        @HegelTest(seed = 5)
        void seeded(TestCase tc) {}

        @HegelTest
        void unseeded(TestCase tc) {}

        void plain(TestCase tc) {}

        @HegelTest(
                derandomize = OptBoolean.TRUE,
                phases = {Phase.GENERATE},
                suppressHealthCheck = {HealthCheck.TOO_SLOW},
                mode = Mode.SINGLE_TEST_CASE,
                backend = Backend.URANDOM,
                reportMultipleFailures = true,
                printBlob = true,
                reproduceFailure = "blob-xyz",
                name = "custom")
        void configured(TestCase tc) {}

        @HegelTest(
                derandomize = OptBoolean.FALSE,
                phases = {},
                database = Database.DISABLED)
        void derandomFalseEmptyPhases(TestCase tc) {}

        @HegelTest(database = "/tmp/hdb")
        void customDb(TestCase tc) {}
    }

    @Test
    void hegelTestHelpers() throws Exception {
        Method seeded = Holder.class.getDeclaredMethod("seeded", TestCase.class);
        Method unseeded = Holder.class.getDeclaredMethod("unseeded", TestCase.class);
        Method plain = Holder.class.getDeclaredMethod("plain", TestCase.class);

        assertTrue(HegelTestExtension.isHegelTest(seeded));
        assertFalse(HegelTestExtension.isHegelTest(plain));
        assertFalse(HegelTestExtension.isHegelTest(null));

        assertTrue(HegelTestExtension.isTestCaseParam(TestCase.class));
        assertFalse(HegelTestExtension.isTestCaseParam(String.class));

        Settings withSeed = HegelTestExtension.settingsFrom(seeded.getAnnotation(HegelTest.class), "s");
        assertEquals(5L, withSeed.seed);
        assertTrue(withSeed.hasSeed);

        // Defaults: no seed, no derandomize override, engine-default phases, default database, no
        // blob replay, method name as the property name.
        Settings noSeed = HegelTestExtension.settingsFrom(unseeded.getAnnotation(HegelTest.class), "u");
        assertFalse(noSeed.hasSeed);
        assertNull(noSeed.derandomize);
        assertNull(noSeed.phasesMask);
        assertEquals(Database.Kind.UNSET, noSeed.database.kind);
        assertEquals(0, noSeed.suppressMask);
        assertEquals(Mode.TEST_RUN, noSeed.mode);
        assertEquals(Backend.AUTO, noSeed.backend);
        assertFalse(noSeed.reportMultipleFailures);
        assertFalse(noSeed.printBlob);
        assertNull(noSeed.reproduceFailure);
        assertEquals("u", noSeed.name);

        // Fully-configured: derandomize forced on, a single explicit phase, a suppressed check,
        // single-case mode, explicit backend, multi-failure and blob options, and a name override.
        Method configured = Holder.class.getDeclaredMethod("configured", TestCase.class);
        Settings c = HegelTestExtension.settingsFrom(configured.getAnnotation(HegelTest.class), "ignored");
        assertEquals(Boolean.TRUE, c.derandomize);
        assertEquals(Integer.valueOf(Phase.GENERATE.bit), c.phasesMask);
        assertEquals(HealthCheck.TOO_SLOW.bit, c.suppressMask);
        assertEquals(Mode.SINGLE_TEST_CASE, c.mode);
        assertEquals(Backend.URANDOM, c.backend);
        assertTrue(c.reportMultipleFailures);
        assertTrue(c.printBlob);
        assertEquals("blob-xyz", c.reproduceFailure);
        assertEquals("custom", c.name);

        // derandomize forced off, an explicitly empty phase set (runs nothing — distinct from the
        // all-phases default), and the database disabled.
        Method emptyPhases = Holder.class.getDeclaredMethod("derandomFalseEmptyPhases", TestCase.class);
        Settings e = HegelTestExtension.settingsFrom(emptyPhases.getAnnotation(HegelTest.class), "e");
        assertEquals(Boolean.FALSE, e.derandomize);
        assertEquals(Integer.valueOf(0), e.phasesMask);
        assertEquals(Database.Kind.DISABLED, e.database.kind);

        // A custom (compile-time) database path.
        Method customDb = Holder.class.getDeclaredMethod("customDb", TestCase.class);
        Settings d = HegelTestExtension.settingsFrom(customDb.getAnnotation(HegelTest.class), "d");
        assertEquals(Database.Kind.PATH, d.database.kind);
        assertEquals("/tmp/hdb", d.database.path);
    }

    // --- generators: stale-handle rebuild and IPv6 formatting ---
    @Test
    void cachedStringHandlesAreRebuiltForANewBinding() {
        var emailGen = Generators.emails();
        FakeLibhegel first = new FakeLibhegel();
        drawWith(first, emailGen);
        FakeLibhegel second = new FakeLibhegel();
        second.stringValue = "b@c.d";
        drawWith(second, emailGen); // must rebuild rather than draw through the stale handle
        FakeLibhegel third = new FakeLibhegel();
        third.stringGeneratorEmailRc = Abi.E_INVALID_ARG;
        assertThrows(IllegalArgumentException.class, () -> drawWith(third, emailGen));
    }

    private static <T> T drawWith(FakeLibhegel fake, Generator<T> gen) {
        TestCase tc = new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        return tc.draw(gen);
    }

    @Test
    void ipv6FormattingCompressesTheLongestZeroRun() {
        assertEquals("::", fmt6(new int[] {0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("::1", fmt6(new int[] {0, 0, 0, 0, 0, 0, 0, 1}));
        assertEquals("1::", fmt6(new int[] {1, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("2001:db8::1", fmt6(new int[] {0x2001, 0xdb8, 0, 0, 0, 0, 0, 1}));
        // A lone zero group is not compressed, and only the longest run collapses (RFC 5952).
        assertEquals("1:0:1:1:1:1:1:1", fmt6(new int[] {1, 0, 1, 1, 1, 1, 1, 1}));
        assertEquals("1:0:1::1:1:1", fmt6(new int[] {1, 0, 1, 0, 0, 1, 1, 1}));
        assertEquals("1:2:3:4:5:6:7:8", fmt6(new int[] {1, 2, 3, 4, 5, 6, 7, 8}));
    }

    private static String fmt6(int[] groups) {
        byte[] b = new byte[16];
        for (int i = 0; i < 8; i++) {
            b[2 * i] = (byte) (groups[i] >> 8);
            b[2 * i + 1] = (byte) groups[i];
        }
        try {
            Method m = Class.forName("dev.hegel.generators.IpAddressGenerator")
                    .getDeclaredMethod("formatV6", byte[].class);
            m.setAccessible(true);
            return (String) m.invoke(null, (Object) b);
        } catch (ReflectiveOperationException t) {
            throw new AssertionError(t);
        }
    }

    // --- Pool bookkeeping against the fake ---
    @Test
    void poolTracksValuesByVariableId() {
        FakeLibhegel fake = new FakeLibhegel();
        TestCase tc = new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        Pool<String> pool = new Pool<>(tc);
        assertTrue(pool.isEmpty());
        pool.add("first");
        pool.add("second");
        assertEquals(2, pool.size());
        assertEquals("first", tc.draw(pool.reusable())); // fake picks variable id 0
        assertEquals(2, pool.size());
        fake.poolGenerateValue = 1L;
        assertEquals("second", tc.draw(pool.consuming()));
        assertEquals(1, pool.size());
        // Empty pools reject the draw.
        Pool<String> empty = new Pool<>(tc);
        assertThrows(AssumeRejected.class, () -> tc.draw(empty.reusable()));
        assertThrows(AssumeRejected.class, () -> tc.draw(empty.consuming()));
    }

    // --- Stateful driver against the fake ---
    static final class TwoRuleMachine {
        final List<String> applied = new java.util.ArrayList<>();

        @Rule
        void alpha(TestCase tc) {
            applied.add("alpha");
        }

        @Rule
        void beta(TestCase tc) {
            applied.add("beta");
            tc.assume(false); // exercises the rejected-rule path
        }

        @Invariant
        void alwaysFine(TestCase tc) {}
    }

    @Test
    void statefulDriverFollowsTheEngineRuleSequence() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.ruleSequence = new long[] {0, 1, 0, Abi.STATE_MACHINE_DONE};
        TwoRuleMachine machine = new TwoRuleMachine();
        TestCase tc = new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        Stateful.run(machine, tc);
        assertEquals(List.of("alpha", "beta", "alpha"), machine.applied);
        assertEquals(List.of("alpha", "beta"), fake.stateMachineRules);
        assertEquals(List.of("alwaysFine"), fake.stateMachineInvariants);
    }

    @Test
    void statefulDriverRejectsBadMachines() {
        FakeLibhegel fake = new FakeLibhegel();
        TestCase tc = fakeTestCase(fake);
        assertThrows(IllegalArgumentException.class, () -> Stateful.run(new Object(), tc));

        class BadSignature {
            @Rule
            void wrong(int x) {}
        }
        assertThrows(IllegalArgumentException.class, () -> Stateful.run(new BadSignature(), tc));

        class NoParameters {
            @Rule
            void wrong() {}
        }
        assertThrows(IllegalArgumentException.class, () -> Stateful.run(new NoParameters(), tc));

        class Fine {
            @Rule
            void ok(TestCase t) {}
        }
        fake.ruleSequence = new long[] {7}; // above the rule count
        HegelException high = assertThrows(HegelException.class, () -> Stateful.run(new Fine(), tc));
        assertTrue(high.getMessage().contains("out-of-range"), high.getMessage());

        FakeLibhegel negative = new FakeLibhegel();
        negative.ruleSequence = new long[] {-5}; // negative but not the DONE sentinel
        TestCase tc2 = fakeTestCase(negative);
        HegelException low = assertThrows(HegelException.class, () -> Stateful.run(new Fine(), tc2));
        assertTrue(low.getMessage().contains("out-of-range"), low.getMessage());
    }

    private static TestCase fakeTestCase(FakeLibhegel fake) {
        return new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @Test
    void statefulRuleFailuresUnwind() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.ruleSequence = new long[] {0, Abi.STATE_MACHINE_DONE};
        class Failing {
            @Rule
            void explode(TestCase t) {
                throw new AssertionError("rule failed");
            }
        }
        TestCase tc = new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        AssertionError e = assertThrows(AssertionError.class, () -> Stateful.run(new Failing(), tc));
        assertEquals("rule failed", e.getMessage());

        class FailingInvariant {
            @Rule
            void ok(TestCase t) {}

            @Invariant
            void broken(TestCase t) {
                throw new AssertionError("invariant failed");
            }
        }
        FakeLibhegel fake2 = new FakeLibhegel();
        TestCase tc2 = new TestCase(
                new LiveDataSource(fake2, FakeLibhegel.TC),
                false,
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertThrows(AssertionError.class, () -> Stateful.run(new FailingInvariant(), tc2));
    }

    @Test
    void isNullHandlesJavaNull() {
        assertTrue(Runner.isNull(null));
    }
}
