package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.simulation.DimensionAtmos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Zones catching changes they couldn't see at write time, when a chunk loads (GEO-013). Areas 674 to
 * 680. Each room runs from x 11 to 20, across the border of two chunks at x 16.
 *
 * <p>A chunk unloads only as far as the atmosphere is concerned ({@link DimensionAtmos#simulateUnload},
 * see {@code PausedZoneTests}), and a change it misses goes straight to the chunk's section, under the
 * hook, as an offline editor's or worldgen's does.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChunkRevalidationTests {
    private static final int WEST = 0;
    private static final int EAST = 16;

    private ChunkRevalidationTests() {}

    /** A sealed room of 90 cells across two chunks, zoned by adding oxygen. */
    private static Rooms spanning(GameTestHelper h, int index) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 23);
        r.shell(10, 21);
        h.assertValueEqual(
                r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(2000), source("geo_013")))
                        .outcome(),
                MutationOutcome.ZONE,
                "the room");
        h.assertValueEqual(r.zone(12, 2, 2).cellCount(), 90, "the room's cells");
        return r;
    }

    /** Sets a block as nothing would see: in the chunk's section, under {@code LevelChunk#setBlockState}. */
    private static void unseen(Rooms r, int x, int y, int z, BlockState state) {
        BlockPos p = r.abs(x, y, z);
        LevelChunk chunk = r.level.getChunkAt(p);
        chunk.getSection(chunk.getSectionIndex(p.getY()))
                .setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, state);
    }

    /** A stone wall across the room at x. */
    private static void unseenWall(Rooms r, int x) {
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                unseen(r, x, y, z, STONE);
            }
        }
    }

    /** With one chunk unloaded, so the zone waits in memory, paused. */
    private static void whilePartUnloaded(Rooms r, Runnable change) {
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        r.h.assertTrue(r.zone(12, 2, 2).paused(), "the zone didn't pause");
        change.run();
        r.ticks(2);
        r.h.assertTrue(r.zone(12, 2, 2).paused(), "the zone resumed");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.ticks(3);
    }

    /** With both chunks unloaded, so the zone is saved and leaves memory. */
    private static void whileWhollyUnloaded(Rooms r, Runnable change) {
        DimensionAtmos d = r.dimension();
        byte[] west = d.simulateUnload(r.chunk(WEST));
        byte[] east = d.simulateUnload(r.chunk(EAST));
        r.tick();
        r.h.assertFalse(r.zoned(12, 2, 2), "the zone stayed in memory");
        change.run();
        d.simulateLoad(r.chunk(WEST), west);
        d.simulateLoad(r.chunk(EAST), east);
        r.ticks(3);
    }

    /** GEO-013's plan: a zone's wall changes while its chunk is unloaded, and the zone vents on load. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_013_a_wall_opened_while_unloaded_breaches_the_paused_zone(GameTestHelper h) {
        Rooms r = spanning(h, 674);
        double full = r.zone(12, 2, 2).mixture().moles().get(Rooms.O2);
        whilePartUnloaded(r, () -> unseen(r, 21, 2, 2, AIR));
        ZoneInfo open = r.zone(12, 2, 2);
        h.assertFalse(open.paused(), "the zone didn't resume");
        h.assertTrue(open.breached(), "the zone doesn't know its wall has gone: " + open);
        h.assertTrue(open.mixture().moles().get(Rooms.O2) < full, "the zone didn't vent: " + open);
        h.succeed();
    }

    /** GEO-013's plan again, with the whole zone saved and rebuilt from its chunks. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_013_a_wall_opened_while_unloaded_breaches_the_rebuilt_zone(GameTestHelper h) {
        Rooms r = spanning(h, 675);
        double full = r.zone(12, 2, 2).mixture().moles().get(Rooms.O2);
        whileWhollyUnloaded(r, () -> unseen(r, 21, 2, 2, AIR));
        ZoneInfo open = r.zone(12, 2, 2);
        h.assertFalse(open.paused(), "the zone didn't resume");
        h.assertTrue(open.breached(), "the zone doesn't know its wall has gone: " + open);
        h.assertTrue(open.mixture().moles().get(Rooms.O2) < full, "the zone didn't vent: " + open);
        h.succeed();
    }

    /** A cell filled while unloaded leaves the zone, in the chunk that's unloaded and in one that isn't saved. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_013_a_block_placed_while_unloaded_leaves_the_zone(GameTestHelper h) {
        Rooms paused = spanning(h, 676);
        whilePartUnloaded(paused, () -> unseen(paused, 18, 2, 2, STONE));
        h.assertValueEqual(paused.zone(12, 2, 2).cellCount(), 89, "the paused zone's cells");
        h.assertFalse(paused.zoned(18, 2, 2), "the filled cell is still zoned");

        Rooms rebuilt = spanning(h, 677);
        whileWhollyUnloaded(rebuilt, () -> unseen(rebuilt, 18, 2, 2, STONE));
        h.assertValueEqual(rebuilt.zone(12, 2, 2).cellCount(), 89, "the rebuilt zone's cells");
        h.assertFalse(rebuilt.zoned(18, 2, 2), "the filled cell is still zoned");
        h.succeed();
    }

    /** A wall put across a zone while it was unloaded splits it (CORE-024). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_013_a_wall_built_while_unloaded_splits_the_zone(GameTestHelper h) {
        Rooms paused = spanning(h, 678);
        whilePartUnloaded(paused, () -> unseenWall(paused, 18));
        h.assertFalse(
                paused.zone(12, 2, 2).id().equals(paused.zone(19, 2, 2).id()), "the paused zone is still one zone");
        h.assertValueEqual(paused.zone(12, 2, 2).cellCount(), 63, "the west part's cells");
        h.assertValueEqual(paused.zone(19, 2, 2).cellCount(), 18, "the east part's cells");

        Rooms rebuilt = spanning(h, 679);
        whileWhollyUnloaded(rebuilt, () -> unseenWall(rebuilt, 18));
        h.assertFalse(
                rebuilt.zone(12, 2, 2).id().equals(rebuilt.zone(19, 2, 2).id()), "the rebuilt zone is still one zone");
        h.assertValueEqual(rebuilt.zone(12, 2, 2).cellCount(), 63, "the west part's cells");
        h.assertValueEqual(rebuilt.zone(19, 2, 2).cellCount(), 18, "the east part's cells");
        h.succeed();
    }

    /** A hole mended while unloaded closes the breach. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_013_a_hole_mended_while_unloaded_closes_the_breach(GameTestHelper h) {
        Rooms r = spanning(h, 680);
        r.set(21, 2, 2, AIR);
        r.tick();
        h.assertTrue(r.zone(12, 2, 2).breached(), "the test needs the zone breached");
        whilePartUnloaded(r, () -> unseen(r, 21, 2, 2, STONE));
        h.assertFalse(r.zone(12, 2, 2).breached(), "the zone doesn't know its hole has been mended");
        h.succeed();
    }
}
