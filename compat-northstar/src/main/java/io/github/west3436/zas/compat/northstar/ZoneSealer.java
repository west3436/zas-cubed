package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * ZAS³'s zones as one of Northstar's oxygen sealers, for a level (SPACE-011). Everything Northstar
 * asks of its oxygen, its torches, furnaces, campfires, engines and oxygen detector, is answered by
 * ZAS³: a point or a block is sealed when a zone there holds enough oxygen to breathe. The answer is
 * a lookup in the zones ZAS³ last published, so it costs no more than Northstar's own sealers do.
 *
 * <p>What Northstar burns or breathes there is taken from the zone it was found in: {@link
 * #drainOxygen} is in Northstar's mB of oxygen, which ZAS³ counts at its {@linkplain
 * Millibuckets#molesPerMillibucket() mB per mole}. A drain is gathered by block and taken once a
 * second, since Northstar drains a little every tick.
 */
final class ZoneSealer implements NorthstarOxygen.Provider {
    /** Who takes the oxygen, in ZAS³'s logs. */
    static final SourceId SOURCE = SourceId.of("zas", "northstar_oxygen");

    /** How often what was drained is taken, in ticks. */
    static final int FLUSH_TICKS = 20;

    private final ServerLevel level;

    /** The block where Northstar last found oxygen, which its next drain is taken from. */
    private final ThreadLocal<BlockPos> lastFound = new ThreadLocal<>();

    /** Oxygen drained and not yet taken, in moles, by block. Server thread. */
    private final Long2DoubleMap owed = new Long2DoubleOpenHashMap();

    ZoneSealer(ServerLevel level) {
        this.level = level;
    }

    ServerLevel level() {
        return level;
    }

    @Override
    public boolean isSealed(Vec3 pos) {
        AtmosphereSample sample = Zones.at(level, pos, Zones::breathable);
        if (sample == null) {
            return false;
        }
        lastFound.set(BlockPos.containing(pos));
        return true;
    }

    @Override
    public boolean isSealed(Vec3i pos) {
        Zones.Found found = Zones.at(level, pos, Zones::breathable);
        if (found == null) {
            return false;
        }
        lastFound.set(found.pos());
        return true;
    }

    /**
     * Owes the zone Northstar last found oxygen in this much of it. Northstar drains right after it
     * asks where the oxygen is, on the same thread.
     *
     * @param oxygen mB of oxygen
     */
    @Override
    public void drainOxygen(float oxygen) {
        BlockPos at = lastFound.get();
        if (at == null || !(oxygen > 0) || !level.getServer().isSameThread()) {
            return;
        }
        owed.mergeDouble(at.asLong(), oxygen * Millibuckets.molesPerMillibucket(), Double::sum);
    }

    /** Takes what was drained, once every {@link #FLUSH_TICKS}. Server thread, each server tick. */
    void tick() {
        if (owed.isEmpty() || level.getGameTime() % FLUSH_TICKS != 0) {
            return;
        }
        flush();
    }

    /** Takes what was drained now. Server thread. */
    void flush() {
        for (Long2DoubleMap.Entry e : owed.long2DoubleEntrySet()) {
            Vec3 point = Vec3.atCenterOf(BlockPos.of(e.getLongKey()));
            GasMutations.remove(level, point, Map.of(StandardAir.OXYGEN, e.getDoubleValue()), SOURCE);
        }
        owed.clear();
    }

    /** Oxygen drained and not yet taken, in moles. For tests. */
    double owed() {
        return owed.values().doubleStream().sum();
    }
}
