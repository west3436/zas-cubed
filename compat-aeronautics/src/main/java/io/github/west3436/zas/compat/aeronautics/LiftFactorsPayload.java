package io.github.west3436.zas.compat.aeronautics;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Every dimension's lift factor, sent to clients so their Sable reads the server's pressure
 * (COMPAT-081): to a player as it joins, and to everyone whenever a factor changes. It holds every
 * dimension the server has, and replaces what the client held. A dimension it leaves out has a
 * factor of 1.
 *
 * @param factors the factor by dimension id, each 0 or more and finite
 */
public record LiftFactorsPayload(Map<ResourceLocation, Double> factors) implements CustomPacketPayload {
    /** Its type, in zas's namespace, as the adapter ships in the zas jar. */
    public static final Type<LiftFactorsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zas", "aeronautics_lift"));

    /** The most dimensions one payload carries; more is no server's. */
    public static final int MAX_DIMENSIONS = 4096;

    /**
     * Checks and copies the map.
     *
     * @throws IllegalArgumentException if a factor is negative or not finite, or there are too many
     */
    public LiftFactorsPayload {
        TreeMap<ResourceLocation, Double> copy = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : factors.entrySet()) {
            double f = e.getValue();
            if (!(f >= 0) || !Double.isFinite(f)) {
                throw new IllegalArgumentException("lift factor of " + e.getKey() + " not finite and >= 0: " + f);
            }
            copy.put(e.getKey(), f);
        }
        if (copy.size() > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("too many dimensions: " + copy.size());
        }
        factors = Map.copyOf(copy);
    }

    @Override
    public Type<LiftFactorsPayload> type() {
        return TYPE;
    }

    /** The wire form: a count, then each dimension's id and factor. */
    public static final StreamCodec<ByteBuf, LiftFactorsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                VarInt.write(buf, p.factors().size());
                for (Map.Entry<ResourceLocation, Double> e : new TreeMap<>(p.factors()).entrySet()) {
                    ResourceLocation.STREAM_CODEC.encode(buf, e.getKey());
                    buf.writeDouble(e.getValue());
                }
            },
            buf -> {
                int n = VarInt.read(buf);
                if (n < 0 || n > MAX_DIMENSIONS) {
                    throw new IllegalArgumentException("too many dimensions: " + n);
                }
                Map<ResourceLocation, Double> factors = new TreeMap<>();
                for (int i = 0; i < n; i++) {
                    factors.put(ResourceLocation.STREAM_CODEC.decode(buf), buf.readDouble());
                }
                return new LiftFactorsPayload(factors);
            });
}
