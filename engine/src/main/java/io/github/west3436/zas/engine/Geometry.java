package io.github.west3436.zas.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The gas geometry of one block position: an entry of the {@link GeometryPalette}.
 *
 * <p>{@code coverage} gives, for each {@link Face} in order, the part of the face covered by the
 * block's collision shape. {@code cells} are the connected regions of free space that reach at
 * least one face (GEO-001, GEO-004), in a fixed order. Free space that reaches no face is not a
 * cell. {@code flags} is a set of {@code FLAG_*} bits.
 *
 * <p><b>Heat</b> (HEAT-011 to HEAT-013, architecture §4.8). Conduction follows lines of the 1/16
 * grid along a face's normal. {@code through} gives, for each face, the lines that cross the whole
 * block without meeting a cell (solid, or enclosed pockets): a wall layer. Each cell's {@link
 * Cell#shields()} gives the lines that, entering from a face, cross solid before they reach that
 * cell. {@code partitions} are the pairs of the block's own cells with solid between them, such as
 * the two sides of a pane, and the area they face each other across. {@code transmittance} is the
 * block's U in W/(m²·K) per block-thick layer (HEAT-002), or NaN for the configured fallback
 * (HEAT-015); {@code sourceTemperature} is the fixed temperature of a heat-source block (HEAT-003),
 * or NaN.
 *
 * <p><b>Fire</b> (FIRE-033, FIRE-034). {@code igniteOdds} is a flammable block's vanilla ignite odds,
 * 0 if it isn't flammable, and {@code ignitionTemperature} the gas temperature in K it catches fire
 * at, or NaN for the configured one. The worker needs them per block, so they are here, as heat data
 * is.
 *
 * @param coverage six masks, indexed by {@link Face#ordinal()}
 * @param cells the cells, possibly none (a solid block)
 * @param flags {@code FLAG_*} bits
 * @param through six masks, indexed by {@link Face#ordinal()}: lines through the block that meet no
 *     cell; a face and its opposite have the same mask
 * @param partitions the pairs of cells with solid between them
 * @param transmittance U in W/(m²·K), positive, or NaN for the fallback
 * @param sourceTemperature the heat source's temperature in K, or NaN if the block isn't one
 * @param igniteOdds the block's vanilla ignite odds, or 0 if it isn't flammable
 * @param ignitionTemperature the temperature in K it catches fire at, or NaN for the configured one
 */
public record Geometry(
        List<FaceMask> coverage,
        List<Cell> cells,
        int flags,
        List<FaceMask> through,
        List<Partition> partitions,
        double transmittance,
        double sourceTemperature,
        int igniteOdds,
        double ignitionTemperature) {
    /** The gas volume of a whole position, in litres (A-02). */
    public static final double FULL_VOLUME = 1000.0;

    /** The gas volume of one 1/16 voxel, in litres. Exact in binary. */
    public static final double VOXEL_VOLUME = FULL_VOLUME / 4096;

    /** The area of one line of the 1/16 grid, in m². Exact in binary. */
    public static final double LINE_AREA = 1.0 / FaceMask.BITS;

    /** The geometry comes from a datapack override, not the collision shape (GEO-005). */
    public static final int FLAG_OVERRIDE = 1;

    /**
     * The position holds a block, not air, that gas passes as it passes air: a flower, a lever,
     * redstone wire, a rail. Its cell is the same as air's; the flag only keeps a gas fire from
     * being asked for there, since the server places one only on air (FIRE-018).
     */
    public static final int FLAG_OCCUPIED = 1 << 1;

    /** The bit of the first {@link #boundaryFlag}; the six follow in {@link Face} order. */
    private static final int BOUNDARY_SHIFT = 8;

    /**
     * Every face is a zone boundary (GEO-007): all six {@link #boundaryFlag} bits, so the block's
     * cells are zones of their own. A block that keeps zones apart across some faces only, such as
     * a door, has only those faces' bits.
     */
    public static final int FLAG_ZONE_BOUNDARY = 0x3F << BOUNDARY_SHIFT;

    /** The position holds a fluid (GEO-006). */
    public static final int FLAG_FLUID = 1 << 2;

    /** The block is a heat source (HEAT-003): it has a {@link #sourceTemperature()}. */
    public static final int FLAG_HEAT_SOURCE = 1 << 3;

    /** The geometry depends on position and is computed per position (GEO-043). */
    public static final int FLAG_POSITIONAL = 1 << 4;

    /**
     * The block is a vanilla fire (fire, soul fire): in a zone it is a hotspot (FIRE-019). The
     * flag makes lighting and putting out a fire a geometry change, so the worker sees it.
     */
    public static final int FLAG_FIRE = 1 << 5;

    /** The block is {@code zas:gas_fire}, which shows a hotspot (FIRE-018, DATA-009). */
    public static final int FLAG_GAS_FIRE = 1 << 6;

    /**
     * The block is a standing ignition source, such as a torch or lava (FIRE-014, FIRE-015): a
     * flammable zone in its cell or a neighbouring cell ignites.
     */
    public static final int FLAG_IGNITION_SOURCE = 1 << 7;

    private static final List<FaceMask> NO_MASKS =
            List.of(FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY, FaceMask.EMPTY);
    private static final List<FaceMask> ALL_MASKS =
            List.of(FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL, FaceMask.FULL);

    /** An air position: no coverage, one 1,000 L cell reaching every face. */
    public static final Geometry AIR = new Geometry(
            NO_MASKS, List.of(new Cell(FULL_VOLUME, ALL_MASKS)), 0, NO_MASKS, List.of(), Double.NaN, Double.NaN);

    /** A full block: every face covered, no cells, every line a wall layer. */
    public static final Geometry SOLID =
            new Geometry(ALL_MASKS, List.of(), 0, ALL_MASKS, List.of(), Double.NaN, Double.NaN);

    public Geometry {
        coverage = List.copyOf(coverage);
        cells = List.copyOf(cells);
        through = List.copyOf(through);
        partitions = List.copyOf(partitions);
        if (coverage.size() != 6) {
            throw new IllegalArgumentException("coverage needs 6 masks, got " + coverage.size());
        }
        if (through.size() != 6) {
            throw new IllegalArgumentException("through needs 6 masks, got " + through.size());
        }
        if (!Double.isNaN(transmittance) && !(transmittance > 0 && Double.isFinite(transmittance))) {
            throw new IllegalArgumentException("transmittance must be positive and finite, or NaN: " + transmittance);
        }
        if (!Double.isNaN(sourceTemperature) && !(sourceTemperature >= 0 && Double.isFinite(sourceTemperature))) {
            throw new IllegalArgumentException(
                    "source temperature must be finite and not negative, or NaN: " + sourceTemperature);
        }
        if (igniteOdds < 0) {
            throw new IllegalArgumentException("ignite odds must not be negative: " + igniteOdds);
        }
        if (!Double.isNaN(ignitionTemperature) && !(ignitionTemperature >= 0 && Double.isFinite(ignitionTemperature))) {
            throw new IllegalArgumentException(
                    "ignition temperature must be finite and not negative, or NaN: " + ignitionTemperature);
        }
    }

    /** A geometry that isn't flammable. */
    public Geometry(
            List<FaceMask> coverage,
            List<Cell> cells,
            int flags,
            List<FaceMask> through,
            List<Partition> partitions,
            double transmittance,
            double sourceTemperature) {
        this(coverage, cells, flags, through, partitions, transmittance, sourceTemperature, 0, Double.NaN);
    }

    /**
     * A geometry with no heat data of its own: its wall layers are every line if it has no cells
     * and none otherwise, it has no partitions, and it uses the fallback transmittance. {@link
     * GeometryDeriver} gives derived geometries their exact heat masks.
     */
    public Geometry(List<FaceMask> coverage, List<Cell> cells, int flags) {
        this(coverage, cells, flags, cells.isEmpty() ? ALL_MASKS : NO_MASKS, List.of(), Double.NaN, Double.NaN);
    }

    /**
     * A declared geometry, as a datapack override gives it (GEO-005): the listed faces are fully
     * covered, the others open, and the block is a single cell of {@code volume} litres reaching
     * every open face. With every face sealed there is no cell, since a cell must reach a face.
     * Heat crossing a sealed face reaches the cell behind it through the block's own layer.
     */
    public static Geometry declared(Set<Face> sealed, double volume, int flags) {
        if (!(volume >= 0 && volume <= FULL_VOLUME)) {
            throw new IllegalArgumentException("volume must be 0 to 1000 L, got " + volume);
        }
        List<FaceMask> cover = new ArrayList<>(6);
        List<FaceMask> footprint = new ArrayList<>(6);
        for (Face f : Face.values()) {
            boolean tight = sealed.contains(f);
            cover.add(tight ? FaceMask.FULL : FaceMask.EMPTY);
            footprint.add(tight ? FaceMask.EMPTY : FaceMask.FULL);
        }
        if (sealed.size() == 6) {
            return new Geometry(cover, List.of(), flags | FLAG_OVERRIDE, ALL_MASKS, List.of(), Double.NaN, Double.NaN);
        }
        // The single cell reaches every open face; behind every sealed face is the cell.
        Cell cell = new Cell(volume, footprint, cover);
        return new Geometry(cover, List.of(cell), flags | FLAG_OVERRIDE, NO_MASKS, List.of(), Double.NaN, Double.NaN);
    }

    /** The part of {@code face} covered by the collision shape. */
    public FaceMask coverage(Face face) {
        return coverage.get(face.ordinal());
    }

    /** The lines entering at {@code face} that cross the whole block and meet no cell. */
    public FaceMask through(Face face) {
        return through.get(face.ordinal());
    }

    /** Whether the block alone covers the whole of {@code face}. */
    public boolean isFaceFull(Face face) {
        return coverage(face).isFull();
    }

    public int cellCount() {
        return cells.size();
    }

    public Cell cell(int index) {
        return cells.get(index);
    }

    /** The total gas volume of all cells, in litres (GEO-002). */
    public double volume() {
        double v = 0;
        for (Cell c : cells) {
            v += c.volume();
        }
        return v;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    /** Whether the block is a heat source (HEAT-003). */
    public boolean isHeatSource() {
        return !Double.isNaN(sourceTemperature);
    }

    /**
     * The flag that makes {@code face} a zone boundary (GEO-007): gas crosses an opening there, but
     * the zones on either side stay apart (CORE-034). An opening between two positions is a zone
     * boundary when either block flags its side of the shared face.
     */
    public static int boundaryFlag(Face face) {
        return 1 << (BOUNDARY_SHIFT + face.ordinal());
    }

    /** Whether openings across {@code face} are zone boundaries (GEO-007). */
    public boolean isBoundary(Face face) {
        return (flags & boundaryFlag(face)) != 0;
    }

    /** This geometry with {@code flag} bits added. */
    public Geometry withFlags(int flag) {
        return (flags | flag) == flags
                ? this
                : new Geometry(
                        coverage,
                        cells,
                        flags | flag,
                        through,
                        partitions,
                        transmittance,
                        sourceTemperature,
                        igniteOdds,
                        ignitionTemperature);
    }

    /**
     * This geometry with heat data (HEAT-002, HEAT-003): a transmittance, or NaN for the fallback,
     * and a heat-source temperature, or NaN. A source gets {@link #FLAG_HEAT_SOURCE}.
     */
    public Geometry withThermal(double u, double source) {
        int f = Double.isNaN(source) ? flags & ~FLAG_HEAT_SOURCE : flags | FLAG_HEAT_SOURCE;
        return new Geometry(coverage, cells, f, through, partitions, u, source, igniteOdds, ignitionTemperature);
    }

    /** Whether the block can catch fire (FIRE-033, FIRE-034). */
    public boolean isFlammable() {
        return igniteOdds > 0;
    }

    /**
     * This geometry with fire data (FIRE-033, FIRE-034): vanilla ignite odds, or 0 if it isn't
     * flammable, and a temperature it catches fire at, or NaN for the configured one.
     */
    public Geometry withIgnition(int odds, double temperature) {
        return new Geometry(
                coverage, cells, flags, through, partitions, transmittance, sourceTemperature, odds, temperature);
    }

    /**
     * One cell: a connected region of free space reaching at least one face.
     *
     * @param volume gas volume in litres (GEO-002)
     * @param footprints six masks, indexed by {@link Face#ordinal()}: the part of each face this
     *     cell's free space touches. Empty for faces it doesn't reach. Never overlaps the coverage.
     * @param shields six masks, indexed by {@link Face#ordinal()}: the lines that, entering the
     *     block at that face, cross solid and then reach this cell first. Never overlaps the
     *     footprint.
     */
    public record Cell(double volume, List<FaceMask> footprints, List<FaceMask> shields) {
        public Cell {
            footprints = List.copyOf(footprints);
            shields = List.copyOf(shields);
            if (footprints.size() != 6) {
                throw new IllegalArgumentException("footprints needs 6 masks, got " + footprints.size());
            }
            if (shields.size() != 6) {
                throw new IllegalArgumentException("shields needs 6 masks, got " + shields.size());
            }
        }

        /** A cell with no shielded lines. */
        public Cell(double volume, List<FaceMask> footprints) {
            this(volume, footprints, NO_MASKS);
        }

        public FaceMask footprint(Face face) {
            return footprints.get(face.ordinal());
        }

        /** The lines entering at {@code face} that cross solid and then reach this cell first. */
        public FaceMask shield(Face face) {
            return shields.get(face.ordinal());
        }

        /** Whether this cell touches {@code face} at all. */
        public boolean reaches(Face face) {
            return !footprint(face).isEmpty();
        }
    }

    /**
     * Two cells of one block with solid between them (GEO-004, HEAT-013), such as the two sides of
     * a pane: {@code lines} lines of the 1/16 grid cross from one to the other through solid.
     *
     * @param first the lower cell index
     * @param second the higher cell index
     * @param lines the lines between them, each {@link #LINE_AREA} m²
     */
    public record Partition(int first, int second, int lines) {
        public Partition {
            if (first < 0 || second <= first || lines <= 0) {
                throw new IllegalArgumentException("bad partition " + first + ", " + second + ", " + lines);
            }
        }

        /** The area the two cells face each other across, in m². */
        public double area() {
            return lines * LINE_AREA;
        }
    }
}
