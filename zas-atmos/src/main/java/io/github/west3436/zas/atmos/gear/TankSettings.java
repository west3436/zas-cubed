package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A press of a button on a held tank's settings screen (LIFE-044), sent to the server. It carries
 * only the hand and the button's id: the server refuses an unknown hand or button, and a hand not
 * holding a tank, and clamps the distribution pressure to 0–304 kPa.
 *
 * @param hand the hand's ordinal
 * @param button the button's id
 */
public record TankSettings(int hand, int button) implements CustomPacketPayload {
    /** Lowers the distribution pressure by 10 kPa. */
    public static final int DOWN_10 = 0;
    /** Lowers it by 1 kPa. */
    public static final int DOWN_1 = 1;
    /** Raises it by 1 kPa. */
    public static final int UP_1 = 2;
    /** Raises it by 10 kPa. */
    public static final int UP_10 = 3;
    /** Sets it back to the tank's default. */
    public static final int DEFAULT = 4;
    /** Sets it to 0, closing the tank. */
    public static final int CLOSE = 5;

    private static final int BUTTONS = 6;

    public static final Type<TankSettings> TYPE = new Type<>(ZasAtmosMod.id("tank_settings"));

    public static final StreamCodec<ByteBuf, TankSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TankSettings::hand, ByteBufCodecs.VAR_INT, TankSettings::button, TankSettings::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handles a press on the server. */
    public static void handle(TankSettings press, IPayloadContext context) {
        InteractionHand hand = handOf(press.hand);
        if (hand != null && context.player() instanceof ServerPlayer player && player.isAlive()) {
            apply(player.getItemInHand(hand), press);
        }
    }

    private static InteractionHand handOf(int ordinal) {
        InteractionHand[] hands = InteractionHand.values();
        return ordinal >= 0 && ordinal < hands.length ? hands[ordinal] : null;
    }

    /**
     * Applies a press to a tank stack.
     *
     * @return whether it was applied: false for an unknown hand or button, or a stack that isn't a tank
     */
    public static boolean apply(ItemStack stack, TankSettings press) {
        if (handOf(press.hand) == null
                || press.button < 0
                || press.button >= BUTTONS
                || !(stack.getItem() instanceof GasTankItem tank)) {
            return false;
        }
        double now = GasTankItem.distributionPressure(stack);
        double next = switch (press.button) {
            case DOWN_10 -> now - 10;
            case DOWN_1 -> now - 1;
            case UP_1 -> now + 1;
            case UP_10 -> now + 10;
            case DEFAULT -> tank.defaultDistribution();
            default -> 0;
        };
        GasTankItem.setDistributionPressure(stack, next);
        return true;
    }
}
