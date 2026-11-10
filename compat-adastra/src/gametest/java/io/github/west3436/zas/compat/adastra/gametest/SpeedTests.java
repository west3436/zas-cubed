package io.github.west3436.zas.compat.adastra.gametest;

import com.mojang.logging.LogUtils;
import earth.terrarium.adastra.api.events.AdAstraEvents;
import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.api.systems.OxygenApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * How long the adapter's answers take (SPACE-018, NFR-001). Ad Astra asks about the environment
 * once per randomly ticked block, so that answer is the hot path.
 *
 * <p>The figures are logged, and the bounds are loose: they catch an answer that has stopped being
 * a lookup, not a slow machine.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class SpeedTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Times each position is asked about, after as many again to warm up. */
    private static final int ROUNDS = 50;

    /** The most an answer may take on average, in nanoseconds. */
    private static final double LOOSE_BOUND = 10_000;

    private SpeedTests() {}

    /**
     * The environment answer over a whole chunk section of blocks on the Moon, 4,096 positions: a
     * slab of stone four deep with nine rooms of air standing on it, so the positions are in zones,
     * in their walls, under their floors and nowhere near one. Then the same positions on Mars, a
     * dimension with no zone at all, which is what most of a planet is.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void space_018_the_environment_answer_is_a_lookup(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 50)) {
            BlockState stone = Blocks.STONE.defaultBlockState();
            for (BlockPos p : BlockPos.betweenClosed(lab.abs(0, -4, 0), lab.abs(15, -1, 15))) {
                lab.level.setBlockAndUpdate(p, stone);
            }
            for (int x = 0; x < 15; x += 5) {
                for (int z = 0; z < 15; z += 5) {
                    lab.room(x, z, Lab.AIR);
                }
            }
            List<BlockPos> section = new ArrayList<>();
            BlockPos.betweenClosed(lab.abs(0, -4, 0), lab.abs(15, 11, 15)).forEach(p -> section.add(p.immutable()));
            h.assertValueEqual(section.size(), 4096, "positions asked about");

            int[] vetoed = new int[1];
            double moon = nanosPerAnswer(lab.level, section, vetoed);
            // Of each of the 9 rooms: its 27 cells, and the 54 blocks of its walls that face one.
            h.assertValueEqual(vetoed[0], 9 * 81, "positions Ad Astra is kept from, of 4,096");

            ServerLevel mars = Moon.planet(h.getLevel().getServer(), Planet.MARS);
            mars.getChunk(lab.origin);
            int[] onMars = new int[1];
            double noZones = nanosPerAnswer(mars, section, onMars);
            h.assertValueEqual(onMars[0], 0, "positions Ad Astra is kept from on Mars");

            LOGGER.info(
                    "SPACE-018: environment answer, {} ns each over a section with 9 rooms ({} of 4,096"
                            + " positions touched by a zone); {} ns each in a dimension with no zone",
                    Math.round(moon),
                    vetoed[0],
                    Math.round(noZones));
            h.assertTrue(moon < LOOSE_BOUND, "an environment answer took " + moon + " ns");
            h.assertTrue(noZones < LOOSE_BOUND, "an environment answer with no zone about took " + noZones + " ns");
        }
        h.succeed();
    }

    /** Ad Astra's entity oxygen check, which it makes for each player every 5 ticks and each worn suit every 12. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void space_018_the_oxygen_check_is_a_lookup(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 51)) {
            lab.room(0, Lab.AIR);
            Pig inRoom = lab.mob(EntityType.PIG, lab.floor(0));
            FakePlayer inTheOpen = lab.player(lab.open(10));
            int calls = 20_000;
            boolean sink = false;
            for (int i = 0; i < calls; i++) {
                sink ^= OxygenApi.API.hasOxygen(inRoom) ^ OxygenApi.API.hasOxygen(inTheOpen);
            }
            long start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                sink ^= OxygenApi.API.hasOxygen(inRoom) ^ OxygenApi.API.hasOxygen(inTheOpen);
            }
            double each = (System.nanoTime() - start) / (2.0 * calls);
            LOGGER.info("SPACE-018: entity oxygen check, {} ns each ({})", Math.round(each), sink);
            h.assertTrue(each < 10 * LOOSE_BOUND, "an oxygen check took " + each + " ns");
        }
        h.succeed();
    }

    /**
     * Asks Ad Astra's environment event about each position, as its chunk tick does, and returns the
     * average time of an answer in nanoseconds.
     *
     * @param vetoed set to how many of the positions the answer keeps Ad Astra from
     */
    private static double nanosPerAnswer(ServerLevel level, List<BlockPos> positions, int[] vetoed) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        int refused = 0;
        for (int round = 0; round < ROUNDS; round++) {
            for (BlockPos pos : positions) {
                if (!AdAstraEvents.EnvironmentTickEvent.fire(level, pos, stone, (short) -173)) {
                    refused++;
                }
            }
        }
        vetoed[0] = refused / ROUNDS;
        long start = System.nanoTime();
        for (int round = 0; round < ROUNDS; round++) {
            for (BlockPos pos : positions) {
                if (!AdAstraEvents.EnvironmentTickEvent.fire(level, pos, stone, (short) -173)) {
                    refused++;
                }
            }
        }
        long taken = System.nanoTime() - start;
        if (refused != 2 * ROUNDS * vetoed[0]) {
            throw new IllegalStateException("the answers changed between rounds");
        }
        return taken / (double) (ROUNDS * positions.size());
    }
}
