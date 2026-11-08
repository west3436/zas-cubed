package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

/**
 * The powered parts of each level by the zone their air point is in, for the area air controllers
 * that cover them (CONT-042, architecture §11.6). Asking every loaded part's zone at every
 * controller's step took most of the S-10 bench's device time, so each part's zone is kept here and
 * asked again only when it may have changed:
 *
 * <ul>
 *   <li>a part's, when it joins its level's schedule, and when its block changes in place, as when it
 *       is turned;
 *   <li>the parts in each zone a lifecycle event names, and those in none, after a zone of its
 *       level is created, merged, split, breached, sealed, dissolved, paused or resumed (API-030);
 *       an event names every zone whose cells it changes, both of them for a merge or a split;
 *   <li>the parts in a zone and those in none, when a controller finds that zone's cell count has
 *       changed since it last looked: a zone that grows into a room that had none, or loses a cell,
 *       fires no event;
 *   <li>a part whose air read as unresolved, at every lookup while its chunk is loaded in full, as
 *       when a loaded chunk's saved zones are still on their way;
 *   <li>a part that has stepped since the last lookup, which bounds anything else to one atmos tick.
 * </ul>
 *
 * <p>Server thread only.
 */
public final class ZoneIndex {
    private static final Map<ResourceKey<Level>, ZoneIndex> LEVELS = new HashMap<>();

    /** A part and its zone as last asked. */
    private static final class Entry {
        final PoweredDevice part;
        final BlockPos pos;
        final long order;

        @Nullable
        ZoneId zone;

        Entry(PoweredDevice part, BlockPos pos, long order) {
            this.part = part;
            this.pos = pos;
            this.order = order;
        }
    }

    private final Map<PoweredDevice, Entry> entries = new HashMap<>();
    private final Map<BlockPos, Entry> byPos = new HashMap<>();
    private final Map<ZoneId, Set<Entry>> byZone = new HashMap<>();
    private final Set<Entry> unzoned = new HashSet<>();
    private final Set<Entry> stale = new HashSet<>();
    private final Set<Entry> unresolved = new HashSet<>();
    /** Each zone's cell count when a controller last looked it up. */
    private final Map<ZoneId, Integer> cells = new HashMap<>();

    /** The zones lifecycle events have named since the last lookup. */
    private final Set<ZoneId> changed = new HashSet<>();

    private long joined;

    private ZoneIndex() {}

    /** Registers the zone event listeners. Called once, from {@link DeviceTicker#register}. */
    static void register(IEventBus gameBus) {
        gameBus.addListener((ZoneCreatedEvent e) -> zonesChanged(e));
        gameBus.addListener((ZoneMergedEvent e) -> zonesChanged(e, e.absorbed()));
        gameBus.addListener((ZoneSplitEvent e) -> zonesChanged(e, e.remnant()));
        gameBus.addListener((ZoneBreachedEvent e) -> zonesChanged(e));
        gameBus.addListener((ZoneSealedEvent e) -> zonesChanged(e));
        gameBus.addListener((ZoneDissolvedEvent e) -> zonesChanged(e));
        gameBus.addListener((ZonePausedEvent e) -> zonesChanged(e));
        gameBus.addListener((ZoneResumedEvent e) -> zonesChanged(e));
    }

    /** Notes the zones a lifecycle event names: its own, and the other a merge or a split names. */
    private static void zonesChanged(ZoneLifecycleEvent e, ZoneId... others) {
        ZoneIndex index = LEVELS.get(e.level().dimension());
        if (index != null) {
            index.changed.add(e.zone());
            index.changed.addAll(List.of(others));
        }
    }

    /** Adds a device that has joined its level's schedule, if it is a powered part. */
    static void join(ServerLevel level, AtmosDevice device) {
        if (device instanceof PoweredDevice part) {
            LEVELS.computeIfAbsent(level.dimension(), k -> new ZoneIndex()).add(part, device.getBlockPos());
        }
    }

    /** Drops a device that has left its level's schedule. */
    static void leave(ServerLevel level, AtmosDevice device) {
        ZoneIndex index = LEVELS.get(level.dimension());
        if (index != null && device instanceof PoweredDevice part) {
            index.drop(part);
        }
    }

    /** Has a part's zone asked again at the next lookup: it has stepped, or its block has changed. */
    static void mark(ServerLevel level, AtmosDevice device) {
        ZoneIndex index = LEVELS.get(level.dimension());
        if (index != null && device instanceof PoweredDevice part) {
            Entry e = index.entries.get(part);
            if (e != null) {
                index.stale.add(e);
            }
        }
    }

