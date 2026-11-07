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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An inline device with a third pipe port on one side: a filter or a mixer (architecture §11.5). Its
 * port 2 is to the right of the way its gas goes, or to the left while {@link #MIRRORED}, as its
 * {@code _mirrored} models show. Facing up or down, its models turn about the x axis, so the side
 * port stays east, or west while mirrored. Which side it's on comes from the item that places it, a
 * {@link MirroredDeviceItem} for the left, and nothing in the game changes it afterwards: a wrench
 * turns its facing alone, so its port stays on the same side of its gas (CONT-035, D-77).
 */
public abstract class SidePortBlock extends InlineDeviceBlock {
    /** Whether the side port is on the left of the way its gas goes. */
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

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

    protected SidePortBlock(Properties properties) {
        super(properties, NORTH);
        for (Direction d : Direction.values()) {
            mirroredShapes.put(d, turned(NORTH_MIRRORED, d));
        }
        registerDefaultState(defaultBlockState().setValue(MIRRORED, false));
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
        return sideFace(state.getValue(FACING), state.getValue(MIRRORED));
    }

    private static Direction sideFace(Direction front, boolean mirrored) {
        Direction right = front.getAxis().isVertical() ? Direction.EAST : front.getClockWise();
        return mirrored ? right.getOpposite() : right;
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
        builder.add(MIRRORED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MIRRORED)
                ? mirroredShapes.get(state.getValue(FACING))
                : super.getShape(state, level, pos, context);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return withSideOn(super.rotate(state, rotation), rotation.rotate(sideFace(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return withSideOn(super.mirror(state, mirror), mirror.mirror(sideFace(state)));
    }

    /** A state with its side port on a face, if its facing allows that; otherwise on the other side. */
    private static BlockState withSideOn(BlockState state, Direction face) {
        return state.setValue(MIRRORED, sideFace(state.getValue(FACING), false) != face);
    }
}
