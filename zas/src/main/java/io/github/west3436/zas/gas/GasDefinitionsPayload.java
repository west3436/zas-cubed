package io.github.west3436.zas.gas;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's gas definitions and gas tags, sent to each client on join and on reload (CORE-014,
 * CORE-012).
 *
 * @param definitions every gas definition
 * @param tags each gas tag's id and the ids of its gases
 */
public record GasDefinitionsPayload(List<GasDefinition> definitions, Map<String, List<String>> tags)
        implements CustomPacketPayload {
    public static final Type<GasDefinitionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "gas_definitions"));

    private static final StreamCodec<ByteBuf, Map<String, List<String>>> TAGS = ByteBufCodecs.map(
            size -> new TreeMap<>(), ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()));

    public static final StreamCodec<ByteBuf, GasDefinitionsPayload> STREAM_CODEC = StreamCodec.composite(
            GasDefinitionCodecs.STREAM.apply(ByteBufCodecs.list()),
            GasDefinitionsPayload::definitions,
            TAGS,
            GasDefinitionsPayload::tags,
            GasDefinitionsPayload::new);

    public GasDefinitionsPayload {
        definitions = List.copyOf(definitions);
        tags = Map.copyOf(tags);
    }

    public static GasDefinitionsPayload of(GasRegistry registry) {
        return new GasDefinitionsPayload(registry.definitions(), registry.tags());
    }

    @Override
    public Type<GasDefinitionsPayload> type() {
        return TYPE;
    }
}
