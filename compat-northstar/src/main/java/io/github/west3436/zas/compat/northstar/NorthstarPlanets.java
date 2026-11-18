package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.accessor.NorthstarLevel;
import com.lightning.northstar.planet.data.Atmosphere;
import com.lightning.northstar.planet.data.AtmosphereFluid;
import com.lightning.northstar.planet.data.PlanetDimension;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

/**
 * Ambients seeded from Northstar's {@code planet_dimension} data (SPACE-020's Northstar part): a
 * dimension Northstar's data describes, and that no ZAS³ data pack and no provider gives an
 * ambient (D-45), is seeded with the atmosphere Northstar gives it, at its average temperature.
 * The same under both presets.
 *
 * <ul>
 *   <li>No atmosphere, or no pressure: a vacuum (ENV-003).
 *   <li>Otherwise the atmosphere's pressure, in equal shares to the fluids it lists, since
 *       Northstar's data gives no fractions. A share of a fluid in {@code #c:oxygen} or {@code
 *       #northstar:breathable} is standard air's mix, which is what Northstar means by an
 *       atmosphere with oxygen; {@code #c:carbon}, Northstar's carbon dioxide, is CO₂; and {@code
 *       #c:hydrogen}, {@code #c:methane} and {@code #c:nitrogen} are those gases. A fluid of none of
 *       these counts as nitrogen, so its pressure is still there.
 * </ul>
 *
 * <p>The temperature is the middle of Northstar's average temperature, in °C, which is what its
 * telescope shows: −183 °C on the Moon seeds 90.15 K. A dimension's ambient is one temperature, so
 * a planet whose temperature Northstar varies by place or time of day, as Mercury's, gets its
 * average. Northstar's data is read each atmos tick, and a reload counts from the next one.
 */
final class NorthstarPlanets {
    /** The id the seed is registered under. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "northstar_planets");

    /** 0 °C in kelvin. */
    static final double ZERO_CELSIUS = 273.15;

    static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    static final ResourceLocation CH4 = ResourceLocation.fromNamespaceAndPath("zas", "methane");

    /** The fluid tags a listed fluid is read by, in this order, and the gas each stands for. */
    private static final Map<TagKey<Fluid>, ResourceLocation> GASES = new LinkedHashMap<>();

    /** Standard air, which a breathable fluid stands for, as the marker {@link #share} returns. */
    private static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath("zas", "standard_air");

    static {
        GASES.put(fluidTag("c", "oxygen"), AIR);
        GASES.put(fluidTag("northstar", "breathable"), AIR);
        GASES.put(fluidTag("c", "carbon"), CO2);
        GASES.put(fluidTag("c", "hydrogen"), H2);
        GASES.put(fluidTag("c", "methane"), CH4);
        GASES.put(fluidTag("c", "nitrogen"), StandardAir.NITROGEN);
    }

    /** What each dimension was last seeded with, and the data it came from. Server thread. */
    private static final Map<ResourceKey<Level>, Seeded> SEEDED = new HashMap<>();

    private record Seeded(Atmosphere atmosphere, float celsius, AmbientDefinition ambient) {}

    private NorthstarPlanets() {}

    /** Forgets every answer: the next server starts from its own data. */
    static void serverStopped() {
        SEEDED.clear();
    }

    /** The seed: a planet dimension's ambient, or null for a dimension Northstar has no data for. */
    static @Nullable AmbientDefinition seed(ServerLevel level) {
        PlanetDimension dimension = NorthstarLevel.SERVER_TRACKER.getDimensionByLevel(level);
        if (dimension == null) {
            SEEDED.remove(level.dimension());
            return null;
        }
        Atmosphere atmosphere = dimension.atmosphere();
        float celsius = averageCelsius(dimension.averageTemperature());
        Seeded last = SEEDED.get(level.dimension());
        if (last == null || !last.atmosphere.equals(atmosphere) || last.celsius != celsius) {
            last = new Seeded(atmosphere, celsius, ambient(gasesOf(atmosphere), atmosphere.pressure(), celsius));
            SEEDED.put(level.dimension(), last);
        }
        return last.ambient;
    }

    /** The middle of an average temperature range, in °C. */
    static float averageCelsius(Vector2f range) {
        return (range.x + range.y) / 2;
    }

    /** What each fluid an atmosphere lists stands for, in its order. */
    private static List<ResourceLocation> gasesOf(Atmosphere atmosphere) {
        List<ResourceLocation> gases = new ArrayList<>();
        for (AtmosphereFluid fluid : atmosphere.composition()) {
            gases.add(share(fluid.fluid()));
        }
        return gases;
    }

    /** The gas a listed fluid stands for, or {@link #AIR} for one that is breathable. */
    private static ResourceLocation share(Fluid fluid) {
        var holder = BuiltInRegistries.FLUID.wrapAsHolder(fluid);
        for (Map.Entry<TagKey<Fluid>, ResourceLocation> e : GASES.entrySet()) {
            if (holder.is(e.getKey())) {
                return e.getValue();
            }
        }
        return StandardAir.NITROGEN;
    }

    /**
     * The ambient a planet's atmosphere gives.
     *
     * @param shares what each listed fluid stands for: {@link #AIR} for standard air's mix, or a gas
     * @param pascals the atmosphere's pressure, in Pa
     * @param celsius its temperature, in °C
     * @return a vacuum if nothing is listed or there is no pressure, else the pressure in equal
     *     shares, at the temperature in kelvin; a temperature at or below absolute zero reads as the
     *     cosmic background's, the coldest an ambient goes
     */
    static AmbientDefinition ambient(List<ResourceLocation> shares, double pascals, double celsius) {
        double kelvin = Math.max(celsius + ZERO_CELSIUS, AmbientDefinition.COSMIC_BACKGROUND);
        double kPa = pascals / 1000;
        if (shares.isEmpty() || !(kPa > 0) || !Double.isFinite(kPa)) {
            return AmbientDefinition.vacuum(kelvin);
        }
        double each = kPa / shares.size();
        Map<ResourceLocation, Double> pressures = new HashMap<>();
        for (ResourceLocation gas : shares) {
            if (Objects.equals(gas, AIR)) {
                pressures.merge(StandardAir.NITROGEN, each * StandardAir.NITROGEN_FRACTION, Double::sum);
                pressures.merge(StandardAir.OXYGEN, each * StandardAir.OXYGEN_FRACTION, Double::sum);
            } else {
                pressures.merge(gas, each, Double::sum);
            }
        }
        return new AmbientDefinition(pressures, kelvin);
    }

    /** {@link #AIR}, for tests of {@link #ambient}. */
    static ResourceLocation air() {
        return AIR;
    }

    private static TagKey<Fluid> fluidTag(String namespace, String path) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
