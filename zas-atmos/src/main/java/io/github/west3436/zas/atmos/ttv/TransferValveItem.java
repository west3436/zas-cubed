package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * A tank transfer valve as an item (CONT-103): two gas tanks joined by a valve, carried as {@link
 * AtmosComponents#TTV_TANKS}. As a bundle does, it takes a tank right-clicked onto it in an
 * inventory, and gives its last tank back to an empty cursor. Using it opens its {@linkplain
 * TransferValveMenu screen}, which has a slot for each tank, and where the valve opens and closes by
 * hand and the timer is set and started.
 *
 * <p>Its timer never counts down in this form: starting it primes the TTV, which becomes a {@link
 * PrimedTransferValve} (CONT-104). Its tanks' gas does go on reacting while it is in a player's
 * inventory or lying on the ground (CONT-100).
 */
public class TransferValveItem extends Item {
    public TransferValveItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ---- A TTV stack's state -----------------------------------------------------------------

    /** The stack's tanks and valve, as a working copy; {@link #save} writes it back. */
    public static TransferValve valve(ItemStack stack) {
        ItemContainerContents tanks = stack.getOrDefault(AtmosComponents.TTV_TANKS.get(), ItemContainerContents.EMPTY);
        return new TransferValve(
                slot(tanks, 0), slot(tanks, 1), stack.getOrDefault(AtmosComponents.TTV_OPEN.get(), false));
    }

    private static ItemStack slot(ItemContainerContents tanks, int slot) {
        return slot < tanks.getSlots() ? tanks.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Writes a valve to a stack. A valve without both tanks has nothing to hold open, and is closed. */
    public static void save(ItemStack stack, TransferValve valve) {
        stack.set(
                AtmosComponents.TTV_TANKS.get(),
                ItemContainerContents.fromItems(List.of(valve.first(), valve.second())));
        if (valve.isOpen() && valve.joined()) {
            stack.set(AtmosComponents.TTV_OPEN.get(), true);
        } else {
            stack.remove(AtmosComponents.TTV_OPEN.get());
        }
    }

    /** The timer, s (CONT-104). */
    public static int timer(ItemStack stack) {
        return stack.getOrDefault(AtmosComponents.TTV_TIMER.get(), TransferValve.DEFAULT_TIMER);
    }

    /** The tank in a place, as a copy; an empty stack if there is none. */
    public static ItemStack tank(ItemStack stack, int place) {
        return slot(stack.getOrDefault(AtmosComponents.TTV_TANKS.get(), ItemContainerContents.EMPTY), place);
    }

    /**
     * Puts a stack in a place as it is, checking nothing. For whoever has checked already, or a client
     * being told what the server's stack holds.
     */
    static void setTank(ItemStack stack, int place, ItemStack tank) {
        TransferValve valve = valve(stack);
        save(
                stack,
                new TransferValve(
                        place == TransferValve.FIRST ? tank : valve.first(),
                        place == TransferValve.FIRST ? valve.second() : tank,
                        valve.isOpen()));
    }

    /** Sets the timer, clamped to 0–600 s (CONT-104). */
    public static void setTimer(ItemStack stack, int seconds) {
        stack.set(AtmosComponents.TTV_TIMER.get(), TransferValve.clampTimer(seconds));
    }

    /** How many tanks a TTV stack holds. */
    public static int tankCount(ItemStack stack) {
        ItemContainerContents tanks = stack.getOrDefault(AtmosComponents.TTV_TANKS.get(), ItemContainerContents.EMPTY);
        return (int) tanks.nonEmptyStream().count();
    }

    // ---- What a player does with it ----------------------------------------------------------

    /**
     * Opens a closed valve or closes an open one, by hand (CONT-104).
     *
     * @return whether it moved: it needs both tanks to join
     */
    public static boolean toggle(ItemStack stack) {
        TransferValve valve = valve(stack);
        if (!valve.joined()) {
            return false;
        }
        if (valve.isOpen()) {
            valve.close();
        } else {
            valve.open();
        }
        save(stack, valve);
        return true;
    }

    /**
     * Starts the timer of the TTV in a player's hand, priming it (CONT-104): the item becomes a
     * {@link PrimedTransferValve} at the player's feet, in creative too, or its tanks would exist
     * twice. An open valve has nothing left for a timer to do, so it refuses.
     *
     * @return the primed TTV, or null if it refused
     */
    public static PrimedTransferValve start(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof TransferValveItem)
                || !(player.level() instanceof ServerLevel level)
                || valve(stack).isOpen()) {
            return null;
        }
        PrimedTransferValve primed = PrimedTransferValve.prime(level, player, stack);
        stack.shrink(1);
        return primed;
    }

    /**
     * Fits a tank in the first free place, as every way of loading a TTV but a slot does. The stack
     * given isn't changed: the caller takes it away.
     *
     * @return whether it went on
     */
    static boolean fit(ItemStack stack, ItemStack tank, Player player) {
        TransferValve valve = valve(stack);
        if (!valve.attach(tank.copy())) {
            return false;
        }
        save(stack, valve);
        sound(player, true);
        return true;
    }

    /**
     * Takes the tank in a place off, closing an open valve first, so that it has its share of the gas.
     *
     * @return the tank, or an empty stack if there was none, which changes nothing
     */
    static ItemStack takeOff(ItemStack stack, int place) {
        TransferValve valve = valve(stack);
        ItemStack tank = valve.detach(place);
        if (!tank.isEmpty()) {
            save(stack, valve);
        }
        return tank;
    }

    /** A right-click onto the TTV, from wherever it is: a tank goes on, and an empty cursor takes the last one off. */
    static boolean rightClicked(ItemStack stack, ItemStack carried, Player player, SlotAccess cursor) {
        if (carried.isEmpty()) {
            int place = tank(stack, TransferValve.SECOND).isEmpty() ? TransferValve.FIRST : TransferValve.SECOND;
            ItemStack off = takeOff(stack, place);
            if (off.isEmpty()) {
                return false;
            }
            cursor.set(off);
            sound(player, false);
            return true;
        }
        if (!fit(stack, carried, player)) {
            return false;
        }
        cursor.set(ItemStack.EMPTY);
        return true;
    }

    /** A tank right-clicked onto the TTV goes on it; an empty cursor takes its last tank off. */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return action == ClickAction.SECONDARY
                && stack.getCount() == 1
                && slot.allowModification(player)
                && rightClicked(stack, carried, player, access);
    }

    /** The TTV carried onto a tank takes it, as a bundle takes what it is carried onto. */
    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        ItemStack other = slot.getItem();
        if (action != ClickAction.SECONDARY
                || stack.getCount() != 1
                || !slot.allowModification(player)
                || !fit(stack, other, player)) {
            return false;
        }
        slot.safeTake(1, 1, player);
        return true;
    }

    static void sound(Player player, boolean in) {
        player.playSound(in ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
    }

    /** Opens its screen, from the server, which alone can say what is on it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getCount() != 1) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer server) {
            TransferValveMenu.open(server, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ---- Its gas, while it is an item --------------------------------------------------------

    /**
     * One atmos tick of a TTV stack's gas (CONT-100, CONT-038).
     *
     * @param stack the TTV
     * @param level the level it is in
     * @param holder who or what holds it
     * @return whether it was destroyed; the caller removes the stack
     */
    public static boolean step(ItemStack stack, ServerLevel level, Entity holder) {
        TransferValve valve = valve(stack);
        TransferValve.Step step = valve.step(level, holder, null);
        if (step == TransferValve.Step.CHANGED) {
            save(stack, valve);
        }
        return step == TransferValve.Step.DESTROYED;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (level instanceof ServerLevel server && AtmosTicks.due(server, holder) && step(stack, server, holder)) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server
                && AtmosTicks.due(server, entity)
                && step(stack, server, entity)) {
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TransferValve valve = valve(stack);
        tooltip.add(Component.translatable(
                        valve.isOpen()
                                ? "item.zas_atmos.tank_transfer_valve.open"
                                : "item.zas_atmos.tank_transfer_valve.closed")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zas_atmos.tank_transfer_valve.timer", timer(stack))
                .withStyle(ChatFormatting.GRAY));
        if (valve.first().isEmpty() && valve.second().isEmpty()) {
            tooltip.add(Component.translatable("item.zas_atmos.tank_transfer_valve.no_tanks")
                    .withStyle(ChatFormatting.GRAY));
        }
        tank(valve.first(), tooltip);
        tank(valve.second(), tooltip);
    }

    private static void tank(ItemStack tank, List<Component> tooltip) {
        if (tank.isEmpty()) {
            return;
        }
        tooltip.add(tank.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        GasTankItem.lines(tank, tooltip);
    }
}
