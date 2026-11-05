package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.HotspotTests.gasFire;
import static io.github.west3436.zas.gametest.HotspotTests.hotspots;
import static io.github.west3436.zas.gametest.HotspotTests.hydrogenInAir;
import static io.github.west3436.zas.gametest.HotspotTests.seconds;
import static io.github.west3436.zas.gametest.HotspotTests.with;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.fire.BlockIgnitionSettings;
import io.github.west3436.zas.fire.Burners;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hot gas and gas fires setting flammable blocks alight (FIRE-033, FIRE-034): the worker's draws,
 * the fire intents and the server's limits on placing fire together, with vanilla's real flammable
 * blocks.
 *
 * <p>Each room is a sealed room in an area of its own, stone until a test changes its walls. Every
 * test drives its atmos ticks itself and runs within one game tick, so the settings and the game rule
 * it sets are the only ones its ticks see, and no vanilla fire ticks. The GameTest server runs with
 * {@code doFireTick} off, so a test turns it on for its body: nothing else in the suite lights a block
 * this way, whatever the rooms around it are made of.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockIgnitionTests {
    private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState WOOL = Blocks.WHITE_WOOL.defaultBlockState();
    private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();

    private static BlockState gasFireBlock() {
        return HotspotBlocks.GAS_FIRE.get().defaultBlockState();
    }

    /** Air's partial pressures, kPa. */
    private static final Map<ResourceLocation, Double> AIR =
            Map.of(N2, 0.79 * StandardAir.PRESSURE, O2, 0.21 * StandardAir.PRESSURE);

    /** 13 % O₂ at 1 atm, below the fire oxygen threshold of 14 %, kPa. */
    private static final Map<ResourceLocation, Double> THIN_AIR =
            Map.of(N2, 0.87 * StandardAir.PRESSURE, O2, 0.13 * StandardAir.PRESSURE);

    private BlockIgnitionTests() {}

    /** The test's own area, apart from every other test's; chunk-aligned. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(12288 + 256 * index, 100, 14336));
    }

    /**
     * Builds a sealed room like {@link HotspotTests#room}, then makes its walls of {@code wall},
     * and leaves its zone formed.
     */
    private static void room(
            Rooms r, int x0, int w, int h, int d, BlockState wall, double kelvin, Map<ResourceLocation, Double> kPa) {
        HotspotTests.room(r, x0, w, h, d, kelvin, kPa);
        if (wall != Rooms.STONE) {
            for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, 0, 0), r.abs(x0 + w + 1, h + 1, d + 1))) {
                if (!r.level.getBlockState(p).isAir()) {
                    r.level.setBlockAndUpdate(p, wall);
                }
            }
        }
    }

    /** Runs a body with {@code doFireTick} set as asked, and how blocks catch fire set; then undoes both. */
    private static void burning(GameTestHelper h, boolean fireTick, BlockIgnitionSettings settings, Runnable body) {
        GameRules.BooleanValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        boolean was = rule.get();
        rule.set(fireTick, h.getLevel().getServer());
        FireConfig.overrideBlockIgnitionForTest(settings);
        try {
            body.run();
        } finally {
            rule.set(was, h.getLevel().getServer());
            FireConfig.overrideBlockIgnitionForTest(null);
        }
    }

    /** {@link #burning} with the default temperature and chance, and {@code doFireTick} on. */
    private static void burning(GameTestHelper h, Runnable body) {
        burning(h, true, BlockIgnitionSettings.DEFAULT, body);
    }

    /** The vanilla fires in the interior of the room from x0, of the given size. */
    private static List<BlockPos> fires(Rooms r, int x0, int w, int h, int d) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0 + 1, 1, 1), r.abs(x0 + w, h, d))) {
            if (r.level.getBlockState(p).is(Blocks.FIRE)) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** The vanilla fires around a room's interior, walls included, and on them. */
    private static int firesOutside(Rooms r, int x0, int w, int h, int d) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0 - 1, -1, -1), r.abs(x0 + w + 2, h + 2, d + 2))) {
            int x = p.getX() - r.origin.getX();
            int y = p.getY() - r.origin.getY();
            int z = p.getZ() - r.origin.getZ();
            boolean inside = x > x0 && x <= x0 + w && y >= 1 && y <= h && z >= 1 && z <= d;
            if (!inside && r.level.getBlockState(p).is(Blocks.FIRE)) {
                n++;
            }
        }
        return n;
    }

    /** Clears a room's fires and leaves it with nothing to burn. */
    private static void clear(Rooms r, int x0, int w, int h, int d) {
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0 + 1, 1, 1), r.abs(x0 + w, h, d))) {
            if (!r.level.getBlockState(p).isAir()) {
                r.level.setBlockAndUpdate(p, Rooms.AIR);
            }
        }
        HotspotTests.empty(r, x0);
    }

    // ---- The geometry table --------------------------------------------------------------

    /**
     * FIRE-033, FIRE-034: the geometry table gives a flammable state its vanilla ignite odds and any
     * ignition temperature of its own, and keeps its shape. Wool and leaves have the shipped 500 K,
     * planks none; a waterlogged fence isn't flammable, as in vanilla.
     */
    @GameTest(template = "empty")
    public static void fire_033_the_table_has_each_block_flammability(GameTestHelper h) {
        var table = Geometries.table();
        Geometry planks = table.geometry(PLANKS);
        h.assertValueEqual(planks.igniteOdds(), 5, "planks' odds");
        h.assertTrue(Double.isNaN(planks.ignitionTemperature()), "planks have their own temperature");
        h.assertValueEqual(planks.cells().size(), 0, "planks have cells");
        Geometry wool = table.geometry(WOOL);
        h.assertValueEqual(wool.igniteOdds(), 30, "wool's odds");
        h.assertValueEqual(wool.ignitionTemperature(), 500.0, "wool's temperature");
        Geometry leaves = table.geometry(Blocks.OAK_LEAVES.defaultBlockState());
        h.assertValueEqual(leaves.igniteOdds(), 30, "leaves' odds");
        h.assertValueEqual(leaves.ignitionTemperature(), 500.0, "leaves' temperature");
        h.assertValueEqual(table.geometry(HAY).igniteOdds(), 60, "hay's odds");
        h.assertFalse(table.geometry(Rooms.STONE).isFlammable(), "stone is flammable");
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        h.assertTrue(table.geometry(fence).isFlammable(), "a fence isn't flammable");
        h.assertFalse(
                table.geometry(fence.setValue(BlockStateProperties.WATERLOGGED, true))
                        .isFlammable(),
                "a waterlogged fence is flammable");
        h.succeed();
    }

    // ---- FIRE-033: hot gas --------------------------------------------------------------

    /**
     * FIRE-033: a sealed planks room filled at 293 K stays unlit. Held at 700 K, it gets vanilla
     * fires on its inside faces: beside a wall, and none outside it. Each burns as a fire does: it is
     * a hotspot (FIRE-019), and tracked to burn its fuel profile (FIRE-022).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_a_planks_room_lights_only_when_hot(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 0);
            room(r, 0, 3, 3, 3, PLANKS, StandardAir.TEMPERATURE, AIR);
            room(r, 6, 3, 3, 3, PLANKS, 700, AIR);
            burning(h, () -> {
                r.ticks(15);
                h.assertValueEqual(fires(r, 0, 3, 3, 3).size(), 0, "fires in the room at 293 K");
                List<BlockPos> hot = fires(r, 6, 3, 3, 3);
                h.assertTrue(!hot.isEmpty(), "no fires in the room at 700 K");
                h.assertValueEqual(firesOutside(r, 6, 3, 3, 3), 0, "fires outside the hot room");
                for (BlockPos p : hot) {
                    boolean beside = false;
                    for (Direction d : Direction.values()) {
                        beside |= r.level.getBlockState(p.relative(d)).is(Blocks.OAK_PLANKS);
                    }
                    h.assertTrue(beside, "a fire with no planks beside it at " + p);
                    h.assertTrue(Burners.tracks(r.level, p), "a fire isn't burning at " + p);
                }
                r.tick();
                h.assertTrue(hotspots(r).zoneAt(hot.get(0).asLong()) != 0, "a lit fire isn't a hotspot");
            });
            clear(r, 0, 3, 3, 3);
            clear(r, 6, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * FIRE-033: wool and leaves catch at 500 K (the shipped data), below planks' 573 K: at 540 K a
     * wool room burns and a planks room doesn't.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_data_lowers_the_ignition_temperature(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 1);
            room(r, 0, 3, 3, 3, WOOL, 540, AIR);
            room(r, 6, 3, 3, 3, PLANKS, 540, AIR);
            burning(h, () -> {
                r.ticks(15);
                h.assertTrue(!fires(r, 0, 3, 3, 3).isEmpty(), "no fires in the wool room at 540 K");
                h.assertValueEqual(fires(r, 6, 3, 3, 3).size(), 0, "fires in the planks room at 540 K");
            });
            clear(r, 0, 3, 3, 3);
            clear(r, 6, 3, 3, 3);
        });
        h.succeed();
    }

    /** FIRE-033, FIRE-030: below 14 % O₂, a hot planks room lights nothing. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_no_fire_below_the_oxygen_threshold(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 2);
            room(r, 0, 3, 3, 3, PLANKS, 700, THIN_AIR);
            burning(h, () -> {
                r.ticks(15);
                h.assertValueEqual(fires(r, 0, 3, 3, 3).size(), 0, "fires at 13 % O2");
            });
            clear(r, 0, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * FIRE-033: the chance per second follows the block's ignite odds and how far the gas is above
     * the ignition temperature. A 20 × 1 × 20 room has a hay floor under all of its 400 cells, and
     * stone above and around. At 823 K, 250 K above hay's ignition point, the heat share is 0.55, so
     * with hay's odds of 60 the chance is 0.33. A configured ignition point above the gas lights none.
     * Each tick's fires are put out before the next, so each of 16 ticks draws for all 400 cells.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_rate_matches_the_odds_and_the_heat(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 3);
            room(r, 0, 20, 1, 20, Rooms.STONE, 823, AIR);
            for (int x = 1; x <= 20; x++) {
                for (int z = 1; z <= 20; z++) {
                    r.set(x, 0, z, HAY);
                }
            }
            burning(h, true, new BlockIgnitionSettings(true, 900, 1), () -> {
                r.tick();
                h.assertValueEqual(fires(r, 0, 20, 1, 20).size(), 0, "fires below the configured ignition point");
            });
            burning(h, true, new BlockIgnitionSettings(true, 573, 1), () -> {
                int ticks = 16;
                int lit = 0;
                for (int i = 0; i < ticks; i++) {
                    r.tick();
                    for (BlockPos p : fires(r, 0, 20, 1, 20)) {
                        r.level.setBlockAndUpdate(p, Rooms.AIR);
                        lit++;
                    }
                }
                double expected = 1 - Math.pow(1 - 0.33, seconds());
                double rate = lit / (ticks * 400.0);
                // Binomial: σ of the rate is √(0.33 · 0.67 / 6400) ≈ 0.0059, so 0.04 is 6.8 σ.
                h.assertTrue(Math.abs(rate - expected) <= 0.04, "ignition rate " + rate + ", expected " + expected);
            });
            clear(r, 0, 20, 1, 20);
        });
        h.succeed();
    }

    // ---- FIRE-034: gas fires ------------------------------------------------------------

    /**
     * FIRE-034: a gas fire beside wool lights it: a vanilla fire takes the gas fire's cell, the only
     * open one beside that wool. A gas fire beside stone stays a gas fire.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_034_a_gas_fire_lights_wool_not_stone(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 4);
            room(r, 0, 5, 3, 5, Rooms.STONE, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            r.set(0, 2, 3, WOOL);
            BlockPos besideWool = r.abs(1, 2, 3);
            BlockPos besideStone = r.abs(5, 2, 3);
            r.level.setBlockAndUpdate(besideWool, gasFireBlock());
            r.level.setBlockAndUpdate(besideStone, gasFireBlock());
            burning(h, () -> {
                for (int i = 0; i < 30 && !r.level.getBlockState(besideWool).is(Blocks.FIRE); i++) {
                    r.tick();
                }
                h.assertTrue(r.level.getBlockState(besideWool).is(Blocks.FIRE), "the gas fire didn't light the wool");
                h.assertTrue(gasFire(r, besideStone), "the gas fire beside stone isn't a gas fire");
                h.assertValueEqual(fires(r, 0, 5, 3, 5).size(), 1, "vanilla fires in the room");
                h.assertTrue(Burners.tracks(r.level, besideWool), "the new fire isn't burning");
                r.tick();
                h.assertTrue(hotspots(r).zoneAt(besideWool.asLong()) != 0, "the new fire isn't a hotspot");
            });
            clear(r, 0, 5, 3, 5);
        });
        h.succeed();
    }

    /**
     * FIRE-034, FIRE-030: below 14 % O₂, with hydrogen to burn, a gas fire beside wool goes out
     * (FIRE-017) without lighting it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_034_no_fire_below_the_oxygen_threshold(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 5);
            double atm = StandardAir.PRESSURE;
            HotspotTests.room(
                    r,
                    0,
                    3,
                    3,
                    3,
                    StandardAir.TEMPERATURE,
                    Map.of(HotspotTests.H2, 0.1 * atm, N2, 0.77 * atm, O2, 0.13 * atm));
            r.set(0, 2, 2, WOOL);
            BlockPos besideWool = r.abs(1, 2, 2);
            r.level.setBlockAndUpdate(besideWool, gasFireBlock());
            burning(h, () -> {
                r.ticks(5);
                h.assertFalse(r.level.getBlockState(besideWool).is(Blocks.FIRE), "a fire at 13 % O2");
                h.assertFalse(gasFire(r, besideWool), "the gas fire burns on at 13 % O2");
            });
            clear(r, 0, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * FIRE-034: the chance per second is the configured one scaled by the block's ignite odds. Each of
     * eight 20 × 1 × 20 rooms holds a gas fire in each of its 400 cells over a wool floor, the only
     * flammable block any of them touches; a chance of 0.5 and wool's odds of 30 make 0.15. It takes
     * eight rooms rather than eight ticks because 400 gas fires burn about a third of a room's gas in a
     * tick.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_034_rate_matches_the_configured_chance(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 6);
            int count = 8;
            for (int i = 0; i < count; i++) {
                room(r, 23 * i, 20, 1, 20, Rooms.STONE, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            }
            // Once every room is built, so that no fire burns before the tick that counts.
            for (int i = 0; i < count; i++) {
                for (int x = 1; x <= 20; x++) {
                    for (int z = 1; z <= 20; z++) {
                        r.set(23 * i + x, 0, z, WOOL);
                        r.set(23 * i + x, 1, z, gasFireBlock());
                    }
                }
            }
            burning(h, true, new BlockIgnitionSettings(true, 573, 0.5), () -> {
                r.tick();
                int lit = 0;
                for (int i = 0; i < count; i++) {
                    lit += fires(r, 23 * i, 20, 1, 20).size();
                }
                double expected = 1 - Math.pow(1 - 0.15, seconds());
                double rate = lit / (count * 400.0);
                // Binomial: σ of the rate is √(0.15 · 0.85 / 3200) ≈ 0.0063, so 0.04 is 6.3 σ.
                h.assertTrue(Math.abs(rate - expected) <= 0.04, "ignition rate " + rate + ", expected " + expected);
            });
            for (int i = 0; i < count; i++) {
                clear(r, 23 * i, 20, 1, 20);
            }
        });
        h.succeed();
    }

    // ---- Both -------------------------------------------------------------------------

    /**
     * FIRE-033, FIRE-034: with {@code doFireTick} false, neither a hot planks room nor a gas fire
     * beside wool lights anything, and the gas fire stays one.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_fire_034_no_fire_without_do_fire_tick(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 7);
            room(r, 0, 3, 3, 3, PLANKS, 700, AIR);
            room(r, 6, 5, 3, 5, Rooms.STONE, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            r.set(6, 2, 3, WOOL);
            BlockPos besideWool = r.abs(7, 2, 3);
            r.level.setBlockAndUpdate(besideWool, gasFireBlock());
            burning(h, false, BlockIgnitionSettings.DEFAULT, () -> {
                r.ticks(15);
                h.assertValueEqual(fires(r, 0, 3, 3, 3).size(), 0, "fires in the hot room");
                h.assertValueEqual(fires(r, 6, 5, 3, 5).size(), 0, "fires beside the gas fire");
                h.assertTrue(gasFire(r, besideWool), "the gas fire isn't a gas fire");
            });
            clear(r, 0, 3, 3, 3);
            clear(r, 6, 5, 3, 5);
        });
        h.succeed();
    }

    /**
     * FIRE-033, FIRE-034: the game rule holds at apply too. A batch closed while the rule was on
     * lights nothing if the rule goes off before the result is applied, and the room burns once the
     * rule is back on.
     *
     * <p>The batch held is the room's first, so all 54 faces draw and it asks for about 21 fires. A
     * tick before it could light all 26 cells that have a face, about one run in 300, which leaves
     * nothing to refuse and nothing more to light.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_the_rule_holds_when_the_fire_is_lit(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 8);
            room(r, 0, 3, 3, 3, HAY, 1200, AIR);
            burning(h, () -> {
                GameRules.BooleanValue rule = r.level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
                Rooms.service().closeNow(r.level);
                rule.set(false, r.level.getServer());
                Rooms.service().applyNow(r.level);
                h.assertValueEqual(fires(r, 0, 3, 3, 3).size(), 0, "fires lit after the rule went off");
                rule.set(true, r.level.getServer());
                r.ticks(5);
                h.assertTrue(!fires(r, 0, 3, 3, 3).isEmpty(), "the room doesn't burn with the rule back on");
            });
            clear(r, 0, 3, 3, 3);
        });
        h.succeed();
    }

    /**
     * FIRE-033, API-057: a fire the batch in flight asks for in a box put out after the batch closed
     * isn't lit: the batch didn't see the spray. The next batch, which did, lights fires again.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_033_extinguish_stops_a_fire_in_flight(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 9);
            room(r, 0, 3, 3, 3, HAY, 1200, AIR);
            burning(h, () -> {
                r.tick();
                Rooms.service().closeNow(r.level);
                Fires.extinguish(r.level, r.abs(1, 1, 1), r.abs(3, 3, 3));
                Rooms.service().applyNow(r.level);
                h.assertValueEqual(fires(r, 0, 3, 3, 3).size(), 0, "fires lit in the sprayed box");
                r.ticks(5);
                h.assertTrue(!fires(r, 0, 3, 3, 3).isEmpty(), "the room doesn't burn after the spray");
            });
            clear(r, 0, 3, 3, 3);
        });
        h.succeed();
    }
}
