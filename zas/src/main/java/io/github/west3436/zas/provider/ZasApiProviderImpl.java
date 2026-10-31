package io.github.west3436.zas.provider;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.ZasApiProvider;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.EdgeSummary;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

/**
 * ZAS³'s implementation of the API (architecture §6.2).
 *
 * <p>Queries read the zone table the atmos scheduler last published for the level's dimension
 * ({@link AtmosService}, API-046): a zoned position answers with its zone, any other with the
 * dimension's ambient (CORE-050, ENV-001). Mutations are queued for the next atmos tick and their
 * futures complete on the server thread when that tick is applied (API-016).
 */
public final class ZasApiProviderImpl implements ZasApiProvider {
    /** One cell of each ambient seen, by value; ambients are few. */
    private final Map<Ambient, AtmosphereSample> ambientSamples = new ConcurrentHashMap<>();

    private ZasApiProviderImpl() {}

    /**
     * Installs the provider into the API. Called once, from the mod constructor.
     *
     * @param gameBus NeoForge's game event bus
     */
    public static void install(IEventBus gameBus) {
        ZasApi.install(new ZasApiProviderImpl());
    }

    @Override
    public boolean isReal() {
        return true;
    }

    // TODO(API-015): resolve an exact point to the sub-cell that contains it.
    @Override
    public AtmosphereSample sample(Level level, Vec3 point) {
        return sample(level, BlockPos.containing(point));
    }

    @Override
    public AtmosphereSample sample(Level level, BlockPos pos) {
        DimensionAtmos d = dimension(level);
        AtmosSnapshot table = d == null ? null : d.published();
        ZoneView zone = table == null ? null : table.zoneAt(pos.asLong());
        if (zone == null) {
            return ambientSample(level);
        }
        return new AtmosphereSample(
                zone.paused() ? AtmosphereState.PAUSED : AtmosphereState.ZONED,
                Optional.of(new ZoneId(zone.id())),
                mixture(zone, table.registry()));
    }

    @Override
    public Optional<ZoneInfo> zone(Level level, ZoneId id) {
        DimensionAtmos d = dimension(level);
        AtmosSnapshot table = d == null ? null : d.published();
        ZoneView zone = table == null ? null : table.zone(id.value());
        return zone == null ? Optional.empty() : Optional.of(info(zone, table.registry()));
    }

    @Override
    public CompletableFuture<MutationResult> addGas(ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            // No server running the scheduler yet: the open air takes everything offered.
            return CompletableFuture.completedFuture(new MutationResult(MutationOutcome.AMBIENT, gas));
        }
        return service.add(level, BlockPos.containing(point), gas, source);
    }

    @Override
    public CompletableFuture<MutationResult> removeGas(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return CompletableFuture.completedFuture(new MutationResult(MutationOutcome.AMBIENT, GasParcel.EMPTY));
        }
        return service.remove(level, BlockPos.containing(point), moles, source);
    }

    private static DimensionAtmos dimension(Level level) {
        AtmosService service = AtmosService.current();
        return service == null || level.isClientSide() ? null : service.dimension(level);
    }

    /** One cell of the level's dimension's ambient. Excluded dimensions keep theirs (ENV-018). */
    private AtmosphereSample ambientSample(Level level) {
        return ambientSamples.computeIfAbsent(
                Ambients.ambient(level.dimension()), ambient -> AtmosphereSample.ambient(Ambients.cell(ambient)));
    }

    /** A zone's whole mixture. */
    static GasMixture mixture(ZoneView zone, GasRegistry registry) {
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        for (int i = 0; i < zone.gasCount(); i++) {
            double n = zone.moles(i);
            if (n > 0) {
                moles.put(ResourceLocation.parse(registry.definition(i).id()), n);
            }
        }
        return new GasMixture(moles, zone.temperature(), zone.volume());
    }

    static ZoneInfo info(ZoneView zone, GasRegistry registry) {
        return new ZoneInfo(
                new ZoneId(zone.id()),
                zone.volume(),
                zone.cellCount(),
                mixture(zone, registry),
                new EdgeSummary(zone.zoneEdges(), zone.ambientEdges(), zone.openFaces()),
                zone.breached(),
                zone.paused());
    }
}
