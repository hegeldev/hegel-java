package dev.hegel;

/**
 * Build-time constants filtered in by Maven from {@code src/main/java-templates}. Generated; do not
 * edit. Change the values via the {@code <libhegel.version>} property in {@code pom.xml}.
 */
final class BuildInfo {
    /**
     * The libhegel release these bindings were built and tested against; used to warn if a
     * user-supplied {@code $HEGEL_LIBHEGEL_PATH} points at a different version.
     */
    static final String ENGINE_VERSION = "${libhegel.version}";

    private BuildInfo() {}
}
