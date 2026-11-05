package io.github.west3436.zas.atmos.igniter;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The igniter (CONT-105): a floor plate that, while it gets a redstone signal and is powered, is
 * an ignition source in its cell (FIRE-014), so a flammable mixture in its zone ignites.
 *
 * <p>{@link #POWERED} is the redstone signal, kept up to date as its neighbours change. {@link
 * #LIT} is whether it is sparking: its block entity sets it at each step when the signal and paid-for
 * power are both there, and it goes off as soon as the signal does. The block is in {@code
 * #zas:ignition_sources} ({@code data/zas/tags/block/ignition_sources.json}), so the framework
 * treats it as a standing ignition source while {@code lit} is true (API-053), with no API call.
 *
 * <p>It isn't a full block: it lies in its room's cell.
 */
public class IgniterBlock extends BaseEntityBlock {
    public static final MapCodec<IgniterBlock> CODEC = simpleCodec(IgniterBlock::new);

    /** Whether it is sparking: signal and power. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Whether it gets a redstone signal. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** The plate (models/block/igniter.json). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);

    public IgniterBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Follows the redstone signal; losing it puts the spark out at once. */
    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level instanceof ServerLevel) {
            boolean signal = level.hasNeighborSignal(pos);
            if (signal != state.getValue(POWERED)) {
                BlockState now = state.setValue(POWERED, signal);
                if (!signal) {
                    now = now.setValue(LIT, false);
                }
                level.setBlock(pos, now, Block.UPDATE_CLIENTS);
                level.playSound(null, pos, AtmosSounds.IGNITER_CLICK.get(), SoundSource.BLOCKS);
            }
        }
        super.neighborChanged(state, level, pos, neighbor, from, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IgniterBlockEntity(pos, state);
    }
}
