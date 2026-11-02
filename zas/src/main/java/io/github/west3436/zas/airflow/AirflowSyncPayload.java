package io.github.west3436.zas.airflow;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The airflow near a player, sent to that player's client once per atmos tick while there is any,
 * so the client pushes its own player with the same {@link FlowField#push} the server uses for
 * other entities (FLOW-017). A payload with no openings ends it.
 *
 * <p>The openings are those of edges beside the player's zone when it was sent; the client treats
 * them all as beside the player. Their zones aren't sent.
 *
 * @param dimension the dimension they are in; the client ignores them in any other
 * @param settings the force model
 * @param threshold the least difference that moves this player, kPa
 * @param resistance the player's airflow resistance, 0 to 1 (FLOW-012), from its worn pieces when
 *     sent; the client can't work it out, as protection data is only on the server
 * @param validTicks game ticks the client keeps them if no newer payload comes
 * @param openings the openings within reach of the player
 */
public record AirflowSyncPayload(
        ResourceLocation dimension,
        FlowSettings settings,
        double threshold,
        double resistance,
        int validTicks,
        List<FlowField.Opening> openings)
        implements CustomPacketPayload {

    public static final Type<AirflowSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "airflow"));

    /** The most openings one payload carries. */
    public static final int MAX_OPENINGS = 512;

    public AirflowSyncPayload {
        if (!(resistance >= 0 && resistance <= 1)) {
            throw new IllegalArgumentException("resistance not in 0 to 1: " + resistance);
        }
        openings = List.copyOf(openings);
        if (openings.size() > MAX_OPENINGS) {
            throw new IllegalArgumentException("too many openings: " + openings.size());
        }
    }

    /** The payload that ends airflow on the client. */
    public static AirflowSyncPayload none(ResourceLocation dimension) {
        return new AirflowSyncPayload(dimension, FlowSettings.DEFAULT, Double.POSITIVE_INFINITY, 0, 0, List.of());
    }

    @Override
    public Type<AirflowSyncPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, AirflowSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, p.dimension());
                FlowSettings s = p.settings();
                buf.writeDouble(s.range());
                buf.writeDouble(s.speedAtOneAtm());
                buf.writeDouble(s.maxSpeed());
                buf.writeDouble(s.response());
                buf.writeDouble(p.threshold());
                buf.writeDouble(p.resistance());
                VarInt.write(buf, p.validTicks());
                VarInt.write(buf, p.openings().size());
                for (FlowField.Opening o : p.openings()) {
                    buf.writeInt(o.hx());
                    buf.writeInt(o.hy());
                    buf.writeInt(o.hz());
                    buf.writeByte(o.lx() - o.hx());
                    buf.writeByte(o.ly() - o.hy());
                    buf.writeByte(o.lz() - o.hz());
                    buf.writeFloat((float) o.difference());
                }
            },
            buf -> {
                ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buf);
                FlowSettings settings =
                        new FlowSettings(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
                double threshold = buf.readDouble();
                double resistance = buf.readDouble();
                int valid = VarInt.read(buf);
                int n = VarInt.read(buf);
                if (n < 0 || n > MAX_OPENINGS) {
                    throw new IllegalArgumentException("bad opening count: " + n);
                }
                List<FlowField.Opening> openings = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int hx = buf.readInt();
                    int hy = buf.readInt();
                    int hz = buf.readInt();
                    int lx = hx + buf.readByte();
                    int ly = hy + buf.readByte();
                    int lz = hz + buf.readByte();
                    double difference = buf.readFloat();
                    openings.add(new FlowField.Opening(hx, hy, hz, lx, ly, lz, difference, 0, 0));
                }
                return new AirflowSyncPayload(dimension, settings, threshold, resistance, valid, openings);
            });
}
