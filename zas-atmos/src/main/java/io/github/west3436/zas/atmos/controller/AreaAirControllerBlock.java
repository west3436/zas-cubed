package io.github.west3436.zas.atmos.controller;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The area air controller (CONT-040, D-68): a panel on a wall, facing out of it into the room it
 * watches. It needs a sturdy wall behind it and drops when the wall goes. Its panel shows its
 * {@link ControllerStatus}, and it emits a redstone signal of 7 at danger 1 and 15 at danger 2
 * (CONT-048), strongly into the wall behind it as a lever does. The work is in {@link
 * AreaAirControllerBlockEntity}.
 */
public class AreaAirControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AreaAirControllerBlock> CODEC = simpleCodec(AreaAirControllerBlock::new);

    /** What the panel shows. */
    public static final EnumProperty<ControllerStatus> STATUS = EnumProperty.create("status", ControllerStatus.class);

    /** The panel's shape for each facing: on the wall behind it (models/block/area_air_controller.json). */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(2, 5, 14, 14, 14, 16),
            Direction.SOUTH, Block.box(2, 5, 0, 14, 14, 2),
            Direction.EAST, Block.box(0, 5, 2, 2, 14, 14),
            Direction.WEST, Block.box(14, 5, 2, 16, 14, 14));

    public AreaAirControllerBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, ControllerStatus.UNPOWERED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The wall behind a controller. */
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
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AreaAirControllerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        // The multitool selects the controller in its own useOn.
        if (stack.getItem() instanceof MultitoolItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AreaAirControllerBlockEntity be && player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> ControllerMenu.writeOpening(buf, pos, false));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof AreaAirControllerBlockEntity be) {
            be.removedFromWorld(server);
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) {
            level.updateNeighborsAt(wall(state, pos), this);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(STATUS).signal();
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) ? state.getValue(STATUS).signal() : 0;
    }
}
