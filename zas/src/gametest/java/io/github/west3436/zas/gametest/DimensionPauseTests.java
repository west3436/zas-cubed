package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.service;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.persistence.DimensionData;
import io.github.west3436.zas.persistence.DimensionPause;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pausing a whole dimension (OPS-005), in the Nether, as {@code PersistenceTests}' restart tests do,
 * in a batch of their own, so no other test's zones pause. Each test drives its own atmos ticks and
 * leaves the dimension running, whatever happens. The rules a paused zone follows are in {@code
 * PausedZoneTests}; the engine's side is {@code DimensionPauseTest}.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DimensionPauseTests {
    private static final String BATCH = "ops_005_dimension_pause";

    private DimensionPauseTests() {}

    static ServerLevel nether(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        return nether;
    }

    /** Runs a test's body, then makes sure the Nether is left running. */
    static void inPausableNether(GameTestHelper h, Runnable body) {
        try {
            Rooms.withoutHeat(body);
        } finally {
            service().dimensionFor(nether(h)).resumeSimulation();
        }
    }

    /**
     * A sealed room from x 10 to 21, across the chunk border at 16, with its zone formed, and enough
     * gas that a hole doesn't vent it down to the ambient within a few atmos ticks.
     */
    static ZoneInfo room(GameTestHelper h, Rooms r) {
        r.clear(8, 23);
        r.shell(10, 21);
        MutationResult m = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(4000), source("ops_005")));
        h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "the room didn't zone");
        return r.zone(12, 2, 2);
    }

    /** Collects what commands send to their source. */
    static final class Capture implements CommandSource {
        final List<Component> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (Component m : messages) {
                b.append(m.getString()).append('\n');
            }
            return b.toString();
        }
    }

    static CommandSourceStack console(ServerLevel level, Capture capture, int permission) {
        return new CommandSourceStack(
                capture,
                Vec3.ZERO,
                Vec2.ZERO,
                level,
                permission,
                "zas_test",
                Component.literal("zas_test"),
                level.getServer(),
                null);
    }

    static int run(CommandSourceStack source, String command) {
        try {
            return source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
    }

    static void refused(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return;
        }
        throw new GameTestAssertException("/" + command + " wasn't refused");
    }

    /**
     * OPS-005's check: {@code /zas pause} pauses a dimension without a restart, its zones behave as
     * paused zones, and the pause and its reason remain after a restart, until {@code /zas resume},
     * when the zones carry on and held block changes apply.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_005_a_paused_dimension_stays_paused_after_a_restart(GameTestHelper h) {
        inPausableNether(h, () -> {
            ServerLevel nether = nether(h);
            Rooms r = new Rooms(h, nether, 975);
            ZoneInfo before = room(h, r);
            h.assertFalse(before.paused(), "paused before the command");
            r.clear(30, 50);
            r.shell(32, 38);

            Capture c = new Capture();
            CommandSourceStack op = console(nether, c, 2);
            refused(console(nether, c, 1), "zas pause");
            h.assertTrue(r.dimension().simulationPause() == null, "paused from below the permission level");
            run(op, "zas pause fixing a leak");
            DimensionPause expected = new DimensionPause("command:server", "fixing a leak");
            h.assertValueEqual(r.dimension().simulationPause(), expected, "the recorded pause");

            r.tick();
            ZoneInfo paused = r.zone(12, 2, 2);
            h.assertTrue(paused.paused(), "the zone isn't paused");
            h.assertValueEqual(
                    Atmospheres.at(nether, r.abs(12, 2, 2)).state(), AtmosphereState.PAUSED, "the API's state");
            r.same(before, paused, "paused");

            MutationResult voided = r.settle(GasMutations.add(nether, r.point(12, 2, 2), oxygen(5), source("ops_005")));
            h.assertValueEqual(voided.outcome(), MutationOutcome.PAUSED, "gas sent to a paused zone");
            r.close(voided.moved().moles(O2), 5, "the gas voided");
            MutationResult supplied =
                    r.settle(GasMutations.remove(nether, r.point(12, 2, 2), Map.of(O2, 3.0), source("ops_005")));
            h.assertValueEqual(supplied.outcome(), MutationOutcome.PAUSED, "gas drawn from a paused zone");
            r.close(supplied.moved().moles(O2), 3, "the gas supplied");
            r.same(before, r.zone(12, 2, 2), "after gas was voided and supplied");

            MutationResult unzoned =
                    r.settle(GasMutations.add(nether, r.point(35, 2, 2), oxygen(1), source("ops_005")));
            h.assertValueEqual(unzoned.outcome(), MutationOutcome.PAUSED, "gas sent to an unzoned room");
            h.assertFalse(r.zoned(35, 2, 2), "a zone formed while paused");

            r.set(10, 2, 2, AIR);
            r.ticks(2);
            h.assertFalse(r.zone(12, 2, 2).breached(), "a block change applied while paused");

            c.messages.clear();
            run(op, "zas zones");
            h.assertTrue(c.text().contains("paused, by command:server: fixing a leak"), "/zas zones:\n" + c.text());

            var saved = service().stopForTest(nether, r.chunkKey(0), r.chunkKey(16));
            h.assertValueEqual(saved.size(), 2, "chunks saved at shutdown");
            DimensionAtmos restarted = service().startForTest(nether, saved);
            h.assertValueEqual(restarted.simulationPause(), expected, "the pause after the restart");

            r.ticks(2);
            ZoneInfo after = Atmospheres.zone(nether, before.id())
                    .orElseThrow(() -> new GameTestAssertException("the zone didn't survive the restart"));
            h.assertTrue(after.paused(), "the zone isn't paused after the restart");
            r.same(before, after, "after the restart");
            h.assertFalse(r.zoned(35, 2, 2), "a zone formed after the restart");

            c.messages.clear();
            h.assertValueEqual(run(op, "zas pause another reason"), 0, "pausing a paused dimension");
            h.assertTrue(c.text().contains("fixing a leak"), "the first reason isn't reported:\n" + c.text());
            h.assertValueEqual(restarted.simulationPause(), expected, "the first pause stays");

            run(op, "zas resume");
            h.assertTrue(restarted.simulationPause() == null, "still paused after /zas resume");
            // The held hole opens onto the Nether's caves. At the default bound of 102,400 cells its
            // fill needs sections the restarted mirror doesn't hold yet, so it comes out open a
            // couple of atmos ticks later than it did at 65,536 (GEO-028).
            r.ticks(6);
            ZoneInfo resumed = Atmospheres.zoneAt(nether, r.abs(12, 2, 2))
                    .orElseThrow(() -> new GameTestAssertException("the zone didn't resume"));
            h.assertFalse(resumed.paused(), "the zone is paused after /zas resume");
            h.assertTrue(
                    resumed.breached() || resumed.cellCount() > before.cellCount(),
                    "the held block change didn't apply: " + resumed);
            refused(op, "zas resume");
            h.succeed();
        });
    }

    /**
     * The entry point assumes no command: anything on the server can pause a dimension with its own
     * name and reason, and the first pause stays until the dimension resumes.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_005_the_pause_records_who_asked_and_why(GameTestHelper h) {
        inPausableNether(h, () -> {
            ServerLevel nether = nether(h);
            Rooms r = new Rooms(h, nether, 976);
            room(h, r);
            DimensionAtmos d = r.dimension();
            DimensionPause why = new DimensionPause("zas:gametest", "a test asked");
            h.assertTrue(d.pauseSimulation(why), "pausing a running dimension");
            h.assertFalse(d.pauseSimulation(new DimensionPause("zas:other", "later")), "pausing it again");
            h.assertValueEqual(d.simulationPause(), why, "the first pause stays");
            r.tick();
            h.assertTrue(r.zone(12, 2, 2).paused(), "the zone isn't paused");
            h.assertTrue(d.resumeSimulation(), "resuming a paused dimension");
            h.assertFalse(d.resumeSimulation(), "resuming it again");
            r.tick();
            h.assertFalse(r.zone(12, 2, 2).paused(), "the zone didn't resume");
            h.succeed();
        });
    }

    /** A dimension excluded from simulation (ENV-007) has nothing to pause, and the command says so. */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_005_an_excluded_dimension_cant_be_paused(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        List<? extends String> before = ZasServerConfig.EXCLUDED_DIMENSIONS.get();
        ZasServerConfig.EXCLUDED_DIMENSIONS.set(List.of(Level.END.location().toString()));
        try {
            refused(console(end, new Capture(), 2), "zas pause");
            h.assertTrue(DimensionData.of(end).pause() == null, "an excluded dimension paused");
        } finally {
            ZasServerConfig.EXCLUDED_DIMENSIONS.set(before);
        }
        h.succeed();
    }

    /** A pause is one dimension's: zones in another keep running. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_005_other_dimensions_keep_running(GameTestHelper h) {
        inPausableNether(h, () -> {
            Rooms here = new Rooms(h, h.getLevel(), 977);
            ZoneInfo before = room(h, here);
            service().dimensionFor(nether(h)).pauseSimulation(new DimensionPause("zas:gametest", "a test asked"));
            MutationResult added =
                    here.settle(GasMutations.add(here.level, here.point(12, 2, 2), oxygen(5), source("ops_005")));
            h.assertValueEqual(added.outcome(), MutationOutcome.ZONE, "gas sent to a zone in another dimension");
            ZoneInfo after = here.zone(12, 2, 2);
            h.assertFalse(after.paused(), "a zone in another dimension paused");
            here.close(after.mixture().moles(O2), before.mixture().moles(O2) + 5, "O2 in the other dimension");
            h.succeed();
        });
    }
}
