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
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.persistence.AtmosPersistence;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;

/**
 * ZAS³'s implementation of the API (architecture §6.2).
 *
 * <p>Queries read the zone table the atmos scheduler last published for the level's dimension
 * ({@link AtmosService}, API-046). A point or position first resolves to one cell ({@link
 * PointResolver}, API-015): the sub-cell holding an exact point, the nearest cell to a point inside
 * a block's shape, a position's largest cell. Then (API-010):
 *
 * <ul>
 *   <li>a zoned cell answers with its zone: {@code ZONED}, or {@code PAUSED} with its last-known
 *       state (GEO-035);
 *   <li>an unzoned cell in an unloaded chunk, or in a region a held breach left unresolved because
 *       it reaches unloaded chunks (GEO-024, GEO-026), is {@code UNRESOLVED}, with the ambient as
 *       its last-known state;
 *   <li>any other point is {@code AMBIENT}: the dimension's ambient (CORE-050, ENV-001). That
 *       includes a point inside a full block with no cell next to it, which holds no gas.
 * </ul>
 *
 * <p>Mutations resolve their point the same way and act on that cell. They are queued for the next
 * atmos tick and their futures complete on the server thread when that tick is applied (API-016).
 *
 * <p><b>Interim:</b> the breathability helper (API-014) answers with vanilla's verdict, as the
 * fallback does, until LIFE's breathing, hazard and protection rules (#34 to #36) and the
 * life-support capability (API-022, #60) are built.
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

    @Override
    public AtmosphereSample sample(Level level, Vec3 point) {
        DimensionAtmos d = dimension(level);
        if (d == null || !(level instanceof ServerLevel server)) {
            return sample(level, d, PointResolver.Cell.largestOf(BlockPos.containing(point)));
        }
        return sample(level, d, PointResolver.resolve(server, point));
    }

    @Override
    public AtmosphereSample sample(Level level, BlockPos pos) {
        DimensionAtmos d = dimension(level);
        if (d == null || !(level instanceof ServerLevel server)) {
            return sample(level, d, PointResolver.Cell.largestOf(pos));
        }
        return sample(level, d, PointResolver.resolve(server, pos));
    }

    /** The sample at one resolved cell. */
    private AtmosphereSample sample(Level level, DimensionAtmos d, PointResolver.Cell cell) {
        AtmosSnapshot table = d == null ? null : d.published();
        long block = cell.pos().asLong();
        ZoneView zone = table == null ? null : table.zoneAt(block, cell.sub());
        if (zone == null) {
            if (level instanceof ServerLevel server
                    && (!isLoaded(server, d, cell.pos()) || (table != null && table.heldAt(block, cell.sub())))) {
                // An unloaded chunk, or a region held at a breach because it reaches one, is
                // unresolved; its last-known state is the ambient (GEO-026, GEO-035).
                return new AtmosphereSample(
                        AtmosphereState.UNRESOLVED,
                        Optional.empty(),
                        ambientSample(level).mixture());
            }
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
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        return service.add(level, cell.pos(), cell.sub(), gas, source);
    }

    @Override
    public CompletableFuture<MutationResult> removeGas(
            ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return CompletableFuture.completedFuture(new MutationResult(MutationOutcome.AMBIENT, GasParcel.EMPTY));
        }
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        return service.remove(level, cell.pos(), cell.sub(), moles, source);
    }

    /**
     * INTERIM until LIFE (#34 to #36) and API-022 (#60): vanilla's verdict, the same as the
     * fallback's. It ignores the atmosphere, so an entity in a vacuum zone is told it can breathe.
     * LIFE replaces this with ZAS³'s own verdict: who breathes, the breath source, the O₂ minimum,
     * gas hazards and protection, keeping vanilla's rule while the eyes are in water (LIFE-022).
     */
    @Override
    public Breathability breathability(LivingEntity entity) {
        return Breathing.vanilla(entity);
    }

    private static boolean isLoaded(ServerLevel level, DimensionAtmos d, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        return d != null ? d.isChunkLoaded(chunk) : AtmosPersistence.isLoaded(level.dimension(), chunk);
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
