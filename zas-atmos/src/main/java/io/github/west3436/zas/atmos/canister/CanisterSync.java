package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What a canister holds, for the player viewing its GUI (CONT-074). It only goes to the client.
 *
 * @param containerId the menu it is for
 * @param moles the moles of each gas
 */
public record CanisterSync(int containerId, Map<ResourceLocation, Double> moles) implements CustomPacketPayload {
    public static final Type<CanisterSync> TYPE = new Type<>(ZasAtmosMod.id("canister_sync"));

    private static final int MOST_GASES = 256;

    public static final StreamCodec<ByteBuf, CanisterSync> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CanisterSync::containerId,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.DOUBLE, MOST_GASES),
            sync -> new HashMap<>(sync.moles()),
            CanisterSync::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** On the client: hands the contents to the open menu it is for. */
    public static void handle(CanisterSync sync, IPayloadContext context) {
        if (context.player().containerMenu instanceof CanisterMenu menu && menu.containerId == sync.containerId()) {
            menu.receive(sync.moles());
        }
    }
}
