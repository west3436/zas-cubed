package io.github.west3436.zas.api.atmosphere;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Atmosphere and zone queries (API-010, API-011, API-015).
 *
 * <p><b>Threads:</b> every query may be called from any thread. Queries read
 * the published, immutable snapshot of the last applied atmos tick and return
 * immutable values that never change afterwards (API-046). A query from a
 * thread other than the server's may therefore see a result one atmos tick
 * older than the server thread would.
 *
 * <p><b>Point resolution</b> (API-015, D-44): a position can hold several
 * cells (a glass pane divides its block into two). A query at an exact point
 * uses the cell that contains it, or the nearest cell if the point is inside
 * the block's collision shape. A query at a block position uses that
 * position's largest cell. A query about an entity uses its eye position.
 *
 * <p>Without ZAS³ installed, every point is {@link AtmosphereState#AMBIENT}
 * standard air and no zone exists (D-17).
 */
public final class Atmospheres {
    private Atmospheres() {}

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
