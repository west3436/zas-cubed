package io.github.west3436.zas.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API method that may be called from any thread (API-004).
 *
 * <p>Such methods read only immutable data, such as the published snapshot of
 * the last applied atmos tick (API-046), so they never block on the server or
 * the atmos worker.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AnyThread {}
