package io.github.west3436.zas.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.command.ZasCommands;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The {@code /zas} commands, run through the server's command dispatcher with a source of a given
 * permission level (OPS-003, OPS-004, OPS-008 to OPS-011, GEO-032).
 *
 * <p>Like {@code AtmosTests}, each test builds in its own area near the origin (cell ids cover only
 * about ±8.4M blocks) without forcing chunks, and finishes within one game tick: it drives atmos
 * ticks itself with {@link AtmosService#tickNow}, and waits for results the atmos worker hands
 * back with a bounded {@link MinecraftServer#managedBlock}, which runs the server's queued tasks.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OpsTests {
    private static final double REL = 1e-9;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final List<OpenAirGasEvent> EVENTS = new CopyOnWriteArrayList<>();
    private static final Map<GameTestHelper, BlockPos> AREAS = new ConcurrentHashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent e) -> EVENTS.add(e));
    }

    private OpsTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** Collects what commands send to their source. */
    private static final class Capture implements CommandSource {
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

        int count(String needle) {
            String t = text();
            int n = 0;
            for (int i = t.indexOf(needle); i >= 0; i = t.indexOf(needle, i + 1)) {
                n++;
            }
            return n;
        }

        void clear() {
            messages.clear();
        }
    }

    private static CommandSourceStack stack(GameTestHelper h, Capture capture, int permission) {
        return new CommandSourceStack(
                capture,
                Vec3.atCenterOf(AREAS.getOrDefault(h, BlockPos.ZERO)),
                Vec2.ZERO,
                h.getLevel(),
                permission,
                "zas_test",
                Component.literal("zas_test"),
                h.getLevel().getServer(),
                null);
    }

    private static int run(CommandSourceStack source, String command) {
        try {
            return source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
    }

    private static void refused(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return;
        }
        throw new GameTestAssertException("/" + command + " ran at permission level " + permissionOf(source));
    }

    private static int permissionOf(CommandSourceStack source) {
        for (int level = 4; level >= 0; level--) {
            if (source.hasPermission(level)) {
                return level;
            }
        }
        return -1;
    }

    /** Waits, running the server's queued tasks, until the condition holds or 10 s pass. */
    private static void await(GameTestHelper h, BooleanSupplier done) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        h.getLevel().getServer().managedBlock(() -> done.getAsBoolean() || System.nanoTime() > deadline);
    }

    private static void awaitText(GameTestHelper h, Capture c, String needle) {
        await(h, () -> c.text().contains(needle));
        h.assertTrue(c.text().contains(needle), "no \"" + needle + "\" in:\n" + c.text());
    }

    /** Runs atmos ticks until a command reports back with {@code needle}. */
    private static void settle(GameTestHelper h, Capture c, String needle) {
        for (int i = 0; i < 8 && !c.text().contains(needle); i++) {
            tick(h);
        }
        h.assertTrue(c.text().contains(needle), "no \"" + needle + "\" within 8 atmos ticks in:\n" + c.text());
    }

    private static AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    private static void tick(GameTestHelper h) {
        service().tickNow(h.getLevel());
    }

    private static <T> T settle(GameTestHelper h, CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick(h);
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /** Gives a test its own building area near the origin, away from the other test classes'. */
    private static void area(GameTestHelper h, int index) {
        AREAS.put(h, new BlockPos(4096 + 256 * index, 100, -4096));
    }

    private static BlockPos abs(GameTestHelper h, int x, int y, int z) {
        return AREAS.get(h).offset(x, y, z);
    }

    private static String coords(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String coords(GameTestHelper h, int x, int y, int z) {
        return coords(abs(h, x, y, z));
    }

    /** Clears a box to air with stone walls: a sealed room with interior (1..3)³ for (0, 0, 0) to (4, 4, 4). */
    private static void shell(GameTestHelper h, int x1, int y1, int z1) {
        for (BlockPos p : BlockPos.betweenClosed(abs(h, 0, 0, 0), abs(h, x1, y1, z1))) {
            BlockPos r = p.subtract(AREAS.get(h));
            boolean wall = r.getX() == 0
                    || r.getX() == x1
                    || r.getY() == 0
                    || r.getY() == y1
                    || r.getZ() == 0
                    || r.getZ() == z1;
            h.getLevel().setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    private static ZoneInfo zone(GameTestHelper h, int x, int y, int z) {
        return Atmospheres.zoneAt(h.getLevel(), abs(h, x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    private static double ambient(ResourceLocation gas, double litres) {
        return Ambients.ambient(Level.OVERWORLD).moles(gas.toString(), litres);
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** A sealed 3 × 3 × 3 room at (1..3)³ of the area, zoned by an add of 10 mol O2. */
    private static ZoneInfo zonedRoom(GameTestHelper h) {
        shell(h, 4, 4, 4);
        settle(
                h,
                GasMutations.add(
                        h.getLevel(),
                        Vec3.atCenterOf(abs(h, 2, 2, 2)),
                        new GasParcel(Map.of(O2, 10.0), StandardAir.TEMPERATURE),
                        SourceId.of(ZasMod.MOD_ID, "ops_tests")));
        return zone(h, 2, 2, 2);
    }

    // ---- OPS-011 ------------------------------------------------------------------------

    /** A source below the configured level can't run {@code /zas}; the level is configurable (OPS-011). */
    @GameTest(template = "empty")
    public static void ops_011_non_operator_is_refused(GameTestHelper h) {
        Capture c = new Capture();
        h.assertValueEqual(ZasServerConfig.permissionLevel(), 2, "default permission level");
        refused(stack(h, c, 0), "zas zones");
        refused(stack(h, c, 1), "zas checker");
        run(stack(h, c, 2), "zas zones");
        int was = ZasServerConfig.PERMISSION_LEVEL.get();
        try {
            ZasServerConfig.PERMISSION_LEVEL.set(0);
            run(stack(h, c, 0), "zas zones");
            ZasServerConfig.PERMISSION_LEVEL.set(3);
            refused(stack(h, c, 2), "zas zones");
            run(stack(h, c, 3), "zas zones");
        } finally {
            ZasServerConfig.PERMISSION_LEVEL.set(was);
        }
        h.succeed();
    }

    // ---- OPS-003 ------------------------------------------------------------------------

    /** {@code /zas at}, {@code /zas zone} and {@code /zas zones} report the atmosphere and zones (OPS-003). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ops_003_inspection_commands(GameTestHelper h) {
        area(h, 0);
        ZoneInfo z = zonedRoom(h);
        long id = z.id().value();
        Capture c = new Capture();
        CommandSourceStack op = stack(h, c, 2);

        run(op, "zas at " + coords(h, 2, 2, 2));
        String at = c.text();
        h.assertTrue(at.contains("Atmosphere at " + coords(h, 2, 2, 2)), "header: " + at);
        h.assertTrue(at.contains("zoned, zone " + id), "state and zone: " + at);
        h.assertTrue(at.contains("Oxygen (zas:oxygen)"), "mixture: " + at);
        h.assertTrue(at.contains("Nitrogen (zas:nitrogen)"), "mixture: " + at);

        c.clear();
        run(op, "zas at " + coords(h, 2, 8, 2));
        h.assertTrue(c.text().contains("ambient, zone -"), "open air: " + c.text());

        c.clear();
        run(op, "zas zone " + id);
        String byId = c.text();
        h.assertTrue(byId.contains("Zone " + id + ": 27 cells, 27000 L, enclosed, active"), "zone: " + byId);
        h.assertTrue(byId.contains("Edges: 0 to zones, 0 to the ambient, 0 open faces"), "edges: " + byId);
        h.assertTrue(byId.contains(ZasCommands.num(z.mixture().moles(O2)) + " mol"), byId);

        c.clear();
        run(op, "zas zone at " + coords(h, 1, 1, 1));
        h.assertValueEqual(c.text(), byId, "zone at a point");

        c.clear();
        run(op, "zas zones");
        h.assertTrue(c.text().contains("Zone " + id + ": 27 cells"), "zones: " + c.text());

        c.clear();
        try {
            op.getServer().getCommands().getDispatcher().execute("zas zone at " + coords(h, 2, 8, 2), op);
            throw new GameTestAssertException("/zas zone at open air succeeded");
        } catch (CommandSyntaxException e) {
            h.assertTrue(e.getMessage().contains("No zone there"), e.getMessage());
        }
        h.succeed();
    }

    // ---- OPS-009 ------------------------------------------------------------------------

    /**
     * {@code /zas gas} adds, removes and sets gas at a point and fills or vents a region, through
     * the API and attributed to {@code command:<player>} (OPS-009).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ops_009_gas_editing_commands(GameTestHelper h) {
        area(h, 1);
        shell(h, 4, 4, 4);
        tick(h);
        Capture c = new Capture();
        CommandSourceStack player = stack(h, c, 2).withEntity(h.makeMockPlayer(GameType.CREATIVE));
        String by = "command:test-mock-player";
        String p = coords(h, 2, 2, 2);

        run(player, "zas gas add " + p + " zas:oxygen 10");
        h.assertTrue(c.text().contains("Queued for the next atmos tick, as " + by), c.text());
        settle(h, c, "Added");
        h.assertTrue(c.text().contains("Added 10 mol of Oxygen at " + p + ", into a zone (by " + by + ")"), c.text());
        double o2 = ambient(O2, 27_000);
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), o2 + 10, "O2 after add");

        c.clear();
        run(player, "zas gas remove " + p + " zas:oxygen 5");
        settle(h, c, "Removed");
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), o2 + 5, "O2 after remove");

        c.clear();
        run(player, "zas gas set " + p + " zas:oxygen 100");
        settle(h, c, "Set Oxygen");
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), 100, "O2 after set");

        c.clear();
        run(player, "zas gas fill " + coords(h, 0, 0, 0) + " " + coords(h, 4, 4, 4) + " zas:carbon_dioxide 1");
        h.assertTrue(c.text().contains("Queued additions at 27 positions"), "walls aren't filled: " + c.text());
        settle(h, c, "Filled");
        close(h, zone(h, 2, 2, 2).mixture().moles(CO2), 27, "CO2 after fill");

        c.clear();
        run(player, "zas gas vent " + coords(h, 1, 1, 1) + " " + coords(h, 3, 3, 3) + " zas:carbon_dioxide");
        settle(h, c, "Vented");
        h.assertTrue(zone(h, 2, 2, 2).mixture().moles(CO2) < 1e-9, "CO2 after vent");
        close(h, zone(h, 2, 2, 2).mixture().moles(O2), 100, "O2 kept by a CO2 vent");

        c.clear();
        run(player, "zas gas vent " + coords(h, 1, 1, 1) + " " + coords(h, 3, 3, 3));
        settle(h, c, "Vented");
        Optional<ZoneInfo> after = Atmospheres.zoneAt(h.getLevel(), abs(h, 2, 2, 2));
        h.assertTrue(after.isEmpty() || after.get().mixture().totalMoles() < 1e-6, "gas left after a full vent");

        // Attribution in the open air, where the open-air event names the source (CORE-058).
        c.clear();
        int before = EVENTS.size();
        run(player, "zas gas add " + coords(h, 2, 10, 2) + " zas:oxygen 3");
        settle(h, c, "Added");
        h.assertTrue(c.text().contains("into the ambient"), c.text());
        boolean attributed = false;
        for (OpenAirGasEvent e : EVENTS.subList(before, EVENTS.size())) {
            attributed |= e.source().toString().equals(by) && e.pos().equals(abs(h, 2, 10, 2));
        }
        h.assertTrue(attributed, "no open-air event attributed to " + by);

        // The server console is attributed as command:server.
        c.clear();
        run(stack(h, c, 4), "zas gas add " + coords(h, 2, 10, 2) + " zas:oxygen 1");
        h.assertTrue(c.text().contains("as command:server"), c.text());
        settle(h, c, "Added");
        h.succeed();
    }

    // ---- OPS-010, GEO-032 -----------------------------------------------------------------

    /**
     * A block change the engine never saw leaves a zone holding a cell that is now stone; {@code
     * /zas audit} reports it, {@code repair} sends the section again, and the next audit is clean
     * (GEO-032, OPS-010).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_032_audit_reports_corrupt_membership(GameTestHelper h) {
        area(h, 2);
        ZoneInfo z = zonedRoom(h);
        tick(h);
        Capture c = new Capture();
        CommandSourceStack op = stack(h, c, 2);
        String region = coords(h, -1, -1, -1) + " " + coords(h, 5, 5, 5);

        run(op, "zas audit " + region);
        awaitText(h, c, "Geometry audit:");
        h.assertTrue(c.text().contains("Geometry audit: no differences"), "before the corruption: " + c.text());

        // Corrupt the zone: place stone in it and drop the change before the engine sees it.
        BlockPos stone = abs(h, 2, 1, 2);
        h.getLevel().setBlockAndUpdate(stone, STONE);
        ChangeTracker.drainWithin(h.getLevel(), new BoundingBox(stone));
        tick(h);
        h.assertValueEqual(zone(h, 2, 2, 2).cellCount(), 27, "the engine saw the change");

        c.clear();
        run(op, "zas audit " + region);
        awaitText(h, c, "Geometry audit:");
        String report = c.text();
        h.assertTrue(report.contains("Geometry audit: 2 differences"), report);
        h.assertTrue(report.contains("Stale geometry: 1"), report);
        h.assertTrue(report.contains("Zoned cell the blocks don't have: 1"), report);
        h.assertTrue(report.contains("Stale geometry at " + coords(stone) + " (zone -)"), report);
        h.assertTrue(
                report.contains(
                        "Zoned cell the blocks don't have at " + coords(stone) + " (zone " + z.id().value() + ")"),
                report);

        c.clear();
        run(op, "zas audit " + region + " repair");
        awaitText(h, c, "Sending 1 stale sections");
        tick(h);
        tick(h);
        h.assertValueEqual(zone(h, 2, 2, 2).cellCount(), 26, "the repair didn't reach the zone");

        c.clear();
        run(op, "zas audit " + region);
        awaitText(h, c, "Geometry audit:");
        h.assertTrue(c.text().contains("Geometry audit: no differences"), "after the repair: " + c.text());
        h.succeed();
    }

    /** {@code /zas checker} switches the invariant checker, runs it on demand and prints its report (OPS-010). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ops_010_checker_commands(GameTestHelper h) {
        area(h, 3);
        zonedRoom(h);
        Capture c = new Capture();
        CommandSourceStack op = stack(h, c, 2);
        DimensionAtmos d = service().dimensionFor(h.getLevel());
        try {
            run(op, "zas checker off");
            h.assertFalse(ZasServerConfig.conservationChecker(), "off");
            run(op, "zas checker on");
            h.assertTrue(ZasServerConfig.conservationChecker(), "on");
            h.assertTrue(c.text().contains("The invariant checker is on (set by command"), c.text());
            tick(h);
            ConservationReport report = d.lastConservation();
            h.assertTrue(report != null, "the checker didn't run");
            c.clear();
            run(op, "zas checker report");
            h.assertTrue(c.text().contains("Invariant check of atmos tick " + report.tick() + ": conserved"), c.text());

            run(op, "zas checker off");
            h.assertFalse(ZasServerConfig.conservationChecker(), "off again");
            c.clear();
            run(op, "zas checker run");
            h.assertTrue(c.text().contains("runs on the next atmos tick"), c.text());
            settle(h, c, "Invariant check of atmos tick");
            h.assertTrue(c.text().contains(": conserved (0 violations)"), c.text());
        } finally {
            run(op, "zas checker config");
        }
        h.assertFalse(ZasServerConfig.conservationCheckerOverridden(), "the override stayed");
        h.succeed();
    }

    // ---- OPS-004 ------------------------------------------------------------------------

    /** {@code /zas profile} reports phase timings, server-thread time and counts (OPS-004). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ops_004_profile_command(GameTestHelper h) {
        area(h, 4);
        zonedRoom(h);
        tick(h);
        Capture c = new Capture();
        CommandSourceStack op = stack(h, c, 2);
        run(op, "zas profile");
        awaitText(h, c, "Atmos profile of minecraft:overworld");
        await(h, () -> c.text().contains("Queues:"));
        String text = c.text();
        for (String needle : List.of(
                "Server thread per game tick:",
                "p99",
                "Close (server thread):",
                "Apply (server thread):",
                "Atmos tick (worker):",
                "Geometry:",
                "Exchange:",
                "Heat:",
                "Zone updates (reactions):",
                "hotspots",
                "pending classifications",
                "paused zones",
                "Queues:")) {
            h.assertTrue(text.contains(needle), "no \"" + needle + "\" in:\n" + text);
        }
        h.assertTrue(!text.contains("No engine"), "the overworld has an engine: " + text);
        c.clear();
        run(op, "zas profile reset");
        h.assertTrue(service().dimensionFor(h.getLevel()).profiler().samples() == 0, "reset");
        h.succeed();
    }

    // ---- OPS-008 ------------------------------------------------------------------------

    /**
     * Invalid entries in each datapack format are reported naming the file and the field, and
     * skipped; the rest of the pack loads (OPS-008).
     */
    @GameTest(template = "empty")
    public static void ops_008_invalid_entries_name_file_and_field(GameTestHelper h) {
        List<DatapackErrors.Problem> problems = DatapackErrors.recent();
        expect(h, problems, "gas definition", "data/zas_gametest/zas/gas/invalid_molar_mass.json", "molar_mass: ");
        expect(
                h,
                problems,
                "gas definition",
                "data/zas_gametest/zas/gas/invalid_negative_heat_capacity.json",
                "molar_heat_capacity: must be finite and positive");
        expect(
                h,
                problems,
                "geometry override",
                "data/zas_gametest/zas/geometry_overrides/invalid_face.json",
                "sealed_faces: ");
        expect(
                h,
                problems,
                "ambient definition",
                "data/zas_gametest/zas/ambient/invalid_no_temperature.json",
                "temperature is required");
        var registry = ServerGasRegistry.current();
        h.assertTrue(registry.find("zas_gametest:test_gas").isPresent(), "the valid gas didn't load");
        h.assertFalse(registry.find("zas_gametest:invalid_molar_mass").isPresent(), "an invalid gas loaded");
        h.assertTrue(
                Ambients.definition(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                ResourceLocation.fromNamespaceAndPath("zas_gametest", "ambient_test")))
                        .isPresent(),
                "the valid ambient didn't load");

        Capture c = new Capture();
        run(stack(h, c, 2), "zas datapacks");
        h.assertTrue(
                c.text()
                        .contains("Skipping gas definition data/zas_gametest/zas/gas/invalid_molar_mass.json: "
                                + "molar_mass: "),
                c.text());
        h.succeed();
    }

    private static void expect(
            GameTestHelper h, List<DatapackErrors.Problem> problems, String kind, String file, String start) {
        for (DatapackErrors.Problem p : problems) {
            if (p.kind().equals(kind) && p.file().equals(file)) {
                h.assertTrue(p.skipped(), file + " wasn't skipped");
                h.assertTrue(p.message().startsWith(start), file + ": \"" + p.message() + "\" doesn't name " + start);
                return;
            }
        }
        throw new GameTestAssertException("no problem reported for " + file + " in " + problems);
    }
}