    /** Forgets a level, when it unloads. */
    static void forget(ResourceKey<Level> level) {
        LEVELS.remove(level);
    }

    /** Forgets every level, when the server stops. */
    static void clear() {
        LEVELS.clear();
    }

    /**
     * The parts an area air controller covers (CONT-042): the loaded parts whose air point is in its
     * zone, and those loaded at the positions linked to it, in the order they joined the schedule, as
     * asking each loaded part now would find them.
     *
     * @param level the level
     * @param zone the controller's zone, empty if it has none
     * @param links the positions linked to it
     * @return a new list
     */
    public static List<PoweredDevice> covered(ServerLevel level, Optional<ZoneId> zone, Collection<BlockPos> links) {
        ZoneIndex index = LEVELS.get(level.dimension());
        if (index == null) {
            return new ArrayList<>();
        }
        ZoneId z = zone.orElse(null);
        index.refresh(level, z);
        List<Entry> found = new ArrayList<>();
        if (z != null) {
            found.addAll(index.byZone.getOrDefault(z, Set.of()));
        }
        for (BlockPos p : links) {
            Entry e = index.byPos.get(p);
            if (e != null && (z == null || !z.equals(e.zone))) {
                found.add(e);
            }
        }
        found.sort(Comparator.comparingLong(e -> e.order));
        List<PoweredDevice> out = new ArrayList<>(found.size());
        for (Entry e : found) {
            out.add(e.part);
        }
        return out;
    }

    /**
     * Whether a part's air point was in a zone at the last lookup, which {@link #covered} has just
     * made.
     */
    public static boolean inZone(ServerLevel level, PoweredDevice part, ZoneId zone) {
        ZoneIndex index = LEVELS.get(level.dimension());
        Entry e = index == null ? null : index.entries.get(part);
        return e != null && zone.equals(e.zone);
    }

    private void add(PoweredDevice part, BlockPos pos) {
        if (entries.containsKey(part)) {
            return;
        }
        Entry e = new Entry(part, pos.immutable(), joined++);
        entries.put(part, e);
        byPos.put(e.pos, e);
        unzoned.add(e);
        stale.add(e);
    }

    private void drop(PoweredDevice part) {
        Entry e = entries.remove(part);
        if (e == null) {
            return;
        }
        byPos.remove(e.pos, e);
        unfile(e);
        stale.remove(e);
        unresolved.remove(e);
    }

    /** Asks again every part whose zone may have changed since the last lookup. */
    private void refresh(ServerLevel level, @Nullable ZoneId zone) {
        if (!changed.isEmpty()) {
            for (ZoneId z : changed) {
                stale.addAll(byZone.getOrDefault(z, Set.of()));
                cells.remove(z);
            }
            stale.addAll(unzoned);
            changed.clear();
        }
        if (zone != null) {
            int now = cellCount(level, zone);
            Integer was = cells.put(zone, now);
            if (was == null || was != now) {
                stale.addAll(byZone.getOrDefault(zone, Set.of()));
                stale.addAll(unzoned);
            }
        }
        for (Entry e : unresolved) {
            if (LoadedBlocks.chunk(level, e.pos) != null) {
                stale.add(e);
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        List<Entry> ask = new ArrayList<>(stale);
        stale.clear();
        for (Entry e : ask) {
            AtmosphereSample s = Atmospheres.at(level, e.part.airPoint());
            file(e, s.zone().orElse(null));
            if (s.state() == AtmosphereState.UNRESOLVED) {
                unresolved.add(e);
            } else {
                unresolved.remove(e);
            }
        }
    }

    private static int cellCount(ServerLevel level, ZoneId zone) {
        return Atmospheres.zone(level, zone).map(ZoneInfo::cellCount).orElse(0);
    }

    private void file(Entry e, @Nullable ZoneId zone) {
        if (Objects.equals(e.zone, zone)) {
            return;
        }
        unfile(e);
        e.zone = zone;
        if (zone == null) {
            unzoned.add(e);
        } else {
            byZone.computeIfAbsent(zone, k -> new HashSet<>()).add(e);
        }
    }

    private void unfile(Entry e) {
        if (e.zone == null) {
            unzoned.remove(e);
            return;
        }
        Set<Entry> in = byZone.get(e.zone);
        if (in != null) {
            in.remove(e);
            if (in.isEmpty()) {
                byZone.remove(e.zone);
            }
        }
    }
}
