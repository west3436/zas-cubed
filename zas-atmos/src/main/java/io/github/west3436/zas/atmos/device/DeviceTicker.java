package io.github.west3436.zas.atmos.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Steps the loaded {@link AtmosDevice}s of each level once per atmos tick, spread across game
 * ticks (D-31, CONT-006, architecture §11.2). It runs at the end of every server tick, on the
 * server thread, after the framework's own tick, so mutations the framework completed in a game
 * tick are seen by devices stepping in that tick.
 *
 * <p>Every method is for the server thread only.
 */
public final class DeviceTicker {
    private static final Map<ResourceKey<Level>, DeviceSchedule<AtmosDevice>> SCHEDULES = new LinkedHashMap<>();

    private DeviceTicker() {}

    /** Registers the tick and lifecycle listeners. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        gameBus.addListener((ServerStoppedEvent e) -> SCHEDULES.clear());
        gameBus.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                SCHEDULES.remove(level.dimension());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            DeviceSchedule<AtmosDevice> schedule = SCHEDULES.get(level.dimension());
            if (schedule != null) {
                schedule.tick(now);
            }
        }
    }

    static void add(ServerLevel level, AtmosDevice device) {
        SCHEDULES
                .computeIfAbsent(level.dimension(), k -> new DeviceSchedule<>(AtmosTicks.PERIOD))
                .add(device, AtmosTicks.now(level));
    }

    static void remove(ServerLevel level, AtmosDevice device) {
        DeviceSchedule<AtmosDevice> schedule = SCHEDULES.get(level.dimension());
        if (schedule != null) {
            schedule.remove(device);
        }
    }

    /**
     * The devices loaded in a level, in the order they joined. An area air controller looks here
     * for the parts it powers (architecture §11.3).
     *
     * @param level the level
     * @return a new list; empty if the level has no devices
     */
    public static List<AtmosDevice> loaded(ServerLevel level) {
        DeviceSchedule<AtmosDevice> schedule = SCHEDULES.get(level.dimension());
        return schedule == null ? List.of() : schedule.members();
    }

    /**
     * Whether a device is in its level's schedule.
     *
     * @param device the device
     * @return true while it is loaded on the server
     */
    public static boolean isScheduled(AtmosDevice device) {
        if (!(device.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        DeviceSchedule<AtmosDevice> schedule = SCHEDULES.get(level.dimension());
        return schedule != null && schedule.contains(device);
    }

    /**
     * Steps every ready device of a level once now, without changing when each is next due. For
     * GameTests, which drive atmos ticks themselves, and only those.
     *
     * @param level the level
     */
    public static void stepNow(ServerLevel level) {
        DeviceSchedule<AtmosDevice> schedule = SCHEDULES.get(level.dimension());
        if (schedule != null) {
            schedule.stepAll();
        }
    }
}
