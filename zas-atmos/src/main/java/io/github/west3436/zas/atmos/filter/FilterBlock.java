package io.github.west3436.zas.atmos.filter;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A filter (CONT-013): a box on the pipe, with a side outlet for the gases it selects. */
public class FilterBlock extends SidePortBlock {
    public static final MapCodec<FilterBlock> CODEC = simpleCodec(FilterBlock::new);

    public FilterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilterBlockEntity(pos, state);
    }

    /** Opens its GUI, sending the client what it offers. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof FilterBlockEntity filter) {
            sp.openMenu(filter, buf -> FilterMenu.OPTIONS.encode(buf, filter.offered()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
