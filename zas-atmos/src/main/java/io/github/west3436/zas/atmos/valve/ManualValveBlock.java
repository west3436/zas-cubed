package io.github.west3436.zas.atmos.valve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The manual valve (CONT-010): using it turns its lever. Redstone doesn't move it. */
public class ManualValveBlock extends ValveBlock {
    public static final MapCodec<ManualValveBlock> CODEC = simpleCodec(ManualValveBlock::new);

    public ManualValveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            turn(level, pos, state, !state.getValue(OPEN), SoundEvents.LEVER_CLICK);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
