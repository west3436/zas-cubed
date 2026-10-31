package io.github.west3436.zas.debug;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.DebugRegion;
import io.github.west3436.zas.engine.scheduler.DebugView;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The operator debug overlay on the server (CLIENT-005, CLIENT-011 to CLIENT-013): who has it on,
 * what the atmos workers export for them, and the {@link DebugOverlayPayload}s sent to them.
 *
 * <p><b>Flow.</b> Each server tick, the regions around the operators with the {@code zones} or
 * {@code edges} layer on are handed to their dimension's scheduler; the worker exports the cells
 * and faces inside them right after the next atmos tick ({@link
 * io.github.west3436.zas.engine.scheduler.DimensionEngine#debugView}), so the server thread never
 * reads the zone graph (architecture §3.1). When a dimension publishes a new atmos tick, each of
 * its operators gets one payload for the area within {@link #RADIUS} blocks of them: at most one
 * per atmos tick (D-53). Zone pressures, temperatures and the paused flag come from the published
 * table.
 *
 * <p><b>Who.</b> Only players with permission level {@link #PERMISSION_LEVEL} can switch it on; a
 * player who loses it has the overlay switched off. Nothing is sent to anyone else (DATA-005).
 *
 * <p>Server thread only.
 */
public final class DebugOverlay {
    /** The permission level the overlay needs: vanilla's operator level (CLIENT-005, OPS-011). */
    public static final int PERMISSION_LEVEL = Commands.LEVEL_GAMEMASTERS;

    /** How far from the operator cells, edges and hotspots are shown: 2 chunks (CLIENT-005). */
    public static final int RADIUS = 32;

    /** The half-width of the cube of block geometry sent for the crosshair (CLIENT-012). */
    public static final int PROBE_RADIUS = 6;

    /** The most cells, unresolved cells or faces one payload carries, to stay well under 1 MiB. */
    public static final int MAX_ENTRIES = 32_768;

    private static final Map<UUID, Integer> LAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Sent> LAST_SENT = new HashMap<>();

    /** The atmos tick of a dimension a player was last sent. */
    private record Sent(ResourceKey<Level> dimension, long tick) {}

    private DebugOverlay() {}

    /** Registers the payload, the command and the listeners. Called once, from the mod constructor. */
    public static void register(IEventBus modBus) {
        modBus.addListener(DebugOverlay::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> DebugCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(
                (PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            LAYERS.clear();
            LAST_SENT.clear();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(DebugOverlayPayload.TYPE, DebugOverlayPayload.STREAM_CODEC, ClientDebugOverlay::handle);
    }

    // ---- Who has it on -----------------------------------------------------------------

    /** The layers a player has on, as {@link DebugLayer} bits; 0 if the overlay is off. */
    public static int layers(ServerPlayer player) {
        return LAYERS.getOrDefault(player.getUUID(), 0);
    }

    /**
     * Sets the layers a player has on; 0 switches the overlay off. The player gets fresh data with
     * the next atmos tick, or at once an empty payload when switched off. The caller has checked
     * the permission.
     */
    public static void setLayers(ServerPlayer player, int layers) {
        int masked = layers & DebugLayer.ALL;
        LAST_SENT.remove(player.getUUID());
        if (masked == 0) {
            if (LAYERS.remove(player.getUUID()) != null) {
                send(player, DebugOverlayPayload.off(player.level().dimension().location()));
            }
        } else {
            LAYERS.put(player.getUUID(), masked);
        }
    }

    private static void forget(UUID player) {
        LAYERS.remove(player);
        LAST_SENT.remove(player);
    }

    // ---- Ticking -----------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        AtmosService service = AtmosService.current();
        if (service == null) {
            return;
        }
        Map<ServerLevel, List<DebugRegion>> regions = new LinkedHashMap<>();
        List<ServerPlayer> operators = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : List.copyOf(LAYERS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                continue; // between respawn and the new player object, or gone
            }
            if (!player.hasPermissions(PERMISSION_LEVEL)) {
                setLayers(player, 0);
                continue;
            }
            operators.add(player);
            int layers = e.getValue();
            if (DebugLayer.ZONES.in(layers) || DebugLayer.EDGES.in(layers)) {
                regions.computeIfAbsent(player.serverLevel(), l -> new ArrayList<>())
                        .add(regionAround(player.blockPosition()));
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            DimensionAtmos d = service.dimension(level);
            DimensionPipeline p = d == null ? null : d.pipeline();
            if (p != null) {
                p.scheduler().setDebugRegions(regions.getOrDefault(level, List.of()));
            }
        }
        for (ServerPlayer player : operators) {
            DimensionAtmos d = service.dimension(player.serverLevel());
            AtmosSnapshot published = d == null ? null : d.published();
            Sent now = new Sent(player.level().dimension(), published == null ? -1 : published.tick());
            if (now.equals(LAST_SENT.get(player.getUUID()))) {
                continue;
            }
            LAST_SENT.put(player.getUUID(), now);
            send(player, build(player, layers(player)));
        }
    }

    private static void send(ServerPlayer player, DebugOverlayPayload payload) {
        // Fake players have no client; every real client has the channel (zas is required on both sides).
        if (!(player instanceof FakePlayer) && player.connection.hasChannel(DebugOverlayPayload.TYPE)) {
            player.connection.send(payload);
        }
    }

    /** The region exported for an operator at a position (CLIENT-005: 2 chunks). */
    public static DebugRegion regionAround(BlockPos pos) {
        return DebugRegion.around(pos.getX(), pos.getY(), pos.getZ(), RADIUS);
    }

    // ---- Building payloads -----------------------------------------------------------------

    /** The payload for an operator now, from the last atmos tick of their dimension. */
    public static DebugOverlayPayload build(ServerPlayer player, int layers) {
        return build(
                player.serverLevel(), player.blockPosition(), BlockPos.containing(player.getEyePosition()), layers);
    }

    /**
     * The payload for an operator at {@code origin} with eyes in {@code eyes}: what the last atmos
     * tick of the level exported within {@link #RADIUS} of the origin, the published state of the
     * zones it names, and the block geometry within {@link #PROBE_RADIUS} of the eyes.
     */
    public static DebugOverlayPayload build(ServerLevel level, BlockPos origin, BlockPos eyes, int layers) {
        AtmosService service = AtmosService.current();
        DimensionAtmos d = service == null ? null : service.dimension(level);
        AtmosSnapshot published = d == null ? null : d.published();
        DimensionPipeline pipeline = d == null ? null : d.pipeline();
        DebugView view =
                pipeline == null ? DebugView.EMPTY : pipeline.scheduler().debugView();
        DebugRegion region = regionAround(origin);
        double ambientPressure = Ambients.ambient(level.dimension()).pressure();

        Builder b = new Builder(published);
        boolean truncated = view.truncated();
        if (DebugLayer.ZONES.in(layers)) {
            for (DebugView.CellEntry c : view.cells()) {
                if (region.contains(c.block())) {
                    if (b.cells.size() >= MAX_ENTRIES) {
                        truncated = true;
                        break;
                    }
                    b.cells.add(new DebugOverlayPayload.Cell(
                            dx(c.block(), origin),
                            dy(c.block(), origin),
                            dz(c.block(), origin),
                            c.sub(),
                            b.zone(c.zone())));
                }
            }
            for (DebugView.UnresolvedEntry u : view.unresolved()) {
                if (region.contains(u.block())) {
                    if (b.unresolved.size() >= MAX_ENTRIES) {
                        truncated = true;
                        break;
                    }
                    b.unresolved.add(new DebugOverlayPayload.Unresolved(
                            dx(u.block(), origin), dy(u.block(), origin), dz(u.block(), origin), u.sub()));
                }
            }
        }
        if (DebugLayer.EDGES.in(layers)) {
            for (DebugView.FaceEntry f : view.faces()) {
                if (region.contains(f.block())) {
                    if (b.faces.size() >= MAX_ENTRIES) {
                        truncated = true;
                        break;
                    }
                    int edge = b.edge(f.zone(), f.ambient() ? -1 : f.other(), f.direct(), ambientPressure);
                    b.faces.add(new DebugOverlayPayload.Face(
                            dx(f.block(), origin),
                            dy(f.block(), origin),
                            dz(f.block(), origin),
                            f.face().ordinal(),
                            edge));
                }
            }
        }
        DebugOverlayPayload.Probe probe =
                DebugLayer.CELLS.in(layers) ? probe(level, eyes) : DebugOverlayPayload.Probe.NONE;
        // Hotspots are a FIRE concept (R1): nothing produces them yet, so the list stays empty.
        List<DebugOverlayPayload.Hotspot> hotspots = List.of();
        return new DebugOverlayPayload(
                layers,
                level.dimension().location(),
                published == null ? -1 : published.tick(),
                origin,
                b.zones,
                b.cells,
                b.unresolved,
                b.faces,
                b.edges,
                probe,
                hotspots,
                truncated);
    }

    /** The block geometry of the cube around the eyes (CLIENT-012); empty before the table is ready. */
    static DebugOverlayPayload.Probe probe(ServerLevel level, BlockPos eyes) {
        GeometryTable table = Geometries.tableIfReady();
        if (table == null) {
            return DebugOverlayPayload.Probe.NONE;
        }
        int side = 2 * PROBE_RADIUS + 1;
        int[] ids = new int[side * side * side];
        Map<Geometry, Integer> palette = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    pos.set(
                            eyes.getX() + x - PROBE_RADIUS,
                            eyes.getY() + y - PROBE_RADIUS,
                            eyes.getZ() + z - PROBE_RADIUS);
                    Geometry g = level.isLoaded(pos) && !level.isOutsideBuildHeight(pos)
                            ? table.geometry(level.getBlockState(pos))
                            : Geometry.AIR;
                    ids[(y * side + z) * side + x] = palette.computeIfAbsent(g, k -> palette.size());
                }
            }
        }
        return new DebugOverlayPayload.Probe(eyes.immutable(), PROBE_RADIUS, List.copyOf(palette.keySet()), ids);
    }

    private static int dx(long block, BlockPos origin) {
        return SectionKeys.blockX(block) - origin.getX();
    }

    private static int dy(long block, BlockPos origin) {
        return SectionKeys.blockY(block) - origin.getY();
    }

    private static int dz(long block, BlockPos origin) {
        return SectionKeys.blockZ(block) - origin.getZ();
    }

    /** Assigns zone and edge indices as entries are added. */
    private static final class Builder {
        final AtmosSnapshot published;
        final List<DebugOverlayPayload.Zone> zones = new ArrayList<>();
        final List<DebugOverlayPayload.Cell> cells = new ArrayList<>();
        final List<DebugOverlayPayload.Unresolved> unresolved = new ArrayList<>();
        final List<DebugOverlayPayload.Face> faces = new ArrayList<>();
        final List<DebugOverlayPayload.Edge> edges = new ArrayList<>();
        final Map<Long, Integer> zoneIndex = new HashMap<>();
        final Map<List<Long>, Integer> edgeIndex = new HashMap<>();

        Builder(AtmosSnapshot published) {
            this.published = published;
        }

        int zone(long id) {
            return zoneIndex.computeIfAbsent(id, k -> {
                ZoneView z = published == null ? null : published.zone(id);
                zones.add(
                        z == null
                                ? new DebugOverlayPayload.Zone(id, Float.NaN, Float.NaN, false)
                                : new DebugOverlayPayload.Zone(
                                        id, (float) z.pressure(), (float) z.temperature(), z.paused()));
                return zones.size() - 1;
            });
        }

        int edge(long zone, long other, boolean direct, double ambientPressure) {
            List<Long> key = List.of(zone, other, direct ? 1L : 0L);
            Integer known = edgeIndex.get(key);
            if (known != null) {
                return known;
            }
            int a = zone(zone);
            int b = other < 0 ? -1 : zone(other);
            float pa = zones.get(a).pressure();
            float pb = b < 0 ? (float) ambientPressure : zones.get(b).pressure();
            edges.add(new DebugOverlayPayload.Edge(a, b, direct, pa - pb));
            edgeIndex.put(key, edges.size() - 1);
            return edges.size() - 1;
        }
    }
}
