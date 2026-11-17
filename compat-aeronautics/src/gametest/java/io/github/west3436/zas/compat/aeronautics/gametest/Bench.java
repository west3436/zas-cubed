package io.github.west3436.zas.compat.aeronautics.gametest;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.compat.aeronautics.AeroSettings;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * What the lift tests share: Sable's pressure at a point, ambients to put in a dimension's place,
 * the arithmetic the tests check against, and putting everything they change back.
 *
 * <p>Every test changes the server's dimensions, so each runs in a batch of its own and starts and
 * ends with {@link #reset}.
 */
final class Bench {
    /** The provider id a test registers on the Nether. */
    static final ResourceLocation PROVIDER = ResourceLocation.fromNamespaceAndPath("zas", "aeronautics_gametest");

    static final String N2 = "zas:nitrogen";
    static final String O2 = "zas:oxygen";
    static final String CO2 = "zas:carbon_dioxide";

    private Bench() {}

    /** Gives every dimension back its own ambient, the settings their defaults, and Sable its own data. */
    static void reset() {
        for (ResourceKey<Level> dimension : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            Ambients.overrideForTest(dimension, null);
            Ambients.overrideDefinitionForTest(dimension, null);
            AmbientProviders.unregister(PROVIDER, dimension);
        }
        AeroSettings.FOLLOW_AMBIENT.set(AeroSettings.DEFAULT_FOLLOW_AMBIENT);
        AeroSettings.SEED_FROM_DIMENSION_PHYSICS.set(AeroSettings.DEFAULT_SEED_FROM_DIMENSION_PHYSICS);
        ZasServerConfig.PRESET.set(AtmospherePreset.NORMAL);
        // The test server has no data pack of Sable's, so the only entries are a test's.
        sableEntries().clear();
    }

    /**
     * Sable's table of {@code dimension_physics} entries, which its data pack loader fills, opened
     * for the tests: the oldest Sable the adapter supports has no public way to add one. A test
     * puts in what a data pack would.
     */
    @SuppressWarnings("unchecked")
    static Map<ResourceKey<Level>, DimensionPhysics> sableEntries() {
        try {
            Field table = DimensionPhysicsData.class.getDeclaredField("DIMENSION_PHYSICS_DATA");
            table.setAccessible(true);
            return (Map<ResourceKey<Level>, DimensionPhysics>) table.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sable's dimension_physics table isn't where the tests look", e);
        }
    }

    /** What a data pack entry with a {@code base_pressure} and nothing else of its own is. */
    static DimensionPhysics physics(ResourceKey<Level> dimension, double basePressure) {
        return new DimensionPhysics(
                dimension.location(),
                1000,
                Optional.empty(),
                Optional.empty(),
                Optional.of(basePressure),
                Optional.empty(),
                Optional.empty(),
                false);
    }

    /** Sable's air pressure at a height in a level, as Aeronautics reads it. */
    static double pressure(ServerLevel level, double y) {
        return DimensionPhysicsData.getAirPressure(level, new Vector3d(0.5, y, 0.5));
    }

    /** An ambient of partial pressures in kPa at a temperature. */
    static Ambient ambient(Map<String, Double> partialPressures, double kelvin) {
        return new Ambient(new TreeMap<>(partialPressures), kelvin);
    }

    /** Air of 79 % N₂ and 21 % O₂ at a total pressure in kPa and a temperature. */
    static Ambient air(double kPa, double kelvin) {
        return ambient(Map.of(N2, kPa * 0.79, O2, kPa * 0.21), kelvin);
    }

    /**
     * ρ / ρ₀ worked out here, from the running server's molar masses, for the tests to check the
     * adapter's against.
     */
    static double expected(Map<String, Double> partialPressures, double kelvin) {
        return density(partialPressures, kelvin) / density(Ambient.STANDARD_AIR.partialPressures(), 293.15);
    }

    private static double density(Map<String, Double> partialPressures, double kelvin) {
        double sum = 0;
        for (Map.Entry<String, Double> gas : partialPressures.entrySet()) {
            GasDefinition definition =
                    ServerGasRegistry.current().find(gas.getKey()).orElseThrow();
            sum += gas.getValue() * definition.molarMass();
        }
        return sum / (GasMixture.GAS_CONSTANT * kelvin);
    }

    static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }
}
