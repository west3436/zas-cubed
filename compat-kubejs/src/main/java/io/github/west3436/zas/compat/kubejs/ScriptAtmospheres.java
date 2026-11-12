package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a server script sees as {@code Atmospheres} (COMPAT-051): the API's {@link Atmospheres},
 * call for call, behind one change that scripts need.
 *
 * <p>The API has {@code at} and {@code zoneAt} twice each, for a block position and for a point.
 * KubeJS turns anything into either of those, so its script engine can't tell the two apart and
 * refuses the call as ambiguous, whatever the script passes. Here each is one method that looks at
 * what it was given: a block position asks about that block, and a point ({@code Vec3d}, or {@code
 * [x, y, z]}) about that point. Every other call goes straight through.
 */
public final class ScriptAtmospheres {
    static final ScriptAtmospheres INSTANCE = new ScriptAtmospheres();

    private ScriptAtmospheres() {}

    /**
     * The atmosphere at a block or at a point: {@link Atmospheres#at(Level, BlockPos)} or {@link
     * Atmospheres#at(Level, Vec3)}.
     *
     * @param level the level
     * @param where a block position, a {@code Vec3d}, or a point as {@code [x, y, z]}
     * @return the sample
     * @throws IllegalArgumentException if {@code where} is none of those
     */
    public AtmosphereSample at(Level level, Object where) {
        return where instanceof BlockPos pos ? Atmospheres.at(level, pos) : Atmospheres.at(level, point("at", where));
    }

    /**
     * The zone at a block or at a point: {@link Atmospheres#zoneAt(Level, BlockPos)} or {@link
     * Atmospheres#zoneAt(Level, Vec3)}.
     *
     * @param level the level
     * @param where a block position, a {@code Vec3d}, or a point as {@code [x, y, z]}
     * @return the zone, or empty where there is none
     * @throws IllegalArgumentException if {@code where} is none of those
     */
    public Optional<ZoneInfo> zoneAt(Level level, Object where) {
        return where instanceof BlockPos pos
                ? Atmospheres.zoneAt(level, pos)
                : Atmospheres.zoneAt(level, point("zoneAt", where));
    }

    /**
     * {@link Atmospheres#of(Entity)}.
     *
     * @param entity the entity
     * @return the atmosphere at its eyes
     */
    public AtmosphereSample of(Entity entity) {
        return Atmospheres.of(entity);
    }

    /**
     * {@link Atmospheres#zone(Level, ZoneId)}.
     *
     * @param level the level
     * @param id the zone's id
     * @return the zone, or empty if there is none with that id
     */
    public Optional<ZoneInfo> zone(Level level, ZoneId id) {
        return Atmospheres.zone(level, id);
    }

    /**
     * {@link Atmospheres#zoneTouches(Level, BlockPos)}.
     *
     * @param level the level
     * @param pos the block's position
     * @return true if a zone touches the block, or the atmosphere there isn't resolved
     */
    public boolean zoneTouches(Level level, BlockPos pos) {
        return Atmospheres.zoneTouches(level, pos);
    }

    /**
     * {@link Atmospheres#periodTicks()}.
     *
     * @return the game ticks between atmos ticks
     */
    public int periodTicks() {
        return Atmospheres.periodTicks();
    }

    /**
     * {@link Atmospheres#containerExplosionsBreakBlocks()}.
     *
     * @return whether a bursting container breaks blocks
     */
    public boolean containerExplosionsBreakBlocks() {
        return Atmospheres.containerExplosionsBreakBlocks();
    }

    /**
     * {@link Atmospheres#nearVacuumDensity()}.
     *
     * @return the density below which a gas counts as a near vacuum, in mol/L
     */
    public double nearVacuumDensity() {
        return Atmospheres.nearVacuumDensity();
    }

    /**
     * {@link Atmospheres#preset()}.
     *
     * @return the server's preset
     */
    public AtmospherePreset preset() {
        return Atmospheres.preset();
    }

    /** A point from what a script wrote for one. */
    private static Vec3 point(String call, Object where) {
        if (where instanceof Vec3 point) {
            return point;
        }
        if (where instanceof List<?> list
                && list.size() == 3
                && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y
                && list.get(2) instanceof Number z) {
            return new Vec3(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        throw new IllegalArgumentException(
                "Atmospheres." + call + " takes a block position, a Vec3d or [x, y, z], not " + where);
    }
}
