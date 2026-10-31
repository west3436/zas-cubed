package io.github.west3436.zas.api;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;

/**
 * The check behind {@link ServerThread}: mutations are refused off the server
 * thread (API-045, D-30).
 */
public final class ServerThreads {
    private ServerThreads() {}

    /**
     * Throws unless the caller is on the main thread of {@code level}'s server.
     *
     * <p>Thread: any; this is the check itself.
     *
     * @param level the level being changed
     * @param operation what the caller tried to do, for the message
     * @throws IllegalStateException if called from another thread
     */
    @AnyThread
    public static void require(ServerLevel level, String operation) {
        Objects.requireNonNull(level, "level");
        require(level.getServer(), operation);
    }

    /**
     * Throws unless the caller is on {@code server}'s own thread.
     *
     * <p>Thread: any; this is the check itself.
     *
     * @param server the server, or any event loop standing in for it
     * @param operation what the caller tried to do, for the message
     * @throws IllegalStateException if called from another thread
     */
    @AnyThread
    public static void require(BlockableEventLoop<?> server, String operation) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException(operation + " may only be called on the server thread, not on "
                    + Thread.currentThread().getName() + " (API-045)");
        }
    }
}
