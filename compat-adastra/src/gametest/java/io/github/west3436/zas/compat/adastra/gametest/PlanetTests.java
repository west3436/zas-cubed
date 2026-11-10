package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.api.planets.PlanetApi;
import earth.terrarium.adastra.api.systems.OxygenApi;
import earth.terrarium.adastra.common.planets.AdAstraData;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.config.AtmospherePreset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ambients seeded from Ad Astra's planets (SPACE-020, S-06): a planet with oxygen is standard air
 * and one without is a vacuum, each at the planet's temperature, wherever no datapack and no
 * provider says otherwise. The planets are Ad Astra's own files, read through its API.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class PlanetTests {
    /** The id the adapter registers its seed under. */
    private static final ResourceLocation SEED = ResourceLocation.fromNamespaceAndPath("zas", "ad_astra_planets");

    private static final ResourceLocation TEST_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("zas", "adastra_gametest");

    private PlanetTests() {}

    /** A planet's open air, as the public API reports it. */
    private static AtmosphereSample open(Lab lab) {
        AtmosphereSample open = lab.sample(0, 20, 0);
        lab.h.assertValueEqual(open.state(), AtmosphereState.AMBIENT, "the open air's state on " + name(lab));
        return open;
    }

    private static String name(Lab lab) {
        return lab.level.dimension().location().toString();
    }

    /** Checks Ad Astra's own data for a planet is what the test takes it to be. */
    private static void adAstraSays(GameTestHelper h, ResourceKey<Level> key, boolean oxygen, int celsius) {
        Planet planet = PlanetApi.API.getPlanet(key);
        h.assertTrue(planet != null, key.location() + " isn't one of Ad Astra's planets");
        h.assertValueEqual(planet.oxygen(), oxygen, "Ad Astra's oxygen for " + key.location());
        h.assertValueEqual((int) planet.temperature(), celsius, "Ad Astra's °C for " + key.location());
    }

    private static void vacuumAt(GameTestHelper h, AtmosphereSample open, double kelvin, String where) {
        h.assertTrue(open.mixture().isEmpty(), where + " should be a vacuum, and is " + open);
        h.assertValueEqual(open.pressure(), 0.0, "the pressure on " + where);
        close(h, open.temperature(), kelvin, "the temperature on " + where + ", K");
    }

    private static void standardAirAt(GameTestHelper h, AtmosphereSample open, double kelvin, String where) {
        close(h, open.pressure(), StandardAir.PRESSURE, "the pressure on " + where + ", kPa");
        Map<ResourceLocation, Double> parts = open.partialPressures();
        h.assertValueEqual(parts.keySet(), Set.of(Lab.N2, Lab.O2), "the gases on " + where);
        close(h, parts.get(Lab.O2), StandardAir.PRESSURE * 0.21, "the O2 on " + where + ", kPa");
        close(h, parts.get(Lab.N2), StandardAir.PRESSURE * 0.79, "the N2 on " + where + ", kPa");
        close(h, open.temperature(), kelvin, "the temperature on " + where + ", K");
    }

    /**
     * A planet without oxygen is a vacuum at its temperature, converted from °C: the Moon at −173 °C
     * is 100.15 K, Mars at −65 °C is 208.15 K and Venus at 464 °C is 737.15 K. Nothing but the
     * adapter's seed says so: no datapack and no provider defines them.
     */
    @GameTest(template = "empty")
    public static void space_020_an_airless_planet_is_a_vacuum_at_its_temperature(GameTestHelper h) {
        h.assertTrue(
                AmbientSeeds.registered().stream().anyMatch(r -> r.id().equals(SEED)),
                "the adapter's seed isn't registered: " + AmbientSeeds.registered());
        record Airless(ResourceKey<Level> key, int celsius, double kelvin) {}
        for (Airless planet : List.of(
                new Airless(Planet.MOON, -173, 100.15),
                new Airless(Planet.MARS, -65, 208.15),
                new Airless(Planet.VENUS, 464, 737.15))) {
            adAstraSays(h, planet.key(), false, planet.celsius());
            h.assertTrue(
                    AmbientProviders.registered(planet.key()).isEmpty(),
                    "a provider defines " + planet.key().location());
            try (Lab lab = Lab.onPlanet(h, planet.key(), 60)) {
                vacuumAt(h, open(lab), planet.kelvin(), name(lab));
                // The simulation has it too: a room built there forms holding nothing.
                lab.room(0, Map.of());
                lab.ticks(2);
                h.assertValueEqual(lab.sample(2, 2, 2).pressure(), 0.0, "the pressure in a room on " + name(lab));
            }
        }
        h.succeed();
    }

    /**
     * A planet with oxygen is standard air at its temperature: Glacio at −20 °C is 101.325 kPa of
     * 79 % N₂ and 21 % O₂ at 253.15 K. A pig breathes there and nothing harms it, and Ad Astra's
     * check agrees.
     */
    @GameTest(template = "empty")
    public static void space_020_an_oxygen_planet_is_standard_air_at_its_temperature(GameTestHelper h) {
        adAstraSays(h, Planet.GLACIO, true, -20);
        try (Lab lab = Lab.onPlanet(h, Planet.GLACIO, 61)) {
            standardAirAt(h, open(lab), 253.15, name(lab));

            Pig pig = lab.mob(EntityType.PIG, lab.open(10));
            Breathability verdict = Breathing.of(pig);
            h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the verdict on Glacio");
            h.assertTrue(verdict.isProtected(), "hazards on Glacio: " + verdict.hazards());
            h.assertTrue(OxygenApi.API.hasOxygen(pig), "Ad Astra's check found no oxygen on Glacio");
            Lab.check(pig, 30);
            h.assertValueEqual(Lab.dealt(pig), List.of(), "damage dealt on Glacio");
        }
        h.succeed();
    }

    /** Seeded ambients are the same under both presets: the Moon's vacuum and Glacio's air. */
    @GameTest(template = "empty")
    public static void space_020_seeded_ambients_are_the_same_under_both_presets(GameTestHelper h) {
        try (Lab moon = Lab.onTheMoon(h, 62);
                Lab glacio = Lab.onPlanet(h, Planet.GLACIO, 62)) {
            for (AtmospherePreset preset : AtmospherePreset.values()) {
                moon.preset(preset);
                moon.ticks(2);
                glacio.ticks(2);
                vacuumAt(h, open(moon), 100.15, "the Moon under " + preset);
                standardAirAt(h, open(glacio), 253.15, "Glacio under " + preset);
            }
        }
        h.succeed();
    }

    /**
     * A datapack definition and a provider each come before the planet's data. The Overworld is Ad
     * Astra's Earth at 15 °C, and stays at the 293.15 K its own definition gives it. Glacio with a
     * provider registered is what the provider says, and goes back to the planet's air without it.
     */
    @GameTest(template = "empty")
    public static void space_020_a_datapack_definition_and_a_provider_come_before_the_planet(GameTestHelper h) {
        adAstraSays(h, Level.OVERWORLD, true, 15);
        try (Lab earth = Lab.onTheOverworld(h, 63);
                Lab glacio = Lab.onPlanet(h, Planet.GLACIO, 63)) {
            earth.ticks(2);
            close(h, open(earth).temperature(), StandardAir.TEMPERATURE, "the Overworld's temperature, K");

            AmbientProviders.register(TEST_PROVIDER, Planet.GLACIO, asked -> AmbientDefinition.vacuum(50));
            try {
                glacio.tick();
                vacuumAt(h, open(glacio), 50, "Glacio with a provider");
            } finally {
                AmbientProviders.unregister(TEST_PROVIDER, Planet.GLACIO);
            }
            glacio.tick();
            standardAirAt(h, open(glacio), 253.15, "Glacio without the provider");
        }
        h.succeed();
    }

    /**
     * A planet missing from Ad Astra's data for one read keeps its ambient, and one missing for two
     * is no planet any more. In single player Ad Astra's client empties and refills the planet list
     * the server reads, from its own thread; the test empties it the same way, through Ad Astra's own
     * class, which the adapter itself never names. A breached room on the Moon stays empty through
     * the one missed read.
     */
    @GameTest(template = "empty")
    public static void space_020_a_planet_missing_for_one_read_keeps_its_ambient(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 64)) {
            lab.room(0, Map.of());
            lab.set(2, 4, 2, Blocks.AIR.defaultBlockState());
            lab.ticks(3);
            h.assertValueEqual(lab.sample(2, 2, 2).pressure(), 0.0, "the pressure in the breached room");

            Map<ResourceKey<Level>, Planet> planets = Map.copyOf(AdAstraData.planets());
            try {
                AdAstraData.setPlanets(Map.of());
                h.assertTrue(PlanetApi.API.getPlanet(Planet.MOON) == null, "Ad Astra still knows the Moon");
                lab.tick();
                vacuumAt(h, open(lab), 100.15, "the Moon, missing for one read");

                AdAstraData.setPlanets(planets);
                lab.ticks(3);
                vacuumAt(h, open(lab), 100.15, "the Moon, back again");
                h.assertValueEqual(lab.sample(2, 2, 2).pressure(), 0.0, "the pressure in the breached room, after");

                AdAstraData.setPlanets(Map.of());
                lab.ticks(2);
                standardAirAt(h, open(lab), StandardAir.TEMPERATURE, "the Moon, missing for two reads");
            } finally {
                AdAstraData.setPlanets(planets);
            }
            lab.tick();
            vacuumAt(h, open(lab), 100.15, "the Moon, once Ad Astra knows it again");
        }
        h.succeed();
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-6 * Math.abs(expected), what + ": " + actual + ", not " + expected);
    }
}
