package io.github.west3436.zas.compat.cosmonautics;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Whether ZAS³ lets the local player breathe where Cosmonautics says it can't (SPACE-012), from
 * the server to that player only, when it changes. The client's breathing hook then leaves the air
 * bar as the server does, so the bar doesn't drain between the server's updates.
 *
 * @param grants true while ZAS³'s zone or internals give the player a breath
 */
public record ZoneBreathPayload(boolean grants) implements CustomPacketPayload {
    public static final Type<ZoneBreathPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zas", "cosmonautics_breath"));

    public static final StreamCodec<ByteBuf, ZoneBreathPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ZoneBreathPayload::new, ZoneBreathPayload::grants);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
