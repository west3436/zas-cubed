package io.github.west3436.zas.provider;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.ZasApiProvider;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * ZAS³'s implementation of the API (architecture §6.2).
 *
 * <p>TODO(#23): this is a stand-in until the atmos scheduler exists. Every
 * point answers as its dimension's ambient ({@link Ambients}, ENV-001), no
 * zone exists, and mutations resolve against the ambient: added gas is lost
 * to the open air and removed gas is supplied without depletion, at the end of the next server tick. The
 * scheduler replaces {@link #sample}, {@link #zone} and the mutation queue
 * with the published snapshot and its own queued operations; the threading
 * rules stay the same.
 */
public final class ZasApiProviderImpl implements ZasApiProvider {
    /** One cell of each ambient seen, by value; ambients are few. */
    private final Map<Ambient, AtmosphereSample> ambientSamples = new ConcurrentHashMap<>();

    /** Mutations waiting for the next tick. Touched only on the server thread. */
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();

    private record Pending(int queuedAt, CompletableFuture<MutationResult> future, Supplier<MutationResult> result) {}

    private ZasApiProviderImpl() {}

    /**
     * Installs the provider into the API and hooks it to server ticks. Called
     * once, from the mod constructor.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void install(IEventBus gameBus) {
        ZasApiProviderImpl impl = new ZasApiProviderImpl();
        ZasApi.install(impl);
        gameBus.addListener(impl::onServerTick);
        gameBus.addListener(impl::onServerStopping);
    }

    @Override
    public boolean isReal() {
        return true;
    }

    // TODO(#23): read the published snapshot; resolve the point to a cell (API-015).
    @Override
    public AtmosphereSample sample(Level level, Vec3 point) {
        return ambientSample(level);
    }

    // TODO(#23): read the published snapshot; use the position's largest cell (API-015).
    @Override
    public AtmosphereSample sample(Level level, BlockPos pos) {
        return ambientSample(level);
    }

    // TODO(#23): look the zone up in the published snapshot.
    @Override
    public Optional<ZoneInfo> zone(Level level, ZoneId id) {
        return Optional.empty();
    }

    // TODO(#23): queue the operation for the next atmos tick instead.
    @Override
    public CompletableFuture<MutationResult> addGas(ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
        // The open air takes everything offered.
        return queue(level, () -> new MutationResult(MutationOutcome.AMBIENT, gas));
    }

    // TODO(#23): queue the operation for the next atmos tick instead.
    @Override
    public CompletableFuture<MutationResult> removeGas(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
        // The ambient supplies any gas it contains, without depleting.
        return queue(level, () -> {
            GasMixture ambient = ambientSample(level).mixture();
            Map<ResourceLocation, Double> supplied = new HashMap<>();
            moles.forEach((gas, n) -> {
                if (ambient.moles(gas) > 0) {
                    supplied.put(gas, n);
                }
            });
            return new MutationResult(MutationOutcome.AMBIENT, new GasParcel(supplied, ambient.temperature()));
        });
    }

    /** One cell of the level's dimension's ambient. Excluded dimensions keep theirs (ENV-018). */
    private AtmosphereSample ambientSample(Level level) {
        return ambientSamples.computeIfAbsent(
                Ambients.ambient(level.dimension()), ambient -> AtmosphereSample.ambient(Ambients.cell(ambient)));
    }

    private CompletableFuture<MutationResult> queue(ServerLevel level, Supplier<MutationResult> result) {
        CompletableFuture<MutationResult> future = new CompletableFuture<>();
        pending.add(new Pending(level.getServer().getTickCount(), future, result));
        return future;
    }

    /** Completes, on the server thread, the mutations queued before this tick. */
    private void onServerTick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        while (!pending.isEmpty() && pending.peek().queuedAt() < now) {
            Pending p = pending.poll();
            try {
                p.future().complete(p.result().get());
            } catch (RuntimeException e) {
                p.future().completeExceptionally(e);
            }
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        while (!pending.isEmpty()) {
            pending.poll().future().completeExceptionally(new CancellationException("server stopping"));
        }
    }
}
