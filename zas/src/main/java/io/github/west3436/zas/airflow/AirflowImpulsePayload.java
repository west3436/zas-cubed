package io.github.west3436.zas.airflow;

import io.github.west3436.zas.ZasMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A one-off decompression impulse for a player's client (FLOW-009): sent when airflow first takes
 * hold of the player, it brings the player's speed along the flow up to the flow's at once, so the
 * onset of a breach is felt in full even before the continuous push ({@link AirflowSyncPayload})
 * builds it up.
 *
 * @param vx the flow's velocity at the player, blocks per game tick
 * @param vy see {@code vx}
 * @param vz see {@code vx}
 */
public record AirflowImpulsePayload(double vx, double vy, double vz) implements CustomPacketPayload {
    public static final Type<AirflowImpulsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "airflow_impulse"));

    /** The most speed an impulse can carry, blocks per game tick; larger ones are scaled down. */
    public static final double MAX_SPEED = 10;

    public AirflowImpulsePayload {
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) {
            throw new IllegalArgumentException("impulse not finite");
        }
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len > MAX_SPEED) {
            vx = vx / len * MAX_SPEED;
            vy = vy / len * MAX_SPEED;
            vz = vz / len * MAX_SPEED;
        }
    }

    @Override
    public Type<AirflowImpulsePayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, AirflowImpulsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.vx());
                buf.writeDouble(p.vy());
                buf.writeDouble(p.vz());
            },
            buf -> new AirflowImpulsePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));
}
