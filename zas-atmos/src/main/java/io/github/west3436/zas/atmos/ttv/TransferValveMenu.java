package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A held TTV's GUI (CONT-103, CONT-104, CONT-074): a slot for each of its tanks, its valve and timer,
 * and the player's inventory. The client sends only clicks and button ids, and the server checks
 * each.
 *
 * <p>A menu on a held item is the classic way to duplicate one, so this one has nothing to duplicate
 * from:
 *
 * <ul>
 *   <li>It keeps no copy. The tank slots read and write the stack's own {@code zas_atmos:ttv_tanks},
 *       so closing it has nothing to put back, and a tank that fails while it is open just goes.
 *   <li>The TTV can't move while it is open: nothing a click can do reaches its slot, and a number
 *       key can't take it from where it is.
 *   <li>It stops as soon as the hand no longer holds the stack it was opened on, one TTV ({@link
 *       #stillValid}). It acts on nothing after that, and the game closes it at the next tick. A tank
 *       on the cursor then goes back to the inventory.
 * </ul>
 */
public class TransferValveMenu extends AbstractContainerMenu {
    /** Lowers the timer by 10 s. */
    public static final int DOWN_10 = 0;
    /** Lowers it by 1 s. */
    public static final int DOWN_1 = 1;
    /** Raises it by 1 s. */
    public static final int UP_1 = 2;
    /** Raises it by 10 s. */
    public static final int UP_10 = 3;
    /** Sets it back to the default, 10 s. */
    public static final int DEFAULT = 4;
    /** Opens a closed valve or closes an open one, by hand. */
    public static final int VALVE = 5;
    /** Starts the timer, which primes the TTV. */
    public static final int START = 6;

    /** How wide its panel is. */
    public static final int WIDTH = 212;

    /** Where the tank slots are drawn. */
    public static final int TANK_X = 8;

    public static final int FIRST_Y = 22;
    public static final int SECOND_Y = 45;

    /** Where the player's inventory starts. */
    public static final int INVENTORY_X = 25;

    public static final int INVENTORY_Y = 110;

    /** Where a TTV in the off hand is drawn, left of the hotbar as the game's own HUD has it. */
    public static final int OFF_HAND_X = 5;

    /** The inventory's slots in this menu follow the tanks, whose indices are their places. */
    private static final int TANKS = 2;

    private static final int HOTBAR_START = TANKS + 27;
    private static final int INVENTORY_END = HOTBAR_START + 9;

    /** The tank slots belong to no container of their own: they are the stack's data. */
    private static final SimpleContainer NO_CONTAINER = new SimpleContainer(0);

    private final Inventory inventory;
    private final int valveIndex;
    private final int valveSlot;

    /** The stack this menu was opened on. Null on the client, whose stacks are replaced as they sync. */
    @Nullable
    private final ItemStack opened;

    /** The server's menu, on what a player's hand holds. */
    public TransferValveMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, indexOf(inventory, hand), inventory.player.getItemInHand(hand));
    }

    /** The client's menu. */
    public TransferValveMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt(), null);
    }

    private TransferValveMenu(int id, Inventory inventory, int valveIndex, @Nullable ItemStack opened) {
        super(AtmosMenus.TANK_TRANSFER_VALVE.get(), id);
        this.inventory = inventory;
        this.valveIndex = valveIndex;
        this.valveSlot = valveIndex == Inventory.SLOT_OFFHAND ? INVENTORY_END : HOTBAR_START + valveIndex;
        this.opened = opened;
        addSlot(new TankSlot(TransferValve.FIRST, FIRST_Y));
        addSlot(new TankSlot(TransferValve.SECOND, SECOND_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = INVENTORY_X + col * 18;
            addSlot(
                    col == valveIndex
                            ? new ValveSlot(inventory, col, x, INVENTORY_Y + 58)
                            : new Slot(inventory, col, x, INVENTORY_Y + 58));
        }
        // Only a slot syncs a stack to a client while a menu is open, so a TTV in the off hand needs one.
        if (valveIndex == Inventory.SLOT_OFFHAND) {
            addSlot(new ValveSlot(inventory, valveIndex, OFF_HAND_X, INVENTORY_Y + 58));
        }
    }

    private static int indexOf(Inventory inventory, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
    }

    /** Opens it on a player's hand. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        int index = indexOf(player.getInventory(), hand);
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new TransferValveMenu(id, inventory, hand),
                        Component.translatable("item.zas_atmos.tank_transfer_valve")),
                buf -> buf.writeVarInt(index));
    }

    /** The stack where the TTV is: the only one this menu ever reads or changes. */
    private ItemStack stack() {
        return inventory.getItem(valveIndex);
    }

    private InteractionHand hand() {
        return valveIndex == Inventory.SLOT_OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** The TTV's tanks and valve, as a working copy. */
    public TransferValve valve() {
        return TransferValveItem.valve(stack());
    }

    /** The TTV's timer, s. */
    public int timer() {
        return TransferValveItem.timer(stack());
    }

    /** The index in this menu of the slot the TTV itself is in. */
    public int valveSlot() {
        return valveSlot;
    }

    /**
     * Whether this is still a menu on one TTV in that hand. On the server it must be the very stack it
     * was opened on: a TTV that was moved, or replaced by another, is no longer what the client sees.
     */
    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = stack();
        return player.isAlive()
                && !player.isSpectator()
                && stack.getCount() == 1
                && stack.getItem() instanceof TransferValveItem
                && (opened == null || (stack == opened && player.getItemInHand(hand()) == opened));
    }

    /**
     * Every click comes through here, on the server and in the client's prediction of it, before the
     * game's own handling: a menu that is no longer valid does nothing, and neither does a click that
     * would move the TTV or make a tank from nothing.
     */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!stillValid(player)) {
            return;
        }
        if (refuses(slotId, button, type)) {
            resetQuickCraft();
            if (slotId == valveSlot && type == ClickType.PICKUP && button == 1) {
                TransferValveItem.rightClicked(
                        stack(), getCarried(), player, SlotAccess.of(this::getCarried, this::setCarried));
            }
            return;
        }
        if (type == ClickType.SWAP
                && isTank(slotId)
                && isSwapKey(button)
                && inventory.getItem(button).isEmpty()) {
            // The game copies the slot's stack into the inventory, then empties the slot. Closing an
            // open valve first gives the tank its share of the gas, as every other way of taking it does.
            if (valve().isOpen()) {
                TransferValveItem.toggle(stack());
            }
        }
        super.clicked(slotId, button, type, player);
    }

    private boolean refuses(int slotId, int button, ClickType type) {
        if (type == ClickType.QUICK_CRAFT) {
            return false; // a drag starts and ends on no slot, and canDragTo refuses the slots it mustn't touch
        }
        if (slotId == valveSlot) {
            return true;
        }
        return switch (type) {
            case SWAP -> button == valveIndex;
            case CLONE -> isTank(slotId);
            default -> false;
        };
    }

    private static boolean isTank(int slotId) {
        return slotId >= 0 && slotId < TANKS;
    }

    /** Whether a number key's click names an inventory slot, as the game's own handling checks. */
    private static boolean isSwapKey(int button) {
        return Inventory.isHotbarSlot(button) || button == Inventory.SLOT_OFFHAND;
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused, and so is a stale menu. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) {
            return false;
        }
        ItemStack stack = stack();
        int now = TransferValveItem.timer(stack);
        return switch (id) {
            case DOWN_10 -> setTimer(stack, now - 10);
            case DOWN_1 -> setTimer(stack, now - 1);
            case UP_1 -> setTimer(stack, now + 1);
            case UP_10 -> setTimer(stack, now + 10);
            case DEFAULT -> setTimer(stack, TransferValve.DEFAULT_TIMER);
            case VALVE -> {
                boolean moved = TransferValveItem.toggle(stack);
                if (moved) {
                    SoundEvent event = TransferValveItem.valve(stack).isOpen()
                            ? AtmosSounds.TTV_OPEN.get()
                            : AtmosSounds.VALVE_CLOSE.get();
                    player.level()
                            .playSound(
                                    null,
                                    player.getX(),
                                    player.getY(),
                                    player.getZ(),
                                    event,
                                    SoundSource.PLAYERS,
                                    1.0f,
                                    1.0f);
                }
                yield moved;
            }
            case START -> start(player);
            default -> false;
        };
    }

    private static boolean setTimer(ItemStack stack, int seconds) {
        TransferValveItem.setTimer(stack, seconds);
        return true;
    }

    /** The timer waits for an empty cursor: a tank on it is one the player hasn't finished moving. */
    private boolean start(Player player) {
        return getCarried().isEmpty() && TransferValveItem.start(player, hand()) != null;
    }

    /**
     * A shift-click. A tank goes from the inventory to the first free place, as a right-click fits it,
     * and from a slot to the inventory. Nothing else moves, and the TTV's slot never gets here.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        if (isTank(index)) {
            // The valve is a working copy, so a full inventory leaves it as it was: open, if it was.
            TransferValve valve = valve();
            ItemStack tank = valve.detach(index);
            ItemStack moved = tank.copy();
            if (!moveItemStackTo(tank, TANKS, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            TransferValveItem.save(stack(), valve);
            TransferValveItem.sound(player, false);
            return moved;
        }
        ItemStack tank = slot.getItem();
        if (!TransferValveItem.fit(stack(), tank, player)) {
            return ItemStack.EMPTY;
        }
        ItemStack moved = tank.copy();
        slot.setByPlayer(ItemStack.EMPTY);
        return moved;
    }

    /** A drag puts nothing in a tank's slot or the TTV's: a click does. */
    @Override
    public boolean canDragTo(Slot slot) {
        return isOrdinary(slot);
    }

    /** A double-click gathers nothing from a tank's slot or the TTV's. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return isOrdinary(slot);
    }

    private boolean isOrdinary(Slot slot) {
        return !isTank(slot.index) && slot.index != valveSlot;
    }

    /** The slot the TTV itself is in: drawn, so a player can see it, and otherwise out of reach. */
    private static final class ValveSlot extends Slot {
        ValveSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }

    /** A place on the valve as a slot: all it holds is what the stack's own data says it holds. */
    private final class TankSlot extends Slot {
        TankSlot(int place, int y) {
            super(NO_CONTAINER, place, TANK_X, y);
        }

        @Override
        public ItemStack getItem() {
            return TransferValveItem.tank(stack(), getContainerSlot());
        }

        /**
         * Puts a stack here as it is, which is how a client hears what the server's stack holds. The
         * rules are in {@link #mayPlace}, which the game asks first. Writing to anything but a TTV could
         * change the one shared empty stack, so this writes to nothing else.
         */
        @Override
        public void set(ItemStack tank) {
            ItemStack stack = stack();
            if (stack.getItem() instanceof TransferValveItem) {
                TransferValveItem.setTank(stack, getContainerSlot(), tank);
            }
        }

        @Override
        public ItemStack remove(int amount) {
            return amount > 0 ? TransferValveItem.takeOff(stack(), getContainerSlot()) : ItemStack.EMPTY;
        }

        @Override
        public boolean mayPickup(Player player) {
            return stillValid(player);
        }

        /** The same rule as a right-click: the valve's own. */
        @Override
        public boolean mayPlace(ItemStack tank) {
            return stillValid(inventory.player) && valve().accepts(tank);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        /** Adding or removing a tank sounds as a bundle does, whichever way it was done. */
        @Override
        public void setByPlayer(ItemStack now, ItemStack was) {
            super.setByPlayer(now, was);
            if (!ItemStack.matches(now, was)) {
                TransferValveItem.sound(inventory.player, !now.isEmpty());
            }
        }
    }
}
