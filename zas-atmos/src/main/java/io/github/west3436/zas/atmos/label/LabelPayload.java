package io.github.west3436.zas.atmos.label;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A label typed in a device window's title bar, sent to the server. It names the window, not a
 * block, so it can only reach the device whose window the sender has open: the server refuses one
 * for any other window, for a window of a device that takes no label, from a spectator, and from a
 * player the window no longer lets act, as it refuses that window's buttons (CONT-074). The text is
 * cleaned and cut by {@link Labels#clean} on the server, whatever the client sent; nothing clears
 * the label.
 *
 * @param containerId the open window
 * @param text what was typed
 */
public record LabelPayload(int containerId, String text) implements CustomPacketPayload {
    /** The most characters the server reads: more than a label keeps, so a long one is cut, not refused. */
    public static final int MAX_SENT = 256;

    public static final Type<LabelPayload> TYPE = new Type<>(ZasAtmosMod.id("label"));

    public static final StreamCodec<ByteBuf, LabelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            LabelPayload::containerId,
            ByteBufCodecs.stringUtf8(MAX_SENT),
            LabelPayload::text,
            LabelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handles one on the server. */
    public static void handle(LabelPayload sent, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, sent);
        }
    }

    /**
     * Sets the label of the device whose window a player has open, if the window is the one named and
     * lets the player act.
     *
     * @return whether the server took it
     */
    public static boolean apply(ServerPlayer player, LabelPayload sent) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != sent.containerId()
                || player.isSpectator()
                || !(menu instanceof LabelledMenu window)
                || !menu.stillValid(player)) {
            return false;
        }
        Labelled device = window.labelled();
        if (device == null || ((BlockEntity) device).isRemoved()) {
            return false;
        }
        device.setLabel(sent.text());
        return true;
    }
}
