package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.gas.GasParcel;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Adding and removing gas at a point (API-012), with asynchronous results
 * (API-016, D-46).
 *
 * <p><b>Threads:</b> every method may be called only from the server thread,
 * and throws {@link IllegalStateException} otherwise (API-045). The returned
 * future completes on the server thread, once the atmos tick that applies the
 * change has been applied: at most one atmos tick later. Callbacks attached
 * with the non-async {@code then...} methods therefore run on the server
 * thread too.
 *
 * <p><b>Conservation:</b> gas added leaves the caller when the call is made;
 * subtract it from your own store right away. Gas removed is delivered only
 * when the future completes, in {@link MutationResult#moved()}.
 *
 * <p>The point is resolved as in {@link io.github.west3436.zas.api.atmosphere.Atmospheres}
 * (API-015). Without ZAS³ installed, every mutation completes at once with
 * {@link MutationResult#ABSENT}: nothing moves (D-17).
 */
public final class GasMutations {
    private GasMutations() {}

    /**
     * Adds gas at a point.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param point the point, resolved to a cell
     * @param gas the gas to add; it leaves the caller now
     * @param source who is adding it
     * @return a future completing on the server thread with how much was taken
     *     and where it went
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<MutationResult> add(ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
        ServerThreads.require(level, "GasMutations.add");
        return ZasApi.provider()
                .addGas(
                        level,
                        Objects.requireNonNull(point, "point"),
                        Objects.requireNonNull(gas, "gas"),
                        Objects.requireNonNull(source, "source"));
    }

    /**
     * Removes gas at a point.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param point the point, resolved to a cell
     * @param moles the moles of each gas wanted; less may be delivered
     * @param source who is removing it
     * @return a future completing on the server thread with the gas delivered
     *     and where it came from
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if an amount is negative or not finite
     */
    @ServerThread
    public static CompletableFuture<MutationResult> remove(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
        ServerThreads.require(level, "GasMutations.remove");
        return ZasApi.provider()
                .removeGas(
                        level,
                        Objects.requireNonNull(point, "point"),
                        GasParcel.validMoles(moles),
                        Objects.requireNonNull(source, "source"));
    }
}
