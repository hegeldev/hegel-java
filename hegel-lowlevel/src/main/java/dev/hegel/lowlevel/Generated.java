package dev.hegel.lowlevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as excluded from coverage measurement.
 *
 * <p>JaCoCo automatically ignores members annotated with an annotation whose name contains
 * "Generated". Used only for genuinely unreachable defensive catch blocks (here: a {@code
 * NoSuchAlgorithmException} for SHA-256, which the JLS mandates to exist).
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
@interface Generated {}
