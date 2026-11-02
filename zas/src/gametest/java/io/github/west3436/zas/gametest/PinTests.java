package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.persistence.DimensionData;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Zone pins through the API in a real level (API-017, API-018, API-050).
 *
 * <p>Each test builds sealed 3 × 3 × 3 stone rooms ({@link Rooms#shell}) in its own area and drives
 * the atmos ticks it needs. The restart test runs in the Nether, in {@code PersistenceTests}'
 * restart batch, since stopping a level's atmosphere would disturb other tests there.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PinTests {
    private static final SourceId CONTROLLER = SourceId.of("zas_gametest", "controller/2_2_2");
    private static final SourceId OTHER = SourceId.of("zas_gametest", "other");

    private PinTests() {}

    private static Rooms rooms(GameTestHelper h, ServerLevel level, int index) {
        return new Rooms(h, level, new BlockPos(12288 + 256 * index, 100, -12288));
    }

    private static ZoneView view(Rooms r, ZoneId id) {
        ZoneView v = r.dimension().published().zone(id.value());
        if (v == null) {
            throw new GameTestAssertException("zone " + id.value() + " isn't in the published table");
        }
        return v;
    }

    /** Checks a zone holds exactly its volume of the level's ambient, nothing added. */
    private static void holdsAmbient(Rooms r, ZoneInfo z, String what) {
        Ambient ambient = Ambients.ambient(r.level.dimension());
        for (String gas : List.of(Rooms.O2.toString(), Rooms.N2.toString())) {
            r.close(
                    z.mixture().moles().getOrDefault(net.minecraft.resources.ResourceLocation.parse(gas), 0.0),
                    ambient.moles(gas, z.volume()),
                    what + ": " + gas);
        }
        r.close(z.mixture().temperature(), ambient.temperature(), what + ": temperature");
    }

    /**
     * Pinning a point in a sealed room with no zone forms a pinned zone there holding the ambient's
     * gas, and the result names it; pinning a point in the open forms nothing and says so (API-050).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_050_pin_forms_a_zone_and_open_air_forms_none(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 0);
        r.clear(0, 12);
        r.shell(0, 4);
        r.shell(8, 12);
        r.set(10, 4, 2, Rooms.AIR); // a hole in the second room's roof
        r.ticks(2);
        h.assertValueEqual(
                Atmospheres.at(r.level, r.abs(2, 2, 2)).state(),
                AtmosphereState.AMBIENT,
                "a sealed room holding ambient air has no zone");

        PinResult pinned = r.settle(ZonePins.pin(r.level, r.point(2, 2, 2), CONTROLLER));
        h.assertValueEqual(pinned.outcome(), PinOutcome.ZONE, "the pin's outcome in the sealed room");
        ZoneInfo zone = r.zone(2, 2, 2);
        h.assertValueEqual(pinned.zone().orElseThrow(), zone.id(), "the result names the zone");
        h.assertValueEqual(zone.cellCount(), 27, "cells of the room");
        h.assertFalse(zone.breached(), "the room is breached");
        holdsAmbient(r, zone, "the pinned room");
        h.assertValueEqual(view(r, zone.id()).pins(), List.of(CONTROLLER.toString()), "the zone's pins");

        PinResult open = r.settle(ZonePins.pin(r.level, r.point(10, 2, 2), CONTROLLER));
        h.assertValueEqual(open.outcome(), PinOutcome.OPEN, "the pin's outcome in the open room");
        h.assertTrue(open.zone().isEmpty(), "a pin in the open names a zone");
        h.assertFalse(r.zoned(10, 2, 2), "a zone formed in the open room");
        h.succeed();
    }

    /**
     * A pinned zone isn't dissolved: opened to the open air it keeps exchanging, and it dissolves
     * once its last owner unpins it; an unpinned twin dissolves at once (API-017, CORE-076, D-64).
     * Pinning a zone that exists pins it without changing its gas, and pins are held per owner.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_017_pinned_zone_lasts_until_unpinned(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 1);
        r.clear(0, 12);
        r.shell(0, 4);
        r.shell(8, 12);
        r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(5), Rooms.source("api_017")));
        r.settle(GasMutations.add(r.level, r.point(10, 2, 2), Rooms.oxygen(5), Rooms.source("api_017")));
        ZoneInfo before = r.zone(2, 2, 2);
        ZoneInfo twin = r.zone(10, 2, 2);

        r.settle(ZonePins.pin(r.level, r.point(1, 1, 1), CONTROLLER));
        r.settle(ZonePins.pin(r.level, r.point(3, 3, 3), OTHER));
        ZoneInfo pinned = r.zone(2, 2, 2);
        r.same(before, pinned, "a pin changed the zone");
        h.assertValueEqual(
                view(r, pinned.id()).pins(),
                List.of(CONTROLLER.toString(), OTHER.toString()),
                "both owners pin the zone");

        r.set(2, 4, 2, Rooms.AIR); // open both roofs
        r.set(10, 4, 2, Rooms.AIR);
        r.ticks(12);
        h.assertTrue(
                Atmospheres.zone(r.level, twin.id()).isEmpty(), "the unpinned room didn't dissolve into the open air");
        ZoneInfo breached = Atmospheres.zone(r.level, pinned.id())
                .orElseThrow(() -> new GameTestAssertException("the pinned room was dissolved"));
        h.assertTrue(breached.breached(), "the pinned room isn't breached");

        r.settle(ZonePins.unpin(r.level, OTHER));
        r.ticks(2);
        h.assertTrue(Atmospheres.zone(r.level, pinned.id()).isPresent(), "one owner's unpin dissolved the room");
        h.assertValueEqual(view(r, pinned.id()).pins(), List.of(CONTROLLER.toString()), "the pins left");

        r.settle(ZonePins.unpin(r.level, CONTROLLER));
        r.ticks(3);
        h.assertTrue(Atmospheres.zone(r.level, pinned.id()).isEmpty(), "the unpinned room didn't dissolve");
        h.assertValueEqual(
                Atmospheres.at(r.level, r.abs(2, 2, 2)).state(),
                AtmosphereState.AMBIENT,
                "the room after it dissolved");
        h.succeed();
    }

    /**
     * Pins are saved with their zone and come back with it after a restart, owners included; an
     * unpin still queued at shutdown takes effect when the zone loads (API-018, API-017, DATA-001).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "data_restart")
    public static void api_018_pins_survive_a_restart(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        Rooms r = rooms(h, nether, 2);
        r.clear(0, 12);
        r.shell(0, 4);
        r.shell(8, 12);
        PinResult a = r.settle(ZonePins.pin(nether, r.point(2, 2, 2), CONTROLLER));
        PinResult b = r.settle(ZonePins.pin(nether, r.point(10, 2, 2), OTHER));
        r.settle(ZonePins.pin(nether, r.point(10, 2, 2), CONTROLLER));
        h.assertValueEqual(a.outcome(), PinOutcome.ZONE, "the first room's pin");
        h.assertValueEqual(b.outcome(), PinOutcome.ZONE, "the second room's pin");
        ZoneId first = a.zone().orElseThrow();
        ZoneId second = b.zone().orElseThrow();
        ZoneInfo firstBefore = Atmospheres.zone(nether, first).orElseThrow();

        // Queued, not applied, when the level stops.
        CompletableFuture<Void> unpin = ZonePins.unpin(nether, OTHER);
        long[] chunks = {new ChunkPos(r.abs(0, 0, 0)).toLong()};
        h.assertValueEqual(new ChunkPos(r.abs(12, 0, 4)).toLong(), chunks[0], "the rooms span one chunk");
        Map<Long, byte[]> saved = Rooms.service().stopForTest(nether, chunks);
        h.assertTrue(unpin.isDone(), "the unpin didn't complete at shutdown");
        h.assertTrue(DimensionData.of(nether).releasedPins().contains(OTHER.toString()), "the unpin wasn't recorded");
        DimensionAtmos restarted = Rooms.service().startForTest(nether, saved);
        r.ticks(2);

        ZoneInfo firstAfter = Atmospheres.zone(nether, first)
                .orElseThrow(() -> new GameTestAssertException("the first room didn't survive the restart"));
        r.same(firstBefore, firstAfter, "the first room after the restart");
        h.assertValueEqual(
                restarted.published().zone(first.value()).pins(),
                List.of(CONTROLLER.toString()),
                "the first room's pins after the restart");
        h.assertValueEqual(
                restarted.published().zone(second.value()).pins(),
                List.of(CONTROLLER.toString()),
                "the second room's pins after the restart, without the unpinned owner");

        // Pinning again under the released owner keeps its saved pins from then on.
        r.settle(ZonePins.pin(nether, r.point(10, 2, 2), OTHER));
        h.assertFalse(
                DimensionData.of(nether).releasedPins().contains(OTHER.toString()), "the owner is still released");
        r.settle(ZonePins.unpin(nether, OTHER));
        r.settle(ZonePins.unpin(nether, CONTROLLER));
        h.succeed();
    }
}
