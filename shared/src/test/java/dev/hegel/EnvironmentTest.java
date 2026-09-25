package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine resolves what {@link Settings} leaves unset from its profile ({@code hegel.toml}) and
 * the {@code HEGEL_*} environment variables, and explicit settings win over both. The variables are
 * read from the process environment, which a JVM cannot change for itself, so each case launches
 * {@link EnvironmentFixture} in a child JVM on this test's classpath and reads its one-line verdict.
 */
class EnvironmentTest {
    @TempDir
    Path tmp;

    private static String fixture(String mode, Map<String, String> env) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(EnvironmentFixture.class.getName());
        command.add(mode);
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        // Start from a clean Hegel environment (keeping the library override this JVM runs with),
        // with the database off so the child leaves no .hegel directory behind.
        pb.environment().keySet().removeIf(k -> k.startsWith("HEGEL_") && !k.equals("HEGEL_LIBHEGEL_PATH"));
        pb.environment().put("HEGEL_DATABASE", "disabled");
        pb.environment().putAll(env);
        Process child = pb.start();
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(child.waitFor(2, TimeUnit.MINUTES), "fixture did not finish: " + output);
        return output;
    }

    @Test
    void hegelTestCasesSetsTheBudgetUnlessTheTestSetsItsOwn() throws Exception {
        String unset = fixture("count", Map.of("HEGEL_TEST_CASES", "7"));
        assertTrue(unset.contains("valid=7"), unset);
        // A budget compiled into the test wins over the variable.
        String explicit = fixture("count-explicit", Map.of("HEGEL_TEST_CASES", "7"));
        assertTrue(explicit.contains("valid=4"), explicit);
    }

    @Test
    void malformedVariableIsReportedAsAnIllegalArgument() throws Exception {
        String output = fixture("count", Map.of("HEGEL_TEST_CASES", "lots"));
        assertTrue(output.contains("error=") && output.contains("HEGEL_TEST_CASES"), output);
    }

    @Test
    void hegelTomlProfileSetsTheBudget() throws Exception {
        Path config = tmp.resolve("hegel.toml");
        Files.writeString(config, "[profiles.fixture]\ntest_cases = 9\n");
        String output = fixture("count", Map.of("HEGEL_CONFIG", config.toString(), "HEGEL_DEFAULT_PROFILE", "fixture"));
        assertTrue(output.contains("valid=9"), output);
    }

    @Test
    void hegelPrintBlobDecidesWhetherReproducersArePrinted() throws Exception {
        // The shipped profiles print a reproducer for each failure; the variable turns that off.
        String printed = fixture("fail", Map.of());
        assertTrue(printed.contains("done") && printed.contains("reproduceFailure = \""), printed);
        String quiet = fixture("fail", Map.of("HEGEL_PRINT_BLOB", "false"));
        assertTrue(quiet.contains("done") && !quiet.contains("reproduceFailure = \""), quiet);
    }
}
