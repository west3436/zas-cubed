package io.github.west3436.zas.debug;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.FaceMask;
import io.github.west3436.zas.engine.Geometry;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The operator debug overlay's data, sent only to operators who switched it on, at most once per
 * atmos tick, for the area around them (CLIENT-005, CLIENT-011 to CLIENT-013, DATA-005, D-53).
 * A payload with no layers switches the overlay off on the client.
 *
 * <p>Positions are offsets from {@code origin}, each within ±{@link DebugOverlay#RADIUS}, so they
 * travel as single bytes. Zones and edges are listed once and referred to by index.
 *
 * @param layers the {@link DebugLayer} bits switched on
 * @param dimension the dimension the data is from; the client ignores it in any other
 * @param tick the atmos tick the data is from
 * @param origin the block position the offsets count from: the operator's, when it was built
 * @param zones the zones that cells or edges refer to
 * @param cells zoned cells (layer {@code zones})
 * @param unresolved cells of unresolved regions (layer {@code zones})
 * @param faces open faces out of zones (layer {@code edges})
 * @param edges the edges those faces belong to, one per pair of sides and kind (layer {@code edges})
 * @param probe the gas geometry around the operator's eyes, or {@link Probe#NONE} (layer {@code cells})
 * @param hotspots hotspots (layer {@code hotspots})
 * @param truncated whether a size cap left data out
 */
public record DebugOverlayPayload(
        int layers,
        ResourceLocation dimension,
        long tick,
        BlockPos origin,
        List<Zone> zones,
        List<Cell> cells,
        List<Unresolved> unresolved,
        List<Face> faces,
        List<Edge> edges,
        Probe probe,
        List<Hotspot> hotspots,
        boolean truncated)
        implements CustomPacketPayload {

    public static final Type<DebugOverlayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "debug_overlay"));

    public DebugOverlayPayload {
        zones = List.copyOf(zones);
        cells = List.copyOf(cells);
        unresolved = List.copyOf(unresolved);
        faces = List.copyOf(faces);
        edges = List.copyOf(edges);
        hotspots = List.copyOf(hotspots);
    }

    /** The payload that switches the overlay off. */
    public static DebugOverlayPayload off(ResourceLocation dimension) {
        return new DebugOverlayPayload(
                0,
                dimension,
                -1,
                BlockPos.ZERO,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Probe.NONE,
                List.of(),
                false);
    }

    /** Whether a layer is on. */
    public boolean has(DebugLayer layer) {
        return layer.in(layers);
    }

    @Override
    public Type<DebugOverlayPayload> type() {
        return TYPE;
    }

    /**
     * A zone's summary.
     *
     * @param id its id
     * @param pressure its pressure in kPa, NaN if the published table lacks it
     * @param temperature its temperature in K, NaN if unknown
     * @param paused whether it is paused (GEO-030)
     */
    public record Zone(long id, float pressure, float temperature, boolean paused) {}

    /**
     * A zoned cell.
     *
     * @param dx x offset from the origin
     * @param dy y offset
     * @param dz z offset
     * @param sub its index among its block's cells (GEO-004)
     * @param zone its zone's index in {@link #zones()}
     */
    public record Cell(int dx, int dy, int dz, int sub, int zone) {}

    /** A cell of an unresolved region (GEO-024), by offset from the origin and sub-cell index. */
    public record Unresolved(int dx, int dy, int dz, int sub) {}

    /**
     * An open face out of a zone.
     *
     * @param dx the zoned cell's x offset from the origin
     * @param dy its y offset
     * @param dz its z offset
     * @param face the face crossed, as {@link io.github.west3436.zas.engine.Face#ordinal()}
     * @param edge its edge's index in {@link #edges()}
     */
    public record Face(int dx, int dy, int dz, int face, int edge) {}

    /**
     * An edge, or the part of one of a single kind (CLIENT-011).
     *
     * @param zone its zone's index in {@link #zones()}
     * @param other the other zone's index, or -1 for the ambient
     * @param direct whether its openings are direct, not through a zone boundary (CORE-037)
     * @param pressureDifference the zone's pressure minus the other side's, in kPa
     */
    public record Edge(int zone, int other, boolean direct, float pressureDifference) {
        /** Whether the edge leads to the ambient. */
        public boolean ambient() {
            return other < 0;
        }
    }

    /**
     * A hotspot (CLIENT-013).
     *
     * @param dx x offset from the origin
     * @param dy y offset
     * @param dz z offset
     * @param temperature its temperature in K
     */
    public record Hotspot(int dx, int dy, int dz, float temperature) {}

    /**
     * The gas geometry (GEO-001 to GEO-004) of every position in a cube around the operator's eyes,
     * so the client can show the seal mask and sub-cells of whatever block its crosshair is on
     * without asking the server (CLIENT-012).
     *
     * @param center the cube's centre
     * @param radius its half-width: it spans {@code 2 · radius + 1} positions on each axis
     * @param palette the distinct geometries in it
     * @param ids per position, its geometry's index in {@code palette}, in x, then z, then y order
     *     (index {@code ((y · side) + z) · side + x} of the offsets plus the radius)
     */
    public record Probe(BlockPos center, int radius, List<Geometry> palette, int[] ids) {
        public static final Probe NONE = new Probe(BlockPos.ZERO, 0, List.of(), new int[0]);

        public Probe {
            palette = List.copyOf(palette);
            ids = ids.clone();
            int side = 2 * radius + 1;
            if (!palette.isEmpty() && ids.length != side * side * side) {
                throw new IllegalArgumentException(
                        "a probe of radius " + radius + " needs " + side * side * side + " ids, got " + ids.length);
            }
        }

        /** Whether the probe holds anything. */
        public boolean isEmpty() {
            return palette.isEmpty();
        }

        /** The geometry at a position, or null if it is outside the cube. */
        public Geometry geometryAt(BlockPos pos) {
            if (isEmpty()) {
                return null;
            }
            int x = pos.getX() - center.getX() + radius;
            int y = pos.getY() - center.getY() + radius;
            int z = pos.getZ() - center.getZ() + radius;
            int side = 2 * radius + 1;
            if (x < 0 || y < 0 || z < 0 || x >= side || y >= side || z >= side) {
                return null;
            }
            return palette.get(ids[(y * side + z) * side + x]);
        }

        @Override
        public int[] ids() {
            return ids.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Probe p
                    && center.equals(p.center)
                    && radius == p.radius
                    && palette.equals(p.palette)
                    && Arrays.equals(ids, p.ids);
        }

        @Override
        public int hashCode() {
            return (center.hashCode() * 31 + radius) * 31 + Arrays.hashCode(ids);
        }
    }

    // ---- Network form ------------------------------------------------------------------

    private static final StreamCodec<ByteBuf, Zone> ZONE = StreamCodec.of(
            (buf, z) -> {
                VarLong.write(buf, z.id());
                buf.writeFloat(z.pressure());
                buf.writeFloat(z.temperature());
                buf.writeBoolean(z.paused());
            },
            buf -> new Zone(VarLong.read(buf), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    private static final StreamCodec<ByteBuf, Cell> CELL = StreamCodec.of(
            (buf, c) -> {
                writeOffset(buf, c.dx(), c.dy(), c.dz());
                buf.writeByte(c.sub());
                VarInt.write(buf, c.zone());
            },
            buf -> new Cell(buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedByte(), VarInt.read(buf)));

    private static final StreamCodec<ByteBuf, Unresolved> UNRESOLVED = StreamCodec.of(
            (buf, u) -> {
                writeOffset(buf, u.dx(), u.dy(), u.dz());
                buf.writeByte(u.sub());
            },
            buf -> new Unresolved(buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedByte()));

    private static final StreamCodec<ByteBuf, Face> FACE = StreamCodec.of(
            (buf, f) -> {
                writeOffset(buf, f.dx(), f.dy(), f.dz());
                buf.writeByte(f.face());
                VarInt.write(buf, f.edge());
            },
            buf -> new Face(buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedByte(), VarInt.read(buf)));

    private static final StreamCodec<ByteBuf, Edge> EDGE = StreamCodec.of(
            (buf, e) -> {
                VarInt.write(buf, e.zone());
                VarInt.write(buf, e.other() + 1);
                buf.writeBoolean(e.direct());
                buf.writeFloat(e.pressureDifference());
            },
            buf -> new Edge(VarInt.read(buf), VarInt.read(buf) - 1, buf.readBoolean(), buf.readFloat()));

    private static final StreamCodec<ByteBuf, Hotspot> HOTSPOT = StreamCodec.of(
            (buf, h) -> {
                writeOffset(buf, h.dx(), h.dy(), h.dz());
                buf.writeFloat(h.temperature());
            },
            buf -> new Hotspot(buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat()));

    private static final StreamCodec<ByteBuf, FaceMask> MASK = StreamCodec.of(
            (buf, m) -> {
                if (m.isEmpty()) {
                    buf.writeByte(0);
                } else if (m.isFull()) {
                    buf.writeByte(1);
                } else {
                    buf.writeByte(2);
                    for (int i = 0; i < 4; i++) {
                        buf.writeLong(m.word(i));
                    }
                }
            },
            buf -> switch (buf.readByte()) {
                case 0 -> FaceMask.EMPTY;
                case 1 -> FaceMask.FULL;
                default -> FaceMask.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong());
            });

    private static final StreamCodec<ByteBuf, Geometry.Cell> GEOMETRY_CELL = StreamCodec.of(
            (buf, c) -> {
                buf.writeDouble(c.volume());
                c.footprints().forEach(m -> MASK.encode(buf, m));
            },
            buf -> new Geometry.Cell(buf.readDouble(), masks(buf)));

    private static final StreamCodec<ByteBuf, Geometry> GEOMETRY = StreamCodec.of(
            (buf, g) -> {
                VarInt.write(buf, g.flags());
                g.coverage().forEach(m -> MASK.encode(buf, m));
                list(GEOMETRY_CELL).encode(buf, g.cells());
            },
            buf -> {
                int flags = VarInt.read(buf);
                List<FaceMask> coverage = masks(buf);
                return new Geometry(coverage, list(GEOMETRY_CELL).decode(buf), flags);
            });

    private static final StreamCodec<ByteBuf, Probe> PROBE = StreamCodec.of(
            (buf, p) -> {
                BlockPos.STREAM_CODEC.encode(buf, p.center());
                VarInt.write(buf, p.radius());
                list(GEOMETRY).encode(buf, p.palette());
                VarInt.write(buf, p.ids.length);
                for (int id : p.ids) {
                    VarInt.write(buf, id);
                }
            },
            buf -> {
                BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
                int radius = VarInt.read(buf);
                List<Geometry> palette = list(GEOMETRY).decode(buf);
                int[] ids = new int[checkedLength(buf, VarInt.read(buf))];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = VarInt.read(buf);
                    if (ids[i] < 0 || ids[i] >= palette.size()) {
                        throw new IllegalArgumentException("probe geometry index out of range: " + ids[i]);
                    }
                }
                return new Probe(center, radius, palette, ids);
            });

    public static final StreamCodec<ByteBuf, DebugOverlayPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                VarInt.write(buf, p.layers());
                ResourceLocation.STREAM_CODEC.encode(buf, p.dimension());
                buf.writeLong(p.tick());
                BlockPos.STREAM_CODEC.encode(buf, p.origin());
                list(ZONE).encode(buf, p.zones());
                list(CELL).encode(buf, p.cells());
                list(UNRESOLVED).encode(buf, p.unresolved());
                list(FACE).encode(buf, p.faces());
                list(EDGE).encode(buf, p.edges());
                PROBE.encode(buf, p.probe());
                list(HOTSPOT).encode(buf, p.hotspots());
                buf.writeBoolean(p.truncated());
            },
            buf -> new DebugOverlayPayload(
                    VarInt.read(buf),
                    ResourceLocation.STREAM_CODEC.decode(buf),
                    buf.readLong(),
                    BlockPos.STREAM_CODEC.decode(buf),
                    list(ZONE).decode(buf),
                    list(CELL).decode(buf),
                    list(UNRESOLVED).decode(buf),
                    list(FACE).decode(buf),
                    list(EDGE).decode(buf),
                    PROBE.decode(buf),
                    list(HOTSPOT).decode(buf),
                    buf.readBoolean()));

    private static void writeOffset(ByteBuf buf, int dx, int dy, int dz) {
        buf.writeByte(dx);
        buf.writeByte(dy);
        buf.writeByte(dz);
    }

    private static List<FaceMask> masks(ByteBuf buf) {
        List<FaceMask> out = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            out.add(MASK.decode(buf));
        }
        return out;
    }

    private static <T> StreamCodec<ByteBuf, List<T>> list(StreamCodec<ByteBuf, T> element) {
        return element.apply(ByteBufCodecs.list());
    }

    /** Rejects a length the remaining bytes can't hold, before allocating for it. */
    private static int checkedLength(ByteBuf buf, int length) {
        if (length < 0 || length > buf.readableBytes()) {
            throw new IllegalArgumentException("bad length " + length);
        }
        return length;
    }
}
