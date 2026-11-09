package io.github.west3436.zas.reaction;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's compiled reactions, sent to each client on join and on reload, after the gas
 * definitions they name (COMPAT-060). A client runs none of them: a recipe viewer shows them.
 *
 * @param definitions every reaction the server runs, in the order it runs them
 */
public record ReactionDefinitionsPayload(List<ReactionDefinition> definitions) implements CustomPacketPayload {
    public static final Type<ReactionDefinitionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "reaction_definitions"));

    public static final StreamCodec<ByteBuf, ReactionDefinitionsPayload> STREAM_CODEC = StreamCodec.composite(
            ReactionCodecs.STREAM.apply(ByteBufCodecs.list()),
            ReactionDefinitionsPayload::definitions,
            ReactionDefinitionsPayload::new);

    public ReactionDefinitionsPayload {
        definitions = List.copyOf(definitions);
    }

    @Override
    public Type<ReactionDefinitionsPayload> type() {
        return TYPE;
    }
}
