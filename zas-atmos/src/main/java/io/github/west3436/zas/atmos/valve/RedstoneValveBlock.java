package io.github.west3436.zas.atmos.valve;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The redstone valve (CONT-010): open exactly while it gets a signal. A hand doesn't move it. */
public class RedstoneValveBlock extends ValveBlock {
    public static final MapCodec<RedstoneValveBlock> CODEC = simpleCodec(RedstoneValveBlock::new);

    public RedstoneValveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(OPEN, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** Follows the signal. A neighbour's change that leaves the signal alone changes nothing. */
    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!level.isClientSide) {
            boolean signal = level.hasNeighborSignal(pos);
            if (signal != state.getValue(OPEN)) {
                turn(
                        level,
                        pos,
                        state,
                        signal,
                        signal ? AtmosSounds.REDSTONE_VALVE_OPEN : AtmosSounds.REDSTONE_VALVE_CLOSE);
            }
        }
        super.neighborChanged(state, level, pos, neighbor, from, moving);
    }
}
