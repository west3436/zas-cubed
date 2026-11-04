package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Photosynthesis (CONT-053, CONT-057, D-33, D-61): lit plants inside a zone turn its CO₂ into the
 * same moles of O₂, at a rate that scales with the light on them.
 *
 * <p><b>Finding plants</b> (NFR-001, NFR-003). The plants are kept per section, and only for the
 * sections the worker mirrors, those holding or bordering zoned cells, so nothing here depends on
 * the size of the loaded world and no cell is read to find a plant once its section is known:
 *
 * <ul>
 *   <li>A section is read once, when it joins the mirror: its palette first ({@link
 *       LevelChunkSection#maybeHas}), which says no at once for a section with no plant, then its
 *       4096 states only if the palette says yes. At most {@value #SCANS_PER_PASS} sections are read
 *       per pass and the rest wait for the next, so a large zone loading costs a few milliseconds
 *       spread over seconds, and its plants start late rather than the server stalling.
 *   <li>After that the change hook reports each position that becomes a plant or stops being one
 *       ({@link ChangeTracker#drainPlantChanges}). A crop growing isn't reported.
 *   <li>A section leaves the index when it leaves the mirror or its chunk unloads, and {@code
 *       /reload} empties the index, since the tags may have changed.
 * </ul>
 *
 * <p><b>The pass.</b> Once per atmos period, each known plant's zone and light are read, spread over
 * the period's game ticks a section at a time, as devices are (D-31). A plant counts if its block is
 * in an unpaused zone holding CO₂ and light reaches it. The light levels are summed per zone, one
 * sum per rate. At the end of the period each zone is sent one removal of CO₂ under the ledger's
 * {@code LIFE} category, and when it is applied, the moles actually removed come back as O₂ ({@link
 * Respiration#turn}), so the zone stops when its CO₂ runs out. Nothing here ever forms a zone
 * (CORE-051): under Hardcore planting forms one through its trigger (CORE-056), and under Normal
 * plants only act in zones that exist.
 *
 * <p><b>Light</b> is the block's own, with the sky darkened by night and weather as a player's light
 * level is: the brightest of block light and sky light less {@link ServerLevel#getSkyDarken}. Sky
 * light passes glass, so a greenhouse works by day and slows at night. A plant that dims light itself,
 * as leaves do, never reads above 14 at its own position, so for those the brightest of its neighbours
 * counts too. A plant with no cell of its own, a full block, acts on its first zoned neighbour.
 *
 * <p>Server thread.
 */
public final class Photosynthesis {
    /** Who changes the gas, in ledgers and logs. */
    public static final SourceId SOURCE = SourceId.of(ZasMod.MOD_ID, "photosynthesis");

    /** The most sections read for plants when a pass begins. */
    private static final int SCANS_PER_PASS = 16;

    private static final String CARBON_DIOXIDE = Respiration.CARBON_DIOXIDE.toString();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int WORDS = 64;

    /** A section read and found to hold no plant. */
    private static final long[] NO_PLANTS = new long[0];

    private static final Map<ServerLevel, Plants> LEVELS = new IdentityHashMap<>();

    /** One zone's light this pass, and a cell of it to act on. */
    private static final class Demand {
        final long block;
        long cropLight;
        long leafLight;

        Demand(long block) {
            this.block = block;
        }
    }

    /** One level's plants: the index, and the pass in progress. */
    private static final class Plants {
        /** Each indexed section's plants, a 4096-bit set by {@link SectionKeys#index}. */
        final TreeMap<Long, long[]> sections = new TreeMap<>();

        final Map<Long, Demand> demand = new LinkedHashMap<>();
        int tags;
        long scans;
        long[] pass = new long[0];
        int next;
        ZoneView lastZone;
        Demand lastDemand;
    }

    private Photosynthesis() {}

    /** Registers the listeners on the game event bus. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        gameBus.addListener(Photosynthesis::onChunkUnload);
        gameBus.addListener((LevelEvent.Unload e) -> LEVELS.remove(e.getLevel()));
        gameBus.addListener((ServerStoppedEvent e) -> LEVELS.clear());
    }

    private static void tick(MinecraftServer server) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return;
        }
        int period = ZasServerConfig.scheduler().periodTicks();
        int phase = (int) (server.getTickCount() % period);
        for (ServerLevel level : server.getAllLevels()) {
            step(service, level, phase, period);
        }
    }

    /** One game tick of a level: starts a pass, walks its slice, and sends it at the period's end. */
    private static void step(AtmosService service, ServerLevel level, int phase, int period) {
        Plants p = LEVELS.computeIfAbsent(level, k -> new Plants());
        DimensionAtmos d = service.dimensionFor(level);
        if (phase == 0) {
            begin(level, p, d, SCANS_PER_PASS);
        }
        if (p.pass.length == 0) {
            return;
        }
        boolean last = phase >= period - 1;
        walk(level, p, d, last ? p.pass.length : (phase + 1) * p.pass.length / period);
        if (last) {
            send(service, level, p);
        }
    }

    /**
     * Runs one whole pass of a level now and sends it, dropping a pass in progress: a stretch of one
     * atmos period's photosynthesis, for GameTests that drive atmos ticks themselves. The removals
     * reach the zones with the next atmos tick and the O₂ with the tick after. Server thread.
     *
     * @return the removals, one per zone, completing with the CO₂ removed
     */
    public static Map<Long, CompletableFuture<GasParcel>> stepNow(ServerLevel level) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return Map.of();
        }
        Plants p = LEVELS.computeIfAbsent(level, k -> new Plants());
        DimensionAtmos d = service.dimensionFor(level);
        begin(level, p, d, Integer.MAX_VALUE);
        walk(level, p, d, p.pass.length);
        return send(service, level, p);
    }

    /** How many plants a level's index holds. For tests and diagnostics. */
    public static int plantCount(ServerLevel level) {
        Plants p = LEVELS.get(level);
        if (p == null) {
            return 0;
        }
        int n = 0;
        for (long[] bits : p.sections.values()) {
            for (long word : bits) {
                n += Long.bitCount(word);
            }
        }
        return n;
    }

    /** How many sections have been read for plants in a level, ever. For tests and diagnostics. */
    public static long scans(ServerLevel level) {
        Plants p = LEVELS.get(level);
        return p == null ? 0 : p.scans;
    }

    // ---- The index ----------------------------------------------------------------------

    /** Starts a pass: brings the index up to date and lists the sections with plants. */
    private static void begin(ServerLevel level, Plants p, DimensionAtmos d, int maxScans) {
        p.demand.clear();
        p.lastZone = null;
        p.lastDemand = null;
        p.next = 0;
        DimensionPipeline pipeline = d.pipeline();
        sync(level, p, pipeline == null ? new long[0] : pipeline.mirroredSections(), maxScans);
        long[] pass = new long[p.sections.size()];
        int n = 0;
        for (Map.Entry<Long, long[]> e : p.sections.entrySet()) {
            if (e.getValue() != NO_PLANTS) {
                pass[n++] = e.getKey();
            }
        }
        p.pass = n == pass.length ? pass : Arrays.copyOf(pass, n);
    }

    /**
     * Brings the index up to the world and to the sections wanted.
     *
     * @param wanted the sections to keep plants for, ascending
     */
    private static void sync(ServerLevel level, Plants p, long[] wanted, int maxScans) {
        int tags = TriggerStates.generation();
        if (tags != p.tags) {
            p.tags = tags;
            p.sections.clear();
        }
        // Reported changes in the sections known; a section read below is already up to date.
        for (DirtySection changes : ChangeTracker.drainPlantChanges(level, p.sections::containsKey)) {
            apply(level, p, changes);
        }
        p.sections.keySet().removeIf(key -> Arrays.binarySearch(wanted, key) < 0);
        int scans = 0;
        for (long key : wanted) {
            if (p.sections.containsKey(key)) {
                continue;
            }
            LevelChunkSection section = section(level, key);
            if (section == null) {
                continue; // its chunk isn't loaded in full: try again next pass
            }
            if (section.hasOnlyAir() || !section.maybeHas(TriggerStates::isPlant)) {
                p.sections.put(key, NO_PLANTS);
            } else if (scans < maxScans) {
                scans++;
                p.scans++;
                p.sections.put(key, read(section));
            }
        }
    }

    /** Applies a section's reported changes, reading what each position holds now. */
    private static void apply(ServerLevel level, Plants p, DirtySection changes) {
        LevelChunkSection section = section(level, changes.key());
        if (section == null) {
            return;
        }
        long[] known = p.sections.get(changes.key());
        long[] bits = known == NO_PLANTS ? new long[WORDS] : known;
        changes.forEachIndex(i -> {
            if (TriggerStates.isPlant(section.getBlockState(i & 15, i >> 8, (i >> 4) & 15))) {
                bits[i >>> 6] |= 1L << i;
            } else {
                bits[i >>> 6] &= ~(1L << i);
            }
        });
        p.sections.put(changes.key(), bits);
    }

    /** Reads every state of a section whose palette holds a plant. */
    private static long[] read(LevelChunkSection section) {
        long[] bits = null;
        for (int i = 0; i < 4096; i++) {
            if (TriggerStates.isPlant(section.getBlockState(i & 15, i >> 8, (i >> 4) & 15))) {
                if (bits == null) {
                    bits = new long[WORDS];
                }
                bits[i >>> 6] |= 1L << i;
            }
        }
        return bits == null ? NO_PLANTS : bits;
    }

    /** A section of a chunk loaded in full, or null. Loads nothing. */
    private static LevelChunkSection section(ServerLevel level, long key) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionKeys.x(key), SectionKeys.z(key));
        if (chunk == null) {
            return null;
        }
        int index = chunk.getSectionIndexFromSectionY(SectionKeys.y(key));
        return index < 0 || index >= chunk.getSectionsCount() ? null : chunk.getSection(index);
    }

    private static void onChunkUnload(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        Plants p = LEVELS.get(level);
        if (p == null || p.sections.isEmpty()) {
            return;
        }
        int x = e.getChunk().getPos().x;
        int z = e.getChunk().getPos().z;
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            p.sections.remove(SectionKeys.section(x, y, z));
        }
    }

    // ---- The pass -----------------------------------------------------------------------

    /** Walks the pass's sections up to index {@code end}, adding each plant's light to its zone. */
    private static void walk(ServerLevel level, Plants p, DimensionAtmos d, int end) {
        if (p.next >= end) {
            return;
        }
        AtmosSnapshot table = d.published();
        int co2 = table == null ? -1 : table.registry().indexOf(CARBON_DIOXIDE);
        if (co2 < 0) {
            p.next = end;
            return;
        }
        int darken = level.getSkyDarken();
        GeometryTable geometry = Geometries.tableIfReady();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        while (p.next < end) {
            long key = p.pass[p.next++];
            long[] bits = p.sections.get(key);
            LevelChunkSection section = section(level, key);
            if (bits == null || section == null) {
                continue; // it unloaded since the pass began
            }
            int x0 = SectionKeys.x(key) << 4;
            int y0 = SectionKeys.y(key) << 4;
            int z0 = SectionKeys.z(key) << 4;
            for (int w = 0; w < bits.length; w++) {
                for (long word = bits[w]; word != 0; word &= word - 1) {
                    int i = (w << 6) | Long.numberOfTrailingZeros(word);
                    int x = i & 15;
                    int y = i >> 8;
                    int z = (i >> 4) & 15;
                    BlockState state = section.getBlockState(x, y, z);
                    int flags = TriggerStates.flags(state);
                    if ((flags & TriggerStates.PLANTING_FLAG) == 0) {
                        continue; // gone since the pass began; the next pass's changes clear it
                    }
                    at.set(x0 + x, y0 + y, z0 + z);
                    plant(level, p, table, geometry, co2, darken, state, flags, at, near);
                }
            }
        }
    }

    /** Adds one plant's light to its zone's demand, if it is in a zone with CO₂ and light reaches it. */
    private static void plant(
            ServerLevel level,
            Plants p,
            AtmosSnapshot table,
            GeometryTable geometry,
            int co2,
            int darken,
            BlockState state,
            int flags,
            BlockPos at,
            BlockPos.MutableBlockPos near) {
        long block = at.asLong();
        ZoneView zone = table.zoneAt(block);
        if (zone == null && geometry != null && geometry.geometry(state).cellCount() == 0) {
            for (Direction side : DIRECTIONS) {
                long neighbour = BlockPos.offset(block, side);
                zone = table.zoneAt(neighbour);
                if (zone != null) {
                    block = neighbour;
                    break;
                }
            }
        }
        if (zone == null || zone.paused() || !(zone.moles(co2) > 0)) {
            return;
        }
        int light = light(level, state, darken, at, near);
        if (light <= 0) {
            return;
        }
        Demand demand = p.lastDemand;
        if (zone != p.lastZone) {
            long cell = block;
            demand = p.demand.computeIfAbsent(zone.id(), id -> new Demand(cell));
            p.lastZone = zone;
            p.lastDemand = demand;
        }
        if ((flags & TriggerStates.LEAF_PLANT_FLAG) != 0) {
            demand.leafLight += light;
        } else {
            demand.cropLight += light;
        }
    }

    /** The light on a plant: see the class comment. */
    private static int light(
            ServerLevel level, BlockState state, int darken, BlockPos at, BlockPos.MutableBlockPos near) {
        int light = level.getRawBrightness(at, darken);
        if (light < PhotosynthesisRates.FULL_LIGHT && state.getLightBlock(level, at) > 0) {
            for (Direction side : DIRECTIONS) {
                light = Math.max(light, level.getRawBrightness(near.setWithOffset(at, side), darken));
            }
        }
        return light;
    }

    /** Ends a pass: sends each zone its removal of CO₂. Rates are read here, so a change applies at once. */
    private static Map<Long, CompletableFuture<GasParcel>> send(AtmosService service, ServerLevel level, Plants p) {
        Map<Long, CompletableFuture<GasParcel>> sent = new LinkedHashMap<>();
        double crop = PresetOptions.CROP_PHOTOSYNTHESIS_RATE.get();
        double leaf = PresetOptions.LEAF_PHOTOSYNTHESIS_RATE.get();
        double seconds = ZasServerConfig.scheduler().periodSeconds();
        p.demand.forEach((zone, demand) -> {
            double moles = PhotosynthesisRates.moles(demand.cropLight, demand.leafLight, crop, leaf, seconds);
            if (moles > 0) {
                sent.put(
                        zone,
                        Respiration.turn(
                                service,
                                level,
                                BlockPos.of(demand.block),
                                -1,
                                Respiration.CARBON_DIOXIDE,
                                StandardAir.OXYGEN,
                                moles,
                                SOURCE));
            }
        });
        p.demand.clear();
        p.lastZone = null;
        p.lastDemand = null;
        p.pass = new long[0];
        p.next = 0;
        return sent;
    }
}
