package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryOverride;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What seals out of the box: the shipped overrides of blocks whose collision shape misleads (GEO-009),
 * and fluids (GEO-006). Areas 650 to 660.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealingDefaultsTests {
    private static final String FLUID_OVERRIDE = "geo_006_override";
    private static final int WEST = 12;
    private static final int EAST = 16;
    private static final int WALL = 14;

    private SealingDefaultsTests() {}

    /** Two rooms sharing a wall at x = 14, with {@code inWall} in it at (14, 2, 2), and gas in the west room. */
    private static Rooms rooms(GameTestHelper h, int index, BlockState inWall) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.box(14, 0, 18, 4);
        r.set(WALL, 2, 2, inWall);
        r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(100), source("geo_009")));
        return r;
    }

    private static void permeable(GameTestHelper h, BlockState state, Geometry g) {
        h.assertTrue(g.hasFlag(Geometry.FLAG_OVERRIDE), state + " isn't overridden");
        h.assertValueEqual(g.cellCount(), 1, state + ": cells");
        h.assertValueEqual(g.volume(), 1000.0, state + ": volume");
        for (Face f : Face.values()) {
            h.assertTrue(g.coverage(f).isEmpty(), state + " covers its " + f + " face");
        }
    }

    private static void sealed(GameTestHelper h, BlockState state, Geometry g) {
        h.assertValueEqual(g.cellCount(), 0, state + ": cells");
        for (Face f : Face.values()) {
            h.assertTrue(g.isFaceFull(f), state + " doesn't seal its " + f + " face");
        }
    }

    /** Every state of the blocks in GEO-009's table has the table's geometry. */
    @GameTest(template = "empty")
    public static void geo_009_shipped_overrides(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        int blocks = 0;
        for (TagKey<Block> tag : List.of(BlockTags.LEAVES, BlockTags.FENCES, BlockTags.FENCE_GATES)) {
            for (Holder<Block> block : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                for (BlockState s : block.value().getStateDefinition().getPossibleStates()) {
                    permeable(h, s, t.geometry(s));
                }
                blocks++;
            }
        }
        h.assertTrue(blocks > 3, "the tags hold " + blocks + " blocks");
        for (Block block : List.of(Blocks.SCAFFOLDING, Blocks.IRON_BARS)) {
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                permeable(h, s, t.geometry(s));
            }
        }
        BlockState honey = Blocks.HONEY_BLOCK.defaultBlockState();
        h.assertTrue(t.geometry(honey).hasFlag(Geometry.FLAG_OVERRIDE), "the honey block isn't overridden");
        sealed(h, honey, t.geometry(honey));
        h.succeed();
    }

    /** Leaves, fences, gates, bars and scaffolding in a wall join the rooms; a honey block seals (GEO-009). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_009_leaves_and_fences_pass_gas_and_honey_seals(GameTestHelper h) {
        List<BlockState> permeable = List.of(
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true),
                Blocks.IRON_BARS.defaultBlockState(),
                Blocks.SCAFFOLDING.defaultBlockState());
        int area = 650;
        for (BlockState state : permeable) {
            Rooms r = rooms(h, area++, state);
            h.assertTrue(r.zoned(EAST, 2, 2), state + " doesn't let the zone through");
            h.assertValueEqual(r.zone(EAST, 2, 2).id(), r.zone(WEST, 2, 2).id(), state + ": the rooms");
            h.assertValueEqual(r.zone(WALL, 2, 2).id(), r.zone(WEST, 2, 2).id(), state + ": its own cell");
            h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 55, state + ": the rooms and the block");
            r.close(r.zone(WEST, 2, 2).volume(), 55_000, state + ": the volume");
        }
        Rooms r = rooms(h, area, Blocks.HONEY_BLOCK.defaultBlockState());
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the west room behind a honey block");
        h.assertFalse(r.zoned(EAST, 2, 2), "gas passed a honey block");
        h.assertFalse(r.zoned(WALL, 2, 2), "a honey block holds gas");
        h.succeed();
    }

    /** Sources, flowing fluid and waterlogged blocks seal every face and hold no gas (GEO-006). */
    @GameTest(template = "empty")
    public static void geo_006_fluids_hold_no_gas(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        for (BlockState s : List.of(
                water,
                water.setValue(LiquidBlock.LEVEL, 3),
                lava,
                lava.setValue(LiquidBlock.LEVEL, 3),
                Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.KELP.defaultBlockState(),
                Blocks.SEAGRASS.defaultBlockState(),
                Blocks.BUBBLE_COLUMN.defaultBlockState())) {
            h.assertTrue(t.geometry(s).hasFlag(Geometry.FLAG_FLUID), s + " isn't a fluid");
            sealed(h, s, t.geometry(s));
        }
        Geometry drySlab = t.geometry(Blocks.OAK_SLAB.defaultBlockState());
        h.assertFalse(drySlab.hasFlag(Geometry.FLAG_FLUID), "a dry slab is a fluid");
        h.assertValueEqual(drySlab.volume(), 500.0, "a dry slab's volume");

        // Heat and fire data come on top, so lava is still what it was.
        h.assertValueEqual(t.geometry(lava).sourceTemperature(), 1450.0, "lava's temperature");
        h.assertTrue(t.geometry(lava).hasFlag(Geometry.FLAG_IGNITION_SOURCE), "lava doesn't ignite");

        // An override has the last word, for a waterlogged state as for any other.
        BlockState wetFence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        permeable(h, wetFence, t.geometry(wetFence));
        h.succeed();
    }

    /** With a slot in its wall a room is open; water standing in the slot seals it (GEO-006). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_006_water_column_seals_a_room(GameTestHelper h) {
        Rooms r = slotted(h, 657);
        MutationResult open = r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
        h.assertValueEqual(open.outcome(), MutationOutcome.AMBIENT, "the room with the slot open");

        setSlot(r, Blocks.WATER.defaultBlockState());
        r.tick();
        MutationResult sealed = r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
        h.assertValueEqual(sealed.outcome(), MutationOutcome.ZONE, "the room behind the water");
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the room's cells");
        h.assertFalse(r.zoned(WALL, 2, 2), "the water holds gas");
        h.succeed();
    }

    /** A bottom slab's top half is open, until it is waterlogged (GEO-006). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_006_waterlogged_block_seals_a_room(GameTestHelper h) {
        Rooms r = slotted(h, 658);
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        setSlot(r, slab);
        MutationResult dry = r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
        h.assertValueEqual(dry.outcome(), MutationOutcome.AMBIENT, "the room behind dry slabs");

        setSlot(r, slab.setValue(BlockStateProperties.WATERLOGGED, true));
        r.tick();
        MutationResult wet = r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
        h.assertValueEqual(wet.outcome(), MutationOutcome.ZONE, "the room behind waterlogged slabs");
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the room's cells");
        h.succeed();
    }

    /** A datapack override of a fluid has the last word: the water no longer seals (GEO-006). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = FLUID_OVERRIDE)
    public static void geo_006_overridden_fluid_does_not_seal(GameTestHelper h) {
        Rooms r = slotted(h, 659);
        setSlot(r, Blocks.WATER.defaultBlockState());
        Geometries.overrideForTest(List.of(new GeometryOverride(
                List.of(new GeometryOverride.Target(ResourceLocation.withDefaultNamespace("water"), false)),
                Set.of(),
                1000)));
        try {
            permeable(
                    h, Blocks.WATER.defaultBlockState(), Geometries.table().geometry(Blocks.WATER.defaultBlockState()));
            r.tick();
            MutationResult open =
                    r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
            h.assertValueEqual(open.outcome(), MutationOutcome.AMBIENT, "the room behind overridden water");
        } finally {
            Geometries.overrideForTest(List.of());
            r.tick(); // rebuilds the level with the normal palette before the next batch
        }
        h.succeed();
    }

    /** Lava touching a room is a heat source to it, with no wall between (GEO-006, HEAT-003). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_006_lava_beside_a_room_warms_it(GameTestHelper h) {
        Rooms r = new Rooms(h, 660);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.set(WALL + 1, 2, 2, STONE);
        r.set(WALL, 2, 2, Blocks.LAVA.defaultBlockState());
        r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(10), source("geo_006")));
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the lava seals the room");
        double start = r.zone(WEST, 2, 2).mixture().temperature();
        r.ticks(3);
        double now = r.zone(WEST, 2, 2).mixture().temperature();
        h.assertTrue(now > start, "the room went from " + start + " K to " + now + " K");
        h.succeed();
    }

    /** One room, x 11 to 13, with a slot through its east wall from y = 1 to 3 at z = 2, open to the air. */
    private static Rooms slotted(GameTestHelper h, int index) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        setSlot(r, AIR);
        return r;
    }

    private static void setSlot(Rooms r, BlockState state) {
        for (int y = 1; y <= 3; y++) {
            r.set(WALL, y, 2, state);
        }
    }
}
