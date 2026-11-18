package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import com.lightning.northstar.world.temperature.NorthstarTemperature;
import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The areas Northstar's own oxygen sealers keep, as ZAS³'s breathing rules take them, as Ad Astra's
 * distributors are in Cooperative mode. Under the Normal preset, outside zones, living things in a
 * sealer's area, on a planet where Northstar finds no oxygen in the open, are in standard air at
 * the temperature Northstar gives the spot: its temperature regulators', or the planet's. Under
 * Hardcore only a ZAS³ zone is breathable. A sealer adds no gas: atmosphere queries still report
 * the planet's open air there.
 */
final class SealerAreas {
    /** The id the adapter's provider is registered under. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "northstar_sealers");

    private SealerAreas() {}

    /** Registers the areas with ZAS³'s breathing rules. */
    static void register() {
        LocalAtmospheres.register(ID, SealerAreas::air);
    }

    /** The air at a point outside zones: a Northstar sealer's under the Normal preset, or null. */
    static @Nullable GasMixture air(ServerLevel level, Vec3 point) {
        if (Atmospheres.preset() == AtmospherePreset.HARDCORE) {
            return null;
        }
        NorthstarOxygen oxygen = NorthstarOxygen.getDimension(level);
        if (oxygen.hasOxygen()) {
            return null;
        }
        NorthstarOxygen.Provider sealer = oxygen.getSealer(point);
        if (sealer == null || sealer instanceof ZoneSealer) {
            return null;
        }
        double celsius = NorthstarTemperature.getTemperature(level, point);
        double kelvin = Double.isFinite(celsius)
                ? Math.max(celsius + NorthstarPlanets.ZERO_CELSIUS, AmbientDefinition.COSMIC_BACKGROUND)
                : StandardAir.TEMPERATURE;
        return standardAir(kelvin);
    }

    /** One cell of standard air, 101.325 kPa of 79 % N₂ and 21 % O₂, at a temperature. */
    static GasMixture standardAir(double kelvin) {
        double moles = StandardAir.PRESSURE * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * kelvin);
        return new GasMixture(
                Map.of(
                        StandardAir.NITROGEN, moles * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN, moles * StandardAir.OXYGEN_FRACTION),
                kelvin,
                StandardAir.CELL_VOLUME);
    }
}
