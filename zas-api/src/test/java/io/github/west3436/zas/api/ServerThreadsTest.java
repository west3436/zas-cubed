package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.Test;

/**
 * Mutations are refused off the server thread (API-045). The GameTest
 * {@code api_045_mutation_off_server_thread_is_refused} checks the same
 * through {@code GasMutations} on a real server.
 */
class ServerThreadsTest {
    /** An event loop whose own thread is the one that created it, like a server. */
    private static final class FakeServer extends BlockableEventLoop<Runnable> {
        private final Thread thread = Thread.currentThread();

        FakeServer() {
            super("zas-api-test");
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return thread;
        }
    }

    @Test
    void api_045_mutation_check_passes_on_the_server_thread() {
        FakeServer server = new FakeServer();
        assertDoesNotThrow(() -> ServerThreads.require(server, "test"));
    }

    @Test
    void api_045_mutation_check_refuses_another_thread() throws InterruptedException {
        FakeServer server = new FakeServer();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                ServerThreads.require(server, "GasMutations.add");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        worker.start();
        worker.join();
        assertInstanceOf(IllegalStateException.class, thrown.get());
        assertTrue(thrown.get().getMessage().contains("GasMutations.add"));
    }
}
