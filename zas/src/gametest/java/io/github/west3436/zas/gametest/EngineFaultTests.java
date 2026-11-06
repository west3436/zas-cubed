package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.DimensionPauseTests.console;
import static io.github.west3436.zas.gametest.DimensionPauseTests.inPausableNether;
import static io.github.west3436.zas.gametest.DimensionPauseTests.nether;
import static io.github.west3436.zas.gametest.DimensionPauseTests.room;
import static io.github.west3436.zas.gametest.DimensionPauseTests.run;
import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.service;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.engine.scheduler.Phase;
import io.github.west3436.zas.gametest.DimensionPauseTests.Capture;
import io.github.west3436.zas.persistence.DimensionPause;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * An exception inside the engine pauses its dimension as OPS-005 does, and the server keeps running
 * (D-55, NFR-014). In the Nether, in a batch of their own, as {@code DimensionPauseTests}. The
 * exception comes from the engine's phase listener, set on the engine under test only, and it dies
 * after its gas operations have run: halfway through its tick, with gas moved that nothing may see.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EngineFaultTests {
    private static final String BATCH = "nfr_014_engine_fault";
    private static final String BOOM = "an injected engine failure";
    private static final DimensionPause FAULT = new DimensionPause(
            DimensionAtmos.ENGINE_FAULT, "the engine failed: java.lang.IllegalStateException: " + BOOM);

    private EngineFaultTests() {}

    /** Makes the engine's next tick throw midway. Nothing is in flight, so no worker sees the change. */
    private static void failNextTick(Rooms r) {
        service().applyNow(r.level);
        r.dimension().pipeline().scheduler().engine().setPhaseListener(phase -> {
            if (phase == Phase.EXCHANGE) {
                throw new IllegalStateException(BOOM);
            }
        });
    }

    /** Collects what a class logs as errors. */
    private static final class Errors extends AbstractAppender implements AutoCloseable {
        final List<LogEvent> events = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.Logger logger;

        Errors(Class<?> of) {
            super("zas_gametest_errors", null, null, true, Property.EMPTY_ARRAY);
            logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(of.getName());
            start();
            logger.addAppender(this);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == Level.ERROR) {
                events.add(event.toImmutable());
            }
        }

        @Override
        public void close() {
            logger.removeAppender(this);
            stop();
        }
    }

    /**
     * NFR-014's check: an engine exception pauses the dimension with a logged error, and the server
     * keeps running. Nothing of the failed tick is published. The dimension then behaves as OPS-005
     * says, with the operations of the failed tick completing under its rules, and {@code /zas
     * resume} runs it again, finding the block change the failed tick took with it.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void nfr_014_an_engine_exception_pauses_the_dimension(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        inPausableNether(h, () -> {
            ServerLevel nether = nether(h);
            Rooms r = new Rooms(h, nether, 1401);
            DimensionAtmos d = r.dimension();
            ZoneInfo before = room(h, r);
            // An empty sealed annex beside the room, so the hole below leads nowhere the world's
            // terrain decides: past it are chunks that aren't loaded, which would hold the zone.
            r.shell(2, 10);
            r.tick();
            CompletableFuture<MutationResult> added =
                    GasMutations.add(nether, r.point(12, 2, 2), oxygen(5), source("nfr_014"));
            CompletableFuture<MutationResult> drawn =
                    GasMutations.remove(nether, r.point(12, 2, 2), Map.of(O2, 3.0), source("nfr_014"));
            r.set(10, 2, 2, AIR);

            failNextTick(r);
            try (Errors errors = new Errors(DimensionAtmos.class)) {
                r.tick();
                h.assertValueEqual(errors.events.size(), 1, "errors logged");
                Throwable logged = errors.events.get(0).getThrown();
                h.assertTrue(logged != null && BOOM.equals(logged.getMessage()), "the logged exception: " + logged);
            }
            h.assertValueEqual(d.simulationPause(), FAULT, "the recorded pause");
            h.assertFalse(d.isFaulted(), "the failed engine wasn't replaced");

            MutationResult voided = r.settle(added);
            h.assertValueEqual(voided.outcome(), MutationOutcome.PAUSED, "gas sent as the engine failed");
            r.close(voided.moved().moles(O2), 5, "the gas voided");
            MutationResult supplied = r.settle(drawn);
            h.assertValueEqual(supplied.outcome(), MutationOutcome.PAUSED, "gas drawn as the engine failed");
            r.close(supplied.moved().moles(O2), 3, "the gas supplied");

            r.ticks(2);
            ZoneInfo paused = r.zone(12, 2, 2);
            h.assertTrue(paused.paused(), "the zone isn't paused");
            h.assertValueEqual(
                    Atmospheres.at(nether, r.abs(12, 2, 2)).state(), AtmosphereState.PAUSED, "the API's state");
            // The hole came before the pause, so the new engine sees it, but no gas leaves by it yet.
            r.same(before, paused, "after the failed tick");

            MutationResult later = r.settle(GasMutations.add(nether, r.point(12, 2, 2), oxygen(1), source("nfr_014")));
            h.assertValueEqual(later.outcome(), MutationOutcome.PAUSED, "gas sent after the engine failed");

            Capture c = new Capture();
            CommandSourceStack op = console(nether, c, 2);
            run(op, "zas zones");
            h.assertTrue(c.text().contains("by " + DimensionAtmos.ENGINE_FAULT + ": " + FAULT.reason()), c.text());
            c.messages.clear();
            h.assertValueEqual(run(op, "zas pause another reason"), 0, "pausing the paused dimension");
            h.assertValueEqual(d.simulationPause(), FAULT, "the first pause stays");

            Rooms here = new Rooms(h, h.getLevel(), 1402);
            h.assertValueEqual(room(h, here).paused(), false, "a zone in another dimension paused");

            run(op, "zas resume");
            r.ticks(3);
            ZoneInfo resumed = Atmospheres.zoneAt(nether, r.abs(12, 2, 2))
                    .orElseThrow(() -> new GameTestAssertException("the zone didn't resume"));
            h.assertFalse(resumed.paused(), "the zone is paused after /zas resume");
            h.assertTrue(
                    resumed.cellCount() > before.cellCount(), "the failed tick's block change was lost: " + resumed);
        });
        int after = server.getTickCount();
        h.succeedWhen(() -> h.assertTrue(server.getTickCount() > after, "the server stopped ticking"));
    }

    /** The pause is an ordinary OPS-005 pause, saved with the dimension, so it outlasts a restart. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void nfr_014_the_pause_survives_a_restart(GameTestHelper h) {
        inPausableNether(h, () -> {
            ServerLevel nether = nether(h);
            Rooms r = new Rooms(h, nether, 1403);
            ZoneInfo before = room(h, r);
            failNextTick(r);
            r.ticks(3);
            r.same(before, r.zone(12, 2, 2), "after the failed tick");

            var saved = service().stopForTest(nether, r.chunkKey(0), r.chunkKey(16));
            h.assertValueEqual(saved.size(), 2, "chunks saved at shutdown");
            DimensionAtmos restarted = service().startForTest(nether, saved);
            h.assertValueEqual(restarted.simulationPause(), FAULT, "the pause after the restart");
            r.ticks(2);
            ZoneInfo after = r.zone(12, 2, 2);
            h.assertTrue(after.paused(), "the zone isn't paused after the restart");
            r.same(before, after, "after the restart");

            h.assertTrue(restarted.resumeSimulation(), "resuming after the restart");
            r.ticks(3);
            h.assertFalse(r.zone(12, 2, 2).paused(), "the zone didn't resume");
            h.succeed();
        });
    }

    /**
     * An engine that throws while the dimension is already paused isn't replaced: the first pause
     * stays, the last table stays readable and mutations fail. Resuming builds a new engine, which
     * gets the gas of the additions that were waiting, and not the removals that failed.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void nfr_014_a_fault_while_paused_waits_for_a_resume(GameTestHelper h) {
        inPausableNether(h, () -> {
            ServerLevel nether = nether(h);
            Rooms r = new Rooms(h, nether, 1404);
            DimensionAtmos d = r.dimension();
            ZoneInfo before = room(h, r);
            DimensionPause first = new DimensionPause("zas:gametest", "a test asked");
            d.pauseSimulation(first);
            r.tick();

            CompletableFuture<MutationResult> added =
                    GasMutations.add(nether, r.point(12, 2, 2), oxygen(5), source("nfr_014"));
            CompletableFuture<MutationResult> drawn =
                    GasMutations.remove(nether, r.point(12, 2, 2), Map.of(O2, 3.0), source("nfr_014"));
            failNextTick(r);
            r.tick();
            h.assertTrue(d.isFaulted(), "the engine isn't faulted");
            h.assertValueEqual(d.simulationPause(), first, "the first pause stays");
            h.assertTrue(added.isCompletedExceptionally(), "the waiting addition didn't fail");
            h.assertTrue(drawn.isCompletedExceptionally(), "the waiting removal didn't fail");
            r.same(before, r.zone(12, 2, 2), "the last table");
            CompletableFuture<MutationResult> refused =
                    GasMutations.add(nether, r.point(12, 2, 2), oxygen(1), source("nfr_014"));
            h.assertTrue(refused.isCompletedExceptionally(), "a new mutation didn't fail");
            r.ticks(2);
            h.assertTrue(d.isFaulted(), "the engine was replaced before the dimension resumed");

            h.assertTrue(d.resumeSimulation(), "resuming");
            h.assertFalse(d.isFaulted(), "resuming didn't replace the engine");
            r.ticks(4);
            ZoneInfo after = r.zone(12, 2, 2);
            h.assertFalse(after.paused(), "the zone didn't resume");
            r.close(after.mixture().moles(O2), before.mixture().moles(O2) + 5, "O2 after the resume");
            h.succeed();
        });
    }
}
