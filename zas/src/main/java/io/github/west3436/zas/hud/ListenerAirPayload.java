package io.github.west3436.zas.hud;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.sound.ThinAir;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The pressure around a player, for how much of this mod's sounds it hears (CLIENT-016, DATA-019),
 * sent to that player only. It is one byte: whole kPa, capped where {@link ThinAir} stops changing.
 *
 * @param kPa the pressure, 0 to {@value ThinAir#FULL_KPA}
 */
public record ListenerAirPayload(int kPa) implements CustomPacketPayload {
    public static final Type<ListenerAirPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "listener_air"));

    public static final StreamCodec<ByteBuf, ListenerAirPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeByte(p.kPa), buf -> new ListenerAirPayload(buf.readUnsignedByte()));

    /** Clamps the pressure to its range. */
    public ListenerAirPayload {
        kPa = Math.max(0, Math.min(ThinAir.FULL_KPA, kPa));
    }

    @Override
    public Type<ListenerAirPayload> type() {
        return TYPE;
    }
}
