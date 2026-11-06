package io.github.west3436.zas.simulation;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import java.lang.reflect.Method;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Fires ZAS³'s API events (API-032) so that a listener's exception stays with that listener
 * (NFR-013). The only place ZAS³ posts an event.
 *
 * <p>The bus's {@code post} can't do this. When a listener throws it logs an error, every time, then
 * rethrows, and the listeners after that one never hear the event. So this calls the listeners itself,
 * in the bus's order. The bus has no public way to get them, but {@code net.neoforged.bus} is an open
 * module, which permits reading its private {@code getListenerList}.
 *
 * <p>If a later bus has no such method, this falls back to {@code post} inside a guard. The server
 * still keeps running, but a listener that throws then costs the ones after it that event, and the bus
 * logs each time. {@code nfr_013_an_event_listener_that_throws_is_skipped_and_logged_once_a_minute}
 * fails if that happens.
 */
public final class ApiEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The bus's list of an event's listeners, or null if it can't be read. */
    private static final Method LISTENER_LIST = listenerList();

    private ApiEvents() {}

    /** Fires an event on {@code NeoForge.EVENT_BUS}. Server thread. */
    public static void post(Event event) {
        EventListener[] listeners = listeners(event);
        if (listeners == null) {
            try {
                NeoForge.EVENT_BUS.post(event);
            } catch (Exception | LinkageError e) {
                failed("A listener of " + event.getClass().getSimpleName(), e);
            }
            return;
        }
        for (EventListener listener : listeners) {
            try {
                listener.invoke(event);
            } catch (Exception | LinkageError e) {
                failed("Listener " + listener + " of " + event.getClass().getSimpleName(), e);
            }
        }
    }

    private static void failed(String source, Throwable error) {
        if (ThirdPartyFaults.shouldLog(source)) {
            LOGGER.error("{} threw; skipped for this event (logged at most once a minute)", source, error);
        }
    }

    private static EventListener @Nullable [] listeners(Event event) {
        if (LISTENER_LIST == null) {
            return null;
        }
        try {
            return ((ListenerList) LISTENER_LIST.invoke(NeoForge.EVENT_BUS, event.getClass())).getListeners();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Method listenerList() {
        try {
            Method method = EventBus.class.getDeclaredMethod("getListenerList", Class.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn(
                    "Can't read the event bus's listeners, so a listener that throws will stop the ones after it"
                            + " hearing that event (NFR-013)",
                    e);
            return null;
        }
    }
}
