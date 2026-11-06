package io.github.west3436.zas.example;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Which zones are open to the ambient or on fire right now, for an alarm to act on, followed through
 * ZAS³'s events (API-030, API-031). The API has no call that lists zones, so following the events is how
 * a mod knows this.
 *
 * <p>An event only says that something changed. Each listener reads the zone with {@link
 * Atmospheres#zone}, which is empty for a zone that's gone, and which already shows the whole atmos tick
 * the event came from. So it needs no bookkeeping of its own for merges and splits, and a zone resumed
 * from chunk storage, which fires no {@link ZoneCreatedEvent}, comes out right.
 *
 * <p>Every event fires on the server thread, so these sets need no lock. Without ZAS³ none fires.
 */
public final class AtmosphereAlarm {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, Set<ZoneId>> OPEN = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<ZoneId>> BURNING = new HashMap<>();

    private AtmosphereAlarm() {}

    /**
     * The zones of a level open to the ambient now. Server thread only.
     *
     * @param level the level
     * @return its open zones
     */
    public static Set<ZoneId> openZones(ServerLevel level) {
        return Set.copyOf(OPEN.getOrDefault(level.dimension(), Set.of()));
    }

    /**
     * The zones of a level on fire now. Server thread only.
     *
     * @param level the level
     * @return its burning zones
     */
    public static Set<ZoneId> burningZones(ServerLevel level) {
        return Set.copyOf(BURNING.getOrDefault(level.dimension(), Set.of()));
    }

    private static void follow(ServerLevel level, ZoneId zone) {
        Set<ZoneId> open = OPEN.computeIfAbsent(level.dimension(), dimension -> new HashSet<>());
        boolean isOpen =
                Atmospheres.zone(level, zone).filter(ZoneInfo::breached).isPresent();
        if (isOpen ? open.add(zone) : open.remove(zone)) {
            LOGGER.info("Zone {} in {} is {}", zone, level.dimension().location(), isOpen ? "open" : "no longer open");
        }
    }

    @SubscribeEvent
    public static void onCreated(ZoneCreatedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onMerged(ZoneMergedEvent event) {
        follow(event.level(), event.zone());
        follow(event.level(), event.absorbed());
    }

    @SubscribeEvent
    public static void onSplit(ZoneSplitEvent event) {
        follow(event.level(), event.zone());
        follow(event.level(), event.remnant());
    }

    @SubscribeEvent
    public static void onBreached(ZoneBreachedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onSealed(ZoneSealedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onDissolved(ZoneDissolvedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onPaused(ZonePausedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onResumed(ZoneResumedEvent event) {
        follow(event.level(), event.zone());
    }

    @SubscribeEvent
    public static void onFireIgnited(FireIgnitedEvent event) {
        BURNING.computeIfAbsent(event.level().dimension(), dimension -> new HashSet<>())
                .add(event.zone());
        LOGGER.info("Zone {} caught fire at {}", event.zone(), event.hotspot());
    }

    @SubscribeEvent
    public static void onFireExtinguished(FireExtinguishedEvent event) {
        BURNING.computeIfAbsent(event.level().dimension(), dimension -> new HashSet<>())
                .remove(event.zone());
        LOGGER.info("Zone {} stopped burning", event.zone());
    }

    /** Gas that went to or came from the open air, which no zone's total shows (CORE-058). */
    @SubscribeEvent
    public static void onOpenAirGas(OpenAirGasEvent event) {
        LOGGER.debug(
                "{} {} {} mol at {}",
                event.source(),
                event.added() ? "vented" : "drew",
                event.gas().totalMoles(),
                event.pos());
    }

    /** Zone ids mean nothing to the next server, as when a player opens another world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
        BURNING.clear();
    }
}
