package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import net.neoforged.bus.api.IEventBus;

/**
 * The server events scripts listen for, as {@code ZasEvents.zoneBreached(event => ...)}
 * (COMPAT-050): one for each of the API's zone, fire and open-air events, under the API event's
 * name without its {@code Event}.
 *
 * <p>ZAS³ fires the API's events on the server thread, when the atmos tick that caused them is
 * applied, and each is handed on from there. An event no script listens for costs one check and
 * makes nothing. KubeJS itself contains a listener that throws: it logs the error with the
 * script's name and line, and the other listeners still hear the event.
 */
final class ZasEvents {
    static final EventGroup GROUP = EventGroup.of("ZasEvents");

    static final EventHandler ZONE_CREATED = GROUP.server("zoneCreated", () -> ZoneKubeEvent.class);
    static final EventHandler ZONE_MERGED = GROUP.server("zoneMerged", () -> ZoneMergedKubeEvent.class);
    static final EventHandler ZONE_SPLIT = GROUP.server("zoneSplit", () -> ZoneSplitKubeEvent.class);
    static final EventHandler ZONE_BREACHED = GROUP.server("zoneBreached", () -> ZoneKubeEvent.class);
    static final EventHandler ZONE_SEALED = GROUP.server("zoneSealed", () -> ZoneKubeEvent.class);
    static final EventHandler ZONE_DISSOLVED = GROUP.server("zoneDissolved", () -> ZoneKubeEvent.class);
    static final EventHandler ZONE_PAUSED = GROUP.server("zonePaused", () -> ZoneKubeEvent.class);
    static final EventHandler ZONE_RESUMED = GROUP.server("zoneResumed", () -> ZoneKubeEvent.class);
    static final EventHandler FIRE_IGNITED = GROUP.server("fireIgnited", () -> FireIgnitedKubeEvent.class);
    static final EventHandler FIRE_EXTINGUISHED =
            GROUP.server("fireExtinguished", () -> FireExtinguishedKubeEvent.class);
    static final EventHandler OPEN_AIR_GAS = GROUP.server("openAirGas", () -> OpenAirGasKubeEvent.class);

    private ZasEvents() {}

    /** Starts handing the API's events on to scripts. Called once. */
    static void listen(IEventBus bus) {
        bus.addListener(ZoneCreatedEvent.class, event -> zone(ZONE_CREATED, event));
        bus.addListener(ZoneBreachedEvent.class, event -> zone(ZONE_BREACHED, event));
        bus.addListener(ZoneSealedEvent.class, event -> zone(ZONE_SEALED, event));
        bus.addListener(ZoneDissolvedEvent.class, event -> zone(ZONE_DISSOLVED, event));
        bus.addListener(ZonePausedEvent.class, event -> zone(ZONE_PAUSED, event));
        bus.addListener(ZoneResumedEvent.class, event -> zone(ZONE_RESUMED, event));
        bus.addListener(ZoneMergedEvent.class, event -> {
            if (ZONE_MERGED.hasListeners()) {
                ZONE_MERGED.post(new ZoneMergedKubeEvent(event));
            }
        });
        bus.addListener(ZoneSplitEvent.class, event -> {
            if (ZONE_SPLIT.hasListeners()) {
                ZONE_SPLIT.post(new ZoneSplitKubeEvent(event));
            }
        });
        bus.addListener(FireIgnitedEvent.class, event -> {
            if (FIRE_IGNITED.hasListeners()) {
                FIRE_IGNITED.post(new FireIgnitedKubeEvent(event));
            }
        });
        bus.addListener(FireExtinguishedEvent.class, event -> {
            if (FIRE_EXTINGUISHED.hasListeners()) {
                FIRE_EXTINGUISHED.post(new FireExtinguishedKubeEvent(event));
            }
        });
        bus.addListener(OpenAirGasEvent.class, event -> {
            if (OPEN_AIR_GAS.hasListeners()) {
                OPEN_AIR_GAS.post(new OpenAirGasKubeEvent(event));
            }
        });
    }

    private static void zone(EventHandler handler, ZoneLifecycleEvent event) {
        if (handler.hasListeners()) {
            handler.post(new ZoneKubeEvent(event));
        }
    }
}
