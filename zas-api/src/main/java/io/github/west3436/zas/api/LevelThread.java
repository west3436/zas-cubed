package io.github.west3436.zas.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API method that may be called only from the main thread of the logical side its level
 * belongs to, or its entity's level: the server thread for a server-side level, the client thread
 * for a client-side one (API-004).
 *
 * <p>Such methods read live game state, such as an entity's effects and equipment, which only
 * that thread may read safely. For a server-side level, a call from any other thread throws
 * {@link IllegalStateException} (see {@link ServerThreads}). For a client-side level nothing
 * checks the thread, so the caller must keep to the client thread.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LevelThread {}
