package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An ambient that changes while a zone is beside it (ENV-006). The zone is a pinned room with a hole
 * in its roof, so it keeps an ambient edge that always exchanges (D-64), and heat is off, so the edge
 * alone moves it. Each test runs within one game tick and drives the End's atmos ticks itself.
 *
 * <p>The reload test really reloads a datapack, as {@code /reload} does, so it has a batch of its own:
 * no other test should see that reload's definitions.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveAmbientTests {
    private static final String BATCH = "env_live_ambients";
    private static final String RELOAD_BATCH = "env_006_reload";

    /** Chunk-aligned, far from the End's island and from {@code AmbientProviderTests}'s areas. */
    private static final BlockPos AREA = new BlockPos(-16320, 100, 16384);

    private static final double EPS = 1e-9;

    /** What the shipped data gives the End under Normal (ENV-002). */
    private static final double SHIPPED = 293.15;

    private static final double RELOADED = 400;
    private static final String PACK = "zas_gametest_env_006";

    private LiveAmbientTests() {}

    private static ServerLevel end(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        return end;
    }

    /** Builds the room, zones it, pins it and opens its roof. */
    private static Rooms breachedRoom(GameTestHelper h, ServerLevel end, String pinOwner) {
        Rooms r = new Rooms(h, end, AREA);
        r.clear(0, 5);
        r.shell(0, 4);
        PinResult pin = r.settle(ZonePins.pin(end, r.point(2, 2, 2), Rooms.source(pinOwner)));
        h.assertValueEqual(pin.outcome(), PinOutcome.ZONE, "the pin");
        r.set(2, 4, 2, Rooms.AIR);
        r.ticks(2);
        h.assertTrue(r.zone(2, 2, 2).breached(), "the room isn't breached");
        return r;
    }

    private static double temperature(Rooms r) {
        return r.zone(2, 2, 2).mixture().temperature();
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * ENV-006's plan: a datapack reload changes an ambient's temperature, and a zone beside it moves
     * toward the new value from the next atmos tick, not before. Another reload takes it back the
     * same way.
     */
    @GameTest(template = "empty", batch = RELOAD_BATCH, timeoutTicks = 100)
    public static void env_006_a_reloaded_datapack_moves_a_zone_from_the_next_atmos_tick(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel end = end(h);
        PackRepository packs = server.getPackRepository();
        List<String> selected = new ArrayList<>(packs.getSelectedIds());
        Path pack = writePack(server);
        Rooms.withoutHeat(() -> {
            Rooms r = breachedRoom(h, end, "env_006_reload");
            try {
                close(h, Ambients.ambient(Level.END).temperature(), SHIPPED, "the shipped End");
                close(h, temperature(r), SHIPPED, "the room before the reload");

                packs.reload();
                List<String> withPack = new ArrayList<>(selected);
                withPack.add("file/" + PACK);
                server.reloadResources(withPack).join();
                close(h, Ambients.ambient(Level.END).temperature(), RELOADED, "the End after the reload");
                close(h, Atmospheres.at(end, r.abs(5, 2, 2)).temperature(), RELOADED, "the open air");
                close(h, temperature(r), SHIPPED, "the room before an atmos tick");

                r.tick();
                double warmer = temperature(r);
                h.assertTrue(warmer > SHIPPED + 0.01 && warmer < RELOADED, "after one atmos tick: " + warmer);
                r.tick();
                h.assertTrue(temperature(r) > warmer, "the room stopped warming: " + temperature(r));

                server.reloadResources(selected).join();
                close(h, Ambients.ambient(Level.END).temperature(), SHIPPED, "the End reloaded back");
                double hottest = temperature(r);
                r.tick();
                h.assertTrue(temperature(r) < hottest, "the room didn't cool back: " + temperature(r));
            } finally {
                if (packs.getSelectedIds().contains("file/" + PACK)) {
                    server.reloadResources(selected).join();
                }
                deleteRecursively(pack);
                packs.reload();
                r.settle(ZonePins.unpin(end, Rooms.source("env_006_reload")));
            }
        });
        h.succeed();
    }

    /** A pack holding a definition of the End, at {@link #RELOADED} under Normal. */
    private static Path writePack(MinecraftServer server) {
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        try {
            Path ambient = pack.resolve("data/minecraft/zas/ambient");
            Files.createDirectories(ambient);
            Files.writeString(
                    pack.resolve("pack.mcmeta"),
                    "{\"pack\": {\"pack_format\": 48, \"description\": \"ENV-006 test\"}}");
            Files.writeString(
                    ambient.resolve("the_end.json"),
                    "{\"partial_pressures\": {\"zas:nitrogen\": 80.04675, \"zas:oxygen\": 21.27825},"
                            + " \"temperature\": " + RELOADED + "}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return pack;
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * ENV-006: a provider's new answer reaches queries and a zone beside it from the next atmos tick,
     * even one that changes every tick, like a planet's day and night.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_006_a_providers_changing_answer_moves_a_zone_every_atmos_tick(GameTestHelper h) {
        ServerLevel end = end(h);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("zas_gametest", "env_006_day");
        double[] answer = {SHIPPED};
        Ambients.overrideDefinitionForTest(Level.END, DimensionAmbient.NONE);
        AmbientProviders.register(id, Level.END, level -> AmbientDefinition.standardAir(answer[0]));
        try {
            Rooms.withoutHeat(() -> {
                Rooms r = breachedRoom(h, end, "env_006_day");
                double last = temperature(r);
                close(h, last, SHIPPED, "the room at the start");
                for (int tick = 1; tick <= 5; tick++) {
                    answer[0] = SHIPPED + 40 * tick;
                    close(h, Ambients.ambient(Level.END).temperature(), answer[0] - 40, "before the atmos tick");
                    r.tick();
                    close(h, Ambients.ambient(Level.END).temperature(), answer[0], "after the atmos tick");
                    double now = temperature(r);
                    h.assertTrue(now > last && now < answer[0], "tick " + tick + ": the room is at " + now);
                    last = now;
                }
                // Night falls: the same edge cools it.
                answer[0] = 200;
                r.tick();
                h.assertTrue(temperature(r) < last, "the room didn't cool: " + temperature(r));
                r.settle(ZonePins.unpin(end, Rooms.source("env_006_day")));
            });
        } finally {
            AmbientProviders.unregister(id, Level.END);
            Ambients.overrideDefinitionForTest(Level.END, null);
            Rooms.service().tickNow(end);
        }
        h.succeed();
    }
}
