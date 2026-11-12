package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The station blocks (CONT-129 to CONT-131): the station wall, the reinforced station wall and
 * reinforced glass, none of which conducts heat (HEAT-019), and the last of which no pressure
 * difference breaks. Each test
 * builds in its own {@link Site} and drives its atmos ticks itself, within one game tick. The
 * vacuum test borrows the End, as S-05 does, and the Hardcore test sets the preset, so each of
 * those has a batch of its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationBlockTests {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final double T = StandardAir.TEMPERATURE;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));
    /** Atmos ticks each heat test watches its rooms for: 20 s. */
    private static final int SECONDS = 20;

    private StationBlockTests() {}

    private static BlockState stationWall() {
        return AtmosBlocks.STATION_WALL.get().defaultBlockState();
    }

    private static BlockState reinforcedWall() {
        return AtmosBlocks.REINFORCED_STATION_WALL.get().defaultBlockState();
    }

    private static BlockState reinforcedGlass() {
        return AtmosBlocks.REINFORCED_GLASS_BLOCK.get().defaultBlockState();
    }

    /** A hollow box from (x0, 0, 0) to (x1, 4, 4) walled with {@code wall}: a 3 × 3 × 3 room for x0 + 4 = x1. */
    private static void room(Site s, int x0, int x1, BlockState wall) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean edge = x == x0 || x == x1 || y == 0 || y == 4 || z == 0 || z == 4;
                    s.set(x, y, z, edge ? wall : Site.AIR);
                }
            }
        }
    }

    /** A 3 × 3 window of reinforced glass in the wall of a room at {@code x}, the whole wall but its edges. */
    private static void window(Site s, int x) {
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                s.set(x, y, z, reinforcedGlass());
            }
        }
    }

    /** Forms the zone of a sealed room, which then holds the level's air, with a mole of nitrogen. */
    private static ZoneInfo form(Site s, int x, int y, int z) {
        s.settle(GasMutations.add(s.level, s.point(x, y, z), new GasParcel(Map.of(N2, 1.0), T), Site.SOURCE));
        return s.zone(x, y, z);
    }

    /** Fills the zone of a sealed room with standard air at {@code T}, from empty, as S-05 does in a vacuum. */
    private static void fillAir(Site s, int x, int y, int z) {
        s.settle(GasMutations.add(s.level, s.point(x, y, z), new GasParcel(Map.of(N2, 1e-6), T), Site.SOURCE));
        double moles = StandardAir.PRESSURE * s.zone(x, y, z).mixture().volume() / (GasMixture.GAS_CONSTANT * T);
        s.settle(GasMutations.add(
                s.level,
                s.point(x, y, z),
                new GasParcel(
                        Map.of(N2, moles * (1 - StandardAir.OXYGEN_FRACTION), O2, moles * StandardAir.OXYGEN_FRACTION),
                        T),
                Site.SOURCE));
    }

    private static double temperature(Site s, int x, int y, int z) {
        return s.zone(x, y, z).mixture().temperature();
    }

    /**
     * Ticks {@link #SECONDS} times and checks each room in {@code still} keeps its temperature every
     * tick, and the stone room at {@code control} moves by more than 1 K, so heat was flowing.
     */
    private static void watch(Site s, int[][] still, String[] names, int[] control, String what) {
        double[] start = new double[still.length];
        for (int i = 0; i < still.length; i++) {
            start[i] = temperature(s, still[i][0], still[i][1], still[i][2]);
        }
        double controlStart = temperature(s, control[0], control[1], control[2]);
        for (int tick = 0; tick < SECONDS; tick++) {
            s.tick();
            for (int i = 0; i < still.length; i++) {
                double t = temperature(s, still[i][0], still[i][1], still[i][2]);
                s.close(t, start[i], 1e-9, "second " + tick + ": the room walled with " + names[i] + " " + what);
            }
        }
        double moved = Math.abs(temperature(s, control[0], control[1], control[2]) - controlStart);
        s.h.assertTrue(moved > 1, "the stone room " + what + " moved only " + moved + " K: no heat was flowing");
    }

    // ---- CONT-129 to CONT-131: the blocks --------------------------------------------------

    /**
     * Each station block is a full gas-tight block in every state: every face sealed, no cell. Each
     * has a U of 0 from the content mod's heat data, the glass too (the owner's call, 2026-11-07).
     * Their blast resistance is stone's 6 for the station wall and obsidian's 1,200 for the reinforced wall
     * and the glass (CONT-129 to CONT-131). The glass is out of {@code #zas:pressure_fragile} and of
     * {@code #c:glass_blocks}, which feeds it, and is seen through as glass is: it occludes nothing
     * and lets skylight through. Each is mined with a pickaxe at the hardness the owner approved on
     * 2026-11-07: 3 and a stone pickaxe or better for the station wall, 25 and a diamond pickaxe or
     * better for the others.
     */
    @GameTest(template = "empty")
    public static void cont_129_station_blocks_seal_and_resist_blasts(GameTestHelper h) {
        block(h, AtmosBlocks.STATION_WALL.get(), 0, 6, 3, BlockTags.NEEDS_STONE_TOOL);
        block(h, AtmosBlocks.REINFORCED_STATION_WALL.get(), 0, 1200, 25, BlockTags.NEEDS_DIAMOND_TOOL);
        block(h, AtmosBlocks.REINFORCED_GLASS_BLOCK.get(), 0, 1200, 25, BlockTags.NEEDS_DIAMOND_TOOL);
        BlockState glass = reinforcedGlass();
        h.assertFalse(glass.is(Geometries.PRESSURE_FRAGILE), "the reinforced glass is in zas:pressure_fragile");
        h.assertFalse(glass.is(Tags.Blocks.GLASS_BLOCKS), "the reinforced glass is in c:glass_blocks");
        h.assertFalse(glass.is(Tags.Blocks.GLASS_PANES), "the reinforced glass is in c:glass_panes");
        h.assertFalse(
                Geometries.table().geometry(glass).hasFlag(Geometry.FLAG_PRESSURE_FRAGILE),
                "the reinforced glass's geometry is fragile");
        h.assertTrue(
                Geometries.table().geometry(Blocks.GLASS.defaultBlockState()).hasFlag(Geometry.FLAG_PRESSURE_FRAGILE),
                "glass isn't fragile, so the check above proves nothing");
        h.assertFalse(glass.canOcclude(), "the reinforced glass hides its neighbours' faces");
        h.assertTrue(glass.propagatesSkylightDown(h.getLevel(), BlockPos.ZERO), "the reinforced glass blocks light");
        h.assertTrue(stationWall().canOcclude(), "the station wall is see-through");
        h.succeed();
    }

    static void block(
            GameTestHelper h, Block block, double u, float blastResistance, float hardness, TagKey<Block> tool) {
        String name = block.getDescriptionId();
        h.assertValueEqual(block.getExplosionResistance(), blastResistance, name + "'s blast resistance");
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            h.assertValueEqual(
                    state.getDestroySpeed(h.getLevel(), BlockPos.ZERO), hardness, "the hardness of " + state);
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), state + " isn't mined with a pickaxe");
            h.assertTrue(state.is(tool), state + " isn't in " + tool.location());
            h.assertTrue(state.requiresCorrectToolForDrops(), state + " drops without a pickaxe");
            Geometry g = Geometries.table().geometry(state);
            for (Face f : Face.values()) {
                h.assertTrue(g.isFaceFull(f), state + " doesn't seal its " + f + " face");
            }
            h.assertValueEqual(g.cellCount(), 0, "the cells of " + state);
            h.assertValueEqual(g.transmittance(), u, "the U of " + state);
        }
    }

    // ---- CONT-129 to CONT-131: heat ----------------------------------------------------------

    /** Lava in the plane x, behind a room's wall: the wall but its edges, which are stone. */
    private static void lava(Site s, int x) {
        for (int y = 0; y <= 4; y++) {
            for (int z = 0; z <= 4; z++) {
                boolean edge = y == 0 || y == 4 || z == 0 || z == 4;
                s.set(x, y, z, edge ? Site.STONE : Blocks.LAVA.defaultBlockState());
            }
        }
    }

    /**
     * CONT-129, CONT-131: a room walled with station walls keeps its temperature beside lava, and so
     * does one whose wall onto the lava is a window of reinforced glass. Lava fills the gaps between
     * them and a stone room in the middle, which warms as a control.
     */
    @GameTest(template = "empty")
    public static void cont_129_cont_131_station_blocks_keep_a_room_at_its_temperature_beside_lava(GameTestHelper h) {
        Site s = new Site(h, 1290);
        try {
            s.clear();
            room(s, 0, 4, stationWall()); // interior x 1–3; the east wall is x = 4
            s.room(6, 10); // stone, interior x 7–9
            room(s, 12, 16, stationWall()); // interior x 13–15; the west wall is x = 12
            window(s, 12);
            lava(s, 5);
            lava(s, 11);
            form(s, 2, 2, 2);
            form(s, 8, 2, 2);
            form(s, 14, 2, 2);
            watch(
                    s,
                    new int[][] {{2, 2, 2}, {14, 2, 2}},
                    new String[] {"station walls", "station walls and a reinforced glass window"},
                    new int[] {8, 2, 2},
                    "beside lava");
            h.succeed();
        } finally {
            s.clear();
        }
    }

    /**
     * CONT-129 to CONT-131: rooms walled with station walls, and with reinforced station walls and a
     * window of reinforced glass, keep their temperature in the test datapack's 2.7 K vacuum. A
     * stone room beside them cools, as a control.
     */
    @GameTest(template = "empty", batch = "zas_atmos_cont_129_vacuum")
    public static void cont_129_to_cont_131_station_blocks_keep_a_room_at_its_temperature_in_a_vacuum(
            GameTestHelper h) {
        inVacuum(h, 1291, s -> {
            room(s, 0, 4, stationWall());
            room(s, 6, 10, reinforcedWall());
            window(s, 10); // onto the vacuum at x = 11
            s.room(12, 16);
            fillAir(s, 2, 2, 2);
            fillAir(s, 8, 2, 2);
            fillAir(s, 14, 2, 2);
            watch(
                    s,
                    new int[][] {{2, 2, 2}, {8, 2, 2}},
                    new String[] {"station walls", "reinforced station walls and a reinforced glass window"},
                    new int[] {14, 2, 2},
                    "in a vacuum");
        });
        h.succeed();
    }

    /** Runs a body in the End with the test datapack's 2.7 K vacuum ambient. */
    private static void inVacuum(GameTestHelper h, int site, Consumer<Site> body) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, site);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            s.clear();
            body.accept(s);
        } finally {
            s.clear();
            Ambients.overrideForTest(Level.END, null);
        }
    }

    // ---- CONT-130, CONT-131: blasts ----------------------------------------------------------

    /**
     * CONT-130, CONT-131: an explosion that breaks a station wall, and glass, leaves a reinforced
     * station wall and reinforced glass standing, each as far from it as the others.
     */
    @GameTest(template = "empty")
    public static void cont_130_cont_131_a_blast_that_breaks_a_station_wall_leaves_the_reinforced_blocks(
            GameTestHelper h) {
        Site s = new Site(h, 1300);
        try {
            s.clear();
            // Each two blocks from the blast at (8, 3, 3).
            s.set(6, 3, 3, stationWall());
            s.set(10, 3, 3, reinforcedWall());
            s.set(8, 3, 5, reinforcedGlass());
            s.set(8, 3, 1, Blocks.GLASS.defaultBlockState());
            Vec3 at = s.point(8, 3, 3);
            s.level.explode(null, at.x, at.y, at.z, 6.0f, Level.ExplosionInteraction.TNT);
            h.assertTrue(s.level.getBlockState(s.abs(6, 3, 3)).isAir(), "the blast didn't break the station wall");
            h.assertTrue(s.level.getBlockState(s.abs(8, 3, 1)).isAir(), "the blast didn't break the glass");
            h.assertValueEqual(
                    s.level.getBlockState(s.abs(10, 3, 3)), reinforcedWall(), "the reinforced station wall after it");
            h.assertValueEqual(
                    s.level.getBlockState(s.abs(8, 3, 5)), reinforcedGlass(), "the reinforced glass after it");
            h.succeed();
        } finally {
            s.clear();
            s.level
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(s.abs(0, 0, 0)).expandTowards(16, 6, 6).inflate(4))
                    .forEach(Entity::discard);
        }
    }

    // ---- CONT-131: pressure ------------------------------------------------------------------

    /**
     * CONT-131: under Hardcore, reinforced glass holds above the threshold that breaks glass. Two
     * rooms have a window onto the open air, one of glass and one of reinforced glass; at the
     * threshold and 1 kPa more over the ambient the glass breaks and the reinforced glass holds,
     * and it still holds at 1,000 kPa more.
     */
    @GameTest(template = "empty", batch = "zas_atmos_cont_131_hardcore")
    public static void cont_131_reinforced_glass_holds_above_the_threshold_that_breaks_glass(GameTestHelper h) {
        h.assertTrue(ZasServerConfig.SPEC.isLoaded(), "the server config isn't loaded");
        h.assertValueEqual(PresetOptions.overridden(), 0, "preset options overridden before the test");
        AtmospherePreset before = ZasServerConfig.preset();
        Site s = new Site(h, 1310);
        try {
            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            ZasServerConfig.overrideHeatForTest(HeatSettings.OFF); // so a pressure stays as set
            s.clear();
            s.room(0, 4);
            s.set(4, 2, 2, reinforcedGlass());
            s.room(8, 12);
            s.set(12, 2, 2, Blocks.GLASS.defaultBlockState());
            double ambient = Atmospheres.at(s.level, s.abs(6, 2, 2)).mixture().pressure();
            double over = ambient + AirflowConfig.fragile().thresholdKpa() + 1;
            raise(s, 2, 2, 2, over);
            raise(s, 10, 2, 2, over);
            s.tick();
            h.assertTrue(s.level.getBlockState(s.abs(12, 2, 2)).isAir(), "the glass held above the threshold");
            h.assertValueEqual(
                    s.level.getBlockState(s.abs(4, 2, 2)),
                    reinforcedGlass(),
                    "the reinforced glass above the threshold");
            raise(s, 2, 2, 2, ambient + 1000);
            s.ticks(2);
            h.assertValueEqual(
                    s.level.getBlockState(s.abs(4, 2, 2)), reinforcedGlass(), "the reinforced glass at 1,000 kPa over");
            h.succeed();
        } finally {
            s.clear();
            ZasServerConfig.overrideHeatForTest(null);
            ZasServerConfig.setPreset(before);
        }
    }

    /** Fills the zone at a point to a pressure, forming it if need be, as the API does. */
    private static void raise(Site s, int x, int y, int z, double kpa) {
        if (!s.zoned(x, y, z)) {
            s.settle(GasMutations.add(s.level, s.point(x, y, z), new GasParcel(Map.of(O2, 0.001), T), Site.SOURCE));
        }
        ZoneInfo zone = s.zone(x, y, z);
        double t = zone.mixture().temperature();
        double moles = (kpa - zone.mixture().pressure()) * zone.volume() / (GasMixture.GAS_CONSTANT * t);
        s.settle(GasMutations.add(s.level, s.point(x, y, z), new GasParcel(Map.of(O2, moles), t), Site.SOURCE));
        s.close(s.zone(x, y, z).mixture().pressure(), kpa, 0.5, "the zone's pressure");
    }
}
