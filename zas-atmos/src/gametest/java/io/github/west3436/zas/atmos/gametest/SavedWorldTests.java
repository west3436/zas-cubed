package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.persistence.DimensionData;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A world saved by an earlier release loads in a running game with everything it held (DATA-012).
 * The unit tests {@code SavedWorldTest} and {@code SavedWorldDataTest} read the same files without
 * the game.
 *
 * <p><b>How the old world gets here.</b> The build copies the release's fixture into this server's
 * new world folder before it starts ({@code zas-atmos/build.gradle}): the station's chunks in the
 * region files, the mods' files in {@code data}, and {@code expected.json}, what the release's
 * console said the station held. So this server opens the files as a server upgraded from the
 * release does: the dimension's data when its simulation starts, and the chunks when they load.
 * Nothing forces them, so they stay away until this test holds them, far from every other test's
 * place. The fixture and how it is made are described in the framework's test resources, at {@code
 * SavedWorld}.
 *
 * <p>The station's chunks are held loaded in full without ticking, as S-09's station is, so nothing
 * steps and the primed TTV's fuse doesn't burn: what is compared is what the files held. It has a
 * batch of its own, as it turns the central controllers' {@code force_load} off and the invariant
 * checker on.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SavedWorldTests {
    private static final String BATCH = "zas_atmos_saved_world";

    /** Game ticks for a few atmos ticks to pass. */
    private static final int SETTLE = 3 * AtmosTicks.PERIOD;

    /** The area air controller in 0.1.0's room R, and the central controller that claimed it. */
    private static final BlockPos CONTROLLER = new BlockPos(643, -59, 2);

    private static final BlockPos CONSOLE = new BlockPos(653, -60, 3);

    /** 0.1.0's chest of items with every data component. */
    private static final BlockPos CHEST = new BlockPos(642, -60, 8);

    /** The box 0.1.0's console audited: the station's three chunks, from the floor up. */
    private static final String AUDITED = "640 -64 0 687 -50 15";

    /**
     * What a vent or scrubber saves only while a controller's orders keep coming, to show in {@code
     * /data get block}. Neither loads it, on 0.1.0 or now: orders don't outlast a reload, and the
     * controller sends them again once its chunk ticks. 0.1.0's airlock vent was under its airlock
     * controller's orders when the server stopped, and nothing ticks here to order it again, so these
     * are the only saved keys that don't come back.
     */
    private static final List<String> ORDERS = List.of("Controlled", "Controller");

    private static final List<String> ORDERED = List.of("zas_atmos:vent", "zas_atmos:scrubber");

    /** What the batch's end undoes, even if the test failed or timed out. */
    private static final List<Runnable> CLEANUP = new ArrayList<>();

    private SavedWorldTests() {}

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        CLEANUP.forEach(Runnable::run);
        CLEANUP.clear();
    }

    /** What a command answered. */
    private static final class Answers implements CommandSource {
        final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message.getString());
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

        Optional<String> line(String start) {
            return lines.stream().filter(l -> l.startsWith(start)).findFirst();
        }
    }

    /** A number as a release's console prints it: six significant digits. */
    private static String printed(double value) {
        return new BigDecimal(value)
                .round(new MathContext(6))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static BlockPos pos(String xyz) {
        String[] p = xyz.split(" ");
        return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    private static CompoundTag tag(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("the fixture's saved data doesn't parse: " + e.getMessage());
        }
    }

    /** What the release's console said its world held, which the build put in this world's folder. */
    private static JsonObject expected(ServerLevel level) {
        Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("expected.json");
        if (!Files.exists(file)) {
            throw new GameTestAssertException("this world wasn't started from 0.1.0's fixture: there's no " + file
                    + ". :zas-atmos:runGameTestServer copies it in");
        }
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException("can't read " + file + ": " + e);
        }
    }

    /**
     * 0.1.0's station comes back from its files: all three zones with their gas, R's pin, every
     * block entity's saved data, the chest's items, the central controller's claim and the primed
     * TTV. Then a geometry audit of the station finds no differences, as it did on 0.1.0, and the
     * invariant checker finds the dimension's gas conserved.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void data_012_a_world_saved_by_0_1_0_loads_with_what_it_held(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        JsonObject expected = expected(level);
        List<ChunkPos> chunks = new ArrayList<>();
        for (JsonElement c : expected.getAsJsonArray("chunks")) {
            chunks.add(new ChunkPos(
                    c.getAsJsonArray().get(0).getAsInt(),
                    c.getAsJsonArray().get(1).getAsInt()));
        }
        for (ChunkPos c : chunks) {
            h.assertFalse(level.getChunkSource().hasChunk(c.x, c.z), "chunk " + c + " loaded before this test");
        }
        int corrupt = AtmosPersistence.corruptionWarnings().size();
        int dropped = AtmosPersistence.droppedGasWarnings().size();

        boolean forceLoad = AtmosServerConfig.get(AtmosServerConfig.CENTRAL_FORCE_LOAD);
        AtmosServerConfig.CENTRAL_FORCE_LOAD.set(false); // the console would hold its chunks ticking
        CLEANUP.add(() -> AtmosServerConfig.CENTRAL_FORCE_LOAD.set(forceLoad));
        boolean checker = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        CLEANUP.add(() -> ZasServerConfig.CONSERVATION_CHECKER.set(checker));
        chunks.forEach(c -> PipeSaveTests.hold(level, c, true));
        CLEANUP.add(0, () -> chunks.forEach(c -> PipeSaveTests.hold(level, c, false)));

        JsonObject zones = expected.getAsJsonObject("zones");
        Answers audit = new Answers();
        long[] checkedBefore = {Long.MIN_VALUE}; // the atmos tick last checked when the station was back
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (ChunkPos c : chunks) {
                        h.assertTrue(level.getChunkSource().hasChunk(c.x, c.z), "chunk " + c + " hasn't loaded");
                    }
                })
                .thenWaitUntil(() -> {
                    for (Map.Entry<String, JsonElement> e : zones.entrySet()) {
                        long id = e.getValue().getAsJsonObject().get("id").getAsLong();
                        Optional<ZoneInfo> zone = Atmospheres.zone(level, new ZoneId(id));
                        h.assertTrue(zone.isPresent() && !zone.get().paused(), "zone " + e.getKey() + " isn't back");
                    }
                })
                .thenWaitUntil(() -> primed(h, level, chunks))
                .thenExecute(() -> {
                    ConservationReport before = dimension(level).lastConservation();
                    checkedBefore[0] = before == null ? Long.MIN_VALUE : before.tick();
                })
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    h.assertValueEqual(
                            AtmosPersistence.corruptionWarnings().size(), corrupt, "chunks that didn't read");
                    h.assertValueEqual(AtmosPersistence.droppedGasWarnings().size(), dropped, "gases dropped");
                    zones.entrySet()
                            .forEach(e ->
                                    sameZone(h, level, e.getKey(), e.getValue().getAsJsonObject()));
                    sameBlockEntities(h, level, chunks, expected.getAsJsonObject("block_entities"));
                    StationSaveTests.sameTag(
                            h,
                            primed(h, level, chunks).saveWithoutId(new CompoundTag()),
                            tag(expected.get("primed_ttv").getAsString()),
                            "the primed TTV");
                    items(h, level);
                    h.assertValueEqual(
                            CentralClaims.of(level).centralOf(CONTROLLER),
                            Optional.of(CONSOLE),
                            "the central controller of R's area air controller");
                    long highest = 0;
                    for (JsonElement z : zones.asMap().values()) {
                        highest =
                                Math.max(highest, z.getAsJsonObject().get("id").getAsLong());
                    }
                    h.assertTrue(
                            DimensionData.of(level).nextZoneId() > highest,
                            "the next zone id, " + DimensionData.of(level).nextZoneId() + ", is one a saved zone has");

                    CommandSourceStack console = new CommandSourceStack(
                            audit,
                            Vec3.atCenterOf(CONTROLLER),
                            Vec2.ZERO,
                            level,
                            4,
                            "zas_test",
                            Component.literal("zas_test"),
                            level.getServer(),
                            null);
                    try {
                        level.getServer().getCommands().getDispatcher().execute("zas audit " + AUDITED, console);
                    } catch (CommandSyntaxException e) {
                        throw new GameTestAssertException("/zas audit failed: " + e.getMessage());
                    }
                })
                .thenWaitUntil(
                        () -> h.assertTrue(audit.line("Geometry audit:").isPresent(), "no audit yet: " + audit.lines))
                .thenExecute(() -> {
                    h.assertValueEqual(
                            audit.line("Geometry audit:").orElseThrow(),
                            expected.get("audit").getAsString(),
                            "the audit of the station");
                    ConservationReport report = dimension(level).lastConservation();
                    h.assertTrue(
                            report != null && report.tick() > checkedBefore[0],
                            "the invariant checker hasn't run since the station came back");
                    h.assertTrue(report.conserved(), "unexplained gas: " + report.violations());
                })
                .thenSucceed();
    }

    private static DimensionAtmos dimension(ServerLevel level) {
        AtmosService service = AtmosService.current();
        if (service == null || service.dimension(level) == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return service.dimension(level);
    }

    /** The primed TTV in the station's chunks, once its chunk's entities have loaded. */
    private static PrimedTransferValve primed(GameTestHelper h, ServerLevel level, List<ChunkPos> chunks) {
        AABB box = new AABB(
                chunks.get(0).getMinBlockX(),
                level.getMinBuildHeight(),
                chunks.get(0).getMinBlockZ(),
                chunks.get(chunks.size() - 1).getMaxBlockX() + 1,
                level.getMaxBuildHeight(),
                chunks.get(chunks.size() - 1).getMaxBlockZ() + 1);
        List<PrimedTransferValve> found =
                level.getEntities(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), box, e -> true);
        h.assertTrue(found.size() == 1, "expected one primed TTV in the station, found " + found.size());
        return found.get(0);
    }

    /** A zone is as the release's console printed it, and R keeps its pin. */
    private static void sameZone(GameTestHelper h, ServerLevel level, String name, JsonObject told) {
        JsonElement at = told.get("at");
        BlockPos pos = new BlockPos(
                at.getAsJsonArray().get(0).getAsInt(),
                at.getAsJsonArray().get(1).getAsInt(),
                at.getAsJsonArray().get(2).getAsInt());
        ZoneInfo zone = Atmospheres.zoneAt(level, pos)
                .orElseThrow(() -> new GameTestAssertException("no zone in " + name + ", at " + pos));
        String what = "zone " + name;
        h.assertValueEqual(zone.id().value(), told.get("id").getAsLong(), what + "'s id");
        h.assertValueEqual(zone.cellCount(), told.get("cells").getAsInt(), what + "'s cells");
        h.assertValueEqual(printed(zone.volume()), told.get("volume").getAsString(), what + "'s volume");
        h.assertTrue(zone.enclosed() && !zone.paused(), what + " isn't enclosed and active");
        h.assertValueEqual(
                printed(zone.mixture().pressure()), told.get("pressure").getAsString(), what + "'s pressure");
        h.assertValueEqual(
                printed(zone.mixture().temperature()), told.get("temperature").getAsString(), what + "'s temperature");
        h.assertValueEqual(
                printed(zone.mixture().totalMoles()), told.get("moles").getAsString(), what + "'s moles");
        Map<String, String> gases = new TreeMap<>();
        zone.mixture().moles().forEach((gas, moles) -> gases.put(gas.toString(), printed(moles)));
        Map<String, String> toldGases = new TreeMap<>();
        told.getAsJsonObject("gases")
                .entrySet()
                .forEach(g -> toldGases.put(g.getKey(), g.getValue().getAsString()));
        h.assertValueEqual(gases, toldGases, what + "'s gases");
        h.assertValueEqual(
                List.of(
                        zone.edges().zoneEdges(),
                        zone.edges().ambientEdges(),
                        zone.edges().openFaces()),
                List.of(
                        told.getAsJsonArray("edges").get(0).getAsInt(),
                        told.getAsJsonArray("edges").get(1).getAsInt(),
                        told.getAsJsonArray("edges").get(2).getAsInt()),
                what + "'s edges");

        List<String> pins = new ArrayList<>();
        told.getAsJsonArray("pins").forEach(p -> pins.add(p.getAsString()));
        ZoneView view = dimension(level).published().zone(zone.id().value());
        h.assertValueEqual(view == null ? List.of() : view.pins(), pins, what + "'s pins");
    }

    /**
     * Every block entity saves the data the release's console showed for it, numbers within 1e-9, and
     * the station's chunks hold no others. The one exception is {@link #ORDERS}. Every block entity
     * is compared before any failure is reported, so one run names all that differ.
     */
    private static void sameBlockEntities(GameTestHelper h, ServerLevel level, List<ChunkPos> chunks, JsonObject told) {
        int loaded = 0;
        for (ChunkPos c : chunks) {
            loaded += level.getChunk(c.x, c.z).getBlockEntities().size();
        }
        h.assertValueEqual(loaded, told.size(), "the block entities in the station's chunks");
        List<String> differ = new ArrayList<>();
        int underOrders = 0;
        for (Map.Entry<String, JsonElement> e : told.entrySet()) {
            BlockEntity be = level.getBlockEntity(pos(e.getKey()));
            if (be == null) {
                differ.add("no block entity at " + e.getKey());
                continue;
            }
            CompoundTag then = tag(e.getValue().getAsString());
            if (ORDERED.contains(then.getString("id")) && then.contains(ORDERS.get(0))) {
                ORDERS.forEach(then::remove);
                underOrders++;
            }
            try {
                StationSaveTests.sameTag(h, be.saveWithFullMetadata(level.registryAccess()), then, "at " + e.getKey());
            } catch (GameTestAssertException difference) {
                differ.add(difference.getMessage());
            }
        }
        h.assertTrue(differ.isEmpty(), differ.size() + " block entities differ: " + differ);
        h.assertValueEqual(underOrders, 1, "the devices that were under a controller's orders when 0.1.0 stopped");
    }

    /** The chest's stacks hold what they were given on 0.1.0, read as the game reads them. */
    private static void items(GameTestHelper h, ServerLevel level) {
        if (!(level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest)) {
            throw new GameTestAssertException("no chest at " + CHEST);
        }
        h.assertValueEqual(
                chest.getItem(0).get(AtmosComponents.GAS.get()),
                new GasParcel(Map.of(O2, 5.5, N2, 0.125), 293.0),
                "the tank's gas");
        ItemStack valve = chest.getItem(1);
        h.assertValueEqual(TransferValveItem.tankCount(valve), 2, "the TTV's tanks");
        h.assertValueEqual(TransferValveItem.timer(valve), 450, "the TTV's timer");
        h.assertValueEqual(
                TransferValveItem.tank(valve, 1).get(AtmosComponents.GAS.get()),
                new GasParcel(Map.of(ResourceLocation.fromNamespaceAndPath("zas", "hydrogen"), 2.25), 293.0),
                "the TTV's second tank");
        h.assertValueEqual(chest.getItem(4).get(AtmosComponents.ENERGY.get()), 2468, "the battery's charge");
        h.assertValueEqual(
                MultitoolItem.selected(chest.getItem(7), level), Optional.of(CONTROLLER), "the multitool's selection");
    }
}
