package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Where the run's scripts and its tests meet. The scripts are a pack's: files in the game
 * directory's {@code kubejs/server_scripts}, which KubeJS loads as the server starts. They load
 * this class with {@code Java.loadClass}, tell it what they were given, and hand it functions for a
 * test to call. Every public method is one a script calls, and its parameter types are what the
 * script's values must convert to, so a value of the wrong kind fails in the script.
 */
public final class Probe {
    private Probe() {}

    /** An event as a script's listener was given it. */
    record Heard(
            String name,
            ServerLevel level,
            @Nullable ZoneId zone,
            BlockPos pos,
            @Nullable Object more,
            boolean serverThread) {}

    /** What an open-air event carried besides its level and position. */
    record OpenAir(boolean added, GasParcel gas, SourceId source) {}

    /** A call to a script's hazard handler. */
    record Exposed(String handler, HazardExposure exposure, boolean serverThread) {}

    /** A function of a script's for a test to call: it calls ZAS³ with what it is given. */
    @FunctionalInterface
    public interface Task {
        /**
         * @param level a level
         * @param inside a block inside a room of the test's
         * @param open a block in the open air
         * @param entity an entity in the room
         */
        void run(ServerLevel level, BlockPos inside, BlockPos open, LivingEntity entity);
    }

    private static final List<Heard> HEARD = new CopyOnWriteArrayList<>();
    private static final List<Exposed> EXPOSED = new CopyOnWriteArrayList<>();
    private static final List<String> ASKED = new CopyOnWriteArrayList<>();
    private static final Map<String, Object> NOTES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LOADS = new ConcurrentHashMap<>();
    private static final Map<String, String> MODES = new ConcurrentHashMap<>();
    private static final Map<String, Task> TASKS = new ConcurrentHashMap<>();

    // ---- For scripts -------------------------------------------------------------------

    /** A script's last line: it ran to its end, without an error. */
    public static void loaded(String script) {
        LOADS.merge(script, 1, Integer::sum);
    }

    /** One of the zone events that carry a zone and a position. */
    public static void zone(String name, ServerLevel level, ZoneId zone, BlockPos pos, MinecraftServer server) {
        if (server != level.getServer()) {
            throw new IllegalArgumentException("the event's server isn't its level's");
        }
        hear(name, level, zone, pos, null);
    }

    /** {@code zoneMerged}, with the zone absorbed. */
    public static void merged(ServerLevel level, ZoneId zone, BlockPos pos, ZoneId absorbed) {
        hear("zoneMerged", level, zone, pos, absorbed);
    }

    /** {@code zoneSplit}, with the zone split off. */
    public static void split(ServerLevel level, ZoneId zone, BlockPos pos, ZoneId remnant) {
        hear("zoneSplit", level, zone, pos, remnant);
    }

    /** {@code fireIgnited} or {@code fireExtinguished}, with its hotspot. */
    public static void fire(String name, ServerLevel level, ZoneId zone, BlockPos hotspot) {
        hear(name, level, zone, hotspot, null);
    }

    /** {@code openAirGas}. */
    public static void openAir(ServerLevel level, BlockPos pos, boolean added, GasParcel gas, SourceId source) {
        hear("openAirGas", level, null, pos, new OpenAir(added, gas, source));
    }

    private static void hear(String name, ServerLevel level, @Nullable ZoneId zone, BlockPos pos, Object more) {
        HEARD.add(new Heard(
                name, level, zone, pos.immutable(), more, level.getServer().isSameThread()));
    }

    /** Something a script found out, under a name of its own. */
    public static void note(String key, @Nullable Object value) {
        NOTES.put(key, value == null ? "null" : value);
    }

    /** A call to a script's hazard handler. */
    public static void exposed(String handler, HazardExposure exposure) {
        EXPOSED.add(new Exposed(
                handler, exposure, exposure.entity().level().getServer().isSameThread()));
    }

    /** A call to a script's ambient provider. */
    public static void asked(String provider, ServerLevel level, ResourceLocation dimension) {
        if (!level.dimension().location().equals(dimension)) {
            throw new IllegalArgumentException(provider + " was asked about " + level.dimension());
        }
        ASKED.add(provider);
    }

    /** What a test asks of a script just now, such as that a handler throw; "" if nothing. */
    public static String mode(String what) {
        return MODES.getOrDefault(what, "");
    }

    /** Hands a test a function to call. */
    public static void offer(String name, Task task) {
        TASKS.put(name, task);
    }

    // ---- For tests ---------------------------------------------------------------------

    /** The events scripts heard in a test's area, all of them on the server thread. */
    static List<Heard> heard(Lab lab, String name) {
        List<Heard> here = HEARD.stream()
                .filter(e -> e.name().equals(name) && e.level() == lab.level && lab.holds(e.pos()))
                .toList();
        for (Heard e : here) {
            lab.h.assertTrue(e.serverThread(), name + " reached the script off the server thread");
        }
        return here;
    }

    /** The one time scripts heard an event in a test's area. */
    static Heard one(Lab lab, String name) {
        List<Heard> here = heard(lab, name);
        lab.h.assertValueEqual(here.size(), 1, "times a script heard " + name);
        return here.get(0);
    }

    /** The calls to a script's hazard handler for an entity. */
    static List<Exposed> exposed(String handler, LivingEntity entity) {
        return EXPOSED.stream()
                .filter(e -> e.handler().equals(handler) && e.exposure().entity() == entity)
                .toList();
    }

    /** How often a script's ambient provider has been asked. */
    static int asked(String provider) {
        return (int) ASKED.stream().filter(provider::equals).count();
    }

    /** What a script noted under a name. */
    static Object noted(String key) {
        Object value = NOTES.get(key);
        if (value == null) {
            throw new GameTestAssertException("no script noted " + key + "; noted: " + NOTES.keySet());
        }
        return value;
    }

    /** A number a script noted under a name. */
    static double number(String key) {
        if (noted(key) instanceof Number n) {
            return n.doubleValue();
        }
        throw new GameTestAssertException(key + " isn't a number: " + noted(key));
    }

    static void forget(String prefix) {
        NOTES.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** How often a script has run to its end: once for each time server scripts loaded. */
    static int loads(String script) {
        return LOADS.getOrDefault(script, 0);
    }

    /** Asks something of a script until told otherwise. */
    static void mode(String what, String mode) {
        MODES.put(what, mode);
    }

    /** A function a script handed over. */
    static Task task(String name) {
        Task task = TASKS.get(name);
        if (task == null) {
            throw new GameTestAssertException("no script offered " + name + "; offered: " + TASKS.keySet());
        }
        return task;
    }
}
