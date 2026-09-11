package dev.hegel;

import java.io.PrintStream;

/**
 * The default {@link Reporter}: prints the classic report to a stream, exactly as the runner did
 * before reporters existed.
 */
final class PrintingReporter implements Reporter {
    private final PrintStream out;
    private boolean printBlob;
    private boolean multiple;

    PrintingReporter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void runStarted(Settings settings) {
        printBlob = settings.printBlob;
    }

    @Override
    public void engineOutput(String line) {
        out.println(line);
    }

    @Override
    public void caseStarted(boolean finalReplay) {
        // With several failures, a blank line separates one counterexample's report from the next.
        if (finalReplay && multiple) {
            out.println();
        }
    }

    @Override
    public void draw(String label, Object value) {
        out.println(label + " = " + TestCase.repr(value) + ";");
    }

    @Override
    public void note(String message) {
        out.println(message);
    }

    @Override
    public void failuresFound(int count) {
        multiple = count > 1;
        if (multiple) {
            out.println("Property-based test failed with " + count + " distinct failures.");
        }
    }

    @Override
    public void failure(Failure failure) {
        if (printBlob && !failure.flaky()) {
            out.println();
            out.println("To reproduce this failure, replay it with:");
            out.println("    @HegelTest(reproduceFailure = \"" + failure.reproduceBlob() + "\")");
        }
    }
}
