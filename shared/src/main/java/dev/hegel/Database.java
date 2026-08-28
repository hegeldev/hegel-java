package dev.hegel;

/**
 * The example-database setting: unset, disabled, or a directory path.
 *
 * <p>This is a closed three-state value — construct one with {@link #unset()}, {@link #disabled()},
 * or {@link #path(String)} and hand it to {@link Settings#database(Database)}. Because the three
 * states are mutually exclusive values of a single setting, you cannot express a contradiction
 * (such as "disabled, but at this path").
 *
 * <ul>
 *   <li>{@link #unset()} — leave the engine default in place (the database lives at {@code .hegel},
 *       and is disabled automatically in CI).
 *   <li>{@link #disabled()} — no persistence and no replay.
 *   <li>{@link #path(String)} — use the given directory as the database root.
 * </ul>
 */
public final class Database {
    /**
     * Sentinel string for {@link HegelTest#database()} that disables the database entirely — the
     * annotation equivalent of {@link #disabled()} (annotation attributes can't carry a {@code
     * Database} value). Distinct enough that it can never collide with a real directory path.
     */
    public static final String DISABLED = "__hegel_database_disabled__";

    enum Kind {
        UNSET,
        DISABLED,
        PATH
    }

    final Kind kind;
    final String path; // non-null only when kind == PATH

    private Database(Kind kind, String path) {
        this.kind = kind;
        this.path = path;
    }

    /**
     * Leave the engine default in place (database at {@code .hegel}; disabled in CI).
     *
     * @return the unset database setting
     */
    public static Database unset() {
        return new Database(Kind.UNSET, null);
    }

    /**
     * Disable the example database entirely — no persistence, no replay.
     *
     * @return the disabled database setting
     */
    public static Database disabled() {
        return new Database(Kind.DISABLED, null);
    }

    /**
     * Use the directory at {@code path} as the example database root.
     *
     * @param path the database directory
     * @return a database setting pointing at {@code path}
     */
    public static Database path(String path) {
        return new Database(Kind.PATH, path);
    }
}
