package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProvider;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Ambient providers (ENV-004), which source applies (ENV-014), when providers run (ENV-015), and
 * what happens when two claim a dimension (ENV-019, ENV-020).
 *
 * <p>The GameTest server has only the vanilla dimensions, and zas ships a datapack definition for
 * each. So these tests borrow the End and take its definition away ({@link #onBareEnd}), which leaves
 * it a level whose ambient comes from whatever the test registers. Each test runs within one game
 * tick and drives the End's atmos ticks itself, in a batch of its own, so no other test sees what it
 * sets; the exception is {@link #env_015_provider_runs_once_per_real_atmos_tick}, in a batch of its
 * own for that reason.
 *
 * <p>ENV-014's third source, an ambient seeded from another mod's data (SPACE-020), comes with the
 * Ad Astra adapter in R2 (D-66). Until then these tests fill its slot with {@link
 * Ambients#seedForTest}.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmbientProviderTests {
    private static final String BATCH = "env_providers";
    private static final double EPS = 1e-9;

    /** Far from the End's island and its dragon fight. */
    private static final BlockPos OPEN = new BlockPos(-16384, 100, 16384);

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;

    private static final Ambient SEEDED = Ambient.vacuum(150);
    private static final AmbientDefinition PROVIDED = new AmbientDefinition(Map.of(O2, 30.0, N2, 20.0), 250);
    private static final Ambient PROVIDED_AMBIENT =
            new Ambient(new TreeMap<>(Map.of(O2.toString(), 30.0, N2.toString(), 20.0)), 250);

    private AmbientProviderTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static ServerLevel end(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        return end;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void tick(ServerLevel level) {
        Rooms.service().tickNow(level);
    }

    /** Runs a body on the End with its datapack definition taken away, then puts everything back. */
    private static void onBareEnd(GameTestHelper h, Consumer<ServerLevel> body) {
        withDefinition(h, DimensionAmbient.NONE, body);
    }

    /** Runs a body on the End with another definition in force, then puts everything back. */
    private static void withDefinition(GameTestHelper h, DimensionAmbient definition, Consumer<ServerLevel> body) {
        ServerLevel end = end(h);
        Ambients.overrideDefinitionForTest(Level.END, definition);
        try {
            end.getChunk(OPEN);
            body.accept(end);
        } finally {
            for (AmbientProviders.Registration r : AmbientProviders.registered(Level.END)) {
                AmbientProviders.unregister(r.id(), Level.END);
            }
            Ambients.seedForTest(Level.END, null);
            Ambients.overrideDefinitionForTest(Level.END, null);
            // Takes the answer away too, so the next test starts without it.
            tick(end);
        }
        h.succeed();
    }

    /** A provider that counts its calls and the threads they came on. */
    private static final class Counting implements AmbientProvider {
        final AmbientDefinition answer;
        final List<Thread> threads = new CopyOnWriteArrayList<>();

        Counting(AmbientDefinition answer) {
            this.answer = answer;
        }

        @Override
        public AmbientDefinition ambient(ServerLevel level) {
            threads.add(Thread.currentThread());
            return answer;
        }

        int calls() {
            return threads.size();
        }
    }

    /** Collects what a class logs as warnings and as errors while it is open. */
    private static final class Warnings extends AbstractAppender implements AutoCloseable {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger logger;

        Warnings(String loggerName) {
            super("zas_gametest_warnings", null, null, true, Property.EMPTY_ARRAY);
            logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(loggerName);
            start();
            logger.addAppender(this);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
                messages.add(event.getMessage().getFormattedMessage());
            } else if (event.getLevel() == org.apache.logging.log4j.Level.ERROR) {
                errors.add(event.getMessage().getFormattedMessage());
            }
        }

        @Override
        public void close() {
            logger.removeAppender(this);
            stop();
        }
    }

    private static Warnings providerWarnings() {
        return new Warnings("io.github.west3436.zas.environment.ProvidedAmbients");
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
    }

    // ---- ENV-004 ------------------------------------------------------------------------

    /**
     * A registered provider sets its dimension's ambient: for queries, for the simulation, whose
     * batch carries it (a zone formed there holds its gas), and for what comes after, once it is
     * gone (ENV-004).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_004_provider_sets_the_dimensions_ambient(GameTestHelper h) {
        onBareEnd(h, end -> {
            AmbientProviders.register(id("zas_gametest", "env_004"), Level.END, level -> PROVIDED);
            tick(end);

            AtmosphereSample open = Atmospheres.at(end, OPEN);
            h.assertValueEqual(open.state(), AtmosphereState.AMBIENT, "state");
            close(h, open.temperature(), 250, "temperature");
            close(h, open.pressure(), 50, "pressure");
            close(h, open.partialPressures().get(O2), 30, "O2");
            close(h, open.partialPressures().get(N2), 20, "N2");
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "the lookup");

            // The simulation has it: a room pinned into a zone holds the provider's gas.
            Rooms r = new Rooms(h, end, OPEN.offset(32, 0, 0));
            r.clear(0, 5);
            r.shell(0, 4);
            PinResult pin = r.settle(ZonePins.pin(end, r.point(2, 2, 2), Rooms.source("env_004")));
            h.assertValueEqual(pin.outcome(), PinOutcome.ZONE, "the pin");
            ZoneInfo zone = r.zone(2, 2, 2);
            close(h, zone.mixture().temperature(), 250, "the zone's temperature");
            close(h, zone.mixture().pressure(), 50, "the zone's pressure");
            r.settle(ZonePins.unpin(end, Rooms.source("env_004")));

            // Without it, the fallback applies again from the next atmos tick.
            AmbientProviders.unregister(id("zas_gametest", "env_004"), Level.END);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "before the next atmos tick");
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.STANDARD_AIR, "without the provider");
        });
    }

    /** A provider can make its dimension a vacuum, at a temperature of its own (ENV-003, ENV-004). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_004_provider_gives_a_vacuum(GameTestHelper h) {
        onBareEnd(h, end -> {
            AmbientProviders.register(
                    id("zas_gametest", "env_004_vacuum"), Level.END, level -> AmbientDefinition.vacuum(737));
            tick(end);
            AtmosphereSample open = Atmospheres.at(end, OPEN);
            h.assertValueEqual(open.pressure(), 0.0, "pressure");
            close(h, open.temperature(), 737, "temperature");
        });
    }

    /**
     * An answer naming a gas the server doesn't have is skipped, as a datapack definition naming one
     * is (OPS-008), and warned about once, however many atmos ticks it goes on giving it (ENV-004).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_004_answer_naming_an_unknown_gas_is_skipped(GameTestHelper h) {
        onBareEnd(h, end -> {
            AmbientDefinition unknown = new AmbientDefinition(Map.of(id("zas_gametest", "no_such_gas"), 10.0), 250);
            AmbientProviders.register(id("zas_gametest", "env_004_unknown"), Level.END, level -> unknown);
            try (Warnings warnings = providerWarnings()) {
                tick(end);
                tick(end);
                h.assertValueEqual(warnings.messages.size(), 1, "warnings: " + warnings.messages);
                h.assertTrue(
                        warnings.messages.get(0).contains("zas_gametest:env_004_unknown")
                                && warnings.messages.get(0).contains("zas_gametest:no_such_gas"),
                        warnings.messages.get(0));
            }
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.STANDARD_AIR, "the ambient");
        });
    }

    // ---- NFR-013 ------------------------------------------------------------------------

    /** A provider that gives the answer it holds, or throws once told to, and counts its calls. */
    private static final class Faulty implements AmbientProvider {
        AmbientDefinition answer;
        boolean failing;
        int calls;

        Faulty(AmbientDefinition answer) {
            this.answer = answer;
        }

        @Override
        public AmbientDefinition ambient(ServerLevel level) {
            calls++;
            if (failing) {
                throw new IllegalStateException("the test provider failing");
            }
            return answer;
        }
    }

    /**
     * A provider that throws is skipped for the atmos tick and keeps its last good answer, the owner's
     * decision of 2026-11-06. It's asked again each atmos tick, its answer counts as soon as it stops,
     * and the log has one line a minute, however many atmos ticks it throws in.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_an_ambient_provider_that_throws_keeps_its_last_good_answer_and_is_logged_once_a_minute(
            GameTestHelper h) {
        onBareEnd(h, end -> {
            ResourceLocation id = id("zas_gametest", "nfr_013");
            Ambient vacuum = Ambient.vacuum(737);
            Faulty provider = new Faulty(AmbientDefinition.vacuum(737));
            AmbientProviders.register(id, Level.END, provider);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), vacuum, "before it throws");
            // Not a FaultLog: the other tests here attach to this logger, which cuts it off from the root.
            try (Warnings log = providerWarnings()) {
                FaultLog.aMinutePasses();
                provider.failing = true;
                provider.calls = 0;
                tick(end);
                tick(end);
                tick(end);
                h.assertValueEqual(provider.calls, 3, "the times it was asked in 3 atmos ticks");
                h.assertValueEqual(Ambients.ambient(Level.END), vacuum, "while it throws");
                h.assertValueEqual(Atmospheres.at(end, OPEN).pressure(), 0.0, "the pressure while it throws");
                h.assertValueEqual(log.errors.size(), 1, "errors logged: " + log.errors);
                h.assertTrue(log.errors.get(0).contains(id.toString()), log.errors.get(0));

                FaultLog.aMinutePasses();
                tick(end);
                h.assertValueEqual(log.errors.size(), 2, "errors logged a minute later: " + log.errors);

                provider.answer = PROVIDED;
                provider.failing = false;
                tick(end);
                h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "once it stops throwing");
                h.assertValueEqual(log.errors.size(), 2, "errors logged once it stops: " + log.errors);
            }
        });
    }

    /**
     * What the decision is for: in a room with a hole, in a vacuum whose provider throws, no gas comes
     * in, since the simulation's batch has the last good answer too and not standard air (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_no_gas_flows_in_while_a_vacuum_dimensions_provider_throws(GameTestHelper h) {
        onBareEnd(h, end -> {
            Faulty provider = new Faulty(AmbientDefinition.vacuum());
            AmbientProviders.register(id("zas_gametest", "nfr_013_vacuum"), Level.END, provider);
            tick(end);
            Rooms r = new Rooms(h, end, OPEN.offset(96, 0, 0));
            r.clear(0, 5);
            r.shell(0, 4);
            PinResult pin = r.settle(ZonePins.pin(end, r.point(2, 2, 2), Rooms.source("nfr_013_vacuum")));
            h.assertValueEqual(pin.outcome(), PinOutcome.ZONE, "the pin");
            r.set(2, 4, 2, Rooms.AIR);
            r.ticks(2);
            h.assertTrue(r.zone(2, 2, 2).breached(), "the room isn't breached");

            provider.failing = true;
            r.ticks(5);
            close(h, r.zone(2, 2, 2).mixture().pressure(), 0, "the room's pressure while it throws");
            r.settle(ZonePins.unpin(end, Rooms.source("nfr_013_vacuum")));
        });
    }

    /**
     * With no good answer yet, a provider that throws gets the next source, whichever that is, and its
     * first good answer applies from then on (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_a_provider_that_throws_before_any_good_answer_gets_the_next_source(GameTestHelper h) {
        onBareEnd(h, end -> {
            Faulty provider = new Faulty(PROVIDED);
            provider.failing = true;
            AmbientProviders.register(id("zas_gametest", "nfr_013_first"), Level.END, provider);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.STANDARD_AIR, "with the fallback next");
            Ambients.seedForTest(Level.END, SEEDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "with a seeded ambient next");

            provider.failing = false;
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "its first good answer");
            provider.failing = true;
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "that answer, once it throws again");
        });
    }

    /**
     * A null means "not defined for now", a real answer and no fault: nothing is logged for it, and a
     * throw after it leaves the next source in place, so what came before it doesn't come back (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_a_provider_that_throws_after_answering_null_keeps_the_next_source(GameTestHelper h) {
        onBareEnd(h, end -> {
            Faulty provider = new Faulty(PROVIDED);
            AmbientProviders.register(id("zas_gametest", "nfr_013_null"), Level.END, provider);
            Ambients.seedForTest(Level.END, SEEDED);
            try (Warnings log = providerWarnings()) {
                FaultLog.aMinutePasses();
                tick(end);
                h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "while it answers");

                provider.answer = null;
                tick(end);
                h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "once it answers null");
                h.assertValueEqual(log.errors, List.of(), "errors logged for a null");

                provider.failing = true;
                tick(end);
                h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "once it throws after the null");
                h.assertValueEqual(log.errors.size(), 1, "errors logged: " + log.errors);
            }
        });
    }

    /**
     * A provider's answer is for a dimension. One provider in two keeps each its own last good answer,
     * and throwing in one of them changes nothing in the other (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_a_providers_last_good_answer_is_kept_for_each_dimension(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        ResourceLocation id = id("zas_gametest", "nfr_013_dimensions");
        Set<ResourceKey<Level>> failing = new HashSet<>();
        double[] temperature = {100};
        AmbientProvider provider = level -> {
            if (failing.contains(level.dimension())) {
                throw new IllegalStateException("the test provider failing");
            }
            return AmbientDefinition.vacuum(temperature[0]);
        };
        Ambients.overrideDefinitionForTest(Level.NETHER, DimensionAmbient.NONE);
        AmbientProviders.register(id, Level.NETHER, provider);
        try {
            onBareEnd(h, end -> {
                AmbientProviders.register(id, Level.END, provider);
                tick(end);
                temperature[0] = 200;
                tick(nether);

                failing.add(Level.END);
                temperature[0] = 300;
                tick(end);
                tick(nether);
                h.assertValueEqual(Ambients.ambient(Level.END), Ambient.vacuum(100), "the End, which throws");
                h.assertValueEqual(Ambients.ambient(Level.NETHER), Ambient.vacuum(300), "the Nether, which answers");

                failing.add(Level.NETHER);
                temperature[0] = 400;
                tick(end);
                tick(nether);
                h.assertValueEqual(Ambients.ambient(Level.END), Ambient.vacuum(100), "the End, with both throwing");
                h.assertValueEqual(
                        Ambients.ambient(Level.NETHER), Ambient.vacuum(300), "the Nether, with both throwing");
            });
        } finally {
            AmbientProviders.unregister(id, Level.NETHER);
            Ambients.overrideDefinitionForTest(Level.NETHER, null);
            tick(nether);
        }
    }

    /**
     * Only its own answer outlasts a provider's fault. One that takes over and throws gets the next
     * source, not the answer of the one before it. And a dimension remembers one answer, so a provider
     * whose answer another has replaced has no good answer left when it throws (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_a_provider_doesnt_keep_another_providers_answer(GameTestHelper h) {
        onBareEnd(h, end -> {
            ResourceLocation highId = id("zas_gametest", "nfr_013_high");
            Faulty low = new Faulty(PROVIDED);
            Faulty high = new Faulty(AmbientDefinition.vacuum(100));
            high.failing = true;
            AmbientProviders.register(id("zas_gametest", "nfr_013_low"), Level.END, low);
            Ambients.seedForTest(Level.END, SEEDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "the one provider");

            AmbientProviders.register(highId, Level.END, 1, high);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "a new provider that throws");

            high.failing = false;
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.vacuum(100), "the new provider's answer");

            AmbientProviders.unregister(highId, Level.END);
            low.failing = true;
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "the first provider, now throwing");
        });
    }

    // ---- ENV-014 ------------------------------------------------------------------------

    /** A datapack definition beats a registered provider (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_datapack_beats_provider(GameTestHelper h) {
        Ambient shipped = Ambients.definition(Level.END).orElseThrow().normal().orElseThrow();
        withDefinition(h, Ambients.definition(Level.END).orElseThrow(), end -> {
            AmbientProviders.register(id("zas_gametest", "env_014"), Level.END, level -> PROVIDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END, AtmospherePreset.NORMAL), shipped, "the ambient");
            close(h, Atmospheres.at(end, OPEN).pressure(), StandardAir.PRESSURE, "the sample's pressure");
        });
    }

    /** A datapack definition beats a seeded ambient (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_datapack_beats_seeded(GameTestHelper h) {
        Ambient shipped = Ambients.definition(Level.END).orElseThrow().normal().orElseThrow();
        withDefinition(h, Ambients.definition(Level.END).orElseThrow(), end -> {
            Ambients.seedForTest(Level.END, SEEDED);
            h.assertValueEqual(Ambients.ambient(Level.END, AtmospherePreset.NORMAL), shipped, "the ambient");
        });
    }

    /** A datapack definition beats the fallback (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_datapack_beats_fallback(GameTestHelper h) {
        Ambient defined = new Ambient(new TreeMap<>(Map.of(O2.toString(), 40.0)), 300);
        withDefinition(h, new DimensionAmbient(Optional.of(defined), Optional.of(defined), false), end -> {
            h.assertValueEqual(Ambients.ambient(Level.END), defined, "the ambient");
        });
    }

    /** A registered provider beats a seeded ambient (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_provider_beats_seeded(GameTestHelper h) {
        onBareEnd(h, end -> {
            Ambients.seedForTest(Level.END, SEEDED);
            AmbientProviders.register(id("zas_gametest", "env_014"), Level.END, level -> PROVIDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "the ambient");
        });
    }

    /** A registered provider beats the fallback (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_provider_beats_fallback(GameTestHelper h) {
        onBareEnd(h, end -> {
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.STANDARD_AIR, "with no source");
            AmbientProviders.register(id("zas_gametest", "env_014"), Level.END, level -> PROVIDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "the ambient");
        });
    }

    /** A seeded ambient beats the fallback (ENV-014, D-45). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_seeded_beats_fallback(GameTestHelper h) {
        onBareEnd(h, end -> {
            h.assertValueEqual(Ambients.ambient(Level.END), Ambient.STANDARD_AIR, "with no source");
            Ambients.seedForTest(Level.END, SEEDED);
            h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "the ambient");
        });
    }

    /**
     * A definition with no ambient for the active variant doesn't define the dimension there, so the
     * next source applies (ENV-014, DimensionAmbient).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_definition_without_this_variant_falls_through(GameTestHelper h) {
        Ambient hardcoreOnly = new Ambient(new TreeMap<>(Map.of(O2.toString(), 40.0)), 300);
        withDefinition(h, new DimensionAmbient(Optional.empty(), Optional.of(hardcoreOnly), false), end -> {
            AmbientProviders.register(id("zas_gametest", "env_014"), Level.END, level -> PROVIDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END, AtmospherePreset.HARDCORE), hardcoreOnly, "Hardcore");
            h.assertValueEqual(Ambients.ambient(Level.END, AtmospherePreset.NORMAL), PROVIDED_AMBIENT, "Normal");
        });
    }

    /** A provider that answers null defines nothing for now, so the next source applies (ENV-014). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_014_provider_answering_null_falls_through(GameTestHelper h) {
        onBareEnd(h, end -> {
            AmbientDefinition[] answer = {PROVIDED};
            AmbientProviders.register(id("zas_gametest", "env_014"), Level.END, level -> answer[0]);
            Ambients.seedForTest(Level.END, SEEDED);
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), PROVIDED_AMBIENT, "while it answers");
            answer[0] = null;
            tick(end);
            h.assertValueEqual(Ambients.ambient(Level.END), SEEDED, "once it doesn't");
        });
    }

    // ---- ENV-015 ------------------------------------------------------------------------

    /**
     * The provider runs once per atmos tick, on the server thread, and only there: queries read what
     * it last answered, from any thread, without running it (ENV-015).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_015_provider_runs_on_the_server_thread_once_per_atmos_tick(GameTestHelper h) {
        onBareEnd(h, end -> {
            Counting provider = new Counting(PROVIDED);
            AmbientProviders.register(id("zas_gametest", "env_015"), Level.END, provider);
            for (int i = 1; i <= 5; i++) {
                tick(end);
                h.assertValueEqual(provider.calls(), i, "calls after " + i + " atmos ticks");
            }
            h.assertTrue(end.getServer().isSameThread(), "the test isn't on the server thread");
            for (Thread t : provider.threads) {
                h.assertTrue(t == Thread.currentThread(), "a provider ran on " + t.getName());
            }

            Atmospheres.at(end, OPEN);
            Ambients.ambient(Level.END);
            AtmosphereSample[] sample = new AtmosphereSample[1];
            CompletableFuture<Void> elsewhere = CompletableFuture.runAsync(() -> sample[0] = Atmospheres.at(end, OPEN));
            try {
                elsewhere.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new GameTestAssertException("the query from another thread failed: " + e);
            }
            close(h, sample[0].temperature(), 250, "the temperature read from another thread");
            h.assertValueEqual(provider.calls(), 5, "calls after the queries");
        });
    }

    /**
     * Left to the scheduler, the provider runs once for each batch that closes (ENV-015). This test
     * spans game ticks, so the End's definition stays away for a while, in a batch of its own.
     */
    @GameTest(template = "empty", batch = BATCH + "_cadence", timeoutTicks = 200)
    public static void env_015_provider_runs_once_per_real_atmos_tick(GameTestHelper h) {
        ServerLevel end = end(h);
        int period = ZasServerConfig.scheduler().periodTicks();
        ResourceLocation provider = id("zas_gametest", "env_015_cadence");
        Counting counting = new Counting(PROVIDED);
        Ambients.overrideDefinitionForTest(Level.END, DimensionAmbient.NONE);
        AmbientProviders.register(provider, Level.END, counting);
        long start = end.getServer().getTickCount();
        h.runAfterDelay(period * 3L + 1, () -> {
            long now = end.getServer().getTickCount();
            AmbientProviders.unregister(provider, Level.END);
            Ambients.overrideDefinitionForTest(Level.END, null);
            // A batch closes at the end of a game tick that is a multiple of the period (CORE-041),
            // after this test's own part of it: so from the start tick up to, not including, now.
            long closes = Math.floorDiv(now - 1, (long) period) - Math.floorDiv(start - 1, (long) period);
            int calls = counting.calls();
            h.assertTrue(closes >= 3, "only " + closes + " batches closed");
            h.assertTrue(
                    calls == closes,
                    calls + " calls over " + closes + " batch closes, in game ticks " + start + " to " + now);
            for (Thread t : counting.threads) {
                h.assertTrue(t == Thread.currentThread(), "a provider ran on " + t.getName());
            }
            h.succeed();
        });
    }

    // ---- ENV-019, ENV-020 -----------------------------------------------------------------

    /**
     * Of several providers for a dimension, the highest priority applies, and it alone runs (ENV-019).
     * A provider that declares none has priority 0.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_019_highest_priority_applies(GameTestHelper h) {
        onBareEnd(h, end -> {
            Counting low = new Counting(AmbientDefinition.vacuum(100));
            Counting plain = new Counting(AmbientDefinition.vacuum(200));
            Counting high = new Counting(AmbientDefinition.vacuum(300));
            AmbientProviders.register(id("aaa", "low"), Level.END, -1, low);
            AmbientProviders.register(id("mmm", "plain"), Level.END, plain);
            tick(end);
            close(h, Ambients.ambient(Level.END).temperature(), 200, "priority 0 over -1");
            AmbientProviders.register(id("zzz", "high"), Level.END, 1, high);
            tick(end);
            close(h, Ambients.ambient(Level.END).temperature(), 300, "priority 1 over 0");
            h.assertValueEqual(low.calls(), 0, "the lowest's calls");
            h.assertValueEqual(plain.calls(), 1, "calls while it applied");
            h.assertValueEqual(high.calls(), 1, "calls since it applied");
        });
    }

    /** Between equal priorities, the lexicographically smallest mod id applies (ENV-019). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_019_tie_goes_to_the_smallest_mod_id(GameTestHelper h) {
        onBareEnd(h, end -> {
            Counting z = new Counting(AmbientDefinition.vacuum(100));
            Counting a = new Counting(AmbientDefinition.vacuum(200));
            AmbientProviders.register(id("zzz", "a"), Level.END, 3, z);
            AmbientProviders.register(id("aaa", "z"), Level.END, 3, a);
            tick(end);
            close(h, Ambients.ambient(Level.END).temperature(), 200, "the smallest mod id");
            h.assertValueEqual(z.calls(), 0, "the other's calls");
        });
    }

    /**
     * Two providers for a dimension log one warning, however many atmos ticks follow, naming both and
     * the one that applies (ENV-020). A change in who is registered warns again; one provider doesn't.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_020_one_warning_names_the_providers_and_the_winner(GameTestHelper h) {
        onBareEnd(h, end -> {
            ResourceLocation winner = id("zas_gametest_b", "planets");
            ResourceLocation loser = id("zas_gametest_a", "planets");
            ResourceLocation third = id("zas_gametest_c", "planets");
            try (Warnings warnings = providerWarnings()) {
                AmbientProviders.register(winner, Level.END, 2, level -> PROVIDED);
                tick(end);
                h.assertValueEqual(warnings.messages, List.of(), "warnings with one provider");

                AmbientProviders.register(loser, Level.END, level -> AmbientDefinition.vacuum());
                tick(end);
                tick(end);
                tick(end);
                h.assertValueEqual(warnings.messages.size(), 1, "warnings: " + warnings.messages);
                String warning = warnings.messages.get(0);
                h.assertTrue(warning.contains(winner.toString()), "it doesn't name the winner: " + warning);
                h.assertTrue(warning.contains(loser.toString()), "it doesn't name the other: " + warning);
                h.assertTrue(warning.contains("minecraft:the_end"), "it doesn't name the dimension: " + warning);
                h.assertTrue(warning.endsWith(winner + " applies"), "it doesn't say who applies: " + warning);

                AmbientProviders.register(third, Level.END, 5, level -> AmbientDefinition.vacuum());
                tick(end);
                h.assertValueEqual(warnings.messages.size(), 2, "warnings: " + warnings.messages);
                h.assertTrue(
                        warnings.messages.get(1).endsWith(third + " applies"),
                        "it doesn't say who applies now: " + warnings.messages.get(1));
            }
        });
    }

    /** The conflict warning comes whichever source applies: a datapack can hide a conflict (ENV-020). */
    @GameTest(template = "empty", batch = BATCH)
    public static void env_020_a_conflict_warns_under_a_datapack_definition(GameTestHelper h) {
        withDefinition(h, Ambients.definition(Level.END).orElseThrow(), end -> {
            try (Warnings warnings = providerWarnings()) {
                AmbientProviders.register(id("zas_gametest_a", "one"), Level.END, level -> PROVIDED);
                AmbientProviders.register(id("zas_gametest_b", "two"), Level.END, level -> PROVIDED);
                tick(end);
                h.assertValueEqual(warnings.messages.size(), 1, "warnings: " + warnings.messages);
            }
        });
    }
}
