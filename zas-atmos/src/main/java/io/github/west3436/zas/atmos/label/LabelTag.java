package io.github.west3436.zas.atmos.label;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A name tag used on a device that takes a label, as one is used on a mob. A tag renamed in an anvil
 * sets the device's label to its name and is used up, except in creative or where the device has
 * that label already. A tag with no name clears a label and is kept. Either takes the click from the
 * block, so its window doesn't open and a door doesn't move.
 *
 * <p>Anywhere else the tag does nothing and the block gets the click as before: on a block that
 * takes no label, and with a tag that has no name on a device that has no label. A spectator's tag
 * does nothing. The game refuses a click the player can't reach or isn't allowed, and another mod
 * may cancel it, before this is asked.
 */
public final class LabelTag {
    private LabelTag() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(LabelTag::use);
    }

    private static void use(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!stack.is(Items.NAME_TAG) || player.isSpectator()) {
            return;
        }
        Level level = event.getLevel();
        Labelled device = Labels.at(level, event.getPos());
        String label = Labels.of(stack);
        if (device == null || (label.isEmpty() && device.label().isEmpty())) {
            return;
        }
        if (!level.isClientSide) {
            boolean changed = device.setLabel(label);
            if (label.isEmpty()) {
                player.displayClientMessage(Component.translatable("block.zas_atmos.label.cleared"), true);
            } else {
                if (changed) {
                    stack.consume(1, player);
                }
                player.displayClientMessage(Component.translatable("block.zas_atmos.label.set", label), true);
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }
}
