package io.github.west3436.zas.gametest;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryDerivation;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** World-facing checks of per-state geometry (GEO-001 to GEO-005, GEO-040, GEO-041). */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeometryTests {
    private GeometryTests() {}

    private static final BlockState BOTTOM_SLAB = Blocks.STONE_SLAB.defaultBlockState();
    private static final BlockState TOP_SLAB = BOTTOM_SLAB.setValue(SlabBlock.TYPE, SlabType.TOP);
    private static final BlockState PANE_NS = Blocks.GLASS_PANE
            .defaultBlockState()
            .setValue(IronBarsBlock.NORTH, true)
            .setValue(IronBarsBlock.SOUTH, true);

    private static Geometry geometry(BlockState state) {
        return Geometries.table().geometry(state);
    }

    private static void check(GameTestHelper h, boolean condition, String message) {
        if (!condition) {
            h.fail(message);
        }
    }

    private static void checkVolume(GameTestHelper h, double expected, Geometry g, String what) {
        check(h, g.volume() == expected, what + ": expected " + expected + " L, got " + g.volume());
    }

    /** A hollow stone box with corners {@code min} and {@code max}, inclusive. */
    private static void buildShell(GameTestHelper h, BlockPos min, BlockPos max) {
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            boolean wall = p.getX() == min.getX()
                    || p.getX() == max.getX()
                    || p.getY() == min.getY()
                    || p.getY() == max.getY()
                    || p.getZ() == min.getZ()
                    || p.getZ() == max.getZ();
            h.setBlock(p, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    // ---- GEO-001 ----

    /** Air, a slab, a pane and a block with a sealed void have 1, 1, 2 and 0 cells in-world. */
    @GameTest(template = "geo_001_cells")
    public static void geo_001_cell_counts_in_world(GameTestHelper h) {
        BlockPos air = new BlockPos(0, 1, 1);
        BlockPos slab = new BlockPos(2, 1, 1);
        BlockPos pane = new BlockPos(4, 1, 1);
        h.setBlock(air, Blocks.AIR);
        h.setBlock(slab, BOTTOM_SLAB);
        h.setBlock(pane, PANE_NS);
        GeometryTable t = Geometries.table();
        check(h, t.geometry(h.getBlockState(air)).cellCount() == 1, "air should be one cell");
        check(h, t.geometry(h.getBlockState(slab)).cellCount() == 1, "a slab should be one cell");
        check(h, t.geometry(h.getBlockState(pane)).cellCount() == 2, "a straight pane should be two cells");
        // No vanilla block has a sealed internal void, so derive one from a shape.
        Geometry hollow = GeometryDerivation.derive(
                Shapes.join(Shapes.block(), Block.box(4, 4, 4, 12, 12, 12), BooleanOp.ONLY_FIRST));
        check(h, hollow.cellCount() == 0 && hollow.volume() == 0, "a sealed void should be ignored");
        h.succeed();
    }

    // ---- GEO-002 ----

    @GameTest(template = "empty")
    public static void geo_002_volumes(GameTestHelper h) {
        checkVolume(h, 1000, geometry(Blocks.AIR.defaultBlockState()), "air");
        checkVolume(h, 500, geometry(BOTTOM_SLAB), "bottom slab");
        checkVolume(h, 0, geometry(BOTTOM_SLAB.setValue(SlabBlock.TYPE, SlabType.DOUBLE)), "double slab");
        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState();
        checkVolume(h, 250, geometry(stairs.setValue(StairBlock.SHAPE, StairsShape.STRAIGHT)), "straight stairs");
        checkVolume(h, 125, geometry(stairs.setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT)), "inner stairs");
        checkVolume(h, 375, geometry(stairs.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT)), "outer stairs");
        checkVolume(h, 875, geometry(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2)), "snow");
        checkVolume(h, 0, geometry(Blocks.STONE.defaultBlockState()), "stone");
        h.succeed();
    }

    // ---- GEO-003 ----

    private static final BlockPos ROOM_MIN = new BlockPos(1, 1, 1);
    private static final BlockPos ROOM_MAX = new BlockPos(5, 5, 5);
    private static final BlockPos ROOM_INSIDE = new BlockPos(3, 3, 3);
    private static final BlockPos ROOM_WALL = new BlockPos(5, 3, 3);
    private static final BlockPos ROOM_OUTSIDE = new BlockPos(6, 3, 3);

    private static CellFlood.Result floodRoom(GameTestHelper h) {
        return CellFlood.flood(h, Geometries.table(), ROOM_INSIDE, 0, BoundingBox.fromCorners(ROOM_MIN, ROOM_MAX));
    }

    /** A bottom slab in the wall with a top slab outside it: together they seal the face. */
    @GameTest(template = "geo_003_room")
    public static void geo_003_complementary_slabs_seal_a_room(GameTestHelper h) {
        buildShell(h, ROOM_MIN, ROOM_MAX);
        h.setBlock(ROOM_WALL, BOTTOM_SLAB);
        h.setBlock(ROOM_OUTSIDE, TOP_SLAB);
        CellFlood.Result sealed = floodRoom(h);
        check(h, !sealed.escaped(), "complementary slabs should seal the room");
        check(h, sealed.reachedPos(ROOM_WALL), "the room's gas should reach the slab's free half");

        // Without the top slab the slab's open upper half leaks.
        h.setBlock(ROOM_OUTSIDE, Blocks.AIR);
        check(h, floodRoom(h).escaped(), "a lone slab in the wall should leak");
        h.succeed();
    }

    /** A chest's collision is inset from its faces, so it leaks around the edges (D-44). */
    @GameTest(template = "geo_003_room")
    public static void geo_003_chest_leaks(GameTestHelper h) {
        buildShell(h, ROOM_MIN, ROOM_MAX);
        h.setBlock(ROOM_WALL, Blocks.CHEST);
        h.setBlock(ROOM_OUTSIDE, Blocks.AIR);
        check(h, floodRoom(h).escaped(), "a chest in the wall should leak");

        h.setBlock(ROOM_WALL, Blocks.STONE);
        check(h, !floodRoom(h).escaped(), "the closed room should be sealed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void geo_003_slab_face_masks(GameTestHelper h) {
        Geometry slab = geometry(BOTTOM_SLAB);
        Geometry top = geometry(TOP_SLAB);
        check(h, slab.isFaceFull(Face.DOWN), "a bottom slab's bottom face should be sealed");
        check(h, slab.coverage(Face.UP).isEmpty(), "a bottom slab's top face should be open");
        check(h, slab.coverage(Face.EAST).cardinality() == 128, "a bottom slab's side should be half covered");
        check(h, GeometryPalette.sealed(slab, Face.EAST, top), "complementary slabs should seal");
        check(h, !GeometryPalette.sealed(slab, Face.EAST, slab), "two bottom slabs should not seal");
        check(h, !GeometryPalette.sealed(slab, Face.EAST, Geometry.AIR), "a slab against air should not seal");
        h.succeed();
    }

    /** Doors and trapdoors seal the face they close against (S2: 812.5 L). */
    @GameTest(template = "empty")
    public static void geo_003_door_and_trapdoor_masks(GameTestHelper h) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        checkClosesOneSide(h, geometry(door), "closed door");
        checkClosesOneSide(h, geometry(door.setValue(DoorBlock.OPEN, true)), "open door");

        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        Geometry closed = geometry(trapdoor);
        check(h, closed.isFaceFull(Face.DOWN), "a closed bottom trapdoor should seal its bottom face");
        checkVolume(h, 812.5, closed, "closed trapdoor");
        checkClosesOneSide(h, geometry(trapdoor.setValue(TrapDoorBlock.OPEN, true)), "open trapdoor");
        h.succeed();
    }

    private static void checkClosesOneSide(GameTestHelper h, Geometry g, String what) {
        int full = 0;
        for (Face f : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            full += g.isFaceFull(f) ? 1 : 0;
        }
        check(h, full == 1, what + " should seal exactly one side, sealed " + full);
        check(h, g.cellCount() == 1, what + " should be one cell");
        checkVolume(h, 812.5, g, what);
    }

    // ---- GEO-004 ----

    @GameTest(template = "empty")
    public static void geo_004_pane_and_wall_sub_cells(GameTestHelper h) {
        Geometry pane = geometry(PANE_NS);
        check(h, pane.cellCount() == 2, "a straight pane should have two cells, got " + pane.cellCount());
        check(h, pane.cell(0).volume() == 437.5 && pane.cell(1).volume() == 437.5, "pane halves should be 437.5 L");
        BlockState cross = PANE_NS.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        check(h, geometry(cross).cellCount() == 4, "a cross pane should have four cells");

        BlockState wall = Blocks.COBBLESTONE_WALL
                .defaultBlockState()
                .setValue(WallBlock.UP, false)
                .setValue(WallBlock.NORTH_WALL, WallSide.LOW)
                .setValue(WallBlock.SOUTH_WALL, WallSide.LOW);
        check(h, geometry(wall).cellCount() == 2, "a straight wall should have two cells");
        h.succeed();
    }

    /**
     * No vanilla state has more cells than a cell id can name ({@link CellIds#MAX_SUB_CELLS}, #105):
     * crossed panes, fences and walls have four, the most of any.
     */
    @GameTest(template = "empty")
    public static void geo_004_vanilla_cells_fit_cell_ids(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        int most = 0;
        BlockState mostState = null;
        int states = 0;
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            if (!BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("minecraft")) {
                continue;
            }
            states++;
            int cells = t.geometry(s).cellCount();
            if (cells > most) {
                most = cells;
                mostState = s;
            }
        }
        LogUtils.getLogger()
                .info(
                        "geo_004: at most {} cells per block over {} vanilla states, first at {}",
                        most,
                        states,
                        mostState);
        check(h, most <= CellIds.MAX_SUB_CELLS, mostState + " has " + most + " cells, more than a cell id can name");
        check(h, most == 4, "crossed panes have four cells, the most of any vanilla state; got " + most);
        h.succeed();
    }

    private static final BlockPos PANES_MIN = new BlockPos(1, 1, 1);
    private static final BlockPos PANES_MAX = new BlockPos(7, 5, 5);

    /** A line of panes across a room keeps its two halves apart. */
    @GameTest(template = "geo_004_panes")
    public static void geo_004_line_of_panes_keeps_sides_apart(GameTestHelper h) {
        buildShell(h, PANES_MIN, PANES_MAX);
        for (int y = 2; y <= 4; y++) {
            for (int z = 2; z <= 4; z++) {
                h.setBlock(new BlockPos(4, y, z), PANE_NS);
            }
        }
        BoundingBox bounds = BoundingBox.fromCorners(PANES_MIN, PANES_MAX);
        BlockPos west = new BlockPos(2, 3, 3);
        BlockPos east = new BlockPos(6, 3, 3);
        CellFlood.Result r = CellFlood.flood(h, Geometries.table(), west, 0, bounds);
        check(h, !r.escaped(), "the room should be sealed");
        check(h, r.reachedPos(new BlockPos(4, 3, 3)), "the west side should reach the panes' west cells");
        check(h, !r.reachedPos(east), "a line of panes should keep the two sides apart");
        check(
                h,
                r.reached().stream().noneMatch(n -> n.pos().getX() == 4 && n.cell() == 1),
                "the west side should never reach a pane's east cell");

        h.setBlock(new BlockPos(4, 3, 3), Blocks.AIR);
        check(h, CellFlood.flood(h, Geometries.table(), west, 0, bounds).reachedPos(east), "a gap should join them");
        h.succeed();
    }

    // ---- GEO-005 ----

    /**
     * The test datapack overrides the lectern by block and the lectern and end rod by tag; the
     * block override wins for the lectern.
     */
    @GameTest(template = "empty")
    public static void geo_005_override_by_block_and_tag(GameTestHelper h) {
        BlockPos pos = BlockPos.ZERO;
        h.setBlock(pos, Blocks.LECTERN);
        Geometry lectern = geometry(h.getBlockState(pos));
        check(h, lectern.hasFlag(Geometry.FLAG_OVERRIDE), "the lectern should be overridden");
        check(h, lectern.cellCount() == 1, "an overridden block should be one cell");
        checkVolume(h, 250, lectern, "lectern");
        check(h, lectern.isFaceFull(Face.NORTH) && lectern.isFaceFull(Face.SOUTH), "lectern N/S should be sealed");
        check(h, lectern.coverage(Face.EAST).isEmpty(), "lectern east should be open");
        check(h, lectern.coverage(Face.DOWN).isEmpty(), "the block override should beat the tag override");

        for (Direction d : Direction.values()) {
            Geometry rod = geometry(Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, d));
            check(h, rod.hasFlag(Geometry.FLAG_OVERRIDE), "every end rod state should be overridden");
            checkVolume(h, 600, rod, "end rod");
            check(h, rod.isFaceFull(Face.DOWN) && rod.coverage(Face.UP).isEmpty(), "end rod should follow the tag");
        }
        check(h, Geometries.table().overriddenStates() > 0, "the table should count overridden states");
        h.succeed();
    }

    // ---- GEO-040 ----

    @GameTest(template = "empty")
    public static void geo_040_cached_per_block_state(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        check(h, t == Geometries.table(), "repeated queries should return the cached table");
        check(h, GeometryDerivation.derived() == GeometryDerivation.derived(), "derivation should run once");
        check(h, t.geometryId(BOTTOM_SLAB) == t.geometryId(BOTTOM_SLAB), "a state's id should be stable");
        check(h, t.geometry(BOTTOM_SLAB) == t.geometry(BOTTOM_SLAB), "a state's geometry should be shared");
        BlockState oakSlab = Blocks.OAK_SLAB.defaultBlockState();
        check(h, t.geometryId(oakSlab) == t.geometryId(BOTTOM_SLAB), "equal shapes should share one geometry");
        check(h, t.distinctShapes() < t.stateCount(), "geometry should be derived per shape, not per state");
        check(h, t.palette().size() < t.distinctShapes(), "geometries should be deduplicated");
        h.succeed();
    }

    /** A rough bench of the lookup: every state id, many times over. */
    @GameTest(template = "empty")
    public static void geo_040_lookup_cost(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        BlockState[] states = StreamSupport.stream(Block.BLOCK_STATE_REGISTRY.spliterator(), false)
                .toArray(BlockState[]::new);
        int rounds = 50;
        long sum = 0;
        for (BlockState s : states) {
            sum += t.geometryId(s); // warm up
        }
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            for (BlockState s : states) {
                sum += t.geometryId(s);
            }
        }
        double ns = (System.nanoTime() - start) / (double) (rounds * states.length);
        LogUtils.getLogger()
                .info("geo_040: {} ns per BlockState lookup over {} states (checksum {})", ns, states.length, sum);
        check(h, ns < 200, "a geometry lookup should be a cheap array read, took " + ns + " ns");
        h.succeed();
    }

    // ---- GEO-041 ----

    @GameTest(template = "empty")
    public static void geo_041_collision_shapes_only(GameTestHelper h) {
        BlockState glassState = Blocks.GLASS.defaultBlockState();
        Geometry glass = geometry(glassState);
        check(h, !glassState.canOcclude(), "glass doesn't occlude, so render data would call it open");
        for (Face f : Face.values()) {
            check(h, glass.isFaceFull(f), "glass should be gas-tight on " + f);
        }
        check(h, glass.cellCount() == 0, "glass should hold no gas");

        BlockState scaffolding = Blocks.SCAFFOLDING.defaultBlockState();
        Geometry expected = GeometryDerivation.derive(GeometryDerivation.collisionShape(scaffolding));
        check(h, geometry(scaffolding).equals(expected), "scaffolding should use its empty-context shape");
        check(h, !expected.equals(Geometry.SOLID), "scaffolding's empty-context shape isn't a full block");

        Geometry powderSnow = geometry(Blocks.POWDER_SNOW.defaultBlockState());
        check(h, powderSnow.equals(Geometry.AIR), "powder snow has an empty collision shape in an empty context");
        h.succeed();
    }
}
