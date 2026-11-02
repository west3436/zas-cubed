package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Conduction in a real level (HEAT-001, HEAT-002, HEAT-011, HEAT-013, HEAT-014).
 *
 * <p>Each room is a single air block, or a row of them, walled in stone, in an area of its own, so
 * every heat path is a straight line of blocks. A step is checked exactly where it can be: a zone
 * coupled to reservoirs of conductance G at T_eq moves as T' − T_eq = (T − T_eq)·e^(−G·dt/C).
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatTests {
    /** The test datapack's heat source: {@code #zas_gametest:heat_source}, 400 K. */
    private static final BlockState SOURCE = Blocks.AMETHYST_BLOCK.defaultBlockState();

    private HeatTests() {}

    /** Fills the box from (x0, y0, z0) to (x1, y1, z1) with {@code state}. */
    private static void fill(Rooms r, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(r.abs(x0, y0, z0), r.abs(x1, y1, z1))) {
            r.level.setBlockAndUpdate(p, state);
        }
    }

    /** Adds oxygen at a temperature at the point. */
    private static java.util.concurrent.CompletableFuture<MutationResult> add(
            Rooms r, int x, int y, int z, double moles, double temperature) {
        return GasMutations.add(
                r.level, r.point(x, y, z), new GasParcel(Map.of(O2, moles), temperature), source("heat"));
    }

    /** Adds oxygen at a temperature to the room at the point, which must zone. */
    private static void zone(Rooms r, int x, int y, int z, double moles, double temperature) {
        MutationResult m = r.settle(add(r, x, y, z, moles, temperature));
        r.h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "the room didn't zone");
    }

    private static double heatCapacity(ZoneInfo z) {
        return GasProperties.heatCapacity(z.mixture().moles());
    }

    private static double ambientTemperature(Rooms r) {
        return Atmospheres.at(r.level, r.abs(0, 30, 0)).temperature();
    }

    /** Checks one atmos tick moves the zone at the point toward {@code teq} at conductance {@code g}. */
    private static void assertStep(Rooms r, int x, int y, int z, double g, double teq, String what) {
        ZoneInfo before = r.zone(x, y, z);
        r.tick();
        ZoneInfo after = r.zone(x, y, z);
        double dt = Atmospheres.periodTicks() / 20.0;
        double t = before.mixture().temperature();
        double expected = teq + (t - teq) * Math.exp(-g * dt / heatCapacity(before));
        double actual = after.mixture().temperature();
        r.h.assertTrue(
                Math.abs(actual - expected) < 1e-6,
                what + ": expected " + expected + " K, got " + actual + " K (from " + t + " K)");
        r.h.assertTrue(Math.abs(actual - t) > 1e-3, what + ": the temperature didn't move");
    }

    /**
     * HEAT-001, HEAT-011, far side a cell: a hot room heats the open air through each of its six
     * one-block stone walls, at stone's U.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heat_011_far_side_is_an_unzoned_cell(GameTestHelper h) {
        Rooms r = new Rooms(h, 140);
        fill(r, 0, 0, 0, 6, 6, 6, AIR);
        fill(r, 2, 2, 2, 4, 4, 4, STONE);
        r.set(3, 3, 3, AIR);
        zone(r, 3, 3, 3, 30, 400);
        assertStep(r, 3, 3, 3, 6 * 1.7, ambientTemperature(r), "six stone walls to the open air");
        h.succeed();
    }

    /**
     * HEAT-001, HEAT-011, far side a zone: two rooms either side of a stone block exchange heat
     * through it, conserving their energy.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heat_011_far_side_is_another_zone(GameTestHelper h) {
        Rooms r = new Rooms(h, 141);
        fill(r, 0, 0, 0, 12, 12, 12, STONE);
        r.set(5, 6, 6, AIR);
        r.set(7, 6, 6, AIR);
        zone(r, 5, 6, 6, 30, 400);
        zone(r, 7, 6, 6, 30, 250);
        ZoneInfo a = r.zone(5, 6, 6);
        ZoneInfo b = r.zone(7, 6, 6);
        h.assertFalse(a.id().equals(b.id()), "the rooms are one zone");
        r.tick();
        ZoneInfo a2 = r.zone(5, 6, 6);
        ZoneInfo b2 = r.zone(7, 6, 6);
        double ta = a.mixture().temperature();
        double tb = b.mixture().temperature();
        h.assertTrue(a2.mixture().temperature() < ta, "the hot room didn't cool");
        h.assertTrue(b2.mixture().temperature() > tb, "the cold room didn't warm");
        h.assertTrue(
                a2.mixture().temperature() > b2.mixture().temperature(), "the rooms crossed each other's temperature");
        h.succeed();
    }

    /**
     * HEAT-011, far side a heat source: a room with the test heat source (400 K, U 3) beyond one
     * stone wall and three blocks of stone elsewhere moves toward it.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heat_011_far_side_is_a_heat_source(GameTestHelper h) {
        Rooms r = new Rooms(h, 142);
        fill(r, 0, 0, 0, 12, 12, 12, STONE);
        r.set(6, 6, 6, AIR);
        r.set(8, 6, 6, SOURCE);
        zone(r, 6, 6, 6, 30, 293.15);
        double ground = ambientTemperature(r);
        // East: one stone layer then the source's own layer. The other five faces: three stone
        // layers to the ground at the ambient temperature.
        double gSource = 1 / (1 / 1.7 + 1 / 3.0);
        double gGround = 5 * 1.7 / 3;
        double teq = (gSource * 400 + gGround * ground) / (gSource + gGround);
        assertStep(r, 6, 6, 6, gSource + gGround, teq, "source and ground");
        h.succeed();
    }

    /**
     * HEAT-011, nothing within three blocks: a room buried in stone conducts through three layers
     * on every side to the ground, at the ambient temperature.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heat_011_nothing_within_three_blocks(GameTestHelper h) {
        Rooms r = new Rooms(h, 143);
        fill(r, 0, 0, 0, 12, 12, 12, STONE);
        r.set(6, 6, 6, AIR);
        zone(r, 6, 6, 6, 30, 400);
        assertStep(r, 6, 6, 6, 6 * 1.7 / 3, ambientTemperature(r), "the ground");
        h.succeed();
    }

    /**
     * HEAT-013: two rooms divided by a glass pane exchange heat across it at glass's U, 5.8 over the
     * pane's 1 m² cross-section; beside them, two rooms divided by stone exchange at 1.7. Each
     * conductance is measured from the heat the cold room gains in one atmos tick, less what it
     * gains from the ground, to within 2 % (the tick is short against the rooms' time constants).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heat_013_pane_between_rooms(GameTestHelper h) {
        Rooms r = new Rooms(h, 144);
        fill(r, 0, 0, 0, 12, 12, 12, STONE);
        // Two pairs of rooms: one divided by a pane, one by stone.
        r.set(5, 4, 6, AIR);
        r.set(
                6,
                4,
                6,
                Blocks.GLASS_PANE
                        .defaultBlockState()
                        .setValue(IronBarsBlock.NORTH, true)
                        .setValue(IronBarsBlock.SOUTH, true));
        r.set(7, 4, 6, AIR);
        r.set(5, 8, 6, AIR);
        r.set(7, 8, 6, AIR);
        for (int y : new int[] {4, 8}) {
            zone(r, 5, y, 6, 30, 400);
            zone(r, 7, y, 6, 30, 250);
        }
        h.assertFalse(r.zone(5, 4, 6).id().equals(r.zone(7, 4, 6).id()), "the pane didn't divide the rooms");
        double ground = ambientTemperature(r);
        // The cold room's own faces reach the ground through three stone layers: five whole
        // faces, and with the pane, its east half's four sides of 7/16 m² each.
        double stoneGround = 5 * 1.7 / 3;
        double paneGround = stoneGround + 4 * (7.0 / 16) * 1.7 / 3;
        ZoneInfo hot = r.zone(5, 4, 6);
        ZoneInfo cold = r.zone(7, 4, 6);
        ZoneInfo hotControl = r.zone(5, 8, 6);
        ZoneInfo coldControl = r.zone(7, 8, 6);
        r.tick();
        double dt = Atmospheres.periodTicks() / 20.0;
        double pane = conductance(hot, cold, r.zone(7, 4, 6), paneGround, ground, dt);
        double stone = conductance(hotControl, coldControl, r.zone(7, 8, 6), stoneGround, ground, dt);
        h.assertTrue(Math.abs(pane - 5.8) < 0.02 * 5.8, "through the pane: " + pane + " W/K, expected 5.8");
        h.assertTrue(Math.abs(stone - 1.7) < 0.02 * 1.7, "through stone: " + stone + " W/K, expected 1.7");
        h.succeed();
    }

    /** The conductance between two rooms, from the heat the cold one gained less the ground's share. */
    private static double conductance(
            ZoneInfo hot, ZoneInfo cold, ZoneInfo coldAfter, double groundG, double ground, double dt) {
        double tc = cold.mixture().temperature();
        double gained = heatCapacity(cold) * (coldAfter.mixture().temperature() - tc);
        double fromGround = groundG * (ground - tc) * dt;
        return (gained - fromGround) / ((hot.mixture().temperature() - tc) * dt);
    }

    /**
     * HEAT-002: heat data loads from datapacks, each field resolved on its own: the test tag gives
     * the source's temperature and U, and a file naming the sea lantern directly overrides only its
     * U. An invalid file is skipped naming its field.
     */
    @GameTest(template = "empty")
    public static void heat_002_datapack_heat_data(GameTestHelper h) {
        Geometry amethyst = geometry(Blocks.AMETHYST_BLOCK);
        h.assertValueEqual(amethyst.transmittance(), 3.0, "the tag's U");
        h.assertValueEqual(amethyst.sourceTemperature(), 400.0, "the tag's temperature");
        Geometry lantern = geometry(Blocks.SEA_LANTERN);
        h.assertValueEqual(lantern.transmittance(), 2.0, "the direct U beats the tag's");
        h.assertValueEqual(lantern.sourceTemperature(), 400.0, "the tag's temperature stays");
        h.assertTrue(Double.isNaN(geometry(Blocks.SPONGE).transmittance()), "the invalid file applied");
        h.assertTrue(
                DatapackErrors.recent().stream()
                        .anyMatch(p -> p.kind().equals("thermal data")
                                && p.file().equals("data/zas_gametest/zas/thermal/invalid_transmittance.json")
                                && p.message().startsWith("transmittance: ")),
                "the invalid file wasn't skipped naming its field: " + DatapackErrors.recent());
        h.succeed();
    }

    /** HEAT-014: the shipped transmittances. */
    @GameTest(template = "empty")
    public static void heat_014_default_transmittances(GameTestHelper h) {
        expectU(h, Blocks.WHITE_WOOL, 0.04);
        expectU(h, Blocks.RED_CARPET, 0.04);
        expectU(h, Blocks.OAK_PLANKS, 0.15);
        expectU(h, Blocks.SPRUCE_LOG, 0.15);
        expectU(h, Blocks.OAK_DOOR, 0.15);
        expectU(h, Blocks.STONE, 1.7);
        expectU(h, Blocks.COBBLESTONE, 1.7);
        expectU(h, Blocks.DEEPSLATE, 1.7);
        expectU(h, Blocks.ICE, 2.2);
        expectU(h, Blocks.GLASS, 5.8);
        expectU(h, Blocks.GLASS_PANE, 5.8);
        expectU(h, Blocks.WHITE_STAINED_GLASS, 5.8);
        expectU(h, Blocks.IRON_BLOCK, 50);
        expectU(h, Blocks.COPPER_BLOCK, 50);
        expectU(h, Blocks.IRON_BARS, 50);
        expectU(h, Blocks.IRON_DOOR, 50);
        h.assertTrue(Double.isNaN(geometry(Blocks.DIRT).transmittance()), "dirt has no U: the fallback");
        h.assertValueEqual(ZasServerConfig.heat().fallbackTransmittance(), 1.7, "the fallback (HEAT-015)");
        h.succeed();
    }

    private static Geometry geometry(Block block) {
        GeometryTable t = Geometries.table();
        return t.palette().geometry(t.geometryId(block.defaultBlockState()));
    }

    private static void expectU(GameTestHelper h, Block block, double u) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            GeometryTable t = Geometries.table();
            double actual = t.palette().geometry(t.geometryId(state)).transmittance();
            if (actual != u) {
                h.fail(state + ": U " + actual + ", expected " + u);
            }
        }
    }
}
