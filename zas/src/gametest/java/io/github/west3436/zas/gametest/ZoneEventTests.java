package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Each change in a zone's life fires its event once, through {@code ApiEvents} and on the server
 * thread (API-030, API-032). The ways to reach each one are {@code ZoneEventTest}, in the engine.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneEventTests {
    private static final String BATCH = "zone_events";

    /** An event as its listener saw it. */
    private record Seen(ZoneLifecycleEvent event, boolean serverThread, boolean zoneExists) {}

    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((ZoneCreatedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneMergedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneSplitEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneBreachedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneSealedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneDissolvedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZonePausedEvent e) -> see(e));
        NeoForge.EVENT_BUS.addListener((ZoneResumedEvent e) -> see(e));
    }

    private ZoneEventTests() {}

    private static void see(ZoneLifecycleEvent e) {
        SEEN.add(new Seen(
                e,
                e.level().getServer().isSameThread(),
                Atmospheres.zone(e.level(), e.zone()).isPresent()));
    }

    /** What has fired in a test's area, all of it on the server thread. */
    private static List<Seen> seen(Rooms r) {
        BlockPos min = r.abs(0, 0, 0);
        BlockPos max = r.abs(255, 255, 255);
        List<Seen> here = SEEN.stream()
                .filter(s -> s.event().level() == r.level)
                .filter(s -> {
                    BlockPos p = s.event().pos();
                    return p.getX() >= min.getX()
                            && p.getX() <= max.getX()
                            && p.getZ() >= min.getZ()
                            && p.getZ() <= max.getZ();
                })
                .toList();
        for (Seen s : here) {
            r.h.assertTrue(s.serverThread(), s.event().getClass().getSimpleName() + " fired off the server thread");
        }
        return here;
    }

    /** How many times an event has fired in a test's area. */
    private static long count(Rooms r, Class<? extends ZoneLifecycleEvent> type) {
        return seen(r).stream().filter(s -> type.isInstance(s.event())).count();
    }

    /** The one time an event has fired in a test's area. */
    private static Seen one(Rooms r, Class<? extends ZoneLifecycleEvent> type) {
        r.h.assertValueEqual(count(r, type), 1L, type.getSimpleName() + " fires");
        return seen(r).stream()
                .filter(s -> type.isInstance(s.event()))
                .findFirst()
                .orElseThrow();
    }

    /** A sealed room with a 3 × 3 × 3 interior, with a zone that doesn't match the ambient. */
    private static ZoneId room(Rooms r, int x0, String test) {
        r.shell(x0, x0 + 4);
        r.settle(GasMutations.add(r.level, r.point(x0 + 2, 2, 2), oxygen(500), source(test)));
        return r.zone(x0 + 2, 2, 2).id();
    }

    /** A zone forming fires created once, and its zone is there for a listener to read. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_zone_forming_fires_created_once(GameTestHelper h) {
        Rooms r = new Rooms(h, 1510);
        r.clear(0, 8);
        ZoneId zone = room(r, 0, "api_030");
        r.ticks(3);

        Seen created = one(r, ZoneCreatedEvent.class);
        h.assertValueEqual(created.event().zone(), zone, "the zone");
        h.assertValueEqual(created.event().level(), r.level, "the level");
        h.assertTrue(created.zoneExists(), "a listener can't read the new zone");
        h.assertValueEqual(
                Atmospheres.zoneAt(r.level, created.event().pos()).orElseThrow().id(), zone, "pos is in the zone");
        h.succeed();
    }

    /** Two rooms joined fire merged once, naming both, and no dissolution for the one absorbed. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_two_zones_becoming_one_fire_merged_once(GameTestHelper h) {
        Rooms r = new Rooms(h, 1511);
        r.clear(0, 12);
        ZoneId west = room(r, 0, "api_030");
        ZoneId east = room(r, 4, "api_030");
        h.assertFalse(west.equals(east), "one room");

        r.set(4, 2, 2, Rooms.AIR);
        r.ticks(3);

        ZoneMergedEvent merged = (ZoneMergedEvent) one(r, ZoneMergedEvent.class).event();
        h.assertValueEqual(merged.zone(), r.zone(2, 2, 2).id(), "the survivor");
        h.assertValueEqual(merged.absorbed(), merged.zone().equals(west) ? east : west, "the one absorbed");
        h.assertTrue(Atmospheres.zone(r.level, merged.absorbed()).isEmpty(), "the absorbed id is gone");
        h.assertValueEqual(count(r, ZoneDissolvedEvent.class), 0L, "dissolved");
        h.assertValueEqual(count(r, ZoneCreatedEvent.class), 2L, "created");
        h.succeed();
    }

    /** A room walled in two fires split once, naming the remnant, which fires no created. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_zone_cut_in_two_fires_split_once(GameTestHelper h) {
        Rooms r = new Rooms(h, 1512);
        r.clear(0, 12);
        r.shell(0, 8);
        r.settle(GasMutations.add(r.level, r.point(2, 2, 2), oxygen(500), source("api_030")));
        ZoneId zone = r.zone(2, 2, 2).id();

        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                r.set(4, y, z, STONE);
            }
        }
        r.ticks(3);

        ZoneSplitEvent split = (ZoneSplitEvent) one(r, ZoneSplitEvent.class).event();
        h.assertValueEqual(split.zone(), zone, "the zone that keeps its id");
        ZoneId west = r.zone(2, 2, 2).id();
        ZoneId east = r.zone(6, 2, 2).id();
        h.assertFalse(west.equals(east), "still one zone");
        h.assertValueEqual(split.remnant(), west.equals(zone) ? east : west, "the remnant");
        h.assertValueEqual(count(r, ZoneCreatedEvent.class), 1L, "created");
        h.succeed();
    }

    /** Opening a pinned room's roof fires breached once, and closing it fires sealed once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_an_opening_fires_breached_and_closing_it_fires_sealed(GameTestHelper h) {
        Rooms r = new Rooms(h, 1513);
        r.clear(0, 8);
        r.shell(0, 4);
        SourceId owner = source("api_030_breach");
        h.assertValueEqual(
                r.settle(ZonePins.pin(r.level, r.point(2, 2, 2), owner)).outcome(), PinOutcome.ZONE, "pin");
        ZoneId zone = r.zone(2, 2, 2).id();
        r.tick();
        h.assertValueEqual(count(r, ZoneBreachedEvent.class), 0L, "breached, sealed");
        h.assertValueEqual(count(r, ZoneSealedEvent.class), 0L, "sealed, sealed");

        r.set(2, 4, 2, Rooms.AIR);
        r.ticks(8); // a classification may take a few
        h.assertValueEqual(one(r, ZoneBreachedEvent.class).event().zone(), zone, "the zone breached");
        h.assertValueEqual(count(r, ZoneSealedEvent.class), 0L, "sealed, open");

        r.set(2, 4, 2, STONE);
        r.ticks(2);
        h.assertValueEqual(one(r, ZoneSealedEvent.class).event().zone(), zone, "the zone sealed");
        h.assertValueEqual(count(r, ZoneBreachedEvent.class), 1L, "breached, once sealed again");

        r.settle(ZonePins.unpin(r.level, owner));
        h.succeed();
    }

    /** A sealed room back at the ambient dissolves after the delay, and fires dissolved once (CORE-052). */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_sealed_zone_going_fires_dissolved_once(GameTestHelper h) {
        Rooms r = new Rooms(h, 1514);
        r.clear(0, 8);
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(2.0);
            ZoneId zone = room(r, 0, "api_030");
            r.settle(GasMutations.remove(r.level, r.point(2, 2, 2), Map.of(O2, 500.0), source("api_030")));
            r.ticks(4);

            Seen dissolved = one(r, ZoneDissolvedEvent.class);
            h.assertValueEqual(dissolved.event().zone(), zone, "the zone");
            h.assertFalse(dissolved.zoneExists(), "a listener can still read the zone");
            h.assertFalse(r.zoned(2, 2, 2), "the room is still zoned");
            BlockPos pos = dissolved.event().pos();
            h.assertTrue(
                    BlockPos.betweenClosedStream(r.abs(1, 1, 1), r.abs(3, 3, 3)).anyMatch(pos::equals),
                    "pos isn't where the zone was: " + pos);
        } finally {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
        }
        h.succeed();
    }

    /** A pinned room at the ambient with an opening goes as soon as its pin does (CORE-076). */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_zone_at_an_opening_going_fires_dissolved_once(GameTestHelper h) {
        Rooms r = new Rooms(h, 1515);
        r.clear(0, 8);
        r.shell(0, 4);
        SourceId owner = source("api_030_vent");
        r.settle(ZonePins.pin(r.level, r.point(2, 2, 2), owner));
        ZoneId zone = r.zone(2, 2, 2).id();
        r.set(2, 4, 2, Rooms.AIR);
        r.ticks(8); // a classification may take a few
        h.assertValueEqual(count(r, ZoneDissolvedEvent.class), 0L, "dissolved, pinned");

        r.settle(ZonePins.unpin(r.level, owner));
        r.ticks(2);

        h.assertValueEqual(one(r, ZoneDissolvedEvent.class).event().zone(), zone, "the zone");
        h.succeed();
    }

    /** Unloading part of a zone fires paused once, and loading it again fires resumed once. */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_zone_part_unloaded_fires_paused_then_resumed(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 1516);
            r.clear(8, 23);
            r.shell(10, 21);
            r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(500), source("api_030")));
            ZoneId zone = r.zone(12, 2, 2).id();
            r.tick();
            h.assertValueEqual(count(r, ZonePausedEvent.class), 0L, "paused, loaded");

            ChunkPos east = r.chunk(16);
            byte[] saved = r.dimension().simulateUnload(east);
            r.ticks(3);
            h.assertValueEqual(one(r, ZonePausedEvent.class).event().zone(), zone, "the zone paused");
            h.assertValueEqual(count(r, ZoneResumedEvent.class), 0L, "resumed, unloaded");

            r.dimension().simulateLoad(east, saved);
            r.ticks(3);
            h.assertValueEqual(one(r, ZoneResumedEvent.class).event().zone(), zone, "the zone resumed");
            h.assertValueEqual(count(r, ZonePausedEvent.class), 1L, "paused, once loaded");
            h.assertValueEqual(count(r, ZoneCreatedEvent.class), 1L, "created");
            h.assertValueEqual(count(r, ZoneDissolvedEvent.class), 0L, "dissolved");
        });
        h.succeed();
    }

    /**
     * A zone wholly unloaded goes to chunk storage: paused, not dissolved. It comes back resumed under
     * its id, not created.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void api_030_a_zone_wholly_unloaded_fires_paused_then_resumed(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 1517);
            r.clear(0, 8);
            ZoneId zone = room(r, 0, "api_030");
            r.tick();

            ChunkPos chunk = r.chunk(0);
            byte[] saved = r.dimension().simulateUnload(chunk);
            r.ticks(3);
            h.assertValueEqual(one(r, ZonePausedEvent.class).event().zone(), zone, "the zone paused");
            h.assertValueEqual(count(r, ZoneDissolvedEvent.class), 0L, "dissolved");

            r.dimension().simulateLoad(chunk, saved);
            r.ticks(3);
            h.assertValueEqual(one(r, ZoneResumedEvent.class).event().zone(), zone, "the zone resumed");
            h.assertValueEqual(count(r, ZonePausedEvent.class), 1L, "paused, once loaded");
            h.assertValueEqual(count(r, ZoneCreatedEvent.class), 1L, "created");
        });
        h.succeed();
    }
}
