package io.github.west3436.zas.atmos.portable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A portable (CONT-018, D-73): a floor machine that runs on the battery in its slot and works on its
 * own on the zone it stands in. Its front faces the player who places it, and its {@link #LIT}
 * property is true while it works. Used with an empty hand it opens its GUI, and broken it drops
 * what it holds ({@link PortableDevice#heldItems()}).
 *
 * <p>It isn't a full block: gas passes around it, so it stands in its room's cell.
 */
public abstract class PortableBlock extends BaseEntityBlock {
    /** The way the front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether it is working. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final VoxelShape northSouth;
    private final VoxelShape eastWest;

    /**
     * @param properties the block's properties
     * @param width its body's width across its front, in pixels, centred in the block
     * @param depth its body's depth, in pixels, centred in the block
     * @param height its height, in pixels
     */
    protected PortableBlock(Properties properties, int width, int depth, int height) {
        super(properties.noOcclusion());
        northSouth = centred(width, depth, height);
        eastWest = centred(depth, width, height);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    private static VoxelShape centred(int x, int z, int height) {
        return Block.box(8 - x / 2.0, 0, 8 - z / 2.0, 8 + x / 2.0, height, 8 + z / 2.0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? northSouth : eastWest;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Opens its GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PortableDevice device) {
            player.openMenu(device);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PortableDevice device) {
            device.heldItems()
                    .forEach(item -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), item));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
