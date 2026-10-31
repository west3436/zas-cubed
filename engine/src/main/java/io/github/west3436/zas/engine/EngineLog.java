package io.github.west3436.zas.engine;

/**
 * Where the engine reports problems. The engine has no logging dependency; the
 * platform passes one backed by its own logger, and tests pass one that
 * records messages.
 */
@FunctionalInterface
public interface EngineLog {
    /** Reports a problem the engine handled, such as a rejected operation. */
    void warn(String message);

    /** A log backed by {@link System.Logger}, named {@code zas.engine}. */
    static EngineLog system() {
        System.Logger logger = System.getLogger("zas.engine");
        return message -> logger.log(System.Logger.Level.WARNING, message);
    }
}
