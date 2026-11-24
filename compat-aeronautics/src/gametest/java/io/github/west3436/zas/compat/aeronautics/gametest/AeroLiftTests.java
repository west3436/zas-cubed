package io.github.west3436.zas.compat.aeronautics.gametest;

import static io.github.west3436.zas.compat.aeronautics.gametest.Bench.CO2;
import static io.github.west3436.zas.compat.aeronautics.gametest.Bench.close;

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.compat.aeronautics.AeroSettings;
import io.github.west3436.zas.compat.aeronautics.LiftFactors;
import io.github.west3436.zas.compat.aeronautics.LiftFactorsPayload;
import io.github.west3436.zas.compat.aeronautics.SableSeed;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Lift follows the ambient (COMPAT-080 to COMPAT-083), read where Aeronautics reads it: Sable's
 * {@code DimensionPhysicsData.getAirPressure}, through the adapter's hook (COMPAT-003). Each test
 * changes the server's dimensions, so each is a batch of its own.
 */
@GameTestHolder(AeroGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AeroLiftTests {
    /** Heights Sable's curve gives different pressures at, below the Nether's ceiling. */
    private static final double LOW = 40;

    private static final double HIGH = 80;

    private AeroLiftTests() {}

    private static ServerLevel level(GameTestHelper h, ResourceKey<Level> dimension) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        h.assertTrue(level != null, dimension.location() + " isn't loaded");
        return level;
    }

    /** Sable's own pressure at both heights in a dimension, read with the factor off. */
    private static double[] own(GameTestHelper h, ResourceKey<Level> dimension) {
        h.assertValueEqual(LiftFactors.server(dimension), 1.0, "the factor in " + dimension.location() + " when off");
        ServerLevel level = level(h, dimension);
        double[] own = {Bench.pressure(level, LOW), Bench.pressure(level, HIGH)};
        h.assertTrue(own[0] > 0 && own[1] > 0, "Sable gives no pressure in " + dimension.location());
        h.assertTrue(own[0] != own[1], "Sable gives one pressure at both heights in " + dimension.location());
        return own;
    }

    /** Checks the pressure at both heights is Sable's own times a factor. */
    private static void scaled(
            GameTestHelper h, ResourceKey<Level> dimension, double[] own, double factor, String what) {
        close(h, LiftFactors.server(dimension), factor, 1e-9, what + ": the server's factor");
        ServerLevel level = level(h, dimension);
        close(h, Bench.pressure(level, LOW), own[0] * factor, 1e-9, what + ": the pressure at y " + LOW);
        close(h, Bench.pressure(level, HIGH), own[1] * factor, 1e-9, what + ": the pressure at y " + HIGH);
    }

    /**
     * The pressure Aeronautics reads is Sable's times ρ / ρ₀: 1 in standard air, 0 in a vacuum,
     * about 0.013 in 0.6 kPa of CO₂ at 210 K, a half in air at half the pressure or twice the
     * temperature. The factor is the dimension's, the same at every height, so Sable's altitude
     * curve keeps its shape.
     */
    @GameTest(template = "empty", batch = "compat_080_density", timeoutTicks = 200)
    public static void compat_080_lift_follows_the_ambients_density(GameTestHelper h) {
        ResourceKey<Level> overworld = Level.OVERWORLD;
        double[][] own = new double[1][];
        h.startSequence()
                .thenExecute(() -> {
                    Bench.reset();
                    AeroSettings.FOLLOW_AMBIENT.set(false);
                })
                .thenExecuteAfter(2, () -> {
                    own[0] = own(h, overworld);
                    AeroSettings.FOLLOW_AMBIENT.set(true);
                })
                .thenExecuteAfter(2, () -> {
                    // The Overworld is standard air at 293.15 K: lift there is unchanged.
                    scaled(h, overworld, own[0], 1.0, "the Overworld");
                    Ambients.overrideForTest(overworld, Ambient.VACUUM);
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, overworld, own[0], 0.0, "a vacuum");
                    Ambients.overrideForTest(overworld, Bench.ambient(Map.of(CO2, 0.6), 210));
                })
                .thenExecuteAfter(2, () -> {
                    double mars = Bench.expected(Map.of(CO2, 0.6), 210);
                    close(h, mars, 0.013, 0.0005, "the spec's 0.6 kPa of CO2 at 210 K");
                    scaled(h, overworld, own[0], mars, "0.6 kPa of CO2 at 210 K");
                    Ambients.overrideForTest(overworld, Bench.air(101.325 / 2, 293.15));
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, overworld, own[0], 0.5, "air at half the pressure");
                    Ambients.overrideForTest(overworld, Bench.air(101.325, 586.3));
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, overworld, own[0], 0.5, "air at twice the temperature");
                    Bench.reset();
                })
                .thenSucceed();
    }

    /**
     * Each dimension follows its own ambient, in the variant of the preset in force. Under Normal
     * the Overworld and the End are standard air and the Nether is air at 313.15 K; under Hardcore
     * the End is 40 kPa at 263.15 K and the Nether has CO₂ at 340 K. Switching the preset changes
     * them at once.
     */
    @GameTest(template = "empty", batch = "compat_080_presets", timeoutTicks = 200)
    public static void compat_080_each_dimension_follows_its_ambient_in_the_presets_variant(GameTestHelper h) {
        Map<ResourceKey<Level>, double[]> own = new HashMap<>();
        h.startSequence()
                .thenExecute(() -> {
                    Bench.reset();
                    AeroSettings.FOLLOW_AMBIENT.set(false);
                })
                .thenExecuteAfter(2, () -> {
                    for (ResourceKey<Level> d : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
                        own.put(d, own(h, d));
                    }
                    AeroSettings.FOLLOW_AMBIENT.set(true);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertValueEqual(
                            Atmospheres.preset(),
                            io.github.west3436.zas.api.atmosphere.AtmospherePreset.NORMAL,
                            "the preset");
                    scaled(h, Level.OVERWORLD, own.get(Level.OVERWORLD), 1.0, "the Overworld under Normal");
                    scaled(h, Level.END, own.get(Level.END), 1.0, "the End under Normal");
                    scaled(h, Level.NETHER, own.get(Level.NETHER), 293.15 / 313.15, "the Nether under Normal");
                    AtmospherePreset.switchTo(AtmospherePreset.HARDCORE);
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, Level.OVERWORLD, own.get(Level.OVERWORLD), 1.0, "the Overworld under Hardcore");
                    double end = Bench.expected(Map.of(Bench.N2, 31.6, Bench.O2, 8.4), 263.15);
                    close(h, end, (40 / 101.325) * (293.15 / 263.15), 1e-9, "the End's factor worked out");
                    scaled(h, Level.END, own.get(Level.END), end, "the End under Hardcore");
                    double nether = Bench.expected(Map.of(CO2, 12.0, Bench.N2, 71.325, Bench.O2, 18.0), 340);
                    scaled(h, Level.NETHER, own.get(Level.NETHER), nether, "the Nether under Hardcore");
                    Bench.reset();
                })
                .thenSucceed();
    }

    /**
     * A provider registered through the public API changes the Nether's ambient at its next atmos
     * tick. The server's factor, the pressure its Sable reads, and the table sent to clients change
     * in the same server tick as the ambient, within one atmos tick of it. A client that takes the
     * table, through its wire form, holds the same factor.
     */
    @GameTest(template = "empty", batch = "compat_081_prompt", timeoutTicks = 300)
    public static void compat_081_a_changed_ambient_reaches_the_server_and_clients_within_an_atmos_tick(
            GameTestHelper h) {
        ResourceKey<Level> nether = Level.NETHER;
        MinecraftServer server = h.getLevel().getServer();
        AmbientDefinition thin = new AmbientDefinition(Map.of(ResourceLocation.parse(CO2), 0.6), 210);
        double factor = Bench.expected(Map.of(CO2, 0.6), 210);
        double[][] own = new double[1][];
        // Game times: when the provider was registered, when the ambient changed, when the server's
        // factor did, and when the table carrying it was sent.
        long[] at = {-1, -1, -1, -1};
        h.startSequence()
                .thenExecute(() -> {
                    Bench.reset();
                    // The Nether without ZAS³'s definition, so a provider decides it (ENV-014).
                    Ambients.overrideDefinitionForTest(nether, DimensionAmbient.NONE);
                    AeroSettings.FOLLOW_AMBIENT.set(false);
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    own[0] = own(h, nether);
                    AeroSettings.FOLLOW_AMBIENT.set(true);
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, nether, own[0], 1.0, "the Nether as standard air");
                    AmbientProviders.register(Bench.PROVIDER, nether, level -> thin);
                    at[0] = server.overworld().getGameTime();
                })
                .thenExecuteFor(Atmospheres.periodTicks() * 2 + 5, () -> {
                    long now = server.overworld().getGameTime();
                    if (at[1] < 0 && Ambients.ambient(nether).temperature() == 210) {
                        at[1] = now;
                    }
                    if (at[2] < 0 && Math.abs(LiftFactors.server(nether) - factor) < 1e-12) {
                        at[2] = now;
                    }
                    LiftFactors.Sent sent = LiftFactors.lastSent();
                    if (at[3] < 0
                            && sent != null
                            && Math.abs(sent.payload().factors().getOrDefault(nether.location(), 1.0) - factor)
                                    < 1e-12) {
                        at[3] = sent.gameTime();
                    }
                })
                .thenExecute(() -> {
                    h.assertTrue(at[1] >= 0, "the provider's answer never became the Nether's ambient");
                    h.assertTrue(at[2] >= 0, "the server's factor never changed");
                    h.assertTrue(at[3] >= 0, "no table with the new factor was sent");
                    int period = Atmospheres.periodTicks();
                    h.assertTrue(
                            at[2] - at[1] <= period && at[3] - at[1] <= period,
                            "the factor followed the ambient (changed at " + at[1] + ") on the server at " + at[2]
                                    + " and was sent at " + at[3] + ": more than " + period + " ticks");
                    scaled(h, nether, own[0], factor, "the Nether from the provider");

                    // The client's side: the table through its wire form, then into the client's table.
                    LiftFactorsPayload sent = LiftFactors.lastSent().payload();
                    ByteBuf buf = Unpooled.buffer();
                    LiftFactorsPayload.STREAM_CODEC.encode(buf, sent);
                    LiftFactorsPayload received = LiftFactorsPayload.STREAM_CODEC.decode(buf);
                    h.assertValueEqual(received, sent, "the table after its wire form");
                    LiftFactors.receiveForTest(received);
                    close(h, LiftFactors.client(nether.location()), factor, 1e-12, "the client's factor");
                    close(h, LiftFactors.client(Level.OVERWORLD.location()), 1.0, 0, "the client's Overworld");
                    AmbientProviders.unregister(Bench.PROVIDER, nether);
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    // And back to the fallback, standard air, once the provider is gone.
                    scaled(h, nether, own[0], 1.0, "the Nether without the provider");
                    LiftFactors.receiveForTest(LiftFactors.lastSent().payload());
                    close(h, LiftFactors.client(nether.location()), 1.0, 0, "the client's Nether after");
                    Bench.reset();
                })
                .thenSucceed();
    }

    /**
     * Where Sable's own {@code dimension_physics} seeded the ambient, the factor is 1: Sable's
     * pressure already holds its {@code base_pressure}. A Nether that Sable's data gives a
     * {@code base_pressure} of 0.5, and that ZAS³ defines no ambient for, is seeded with air at half
     * of 101.325 kPa, and its pressure is Sable's own. The same air from a provider halves it. With
     * {@code base_pressure} 0 the seed is a vacuum.
     */
    @GameTest(template = "empty", batch = "compat_082_seeded", timeoutTicks = 300)
    public static void compat_082_an_ambient_seeded_from_sables_data_leaves_its_pressure_alone(GameTestHelper h) {
        ResourceKey<Level> nether = Level.NETHER;
        double[][] own = new double[1][];
        h.startSequence()
                .thenExecute(() -> {
                    Bench.reset();
                    Ambients.overrideDefinitionForTest(nether, DimensionAmbient.NONE);
                    ServerLevel level = level(h, nether);
                    h.assertTrue(
                            DimensionPhysicsData.of(level) == DimensionPhysicsData.getDefault(level),
                            "Sable has data for the Nether before the test gives it some");
                    Bench.sableEntries().put(nether, Bench.physics(nether, 0.5));
                    AeroSettings.FOLLOW_AMBIENT.set(false);
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    own[0] = own(h, nether);
                    AeroSettings.FOLLOW_AMBIENT.set(true);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertValueEqual(Ambients.seedOf(nether), Optional.of(SableSeed.ID), "the seed in force");
                    Ambient seeded = Ambients.ambient(nether);
                    close(h, seeded.pressure(), 101.325 / 2, 1e-9, "the seeded pressure, kPa");
                    close(h, seeded.partialPressure(Bench.O2), 101.325 / 2 * 0.21, 1e-9, "the seeded O2, kPa");
                    close(h, seeded.temperature(), 293.15, 0, "the seeded temperature, K");
                    scaled(h, nether, own[0], 1.0, "the Nether seeded from Sable");

                    // The same air from a provider isn't Sable's, so it counts.
                    AmbientProviders.register(Bench.PROVIDER, nether, level -> SableSeed.ambient(0.5));
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    h.assertValueEqual(Ambients.seedOf(nether), Optional.empty(), "the seed in force with a provider");
                    scaled(h, nether, own[0], 0.5, "the same air from a provider");
                    AmbientProviders.unregister(Bench.PROVIDER, nether);
                    Bench.sableEntries().put(nether, Bench.physics(nether, 0.0));
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    Ambient vacuum = Ambients.ambient(nether);
                    h.assertTrue(vacuum.isVacuum(), "a base_pressure of 0 seeded " + vacuum);
                    h.assertValueEqual(LiftFactors.server(nether), 1.0, "the factor in the seeded vacuum");
                    close(h, Bench.pressure(level(h, nether), LOW), 0, 0, "the pressure in the seeded vacuum");
                    // With the seed off, the Nether falls back to standard air.
                    AeroSettings.SEED_FROM_DIMENSION_PHYSICS.set(false);
                })
                .thenExecuteAfter(Atmospheres.periodTicks() + 2, () -> {
                    h.assertValueEqual(Ambients.seedOf(nether), Optional.empty(), "the seed in force when off");
                    h.assertValueEqual(Ambients.ambient(nether), Ambient.STANDARD_AIR, "the Nether's fallback");
                    Bench.reset();
                })
                .thenSucceed();
    }

    /**
     * With {@code [lift] follow_ambient} off, Sable's pressure is its own everywhere, a vacuum
     * included, and clients are sent 1. Turned on again, the factor is back within the tick.
     */
    @GameTest(template = "empty", batch = "compat_083_setting", timeoutTicks = 200)
    public static void compat_083_the_lift_setting_turns_the_factor_off(GameTestHelper h) {
        ResourceKey<Level> overworld = Level.OVERWORLD;
        double[][] own = new double[1][];
        h.startSequence()
                .thenExecute(() -> {
                    Bench.reset();
                    h.assertTrue(AeroSettings.followAmbient(), "the setting is off by default");
                })
                .thenExecuteAfter(2, () -> {
                    // Standard air: the factor is 1 either way, so this is Sable's own.
                    own[0] = own(h, overworld);
                    AeroSettings.FOLLOW_AMBIENT.set(false);
                    Ambients.overrideForTest(overworld, Ambient.VACUUM);
                })
                .thenExecuteAfter(2, () -> {
                    scaled(h, overworld, own[0], 1.0, "a vacuum with the setting off");
                    h.assertValueEqual(
                            LiftFactors.lastSent().payload().factors().get(overworld.location()),
                            1.0,
                            "the Overworld's factor sent with the setting off");
                    AeroSettings.FOLLOW_AMBIENT.set(true);
                })
                .thenExecuteAfter(1, () -> {
                    scaled(h, overworld, own[0], 0.0, "a vacuum with the setting on");
                    h.assertValueEqual(
                            LiftFactors.lastSent().payload().factors().get(overworld.location()),
                            0.0,
                            "the Overworld's factor sent with the setting on");
                    Bench.reset();
                })
                .thenSucceed();
    }
}
