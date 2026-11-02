package io.github.west3436.zas.atmos.device;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * A device's port onto a zone: the one way the content mod changes zone gas (CONT-002, CONT-071),
 * with at most one change outstanding (CONT-072).
 *
 * <p>A change is an addition, a removal, or both at once, all decided on one snapshot. The port is
 * {@linkplain #busy() busy} until every mutation of the change has completed, and refuses a new
 * change until then, so a decision made on a stale snapshot never moves gas twice. A device whose
 * port is busy isn't {@linkplain AtmosDevice#readyToStep() ready to step}.
 *
 * <p>The API's rules hold (API-016, D-46): added gas leaves the caller at once, and removed gas
 * arrives when its future completes, on the server thread. If an addition takes less than it was
 * offered, as when ZAS³ is absent and nothing moves, the callback gets the result and should take
 * back the rest ({@link #untaken}). Server thread only.
 */
public final class ZonePort {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The mutations a port issues: the API's, or a test double in unit tests. */
    public interface Mutations {
        CompletableFuture<MutationResult> add(ServerLevel level, Vec3 point, GasParcel gas, SourceId source);

        CompletableFuture<MutationResult> remove(
                ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source);

        /** The public API's {@link GasMutations}. */
        Mutations API = new Mutations() {
            @Override
            public CompletableFuture<MutationResult> add(
                    ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
                return GasMutations.add(level, point, gas, source);
            }

            @Override
            public CompletableFuture<MutationResult> remove(
                    ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
                return GasMutations.remove(level, point, moles, source);
            }
        };
    }

    private final SourceId source;
    private final Mutations mutations;
    private int outstanding;

    /**
     * A port that uses the public API.
     *
     * @param source who the port's changes are made by, for the conservation ledger
     */
    public ZonePort(SourceId source) {
        this(source, Mutations.API);
    }

    /**
     * @param source who the port's changes are made by
     * @param mutations the mutations to issue
     */
    public ZonePort(SourceId source, Mutations mutations) {
        this.source = source;
        this.mutations = mutations;
    }

    /** Whether a change is still outstanding. */
    public boolean busy() {
        return outstanding > 0;
    }

    /**
     * Adds gas at a point. The gas leaves the caller now.
     *
     * @param level the level
     * @param point the point
     * @param gas the gas to add
     * @param done called with the result once it completes, on the server thread
     * @return false, doing nothing, if the port is busy
     */
    public boolean add(ServerLevel level, Vec3 point, GasParcel gas, Consumer<MutationResult> done) {
        return change(level, point, gas, null, done, null);
    }

    /**
     * Removes gas at a point. The gas arrives with the result.
     *
     * @param level the level
     * @param point the point
     * @param moles the moles wanted of each gas
     * @param done called with the result, which holds the gas delivered, on the server thread
     * @return false, doing nothing, if the port is busy
     */
    public boolean remove(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, Consumer<MutationResult> done) {
        return change(level, point, null, moles, null, done);
    }

    /**
     * Adds some gases and removes others at a point, as one change decided on one snapshot. The
     * port stays busy until both complete.
     *
     * @param level the level
     * @param point the point
     * @param gas the gas to add; it leaves the caller now
     * @param moles the moles wanted of each gas to remove
     * @param added called with the addition's result
     * @param removed called with the removal's result, which holds the gas delivered
     * @return false, doing nothing, if the port is busy
     */
    public boolean exchange(
            ServerLevel level,
            Vec3 point,
            GasParcel gas,
            Map<ResourceLocation, Double> moles,
            Consumer<MutationResult> added,
            Consumer<MutationResult> removed) {
        return change(level, point, gas, moles, added, removed);
    }

    private boolean change(
            ServerLevel level,
            Vec3 point,
            GasParcel gas,
            Map<ResourceLocation, Double> moles,
            Consumer<MutationResult> added,
            Consumer<MutationResult> removed) {
        if (busy()) {
            return false;
        }
        if (gas != null) {
            track(mutations.add(level, point, gas, source), added);
        }
        if (moles != null) {
            track(mutations.remove(level, point, moles, source), removed);
        }
        return true;
    }

    private void track(CompletableFuture<MutationResult> future, Consumer<MutationResult> done) {
        outstanding++;
        future.whenComplete((result, failure) -> {
            outstanding--;
            if (failure != null) {
                LOGGER.warn("A zone mutation by {} failed", source, failure);
            } else if (done != null) {
                done.accept(result);
            }
        });
    }

    /**
     * The part of an addition the zone didn't take: what was offered less what moved, per gas.
     * Empty when the zone took everything.
     *
     * @param offered the gas offered
     * @param result the addition's result
     * @return the moles not taken, by gas, with nothing below zero
     */
    public static Map<ResourceLocation, Double> untaken(GasParcel offered, MutationResult result) {
        Map<ResourceLocation, Double> rest = new java.util.TreeMap<>();
        offered.moles().forEach((gas, n) -> {
            double left = n - result.moved().moles(gas);
            if (left > 0) {
                rest.put(gas, left);
            }
        });
        return rest;
    }
}
