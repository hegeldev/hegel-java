package dev.hegel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      ExtensionContext context) {
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
      settings.check(tc -> invoke(method, target, tc));
    }
  }

  static Settings settingsFrom(HegelTest ann, String name) {
    Settings s =
        Settings.defaults().testCases(ann.testCases()).verbosity(ann.verbosity()).name(name);
    if (ann.seed() != HegelTest.NO_SEED) {
      s = s.seed(ann.seed());
    }
    return s;
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
