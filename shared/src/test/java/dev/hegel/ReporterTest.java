package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hegel.lowlevel.Abi;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The built-in reporters and the {@link Label} constants. */
class ReporterTest {
    /** The captured output with platform line endings normalised, so exact comparisons hold on Windows. */
    private static String text(ByteArrayOutputStream buf) {
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void silentReporterIgnoresEveryCallback() {
        Reporter silent = Reporter.silent();
        RunReport report = new RunReport(RunStatus.PASSED, new RunStatistics.Counter().snapshot(), null, List.of());
        Failure failure = new Failure("origin", "blob", new AssertionError("x"), Map.of(), List.of());
        silent.runStarted(new Settings());
        silent.engineOutput("line");
        silent.caseStarted(false);
        silent.draw("x", 1, true);
        silent.note("n", true);
        silent.caseFinished(CaseOutcome.VALID, false);
        silent.failuresFound(1);
        silent.failure(failure);
        silent.runFinished(report);
    }

    @Test
    void printingReporterFormatsDrawsNotesAndEngineOutput() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Reporter printing = Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8));
        printing.runStarted(new Settings());
        printing.engineOutput("engine line");
        printing.caseStarted(true); // a lone failure: no separating blank line
        printing.draw("xs", List.of(1, 2), true);
        printing.note("a note", true);
        printing.caseFinished(CaseOutcome.INTERESTING, true);
        printing.failure(new Failure("o", "b64", new AssertionError("x"), Map.of(), List.of())); // printBlob off
        assertEquals("engine line\nxs = [1, 2];\na note\n", text(buf));
    }

    @Test
    void printingReporterHonoursVerbosity() {
        ByteArrayOutputStream quietBuf = new ByteArrayOutputStream();
        Reporter quiet = Reporter.printing(new PrintStream(quietBuf, true, StandardCharsets.UTF_8));
        quiet.runStarted(new Settings().verbosity(Verbosity.QUIET));
        quiet.draw("x", 1, true);
        quiet.note("hidden", true);
        assertEquals("", quietBuf.toString(StandardCharsets.UTF_8));

        // Verbose: non-final draws print like final ones (the runner decides what to send).
        ByteArrayOutputStream verboseBuf = new ByteArrayOutputStream();
        Reporter verbose = Reporter.printing(new PrintStream(verboseBuf, true, StandardCharsets.UTF_8));
        verbose.runStarted(new Settings().verbosity(Verbosity.VERBOSE));
        verbose.draw("x", 1, false);
        verbose.note("shown", false);
        assertEquals("x = 1;\nshown\n", text(verboseBuf));
    }

    @Test
    void labelsMatchTheEngineConstants() {
        assertEquals(Abi.LABEL_LIST, Label.LIST);
        assertEquals(Abi.LABEL_LIST_ELEMENT, Label.LIST_ELEMENT);
        assertEquals(Abi.LABEL_SET, Label.SET);
        assertEquals(Abi.LABEL_SET_ELEMENT, Label.SET_ELEMENT);
        assertEquals(Abi.LABEL_MAP, Label.MAP);
        assertEquals(Abi.LABEL_MAP_ENTRY, Label.MAP_ENTRY);
        assertEquals(Abi.LABEL_TUPLE, Label.TUPLE);
        assertEquals(Abi.LABEL_ONE_OF, Label.ONE_OF);
        assertEquals(Abi.LABEL_OPTIONAL, Label.OPTIONAL);
        assertEquals(Abi.LABEL_FIXED_DICT, Label.FIXED_DICT);
        assertEquals(Abi.LABEL_FLAT_MAP, Label.FLAT_MAP);
        assertEquals(Abi.LABEL_FILTER, Label.FILTER);
        assertEquals(Abi.LABEL_MAPPED, Label.MAPPED);
        assertEquals(Abi.LABEL_SAMPLED_FROM, Label.SAMPLED_FROM);
        assertEquals(Abi.LABEL_ENUM_VARIANT, Label.ENUM_VARIANT);
        assertEquals(Abi.LABEL_STATEFUL_RULE, Label.STATEFUL_RULE);
        // Minted labels are stable and match the hash the composite generator already uses.
        assertEquals(Label.COMPOSITE, Label.of("dev.hegel.composite"));
        assertEquals(Label.of("my.frontend.pair"), Label.of("my.frontend.pair"));
    }
}
