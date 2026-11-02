package io.github.west3436.zas.airflow;

import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Airflow (FLOW-001 to FLOW-004, FLOW-006, FLOW-009, FLOW-011, FLOW-013, FLOW-014, FLOW-017):
 * pressure differences push entities towards the lower-pressure side of each opening between
 * zones, or between a zone and the open air.
 *
 * <p>Each atmos tick the engine exports the edges whose difference reaches the lowest class
 * threshold, with their openings ({@link DimensionPipeline#flows}); here they become a {@link
 * FlowField}, kept until the next atmos tick's. Every game tick, in each dimension where airflow is
 * on:
 *
 * <ul>
 *   <li><b>Entities</b> other than players within range of a 16-block section holding openings
 *       are found with the level's entity lookup, so the cost follows the entities near openings,
 *       not the zones' size (FLOW-006). At most {@code max_entities_per_tick} of them are moved;
 *       beyond that they take turns (FLOW-013). Each is pushed by {@link FlowField#push} at its
 *       class's threshold ({@link AirflowClass}), unless it is anchored (FLOW-004) or the
 *       atmosphere where it is is unresolved.
 *   <li><b>Players</b> move on their own client, so a server push would rubber-band. Instead, each
 *       atmos tick the server sends each player the openings within reach on edges beside the
 *       player's zone ({@link AirflowSyncPayload}), and the client pushes its own player every
 *       game tick with the same code ({@link #apply}, FLOW-017). When the flow first takes hold
 *       of a player, the server also sends a one-off impulse ({@link AirflowImpulsePayload},
 *       FLOW-009).
 * </ul>
 */
public final class Airflow {
    /** How far beyond the range a player's openings are sent, for moving between atmos ticks. */
    static final double SYNC_MARGIN = 8;

    /** The atmos tick a field was built from, for a dimension with none yet. */
    private static final long NONE = Long.MIN_VALUE;

    /** The zone of an entity whose atmosphere is unresolved: airflow leaves it alone. */
    private static final long UNRESOLVED = Long.MIN_VALUE + 1;

    private static final Map<ResourceKey<Level>, LevelState> STATES = new HashMap<>();

    /** For GameTests: takes every payload instead of the network; null sends normally. */
    private static volatile BiConsumer<ServerPlayer, CustomPacketPayload> sendHook;

    /** One dimension's airflow between game ticks. Server thread. */
    private static final class LevelState {
        long tick = NONE;
        FlowField field = FlowField.EMPTY;
        int rotation;
        int processed;
        int inRange;
        final Set<UUID> synced = new HashSet<>();
        final Set<UUID> pushed = new HashSet<>();
    }

    private Airflow() {}

    /** Registers the payloads and the tick listeners. Called once, from the mod constructor. */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(Airflow::registerPayloads);
        gameBus.addListener((ServerTickEvent.Post e) -> {
            for (ServerLevel level : e.getServer().getAllLevels()) {
                step(level);
            }
        });
        gameBus.addListener((PlayerTickEvent.Pre e) -> {
            Player p = e.getEntity();
            if (p.level().isClientSide() && p.isLocalPlayer()) {
                AirflowReceiver.CLIENT.tick(p);
            }
        });
        gameBus.addListener((ServerStoppedEvent e) -> STATES.clear());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(AirflowSyncPayload.TYPE, AirflowSyncPayload.STREAM_CODEC, AirflowReceiver::handleSync)
                .playToClient(
                        AirflowImpulsePayload.TYPE, AirflowImpulsePayload.STREAM_CODEC, AirflowReceiver::handleImpulse);
    }

    // ---- The push, shared by the server and the client ------------------------------------

    /**
     * Whether airflow leaves an entity where it is (FLOW-004): a leashed mob, a rider (it moves with
     * its vehicle), a player sneaking on the ground, or a spectator.
     */
    public static boolean anchored(Entity e) {
        if (e.isSpectator() || e.isPassenger()) {
            return true;
        }
        if (e instanceof Mob mob && mob.isLeashed()) {
            return true;
        }
        return e instanceof Player p && p.isShiftKeyDown() && p.onGround();
    }

    /**
     * Pushes an entity for one game tick: the change of velocity from {@link FlowField#push} at
     * its centre, unless it is anchored. The server calls it for entities, and the client for its
     * own player, with {@link FlowField#ANY_ZONE} (FLOW-017).
     *
     * @param zone the entity's zone, {@link FlowEdge#AMBIENT} or {@link FlowField#ANY_ZONE}
     * @param threshold the least difference that moves it, kPa
     * @return the change of velocity, or null for none
     */
    public static Vec3 apply(Entity e, FlowField field, long zone, double threshold, FlowSettings settings) {
        if (field.isEmpty() || anchored(e)) {
            return null;
        }
        Vec3 c = e.getBoundingBox().getCenter();
        Vec3 v = e.getDeltaMovement();
        double[] d = field.push(c.x, c.y, c.z, v.x, v.y, v.z, zone, threshold, settings);
        if (d == null) {
            return null;
        }
        Vec3 dv = new Vec3(d[0], d[1], d[2]);
        e.setDeltaMovement(v.add(dv));
        e.hasImpulse = true;
        return dv;
    }

    /**
     * Applies a one-off impulse (FLOW-009): raises the entity's speed along the impulse's direction
     * to the impulse's speed, if it is slower.
     */
    public static void applyImpulse(Entity e, AirflowImpulsePayload impulse) {
        double speed =
                Math.sqrt(impulse.vx() * impulse.vx() + impulse.vy() * impulse.vy() + impulse.vz() * impulse.vz());
        if (speed < 1e-9) {
            return;
        }
        Vec3 dir = new Vec3(impulse.vx() / speed, impulse.vy() / speed, impulse.vz() / speed);
        Vec3 v = e.getDeltaMovement();
        double along = v.dot(dir);
        if (along < speed) {
            e.setDeltaMovement(v.add(dir.scale(speed - along)));
            e.hasImpulse = true;
        }
    }

    // ---- The server ------------------------------------------------------------------------

    /**
     * One game tick of airflow in a level: a new field if an atmos tick was applied since the last,
     * with the players' sync, then the push of the entities near openings. The tick listener calls
     * it for every level; GameTests call it directly. Server thread.
     */
    public static void step(ServerLevel level) {
        AtmosService service = AtmosService.current();
        DimensionAtmos d = service == null ? null : service.dimension(level);
        LevelState st = STATES.get(level.dimension());
        DimensionPipeline p = d == null ? null : d.pipeline();
        if (p == null || !AirflowConfig.enabledIn(level.dimension().location())) {
            if (st != null) {
                end(level, st);
            }
            return;
        }
        if (st == null) {
            st = new LevelState();
            STATES.put(level.dimension(), st);
        }
        AtmosSnapshot table = p.published();
        FlowSettings settings = AirflowConfig.settings();
        long tick = p.flowsTick();
        if (tick != st.tick) {
            st.tick = tick;
            st.field = FlowField.of(p.flows());
            syncPlayers(level, d, table, st, settings);
        }
        st.processed = 0;
        st.inRange = 0;
        if (!st.field.isEmpty()) {
            pushEntities(level, d, table, st, settings);
        }
    }

    /** Ends airflow in a level: tells the players who had it, and drops the field. */
    private static void end(ServerLevel level, LevelState st) {
        for (UUID id : st.synced) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer player) {
                send(player, AirflowSyncPayload.none(level.dimension().location()));
            }
        }
        st.synced.clear();
        st.pushed.clear();
        st.field = FlowField.EMPTY;
        st.tick = NONE;
        st.processed = 0;
        st.inRange = 0;
    }

    private static void pushEntities(
            ServerLevel level, DimensionAtmos d, AtmosSnapshot table, LevelState st, FlowSettings s) {
        List<Entity> found = new ArrayList<>();
        IntOpenHashSet seen = new IntOpenHashSet();
        double reach = s.range() + 1;
        for (int[] sec : st.field.sections()) {
            AABB box = new AABB(
                            sec[0] * 16.0,
                            sec[1] * 16.0,
                            sec[2] * 16.0,
                            sec[0] * 16.0 + 16,
                            sec[1] * 16.0 + 16,
                            sec[2] * 16.0 + 16)
                    .inflate(reach);
            for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player) && e.isAlive())) {
                if (seen.add(e.getId())) {
                    found.add(e);
                }
            }
        }
        int n = found.size();
        st.inRange = n;
        if (n == 0) {
            return;
        }
        found.sort(Comparator.comparingInt(Entity::getId));
        int cap = AirflowConfig.maxEntitiesPerTick();
        int count = Math.min(cap, n);
        int start = n <= cap ? 0 : Math.floorMod(st.rotation, n);
        st.rotation = n <= cap ? 0 : start + count;
        for (int i = 0; i < count; i++) {
            Entity e = found.get((start + i) % n);
            AirflowClass cls = AirflowClass.of(e);
            st.processed++;
            if (cls == AirflowClass.IMMUNE) {
                continue;
            }
            long zone = zoneOf(level, d, table, e.getBoundingBox().getCenter());
            if (zone != UNRESOLVED) {
                apply(e, st.field, zone, cls.threshold(), s);
            }
        }
    }

    /** Sends each player in the level the openings within reach, and the impulse at onset. */
    private static void syncPlayers(
            ServerLevel level, DimensionAtmos d, AtmosSnapshot table, LevelState st, FlowSettings s) {
        for (ServerPlayer player : level.players()) {
            syncPlayer(level, d, table, st, s, player);
        }
    }

    /**
     * Sends a player the openings within reach on edges beside its zone (FLOW-017), or the end of
     * airflow if it had some and now has none; and the impulse, if the flow takes hold of it now
     * and didn't at the last atmos tick (FLOW-009).
     */
    private static void syncPlayer(
            ServerLevel level,
            DimensionAtmos d,
            AtmosSnapshot table,
            LevelState st,
            FlowSettings s,
            ServerPlayer player) {
        UUID id = player.getUUID();
        AirflowClass cls = AirflowClass.of(player);
        Vec3 c = player.getBoundingBox().getCenter();
        long zone = st.field.isEmpty() || cls == AirflowClass.IMMUNE || player.isSpectator()
                ? UNRESOLVED
                : zoneOf(level, d, table, c);
        List<FlowField.Opening> near = zone == UNRESOLVED
                ? List.of()
                : st.field.near(c.x, c.y, c.z, s.range() + SYNC_MARGIN, zone, cls.threshold());
        if (near.isEmpty()) {
            if (st.synced.remove(id)) {
                send(player, AirflowSyncPayload.none(level.dimension().location()));
            }
            st.pushed.remove(id);
            return;
        }
        if (near.size() > AirflowSyncPayload.MAX_OPENINGS) {
            near = new ArrayList<>(near);
            near.sort(Comparator.comparingDouble(o -> o.distance(c.x, c.y, c.z)));
            near = near.subList(0, AirflowSyncPayload.MAX_OPENINGS);
        }
        int valid = ZasServerConfig.scheduler().periodTicks() * 2 + 20;
        send(player, new AirflowSyncPayload(level.dimension().location(), s, cls.threshold(), valid, near));
        st.synced.add(id);
        FlowSettings onset = new FlowSettings(s.range(), s.speedAtOneAtm(), s.maxSpeed(), 1);
        double[] flow = anchored(player) ? null : st.field.push(c.x, c.y, c.z, 0, 0, 0, zone, cls.threshold(), onset);
        if (flow == null) {
            st.pushed.remove(id);
        } else if (st.pushed.add(id)) {
            send(player, new AirflowImpulsePayload(flow[0], flow[1], flow[2]));
        }
    }

    /**
     * The zone an entity's centre is in, {@link FlowEdge#AMBIENT} in the open air, or {@link
     * #UNRESOLVED} where the atmosphere isn't known: an unloaded or settling chunk, a held region or
     * a paused zone.
     */
    private static long zoneOf(ServerLevel level, DimensionAtmos d, AtmosSnapshot table, Vec3 point) {
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        long block = cell.pos().asLong();
        ZoneView zone = table.zoneAt(block, cell.sub());
        if (zone != null) {
            return zone.paused() ? UNRESOLVED : zone.id();
        }
        long chunk = ChunkPos.asLong(cell.pos().getX() >> 4, cell.pos().getZ() >> 4);
        if (!d.isChunkLoaded(chunk) || d.isSettling(chunk) || table.heldAt(block, cell.sub())) {
            return UNRESOLVED;
        }
        return FlowEdge.AMBIENT;
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        BiConsumer<ServerPlayer, CustomPacketPayload> hook = sendHook;
        if (hook != null) {
            hook.accept(player, payload);
            return;
        }
        if (!(player instanceof FakePlayer)
                && player.connection != null
                && player.connection.hasChannel(payload.type())) {
            player.connection.send(payload);
        }
    }

    // ---- For GameTests and the profiler ---------------------------------------------------

    /**
     * For GameTests: syncs one player with the level's current field as each atmos tick does, for
     * a player the level doesn't list, such as a {@link FakePlayer}. Server thread.
     */
    public static void syncForTest(ServerLevel level, ServerPlayer player) {
        AtmosService service = AtmosService.current();
        DimensionAtmos d = service == null ? null : service.dimension(level);
        LevelState st = STATES.get(level.dimension());
        if (d == null || d.pipeline() == null || st == null) {
            return;
        }
        syncPlayer(level, d, d.pipeline().published(), st, AirflowConfig.settings(), player);
    }

    /** For GameTests: every payload goes to {@code hook} instead of the network; null ends it. */
    public static void sendHookForTest(BiConsumer<ServerPlayer, CustomPacketPayload> hook) {
        sendHook = hook;
    }

    /** The entities airflow looked at in a level on its last game tick: at most the cap (FLOW-013). */
    public static int processedLastTick(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? 0 : st.processed;
    }

    /** The entities near openings in a level on its last game tick, before the cap. */
    public static int inRangeLastTick(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? 0 : st.inRange;
    }

    /** A level's current field. */
    public static FlowField field(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? FlowField.EMPTY : st.field;
    }
}
