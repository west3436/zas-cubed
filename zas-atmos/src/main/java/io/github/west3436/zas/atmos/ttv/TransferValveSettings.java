package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A press of a button on a held TTV's settings screen (CONT-074, CONT-104), sent to the server. It
 * carries only the hand and the button's id: the server refuses an unknown hand or button and a hand
 * not holding a TTV, and clamps the timer to 0–600 s.
 *
 * @param hand the hand's ordinal
 * @param button the button's id
 */
public record TransferValveSettings(int hand, int button) implements CustomPacketPayload {
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
    /** Takes the first tank off, into the inventory. */
    public static final int TAKE_FIRST = 7;
    /** Takes the second tank off, into the inventory. */
    public static final int TAKE_SECOND = 8;

    private static final int BUTTONS = 9;

    public static final Type<TransferValveSettings> TYPE = new Type<>(ZasAtmosMod.id("ttv_settings"));

    public static final StreamCodec<ByteBuf, TransferValveSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TransferValveSettings::hand,
            ByteBufCodecs.VAR_INT,
            TransferValveSettings::button,
            TransferValveSettings::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handles a press on the server. */
    public static void handle(TransferValveSettings press, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive()) {
            apply(player, press);
        }
    }

    /**
     * Applies a press to the TTV in a player's hand.
     *
     * @param player the player
     * @param press the press
     * @return whether it did anything: false for an unknown hand or button, a hand not holding a TTV,
     *     or a press the TTV refuses
     */
    public static boolean apply(Player player, TransferValveSettings press) {
        InteractionHand[] hands = InteractionHand.values();
        if (press.hand < 0 || press.hand >= hands.length || press.button < 0 || press.button >= BUTTONS) {
            return false;
        }
        InteractionHand hand = hands[press.hand];
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof TransferValveItem)) {
            return false;
        }
        int now = TransferValveItem.timer(stack);
        return switch (press.button) {
            case DOWN_10 -> setTimer(stack, now - 10);
            case DOWN_1 -> setTimer(stack, now - 1);
            case UP_1 -> setTimer(stack, now + 1);
            case UP_10 -> setTimer(stack, now + 10);
            case DEFAULT -> setTimer(stack, TransferValve.DEFAULT_TIMER);
            case VALVE -> {
                boolean moved = TransferValveItem.toggle(stack);
                if (moved) {
                    sound(player, SoundEvents.LEVER_CLICK, 1.0f, 0.8f);
                }
                yield moved;
            }
            case START -> TransferValveItem.start(player, hand) != null;
            default -> take(player, stack, press.button == TAKE_FIRST);
        };
    }

    private static boolean setTimer(ItemStack stack, int seconds) {
        TransferValveItem.setTimer(stack, seconds);
        return true;
    }

    private static boolean take(Player player, ItemStack stack, boolean first) {
        TransferValve valve = TransferValveItem.valve(stack);
        ItemStack tank = first ? valve.detachFirst() : valve.detachSecond();
        if (tank.isEmpty()) {
            return false;
        }
        TransferValveItem.save(stack, valve);
        player.getInventory().placeItemBackInInventory(tank);
        sound(player, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
        return true;
    }

    /** Presses are applied only on the server, so the player's own client plays nothing of its own. */
    private static void sound(Player player, SoundEvent sound, float volume, float pitch) {
        player.level()
                .playSound(
                        null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
