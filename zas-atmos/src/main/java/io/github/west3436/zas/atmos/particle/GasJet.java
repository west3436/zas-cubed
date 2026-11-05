package io.github.west3436.zas.atmos.particle;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One burst of gas particles, which the server tells nearby clients about (CLIENT-017, CLIENT-018).
 * It carries a place, a direction and one number, and nothing about any zone (D-53). The client works
 * out the particles from them, and keeps to its own limits ({@link JetEmitters}).
 *
 * @param kind what it is
 * @param x where it comes from
 * @param y as {@code x}
 * @param z as {@code x}
 * @param dx its direction, a unit vector; all zero means every direction
 * @param dy as {@code dx}
 * @param dz as {@code dx}
 * @param amount its amount, as {@code kind} says
 */
public record GasJet(GasJetKind kind, double x, double y, double z, float dx, float dy, float dz, float amount)
        implements CustomPacketPayload {
    public static final Type<GasJet> TYPE = new Type<>(ZasAtmosMod.id("gas_jet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasJet> CODEC =
            StreamCodec.of((buf, jet) -> jet.write(buf), GasJet::read);

    /**
     * Takes each jet on a client; the client's entry point sets it, so that this class, which a
     * dedicated server loads too, names no client class. Null elsewhere.
     */
    public static Consumer<GasJet> receiver;

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(dx);
        buf.writeFloat(dy);
        buf.writeFloat(dz);
        buf.writeFloat(amount);
    }

    private static GasJet read(RegistryFriendlyByteBuf buf) {
        return new GasJet(
                buf.readEnum(GasJetKind.class),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** On the client: hands the jet to the particles. */
    public static void handle(GasJet jet, IPayloadContext context) {
        Consumer<GasJet> to = receiver;
        if (to != null) {
            to.accept(jet);
        }
    }
}
