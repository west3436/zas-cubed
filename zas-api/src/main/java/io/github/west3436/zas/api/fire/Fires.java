package io.github.west3436.zas.api.fire;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Fire: where gas burns (API-055), starting a fire (API-056, API-058) and putting fires out
 * (API-057).
 *
 * <p><b>Hotspots.</b> A fire in a zone burns as hotspots, one per block: each is a vanilla fire
 * block in a zone or a {@code zas:gas_fire} block, and burns the zone's flammable gas (FIRE-011),
 * spreads to neighbouring cells (FIRE-016) and goes out when the gas won't burn (FIRE-017). A
 * vanilla fire outside any zone isn't a hotspot. A zone burns while it has a hotspot; it catching
 * fire and going out are events ({@link io.github.west3436.zas.api.event.FireIgnitedEvent}, {@link
 * io.github.west3436.zas.api.event.FireExtinguishedEvent}, API-031).
 *
 * <p><b>Threads:</b> the queries may be called from any thread and answer from the last applied
 * atmos tick (API-046). {@link #ignite} and {@link #extinguish} may be called only from the server
 * thread, and throw {@link IllegalStateException} otherwise (API-045).
 *
 * <p>Without ZAS³ installed, nothing burns as a hotspot: the queries find none, an ignition
 * completes at once with {@link Ignition#ABSENT} and {@link #extinguish} puts out nothing (D-17).
 */
public final class Fires {
    /** The most blocks {@link #extinguish} covers in one call: a 16 × 16 × 16 box. */
    public static final int MAX_EXTINGUISH_BLOCKS = 4096;

    private Fires() {}

    /**
     * The hotspots of a zone, in ascending order of {@link BlockPos#asLong} (API-055).
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param zone the zone
     * @return an immutable list, empty if the zone doesn't burn or doesn't exist
     */
    @AnyThread
    public static List<BlockPos> hotspots(Level level, ZoneId zone) {
        return ZasApi.provider().hotspots(Objects.requireNonNull(level, "level"), Objects.requireNonNull(zone, "zone"));
    }

    /**
     * Whether a zone burns: whether it has a hotspot (API-055).
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param zone the zone
     * @return true if it has a hotspot
     */
    @AnyThread
    public static boolean isBurning(Level level, ZoneId zone) {
        return ZasApi.provider()
                .isBurning(Objects.requireNonNull(level, "level"), Objects.requireNonNull(zone, "zone"));
    }

    /**
     * Whether a block position is a hotspot (API-055).
     *
     * <p>A new gas fire is a hotspot once an atmos tick has seen its block: false when the {@link
     * #ignite} future that placed it completes, true from the next atmos tick. The same goes for
     * {@link #hotspots} and {@link #isBurning}.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param pos the position
     * @return true if a fire burns gas there
     */
    @AnyThread
    public static boolean isHotspot(Level level, BlockPos pos) {
        return ZasApi.provider().isHotspot(Objects.requireNonNull(level, "level"), Objects.requireNonNull(pos, "pos"));
    }

    /**
     * Ignites the gas at a position or a neighbouring one, in the next atmos tick (API-056,
     * FIRE-014): if a zone there has gas that would burn, a fire starts at the position, or else
     * at the first of its six neighbours in that zone where a fire block can go. A fire already
     * burning at any of them is reported, not doubled. It never forms a zone.
     *
     * <p>When the future completes with {@link IgnitionOutcome#IGNITED}, the gas fire block is
     * already placed, but the fire isn't a hotspot yet: {@link #isHotspot}, {@link #hotspots} and
     * {@link #isBurning} see it from the next atmos tick, which is also when the zone's {@link
     * io.github.west3436.zas.api.event.FireIgnitedEvent} fires if it wasn't burning before.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param pos the ignition source's position
     * @param source what ignites, such as {@code mymod:igniter}
     * @return a future completing on the server thread, once the atmos tick that applied the
     *     ignition is applied, with what it found
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<Ignition> ignite(ServerLevel level, BlockPos pos, SourceId source) {
        ServerThreads.require(level, "Fires.ignite");
        return ZasApi.provider()
                .ignite(
                        level,
                        Objects.requireNonNull(pos, "pos").immutable(),
                        Objects.requireNonNull(source, "source"));
    }

    /**
     * Puts out every fire in a box, now (API-057): removes each {@code zas:gas_fire} and vanilla
     * fire block from {@code from} to {@code to}, both corners included, in loaded chunks. The
     * hotspots go with their blocks in the next atmos tick. A gas fire the next tick was about to
     * place in the box isn't placed. The zone's gas is unchanged, so a fire nearby can spread back,
     * and a zone hot enough relights by itself (FIRE-010).
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param from one corner
     * @param to the opposite corner
     * @return how many fire blocks were removed
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if the box holds more than {@link #MAX_EXTINGUISH_BLOCKS}
     *     blocks
     */
    @ServerThread
    public static int extinguish(ServerLevel level, BlockPos from, BlockPos to) {
        ServerThreads.require(level, "Fires.extinguish");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        long blocks = (long) (Math.abs(to.getX() - from.getX()) + 1)
                * (Math.abs(to.getY() - from.getY()) + 1)
                * (Math.abs(to.getZ() - from.getZ()) + 1);
        if (blocks > MAX_EXTINGUISH_BLOCKS) {
            throw new IllegalArgumentException(
                    "extinguish covers at most " + MAX_EXTINGUISH_BLOCKS + " blocks, got " + blocks);
        }
        return ZasApi.provider().extinguish(level, from.immutable(), to.immutable());
    }
}
