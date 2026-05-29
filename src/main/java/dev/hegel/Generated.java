package dev.hegel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as excluded from coverage measurement.
 *
 * <p>JaCoCo automatically ignores members annotated with an annotation whose name contains
 * "Generated". We use it for the two genuinely unreachable defensive catch blocks: a {@code
 * NoSuchAlgorithmException} for SHA-256 (mandated to exist by the JLS) and an {@code
 * IllegalAccessException} after {@code setAccessible(true)} has already succeeded.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
@interface Generated {}
