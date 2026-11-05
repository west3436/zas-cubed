package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How hard the devices near a player are working (DATA-018): what has changed since it was last told,
 * sent to that player alone at most once per atmos tick. Each change is 9 bytes, a block position and
 * a level. A message adds 1 byte of count, and 20 as a packet, for its id and its channel's name. It
 * carries no zone data (D-53).
 *
 * @param changes what changed; a change to {@link DeviceActivity#IDLE} means forget that device
 */
public record DeviceActivityPayload(List<Change> changes) implements CustomPacketPayload {
    /** The most changes in a message, which keeps its count to 1 byte; the rest wait for the next atmos tick. */
    public static final int MAX_CHANGES = 127;

    public static final Type<DeviceActivityPayload> TYPE = new Type<>(ZasAtmosMod.id("activity"));

    public static final StreamCodec<ByteBuf, DeviceActivityPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                VarInt.write(buf, p.changes.size());
                for (Change c : p.changes) {
                    buf.writeLong(c.pos());
                    buf.writeByte(c.level());
                }
            },
            buf -> {
                int n = VarInt.read(buf);
                if (n < 0 || n > MAX_CHANGES) {
                    throw new IllegalArgumentException("bad change count: " + n);
                }
                List<Change> changes = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    changes.add(new Change(buf.readLong(), buf.readByte()));
                }
                return new DeviceActivityPayload(changes);
            });

    /**
     * One device's level.
     *
     * @param pos its block position, as {@code BlockPos.asLong}
     * @param level its {@link DeviceActivity} level
     */
    public record Change(long pos, int level) {}

    public DeviceActivityPayload {
        changes = List.copyOf(changes);
        if (changes.size() > MAX_CHANGES) {
            throw new IllegalArgumentException("too many changes: " + changes.size());
        }
    }

    @Override
    public Type<DeviceActivityPayload> type() {
        return TYPE;
    }
}
