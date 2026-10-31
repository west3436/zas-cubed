package io.github.west3436.zas.gas;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The server's gas definitions, sent to each client on join and on reload (CORE-014). */
public record GasDefinitionsPayload(List<GasDefinition> definitions) implements CustomPacketPayload {
    public static final Type<GasDefinitionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "gas_definitions"));

    public static final StreamCodec<ByteBuf, GasDefinitionsPayload> STREAM_CODEC = GasDefinitionCodecs.STREAM
            .apply(ByteBufCodecs.list())
            .map(GasDefinitionsPayload::new, GasDefinitionsPayload::definitions);

    public GasDefinitionsPayload {
        definitions = List.copyOf(definitions);
    }

    public static GasDefinitionsPayload of(GasRegistry registry) {
        return new GasDefinitionsPayload(registry.definitions());
    }

    @Override
    public Type<GasDefinitionsPayload> type() {
        return TYPE;
    }
}
