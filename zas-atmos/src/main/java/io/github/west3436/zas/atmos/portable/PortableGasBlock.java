package io.github.west3436.zas.atmos.portable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A portable that holds gas (CONT-018). What becomes of its gas when it is broken is decided here,
 * since the game removes a block before it makes the drops: see {@link PortableGasDevice}.
 */
public abstract class PortableGasBlock extends PortableBlock {
    protected PortableGasBlock(Properties properties, int width, int depth, int height) {
        super(properties, width, depth, height);
    }

    /** Mined into an item, it keeps its gas for the item; broken in creative, its gas is destroyed. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof PortableGasDevice device) {
            if (player.isCreative()) {
                device.voidContents();
            } else if (state.canHarvestBlock(level, pos, player)
                    && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                device.keepContents();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops made before the block goes, as by an explosion or {@code destroyBlock}, keep the gas too. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PortableGasDevice device
                && drops.stream().anyMatch(s -> s.is(asItem()))) {
            device.keepContents();
        }
        return drops;
    }
}
