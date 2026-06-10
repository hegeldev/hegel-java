package dev.hegel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * JUnit 5 extension backing {@link HegelTest}. Reports the property as a single test entry and
 * drives the engine loop, invoking the user method once per generated input.
 *
 * @hidden
 */
public final class HegelTestExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return isHegelTest(context.getTestMethod().orElse(null));
    }

    static boolean isHegelTest(Method method) {
        return method != null && method.isAnnotationPresent(HegelTest.class);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return Stream.of(new HegelInvocationContext());
    }

    private static final class HegelInvocationContext implements TestTemplateInvocationContext {
        @Override
        public String getDisplayName(int invocationIndex) {
            return "hegel";
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new TestCaseResolver(), new PropertyInterceptor());
        }
    }

    /**
     * Resolves the {@link TestCase} parameter so JUnit accepts the method shape; the value is unused.
     */
    private static final class TestCaseResolver implements ParameterResolver {
        @Override
        public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
            return isTestCaseParam(pc.getParameter().getType());
        }

        @Override
        public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
            return null;
        }
    }

    private static final class PropertyInterceptor implements InvocationInterceptor {
        @Override
        public void interceptTestTemplateMethod(
                Invocation<Void> invocation,
                ReflectiveInvocationContext<Method> invocationContext,
                ExtensionContext extensionContext)
                throws Throwable {
            invocation.skip();
            Method method = invocationContext.getExecutable();
            HegelTest ann = method.getAnnotation(HegelTest.class);
            Settings settings = settingsFrom(ann, method.getName());
            Object target = invocationContext.getTarget().orElse(null);
            Hegel.test(tc -> invoke(method, target, tc), settings);
        }
    }

    static Settings settingsFrom(HegelTest ann, String methodName) {
        String name = ann.name().isEmpty() ? methodName : ann.name();
        Settings s = new Settings()
                .testCases(ann.testCases())
                .verbosity(ann.verbosity())
                .mode(ann.mode())
                .reportMultipleFailures(ann.reportMultipleFailures())
                .suppressHealthCheck(ann.suppressHealthCheck())
                .name(name);
        if (ann.seed() != HegelTest.NO_SEED) {
            s = s.seed(ann.seed());
        }
        if (ann.derandomize() != OptBoolean.DEFAULT) {
            s = s.derandomize(ann.derandomize() == OptBoolean.TRUE);
        }
        if (!isDefaultPhases(ann.phases())) {
            s = s.phases(ann.phases());
        }
        // database() is a single tri-state String (members can't be value objects): "" leaves the
        // engine default, DISABLED turns it off, anything else is a directory path.
        if (ann.database().equals(Database.DISABLED)) {
            s = s.database(Database.disabled());
        } else if (!ann.database().isEmpty()) {
            s = s.database(Database.path(ann.database()));
        }
        return s;
    }

    /**
     * Whether {@code phases} is the annotation default (every phase), in which case the engine
     * default is left untouched. An explicitly empty list is <em>not</em> the default — it disables
     * all phases.
     */
    static boolean isDefaultPhases(Phase[] phases) {
        return phases.length > 0 && EnumSet.copyOf(Arrays.asList(phases)).size() == Phase.values().length;
    }

    static boolean isTestCaseParam(Class<?> type) {
        return type == TestCase.class;
    }

    @Generated // thin reflective dispatch; failure propagation is verified end-to-end.
    private static void invoke(Method method, Object target, TestCase tc) {
        try {
            method.setAccessible(true);
            method.invoke(target, tc);
        } catch (InvocationTargetException e) {
            sneakyThrow(e.getCause());
        } catch (IllegalAccessException e) {
            throw new HegelException("Cannot invoke @HegelTest method " + method.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
