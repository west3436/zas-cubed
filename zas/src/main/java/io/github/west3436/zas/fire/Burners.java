package io.github.west3436.zas.fire;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.TriggerStates;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.fire.CarbonCombustion;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.simulation.AtmosService;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Burning blocks (FIRE-021, FIRE-022, FIRE-005, HEAT-007): every fire and lit fuel block, the
 * burning states of {@code #zas:combustion} ({@link TriggerStates#isCombustion}), burns its fuel
 * profile ({@link FuelProfiles}) as carbon in its zone.
 *
 * <p><b>Tracking.</b> The change hook reports each position whose state starts or stops burning
 * ({@link #onBlockWrite}), and each loaded chunk is scanned once for burning states, so the
 * service knows every burning block in the loaded world without polling block entities. It needs
 * nothing from a fuel block but its tag and its {@code lit} property, so a modded fuel block, such
 * as the fuel generator (CONT-067), burns through data.
 *
 * <p><b>Burning.</b> Once per atmos period each tracked block that still burns is checked against
 * the fire oxygen threshold (FIRE-030) at its cell, the cell in front of it for a full block
 * ({@link FireAir}). A fire block that fails it goes out here, which covers soul fire, which never
 * ticks (FIRE-020); furnaces and campfires are paused or put out by their own ticks (M5, M6). A
 * block that passes it, in a zone, burns its profile's power for the period: the carbon {@code
 * watts × seconds / 394 kJ}, with FIRE-005's CO share at the zone's O₂ fraction. The burns of a
 * zone are summed and sent once: the O₂ is removed, and when the removal is applied, CO₂, CO and
 * heat come back in proportion to the O₂ actually removed, so a zone short of O₂ gets only the
 * products of what burned. Gas is recorded under the ledger's {@code REACTION} category; nothing
 * forms a zone (CORE-051): combustion forms zones only through the Hardcore trigger (CORE-056). A
 * block outside zones changes nothing.
 *
 * <p>Server thread. Writes made off the server thread aren't tracked until their chunk loads again.
 */
public final class Burners {
    /** Who changes the gas, in ledgers and logs. */
    public static final SourceId SOURCE = SourceId.of(ZasMod.MOD_ID, "combustion");

    static final ResourceLocation CARBON_DIOXIDE =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_dioxide");
    static final ResourceLocation CARBON_MONOXIDE =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_monoxide");

    /** The longest stretch one burn covers, in game ticks, after a stall. */
    private static final long MAX_TICKS = 200;

    /** One level's burning positions by chunk. */
    private static final class Tracked {
        final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
        long lastBurnTick = Long.MIN_VALUE;
    }

    /** One zone's burn this period. */
    private static final class Draw {
        BlockPos pos;
        int sub;
        double carbon;
        double coShare;
    }

    private static final Map<ServerLevel, Tracked> LEVELS = new IdentityHashMap<>();

    private Burners() {}

    /** Registers the listeners on the game event bus. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(Burners::onChunkLoad);
        gameBus.addListener(Burners::onChunkUnload);
        gameBus.addListener((LevelEvent.Unload e) -> LEVELS.remove(e.getLevel()));
        gameBus.addListener((ServerStoppedEvent e) -> LEVELS.clear());
        gameBus.addListener(Burners::onServerTick);
    }

    /**
     * Records a write from the change hook, on the server thread: a position that starts or stops
     * burning.
     */
    public static void onBlockWrite(ServerLevel level, BlockPos pos, BlockState old, BlockState now) {
        boolean was = TriggerStates.isCombustion(old);
        boolean is = TriggerStates.isCombustion(now);
        if (was == is) {
            return;
        }
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (is) {
            tracked(level)
                    .byChunk
                    .computeIfAbsent(chunk, k -> new LongOpenHashSet())
                    .add(pos.asLong());
        } else {
            Tracked t = LEVELS.get(level);
            LongOpenHashSet set = t == null ? null : t.byChunk.get(chunk);
            if (set != null) {
                set.remove(pos.asLong());
                if (set.isEmpty()) {
                    t.byChunk.remove(chunk);
                }
            }
        }
    }

    /** How many burning positions a level tracks. For tests and diagnostics. */
    public static int count(ServerLevel level) {
        Tracked t = LEVELS.get(level);
        if (t == null) {
            return 0;
        }
        int n = 0;
        for (LongOpenHashSet set : t.byChunk.values()) {
            n += set.size();
        }
        return n;
    }

    /** Whether a position is tracked as burning. For tests. */
    public static boolean tracks(ServerLevel level, BlockPos pos) {
        Tracked t = LEVELS.get(level);
        LongOpenHashSet set = t == null ? null : t.byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return set != null && set.contains(pos.asLong());
    }

    private static Tracked tracked(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, k -> new Tracked());
    }

    private static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()
                || !(e.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        LongOpenHashSet found = null;
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();
        ChunkPos cp = chunk.getPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(TriggerStates::isCombustion)) {
                continue;
            }
            int y0 = SectionPos.sectionToBlockCoord(minSection + i);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (TriggerStates.isCombustion(s.getBlockState(x, y, z))) {
                            if (found == null) {
                                found = new LongOpenHashSet();
                            }
                            found.add(BlockPos.asLong(cp.getMinBlockX() + x, y0 + y, cp.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        if (found != null) {
            tracked(level).byChunk.put(cp.toLong(), found);
        } else {
            Tracked t = LEVELS.get(level);
            if (t != null) {
                t.byChunk.remove(cp.toLong());
            }
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            Tracked t = LEVELS.get(level);
            if (t != null) {
                t.byChunk.remove(e.getChunk().getPos().toLong());
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (AtmosService.current() == null) {
            return;
        }
        long now = e.getServer().getTickCount();
        int period = ZasServerConfig.scheduler().periodTicks();
        for (Map.Entry<ServerLevel, Tracked> entry : LEVELS.entrySet()) {
            Tracked t = entry.getValue();
            if (t.lastBurnTick == Long.MIN_VALUE || t.lastBurnTick > now) {
                t.lastBurnTick = now;
                continue;
            }
            long elapsed = now - t.lastBurnTick;
            if (elapsed >= period) {
                t.lastBurnTick = now;
                burn(entry.getKey(), Math.min(elapsed, MAX_TICKS) / 20.0);
            }
        }
    }

    /**
     * Burns every burning block of a level for {@code seconds}, now: the periodic step, and for
     * GameTests, which drive time themselves. The changes reach the zones with the next atmos tick
     * and the products with the one after.
     *
     * @return the burns sent, one per zone, each completing with what burned (scaled to the O₂
     *     removed), or with nothing if no O₂ was removed
     */
    public static Map<Long, CompletableFuture<CarbonCombustion.Burn>> burn(ServerLevel level, double seconds) {
        return burn(level, seconds, pos -> true);
    }

    /**
     * {@link #burn(ServerLevel, double)} for only the burning blocks {@code only} accepts: for
     * GameTests, so one test's burn doesn't reach another's zones.
     */
    public static Map<Long, CompletableFuture<CarbonCombustion.Burn>> burn(
            ServerLevel level, double seconds, java.util.function.Predicate<BlockPos> only) {
        Map<Long, CompletableFuture<CarbonCombustion.Burn>> sent = new LinkedHashMap<>();
        Tracked t = LEVELS.get(level);
        AtmosService service = AtmosService.current();
        if (t == null || service == null || !(seconds > 0)) {
            return sent;
        }
        FireSettings fire = FireConfig.settings();
        Map<Long, Draw> draws = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (LongOpenHashSet set : t.byChunk.values().toArray(new LongOpenHashSet[0])) {
            for (LongIterator it = set.clone().iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                pos.set(key);
                if (!only.test(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!TriggerStates.isCombustion(state)) {
                    set.remove(key);
                    continue;
                }
                boolean isFire = state.getBlock() instanceof BaseFireBlock;
                FireAir.Air air = FireAir.at(level, pointOf(pos, state));
                if (!air.sustains(fire)) {
                    if (isFire) {
                        // FIRE-020: soul fire never ticks, so a fire without enough O2 goes out here too.
                        level.removeBlock(pos.immutable(), false);
                    }
                    continue;
                }
                if (!air.zoned()) {
                    continue;
                }
                double watts = FuelProfiles.watts(state, isFire ? burningOn(level, pos) : null);
                if (!(watts > 0)) {
                    continue;
                }
                Draw d = draws.computeIfAbsent(air.zone().id(), k -> new Draw());
                d.pos = air.pos();
                d.sub = air.sub();
                d.carbon += CarbonCombustion.carbonFor(watts, seconds);
                d.coShare = CarbonCombustion.coShare(air.oxygenFraction(), fire);
            }
        }
        t.byChunk.values().removeIf(LongOpenHashSet::isEmpty);
        draws.forEach((zone, d) -> sent.put(zone, send(service, level, d)));
        return sent;
    }

    /** Sends one zone's burn: the O₂ now, the products and heat when the removal is applied. */
    private static CompletableFuture<CarbonCombustion.Burn> send(AtmosService service, ServerLevel level, Draw d) {
        CarbonCombustion.Burn burn = CarbonCombustion.burn(d.carbon, d.coShare);
        return service.removeForCombustion(level, d.pos, d.sub, Map.of(StandardAir.OXYGEN, burn.oxygen()), SOURCE)
                .thenApply(r -> products(service, level, d, burn, r));
    }

    private static CarbonCombustion.Burn products(
            AtmosService service, ServerLevel level, Draw d, CarbonCombustion.Burn burn, MutationResult r) {
        double o2 = r.moved().moles().getOrDefault(StandardAir.OXYGEN, 0.0);
        if (r.outcome() != MutationOutcome.ZONE || !(o2 > 0) || !(burn.oxygen() > 0)) {
            return CarbonCombustion.burn(0, 0);
        }
        CarbonCombustion.Burn done = burn.scaled(Math.min(1, o2 / burn.oxygen()));
        Map<ResourceLocation, Double> made = new HashMap<>();
        if (done.carbonDioxide() > 0) {
            made.put(CARBON_DIOXIDE, done.carbonDioxide());
        }
        if (done.carbonMonoxide() > 0) {
            made.put(CARBON_MONOXIDE, done.carbonMonoxide());
        }
        if (!made.isEmpty()) {
            service.addForCombustion(
                    level, d.pos, d.sub, new GasParcel(made, r.moved().temperature()), SOURCE);
        }
        if (done.heat() > 0) {
            service.heatForCombustion(level, d.pos, d.sub, done.heat(), SOURCE);
        }
        return done;
    }

    /**
     * Where a burning block takes its air from: a fire its own cell; a block facing a way the cell
     * in front of it; any other its own cells, or the open cell next to it.
     */
    static Vec3 pointOf(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return FireAir.front(pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return Vec3.atCenterOf(pos);
    }

    /**
     * The block a fire burns on (FIRE-022): the block below it unless that is air, else the first
     * flammable block next to it, in {@link Direction} order; null if none.
     */
    static @Nullable BlockState burningOn(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isAir()) {
            return below;
        }
        for (Direction d : Direction.values()) {
            BlockPos next = pos.relative(d);
            BlockState there = level.getBlockState(next);
            if (!there.isAir() && there.isFlammable(level, next, d.getOpposite())) {
                return there;
            }
        }
        return null;
    }
}
