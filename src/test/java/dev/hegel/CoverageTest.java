package dev.hegel;

import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Targeted tests closing remaining coverage branches. */
class CoverageTest {
    private static final Map<String, String> NO_CI = Map.of();

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

    // --- BasicGenerator is its own basic representation ---
    @Test
    void basicGeneratorAsBasicIsIdentity() {
        var basic = integers().asBasic();
        assertSame(basic, basic.asBasic());
    }

    // --- FloatGenerator / DoubleGenerator schema branches (no engine needed) ---
    @Test
    void floatSchemaVariants() {
        assertNotNull(floats().asBasic());
        assertNotNull(floats().allowNan(true).asBasic());
        assertNotNull(floats().allowInfinity(true).asBasic());
        assertNotNull(floats().allowNan(false).allowInfinity(false).asBasic());
        assertNotNull(floats().min(0).max(1).asBasic());
        assertNotNull(floats().min(-2).asBasic());
        assertNotNull(floats().max(2).excludeMin(true).excludeMax(true).asBasic());
    }

    @Test
    void doubleSchemaVariants() {
        assertNotNull(doubles().asBasic());
        assertNotNull(doubles().allowNan(true).asBasic());
        assertNotNull(doubles().allowInfinity(true).asBasic());
        assertNotNull(doubles().allowNan(false).allowInfinity(false).asBasic());
        assertNotNull(doubles().min(0).max(1).asBasic());
        assertNotNull(doubles().min(-2).asBasic());
        assertNotNull(doubles().max(2).excludeMin(true).excludeMax(true).asBasic());
    }

    // --- Collection schema basicness branches ---
    @Test
    void setAndDictBasicnessBranches() {
        assertNotNull(sets(integers()).minSize(1).maxSize(3).asBasic()); // bounded, basic
        assertNotNull(maps(integers(), integers()).minSize(1).maxSize(3).asBasic()); // bounded, basic
        // key basic, value non-basic -> not basic
        assertNull(maps(integers(), integers().filter(x -> x > 0)).asBasic());
        // key non-basic -> not basic
        assertNull(maps(integers().filter(x -> x > 0), integers()).asBasic());
    }

    @Test
    void textExcludeCategoriesAlreadyHasCs() {
        assertNotNull(text().excludeCategories("Cs").asBasic());
        assertNotNull(text().excludeCategories("Cc").asBasic());
    }

    @Test
    void generatorWithoutOverridesFails() {
        // A Generator overriding neither doDraw() nor asBasic() has no draw path.
        Generator<Object> g = new Generator<>() {};
        assertThrows(IllegalStateException.class, () -> g.doDraw(null));
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
                reportMultipleFailures = true,
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

        // Defaults: no seed, no derandomize override, engine-default phases, default database, method
        // name as the property name.
        Settings noSeed = HegelTestExtension.settingsFrom(unseeded.getAnnotation(HegelTest.class), "u");
        assertFalse(noSeed.hasSeed);
        assertNull(noSeed.derandomize);
        assertNull(noSeed.phasesMask);
        assertEquals(Database.Kind.UNSET, noSeed.database.kind);
        assertEquals(0, noSeed.suppressMask);
        assertEquals(Mode.TEST_RUN, noSeed.mode);
        assertFalse(noSeed.reportMultipleFailures);
        assertEquals("u", noSeed.name);

        // Fully-configured: derandomize forced on, a single explicit phase, a suppressed check, single
        // case and multi-failure modes, and a name override.
        Method configured = Holder.class.getDeclaredMethod("configured", TestCase.class);
        Settings c = HegelTestExtension.settingsFrom(configured.getAnnotation(HegelTest.class), "ignored");
        assertEquals(Boolean.TRUE, c.derandomize);
        assertEquals(Integer.valueOf(Phase.GENERATE.bit), c.phasesMask);
        assertEquals(HealthCheck.TOO_SLOW.bit, c.suppressMask);
        assertEquals(Mode.SINGLE_TEST_CASE, c.mode);
        assertTrue(c.reportMultipleFailures);
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

    // --- Runner residual branches via fake ---
    private static void run(FakeLibhegel fake, java.util.function.Consumer<TestCase> body) {
        Runner.run(
                fake,
                new Settings().database(Database.disabled()),
                body,
                NO_CI,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @Test
    void nextTestCaseNullWithNullLastErrorCompletesNormally() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 0;
        fake.doneLastError = null; // exercises the msg == null path in the loop
        run(fake, tc -> {});
        assertTrue(fake.markedStatuses.isEmpty());
    }

    @Test
    void describeHandlesNullMessage() {
        // describe() is exercised only by the multiple-failures report path.
        FakeLibhegel fake = new FakeLibhegel();
        fake.finalReplay = true;
        Runner.run(
                fake,
                new Settings().database(Database.disabled()).reportMultipleFailures(true),
                tc -> {
                    throw new IllegalStateException(); // null message
                },
                NO_CI,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertEquals(Abi.STATUS_INTERESTING, fake.markedStatuses.get(0));
    }

    @Test
    void failureWithNullDiagnosticAndPanicUsesEmpty() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false;
        FakeLibhegel.Failure f = new FakeLibhegel.Failure();
        f.diagnostic = null;
        f.panic = null;
        fake.failures.add(f);
        assertThrows(AssertionError.class, () -> run(fake, tc -> {}));
    }

    @Test
    void backendErrorWithNullMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateRc = Abi.E_BACKEND;
        fake.lastError = null;
        LiveDataSource ds = new LiveDataSource(fake, FakeLibhegel.TC);
        assertThrows(HegelException.class, () -> ds.generate(com.upokecenter.cbor.CBORObject.NewMap()));
    }

    @Test
    void isNullHandlesJavaNull() {
        assertTrue(Runner.isNull(null));
    }
}
