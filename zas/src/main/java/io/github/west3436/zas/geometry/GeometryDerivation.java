package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.hotspot.GasFireBlock;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Derives each block state's geometry from its collision shape (architecture §4.1, steps 1–3).
 *
 * <p>Shapes come from an empty collision context at the origin (GEO-041), never from render or
 * light occlusion. Geometry is derived once per distinct {@link VoxelShape} instance and
 * deduplicated into a palette, so each block state costs one derivation at most (GEO-040). The
 * shape-derived table doesn't depend on datapacks, so it is computed once per game session and
 * reused on every reload.
 *
 * <p>Fire blocks are flagged here too, since which blocks are fire doesn't depend on datapacks
 * either: vanilla fire ({@link BaseFireBlock}) with {@link Geometry#FLAG_FIRE} (FIRE-019) and
 * {@code zas:gas_fire} with {@link Geometry#FLAG_GAS_FIRE} (FIRE-018), so the engine's hotspots see
 * them come and go through the change hook (architecture §5). So is every other state that isn't
 * air but whose geometry is air's, with {@link Geometry#FLAG_OCCUPIED}: a gas fire can't go there.
 *
 * <p>So is a state holding a fluid, a source, flowing or waterlogged, which is gas-tight and holds no
 * gas whatever its shape (GEO-006).
 *
 * <p>So is a state whose shape may depend on its position or its data, with {@link
 * Geometry#FLAG_POSITIONAL}, so that {@link PositionGeometries} works it out per position (GEO-042,
 * GEO-043).
 */
public final class GeometryDerivation {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A position holding a fluid (GEO-006): as a full block, with {@link Geometry#FLAG_FLUID}. */
    private static final Geometry FLUID = Geometry.SOLID.withFlags(Geometry.FLAG_FLUID);

    /** Where a state's shape is compared with its shape at the origin (GEO-043). */
    private static final BlockPos[] PROBES = {
        new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(5, 64, -3), new BlockPos(-11, -7, 13)
    };

    /**
     * Debug: {@code -Dzas.geometry.profile=true} logs where the derivation's time goes, and once the
     * server has started, times it again warm, as at start and once per state without the cache
     * (docs/config.md).
     */
    static final boolean PROFILE = Boolean.getBoolean("zas.geometry.profile");

    private static volatile GeometryTable derived;

    private GeometryDerivation() {}

    /** The {@link Face} for a Minecraft direction. */
    public static Face face(Direction direction) {
        return Face.of(direction.get3DDataValue());
    }

    /** The collision shape the geometry is derived from (GEO-041). */
    public static VoxelShape collisionShape(BlockState state) {
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    /**
     * Rasterizes {@code shape} to 16³ voxels, clipped to the block. A voxel is solid when the
     * shape covers it; box edges are rounded to the nearest 1/16, which is exact for shapes on
     * the 1/16 grid.
     */
    public static BitSet rasterize(VoxelShape shape) {
        BitSet solid = new BitSet(GeometryDeriver.VOXELS);
        if (shape.isEmpty()) {
            return solid;
        }
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            int ax = grid(x0);
            int ay = grid(y0);
            int az = grid(z0);
            int bx = grid(x1);
            int by = grid(y1);
            int bz = grid(z1);
            if (ax < bx && ay < by && az < bz) {
                GeometryDeriver.fill(solid, ax, ay, az, bx, by, bz);
            }
        });
        return solid;
    }

    private static int grid(double coordinate) {
        return (int) Math.max(0, Math.min(GeometryDeriver.N, Math.round(coordinate * GeometryDeriver.N)));
    }

    /** The geometry of a collision shape. */
    public static Geometry derive(VoxelShape shape) {
        return GeometryDeriver.derive(rasterize(shape));
    }

    /**
     * The table derived from collision shapes alone, without overrides. Computed on first call
     * and then reused; safe to call from any thread once blocks are registered.
     */
    public static GeometryTable derived() {
        GeometryTable t = derived;
        if (t == null) {
            synchronized (GeometryDerivation.class) {
                t = derived;
                if (t == null) {
                    t = deriveAll();
                    derived = t;
                }
            }
        }
        return t;
    }

    /**
     * Whether a state's geometry is worked out per position (GEO-042, GEO-043): its block says its
     * shape is dynamic, as a block must if the shape depends on position or data, or its shape differs
     * from one position to another. Three vanilla kinds of dynamic block are left out, since nothing
     * about the position matters to their geometry: a shulker box only animates, and settles closed,
     * which is its shape at the origin, and scaffolding and powder snow only look at the entity, which
     * the empty context hasn't got.
     *
     * @param atOrigin the state's shape at the origin
     */
    static boolean positional(BlockState state, VoxelShape atOrigin) {
        Block block = state.getBlock();
        if (block instanceof ShulkerBoxBlock || block instanceof ScaffoldingBlock || block instanceof PowderSnowBlock) {
            return false;
        }
        if (block.hasDynamicShape()) {
            return true;
        }
        BitSet voxels = null;
        for (BlockPos probe : PROBES) {
            VoxelShape there = state.getCollisionShape(EmptyBlockGetter.INSTANCE, probe, CollisionContext.empty());
            if (there != atOrigin) {
                if (voxels == null) {
                    voxels = rasterize(atOrigin);
                }
                if (!rasterize(there).equals(voxels)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The hotspot flags of a state: {@link Geometry#FLAG_GAS_FIRE}, {@link Geometry#FLAG_FIRE} or none. */
    static int fireFlags(BlockState state) {
        if (state.getBlock() instanceof GasFireBlock) {
            return Geometry.FLAG_GAS_FIRE;
        }
        return state.getBlock() instanceof BaseFireBlock ? Geometry.FLAG_FIRE : 0;
    }

    /**
     * The geometry id for a state: {@code id}, or for a state that isn't air but has air's
     * geometry, that geometry with {@link Geometry#FLAG_OCCUPIED} (FIRE-018).
     */
    static int occupied(GeometryPalette.Builder palette, int id, BlockState state) {
        return id == GeometryPalette.AIR_ID && !state.isAir()
                ? palette.intern(Geometry.AIR.withFlags(Geometry.FLAG_OCCUPIED))
                : id;
    }

    private static GeometryTable deriveAll() {
        return deriveAll(true);
    }

    /**
     * Derives the table.
     *
     * @param report whether to log the result, and with {@link #PROFILE} where the time went; false
     *     for the profile's warm rerun
     */
    private static GeometryTable deriveAll(boolean report) {
        long start = System.nanoTime();
        int maxId = -1;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            maxId = Math.max(maxId, Block.getId(state));
        }
        int[] byStateId = new int[maxId + 1];
        GeometryPalette.Builder palette = GeometryPalette.builder();
        Map<VoxelShape, Integer> byShape = new IdentityHashMap<>();
        // Distinct shape instances often rasterize to the same voxels; derive each grid once.
        Map<BitSet, Integer> byVoxels = new HashMap<>();
        boolean profile = PROFILE && report;
        Profile p = profile ? new Profile() : null;
        int failures = 0;
        int perPosition = 0;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int id;
            boolean positional = false;
            long t0 = profile ? System.nanoTime() : 0;
            long t1 = t0;
            long t2 = t0;
            String step = "shape";
            try {
                VoxelShape shape = collisionShape(state);
                t1 = profile ? System.nanoTime() : 0;
                step = "derive";
                Integer known = byShape.get(shape);
                if (known == null) {
                    BitSet voxels = rasterize(shape);
                    known = byVoxels.computeIfAbsent(voxels, v -> palette.intern(GeometryDeriver.derive(v)));
                    byShape.put(shape, known);
                }
                id = known;
                t2 = profile ? System.nanoTime() : 0;
                step = "probe";
                positional = positional(state, shape);
            } catch (RuntimeException e) {
                // A broken modded shape mustn't stop the server; treat the block as solid.
                if (failures++ == 0 && report) {
                    LOGGER.warn("Couldn't derive the geometry of {}; treating it as solid", state, e);
                }
                if (profile) {
                    p.failed.merge(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " (" + step + ")", 1, Integer::sum);
                }
                id = GeometryPalette.SOLID_ID;
            }
            if (profile) {
                long t3 = System.nanoTime();
                p.fetchNs += t1 - t0;
                p.deriveNs += t2 - t1;
                p.probeNs += t3 - t2;
                if (state.getBlock().hasDynamicShape()) {
                    p.dynamic++;
                }
                if (positional) {
                    p.perPosition.merge(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace(), 1, Integer::sum);
                }
            }
            if (positional) {
                perPosition++;
            }
            int fire = fireFlags(state);
            if (fire != 0) {
                id = palette.intern(palette.geometry(id).withFlags(fire));
            }
            id = occupied(palette, id, state);
            if (positional) {
                id = palette.intern(palette.geometry(id).withFlags(Geometry.FLAG_POSITIONAL));
            }
            if (!state.getFluidState().isEmpty()) {
                // A fluid leaves no room for gas, whatever the block's shape (GEO-006). An override
                // comes later, so it has the last word unless it says fluid_seals.
                id = palette.intern(FLUID);
            }
            byStateId[Block.getId(state)] = id;
        }
        if (!report) {
            return new GeometryTable(palette.build(), byStateId, byShape.size(), 0, 0);
        }
        if (failures > 1) {
            LOGGER.warn("Couldn't derive the geometry of {} block states; treated them as solid", failures);
        }
        GeometryTable table = new GeometryTable(palette.build(), byStateId, byShape.size(), 0, 0);
        LOGGER.info(
                "Derived gas geometry for {} block states from {} distinct shapes: {} geometries in {} ms; {} worked"
                        + " out per position, {} failed",
                byStateId.length,
                byShape.size(),
                table.palette().size(),
                (System.nanoTime() - start) / 1_000_000,
                perPosition,
                failures);
        if (profile) {
            LOGGER.info(
                    "Gas geometry profile: shapes fetched in {} ms; {} voxel grids rasterized and derived into {}"
                            + " geometries in {} ms; positions probed in {} ms; {} states declare a dynamic shape;"
                            + " the table holds {} KiB of ids and per-position bits",
                    p.fetchNs / 1_000_000,
                    byVoxels.size(),
                    new HashSet<>(byVoxels.values()).size(),
                    p.deriveNs / 1_000_000,
                    p.probeNs / 1_000_000,
                    p.dynamic,
                    (4L * byStateId.length + (byStateId.length + 63) / 64 * 8L) / 1024);
            // A cell id names only so many cells per position (architecture O-11); the rest act as solid.
            int beyond = 0;
            for (int i = 0; i < table.palette().size(); i++) {
                if (table.palette().geometry(i).cells().size() > CellIds.MAX_SUB_CELLS) {
                    beyond++;
                }
            }
            Map<String, Integer> beyondStates = new HashMap<>();
            for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
                if (table.geometry(state).cells().size() > CellIds.MAX_SUB_CELLS) {
                    beyondStates.merge(
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace(), 1, Integer::sum);
                }
            }
            LOGGER.info(
                    "Gas geometry profile: {} geometries have more than the {} cells a cell id can name; their states, by"
                            + " namespace: {}",
                    beyond,
                    CellIds.MAX_SUB_CELLS,
                    sorted(beyondStates));
            LOGGER.info("Gas geometry profile: worked out per position, by namespace: {}", sorted(p.perPosition));
            LOGGER.info(
                    "Gas geometry profile: failed states, by block and step (shape, derive or probe): {}",
                    sorted(p.failed));
        }
        return table;
    }

    /** The profile's tallies for one derivation. */
    private static final class Profile {
        long fetchNs;
        long deriveNs;
        long probeNs;
        int dynamic;
        final Map<String, Integer> perPosition = new HashMap<>();
        final Map<String, Integer> failed = new HashMap<>();
    }

    private static String sorted(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * For {@link #PROFILE}: times the derivation again, now that the server is warm, as it runs at
     * start and once per state without the per-shape cache, and logs both. Any thread.
     */
    static void profileWarm() {
        long start = System.nanoTime();
        deriveAll(false);
        long cached = System.nanoTime() - start;
        start = System.nanoTime();
        int states = 0;
        int cells = 0;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            states++;
            try {
                cells += derive(collisionShape(state)).cells().size();
            } catch (RuntimeException e) {
                // Counted at start.
            }
        }
        long uncached = System.nanoTime() - start;
        LOGGER.info(
                "Gas geometry profile, warm: {} ms as derived at start; {} ms deriving each of {} states without"
                        + " the cache ({} cells)",
                cached / 1_000_000,
                uncached / 1_000_000,
                states,
                cells);
    }
}
