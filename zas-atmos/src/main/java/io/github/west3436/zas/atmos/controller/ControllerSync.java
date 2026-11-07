package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the server sends the player viewing a controller's GUI, twice a second: everything the
 * screen shows (CONT-049, CONT-074). The client never sends settings back in this form; its buttons
 * go through {@link ControllerMenu#clickMenuButton}, which the server checks.
 *
 * @param containerId the menu it is for
 * @param mode the mode's ordinal
 * @param preset the preset's name
 * @param edited whether the preset was edited
 * @param status the status's ordinal
 * @param energy the buffer's FE
 * @param capacity the buffer's capacity
 * @param reading the reading selected for editing
 * @param bound the bound selected for editing, 0 to 3
 * @param labels each reading's label: "pressure", "temperature", "others" or a gas id
 * @param values each reading's last value; empty before the first
 * @param ratings each reading's last danger level
 * @param bounds each reading's four bounds, in a row
 * @param parts the parts it covers
 * @param pin the last pin's outcome, or "" before one
 * @param breach whether its last reading was a breach (CONT-043)
 * @param fire whether a fire alarm it covers was triggered at its last reading (CONT-044)
 * @param central the central controller it belongs to, or null (CONT-118)
 */
public record ControllerSync(
        int containerId,
        int mode,
        String preset,
        boolean edited,
        int status,
        int energy,
        int capacity,
        int reading,
        int bound,
        List<String> labels,
        double[] values,
        int[] ratings,
        double[] bounds,
        List<AreaAirControllerBlockEntity.Part> parts,
        String pin,
        boolean breach,
        boolean fire,
        BlockPos central)
        implements CustomPacketPayload {

    public static final Type<ControllerSync> TYPE = new Type<>(ZasAtmosMod.id("controller_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerSync> CODEC =
            StreamCodec.of((buf, s) -> s.write(buf), ControllerSync::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(mode);
        buf.writeUtf(preset);
        buf.writeBoolean(edited);
        buf.writeVarInt(status);
        buf.writeVarInt(energy);
        buf.writeVarInt(capacity);
        buf.writeVarInt(reading);
        buf.writeVarInt(bound);
        buf.writeVarInt(labels.size());
        labels.forEach(buf::writeUtf);
        buf.writeVarInt(values.length);
        for (double v : values) {
            buf.writeDouble(v);
        }
        buf.writeVarIntArray(ratings);
        buf.writeVarInt(bounds.length);
        for (double b : bounds) {
            buf.writeDouble(b);
        }
        buf.writeVarInt(parts.size());
        for (AreaAirControllerBlockEntity.Part p : parts) {
            buf.writeResourceLocation(p.block());
            buf.writeBlockPos(p.pos());
            buf.writeBoolean(p.inZone());
            buf.writeBoolean(p.linked());
            buf.writeBoolean(p.powered());
        }
        buf.writeUtf(pin);
        buf.writeBoolean(breach);
        buf.writeBoolean(fire);
        buf.writeBoolean(central != null);
        if (central != null) {
            buf.writeBlockPos(central);
        }
    }

    private static ControllerSync read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int mode = buf.readVarInt();
        String preset = buf.readUtf();
        boolean edited = buf.readBoolean();
        int status = buf.readVarInt();
        int energy = buf.readVarInt();
        int capacity = buf.readVarInt();
        int reading = buf.readVarInt();
        int bound = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 256);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            labels.add(buf.readUtf());
        }
        double[] values = new double[Math.min(buf.readVarInt(), 256)];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readDouble();
        }
        int[] ratings = buf.readVarIntArray(256);
        double[] bounds = new double[Math.min(buf.readVarInt(), 1024)];
        for (int i = 0; i < bounds.length; i++) {
            bounds[i] = buf.readDouble();
        }
        int m = Math.min(buf.readVarInt(), 4096);
        List<AreaAirControllerBlockEntity.Part> parts = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            ResourceLocation block = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            parts.add(new AreaAirControllerBlockEntity.Part(
                    block, pos, buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
        String pin = buf.readUtf();
        boolean breach = buf.readBoolean();
        boolean fire = buf.readBoolean();
        BlockPos central = buf.readBoolean() ? buf.readBlockPos() : null;
        return new ControllerSync(
                containerId,
                mode,
                preset,
                edited,
                status,
                energy,
                capacity,
                reading,
                bound,
                labels,
                values,
                ratings,
                bounds,
                parts,
                pin,
                breach,
                fire,
                central);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** On the client: hands the state to the open menu it is for. */
    public static void handle(ControllerSync sync, IPayloadContext context) {
        if (context.player().containerMenu instanceof ControllerMenu menu && menu.containerId == sync.containerId()) {
            menu.receive(sync);
        }
    }
}
