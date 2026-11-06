package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.scheduler.Classifier;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Classifying a cell as open without a fill, in a real level: straight up its column (GEO-021) and
 * from what was found before (GEO-027). Areas 670 to 672.
 *
 * <p>Each test runs within one game tick, and counts only the fills seeded at its own cell, so other
 * tests' fills don't matter.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OpenClassificationTests {
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();

    private OpenClassificationTests() {}

    /** Collects the seeds of the fills run while it's open. */
    private static final class Fills implements AutoCloseable {
        private final Classifier classifier;
        private final Queue<Long> seeds = new ConcurrentLinkedQueue<>();

        Fills(Rooms r) {
            classifier = r.dimension().pipeline().scheduler().engine().classifier();
            classifier.onFill(seeds::add);
        }

        /** How many fills started at the cell of {@code pos}. */
        long from(BlockPos pos) {
            long cell = CellIds.of(pos.getX(), pos.getY(), pos.getZ());
            return seeds.stream().filter(seed -> seed == cell).count();
        }

        @Override
        public void close() {
            classifier.onFill(null);
        }
    }

    private static MutationOutcome add(Rooms r, int x, int y, int z) {
        return r.settle(GasMutations.add(r.level, r.point(x, y, z), oxygen(1), source("geo_021")))
                .outcome();
    }

    /** Nothing above the cell up to the build height: no fill. A lid high above takes one. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_021_open_sky_classifies_without_a_fill(GameTestHelper h) {
        Rooms r = new Rooms(h, 670);
        r.clear(8, 0, 20, 6);
        try (Fills fills = new Fills(r)) {
            h.assertValueEqual(add(r, 10, 2, 2), MutationOutcome.AMBIENT, "the cell under open sky");
            h.assertValueEqual(fills.from(r.abs(10, 2, 2)), 0L, "fills under open sky");

            r.set(14, 60, 2, STONE);
            h.assertValueEqual(add(r, 14, 2, 2), MutationOutcome.AMBIENT, "the cell under a lid, open around it");
            h.assertTrue(fills.from(r.abs(14, 2, 2)) >= 1, "the cell under a lid took no fill");
        }
        h.succeed();
    }

    /** Sky light comes in through the glass, and the room is still sealed. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_021_glass_roofed_greenhouse_is_enclosed(GameTestHelper h) {
        Rooms r = new Rooms(h, 671);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        for (BlockPos p : BlockPos.betweenClosed(r.abs(10, 4, 0), r.abs(14, 4, 4))) {
            r.level.setBlockAndUpdate(p, GLASS);
        }
        h.assertTrue(GLASS.propagatesSkylightDown(r.level, r.abs(12, 4, 2)), "sky light passes the roof");
        try (Fills fills = new Fills(r)) {
            h.assertValueEqual(add(r, 12, 2, 2), MutationOutcome.ZONE, "the greenhouse");
            h.assertValueEqual(fills.from(r.abs(12, 2, 2)), 1L, "fills in the greenhouse");
        }
        h.assertValueEqual(r.zone(12, 2, 2).cellCount(), 27, "the greenhouse's cells");
        h.succeed();
    }

    /**
     * A room with a roof and a hole in its side: the column stops at the roof, so it takes a fill to
     * find it open. The next add takes none, and plugging the hole means another.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_027_open_cell_takes_no_second_fill_until_a_block_changes(GameTestHelper h) {
        Rooms r = new Rooms(h, 672);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.set(14, 2, 2, Rooms.AIR);
        try (Fills fills = new Fills(r)) {
            BlockPos inside = r.abs(12, 2, 2);
            h.assertValueEqual(add(r, 12, 2, 2), MutationOutcome.AMBIENT, "the first add");
            long first = fills.from(inside);
            h.assertTrue(first >= 1, "the first add took no fill");

            h.assertValueEqual(add(r, 12, 2, 2), MutationOutcome.AMBIENT, "the second add");
            h.assertValueEqual(fills.from(inside), first, "fills after the second add");

            r.set(14, 2, 2, STONE);
            h.assertValueEqual(add(r, 12, 2, 2), MutationOutcome.ZONE, "the add once the hole is plugged");
            h.assertValueEqual(fills.from(inside), first + 1, "fills once the hole is plugged");
        }
        h.assertValueEqual(r.zone(12, 2, 2).cellCount(), 27, "the room's cells");
        h.succeed();
    }
}
