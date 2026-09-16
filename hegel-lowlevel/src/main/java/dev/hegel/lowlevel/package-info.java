/**
 * The {@code libhegel} binding contract: what a binding implements and what a frontend consumes.
 *
 * <p>This package is for two audiences, neither of which is an ordinary Hegel user (they want
 * {@code dev.hegel.Hegel} and {@code dev.hegel.Generators} from {@code dev.hegel:hegel} or {@code
 * dev.hegel:hegel-jna}):
 *
 * <ul>
 *   <li><strong>People binding Hegel</strong> — implementing {@link dev.hegel.lowlevel.Libhegel}
 *       over some FFI mechanism (JNI, a GraalVM native, ...) and registering it as a {@link
 *       dev.hegel.lowlevel.LibhegelBackend}. The interface mirrors {@code hegel.h} one method per
 *       function, so the C header's documentation is the binding's specification.
 *   <li><strong>People building a frontend from scratch</strong> — driving the engine's run loop
 *       and per-case primitives themselves rather than through the Java {@code TestCase} and
 *       generator layer. {@link dev.hegel.lowlevel.Libhegel#load()} finds whichever binding is on
 *       the classpath; {@link dev.hegel.lowlevel.Abi} holds the constants.
 * </ul>
 *
 * <p>The contract deliberately stays close to the C ABI: opaque handles are raw {@code long}
 * addresses ({@code 0} is NULL) that the caller owns and must free, fallible per-case primitives
 * return the raw {@code hegel_result_t} code with results in out-parameters, and engine strings are
 * copied out before a method returns. Everything higher — exceptions for return codes, generators,
 * reporting — is frontend policy and lives in the frontend jars.
 *
 * <p><strong>Stability.</strong> This package is experimental. Consumers of {@code Libhegel} see only
 * additive changes as the engine grows, but implementors must expect new abstract methods with each
 * engine release that adds functions.
 */
package dev.hegel.lowlevel;
