package io.github.west3436.zas.atmos.turbine;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The gas turbine (CONT-161, CONT-162): it lets the combustion chamber's gas out to the cell in front
 * of it, taking work from the gas into the shaft it shares with its {@linkplain CompressorBlock
 * compressor}, and its generator turns the shaft's energy into FE. Its block entity does the work of
 * the pair, and its redstone setting reads the signal at this block (CONT-170).
 */
public class TurbineBlock extends TurbinePartBlock implements EntityBlock {
    public static final MapCodec<TurbineBlock> CODEC = simpleCodec(TurbineBlock::new);

    public TurbineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean compressor() {
        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** How full its FE store is (CONT-175). The compressor, which holds nothing, gives none. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TurbineBlockEntity turbine ? turbine.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AtmosBlockEntities.TURBINE.get()) {
            return null;
        }
        return level.isClientSide
                ? (l, p, s, be) -> TurbineBlockEntity.clientTick(s, p)
                : (l, p, s, be) -> ((TurbineBlockEntity) be).serverTick();
    }
}
