package io.github.west3436.zas.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API method that may be called only from the logical server's main
 * thread (API-004, API-045).
 *
 * <p>Called from any other thread, such a method throws
 * {@link IllegalStateException} (see {@link ServerThreads}).
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ServerThread {}
