package dev.hegel;

/**
 * A three-valued boolean for an annotation attribute whose underlying setting has an
 * environment-dependent default (so a plain {@code boolean} cannot express "leave the default").
 *
 * <p>Java annotation attributes must be compile-time constants and cannot be {@code null} or a
 * boxed {@code Boolean}, so a setting with a "force on / force off / leave default" choice needs a
 * dedicated enum rather than a {@code boolean}. (Jackson's {@code OptBoolean} exists for the same
 * reason.)
 *
 * <p>Used by {@link HegelTest#derandomize()}: {@link #DEFAULT} keeps Hegel's behaviour
 * (deterministic in CI, random otherwise), while {@link #TRUE}/{@link #FALSE} force the choice.
 */
public enum OptBoolean {
    /** Leave the setting's environment-dependent default in place. */
    DEFAULT,
    /** Force the setting on. */
    TRUE,
    /** Force the setting off. */
    FALSE
}
