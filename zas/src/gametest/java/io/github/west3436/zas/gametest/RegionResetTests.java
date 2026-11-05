package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.DimensionPauseTests.console;
import static io.github.west3436.zas.gametest.DimensionPauseTests.refused;
import static io.github.west3436.zas.gametest.DimensionPauseTests.run;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gametest.DimensionPauseTests.Capture;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Resetting a region to the ambient by command (OPS-013), in a batch of its own since the tests
 * drive their own atmos ticks. The engine's rules are in {@code RegionResetTest}.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RegionResetTests {
    private static final String BATCH = "ops_013_region_reset";

    /** The player the commands run as, and the source their gas is attributed to. */
    private static final String PLAYER = "zas_reset";

    private static final String BY = "command:" + PLAYER;

    private static final BlockState PANE = Blocks.GLASS_PANE
            .defaultBlockState()
            .setValue(IronBarsBlock.NORTH, true)
            .setValue(IronBarsBlock.SOUTH, true);

    private RegionResetTests() {}

    /** An operator-level command source that is a player, as the command attributes gas to one. */
    private static CommandSourceStack player(Rooms r, Capture c) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(PLAYER.getBytes()), PLAYER);
        return console(r.level, c, 2).withEntity(FakePlayerFactory.get(r.level, profile));
    }

    private static String at(Rooms r, int x, int y, int z) {
        BlockPos p = r.abs(x, y, z);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String reset(Rooms r, int x0, int y0, int z0, int x1, int y1, int z1) {
        return "zas reset " + at(r, x0, y0, z0) + " " + at(r, x1, y1, z1);
    }

    /** Waits, running the server's queued tasks, until a command reports {@code needle}. */
    private static void awaitText(GameTestHelper h, Capture c, String needle) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        h.getLevel().getServer().managedBlock(() -> c.text().contains(needle) || System.nanoTime() > deadline);
        h.assertTrue(c.text().contains(needle), "no \"" + needle + "\" in:\n" + c.text());
    }

    private static void assertUnzoned(GameTestHelper h, Rooms r, int x0, int x1, String what) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    h.assertFalse(r.zoned(x, y, z), what + " is still zoned at " + x + ", " + y + ", " + z);
                    h.assertValueEqual(Atmospheres.at(r.level, r.abs(x, y, z)).state(), AtmosphereState.AMBIENT, what);
                }
            }
        }
    }

    /**
     * OPS-013's check: a region with only part of a room in it resets the room, whole. Its cells are
     * unzoned, the checker records the gas as an operator sink attributed to the player, and a room
     * outside the region is untouched. The cells stay unzoned through a save and load, and gas added
     * later forms a new zone holding the ambient and that gas.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_a_region_resets_and_the_checker_records_the_sink(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 980);
            r.clear(8, 40);
            r.shell(10, 21); // across the chunk border at 16
            r.shell(30, 36);
            r.settle(GasMutations.add(
                    r.level, r.point(12, 2, 2), new GasParcel(Map.of(O2, 300.0, N2, 900.0), 310), source("ops_013")));
            r.settle(GasMutations.add(r.level, r.point(32, 2, 2), oxygen(50), source("ops_013")));
            ZoneInfo room = r.zone(12, 2, 2);
            ZoneInfo other = r.zone(32, 2, 2);
            double o2 = room.mixture().moles(O2);
            double n2 = room.mixture().moles(N2);

            Capture c = new Capture();
            CommandSourceStack op = player(r, c);
            DimensionAtmos d = r.dimension();
            CompletableFuture<ConservationReport> check = d.checkConservation();
            run(op, reset(r, 12, 1, 1, 13, 2, 2));
            h.assertTrue(c.text().contains("Queued resetting 1 zones"), c.text());
            r.tick();

            assertUnzoned(h, r, 11, 20, "the room");
            r.same(other, r.zone(32, 2, 2), "the room outside the region");

            ConservationReport report = r.settle(check);
            h.assertTrue(report != null && report.conserved(), "the checker reported " + report);
            int o2Index = d.registry().indexOf(O2.toString());
            int n2Index = d.registry().indexOf(N2.toString());
            r.close(report.recorded(LedgerCategory.OPERATOR, o2Index), -o2, "the O2 sink");
            r.close(report.recorded(LedgerCategory.OPERATOR, n2Index), -n2, "the N2 sink");
            r.close(report.removedBy(BY, o2Index), o2, "the O2 attributed to the player");
            r.close(report.removedBy(BY, n2Index), n2, "the N2 attributed to the player");

            awaitText(h, c, "Reset 1 zones to the ambient");
            h.assertTrue(c.text().contains("(by " + BY + ")"), c.text());
            c.messages.clear();
            run(op, "zas checker report");
            h.assertTrue(c.text().contains("operator -"), "/zas checker report:\n" + c.text());
            h.assertTrue(c.text().contains("Removed by " + BY + ":"), "/zas checker report:\n" + c.text());

            byte[] west = d.simulateUnload(r.chunk(0));
            byte[] east = d.simulateUnload(r.chunk(16));
            r.tick();
            d.simulateLoad(r.chunk(0), west);
            d.simulateLoad(r.chunk(16), east);
            r.ticks(2);
            assertUnzoned(h, r, 11, 20, "the room after a save and load");

            MutationResult again = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(5), source("ops_013")));
            h.assertValueEqual(again.outcome(), MutationOutcome.ZONE, "gas added after the reset");
            ZoneInfo fresh = r.zone(12, 2, 2);
            h.assertTrue(fresh.id().value() > room.id().value(), "the zone id was reissued");
            double ambient = Ambients.ambient(Level.OVERWORLD).moles(O2.toString(), fresh.volume());
            r.close(fresh.mixture().moles(O2), ambient + 5, "O2 in the new zone");
            h.succeed();
        });
    }

    /** Each half of a pane is a cell of its own (D-44), so a region of that block resets both rooms. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_a_pane_between_two_rooms_resets_both(GameTestHelper h) {
        Rooms r = new Rooms(h, 981);
        r.clear(8, 24);
        r.shell(10, 15);
        r.shell(15, 20);
        r.set(15, 2, 2, PANE);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(50), source("ops_013")));
        r.settle(GasMutations.add(r.level, r.point(18, 2, 2), oxygen(90), source("ops_013")));
        h.assertFalse(r.zone(12, 2, 2).id().equals(r.zone(18, 2, 2).id()), "the rooms are one zone");

        Capture c = new Capture();
        run(player(r, c), reset(r, 15, 2, 2, 15, 2, 2));
        h.assertTrue(c.text().contains("Queued resetting 2 zones"), c.text());
        r.tick();
        assertUnzoned(h, r, 11, 14, "the west room");
        assertUnzoned(h, r, 16, 19, "the east room");
        h.succeed();
    }

    /** A paused zone keeps its state (GEO-030), so it is left as it was, and the operator is told. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_a_paused_zone_is_left_as_it_was(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 982);
            r.clear(8, 24);
            r.shell(10, 21);
            r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(50), source("ops_013")));
            ZoneInfo before = r.zone(12, 2, 2);
            DimensionAtmos d = r.dimension();
            byte[] east = d.simulateUnload(r.chunk(16));
            r.tick();
            h.assertTrue(r.zone(12, 2, 2).paused(), "the zone didn't pause");

            Capture c = new Capture();
            CompletableFuture<ConservationReport> check = d.checkConservation();
            run(player(r, c), reset(r, 12, 2, 2, 12, 2, 2));
            r.tick();
            r.same(before, r.zone(12, 2, 2), "the paused zone");
            ConservationReport report = r.settle(check);
            h.assertValueEqual(
                    report.removedBy(BY, d.registry().indexOf(O2.toString())), 0.0, "O2 attributed to the player");
            awaitText(h, c, "Reset 0 zones");
            awaitText(h, c, "1 paused zones were left as they were");

            d.simulateLoad(r.chunk(16), east);
            r.ticks(2);
            ZoneInfo resumed = r.zone(12, 2, 2);
            h.assertFalse(resumed.paused(), "the zone didn't resume");
            r.same(before, resumed, "the zone once its chunk loaded");
            h.succeed();
        });
    }

    /** A reset is an operator's explicit order, so a pin doesn't stop it, and the pin goes too. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_a_pinned_zone_resets_and_its_pins_go(GameTestHelper h) {
        Rooms r = new Rooms(h, 983);
        r.clear(8, 24);
        r.shell(10, 15);
        r.settle(ZonePins.pin(r.level, r.point(12, 2, 2), SourceId.of("zas_gametest", "ops_013")));
        h.assertValueEqual(
                r.dimension().published().zone(r.zone(12, 2, 2).id().value()).pins(),
                List.of("zas_gametest:ops_013"),
                "the pin");

        run(player(r, new Capture()), reset(r, 12, 2, 2, 12, 2, 2));
        r.tick();
        assertUnzoned(h, r, 11, 14, "the pinned room");

        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(5), source("ops_013")));
        h.assertValueEqual(
                r.dimension().published().zone(r.zone(12, 2, 2).id().value()).pins(),
                List.of(),
                "the pins of a later zone there");
        h.succeed();
    }

    /** Open air has nothing to reset, and the command says so. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_a_region_with_no_zones_does_nothing(GameTestHelper h) {
        Rooms r = new Rooms(h, 984);
        r.clear(8, 15);
        Capture c = new Capture();
        h.assertValueEqual(run(player(r, c), reset(r, 9, 1, 1, 12, 3, 3)), 0, "the zones queued");
        h.assertTrue(c.text().contains("No zones in that region"), c.text());
        h.succeed();
    }

    /** The command keeps to the {@code /zas} permission level, and needs a simulated dimension. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
    public static void ops_013_is_refused_without_permission_or_simulation(GameTestHelper h) {
        Rooms r = new Rooms(h, 985);
        r.clear(8, 24);
        r.shell(10, 15);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(5), source("ops_013")));
        refused(console(r.level, new Capture(), 1), reset(r, 12, 2, 2, 12, 2, 2));
        r.tick();
        h.assertTrue(r.zoned(12, 2, 2), "reset from below the permission level");

        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        List<? extends String> before = ZasServerConfig.EXCLUDED_DIMENSIONS.get();
        ZasServerConfig.EXCLUDED_DIMENSIONS.set(List.of(Level.END.location().toString()));
        try {
            refused(console(end, new Capture(), 2), "zas reset 0 64 0 1 65 1");
        } finally {
            ZasServerConfig.EXCLUDED_DIMENSIONS.set(before);
        }
        h.succeed();
    }
}
