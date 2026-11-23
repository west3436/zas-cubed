package io.github.west3436.zas.atmos.inline;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An inline device with a third pipe port on one side: a filter or a mixer (architecture §11.5). Its
 * port 2 is to the right of the way its gas goes, or to the left while {@link #MIRRORED}, as its
 * {@code _mirrored} models show. Facing up or down, its models turn about the x axis, so the side
 * port is east, or west while mirrored, turned about the vertical by its {@link #SPIN}: a structure
 * or a ship turned a quarter round takes an upright one's side port round with it (CONT-156). Which
 * side it's on comes from the item that places it, a {@link MirroredDeviceItem} for the left, and
 * nothing in the game changes it afterwards but a mirror: a wrench turns its facing alone, so its
 * port stays on the same side of its gas (CONT-035, D-77).
 */
public abstract class SidePortBlock extends InlineDeviceBlock {
    /** Whether the side port is on the left of the way its gas goes. */
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    /**
     * How far an upright one is turned about the vertical from where its models put its side port:
     * east, or west while mirrored. Always {@link Rotation#NONE} facing sideways, where its facing
     * alone says where the side port is.
     */
    public static final EnumProperty<Rotation> SPIN = EnumProperty.create("spin", Rotation.class);

    /** The box, the tube through it and its flanges at each end, facing north. */
    private static final VoxelShape THROUGH = Shapes.or(
            Block.box(2.75, 2.5, 2.5, 13.25, 13.5, 13.5),
            Block.box(6, 6, 0, 10, 10, 16),
            Block.box(5, 5, 0, 11, 11, 1),
            Block.box(5, 5, 15, 11, 11, 16));

    private static final VoxelShape NORTH =
            Shapes.or(THROUGH, Block.box(13, 6, 6, 16, 10, 10), Block.box(15, 5, 5, 16, 11, 11));
    private static final VoxelShape NORTH_MIRRORED =
            Shapes.or(THROUGH, Block.box(0, 6, 6, 3, 10, 10), Block.box(0, 5, 5, 1, 11, 11));

    private final Map<Direction, VoxelShape> mirroredShapes = new EnumMap<>(Direction.class);
    /** An upright one's shapes turned about the vertical: by facing, mirrored, then spin. */
    private final Map<Direction, VoxelShape[][]> spunShapes = new EnumMap<>(Direction.class);

    protected SidePortBlock(Properties properties) {
        super(properties, NORTH);
        for (Direction d : Direction.values()) {
            mirroredShapes.put(d, turned(NORTH_MIRRORED, d));
        }
        for (Direction d : new Direction[] {Direction.UP, Direction.DOWN}) {
            VoxelShape[][] spun = new VoxelShape[2][Rotation.values().length];
            for (Rotation r : Rotation.values()) {
                spun[0][r.ordinal()] = spun(turned(NORTH, d), r);
                spun[1][r.ordinal()] = spun(turned(NORTH_MIRRORED, d), r);
            }
            spunShapes.put(d, spun);
        }
        registerDefaultState(defaultBlockState().setValue(MIRRORED, false).setValue(SPIN, Rotation.NONE));
    }

    /** A shape turned about the vertical, as a blockstate's y rotation turns a model. */
    private static VoxelShape spun(VoxelShape shape, Rotation rotation) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = spun(x0, z0, rotation);
            double[] b = spun(x1, z1, rotation);
            out[0] = Shapes.or(
                    out[0],
                    Shapes.box(
                            Math.min(a[0], b[0]),
                            y0,
                            Math.min(a[1], b[1]),
                            Math.max(a[0], b[0]),
                            y1,
                            Math.max(a[1], b[1])));
        });
        return out[0];
    }

    private static double[] spun(double x, double z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new double[] {x, z};
            case CLOCKWISE_90 -> new double[] {1 - z, x};
            case CLOCKWISE_180 -> new double[] {1 - x, 1 - z};
            case COUNTERCLOCKWISE_90 -> new double[] {z, 1 - x};
        };
    }

    /** The item that places it with its side port on the left. */
    protected abstract ItemLike mirroredItem();

    /** A state for its mirrored item to place. */
    BlockState mirroredStateForPlacement(BlockPlaceContext context) {
        return facingFor(defaultBlockState().setValue(MIRRORED, true), context);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(MIRRORED) ? new ItemStack(mirroredItem()) : super.getCloneItemStack(level, pos, state);
    }

    /** The face its side port is on. */
    public static Direction sideFace(BlockState state) {
        return sideFace(state.getValue(FACING), state.getValue(MIRRORED), state.getValue(SPIN));
    }

    private static Direction sideFace(Direction front, boolean mirrored, Rotation spin) {
        if (front.getAxis().isVertical()) {
            return spin.rotate(mirrored ? Direction.WEST : Direction.EAST);
        }
        Direction right = front.getClockWise();
        return mirrored ? right.getOpposite() : right;
    }

    /**
     * A state whose facing was just set, as a wrench sets it, with its side port by its own rule: an
     * upright one keeps its spin, and one that faces sideways has none.
     *
     * @param state a state of a side port device
     * @return the state as it should be
     */
    public static BlockState settled(BlockState state) {
        return state.getValue(FACING).getAxis().isVertical() ? state : state.setValue(SPIN, Rotation.NONE);
    }

    /** Port 0 at the back, port 1 at the front and port 2 on its side. */
    @Override
    public List<Direction> portFaces(BlockState state) {
        Direction front = state.getValue(FACING);
        return List.of(front.getOpposite(), front, sideFace(state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MIRRORED, SPIN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis().isVertical()) {
            return spunShapes
                    .get(facing)[state.getValue(MIRRORED) ? 1 : 0][
                    state.getValue(SPIN).ordinal()];
        }
        return state.getValue(MIRRORED) ? mirroredShapes.get(facing) : super.getShape(state, level, pos, context);
    }

    /**
     * Turned, its side port goes round with it: facing sideways, its facing says where; upright, it
     * spins. Either way it keeps its hand.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = super.rotate(state, rotation);
        if (state.getValue(FACING).getAxis().isVertical()) {
            return turned.setValue(SPIN, state.getValue(SPIN).getRotated(rotation));
        }
        return withSideOn(turned, rotation.rotate(sideFace(state)));
    }

    /** Mirrored, it is the other hand's device, its side port mirrored with it. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState flipped = super.mirror(state, mirror);
        Direction face = mirror.mirror(sideFace(state));
        if (mirror != Mirror.NONE && state.getValue(FACING).getAxis().isVertical()) {
            boolean mirrored = !state.getValue(MIRRORED);
            for (Rotation spin : Rotation.values()) {
                if (sideFace(flipped.getValue(FACING), mirrored, spin) == face) {
                    return flipped.setValue(MIRRORED, mirrored).setValue(SPIN, spin);
                }
            }
        }
        return withSideOn(flipped, face);
    }

    /** A sideways state with its side port on a face, if its facing allows; otherwise on the other side. */
    private static BlockState withSideOn(BlockState state, Direction face) {
        if (state.getValue(FACING).getAxis().isVertical()) {
            return state;
        }
        return state.setValue(MIRRORED, sideFace(state.getValue(FACING), false, Rotation.NONE) != face)
                .setValue(SPIN, Rotation.NONE);
    }
}
