package io.github.west3436.zas.atmos.pipe;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * The item of a pipe, a pipe cap or a large gas tank, which keeps its dye (CONT-114). The colour is carried as the
 * vanilla block state component, so a dyed item places a dyed block and a dyed block, mined, drops
 * an item of its colour (its loot table copies the colour). An undyed item has no component, so it
 * stacks with new ones.
 */
public class PipeItem extends BlockItem {
    public PipeItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A stack's colour: {@link PipeColor#NONE} unless it carries one. */
    public static PipeColor color(ItemStack stack) {
        BlockItemStateProperties props = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        PipeColor color = props.get(PipeColor.PROPERTY);
        return color == null ? PipeColor.NONE : color;
    }

    /** A stack of a pipe or cap item in a colour; undyed stacks carry nothing. */
    public static ItemStack of(PipeItem item, PipeColor color, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (color != PipeColor.NONE) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(PipeColor.PROPERTY, color));
        }
        return stack;
    }

    /**
     * A pipe used on a block that takes one, such as a station wall, goes into it (#487). Sneaking, it
     * is placed against the block, as against any other.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (getBlock() instanceof PipeBlock && !context.isSecondaryUseActive()) {
            InteractionResult inserted = EncasedPipeBlock.insert(context);
            if (inserted != InteractionResult.PASS) {
                return inserted;
            }
        }
        return super.useOn(context);
    }

    @Override
    public Component getName(ItemStack stack) {
        PipeColor color = color(stack);
        if (color == PipeColor.NONE) {
            return super.getName(stack);
        }
        return Component.translatable(
                "item.zas_atmos.dyed",
                Component.translatable("color.minecraft." + color.name().toLowerCase(Locale.ROOT)),
                super.getName(stack));
    }
}
