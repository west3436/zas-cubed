package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.accessor.NorthstarLevel;
import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZoneRelocatedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Tells Northstar's blocks when a zone changes, so a torch in a room goes out when the room is
 * breached and a furnace lights again when it is sealed (SPACE-011). Northstar only looks at a
 * block's oxygen and temperature again when told to, through {@code enqueueUpdates}; each zone
 * lifecycle event tells it of every block of the zones it names.
 *
 * <p>A zone's blocks come from the zones ZAS³ last published, which the events follow. A zone that
 * is gone, dissolved or absorbed into another, is no longer there to ask, so the blocks of each zone
 * are remembered from the last time it was asked about.
 *
 * <p>A zone's oxygen can cross the line between breathable and not with no lifecycle event, as a
 * room filled with air after it was sealed does, or one whose air is used up. So once a second the
 * zones known are looked at again, and the blocks of each that crossed it are told too. A zone no
 * event has named, such as one loaded with its level, becomes known then. Server thread.
 */
final class ZoneUpdates {
    /** How often the zones known are looked at for oxygen, in ticks. */
    static final int CHECK_TICKS = 20;

    /** Each zone when last asked about, by level and id. */
    private static final Map<ResourceKey<Level>, Map<Long, Known>> KNOWN = new HashMap<>();

    /**
     * A zone when last asked about.
     *
     * @param blocks its blocks, as {@link BlockPos#asLong} longs
     * @param breathable whether its oxygen was enough to breathe
     */
    private record Known(long[] blocks, boolean breathable) {}

    private ZoneUpdates() {}

    /** Forgets every level's zones. */
    static void serverStopped() {
        KNOWN.clear();
    }

    /** Forgets a level's zones. */
    static void unloaded(ServerLevel level) {
        KNOWN.remove(level.dimension());
    }

    /** For each zone lifecycle event. */
    static void changed(ZoneLifecycleEvent e) {
        ServerLevel level = e.level();
        LongOpenHashSet blocks = new LongOpenHashSet();
        update(level, e.zone(), blocks);
        if (e instanceof ZoneMergedEvent merged) {
            update(level, merged.absorbed(), blocks);
        } else if (e instanceof ZoneSplitEvent split) {
            update(level, split.remnant(), blocks);
        } else if (e instanceof ZoneRelocatedEvent moved) {
            ServerLevel from = level.getServer().getLevel(moved.fromDimension());
            if (from != null) {
                LongOpenHashSet left = new LongOpenHashSet();
                update(from, moved.from(), left);
                tell(from, left);
            }
        }
        tell(level, blocks);
    }

    /** Adds a zone's blocks, now and when last asked about, and remembers those it has now. */
    private static void update(ServerLevel level, ZoneId zone, LongOpenHashSet into) {
        Map<Long, Known> known = KNOWN.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        Known before = known.remove(zone.value());
        if (before != null) {
            into.addAll(LongArrayList.wrap(before.blocks));
        }
        long[] now = blocksOf(level, zone.value());
        if (now.length > 0) {
            known.put(zone.value(), new Known(now, breathable(level, zone)));
            into.addAll(LongArrayList.wrap(now));
        }
    }

    /** A zone's blocks in what ZAS³ last published; none before the level's first publish. */
    static long[] blocksOf(ServerLevel level, long zone) {
        AtmosSnapshot snapshot = published(level);
        if (snapshot == null) {
            return new long[0];
        }
        LongOpenHashSet blocks = new LongOpenHashSet();
        for (long chunk : snapshot.zoneChunks(zone)) {
            for (ZoneShare share : snapshot.shares(chunk)) {
                if (share.zoneId() != zone) {
                    continue;
                }
                for (long cell : share.cells()) {
                    blocks.add(BlockPos.asLong(CellIds.x(cell), CellIds.y(cell), CellIds.z(cell)));
                }
            }
        }
        return blocks.toLongArray();
    }

    /**
     * Once every {@link #CHECK_TICKS}, tells Northstar of the blocks of each zone known whose oxygen
     * crossed the line between breathable and not. Server thread, each server tick.
     */
    static void tick(ServerLevel level) {
        if (level.getGameTime() % CHECK_TICKS == 0) {
            check(level);
        }
    }

    /** Tells Northstar of the blocks of each zone known whose oxygen crossed the line, now. */
    static void check(ServerLevel level) {
        AtmosSnapshot snapshot = published(level);
        if (snapshot == null) {
            return;
        }
        Map<Long, Known> known = KNOWN.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        for (ZoneView zone : snapshot.zones()) {
            if (!known.containsKey(zone.id())) {
                // A zone from before, such as one loaded with the level, that no event named yet.
                long[] now = blocksOf(level, zone.id());
                if (now.length > 0) {
                    known.put(zone.id(), new Known(now, breathable(level, new ZoneId(zone.id()))));
                }
            }
        }
        if (known.isEmpty()) {
            return;
        }
        LongOpenHashSet blocks = new LongOpenHashSet();
        for (Map.Entry<Long, Known> e : known.entrySet()) {
            boolean now = breathable(level, new ZoneId(e.getKey()));
            if (now != e.getValue().breathable) {
                e.setValue(new Known(e.getValue().blocks, now));
                blocks.addAll(LongArrayList.wrap(e.getValue().blocks));
            }
        }
        tell(level, blocks);
    }

    /** What ZAS³ last published for a level, or null if nothing yet. */
    private static AtmosSnapshot published(ServerLevel level) {
        AtmosService service = AtmosService.current();
        DimensionAtmos dimension = service == null ? null : service.dimension(level);
        return dimension == null ? null : dimension.published();
    }

    /** Whether a zone holds enough oxygen to breathe. */
    private static boolean breathable(ServerLevel level, ZoneId zone) {
        return Atmospheres.zone(level, zone)
                .map(info -> info.mixture().partialPressure(StandardAir.OXYGEN) >= Breathing.oxygenMinimum())
                .orElse(false);
    }

    /**
     * Queues the blocks for Northstar, with the blocks beside them: a furnace or engine in a room's
     * wall is not one of its cells, but answers to it through a face.
     */
    private static void tell(ServerLevel level, LongOpenHashSet blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        LongOpenHashSet all = new LongOpenHashSet(blocks);
        for (long block : blocks) {
            for (Direction d : Direction.values()) {
                all.add(BlockPos.offset(block, d));
            }
        }
        NorthstarOxygen.getDimension(level).enqueueUpdates(all);
        ((NorthstarLevel) level).northstar$temperature().enqueueUpdates(all);
    }
}
