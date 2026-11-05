package io.github.west3436.zas.atmos.device;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * How hard the server last said each device near the local player is working (DATA-018). Sounds and
 * particles read it: {@link #level} for one device, {@link #forEach} for all of them. A device that
 * isn't listed is {@link DeviceActivity#IDLE}.
 *
 * <p>The table forgets everything when the level changes or the player leaves, and a device when the
 * server says it is idle or out of reach. It doesn't drop a device whose chunk left the client, because
 * the server wouldn't say it again if the chunk came back; whoever reads it checks the block is there.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handler loads on a
 * dedicated server too. Client thread only.
 */
public final class ClientDeviceActivity {
    /** The local player's. */
    public static final ClientDeviceActivity LOCAL = new ClientDeviceActivity();

    /** Takes each device in the table. */
    public interface Visitor {
        void visit(long pos, int level);
    }

    private final Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap();
    private Object level;

    /** A fresh table; tests make their own. */
    public ClientDeviceActivity() {}

    /**
     * Says which level the player is in. A new one, which is a new dimension or a new world, empties
     * the table.
     *
     * @param level the client's level, or null for none
     */
    public void enter(Object level) {
        if (level != this.level) {
            levels.clear();
            this.level = level;
        }
    }

    /** Takes what the server sent. */
    public void accept(DeviceActivityPayload payload) {
        for (DeviceActivityPayload.Change c : payload.changes()) {
            if (c.level() == DeviceActivity.IDLE) {
                levels.remove(c.pos());
            } else {
                levels.put(c.pos(), (byte) c.level());
            }
        }
    }

    /** How hard a device is working, {@link DeviceActivity#IDLE} if the server hasn't said. */
    public int level(BlockPos pos) {
        return levels.getOrDefault(pos.asLong(), (byte) DeviceActivity.IDLE);
    }

    /** Visits every device listed, none of them idle. The visitor mustn't change the table. */
    public void forEach(Visitor visitor) {
        for (Long2ByteMap.Entry e : levels.long2ByteEntrySet()) {
            visitor.visit(e.getLongKey(), e.getByteValue());
        }
    }

    /** How many devices are listed. */
    public int size() {
        return levels.size();
    }

    /** Forgets everything, as when leaving a server. */
    public void clear() {
        levels.clear();
        level = null;
    }

    /** On the client: the entry point named in the payload's registration. */
    public static void handle(DeviceActivityPayload payload, IPayloadContext context) {
        LOCAL.enter(context.player().level());
        LOCAL.accept(payload);
    }
}
