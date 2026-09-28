package dev.hegel;

import static dev.hegel.Generators.integers;

/**
 * The child-process side of {@link EnvironmentTest}. The engine reads the {@code HEGEL_*} variables
 * and {@code hegel.toml} from its own process while constructing a settings handle, so what they
 * produce can only be observed from a JVM launched with them. Prints one line per outcome:
 *
 * <ul>
 *   <li>{@code count}: {@code valid=N}, the number of cases a passing property ran under settings
 *       that leave the budget to the engine;
 *   <li>{@code count-explicit}: the same with an explicit {@code testCases(4)};
 *   <li>{@code strictness}: {@code strictness=<value>}, the nondeterminism strictness the engine
 *       resolved for settings that leave it unset;
 *   <li>{@code fail}: the printing reporter's output for a property that always fails, followed by
 *       {@code done};
 *   <li>{@code error=<message>} when constructing the settings failed on a malformed variable.
 * </ul>
 */
public final class EnvironmentFixture {
    private EnvironmentFixture() {}

    /**
     * Entry point.
     *
     * @param args the mode: {@code count}, {@code count-explicit}, {@code strictness} or {@code fail}
     */
    public static void main(String[] args) {
        try {
            switch (args[0]) {
                case "count":
                    System.out.println("valid=" + count(new Settings()));
                    break;
                case "count-explicit":
                    System.out.println("valid=" + count(new Settings().testCases(4)));
                    break;
                case "strictness":
                    Settings[] effective = new Settings[1];
                    Hegel.run(tc -> {}, new Settings().testCases(1), new Reporter() {
                        @Override
                        public void runStarted(Settings settings) {
                            effective[0] = settings;
                        }
                    });
                    System.out.println("strictness=" + effective[0].nondeterminismStrictness);
                    break;
                default:
                    Hegel.run(
                            tc -> {
                                tc.draw(integers(), "x");
                                throw new AssertionError("always");
                            },
                            new Settings().testCases(5),
                            Reporter.printing(System.out));
                    System.out.println("done");
                    break;
            }
        } catch (IllegalArgumentException e) {
            System.out.println("error=" + e.getMessage());
        }
    }

    private static long count(Settings settings) {
        return Hegel.run(tc -> tc.draw(integers()), settings, Reporter.silent())
                .statistics()
                .valid();
    }
}
