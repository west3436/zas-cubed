package io.github.west3436.zas.atmos.gauge;

import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * A gauge's item. On a part that can carry a gauge it places one ahead of the part's own use, so a
 * canister or a pump takes a gauge without opening its window. Anywhere else, or where there is no
 * room, it does what a block's item does.
 */
public class GaugeItem extends BlockItem {
    public GaugeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (AnalyzerTarget.onFace(context.getLevel(), context.getClickedPos(), context.getClickedFace()) == null) {
            return InteractionResult.PASS;
        }
        InteractionResult placed = place(new BlockPlaceContext(context));
        return placed.consumesAction() ? placed : InteractionResult.PASS;
    }
}
