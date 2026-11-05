package io.github.west3436.zas.airflow;

import io.github.west3436.zas.ZasMod;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The airflow a client should show its player (CLIENT-003, DATA-015): every active edge in range,
 * replacing whatever it was last sent. An empty list ends them all. See {@link VisibleFlows} for
 * when it is sent, and {@link HeldFlows} for how the client holds it.
 *
 * <p>Openings are written relative to the first, so each takes 4 bytes: they are all within about
 * 66 blocks of each other, which fits a byte per axis.
 *
 * @param flows the edges in range, at most {@link VisibleFlows#MAX_FLOWS}
 */
public record AirflowEffectsPayload(List<VisibleFlow> flows) implements CustomPacketPayload {
    public static final Type<AirflowEffectsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "airflow_effects"));

    public AirflowEffectsPayload {
        flows = List.copyOf(flows);
        if (flows.size() > VisibleFlows.MAX_FLOWS) {
            throw new IllegalArgumentException("too many flows: " + flows.size());
        }
    }

    @Override
    public Type<AirflowEffectsPayload> type() {
        return TYPE;
    }

    public static final StreamCodec<ByteBuf, AirflowEffectsPayload> STREAM_CODEC =
            StreamCodec.of(AirflowEffectsPayload::write, AirflowEffectsPayload::read);

    private static void write(ByteBuf buf, AirflowEffectsPayload p) {
        VarInt.write(buf, p.flows().size());
        if (p.flows().isEmpty()) {
            return;
        }
        VisibleOpening origin = p.flows().get(0).openings().get(0);
        buf.writeLong(BlockPos.asLong(origin.x(), origin.y(), origin.z()));
        for (VisibleFlow flow : p.flows()) {
            VarInt.write(buf, flow.kpa());
            VarInt.write(buf, flow.openings().size());
            for (VisibleOpening o : flow.openings()) {
                VarInt.write(buf, zigzag(o.x() - origin.x()));
                VarInt.write(buf, zigzag(o.y() - origin.y()));
                VarInt.write(buf, zigzag(o.z() - origin.z()));
                buf.writeByte(o.face());
            }
        }
    }

    private static AirflowEffectsPayload read(ByteBuf buf) {
        int count = VarInt.read(buf);
        if (count < 0 || count > VisibleFlows.MAX_FLOWS) {
            throw new IllegalArgumentException("bad flow count: " + count);
        }
        if (count == 0) {
            return new AirflowEffectsPayload(List.of());
        }
        long origin = buf.readLong();
        int ox = BlockPos.getX(origin);
        int oy = BlockPos.getY(origin);
        int oz = BlockPos.getZ(origin);
        List<VisibleFlow> flows = new ArrayList<>(count);
        for (int f = 0; f < count; f++) {
            int kpa = VarInt.read(buf);
            int n = VarInt.read(buf);
            if (n < 0 || n > VisibleFlows.MAX_OPENINGS_PER_FLOW) {
                throw new IllegalArgumentException("bad opening count: " + n);
            }
            List<VisibleOpening> openings = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int x = ox + unzigzag(VarInt.read(buf));
                int y = oy + unzigzag(VarInt.read(buf));
                int z = oz + unzigzag(VarInt.read(buf));
                openings.add(new VisibleOpening(x, y, z, buf.readByte()));
            }
            flows.add(new VisibleFlow(kpa, openings));
        }
        return new AirflowEffectsPayload(flows);
    }

    private static int zigzag(int v) {
        return (v << 1) ^ (v >> 31);
    }

    private static int unzigzag(int n) {
        return (n >>> 1) ^ -(n & 1);
    }
}
