package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.BlockGeometry;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.scheduler.GeometryAudit;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryDerivation;
import io.github.west3436.zas.geometry.GeometryOverride;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.geometry.PositionGeometries;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Blocks whose geometry depends on the position, in a real level: shapes read per position (GEO-043),
 * shapes changed through markDirty (GEO-014), points in their cells (API-015) and blocks in motion
 * (GEO-042). Areas 662 to 669.
 *
 * <p>Two rooms 3 × 3 × 3 inside, in a row along x, share a wall at x = 14, with a {@link
 * TestShapeBlock} at (14, 2, 2) in it.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PositionShapeTests {
    private static final int WEST = 12;
    private static final int EAST = 16;
    private static final int WALL = 14;

    /** A wall across x through the block's middle, leaving a west cell and an east cell. */
    private static final VoxelShape ACROSS_X = Block.box(7, 0, 0, 9, 16, 16);

    /** A small bar in the block's middle, leaving one cell reaching every face. */
    private static final VoxelShape BAR = Block.box(7, 7, 7, 9, 9, 9);

    private PositionShapeTests() {}

    /** The two rooms, with the test block in their wall having {@code shape} at its position. */
    private static Rooms rooms(GameTestHelper h, int index, VoxelShape shape) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.box(14, 0, 18, 4);
        TestShapeBlock.shape(r.abs(WALL, 2, 2), shape);
        r.set(WALL, 2, 2, TestShapeBlock.state());
        return r;
    }

    private static void addWest(Rooms r, double moles) {
        r.settle(GasMutations.add(r.level, r.point(WEST, 2, 2), oxygen(moles), source("geo_043")));
    }

    private static void addEast(Rooms r, double moles) {
        r.settle(GasMutations.add(r.level, r.point(EAST, 2, 2), oxygen(moles), source("geo_043")));
    }

    private static boolean oneZone(Rooms r) {
        return r.zone(WEST, 2, 2).id().equals(r.zone(EAST, 2, 2).id());
    }

    private static Geometry here(Rooms r, BlockPos pos) {
        return PositionGeometries.geometry(r.level, pos, r.level.getBlockState(pos), Geometries.table());
    }

    /** What the block in the level has as a shape at {@code pos}, as a geometry. */
    private static Geometry shapeAt(Rooms r, BlockPos pos, BlockState state) {
        return GeometryDerivation.derive(state.getCollisionShape(r.level, pos, CollisionContext.empty()));
    }

    /** Checks the audit finds the zones and connections right, against a fresh copy of the blocks. */
    private static void clean(Rooms r, int x0, int x1, String when) {
        BlockPos a = r.abs(x0, 0, 0);
        BlockPos b = r.abs(x1, 4, 4);
        DimensionAtmos.AuditResult result = r.settle(r.dimension()
                .audit(GeometryAudit.Region.spanning(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()), 8));
        r.h.assertTrue(result.report().clean(), "the audit " + when + ": " + result.report());
    }

    /**
     * Bamboo's offset gives each position its own cell, which the zones have, and writing the block
     * reads it again (GEO-043).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_043_bamboo_cells_differ_by_position(GameTestHelper h) {
        Rooms r = new Rooms(h, 662);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        addWest(r, 100);
        BlockState bamboo = Blocks.BAMBOO.defaultBlockState();
        BlockPos a = r.abs(11, 1, 1);
        BlockPos b = null;
        for (int x = 11; x <= 13 && b == null; x++) {
            for (int z = 1; z <= 3 && b == null; z++) {
                BlockPos p = r.abs(x, 1, z);
                if (!shapeAt(r, p, bamboo).equals(shapeAt(r, a, bamboo))) {
                    b = p;
                }
            }
        }
        h.assertTrue(b != null, "bamboo has one shape all over the room");
        r.level.setBlockAndUpdate(a, bamboo);
        r.level.setBlockAndUpdate(b, bamboo);
        r.tick();

        Geometry atA = here(r, a);
        Geometry atB = here(r, b);
        h.assertFalse(atA.cell(0).equals(atB.cell(0)), "the two positions' cells are the same");
        h.assertValueEqual(atA.cell(0), shapeAt(r, a, bamboo).cell(0), "the cell at the first position");
        h.assertValueEqual(atB.cell(0), shapeAt(r, b, bamboo).cell(0), "the cell at the second position");
        h.assertTrue(atA.isFlammable(), "the position lost the block's fire data");
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the room's cells");
        r.close(r.zone(WEST, 2, 2).volume(), 27_000 - 2 * 144 * Geometry.VOXEL_VOLUME, "the room less two stalks");
        clean(r, 10, 14, "with bamboo");

        // Another block at the position: it is read again, and the stalk comes back as it was.
        BlockState dripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        r.level.setBlockAndUpdate(a, dripstone);
        r.tick();
        h.assertValueEqual(here(r, a).cell(0), shapeAt(r, a, dripstone).cell(0), "the cell after the change");
        h.assertFalse(here(r, a).cell(0).equals(atA.cell(0)), "the cell didn't change");
        clean(r, 10, 14, "after the change");
        r.level.setBlockAndUpdate(a, bamboo);
        r.tick();
        h.assertValueEqual(here(r, a).cell(0), atA.cell(0), "the cell with the stalk back");
        h.succeed();
    }

    /**
     * Each cell of a shape dividing the block holds its own point, though the block state's own cells
     * divide it the other way (API-015, GEO-043).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_043_points_in_each_cell_resolve_to_that_cell(GameTestHelper h) {
        Rooms r = rooms(h, 663, ACROSS_X);
        addWest(r, 100);
        BlockPos pos = r.abs(WALL, 2, 2);
        GeometryTable table = Geometries.table();
        h.assertValueEqual(table.geometry(TestShapeBlock.state()).cellCount(), 2, "the block state's cells");
        h.assertValueEqual(here(r, pos).cellCount(), 2, "the position's cells");
        ZoneInfo west = r.zone(WEST, 2, 2);
        h.assertValueEqual(west.cellCount(), 28, "the west room and the block's west cell");
        r.close(west.volume(), 27_000 + 7.0 / 16 * 1000, "the west room and the block's west cell");

        // In the block, near a corner and away from the state's own wall, on either side of this one.
        Vec3 inWest = Vec3.atLowerCornerOf(pos).add(0.2, 0.5, 0.2);
        Vec3 inEast = Vec3.atLowerCornerOf(pos).add(0.8, 0.5, 0.2);
        PointResolver.Cell westCell = PointResolver.resolve(r.level, inWest);
        PointResolver.Cell eastCell = PointResolver.resolve(r.level, inEast);
        h.assertValueEqual(westCell.pos(), pos, "the west point's block");
        h.assertValueEqual(eastCell.pos(), pos, "the east point's block");
        h.assertValueEqual(westCell.cells(), 2, "the west point's block's cells");
        h.assertTrue(westCell.sub() >= 0 && eastCell.sub() >= 0, "a point fell back to the largest cell");
        h.assertFalse(westCell.sub() == eastCell.sub(), "both points resolve to cell " + westCell.sub());
        h.assertValueEqual(
                PointResolver.resolve(r.level, Vec3.atLowerCornerOf(pos).add(0.2, 0.5, 0.8))
                        .sub(),
                westCell.sub(),
                "another point on the west side, across the state's wall");

        h.assertValueEqual(Atmospheres.at(r.level, inWest).zone().orElseThrow(), west.id(), "a query in the west cell");
        h.assertTrue(Atmospheres.at(r.level, inEast).zone().isEmpty(), "a query in the east cell has a zone");

        // Gas at the east point goes to the east room, through the block's east cell.
        double moles = west.mixture().moles(O2);
        r.settle(GasMutations.add(r.level, inEast, oxygen(50), source("geo_043")));
        ZoneInfo east = r.zone(EAST, 2, 2);
        h.assertFalse(east.id().equals(west.id()), "gas at the east point reached the west zone");
        h.assertValueEqual(east.cellCount(), 28, "the east room and the block's east cell");
        h.assertValueEqual(Atmospheres.at(r.level, inEast).zone().orElseThrow(), east.id(), "the east cell's zone");
        r.close(r.zone(WEST, 2, 2).mixture().moles(O2), moles, "the west room's gas");

        // A point for the block's largest cell resolves back to it; here that's the first of two equals.
        PointResolver.Cell back = PointResolver.resolve(r.level, PointResolver.pointIn(r.level, pos));
        h.assertValueEqual(back.sub(), PointResolver.resolve(r.level, pos).sub(), "the point for the largest cell");
        h.succeed();
    }

    /**
     * A shape driven by data changes nothing until markDirty, and then the zones follow it, both ways
     * (GEO-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_014_mark_dirty_reevaluates_a_data_driven_shape(GameTestHelper h) {
        Rooms r = rooms(h, 664, Shapes.block());
        addWest(r, 200);
        addEast(r, 200);
        BlockPos pos = r.abs(WALL, 2, 2);
        h.assertFalse(oneZone(r), "a full block lets the zones join");
        h.assertValueEqual(here(r, pos).cellCount(), 0, "a full block's cells");

        TestShapeBlock.shape(pos, BAR);
        r.ticks(2);
        h.assertFalse(oneZone(r), "the zones joined before markDirty");
        h.assertValueEqual(here(r, pos).cellCount(), 0, "the cells before markDirty");

        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        h.assertValueEqual(here(r, pos).cellCount(), 1, "the cells after markDirty");
        for (int i = 0; i < 40 && !oneZone(r); i++) {
            r.tick();
        }
        h.assertTrue(oneZone(r), "the rooms never became one zone");
        ZoneInfo zone = r.zone(WEST, 2, 2);
        h.assertValueEqual(zone.cellCount(), 55, "the rooms and the block");
        r.close(zone.volume(), 55_000 - 8 * Geometry.VOXEL_VOLUME, "the rooms and the block less its bar");

        TestShapeBlock.shape(pos, Shapes.block());
        BlockGeometry.markDirty(r.level, pos);
        r.tick();
        h.assertFalse(oneZone(r), "the zones are still one with the block full again");
        h.assertValueEqual(r.zone(WEST, 2, 2).cellCount(), 27, "the west room with the block full again");
        h.succeed();
    }

    /** A shape that throws leaves the block state's geometry, and the tick goes on (GEO-043). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_043_a_broken_shape_keeps_the_block_state_geometry(GameTestHelper h) {
        Rooms r = rooms(h, 665, Shapes.block());
        BlockPos pos = r.abs(WALL, 2, 2);
        TestShapeBlock.broken(pos, true);
        try {
            addWest(r, 100);
            GeometryTable table = Geometries.table();
            h.assertValueEqual(here(r, pos), table.geometry(TestShapeBlock.state()), "the position's geometry");
            h.assertTrue(r.zoned(WEST, 2, 2), "the room has no zone");
        } finally {
            TestShapeBlock.broken(pos, false);
        }
        h.succeed();
    }

    /** Which states are read per position; an override, a fluid or settling closed means no (GEO-042, GEO-006). */
    @GameTest(template = "empty")
    public static void geo_043_which_states_are_read_per_position(GameTestHelper h) {
        GeometryTable t = Geometries.table();
        for (BlockState s : Blocks.BAMBOO.getStateDefinition().getPossibleStates()) {
            h.assertTrue(t.perPosition(s), s + " isn't read per position");
        }
        for (BlockState s : Blocks.MOVING_PISTON.getStateDefinition().getPossibleStates()) {
            h.assertTrue(t.perPosition(s), s + " isn't read per position");
        }
        for (BlockState s : Blocks.POINTED_DRIPSTONE.getStateDefinition().getPossibleStates()) {
            h.assertValueEqual(
                    t.perPosition(s),
                    !s.getValue(BlockStateProperties.WATERLOGGED),
                    s + " read per position, when only a dry one is");
        }
        h.assertTrue(t.perPosition(TestShapeBlock.state()), "the test block isn't read per position");
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.SHULKER_BOXES)) {
            for (BlockState s : holder.value().getStateDefinition().getPossibleStates()) {
                h.assertFalse(t.perPosition(s), s + " is read per position");
            }
        }
        for (BlockState s : List.of(
                Blocks.SCAFFOLDING.defaultBlockState(),
                Blocks.POWDER_SNOW.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                Blocks.WATER.defaultBlockState())) {
            h.assertFalse(t.perPosition(s), s + " is read per position");
        }
        h.succeed();
    }

    /** A datapack override has the last word over a shape read per position (GEO-005, GEO-043). */
    @GameTest(template = "empty")
    public static void geo_043_datapack_override_beats_a_position_shape(GameTestHelper h) {
        BlockState bamboo = Blocks.BAMBOO.defaultBlockState();
        Geometries.overrideForTest(List.of(new GeometryOverride(
                List.of(new GeometryOverride.Target(ResourceLocation.withDefaultNamespace("bamboo"), false)),
                EnumSet.of(Face.DOWN),
                500)));
        try {
            GeometryTable table = Geometries.table();
            h.assertFalse(table.perPosition(bamboo), "the override left it per position");
            Geometry g = PositionGeometries.geometry(h.getLevel(), BlockPos.ZERO, bamboo, table);
            h.assertValueEqual(g.volume(), 500.0, "the override's volume");
        } finally {
            Geometries.overrideForTest(List.of());
        }
        h.succeed();
    }

    /** Moves a piston's motion on one step, ticking each of the blocks in motion, as the game would. */
    private static void stepMotion(Rooms r, BlockPos... moving) {
        for (BlockPos p : moving) {
            BlockState state = r.level.getBlockState(p);
            if (r.level.getBlockEntity(p) instanceof PistonMovingBlockEntity be) {
                PistonMovingBlockEntity.tick(r.level, p, state, be);
            }
        }
    }

    /**
     * A sticky piston pushes a block into a gap in a room's wall, and pulls it out again. The room's
     * cells change as the motion starts, to what they'll be when it has ended, and then don't change
     * (GEO-042). The motion is driven directly, within this game tick.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_042_cells_do_not_change_while_a_piston_moves(GameTestHelper h) {
        Rooms r = new Rooms(h, 666);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        r.set(WALL, 2, 2, Rooms.AIR);
        r.set(WALL + 1, 2, 2, STONE);
        BlockPos gap = r.abs(WALL, 2, 2);
        BlockPos middle = r.abs(WALL + 1, 2, 2);
        BlockPos base = r.abs(WALL + 2, 2, 2);
        BlockPos power = r.abs(WALL + 3, 2, 2);
        r.level.setBlock(
                base,
                Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST),
                Block.UPDATE_CLIENTS);
        addWest(r, 100);
        ZoneInfo start = r.zone(WEST, 2, 2);
        h.assertValueEqual(start.cellCount(), 28, "the room and the gap");

        // Extend. No neighbour update, so the piston moves only because this says so.
        r.level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockState piston = r.level.getBlockState(base);
        h.assertTrue(
                piston.triggerEvent(r.level, base, 0, Direction.WEST.get3DDataValue()), "the piston didn't extend");
        h.assertTrue(r.level.getBlockState(gap).is(Blocks.MOVING_PISTON), "the gap holds no block in motion");
        for (int step = 0; step < 4; step++) {
            r.tick();
            ZoneInfo zone = r.zone(WEST, 2, 2);
            h.assertValueEqual(zone.id(), start.id(), "the zone at step " + step);
            h.assertValueEqual(zone.cellCount(), 27, "the room's cells at step " + step);
            r.close(zone.volume(), 27_000, "the room's volume at step " + step);
            h.assertFalse(r.zoned(WALL, 2, 2), "the gap has a zone at step " + step);
            stepMotion(r, gap, middle);
        }
        h.assertValueEqual(r.level.getBlockState(gap), STONE, "the block when it has settled");
        h.assertTrue(r.level.getBlockState(middle).is(Blocks.PISTON_HEAD), "the head when it has settled");
        clean(r, 10, 14, "after extending");

        // Retract. The block leaves the gap as the motion starts.
        r.level.setBlock(power, Rooms.AIR, Block.UPDATE_CLIENTS);
        piston = r.level.getBlockState(base);
        h.assertTrue(
                piston.triggerEvent(r.level, base, 1, Direction.WEST.get3DDataValue()), "the piston didn't retract");
        h.assertTrue(r.level.getBlockState(middle).is(Blocks.MOVING_PISTON), "the middle holds no block in motion");
        for (int step = 0; step < 4; step++) {
            r.tick();
            ZoneInfo zone = r.zone(WEST, 2, 2);
            h.assertValueEqual(zone.id(), start.id(), "the zone at retracting step " + step);
            h.assertValueEqual(zone.cellCount(), 28, "the room's cells at retracting step " + step);
            h.assertValueEqual(r.zone(WALL, 2, 2).id(), start.id(), "the gap's zone at retracting step " + step);
            stepMotion(r, middle, base);
        }
        h.assertValueEqual(r.level.getBlockState(middle), STONE, "the block back when it has settled");
        h.assertTrue(r.level.getBlockState(base).is(Blocks.STICKY_PISTON), "the piston when it has settled");
        clean(r, 10, 14, "after retracting");
        h.succeed();
    }

    /**
     * A shulker box in a room's wall, with open air behind it, seals the room however far open it is
     * (GEO-042). The animation is driven directly, within this game tick.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_042_an_open_shulker_box_counts_as_closed(GameTestHelper h) {
        Rooms r = new Rooms(h, 667);
        r.clear(8, 0, 20, 6);
        r.box(10, 0, 14, 4);
        BlockPos pos = r.abs(WALL, 2, 2);
        BlockState box = Blocks.SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.EAST);
        r.level.setBlockAndUpdate(pos, box);
        addWest(r, 100);
        ZoneInfo start = r.zone(WEST, 2, 2);
        h.assertValueEqual(start.cellCount(), 27, "the room with the box shut");
        h.assertFalse(Geometries.table().perPosition(box), "the box is read per position");
        h.assertValueEqual(here(r, pos).cellCount(), 0, "the box's cells");

        ShulkerBoxBlockEntity be = (ShulkerBoxBlockEntity) r.level.getBlockEntity(pos);
        be.triggerEvent(1, 1);
        for (int step = 0; step < 12; step++) {
            ShulkerBoxBlockEntity.tick(r.level, pos, box, be);
            BlockGeometry.markDirty(r.level, pos);
            r.tick();
            ZoneInfo zone = r.zone(WEST, 2, 2);
            h.assertValueEqual(zone.id(), start.id(), "the zone at step " + step);
            h.assertValueEqual(zone.cellCount(), 27, "the room at step " + step);
            h.assertValueEqual(here(r, pos).cellCount(), 0, "the box's cells at step " + step);
        }
        h.assertValueEqual(be.getAnimationStatus(), ShulkerBoxBlockEntity.AnimationStatus.OPENED, "the box's state");
        h.assertTrue(
                box.getCollisionShape(r.level, pos, CollisionContext.empty()).max(Direction.Axis.X) > 1,
                "the box's shape doesn't reach out of its block");
        h.succeed();
    }
}
