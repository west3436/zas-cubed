package io.github.west3436.zas.airflow;

import io.github.west3436.zas.ZasMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells the players tracking an entity, and the entity itself if it is a player, that airflow has
 * flung it off its feet or that it has come down (CLIENT-027). Sent only when that changes, and to
 * a player that starts tracking an entity already flung.
 *
 * @param entityId the entity's network id
 * @param flung whether it is flung now
 */
public record TumblePayload(int entityId, boolean flung) implements CustomPacketPayload {
    public static final Type<TumblePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "tumble"));

    public static final StreamCodec<ByteBuf, TumblePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TumblePayload::entityId,
            ByteBufCodecs.BOOL,
            TumblePayload::flung,
            TumblePayload::new);

    @Override
    public Type<TumblePayload> type() {
        return TYPE;
    }
}
