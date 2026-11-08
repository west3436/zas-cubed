package io.github.west3436.zas.atmos.airlock;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small panel on a wall, as the fire alarm is: {@code facing} points out of the wall into the
 * room, it needs a sturdy face behind it, and it drops when that goes. The airlock controller and
 * the air sensor are panels.
 */
public abstract class WallPanelBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /**
     * @param properties the block's properties
     * @param x0 the panel's left edge on a north-facing model, in pixels
     * @param y0 its bottom
     * @param x1 its right edge
     * @param y1 its top
     */
    protected WallPanelBlock(Properties properties, double x0, double y0, double x1, double y1) {
        super(properties.noOcclusion());
        shapes.put(Direction.NORTH, Block.box(x0, y0, 14, x1, y1, 16));
        shapes.put(Direction.SOUTH, Block.box(16 - x1, y0, 0, 16 - x0, y1, 2));
        shapes.put(Direction.EAST, Block.box(0, y0, x0, 2, y1, x1));
        shapes.put(Direction.WEST, Block.box(14, y0, 16 - x1, 16, y1, 16 - x0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** The wall behind a panel. */
    public static BlockPos wall(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos wall = wall(state, pos);
        return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction looking : context.getNearestLookingDirections()) {
            if (looking.getAxis().isHorizontal()) {
                BlockState placed = state.setValue(FACING, looking.getOpposite());
                if (placed.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return placed;
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
