package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
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
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hotspots (FIRE-010, FIRE-011, FIRE-013, FIRE-014, FIRE-016 to FIRE-019, FIRE-032, FIRE-035, DATA-009): the engine's
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
     * per second. Each of seven 30 × 1 × 30 rooms holds 100 gas fires 3 apart, whose 400 neighbours
     * are all different; with a chance of 0.6, about 240 of them catch in one second. It takes seven
     * rooms rather than seven ticks because a neighbour that caught would spread in the next tick.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_016_spread_matches_the_configured_chance(GameTestHelper h) {
        with(0.6, false, () -> {
            Rooms r = rooms(h, 3);
            int count = 7;
            for (int i = 0; i < count; i++) {
                room(r, 33 * i, 30, 1, 30, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            }
            BlockState fire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
            // Once every room is built, so that no fire spreads before the tick that counts.
            for (int i = 0; i < count; i++) {
                for (int x = 1; x < 30; x += 3) {
                    for (int z = 1; z < 30; z += 3) {
                        r.set(33 * i + x + 1, 1, z + 1, fire);
                    }
                }
            }
            r.tick();
            int lit = 0;
            int trials = 0;
            for (int i = 0; i < count; i++) {
                for (int x = 1; x < 30; x += 3) {
                    for (int z = 1; z < 30; z += 3) {
                        h.assertTrue(gasFire(r, r.abs(33 * i + x + 1, 1, z + 1)), "a fire went out");
                        for (int[] n : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                            trials++;
                            if (gasFire(r, r.abs(33 * i + x + 1 + n[0], 1, z + 1 + n[1]))) {
                                lit++;
                            }
                        }
                    }
                }
            }
            h.assertValueEqual(trials, count * 400, "trials");
            double expected = 1 - Math.pow(1 - 0.6, seconds());
            double rate = lit / (double) trials;
            // Binomial: σ of the rate is √(0.6 · 0.4 / 2800) ≈ 0.0093, so 0.06 is 6.5 σ.
            h.assertTrue(Math.abs(rate - expected) <= 0.06, "spread rate " + rate + ", expected " + expected);
            for (int i = 0; i < count; i++) {
                empty(r, 33 * i);
            }
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

    /**
     * FIRE-017: a gas fire goes out once its hydrogen falls below its lower limit, with O₂ left. The
     * fire warms the room, so the limit by then is under the 4 % it has when cold (FIRE-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_017_gas_fire_goes_out_without_fuel(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 5);
            room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.045));
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            h.assertTrue(gasFire(r, middle), "it didn't ignite");
            int ticks = 0;
            while (gasFire(r, middle) && ticks < 400) {
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

    // ---- FIRE-032: the limits widen with temperature ---------------------------------------

    /**
     * FIRE-032: two sealed rooms at 9 % H₂ burn with one gas fire each. One has its fire's heat taken
     * out each tick, so it stays at room temperature and its fire goes out at about 4 %, H₂'s declared
     * limit. The other keeps its heat, which takes it past 600 K, and it burns well below 4 %, with O₂
     * left: the fuel ran out, not the air.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_032_sealed_room_burns_below_the_declared_limit_once_hot(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 14);
            room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.09));
            room(r, 6, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0.09));
            BlockPos kept = r.abs(2, 2, 2);
            BlockPos free = r.abs(8, 2, 2);
            ignite(r, kept);
            ignite(r, free);
            h.assertTrue(gasFire(r, kept) && gasFire(r, free), "they didn't ignite");
            int ticks = 0;
            while ((gasFire(r, kept) || gasFire(r, free)) && ticks < 400) {
                ZoneInfo z = r.zone(2, 2, 2);
                double joules = GasProperties.heatCapacity(z.mixture().moles())
                        * (z.mixture().temperature() - StandardAir.TEMPERATURE);
                if (joules > 0) {
                    HeatMutations.remove(r.level, r.point(2, 2, 2), joules, Rooms.source("hotspots"));
                }
                r.tick();
                ticks++;
            }
            h.assertFalse(gasFire(r, kept), "the cool room still burns after " + ticks + " ticks");
            h.assertFalse(gasFire(r, free), "the hot room still burns after " + ticks + " ticks");

            ZoneInfo cool = r.zone(2, 2, 2);
            h.assertTrue(
                    fraction(cool, H2) < 0.04 && fraction(cool, H2) > 0.039,
                    "the cool room's fire went out at " + fraction(cool, H2) + " H₂");
            h.assertTrue(cool.mixture().temperature() < 320, "the cool room warmed: " + cool.mixture());

            ZoneInfo hot = r.zone(8, 2, 2);
            h.assertTrue(hot.mixture().temperature() > 600, "the hot room stayed cool: " + hot.mixture());
            h.assertTrue(fraction(hot, H2) < 0.002, "the hot room's fire went out at " + fraction(hot, H2) + " H₂");
            h.assertTrue(sustains(hot), "the hot room's O₂ ran out first: " + fraction(hot, O2));
            empty(r, 0);
            empty(r, 6);
        });
        h.succeed();
    }

    // ---- FIRE-035: a gas fire at autoignition needs only the O₂ floor ------------------------

    /** Partial pressures, kPa, at 3 atm: 3 % hydrogen and 12 % O₂, nitrogen for the rest. */
    private static Map<ResourceLocation, Double> shortOfOxygen() {
        double kPa = 3 * 101.325;
        return Map.of(H2, 0.03 * kPa, O2, 0.12 * kPa, N2, 0.85 * kPa);
    }

    /**
     * FIRE-035: sealed rooms of hydrogen at 12 % O₂. At 1,000 K, above hydrogen's 858 K, the gas lights
     * by itself and burns down to the 0.1 % floor, its O₂ still above 10 kPa. At 800 K nothing burns,
     * and an ignition finds it not flammable (FIRE-030). A burning room whose heat is taken out, down
     * to 800 K, goes out with fuel left. And at 1,000 K, with O₂ at 9 kPa, nothing burns.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_035_a_hot_room_burns_below_14_percent_and_a_cooler_one_does_not(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 15);
            room(r, 0, 3, 3, 3, 1000, shortOfOxygen());
            room(r, 6, 3, 3, 3, 800, shortOfOxygen());
            room(r, 12, 3, 3, 3, 1000, shortOfOxygen());
            room(r, 18, 3, 3, 3, 1000, Map.of(H2, 3.0, O2, 9.0, N2, 20.0));
            h.assertTrue(fraction(r.zone(2, 2, 2), O2) < 0.14, "the room isn't short of O2");

            r.ticks(3);
            h.assertTrue(fires(r, 0) > 0, "the room at 1,000 K didn't ignite");
            h.assertTrue(fires(r, 12) > 0, "the room to be cooled didn't ignite");
            h.assertValueEqual(fires(r, 6), 0, "gas fires in the room at 800 K");
            h.assertValueEqual(fires(r, 18), 0, "gas fires in the room with O2 at 9 kPa");
            h.assertValueEqual(
                    ignite(r, r.abs(8, 2, 2)).status(),
                    OpOutcome.IgnitionStatus.NOT_FLAMMABLE,
                    "an ignition at 800 K and 12 % O2");

            ZoneInfo burning = r.zone(14, 2, 2);
            double joules = GasProperties.heatCapacity(burning.mixture().moles())
                    * (burning.mixture().temperature() - 800);
            HeatMutations.remove(r.level, r.point(14, 2, 2), joules, Rooms.source("hotspots"));
            r.ticks(2);
            h.assertValueEqual(fires(r, 12), 0, "the fire burns on after cooling to 800 K");
            h.assertTrue(fraction(r.zone(14, 2, 2), H2) > 0.02, "the cooled room's hydrogen ran out");

            int ticks = 0;
            while (fires(r, 0) > 0 && ticks < 500) {
                r.tick();
                ticks++;
            }
            h.assertValueEqual(fires(r, 0), 0, "the hot room still burns after " + ticks + " ticks");
            ZoneInfo hot = r.zone(2, 2, 2);
            h.assertTrue(
                    fraction(hot, H2) < 0.001 && fraction(hot, H2) > 0.0009,
                    "the hot room's fire went out at " + fraction(hot, H2) + " H₂");
            h.assertTrue(fraction(hot, O2) < 0.14, "the hot room's O₂ rose: " + fraction(hot, O2));
            h.assertTrue(hot.mixture().partialPressure(O2) > 10, "the O₂ reached its floor: " + hot.mixture());
            h.assertValueEqual(r.zone(8, 2, 2).mixture().moles(H2O), 0.0, "water in the room at 800 K");
            h.assertValueEqual(r.zone(20, 2, 2).mixture().moles(H2O), 0.0, "water in the room with O2 at 9 kPa");
            empty(r, 0);
            empty(r, 6);
            empty(r, 12);
            empty(r, 18);
        });
        h.succeed();
    }

    /**
     * FIRE-035: only a gas fire skips the fraction. In gas at 1,000 K and 12 % O₂, flint and steel
     * can't light a fire (M3), and a vanilla fire goes out on its own tick (M2, FIRE-030), while the
     * gas burns on as a gas fire.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_035_a_vanilla_fire_in_hot_gas_keeps_the_oxygen_fraction(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 15);
            room(r, 24, 3, 3, 3, 1000, shortOfOxygen());
            r.set(26, 0, 2, Blocks.NETHERRACK.defaultBlockState());
            BlockPos at = r.abs(26, 1, 2);
            h.assertFalse(BaseFireBlock.canBePlacedAt(r.level, at, Direction.NORTH), "fire can go in at 12 % O₂");

            r.level.setBlockAndUpdate(at, Blocks.FIRE.defaultBlockState());
            r.level.getBlockState(at).tick(r.level, at, r.level.random);
            h.assertTrue(r.level.getBlockState(at).isAir(), "the vanilla fire burns on at 12 % O₂");

            r.ticks(3);
            h.assertTrue(fires(r, 24) > 0, "the hot gas didn't ignite");
            h.assertTrue(r.zone(26, 2, 2).mixture().moles(H2O) > 0, "the hot gas didn't burn");
            empty(r, 24);
        });
        h.succeed();
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

    // ---- FIRE-018: passable blocks ---------------------------------------------------------

    /** Blocks gas passes as it passes air, but that aren't air: no gas fire can go there. */
    private static final BlockState LEVER =
            Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);

    private static final BlockState POPPY = Blocks.POPPY.defaultBlockState();

    /**
     * FIRE-018: a block that isn't air but has air's geometry is flagged occupied, with air's cell;
     * air itself isn't.
     */
    @GameTest(template = "empty")
    public static void fire_018_passable_blocks_are_flagged_occupied(GameTestHelper h) {
        var table = Geometries.table();
        for (BlockState s : new BlockState[] {
            LEVER,
            POPPY,
            Blocks.REDSTONE_WIRE.defaultBlockState(),
            Blocks.RAIL.defaultBlockState(),
            Blocks.STONE_BUTTON.defaultBlockState(),
            Blocks.SHORT_GRASS.defaultBlockState()
        }) {
            Geometry g = table.geometry(s);
            h.assertTrue(g.hasFlag(Geometry.FLAG_OCCUPIED), "not occupied: " + s);
            h.assertValueEqual(g.cells(), Geometry.AIR.cells(), "the cell of " + s);
            h.assertValueEqual(g.coverage(), Geometry.AIR.coverage(), "the coverage of " + s);
        }
        for (BlockState s :
                new BlockState[] {Rooms.AIR, Blocks.CAVE_AIR.defaultBlockState(), Blocks.VOID_AIR.defaultBlockState()
                }) {
            h.assertValueEqual(Geometries.geometryId(s), GeometryPalette.AIR_ID, "the geometry of " + s);
        }
        h.assertFalse(
                table.geometry(Blocks.STONE_SLAB.defaultBlockState()).hasFlag(Geometry.FLAG_OCCUPIED),
                "a slab is occupied");
        h.succeed();
    }

    /**
     * FIRE-018, FIRE-014: an ignition at a lever in a hydrogen room lights the air beside it, not
     * the lever's cell, and the room burns.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_018_ignition_at_a_lever_lights_the_air_beside_it(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 11);
            hydrogenRoom(r, 0);
            BlockPos lever = r.abs(2, 1, 2);
            r.set(2, 1, 2, LEVER);
            Ignited i = ignite(r, lever);
            h.assertValueEqual(i.status(), OpOutcome.IgnitionStatus.IGNITED, "the ignition");
            h.assertTrue(
                    i.hotspot() != null && i.hotspot().distManhattan(lever) == 1,
                    "the fire isn't beside the lever: " + i.hotspot());
            h.assertTrue(gasFire(r, i.hotspot()), "no gas fire beside the lever");
            h.assertTrue(r.level.getBlockState(lever) == LEVER, "the lever changed");
            r.tick();
            h.assertTrue(r.zone(2, 2, 2).mixture().moles(H2O) > 0, "the room didn't burn: no water");
            r.set(2, 1, 2, Rooms.AIR);
            empty(r, 0);
        });
        h.succeed();
    }

    /**
     * FIRE-018, FIRE-016: a fire spreads past flowers, not into them. A gas fire whose four
     * neighbours are poppies, at a spread chance of 1, lights none of them; once one is picked, the
     * fire spreads there at the next tick, so the engine never refused that cell.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_018_spread_skips_flowers(GameTestHelper h) {
        with(1, false, () -> {
            Rooms r = rooms(h, 12);
            room(r, 0, 5, 1, 5, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            int[][] around = {{2, 3}, {4, 3}, {3, 2}, {3, 4}};
            for (int[] c : around) {
                r.set(c[0], 0, c[1], Blocks.DIRT.defaultBlockState());
                r.set(c[0], 1, c[1], POPPY);
            }
            r.set(3, 1, 3, HotspotBlocks.GAS_FIRE.get().defaultBlockState());
            r.ticks(2);
            h.assertTrue(gasFire(r, r.abs(3, 1, 3)), "the fire went out");
            h.assertTrue(hotspots(r).zoneAt(r.abs(3, 1, 3).asLong()) != 0, "the fire isn't a hotspot");
            for (int[] c : around) {
                h.assertTrue(
                        r.level.getBlockState(r.abs(c[0], 1, c[1])) == POPPY, "a poppy went at " + c[0] + ", " + c[1]);
            }
            r.set(4, 1, 3, Rooms.AIR);
            r.tick();
            h.assertTrue(gasFire(r, r.abs(4, 1, 3)), "the fire didn't spread where the poppy was");
            for (int[] c : around) {
                r.set(c[0], 1, c[1], Rooms.AIR);
            }
            empty(r, 0);
        });
        h.succeed();
    }

    /**
     * FIRE-018: placing passable blocks in a zone leaves it as it was: the same zone, volume and
     * gas.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_018_passable_blocks_leave_the_zone_as_air_does(GameTestHelper h) {
        with(0, false, () -> {
            Rooms r = rooms(h, 13);
            ZoneInfo before = room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0));
            r.set(1, 0, 1, Blocks.DIRT.defaultBlockState());
            r.set(1, 1, 1, POPPY);
            r.set(2, 1, 2, LEVER);
            r.set(3, 1, 3, Blocks.REDSTONE_WIRE.defaultBlockState());
            r.set(3, 1, 1, Blocks.RAIL.defaultBlockState());
            r.ticks(3);
            ZoneInfo after = r.zone(2, 2, 2);
            h.assertValueEqual(after.id(), before.id(), "the zone");
            h.assertValueEqual(after.volume(), before.volume(), "its volume");
            r.close(after.mixture().totalMoles(), before.mixture().totalMoles(), "its gas");
            h.assertValueEqual(r.zone(1, 1, 1).id(), before.id(), "the poppy's cell's zone");
            for (BlockPos p : BlockPos.betweenClosed(r.abs(1, 1, 1), r.abs(3, 1, 3))) {
                r.level.setBlockAndUpdate(p, Rooms.AIR);
            }
            empty(r, 0);
        });
        h.succeed();
    }
}
