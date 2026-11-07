package io.github.west3436.zas.atmos.analyzer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The atmos analyzer (CONT-034). Used on nothing, its window reads the air the player is in, and
 * follows the player. Used on a block, it reads what {@link AnalyzerTarget#on} says, before the block
 * can open a window of its own. A tank in the other hand gets a tab of its own. Holding one shows the
 * atmosphere HUD (CLIENT-001), through the {@code #zas:shows_hud} item tag.
 */
public class AnalyzerItem extends Item {
    public AnalyzerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            AnalyzerMenu.open(server, hand, AnalyzerTarget.here());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Reads the block used on, ahead of the block's own use, so a canister or a vent doesn't open its
     * own window. The server refuses a block beyond the player's reach, as the game's own packet
     * handling does, so nothing else that calls this can read one either.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            BlockHitResult hit = new BlockHitResult(
                    context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
            if (!server.canInteractWithBlock(hit.getBlockPos(), 1.0)
                    || !AnalyzerMenu.open(server, context.getHand(), AnalyzerTarget.on(context.getLevel(), hit))) {
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
