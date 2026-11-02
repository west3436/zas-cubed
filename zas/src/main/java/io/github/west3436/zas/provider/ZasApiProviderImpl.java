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
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 *   <li>an unzoned cell in an unloaded chunk, in a loaded chunk whose saved zones haven't reached
 *       the published table yet, or in a region a held breach left unresolved because it reaches
 *       unloaded chunks (GEO-024, GEO-026), is {@code UNRESOLVED}, with the ambient as its
 *       last-known state;
 *   <li>any other point is {@code AMBIENT}: the dimension's ambient (CORE-050, ENV-001). That
 *       includes a point inside a full block with no cell next to it, which holds no gas.
 * </ul>
 *
 * <p>Mutations and pins resolve their point the same way and act on that cell. They are queued for
 * the next atmos tick and their futures complete on the server thread when that tick is applied
 * (API-016, API-050).
 *
 * <p>The breathability helper (API-014) answers with LIFE's breathing rules ({@link
 * BreathingRules}); protection, internals and the pressure and temperature hazards join it with
 * #35, #36 and the life-support capability (API-022, #60).
 */
public final class ZasApiProviderImpl implements ZasApiProvider {
    /** One cell of each ambient seen, by value; ambients are few. */
    private final Map<Ambient, AtmosphereSample> ambientSamples = new ConcurrentHashMap<>();
    /** The tags of the gas set last asked about. */
    private volatile TagSets tagSets = TagSets.of(GasRegistry.EMPTY);

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
                    && (!isResolved(server, d, cell.pos()) || (table != null && table.heldAt(block, cell.sub())))) {
                // An unloaded chunk, a loaded one whose saved zones haven't been published yet, or a
                // region held at a breach because it reaches an unloaded one, is unresolved; its
                // last-known state is the ambient (GEO-026, GEO-035).
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

    @Override
    public CompletableFuture<PinResult> pin(ServerLevel level, Vec3 point, SourceId owner) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            // No server running the scheduler yet: there are no zones to pin or form.
            return CompletableFuture.completedFuture(PinResult.none(PinOutcome.UNRESOLVED));
        }
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        return service.pin(level, cell.pos(), cell.sub(), owner);
    }

    @Override
    public CompletableFuture<Void> unpin(ServerLevel level, SourceId owner) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return CompletableFuture.completedFuture(null);
        }
        return service.unpin(level, owner);
    }

    /**
     * The tag's gases in the gas set of the server running in this JVM, or else in the set the
     * client received from its server (CORE-012). In a singleplayer game they are the same set.
     */
    @Override
    public Set<ResourceLocation> gasesInTag(GasTag tag) {
        GasRegistry registry = ServerGasRegistry.running() ? ServerGasRegistry.current() : ClientGasRegistry.current();
        TagSets sets = tagSets;
        if (sets.registry() != registry) {
            sets = TagSets.of(registry);
            tagSets = sets;
        }
        return sets.gases().getOrDefault(tag.id(), Set.of());
    }

    /** A registry's tags as the API's id sets, built once per registry. */
    private record TagSets(GasRegistry registry, Map<ResourceLocation, Set<ResourceLocation>> gases) {
        static TagSets of(GasRegistry registry) {
            Map<ResourceLocation, Set<ResourceLocation>> gases = new HashMap<>();
            registry.tags().forEach((tag, ids) -> {
                // In id order, so iterating a set is deterministic.
                Set<ResourceLocation> set = new LinkedHashSet<>();
                ids.forEach(id -> set.add(ResourceLocation.parse(id)));
                gases.put(ResourceLocation.parse(tag), Collections.unmodifiableSet(set));
            });
            return new TagSets(registry, Map.copyOf(gases));
        }
    }

    /**
     * LIFE's verdict on the server ({@link Breathers#verdict}): who breathes, the atmosphere at
     * the eyes, the O₂ minimum and the gases' hazard profiles, keeping vanilla's rule while the
     * eyes are in a fluid (LIFE-022), and the verdict the breath checks hold while the atmosphere
     * at the eyes is unresolved (LIFE-048). Internals, protection and the pressure and temperature
     * hazards join it with #35, #36 and API-022 (#60). On the client, vanilla's verdict, until the
     * client breathing sync (LIFE-023) gives it the server's.
     */
    @Override
    public Breathability breathability(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return Breathing.vanilla(entity);
        }
        return Breathers.verdict(entity);
    }

    /** The server's configured O₂ minimum (LIFE-003). */
    @Override
    public double oxygenMinimum() {
        return BreathingConfig.oxygenMinimum();
    }

    /**
     * Whether the atmosphere knows a position's chunk: it is loaded, and its saved zones, if any,
     * are in the published table (API-010, #153).
     */
    private static boolean isResolved(ServerLevel level, DimensionAtmos d, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (d != null) {
            return d.isChunkLoaded(chunk) && !d.isSettling(chunk);
        }
        return AtmosPersistence.isLoaded(level.dimension(), chunk)
                && (Ambients.isExcluded(level.dimension()) || !AtmosPersistence.hasUntaken(level, chunk));
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
