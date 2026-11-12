package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ambients a pack's server script provides (COMPAT-053), and the guard around a provider that
 * throws (NFR-013). The run's {@code ambient.js} registers providers for the End and {@code
 * ambient_throws.js} one for the Nether, as the server starts.
 *
 * <p>ZAS³ ships a datapack ambient for each vanilla dimension, and a datapack's beats every
 * provider. So, as the framework's own tests of providers do, each test here takes its dimension's
 * definition away for as long as it runs, which leaves a dimension whose ambient is the script's.
 * Each runs within one game tick and drives that dimension's atmos ticks itself.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AmbientTests {
    /** In the End's void, far from its island. */
    private static final BlockPos END = new BlockPos(-16384, 100, 16384);

    /** Above the Nether's roof, where it is open. */
    private static final BlockPos NETHER = new BlockPos(-16384, 200, 16384);

    private static final String ZAS = "io.github.west3436.zas.environment.ProvidedAmbients";
    private static final String FAULT = "Ambient provider zas_kubejs:nether_air threw; skipped for the atmos tick";

    private AmbientTests() {}

    /** Runs a body in a dimension with its datapack definition taken away, then puts it back. */
    static void bare(GameTestHelper h, ResourceKey<Level> dimension, BlockPos at, String mode, Consumer<Lab> body) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw new GameTestAssertException("no " + dimension.location());
        }
        Ambients.overrideDefinitionForTest(dimension, DimensionAmbient.NONE);
        try (Lab lab = new Lab(h, level, at)) {
            body.accept(lab);
        } finally {
            Probe.mode(mode, "");
            Ambients.overrideDefinitionForTest(dimension, null);
            // The definition applies again from the next atmos tick.
            new Lab(h, level, at).tick();
        }
    }

    private static AtmosphereSample open(Lab lab) {
        AtmosphereSample sample = Atmospheres.at(lab.level, lab.origin);
        lab.h.assertValueEqual(sample.state(), AtmosphereState.AMBIENT, "the state in the open");
        return sample;
    }

    /**
     * A script's provider is registered for the dimension, under the id and with the priority the
     * script gave, and what it answers is the dimension's open air from the next atmos tick: an
     * {@code AmbientDefinition} it made, a plain object with the definition's two fields, or a
     * vacuum. Answering null leaves the dimension to the next source, and a provider of a lower
     * priority is never asked.
     */
    @GameTest(template = "empty")
    public static void compat_053_a_script_provides_a_dimensions_ambient(GameTestHelper h) {
        bare(h, Level.END, END, "ambient", lab -> {
            List<AmbientProviders.Registration> registered = AmbientProviders.registered(Level.END);
            h.assertValueEqual(
                    registered.stream().map(r -> r.id() + " " + r.priority()).toList(),
                    List.of("zas_kubejs:end_air 5", "zas_kubejs:end_fallback 0"),
                    "the End's providers, first the one that applies");

            int asked = Probe.asked("end_air");
            lab.tick();
            AtmosphereSample made = open(lab);
            lab.close(made.temperature(), 280, 1e-9, "the temperature of a definition the script made");
            lab.close(made.mixture().partialPressure(Lab.O2), 18, 1e-9, "its oxygen");
            lab.close(made.mixture().partialPressure(Lab.N2), 60, 1e-9, "its nitrogen");
            lab.close(made.pressure(), 78, 1e-9, "its pressure");

            Probe.mode("ambient", "object");
            lab.tick();
            AtmosphereSample object = open(lab);
            lab.close(object.temperature(), 250, 1e-9, "the temperature of a plain object");
            lab.close(object.mixture().partialPressure(Lab.O2), 30, 1e-9, "its oxygen");
            lab.close(object.mixture().partialPressure(Lab.N2), 20, 1e-9, "its nitrogen");

            Probe.mode("ambient", "vacuum");
            lab.tick();
            AtmosphereSample vacuum = open(lab);
            lab.close(vacuum.pressure(), 0, 0, "a vacuum's pressure");
            lab.close(vacuum.temperature(), 120, 1e-9, "its temperature");

            Probe.mode("ambient", "nothing");
            lab.tick();
            AtmosphereSample nothing = open(lab);
            lab.close(nothing.pressure(), StandardAir.PRESSURE, 1e-6, "the pressure once the script answers null");
            lab.close(nothing.temperature(), StandardAir.TEMPERATURE, 1e-9, "its temperature");

            h.assertValueEqual(Probe.asked("end_air") - asked, 4, "times it was asked in 4 atmos ticks");
            h.assertValueEqual(Probe.asked("end_fallback"), 0, "times the one of lower priority was asked");
        });
        h.succeed();
    }

    /**
     * A script's provider that throws is contained as a mod's is: it is skipped for that atmos
     * tick, its last good answer stands, and it is asked again at the next. The fault is logged
     * twice, each at most once a minute: by ZAS³ under the provider's id, and in KubeJS's console
     * for server scripts with the script's file and line. A provider that answers nothing at all
     * is a fault too, and its answers count again once it stops.
     */
    @GameTest(template = "empty")
    public static void nfr_013_a_script_ambient_provider_that_throws_is_contained(GameTestHelper h) {
        bare(h, Level.NETHER, NETHER, "nether", lab -> {
            lab.tick();
            lab.close(open(lab).pressure(), 0, 0, "the pressure before it throws");
            lab.close(open(lab).temperature(), 737, 1e-9, "the temperature before it throws");

            try (FaultLog log = new FaultLog()) {
                FaultLog.aMinutePasses();
                Probe.mode("nether", "throw");
                int asked = Probe.asked("nether_air");
                lab.ticks(3);
                h.assertValueEqual(Probe.asked("nether_air") - asked, 3, "times it was asked in 3 atmos ticks");
                lab.close(open(lab).pressure(), 0, 0, "the pressure while it throws");
                lab.close(open(lab).temperature(), 737, 1e-9, "the temperature while it throws");
                h.assertValueEqual(log.by(ZAS, FAULT).size(), 1, "errors ZAS³ logged: " + log.all());
                List<String> console = log.by(FaultLog.KUBEJS, FAULT);
                h.assertValueEqual(console.size(), 1, "errors in KubeJS's console: " + log.all());
                h.assertTrue(
                        console.get(0).contains("ambient_throws.js#")
                                && console.get(0).contains("the ambient script is broken"),
                        "the console's line doesn't name the script and what it threw: " + console.get(0));

                FaultLog.aMinutePasses();
                Probe.mode("nether", "forgot");
                lab.tick();
                lab.close(open(lab).temperature(), 737, 1e-9, "the temperature while it answers nothing at all");
                h.assertValueEqual(log.by(ZAS, FAULT).size(), 2, "errors ZAS³ logged a minute later: " + log.all());
                h.assertValueEqual(
                        log.by(FaultLog.KUBEJS, FAULT).size(),
                        2,
                        "errors in KubeJS's console a minute later: " + log.all());

                Probe.mode("nether", "hot");
                lab.tick();
                lab.close(open(lab).temperature(), 900, 1e-9, "the temperature once it stops throwing");
                h.assertValueEqual(log.by(ZAS, FAULT).size(), 2, "errors ZAS³ logged once it stops: " + log.all());
            }
        });
        h.succeed();
    }
}
