package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.api.relocation.Relocations;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tells players near a working device how hard it is working (DATA-018), so their clients can play
 * its loop and show its particles. It stays within D-53: a level per device, and nothing of a zone.
 *
 * <p>Each level holds its devices that aren't {@linkplain DeviceActivity#IDLE idle}, and each player
 * what it was last told. Every {@value #SEND_TICKS} game ticks, at a phase given by its entity id, a
 * player is sent what differs, so a sound starts and stops close to its device's step: a device within
 * {@value #RANGE} blocks that is new or has changed, and an idle or removed one it knew of. One that
 * it knew of stays listed out to {@value #FORGET_RANGE} blocks, so a
 * player walking along the edge doesn't keep being told. Nothing changed means nothing is sent, so a
 * station that has settled costs nothing, however big it is. Distances are to where the world shows a
 * device (API-068), so a player on a Sable ship is told of the devices on board, not of those in the
 * ship's far-off plot.
 *
 * <p>A device's level is a lease too. A device that hasn't finished a step for {@value #STALE_TICKS}
 * game ticks reads as idle, whatever it last reported, so one whose chunk has left full status, or
 * whose port is stuck waiting, can't leave a loop running.
 *
 * <p>Server thread only.
 */
public final class DeviceActivitySync {
    /** How near a device is before a player is told of it, in blocks: the range of airflow's sync. */
    public static final int RANGE = 32;

    /** How far away a device a player knows of is before it is told to forget it, in blocks. */
    public static final int FORGET_RANGE = 40;

    /** How often a player is sent what has changed, in game ticks. */
    public static final int SEND_TICKS = 4;

    /** How long a device's level lasts without another step, in game ticks. */
    public static final int STALE_TICKS = 3 * AtmosTicks.PERIOD;

    /** A working device: its level, and the game tick of its last step. */
    private static final class Working {
        int level;
        long step;
    }

    /** What a player was last told. */
    private static final class Told {
        final Long2ByteOpenHashMap known = new Long2ByteOpenHashMap();
        ResourceKey<Level> dimension;
        long sent = -1;
    }

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Working>> WORKING = new HashMap<>();
    private static final Map<UUID, Told> TOLD = new HashMap<>();

    private DeviceActivitySync() {}

    /** Registers the tick and lifecycle listeners. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        gameBus.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
                TOLD.remove(e.getEntity().getUUID()));
        // A new dimension is a new level on the client, whose table starts empty.
        gameBus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) ->
                forgetKnown(e.getEntity().getUUID()));
        gameBus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                WORKING.remove(level.dimension());
            }
        });
        gameBus.addListener((ServerStoppedEvent e) -> {
            WORKING.clear();
            TOLD.clear();
        });
    }

    private static void forgetKnown(UUID player) {
        Told told = TOLD.get(player);
        if (told != null) {
            told.known.clear();
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (Math.floorMod(now, AtmosTicks.PERIOD) == 0) {
            WORKING.values().forEach(working -> sweep(working, now));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(now + player.getId(), SEND_TICKS) == 0) {
                syncAt(player, now, payload -> send(player, payload));
            }
        }
    }

    /** Lets go of devices that have stopped stepping. */
    private static void sweep(Long2ObjectOpenHashMap<Working> working, long now) {
        ObjectIterator<Long2ObjectMap.Entry<Working>> it =
                working.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            if (stale(it.next().getValue(), now)) {
                it.remove();
            }
        }
    }

    private static boolean stale(Working w, long now) {
        return now - w.step > STALE_TICKS;
    }

    /**
     * A device's level after a step. An idle device isn't listed, so most devices cost nothing here.
     *
     * @param now the game tick, as {@link AtmosTicks#now}
     */
    static void report(ServerLevel level, BlockPos pos, int activity, long now) {
        if (activity == DeviceActivity.IDLE) {
            remove(level, pos);
            return;
        }
        Working w = WORKING.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(pos.asLong(), p -> new Working());
        w.level = activity;
        w.step = now;
    }

    /**
     * For GameTests: a device's level after a step, as {@link AtmosDevice} reports it, for a block
     * the test doesn't step, such as one on a Sable ship. {@link DeviceActivity#IDLE} forgets it.
     *
     * @param now the game tick, as {@link AtmosTicks#now}
     */
    public static void reportForTest(ServerLevel level, BlockPos pos, int activity, long now) {
        report(level, pos, activity, now);
    }

    /** A device has gone: removed, or its chunk unloaded. */
    static void remove(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Working> working = WORKING.get(level.dimension());
        if (working != null) {
            working.remove(pos.asLong());
        }
    }

    /**
     * Passes a player what it needs to know at game tick {@code now} to {@code out}, if there is
     * anything and it is due: at least {@value #SEND_TICKS} ticks have passed since the last.
     *
     * @return the payload passed on, or null if none was
     */
    public static @Nullable DeviceActivityPayload syncAt(
            ServerPlayer player, long now, Consumer<DeviceActivityPayload> out) {
        Told told = TOLD.computeIfAbsent(player.getUUID(), id -> new Told());
        ResourceKey<Level> dimension = player.serverLevel().dimension();
        if (told.dimension != dimension) {
            told.known.clear();
            told.dimension = dimension;
        }
        if (told.sent >= 0 && now - told.sent < SEND_TICKS) {
            return null;
        }
        Long2ObjectOpenHashMap<Working> working = WORKING.get(dimension);
        if (told.known.isEmpty() && (working == null || working.isEmpty())) {
            return null;
        }
        List<DeviceActivityPayload.Change> changes = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        forgetGone(level, told, working, at, now, changes);
        if (working != null) {
            tellChanged(level, told, working, at, now, changes);
        }
        if (changes.isEmpty()) {
            return null;
        }
        told.sent = now;
        DeviceActivityPayload payload = new DeviceActivityPayload(changes);
        out.accept(payload);
        return payload;
    }

    /** First, so a loop that should stop is never held up by others starting. */
    private static void forgetGone(
            Level level,
            Told told,
            @Nullable Long2ObjectOpenHashMap<Working> working,
            Vec3 at,
            long now,
            List<DeviceActivityPayload.Change> changes) {
        ObjectIterator<Long2ByteMap.Entry> it = told.known.long2ByteEntrySet().fastIterator();
        while (it.hasNext() && changes.size() < DeviceActivityPayload.MAX_CHANGES) {
            long pos = it.next().getLongKey();
            Working w = working == null ? null : working.get(pos);
            if (w == null || stale(w, now) || !within(level, pos, at, FORGET_RANGE)) {
                changes.add(new DeviceActivityPayload.Change(pos, DeviceActivity.IDLE));
                it.remove();
            }
        }
    }

    private static void tellChanged(
            Level level,
            Told told,
            Long2ObjectOpenHashMap<Working> working,
            Vec3 at,
            long now,
            List<DeviceActivityPayload.Change> changes) {
        ObjectIterator<Long2ObjectMap.Entry<Working>> it =
                working.long2ObjectEntrySet().fastIterator();
        while (it.hasNext() && changes.size() < DeviceActivityPayload.MAX_CHANGES) {
            Long2ObjectMap.Entry<Working> e = it.next();
            long pos = e.getLongKey();
            Working w = e.getValue();
            byte known = told.known.get(pos);
            if (known != w.level && !stale(w, now) && (known != 0 || within(level, pos, at, RANGE))) {
                changes.add(new DeviceActivityPayload.Change(pos, w.level));
                told.known.put(pos, (byte) w.level);
            }
        }
    }

    /** Whether a device's middle, where the world shows it, is within a range of a point. */
    private static boolean within(Level level, long pos, Vec3 at, int range) {
        Vec3 middle = new Vec3(BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5, BlockPos.getZ(pos) + 0.5);
        return Relocations.shownAt(level, middle).distanceToSqr(at) <= range * range;
    }

    private static void send(ServerPlayer player, DeviceActivityPayload payload) {
        if (player.connection.hasChannel(DeviceActivityPayload.TYPE)) {
            player.connection.send(payload);
        }
    }
}
