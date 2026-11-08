package io.github.west3436.zas.atmos.firelock;

import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The firelock item. It places a standalone firelock (CONT-043), and used on a door or an airlock
 * that has none, sneaking or not, it fits one to it instead (CONT-137): before the door would open.
 */
public class FirelockItem extends DoubleHighBlockItem {
    public FirelockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean airlock = state.getBlock() instanceof AirlockBlock
                && !state.getValue(AirlockBlock.FIRELOCK).fitted();
        if (!airlock && !FirelockDoorBlock.fits(state)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            boolean fitted = airlock
                    ? AirlockBlock.fitFirelock(server, AirlockBlock.origin(state, pos))
                    : FirelockDoorBlock.fit(server, pos);
            if (fitted) {
                stack.consume(1, context.getPlayer());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
