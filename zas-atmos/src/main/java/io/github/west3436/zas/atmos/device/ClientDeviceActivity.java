package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.api.relocation.Relocations;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    /** Takes a device a listener hears, and where. */
    public interface Heard {
        /**
         * @param pos the device's block, where the level keeps it; the same object each call
         * @param level how hard it is working
         * @param at the middle of its block, where the world shows it
         */
        void visit(BlockPos pos, int level, Vec3 at);
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

    /**
     * Visits every device listed whose chunk the level has and whose middle, where the world shows
     * it, is within {@code range} of a listener: on a Sable ship, where the ship is now, not in its
     * plot (API-068). Called each tick, so what plays there follows the ship. The visitor mustn't
     * change the table.
     *
     * @param level the level the devices are in, on its own thread
     * @param ear where the listener is, as the world shows it
     * @param range how far away a device is heard, blocks
     */
    public void forEachHeard(Level level, Vec3 ear, double range, Heard visitor) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Long2ByteMap.Entry e : levels.long2ByteEntrySet()) {
            at.set(e.getLongKey());
            if (!level.getChunkSource()
                    .hasChunk(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()))) {
                continue;
            }
            Vec3 shown = Relocations.shownAt(level, Vec3.atCenterOf(at));
            if (ear.distanceToSqr(shown) <= range * range) {
                visitor.visit(at, e.getByteValue(), shown);
            }
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
