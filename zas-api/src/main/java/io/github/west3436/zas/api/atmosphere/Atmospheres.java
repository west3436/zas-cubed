package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Atmosphere and zone queries (API-010, API-011, API-015), the atmos
 * period (API-052), the near-vacuum density (API-064), the preset and
 * whether a zone touches a block.
 *
 * <p><b>Threads:</b> every query may be called from any thread. Queries read
 * the published, immutable snapshot of the last applied atmos tick and return
 * immutable values that never change afterwards (API-046). A query from a
 * thread other than the server's may therefore see a result one atmos tick
 * older than the server thread would.
 *
 * <p><b>Point resolution</b> (API-015, D-44): a position can hold several
 * cells (a glass pane divides its block into two). A query at an exact point
 * uses the cell that contains it, or the block's nearest cell if the point is
 * inside the block's collision shape. A point inside a block with no cells, a
 * full block such as stone, which holds no gas, uses the nearest cell next to
 * it: of the six adjacent blocks, the one whose shared face is closest to the
 * point that has a cell. A point buried with no cell next to it is reported as
 * {@link AtmosphereState#AMBIENT} (or {@link AtmosphereState#UNRESOLVED}, in
 * an unloaded chunk), with no zone. A query at a block position uses that
 * position's largest cell, the first of equals. A query about an entity uses
 * its eye position. Mutations at a point resolve it in the same way.
 *
 * <p>Without ZAS³ installed, every point is {@link AtmosphereState#AMBIENT}
 * standard air and no zone exists (D-17).
 */
public final class Atmospheres {
    /** CORE-041's default atmos period: 20 game ticks, 1 s. */
    public static final int DEFAULT_PERIOD_TICKS = 20;

    /**
     * HEAT-008's default near-vacuum density, in mol/L: 1 % of standard air's molar density,
     * 101.325 kPa at 293.15 K (ENV-011).
     */
    public static final double DEFAULT_NEAR_VACUUM_DENSITY =
            0.01 * (StandardAir.PRESSURE / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE));

    private Atmospheres() {}

    /**
     * The atmos tick period, in game ticks (CORE-041, API-052): how often ZAS³ steps its
     * simulation and applies queued mutations. A mod that steps its own devices in time with the
     * atmosphere reads it here. Every rate in ZAS³ is per second, so a different period changes
     * how often changes land, not how fast things happen.
     *
     * <p>It is the server's configured period ({@code period_ticks}), which can change when the
     * server's config reloads; without ZAS³, {@link #DEFAULT_PERIOD_TICKS}.
     *
     * <p>Thread: any.
     *
     * @return the period in game ticks, at least 2
     */
    @AnyThread
    public static int periodTicks() {
        int ticks = ZasApi.provider().atmosPeriodTicks();
        return ticks >= 2 ? ticks : DEFAULT_PERIOD_TICKS;
    }

    /**
     * Whether explosions of gas containers, such as tanks and canisters, break blocks (API-059,
     * CONT-107, OPS-007): true under the Hardcore preset and false under Normal, unless the server
     * overrides it. They hurt entities either way. Ask each time, since a preset change or a config
     * edit applies from the next read. Without ZAS³, false.
     *
     * <p>Thread: any.
     *
     * @return true if a container's explosion should break blocks
     */
    @AnyThread
    public static boolean containerExplosionsBreakBlocks() {
        return ZasApi.provider().containerExplosionsBreakBlocks();
    }

    /**
     * The molar density below which gas is too thin to carry heat (API-064, HEAT-008), in mol/L: a
     * zone with fewer moles than this times its volume neither conducts nor radiates. A mod that
     * moves heat between its own gas and a zone, or the open air, can treat gas that thin the same
     * way. It is the server's {@code near_vacuum_percent} of standard air's density, so ask each
     * time, since a config edit applies from the next read. Without ZAS³, {@link
     * #DEFAULT_NEAR_VACUUM_DENSITY}.
     *
     * <p>Thread: any.
     *
     * @return the density in mol/L, 0 or more
     */
    @AnyThread
    public static double nearVacuumDensity() {
        double density = ZasApi.provider().nearVacuumDensity();
        return density >= 0 && Double.isFinite(density) ? density : DEFAULT_NEAR_VACUUM_DENSITY;
    }

    /**
     * The server's atmosphere difficulty preset (OPS-007, D-14). A mod reads it to give an option of
     * its own a default that follows the preset, as the mode of ZAS³'s Ad Astra adapter does
     * (SPACE-004).
     *
     * <p>A server can set each of ZAS³'s own preset options by itself, so the preset doesn't say
     * what any of them is: where the API has the option, such as {@link
     * #containerExplosionsBreakBlocks()}, ask for it instead. Ask each time, since a preset change
     * applies from the next read.
     *
     * <p>Without ZAS³, {@link AtmospherePreset#NORMAL}. On a client it is the preset of the server
     * the client is on, once the server's config has reached it, and Normal before.
     *
     * <p>Thread: any.
     *
     * @return the preset
     */
    @AnyThread
    public static AtmospherePreset preset() {
        AtmospherePreset preset = ZasApi.provider().preset();
        return preset != null ? preset : AtmospherePreset.NORMAL;
    }

    /**
     * Whether a zone touches a block: whether the block's position, or one of the six positions
     * beside it, holds a cell of a zone. It is for a mod that changes blocks by its own rules of
     * the open air, such as freezing water or killing plants on an airless planet, and should leave
     * alone what a zone's air reaches (SPACE-014). That includes blocks that hold no gas themselves:
     * a water source in a room, or the floor under it.
     *
     * <p>It is also true where the atmosphere isn't resolved ({@link AtmosphereState#UNRESOLVED}),
     * such as in a chunk whose saved zones are still being read back, so that such a mod doesn't
     * act on a room in the moments before ZAS³ knows it again.
     *
     * <p>It is a lookup in the last applied atmos tick (API-046) that builds no gas mixture, so it
     * suits a call made once per block. Without ZAS³, and on a client, it is false: no zone exists
     * there.
     *
     * <p>Thread: any.
     *
     * @param level the level, server or client side
     * @param pos the block's position
     * @return true if a zone touches the block, or the atmosphere there isn't resolved
     */
    @AnyThread
    public static boolean zoneTouches(Level level, BlockPos pos) {
        return ZasApi.provider()
                .zoneTouches(Objects.requireNonNull(level, "level"), Objects.requireNonNull(pos, "pos"));
    }

    /**
     * Whether a block position has room for gas: whether the block there leaves any of its space
     * to the atmosphere. A full block, such as stone or glass, leaves none; air, a slab, a pane and
     * a torch do, and so does a block that a datapack's geometry override opens, as leaves are.
     *
     * <p>{@link #at(Level, BlockPos)} reads the ambient both in the open air and inside a full
     * block, and a mutation at a point inside a full block acts on the nearest cell beside it
     * (API-015), which may be round a corner or through a wall. A device that releases gas through
     * one of its faces asks this of the position in front of that face, and holds its gas while the
     * answer is false, as the exhaust port of ZAS³'s Mekanism adapter does (MEK-022).
     *
     * <p>It reads the block as it is now, not as of the last atmos tick, and builds no mixture. It
     * is true wherever the block can't be read: in a chunk that isn't loaded, where {@link #at}
     * says {@link AtmosphereState#UNRESOLVED}; on a client; and without ZAS³, where every position
     * reads as standard air.
     *
     * <p>Thread: any.
     *
     * @param level the level, server or client side
     * @param pos the block's position
     * @return false if the block there is known to leave no room for gas
     */
    @AnyThread
    public static boolean holdsGas(Level level, BlockPos pos) {
        return ZasApi.provider().holdsGas(Objects.requireNonNull(level, "level"), Objects.requireNonNull(pos, "pos"));
    }

    /**
     * The atmosphere at an exact point.
     *
     * <p>Thread: any.
     *
     * @param level the level, server or client side
     * @param point the point
     * @return the sample at the cell containing the point, or the nearest cell
     */
    @AnyThread
    public static AtmosphereSample at(Level level, Vec3 point) {
        return ZasApi.provider().sample(Objects.requireNonNull(level, "level"), Objects.requireNonNull(point, "point"));
    }

    /**
     * The atmosphere at a block position.
     *
     * <p>Thread: any.
     *
     * @param level the level, server or client side
     * @param pos the block position
     * @return the sample at the position's largest cell
     */
    @AnyThread
    public static AtmosphereSample at(Level level, BlockPos pos) {
        return ZasApi.provider().sample(Objects.requireNonNull(level, "level"), Objects.requireNonNull(pos, "pos"));
    }

    /**
     * The atmosphere an entity is in, at its eye position.
     *
     * <p>Thread: any, though reading a live entity's position off the server
     * thread may see a stale or torn position.
     *
     * @param entity the entity
     * @return the sample at the entity's eye position
     */
    @AnyThread
    public static AtmosphereSample of(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return at(entity.level(), entity.getEyePosition());
    }

    /**
     * A zone by id.
     *
     * <p>Thread: any.
     *
     * @param level the level the zone is in
     * @param id the zone's id
     * @return the zone, or empty if no zone has that id in that level
     */
    @AnyThread
    public static Optional<ZoneInfo> zone(Level level, ZoneId id) {
        return ZasApi.provider().zone(Objects.requireNonNull(level, "level"), Objects.requireNonNull(id, "id"));
    }

    /**
     * The zone containing an exact point.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param point the point, resolved as in {@link #at(Level, Vec3)}
     * @return the zone, or empty if the point is ambient or unresolved
     */
    @AnyThread
    public static Optional<ZoneInfo> zoneAt(Level level, Vec3 point) {
        return at(level, point).zone().flatMap(id -> zone(level, id));
    }

    /**
     * The zone containing a block position.
     *
     * <p>Thread: any.
     *
     * @param level the level
     * @param pos the block position, resolved as in {@link #at(Level, BlockPos)}
     * @return the zone, or empty if the position is ambient or unresolved
     */
    @AnyThread
    public static Optional<ZoneInfo> zoneAt(Level level, BlockPos pos) {
        return at(level, pos).zone().flatMap(id -> zone(level, id));
    }
}
