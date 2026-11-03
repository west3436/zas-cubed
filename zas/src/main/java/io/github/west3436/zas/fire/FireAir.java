package io.github.west3436.zas.fire;

import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasConstants;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The air a fire burns in (FIRE-030, FIRE-020): the O₂ at a point, from the published zone holding
 * it, or outside zones from the dimension's ambient (CORE-050), so a fire in a vacuum dimension
 * can't burn. A point in an unresolved region reads as the ambient, its last-known state
 * (GEO-035).
 *
 * <p>The point is resolved as the API resolves one (API-015): a point inside a full block, such as
 * a furnace, uses the nearest open cell next to it, so {@link #front} gives the point just inside a
 * block's face for the cell in front of it.
 *
 * <p>Server thread. On a client, where there is no simulation, every point sustains fire.
 */
public final class FireAir {
    private FireAir() {}

    /**
     * The air at one point.
     *
     * @param zone the zone holding it, or null outside zones
     * @param pos the block of the cell it resolved to
     * @param sub that cell's index in its block, or -1 for its largest
     * @param oxygenFraction the O₂ mole fraction
     * @param oxygenKpa the O₂ partial pressure, kPa
     */
    public record Air(@Nullable ZoneView zone, BlockPos pos, int sub, double oxygenFraction, double oxygenKpa) {
        /** Whether the air meets a fire oxygen threshold (FIRE-030). */
        public boolean sustains(FireSettings fire) {
            return fire.sustains(oxygenFraction, oxygenKpa);
        }

        /** Whether the point is in a zone that isn't paused, where burning changes the gas. */
        public boolean zoned() {
            return zone != null && !zone.paused();
        }
    }

    /** The air at a point. */
    public static Air at(ServerLevel level, Vec3 point) {
        PointResolver.Cell cell = PointResolver.resolve(level, point);
        AtmosService service = AtmosService.current();
        DimensionAtmos d = service == null ? null : service.dimension(level);
        AtmosSnapshot table = d == null ? null : d.published();
        ZoneView zone = table == null ? null : table.zoneAt(cell.pos().asLong(), cell.sub());
        if (zone != null) {
            int o2 = table.registry().indexOf(FireSettings.OXYGEN);
            double total = zone.totalMoles();
            if (o2 < 0 || !(total > 0) || !(zone.volume() > 0)) {
                return new Air(zone, cell.pos(), cell.sub(), 0, 0);
            }
            double n = zone.moles(o2);
            return new Air(
                    zone, cell.pos(), cell.sub(), n / total, n * GasConstants.R * zone.temperature() / zone.volume());
        }
        Ambient ambient = Ambients.ambient(level.dimension());
        double pressure = ambient.pressure();
        double o2 = ambient.partialPressure(Ambient.OXYGEN);
        return new Air(null, cell.pos(), cell.sub(), pressure > 0 ? o2 / pressure : 0, o2);
    }

    /** Whether fire can burn at a point under the config's threshold; true on a client. */
    public static boolean sustains(Level level, Vec3 point) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        return at(server, point).sustains(FireConfig.settings());
    }

    /** Whether fire can burn in a block's largest cell, or in front of a full block; true on a client. */
    public static boolean sustains(Level level, BlockPos pos) {
        return sustains(level, Vec3.atCenterOf(pos));
    }

    /**
     * The point just inside a block's face on {@code side}: it resolves to the block's own cell
     * nearest that face, or, for a full block, to the cell beyond the face.
     */
    public static Vec3 front(BlockPos pos, Direction side) {
        return Vec3.atCenterOf(pos).add(side.getStepX() * 0.49, side.getStepY() * 0.49, side.getStepZ() * 0.49);
    }
}
