package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A held remote signaler's window (CONT-185, CONT-074): its frequency and code, and Send. It has no
 * slots. The client sends only button ids, and the server applies them to the stack in the hand it
 * was opened on, which must still be the same stack, as a held TTV's window checks.
 */
public class RemoteSignalerMenu extends AbstractContainerMenu {
    /** Changes the frequency by −1.0, −0.1, +0.1 and +1.0 (ids 0–3). */
    public static final int FREQUENCY_DOWN_10 = 0;

    /** Changes the code by −10, −1, +1 and +10 (ids 4–7). */
    public static final int CODE_DOWN_10 = 4;

    /** Sets both back to a new signaler's. */
    public static final int DEFAULT = 8;

    /** Sends a signal. */
    public static final int SEND = 9;

    /** The frequency, in tenths. */
    public static final int DATA_FREQUENCY = 0;

    public static final int DATA_CODE = 1;
    public static final int DATA_COUNT = 2;

    private static final int[] STEPS = {-10, -1, 1, 10};

    private final InteractionHand hand;
    private final ContainerData data;

    /** The stack it was opened on: null on the client. */
    @Nullable
    private final ItemStack opened;

    /** The client's menu. */
    public RemoteSignalerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, handOf(buf.readVarInt()), new SimpleContainerData(DATA_COUNT), null);
    }

    private RemoteSignalerMenu(int id, InteractionHand hand, ContainerData data, @Nullable ItemStack opened) {
        super(AtmosMenus.REMOTE_SIGNALER.get(), id);
        this.hand = hand;
        this.data = data;
        this.opened = opened;
        addDataSlots(data);
    }

    private static InteractionHand handOf(int ordinal) {
        return ordinal == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** Opens it on the signaler in a player's hand. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RemoteSignalerItem)) {
            return;
        }
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                Signal signal = RemoteSignalerItem.signal(stack);
                return index == DATA_FREQUENCY ? signal.frequency() : signal.code();
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new RemoteSignalerMenu(id, hand, data, stack),
                        Component.translatable(AtmosItems.REMOTE_SIGNALER.get().getDescriptionId())),
                buf -> buf.writeVarInt(hand.ordinal()));
    }

    /** The synced setting. */
    public Signal signal() {
        return new Signal(data.get(DATA_FREQUENCY), data.get(DATA_CODE));
    }

    /** The hand holding the signaler. */
    public InteractionHand hand() {
        return hand;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player) || opened == null || player.isSpectator()) {
            return false;
        }
        Signal now = RemoteSignalerItem.signal(opened);
        if (id >= FREQUENCY_DOWN_10 && id < CODE_DOWN_10) {
            RemoteSignalerItem.setSignal(opened, now.withFrequency(now.frequency() + STEPS[id - FREQUENCY_DOWN_10]));
        } else if (id >= CODE_DOWN_10 && id < DEFAULT) {
            RemoteSignalerItem.setSignal(opened, now.withCode(now.code() + STEPS[id - CODE_DOWN_10]));
        } else if (id == DEFAULT) {
            RemoteSignalerItem.setSignal(opened, Signal.DEFAULT);
        } else if (id == SEND) {
            return player instanceof ServerPlayer server && RemoteSignalerItem.send(server, opened) >= 0;
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the hand holds a signaler: on the server, the very stack it was opened on. */
    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive()) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        return held.getItem() instanceof RemoteSignalerItem && (opened == null || held == opened);
    }
}
