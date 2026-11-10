package io.github.west3436.zas.provider;

import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.ZasApiProvider;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.EdgeSummary;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.fire.IgnitionOutcome;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.geometry.PositionGeometries;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.ClientBreath;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.reaction.ServerReactions;
import io.github.west3436.zas.reaction.StandaloneReactions;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import io.github.west3436.zas.simulation.Ignited;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
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
 * BreathingRules}), with the pressure and temperature hazards, the protection of worn items and
 * the life-support capability (API-022), and internals (LIFE-021).
 */
public final class ZasApiProviderImpl implements ZasApiProvider {
    /**
     * One cell of each dimension's ambient as last asked. By dimension rather than by value, since a
     * provider may give a new ambient every atmos tick (ENV-006).
     */
    private final Map<ResourceKey<Level>, AmbientCell> ambientCells = new ConcurrentHashMap<>();

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
    public CompletableFuture<HeatResult> addHeat(ServerLevel level, Vec3 point, double joules, SourceId source) {
        return heat(level, point, true, joules, source);
    }

    @Override
    public CompletableFuture<HeatResult> removeHeat(ServerLevel level, Vec3 point, double joules, SourceId source) {
        return heat(level, point, false, joules, source);
    }

    private static CompletableFuture<HeatResult> heat(
            ServerLevel level, Vec3 point, boolean add, double joules, SourceId source) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            // No server running the scheduler yet: the open air takes or supplies it all.
            return CompletableFuture.completedFuture(new HeatResult(MutationOutcome.AMBIENT, joules, Double.NaN));
        }
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        return service.heat(level, cell.pos(), cell.sub(), add, joules, source);
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

    @Override
    public void markDirty(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)
                || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return; // nothing is held there, and it is read afresh when it loads
        }
        PositionGeometries.forget(level, pos);
        ChangeTracker.markDirty(level, pos);
    }

    @Override
    public List<BlockPos> hotspots(Level level, ZoneId zone) {
        DimensionAtmos d = dimension(level);
        if (d == null) {
            return List.of();
        }
        long[] blocks = d.hotspots().inZone(zone.value());
        List<BlockPos> out = new ArrayList<>(blocks.length);
        for (long b : blocks) {
            out.add(BlockPos.of(b));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public boolean isBurning(Level level, ZoneId zone) {
        DimensionAtmos d = dimension(level);
        return d != null && d.hotspots().burning(zone.value());
    }

    @Override
    public boolean isHotspot(Level level, BlockPos pos) {
        DimensionAtmos d = dimension(level);
        return d != null && d.hotspots().zoneAt(pos.asLong()) != 0;
    }

    @Override
    public CompletableFuture<Ignition> ignite(ServerLevel level, BlockPos pos, SourceId source) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            // No server running the scheduler yet: there are no zones to burn.
            return CompletableFuture.completedFuture(
                    new Ignition(IgnitionOutcome.OPEN, Optional.empty(), Optional.empty()));
        }
        return service.ignite(level, pos, source).thenApply(ZasApiProviderImpl::ignition);
    }

    /** The API's view of an ignition's result (API-056). */
    static Ignition ignition(Ignited i) {
        IgnitionOutcome outcome = switch (i.status()) {
            case IGNITED -> IgnitionOutcome.IGNITED;
            case BURNING -> IgnitionOutcome.BURNING;
            case NOT_FLAMMABLE -> IgnitionOutcome.NOT_FLAMMABLE;
            case NO_SPACE -> IgnitionOutcome.NO_SPACE;
            case OPEN -> IgnitionOutcome.OPEN;
            case PAUSED -> IgnitionOutcome.PAUSED;
        };
        boolean burns = outcome == IgnitionOutcome.IGNITED || outcome == IgnitionOutcome.BURNING;
        return new Ignition(
                outcome,
                burns ? Optional.ofNullable(i.hotspot()) : Optional.empty(),
                i.zoneId() == 0 ? Optional.empty() : Optional.of(new ZoneId(i.zoneId())));
    }

    @Override
    public int extinguish(ServerLevel level, BlockPos from, BlockPos to) {
        AtmosService service = AtmosService.current();
        return service == null ? 0 : service.extinguish(level, from, to);
    }

    /**
     * The tag's gases in the gas set of the server running in this JVM, or else in the set the
     * client received from its server (CORE-012). In a singleplayer game they are the same set.
     */
    @Override
    public Set<ResourceLocation> gasesInTag(GasTag tag) {
        return sets().gases().getOrDefault(tag.id(), Set.of());
    }

    private static GasRegistry registry() {
        return ServerGasRegistry.running() ? ServerGasRegistry.current() : ClientGasRegistry.current();
    }

    /** The id sets of the gas set in use now, built once for each. */
    private TagSets sets() {
        GasRegistry registry = registry();
        TagSets sets = tagSets;
        if (sets.registry() != registry) {
            sets = TagSets.of(registry);
            tagSets = sets;
        }
        return sets;
    }

    /**
     * The gas's C<sub>v</sub> in the gas set of the server running in this JVM, or else in the set
     * the client received from its server (CORE-003, API-051).
     */
    @Override
    public OptionalDouble molarHeatCapacity(ResourceLocation gas) {
        GasRegistry registry = registry();
        int index = registry.indexOf(gas.toString());
        return index < 0 ? OptionalDouble.empty() : OptionalDouble.of(registry.molarHeatCapacity(index));
    }

    /**
     * The gases of the gas set of the server running in this JVM, or else of the set the client
     * received from its server (CORE-010, API-051).
     */
    @Override
    public Set<ResourceLocation> definedGases() {
        return sets().ids();
    }

    /** The name key the gas's definition gives, in the same gas set as {@link #definedGases}. */
    @Override
    public Optional<String> gasTranslationKey(ResourceLocation gas) {
        return registry().find(gas.toString()).map(GasDefinition::nameKey);
    }

    /** The configured atmos period (CORE-041, API-052); the default before the config loads. */
    @Override
    public int atmosPeriodTicks() {
        return ZasServerConfig.scheduler().periodTicks();
    }

    /** The preset's option (API-059, CONT-107, OPS-007): its override, or the active preset's default. */
    @Override
    public boolean containerExplosionsBreakBlocks() {
        return PresetOptions.CONTAINER_EXPLOSIONS_BREAK_BLOCKS.get();
    }

    /** The server's preset (OPS-007), Normal before the config loads. */
    @Override
    public io.github.west3436.zas.api.atmosphere.AtmospherePreset preset() {
        return ZasServerConfig.preset() == AtmospherePreset.HARDCORE
                ? io.github.west3436.zas.api.atmosphere.AtmospherePreset.HARDCORE
                : io.github.west3436.zas.api.atmosphere.AtmospherePreset.NORMAL;
    }

    /**
     * Whether a zone touches a block, or the atmosphere there isn't resolved (SPACE-014, SPACE-018).
     * A space mod asks this once per randomly ticked block, so it stops as early as it can: a
     * dimension with no zone costs the resolved check and one read, and no answer builds a mixture
     * or reads a block.
     */
    @Override
    public boolean zoneTouches(Level level, BlockPos pos) {
        DimensionAtmos d = dimension(level);
        if (d == null || !(level instanceof ServerLevel server)) {
            return false;
        }
        if (!isResolved(server, d, pos)) {
            return true;
        }
        AtmosSnapshot table = d.published();
        if (table == null || (table.zonedPositions() == 0 && !table.hasRejections() && table.heldCells() == 0)) {
            return false;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return touched(table, x, y, z)
                || touched(table, x, y + 1, z)
                || touched(table, x, y - 1, z)
                || touched(table, x + 1, y, z)
                || touched(table, x - 1, y, z)
                || touched(table, x, y, z + 1)
                || touched(table, x, y, z - 1);
    }

    /**
     * Whether the block at a position has a cell (API-015, MEK-022), from the block as it is now.
     * A block that can't be read, as in a chunk that isn't loaded, counts as having one, and so does
     * every block of a client's level.
     */
    @Override
    public boolean holdsGas(Level level, BlockPos pos) {
        return !(level instanceof ServerLevel server)
                || PointResolver.resolve(server, pos).cells() != 0;
    }

    /** Whether a position holds a zoned cell, or a cell held unresolved at a breach (GEO-026). */
    private static boolean touched(AtmosSnapshot table, int x, int y, int z) {
        long block = SectionKeys.block(x, y, z);
        return table.zoneAt(block) != null || table.heldAt(block, -1);
    }

    /** FIRE-030's thresholds as the server has them (D-49), or a GameTest's override. */
    @Override
    public boolean fireBurnsIn(GasMixture air) {
        double total = air.totalMoles();
        return total > 0
                && FireConfig.settings()
                        .sustains(air.moles(StandardAir.OXYGEN) / total, air.partialPressure(StandardAir.OXYGEN));
    }

    /** The configured near-vacuum density (API-064, HEAT-008), or a GameTest's override; the default before the config loads. */
    @Override
    public double nearVacuumDensity() {
        return ZasServerConfig.heat().nearVacuumDensity();
    }

    /**
     * The running server's reactions on a standalone mixture (API-019, FIRE-031), with the
     * configured fire oxygen threshold; unchanged when no server runs in this JVM.
     */
    @Override
    public GasMixture applyStandalone(GasMixture mixture, double seconds, boolean ignition) {
        return StandaloneReactions.apply(mixture, seconds, ignition, ServerReactions.current(), FireConfig.settings());
    }

    /** A registry's gases and tags as the API's id sets, built once per registry. */
    private record TagSets(
            GasRegistry registry, Set<ResourceLocation> ids, Map<ResourceLocation, Set<ResourceLocation>> gases) {
        static TagSets of(GasRegistry registry) {
            Set<ResourceLocation> all = new TreeSet<>();
            registry.definitions().forEach(definition -> all.add(ResourceLocation.parse(definition.id())));
            Map<ResourceLocation, Set<ResourceLocation>> gases = new HashMap<>();
            registry.tags().forEach((tag, ids) -> {
                // In id order, so iterating a set is deterministic.
                Set<ResourceLocation> set = new LinkedHashSet<>();
                ids.forEach(id -> set.add(ResourceLocation.parse(id)));
                gases.put(ResourceLocation.parse(tag), Collections.unmodifiableSet(set));
            });
            return new TagSets(registry, Collections.unmodifiableSet(all), Map.copyOf(gases));
        }
    }

    /**
     * LIFE's verdict on the server ({@link Breathers#verdict}): who breathes, the atmosphere at
     * the eyes, the O₂ minimum and the gases' hazard profiles, keeping vanilla's rule while the
     * eyes are in a fluid (LIFE-022), and the verdict the breath checks hold while the atmosphere
     * at the eyes is unresolved (LIFE-048), and the pressure and temperature hazards (LIFE-010,
     * LIFE-011, LIFE-014) with worn and built-in protection (LIFE-020, LIFE-025), and internals
     * (LIFE-021). On the client, for the local player, the server's verdict without its breath gas,
     * as the client breathing sync (LIFE-023) last gave it ({@link ClientBreath#verdict}); for any
     * other entity, and before the first sync, vanilla's.
     */
    @Override
    public Breathability breathability(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return ClientBreath.isLocal(entity) ? ClientBreath.LOCAL.verdict(entity) : Breathing.vanilla(entity);
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
        Ambient ambient = Ambients.ambient(level.dimension());
        AmbientCell cell = ambientCells.get(level.dimension());
        if (cell == null || !cell.ambient().equals(ambient)) {
            cell = new AmbientCell(ambient, AtmosphereSample.ambient(Ambients.cell(ambient)));
            ambientCells.put(level.dimension(), cell);
        }
        return cell.sample();
    }

    private record AmbientCell(Ambient ambient, AtmosphereSample sample) {}

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
