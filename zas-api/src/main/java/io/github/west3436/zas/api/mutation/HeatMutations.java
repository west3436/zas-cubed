package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Adding and removing heat at a point (API-013), with asynchronous results as for gas (API-016,
 * D-46).
 *
 * <p><b>Threads:</b> every method may be called only from the server thread, and throws {@link
 * IllegalStateException} otherwise (API-045). The returned future completes on the server thread,
 * once the atmos tick that applies the change has been applied.
 *
 * <p><b>Where it goes.</b> The point is resolved as in {@link
 * io.github.west3436.zas.api.atmosphere.Atmospheres} (API-015).
 * <ul>
 *   <li>In a zone, heat added raises its temperature by ΔE/C, where C is its gas's heat capacity;
 *       heat removed lowers it, never below the server's temperature floor, so less may be
 *       delivered than asked.
 *   <li>At an enclosed, unzoned point, the call forms a zone first, as a gas mutation does
 *       (CORE-055).
 *   <li>At an open point, the ambient takes or supplies it all, and nothing changes (HEAT-010).
 *   <li>At a paused zone or an unloaded region, added heat is voided and removed heat is supplied
 *       without changing anything (GEO-036, GEO-038).
 * </ul>
 *
 * <p><b>Conservation:</b> heat added leaves the caller when the call is made; heat removed is
 * delivered only when the future completes, in {@link HeatResult#joules()}. Heat isn't saved: a
 * mutation still queued when the server stops is dropped, and its future completes as {@link
 * MutationOutcome#PAUSED} with nothing moved.
 *
 * <p>Without ZAS³ installed, every mutation completes at once with {@link HeatResult#ABSENT}:
 * nothing moves (D-17).
 */
public final class HeatMutations {
    private HeatMutations() {}

    /**
     * Adds heat at a point.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param point the point, resolved to a cell
     * @param joules the energy to add, in J; it leaves the caller now
     * @param source who is adding it
     * @return a future completing on the server thread with how much was taken and where it went
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if the energy is negative or not finite
     */
    @ServerThread
    public static CompletableFuture<HeatResult> add(ServerLevel level, Vec3 point, double joules, SourceId source) {
        ServerThreads.require(level, "HeatMutations.add");
        return ZasApi.provider()
                .addHeat(
                        level,
                        Objects.requireNonNull(point, "point"),
                        valid(joules),
                        Objects.requireNonNull(source, "source"));
    }

    /**
     * Removes heat at a point.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param point the point, resolved to a cell
     * @param joules the energy wanted, in J; less may be delivered
     * @param source who is removing it
     * @return a future completing on the server thread with the heat delivered and where it came
     *     from
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if the energy is negative or not finite
     */
    @ServerThread
    public static CompletableFuture<HeatResult> remove(ServerLevel level, Vec3 point, double joules, SourceId source) {
        ServerThreads.require(level, "HeatMutations.remove");
        return ZasApi.provider()
                .removeHeat(
                        level,
                        Objects.requireNonNull(point, "point"),
                        valid(joules),
                        Objects.requireNonNull(source, "source"));
    }

    private static double valid(double joules) {
        if (!(joules >= 0) || !Double.isFinite(joules)) {
            throw new IllegalArgumentException("joules must be finite and >= 0: " + joules);
        }
        return joules;
    }
}
