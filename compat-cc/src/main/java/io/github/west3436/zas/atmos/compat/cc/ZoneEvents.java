package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.NotAttachedException;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Tells the computers attached to an area air controller what happens to its zone (COMPAT-022): a
 * breach, its sealing again, its catching fire and its last fire going out, as the API's zone and
 * fire events say them (API-030, API-031).
 *
 * <p>It listens once for each of the four events, and nothing here runs between them. An event is
 * for the controllers whose zone it names, among those that have a computer attached: a controller
 * with none is never looked at. A controller's zone is the one it read at its last step, or the one
 * at its own point now, since a zone can get its id in the atmos tick that the event is from.
 *
 * <p>A computer gets the event's name, then the name the controller is attached to it by (its side,
 * or its name on a wired network), then the x, y and z of the event's block: a block of the zone for
 * a breach or a sealing, the fire's lowest hotspot for a fire. Whether the controller has power makes
 * no difference: it is the zone the event is about.
 *
 * <p>Computers attach and detach on their own threads, so the sets are concurrent. The events come
 * on the server thread.
 */
final class ZoneEvents {
    /** The zone gained an opening to the ambient ({@link ZoneBreachedEvent}). */
    static final String BREACHED = "zas_zone_breached";

    /** The zone lost its last opening to the ambient ({@link ZoneSealedEvent}). */
    static final String SEALED = "zas_zone_sealed";

    /** The zone caught fire ({@link FireIgnitedEvent}). */
    static final String IGNITED = "zas_fire_ignited";

    /** The zone's last fire went out ({@link FireExtinguishedEvent}). */
    static final String EXTINGUISHED = "zas_fire_extinguished";

    private static final Map<AreaAirControllerBlockEntity, Set<IComputerAccess>> ATTACHED = new ConcurrentHashMap<>();

    private ZoneEvents() {}

    /** Listens for the four events, and forgets every computer when the server stops. */
    static void register(IEventBus bus) {
        bus.addListener(ZoneBreachedEvent.class, e -> tell(e.level(), e.zone(), BREACHED, e.pos()));
        bus.addListener(ZoneSealedEvent.class, e -> tell(e.level(), e.zone(), SEALED, e.pos()));
        bus.addListener(FireIgnitedEvent.class, e -> tell(e.level(), e.zone(), IGNITED, e.hotspot()));
        bus.addListener(FireExtinguishedEvent.class, e -> tell(e.level(), e.zone(), EXTINGUISHED, e.lastHotspot()));
        bus.addListener(ServerStoppedEvent.class, e -> ATTACHED.clear());
    }

    /** A computer attached to a controller's peripheral. Any thread. */
    static void attach(AreaAirControllerBlockEntity controller, IComputerAccess computer) {
        ATTACHED.compute(controller, (c, computers) -> {
            Set<IComputerAccess> set = computers == null ? ConcurrentHashMap.newKeySet() : computers;
            set.add(computer);
            return set;
        });
    }

    /** A computer detached from a controller's peripheral. Any thread. */
    static void detach(AreaAirControllerBlockEntity controller, IComputerAccess computer) {
        ATTACHED.computeIfPresent(controller, (c, computers) -> {
            computers.remove(computer);
            return computers.isEmpty() ? null : computers;
        });
    }

    /** How many computers are attached to a controller, for tests. Any thread. */
    static int attached(AreaAirControllerBlockEntity controller) {
        Set<IComputerAccess> computers = ATTACHED.get(controller);
        return computers == null ? 0 : computers.size();
    }

    private static void tell(ServerLevel level, ZoneId zone, String event, BlockPos pos) {
        for (Map.Entry<AreaAirControllerBlockEntity, Set<IComputerAccess>> entry : ATTACHED.entrySet()) {
            AreaAirControllerBlockEntity controller = entry.getKey();
            if (controller.isRemoved()) {
                // Its block is gone or unloaded. CC: Tweaked detaches its computers by itself; this
                // only keeps a dead block entity from being held until it does.
                ATTACHED.remove(controller);
                continue;
            }
            if (controller.getLevel() != level || !covers(level, controller, zone)) {
                continue;
            }
            for (IComputerAccess computer : entry.getValue()) {
                try {
                    computer.queueEvent(event, computer.getAttachmentName(), pos.getX(), pos.getY(), pos.getZ());
                } catch (NotAttachedException detached) {
                    // It detached on its own thread while the event was on its way.
                }
            }
        }
    }

    /** Whether a zone is the controller's: the one it read last, or the one at its point now. */
    private static boolean covers(ServerLevel level, AreaAirControllerBlockEntity controller, ZoneId zone) {
        return controller.zone().filter(zone::equals).isPresent()
                || Atmospheres.at(level, controller.airPoint())
                        .zone()
                        .filter(zone::equals)
                        .isPresent();
    }
}
