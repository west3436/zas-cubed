package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.scheduler.HotspotSettings;
import io.github.west3436.zas.engine.scheduler.HotspotTable;
import io.github.west3436.zas.engine.scheduler.OpOutcome;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import io.github.west3436.zas.simulation.DimensionAtmos;
import io.github.west3436.zas.simulation.Ignited;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hotspots (FIRE-010, FIRE-011, FIRE-013, FIRE-014, FIRE-016 to FIRE-019, DATA-009): the engine's
 * hotspot phase with the server's side of it, the {@code zas:gas_fire} block, the fire intents and
 * the fire flags in the geometry table, burning the shipped reactions.
 *
 * <p>Each room is a sealed stone room in an area of its own, its air replaced with a mixture of
 * partial pressures. Every test drives its atmos ticks itself and runs within one game tick, so the
 * spread chance it sets with {@link FireConfig#overrideHotspotsForTest} is the only one its ticks
 * see. A test leaves its rooms with nothing to burn, so their fires go out with later ticks.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HotspotTests {
    static final ResourceLocation H2 = gas("hydrogen");
    static final ResourceLocation H2O = gas("water_vapor");
    static final ResourceLocation CO2 = gas("carbon_dioxide");
    static final ResourceLocation CO = gas("carbon_monoxide");
    static final ResourceLocation CH4 = gas("methane");
    static final ResourceLocation N2O = gas("nitrous_oxide");

    /** Every shipped gas, a lot of it: removing this empties a room. */
    static final Map<ResourceLocation, Double> EVERYTHING =
            Map.of(N2, 1e6, O2, 1e6, H2, 1e6, H2O, 1e6, CO2, 1e6, CO, 1e6, CH4, 1e6, N2O, 1e6);

    private HotspotTests() {}

    private static ResourceLocation gas(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path);
    }

    // ---- Helpers ----------------------------------------------------------------------

    /** The test's own area, apart from every other test's; chunk-aligned. */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(12288 + 256 * index, 100, 12288));
    }

    /** Partial pressures, kPa: {@code h2} of hydrogen's share of 1 atm, air for the rest. */
    static Map<ResourceLocation, Double> hydrogenInAir(double h2) {
        double atm = 101.325;
        return Map.of(H2, h2 * atm, N2, (1 - h2) * 0.79 * atm, O2, (1 - h2) * 0.21 * atm);
    }

    /**
     * Builds a sealed stone room whose interior is x0 + 1 to x0 + w, y 1 to h and z 1 to d, forms
     * its zone and fills it with partial pressures in kPa at {@code temperature}.
     */
    static ZoneInfo room(Rooms r, int x0, int w, int h, int d, double temperature, Map<ResourceLocation, Double> kPa) {
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, 0, 0), r.abs(x0 + w + 1, h + 1, d + 1))) {
            int x = p.getX() - r.origin.getX();
            int y = p.getY() - r.origin.getY();
            int z = p.getZ() - r.origin.getZ();
            boolean wall = x == x0 || x == x0 + w + 1 || y == 0 || y == h + 1 || z == 0 || z == d + 1;
            r.level.setBlockAndUpdate(p, wall ? Rooms.STONE : Rooms.AIR);
        }
        var at = r.point(x0 + 1, 1, 1);
        r.settle(GasMutations.remove(r.level, at, EVERYTHING, Rooms.source("hotspots")));
        double volume = r.zone(x0 + 1, 1, 1).volume();
        Map<ResourceLocation, Double> moles = new HashMap<>();
        kPa.forEach((g, p) -> moles.put(g, p * volume / (GasMixture.GAS_CONSTANT * temperature)));
        r.settle(GasMutations.add(r.level, at, new GasParcel(moles, temperature), Rooms.source("hotspots")));
        return r.zone(x0 + 1, 1, 1);
    }

    /** A 3 × 3 × 3 room from x0 with 10 % hydrogen in air at 20 °C. */
    static ZoneInfo hydrogenRoom(Rooms r, int x0) {
        return room(r, x0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
    }

    /** Leaves a room with nothing to burn, so its fires go out with later ticks. */
    static void empty(Rooms r, int x0) {
        r.settle(GasMutations.remove(r.level, r.point(x0 + 1, 1, 1), EVERYTHING, Rooms.source("hotspots")));
    }

    /** Runs a body with the spread chance set, and heat off if {@code heat} is false. */
    static void with(double spreadChance, boolean heat, Runnable body) {
        FireConfig.overrideHotspotsForTest(new HotspotSettings(spreadChance));
        try {
            if (heat) {
                body.run();
            } else {
                Rooms.withoutHeat(body);
            }
        } finally {
            FireConfig.overrideHotspotsForTest(null);
        }
    }

    static Ignited ignite(Rooms r, BlockPos pos) {
        return r.settle(Rooms.service().ignite(r.level, pos, Rooms.source("hotspots")));
    }

    static boolean gasFire(Rooms r, BlockPos pos) {
        return r.level.getBlockState(pos).is(HotspotBlocks.GAS_FIRE.get());
    }

    static HotspotTable hotspots(Rooms r) {
        return r.dimension().pipeline().hotspots();
    }

    static double fraction(ZoneInfo z, ResourceLocation gas) {
        return z.mixture().moles(gas) / z.mixture().totalMoles();
    }

    static double seconds() {
        return ZasServerConfig.scheduler().periodSeconds();
    }

    // ---- FIRE-018, FIRE-013: the gas fire block ------------------------------------------

    /**
     * FIRE-018: an ignition in mid-air shows a {@code zas:gas_fire} block there, on air: it gives
     * light, has no collision, and stays alight while its gas burns.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_018_gas_fire_burns_in_mid_air(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 0);
            hydrogenRoom(r, 0);
            BlockPos middle = r.abs(2, 2, 2);
            Ignited i = ignite(r, middle);
            h.assertValueEqual(i.status(), OpOutcome.IgnitionStatus.IGNITED, "the ignition");
            h.assertValueEqual(i.hotspot(), middle, "the hotspot");
            h.assertTrue(gasFire(r, middle), "no gas fire where it ignited");
            h.assertTrue(r.level.getBlockState(middle.below()).isAir(), "the gas fire isn't in mid-air");
            BlockState state = r.level.getBlockState(middle);
            h.assertValueEqual(state.getLightEmission(r.level, middle), 15, "its light");
            h.assertTrue(state.getCollisionShape(r.level, middle).isEmpty(), "it has collision");
            h.assertTrue(state.canBeReplaced(), "a block can't be placed in it");
            r.ticks(3);
            h.assertTrue(gasFire(r, middle), "the gas fire went out while its gas burns");
            h.assertTrue(hotspots(r).zoneAt(middle.asLong()) != 0, "it isn't a hotspot");
            empty(r, 0);
            r.tick();
            h.assertFalse(gasFire(r, middle), "the gas fire stayed without gas");
        });
        h.succeed();
    }

    /** FIRE-013: a pig in a gas fire is hurt by fire and set alight. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_013_entity_in_gas_fire_is_hurt_and_set_alight(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 1);
            hydrogenRoom(r, 0);
            BlockPos middle = r.abs(2, 1, 2);
            ignite(r, middle);
            h.assertTrue(gasFire(r, middle), "no gas fire");
            Pig pig = EntityType.PIG.create(r.level);
            if (pig == null) {
                throw new IllegalStateException("no pig");
            }
            pig.moveTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
            r.level.addFreshEntity(pig);
            try {
                float before = pig.getHealth();
                pig.tick();
                h.assertTrue(pig.getHealth() < before, "the pig wasn't hurt: " + pig.getHealth());
                h.assertTrue(pig.isOnFire(), "the pig isn't alight");
                h.assertValueEqual(
                        pig.getLastDamageSource() == null
                                ? null
                                : pig.getLastDamageSource().type(),
                        r.level.damageSources().inFire().type(),
                        "the damage");
            } finally {
                pig.discard();
                empty(r, 0);
            }
        });
        h.succeed();
    }

    // ---- FIRE-011: burn rate ------------------------------------------------------------

    /**
     * FIRE-011: a hotspot burns 375 L of its zone's gas a second: in a 27,000 L room, 375/27,000 of
     * its hydrogen each second.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_011_hotspot_burns_375_litres_a_second(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 2);
            hydrogenRoom(r, 0);
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            ZoneInfo before = r.zone(2, 2, 2);
            h.assertTrue(hotspots(r).zoneAt(middle.asLong()) == 0, "burning before its block was seen");
            r.tick();
            h.assertTrue(hotspots(r).zoneAt(middle.asLong()) != 0, "the gas fire isn't a hotspot");
            ZoneInfo after = r.zone(2, 2, 2);
            double share = 375 * seconds() / before.volume();
            double burned = before.mixture().moles(H2) - after.mixture().moles(H2);
            r.close(burned, before.mixture().moles(H2) * share, "hydrogen burned in one tick");
            r.close(after.mixture().moles(H2O) - before.mixture().moles(H2O), burned, "water made");
            h.assertTrue(
                    after.mixture().temperature() > before.mixture().temperature() + 10,
                    "the burn didn't heat the room: " + after.mixture().temperature());
            empty(r, 0);
        });
        h.succeed();
    }

    // ---- FIRE-016: spread ---------------------------------------------------------------

    /**
     * FIRE-016: a hotspot spreads to each neighbouring cell that can burn with the configured chance
     * per second. A 30 × 1 × 30 room holds 100 gas fires 3 apart, whose 400 neighbours are all
     * different; with a chance of 0.6, about 240 of them catch in one second.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_016_spread_matches_the_configured_chance(GameTestHelper h) {
        with(0.6, false, () -> {
            Rooms r = rooms(h, 3);
            room(r, 0, 30, 1, 30, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            BlockState fire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
            for (int x = 1; x < 30; x += 3) {
                for (int z = 1; z < 30; z += 3) {
                    r.set(x + 1, 1, z + 1, fire);
                }
            }
            r.tick();
            int lit = 0;
            int trials = 0;
            for (int x = 1; x < 30; x += 3) {
                for (int z = 1; z < 30; z += 3) {
                    h.assertTrue(gasFire(r, r.abs(x + 1, 1, z + 1)), "a fire went out");
                    for (int[] n : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        trials++;
                        if (gasFire(r, r.abs(x + 1 + n[0], 1, z + 1 + n[1]))) {
                            lit++;
                        }
                    }
                }
            }
            h.assertValueEqual(trials, 400, "trials");
            double expected = 1 - Math.pow(1 - 0.6, seconds());
            double rate = lit / (double) trials;
            // Binomial: σ of the rate is √(0.6 · 0.4 / 400) ≈ 0.024.
            h.assertTrue(Math.abs(rate - expected) <= 0.08, "spread rate " + rate + ", expected " + expected);
            empty(r, 0);
        });
        h.succeed();
    }

    // ---- FIRE-017: going out ------------------------------------------------------------

    /**
     * FIRE-017 (S-04): a sealed gas fire with plenty of hydrogen goes out once its burning brings
     * the O₂ fraction below the fire oxygen threshold (FIRE-030), with fuel left.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_017_gas_fire_goes_out_without_oxygen(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 4);
            room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.25));
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            int ticks = 0;
            while (gasFire(r, middle) && ticks < 80) {
                r.tick();
                ticks++;
            }
            h.assertFalse(gasFire(r, middle), "the gas fire still burns after " + ticks + " ticks");
            ZoneInfo z = r.zone(2, 2, 2);
            h.assertFalse(sustains(z), "the room still sustains fire: O2 " + fraction(z, O2));
            h.assertTrue(fraction(z, H2) > 0.04, "the hydrogen ran out first: " + fraction(z, H2));
            empty(r, 0);
        });
        h.succeed();
    }

    /** FIRE-017: a gas fire goes out once its hydrogen falls below its lower limit, 4 %, with O₂ left. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_017_gas_fire_goes_out_without_fuel(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 5);
            room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.045));
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            h.assertTrue(gasFire(r, middle), "it didn't ignite");
            int ticks = 0;
            while (gasFire(r, middle) && ticks < 80) {
                r.tick();
                ticks++;
            }
            h.assertFalse(gasFire(r, middle), "the gas fire still burns after " + ticks + " ticks");
            ZoneInfo z = r.zone(2, 2, 2);
            h.assertTrue(fraction(z, H2) < 0.04, "hydrogen left: " + fraction(z, H2));
            h.assertTrue(sustains(z), "the O2 ran out first: " + fraction(z, O2));
            empty(r, 0);
        });
        h.succeed();
    }

    /** Whether a zone's gas passes the fire oxygen threshold (FIRE-030). */
    private static boolean sustains(ZoneInfo z) {
        return FireConfig.settings().sustains(fraction(z, O2), z.mixture().partialPressure(O2));
    }

    // ---- FIRE-019: vanilla fire --------------------------------------------------------

    /**
     * FIRE-019: a vanilla fire in a zone is a hotspot: it burns 375 L a second of the zone's
     * hydrogen, and stays a vanilla fire.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_019_vanilla_fire_burns_zone_gas(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 6);
            hydrogenRoom(r, 0);
            r.set(2, 0, 2, Blocks.NETHERRACK.defaultBlockState());
            BlockPos fire = r.abs(2, 1, 2);
            r.set(2, 1, 2, Blocks.FIRE.defaultBlockState());
            ZoneInfo before = r.zone(2, 2, 2);
            r.tick();
            ZoneInfo after = r.zone(2, 2, 2);
            h.assertTrue(hotspots(r).zoneAt(fire.asLong()) != 0, "the fire isn't a hotspot");
            r.close(
                    before.mixture().moles(H2) - after.mixture().moles(H2),
                    before.mixture().moles(H2) * 375 * seconds() / before.volume(),
                    "hydrogen burned in one tick");
            h.assertTrue(r.level.getBlockState(fire).is(Blocks.FIRE), "the fire isn't vanilla fire any more");
            for (BlockPos p : BlockPos.betweenClosed(r.abs(1, 1, 1), r.abs(3, 3, 3))) {
                h.assertFalse(gasFire(r, p), "a gas fire at " + p);
            }
            r.set(2, 1, 2, Rooms.AIR);
            empty(r, 0);
        });
        h.succeed();
    }

    /** FIRE-019, FIRE-018: the geometry table flags fire blocks and gas fires, and nothing else. */
    @GameTest(template = "empty")
    public static void fire_019_fire_blocks_are_flagged(GameTestHelper h) {
        var table = Geometries.table();
        h.assertTrue(table.geometry(Blocks.FIRE.defaultBlockState()).hasFlag(Geometry.FLAG_FIRE), "fire");
        h.assertTrue(table.geometry(Blocks.SOUL_FIRE.defaultBlockState()).hasFlag(Geometry.FLAG_FIRE), "soul fire");
        Geometry gas = table.geometry(HotspotBlocks.GAS_FIRE.get().defaultBlockState());
        h.assertTrue(gas.hasFlag(Geometry.FLAG_GAS_FIRE) && !gas.hasFlag(Geometry.FLAG_FIRE), "gas fire");
        h.assertFalse(table.geometry(Rooms.AIR).hasFlag(Geometry.FLAG_FIRE), "air");
        h.assertFalse(table.geometry(Blocks.CAMPFIRE.defaultBlockState()).hasFlag(Geometry.FLAG_FIRE), "a campfire");
        h.succeed();
    }

    // ---- FIRE-010: autoignition --------------------------------------------------------

    /** FIRE-010: 10 % hydrogen in air ignites by itself at 900 K, above its 858 K, and not at 800 K. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_010_hydrogen_autoignites_at_900_k_not_800_k(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 7);
            room(r, 0, 3, 3, 3, 900, hydrogenInAir(0.10));
            room(r, 6, 3, 3, 3, 800, hydrogenInAir(0.10));
            r.ticks(3);
            h.assertTrue(fires(r, 0) > 0, "the room at 900 K didn't ignite");
            h.assertValueEqual(fires(r, 6), 0, "gas fires in the room at 800 K");
            empty(r, 0);
            empty(r, 6);
        });
        h.succeed();
    }

    /** The gas fires in the 3 × 3 × 3 room from x0. */
    static int fires(Rooms r, int x0) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0 + 1, 1, 1), r.abs(x0 + 3, 3, 3))) {
            if (gasFire(r, p)) {
                n++;
            }
        }
        return n;
    }

    // ---- FIRE-014: the ignition entry point ----------------------------------------------

    /**
     * FIRE-014: an ignition says what it found: open air, a zone whose gas won't burn, a new fire,
     * or the fire already burning there.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_014_ignition_outcomes(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 8);
            r.clear(0, 12);
            h.assertValueEqual(ignite(r, r.abs(1, 5, 1)).status(), OpOutcome.IgnitionStatus.OPEN, "in open air");
            room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0));
            Ignited air = ignite(r, r.abs(2, 2, 2));
            h.assertValueEqual(air.status(), OpOutcome.IgnitionStatus.NOT_FLAMMABLE, "in air");
            h.assertValueEqual(air.zoneId(), r.zone(2, 2, 2).id().value(), "its zone");
            hydrogenRoom(r, 6);
            Ignited lit = ignite(r, r.abs(8, 2, 2));
            h.assertValueEqual(lit.status(), OpOutcome.IgnitionStatus.IGNITED, "in hydrogen");
            r.tick();
            Ignited again = ignite(r, r.abs(9, 2, 2));
            h.assertValueEqual(again.status(), OpOutcome.IgnitionStatus.BURNING, "next to the fire");
            h.assertValueEqual(again.hotspot(), r.abs(8, 2, 2), "the fire found");
            empty(r, 6);
        });
        h.succeed();
    }

    /**
     * FIRE-014: a state the ignition-source predicate accepts is flagged in the geometry table, and
     * a flammable zone at it catches fire by itself. An end rod stands in for a source here.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_014_standing_source_ignites_its_zone(GameTestHelper h) {
        Predicate<BlockState> was = Geometries.ignitionSources();
        Geometries.setIgnitionSources(was.or(s -> s.is(Blocks.END_ROD)));
        try {
            Geometries.rebuildForTest();
            h.assertTrue(
                    Geometries.table()
                            .geometry(Blocks.END_ROD.defaultBlockState())
                            .hasFlag(Geometry.FLAG_IGNITION_SOURCE),
                    "the end rod isn't flagged");
            with(0, true, () -> {
                Rooms r = rooms(h, 9);
                hydrogenRoom(r, 0);
                r.set(2, 1, 2, Blocks.END_ROD.defaultBlockState());
                int ticks = 0;
                while (fires(r, 0) == 0 && ticks < 6) {
                    r.tick();
                    ticks++;
                }
                h.assertTrue(fires(r, 0) > 0, "the room didn't catch fire from its source");
                r.set(2, 1, 2, Rooms.AIR);
                empty(r, 0);
            });
        } finally {
            Geometries.setIgnitionSources(was);
            Geometries.rebuildForTest();
        }
        h.succeed();
    }

    // ---- DATA-009: hotspots aren't saved -----------------------------------------------

    /**
     * DATA-009: hotspots aren't saved: when a burning room's chunk unloads its hotspot goes, and
     * when it loads again the hotspot comes back from the gas fire block and burns on.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void data_009_hotspots_rebuilt_after_reload(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 10);
            hydrogenRoom(r, 0);
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            r.tick();
            h.assertTrue(hotspots(r).zoneAt(middle.asLong()) != 0, "not burning before the unload");
            DimensionAtmos d = r.dimension();
            byte[] saved = d.simulateUnload(r.chunk(0));
            r.tick();
            h.assertValueEqual(hotspots(r).zoneAt(middle.asLong()), 0L, "a hotspot in an unloaded chunk");
            h.assertTrue(gasFire(r, middle), "the gas fire block went with the unload");
            d.simulateLoad(r.chunk(0), saved);
            int ticks = 0;
            while (hotspots(r).zoneAt(middle.asLong()) == 0 && ticks < 6) {
                r.tick();
                ticks++;
            }
            h.assertTrue(hotspots(r).zoneAt(middle.asLong()) != 0, "the hotspot didn't come back");
            double h2 = r.zone(2, 2, 2).mixture().moles(H2);
            r.tick();
            h.assertTrue(r.zone(2, 2, 2).mixture().moles(H2) < h2, "it doesn't burn after the reload");
            empty(r, 0);
        });
        h.succeed();
    }
}
