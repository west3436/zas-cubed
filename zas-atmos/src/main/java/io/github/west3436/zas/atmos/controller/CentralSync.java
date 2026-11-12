package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.label.Labels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the server sends the player viewing a central controller's GUI, twice a second (CONT-123).
 * The client never sends settings back in this form; its buttons go through {@link
 * CentralMenu#clickMenuButton}, which the server checks (CONT-074).
 *
 * @param containerId the menu it is for
 * @param status the central controller's {@link ControllerStatus} ordinal
 * @param energy its buffer's FE
 * @param capacity its buffer's capacity
 * @param range its range, in chunks
 * @param maxRange the server's maximum range, in chunks
 * @param rows its area air controllers, in the order it claimed them
 */
public record CentralSync(
        int containerId, int status, int energy, int capacity, int range, int maxRange, List<Row> rows)
        implements CustomPacketPayload {

    /** The most rows a client reads. */
    public static final int MAX_ROWS = 4_096;

    /**
     * One claimed area air controller.
     *
     * @param pos its position
     * @param pressure its zone's pressure in kPa, or NaN before its first reading
     * @param temperature its zone's temperature in K, or NaN before its first reading
     * @param danger its danger level, 0 to 2
     * @param powered whether it paid its own draw at its last step
     * @param priority its {@link ZonePriority} ordinal
     * @param contact whether it is in contact, loaded and stepping, so these are its readings now
     *     rather than its last
     * @param label what a player called it, as last seen, or the empty string
     */
    public record Row(
            BlockPos pos,
            double pressure,
            double temperature,
            int danger,
            boolean powered,
            int priority,
            boolean contact,
            String label) {}

    public static final Type<CentralSync> TYPE = new Type<>(ZasAtmosMod.id("central_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CentralSync> CODEC =
            StreamCodec.of((buf, s) -> s.write(buf), CentralSync::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(status);
        buf.writeVarInt(energy);
        buf.writeVarInt(capacity);
        buf.writeVarInt(range);
        buf.writeVarInt(maxRange);
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeBlockPos(r.pos());
            buf.writeDouble(r.pressure());
            buf.writeDouble(r.temperature());
            buf.writeByte(r.danger());
            buf.writeBoolean(r.powered());
            buf.writeByte(r.priority());
            buf.writeBoolean(r.contact());
            buf.writeUtf(r.label());
        }
    }

    private static CentralSync read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int status = buf.readVarInt();
        int energy = buf.readVarInt();
        int capacity = buf.readVarInt();
        int range = buf.readVarInt();
        int maxRange = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(
                    buf.readBlockPos(),
                    buf.readDouble(),
                    buf.readDouble(),
                    Math.max(0, Math.min(2, buf.readByte())),
                    buf.readBoolean(),
                    Math.max(0, Math.min(ZonePriority.values().length - 1, buf.readByte())),
                    buf.readBoolean(),
                    Labels.clean(buf.readUtf())));
        }
        return new CentralSync(
                containerId,
                Math.max(0, Math.min(ControllerStatus.values().length - 1, status)),
                energy,
                capacity,
                range,
                maxRange,
                rows);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** On the client: hands the state to the open menu it is for. */
    public static void handle(CentralSync sync, IPayloadContext context) {
        if (context.player().containerMenu instanceof CentralMenu menu && menu.containerId == sync.containerId()) {
            menu.receive(sync);
        }
    }
}
