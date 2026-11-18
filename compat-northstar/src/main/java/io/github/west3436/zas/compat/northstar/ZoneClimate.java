package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.world.temperature.NorthstarTemperature;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * ZAS³'s zones as one of Northstar's temperature regulators, for a level. Where a zone holds gas,
 * Northstar's blocks see the zone's temperature rather than the planet's: water in a warm room on
 * the Moon stays water, and ice in it melts. What the cold or heat does to entities is ZAS³'s alone
 * (D-51), so Northstar's own check on them is stopped ({@code TemperatureCheckMixin}).
 *
 * <p>Northstar asks a regulator whether a point is its own and then, straight away and with no
 * point, its temperature, so the temperature found by the first question is kept for the second.
 */
final class ZoneClimate implements NorthstarTemperature.Provider {
    private final ServerLevel level;

    /** The temperature of the zone last found, in °C. */
    private final ThreadLocal<Float> lastFound = new ThreadLocal<>();

    ZoneClimate(ServerLevel level) {
        this.level = level;
    }

    ServerLevel level() {
        return level;
    }

    @Override
    public boolean isSealed(Vec3 pos) {
        return found(Zones.at(level, pos, Zones::zoned));
    }

    @Override
    public boolean isSealed(Vec3i pos) {
        Zones.Found found = Zones.at(level, pos, Zones::zoned);
        return found(found == null ? null : found.sample());
    }

    private boolean found(AtmosphereSample sample) {
        if (sample == null) {
            return false;
        }
        lastFound.set((float) (sample.temperature() - NorthstarPlanets.ZERO_CELSIUS));
        return true;
    }

    /** The temperature of the zone the last {@code isSealed} found, in °C. */
    @Override
    public float getTemperature() {
        Float celsius = lastFound.get();
        return celsius == null ? Float.NaN : celsius;
    }
}
