package io.github.west3436.zas.atmos.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Codecs for gas carried by items, such as a mined canister's contents (CONT-017). */
public final class GasCodecs {
    private record Raw(Map<ResourceLocation, Double> moles, double temperature) {}

    private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE)
                            .fieldOf("moles")
                            .forGetter(Raw::moles),
                    Codec.DOUBLE.fieldOf("temperature").forGetter(Raw::temperature))
            .apply(i, Raw::new));

    /** A parcel: moles by gas id, and the temperature in K. Negative or non-finite values fail to decode. */
    public static final Codec<GasParcel> PARCEL = RAW.comapFlatMap(
            raw -> {
                try {
                    return DataResult.success(new GasParcel(raw.moles(), raw.temperature()));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(e::getMessage);
                }
            },
            gas -> new Raw(gas.moles(), gas.temperature()));

    /** The same, over the network. */
    public static final StreamCodec<ByteBuf, GasParcel> PARCEL_STREAM = ByteBufCodecs.fromCodec(PARCEL);

    private GasCodecs() {}

    /** A gas's name: its translation, {@code gas.<namespace>.<path>}, or its id. */
    public static Component name(ResourceLocation gas) {
        return Component.translatableWithFallback("gas." + gas.getNamespace() + "." + gas.getPath(), gas.toString());
    }

    /**
     * A parcel's pressure in a volume.
     *
     * @param gas the parcel
     * @param volume the volume, L
     * @return the pressure, kPa
     */
    public static double pressure(GasParcel gas, double volume) {
        double n = 0;
        for (Map.Entry<ResourceLocation, Double> e : gas.moles().entrySet()) {
            n += e.getValue();
        }
        return n * GasMixture.GAS_CONSTANT * gas.temperature() / volume;
    }
}
