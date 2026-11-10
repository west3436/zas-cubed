package io.github.west3436.zas.compat.adastra;

import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.api.planets.PlanetApi;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Ambients seeded from Ad Astra's planets (SPACE-020): a dimension in Ad Astra's {@code planets}
 * data that no datapack and no provider gives an ambient is standard air at the planet's
 * temperature if the planet has oxygen, and a vacuum at that temperature if it doesn't. The same
 * under both presets.
 *
 * <p>Ad Astra's data is read each atmos tick, so a reload counts from the next one. A dimension
 * that was a planet at the last read and isn't at this one keeps its ambient for that one read:
 * in single player Ad Astra's client rewrites the planet list the server reads, from its own
 * thread, and a read in between finds it empty. One atmos tick of standard air on the Moon would
 * let air into every breached room there.
 */
final class Planets {
    /** The id the adapter's seed is registered under. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "ad_astra_planets");

    /** 0 °C in kelvin. */
    static final double ZERO_CELSIUS = 273.15;

    /** What each planet's dimension was last seeded with. Server thread. */
    private static final Map<ResourceKey<Level>, Seeded> SEEDED = new HashMap<>();

    private Planets() {}

    /** A dimension's last answer, the planet values it came from, and whether the read since found no planet. */
    private static final class Seeded {
        final boolean oxygen;
        final short celsius;
        final AmbientDefinition ambient;
        boolean missed;

        Seeded(boolean oxygen, short celsius) {
            this.oxygen = oxygen;
            this.celsius = celsius;
            this.ambient = ambient(oxygen, celsius);
        }
    }

    /** Registers the seed. Once, at common setup. */
    static void register() {
        AmbientSeeds.register(ID, Planets::seed);
    }

    /** Forgets every answer: the next server starts from its own data. */
    static void serverStopped() {
        SEEDED.clear();
    }

    /** The seed: a planet's ambient, or null for a dimension that isn't one of Ad Astra's planets. */
    static @Nullable AmbientDefinition seed(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        Planet planet = PlanetApi.API.getPlanet(dimension);
        Seeded last = SEEDED.get(dimension);
        if (planet == null) {
            if (last == null) {
                return null;
            }
            if (last.missed) {
                SEEDED.remove(dimension);
                return null;
            }
            last.missed = true;
            return last.ambient;
        }
        if (last == null || last.oxygen != planet.oxygen() || last.celsius != planet.temperature()) {
            last = new Seeded(planet.oxygen(), planet.temperature());
            SEEDED.put(dimension, last);
        }
        last.missed = false;
        return last.ambient;
    }

    /**
     * The ambient a planet's two values give: standard air or vacuum, at its temperature in kelvin.
     * A temperature at or below absolute zero reads as the cosmic background's, the coldest an
     * ambient goes.
     */
    static AmbientDefinition ambient(boolean oxygen, short celsius) {
        double kelvin = Math.max(celsius + ZERO_CELSIUS, AmbientDefinition.COSMIC_BACKGROUND);
        return oxygen ? AmbientDefinition.standardAir(kelvin) : AmbientDefinition.vacuum(kelvin);
    }
}
