package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.testkit.ListeningPlayer;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.miner.GasMinerBlock;
import io.github.west3436.zas.atmos.miner.GasMinerBlockEntity;
import io.github.west3436.zas.atmos.miner.GasMinerMenu;
import io.github.west3436.zas.atmos.miner.GasMinerStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The creative gas miner (CONT-180 to CONT-183). Each test builds in its own {@link Area}, 5900 and
 * up, and drives device steps and atmos ticks itself, in a batch of its own; the one with the
 * conservation checker on has a batch to itself, as the ledger covers the whole dimension.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasMinerTests {
    private static final String BATCH = "zas_atmos_gas_miner";
    private static final String CHECKER_BATCH = "zas_atmos_gas_miner_checker";

    /** The room's air volume: the 3 × 3 × 3 inside of {@link Area#box}. */
    private static final double ROOM_LITRES = 27_000;

    private GasMinerTests() {}

    private static BlockState miner(Direction facing) {
        return AtmosBlocks.GAS_MINER.get().defaultBlockState().setValue(GasMinerBlock.FACING, facing);
    }

    /** A sealed 3 × 3 × 3 room of ambient air, with a miner in its west wall facing in, switched on. */
    private static GasMinerBlockEntity rig(Area a) {
        a.box(0, 0, 4, 4);
        a.place(0, 2, 2, miner(Direction.EAST));
        GasMinerBlockEntity m = a.entity(0, 2, 2);
        m.setOn(true);
        return m;
    }

    /** Steps until it stops filling and has said so, at most {@code most} times. */
    private static void untilDone(Area a, GasMinerBlockEntity m, int most) {
        for (int i = 0;
                i < most
                        && (m.status() == GasMinerStatus.WAITING
                                || m.status() == GasMinerStatus.FILLING
                                || m.status() == GasMinerStatus.OFF);
                i++) {
            a.step();
        }
    }

    private static double pressure(Area a) {
        return a.zone(2, 2, 2).mixture().pressure();
    }

    private static void near(GameTestHelper h, double actual, double expected, double within, String what) {
        h.assertTrue(Math.abs(actual - expected) <= within, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * CONT-180: switched on, a miner fills a sealed room with its gas until the room is at its target,
     * then stops: its state reads At target, and further steps add nothing. It adds at most its rate a
     * second, so the room gets there in more than one step. Its block shows it on.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_180_a_miner_fills_a_sealed_room_to_its_target_and_stops(GameTestHelper h) {
        Area a = new Area(h, 5900);
        GasMinerBlockEntity m = rig(a);
        h.assertTrue(a.get(0, 2, 2).getValue(GasMinerBlock.ON), "its block doesn't show it on");
        h.assertValueEqual(m.gas(), GasMinerBlockEntity.DEFAULT_GAS, "a new miner's gas");
        m.setGas(N2);
        m.setTarget(250);
        int filling = 0;
        for (int i = 0; i < 60 && m.status() != GasMinerStatus.AT_TARGET; i++) {
            a.step();
            if (m.status() == GasMinerStatus.FILLING) {
                filling++;
            }
        }
        h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "its state");
        h.assertTrue(filling > 1, "it filled in " + filling + " step(s), past its rate");
        GasMixture room = a.zone(2, 2, 2).mixture();
        h.assertValueEqual(room.volume(), ROOM_LITRES, "the room's volume");
        near(h, room.pressure(), 250, 0.01, "the room's pressure at its target");
        double moles = room.totalMoles();
        for (int i = 0; i < 10; i++) {
            a.step();
        }
        a.close(a.zone(2, 2, 2).mixture().totalMoles(), moles, "the room's moles once it stopped");
        h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "its state, still");

        m.setOn(false);
        a.step();
        h.assertValueEqual(m.status(), GasMinerStatus.OFF, "its state, switched off");
        h.assertFalse(a.get(0, 2, 2).getValue(GasMinerBlock.ON), "its block shows it on, switched off");
        h.succeed();
    }

    /**
     * CONT-181: a miner's gas, target and temperature are its own to set. A new gas goes in on top of
     * what is there until the room is at the new target, and what was there stays. Gas it makes is at
     * its temperature. It takes only a gas the server defines, and clamps its target and temperature.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_181_changing_its_gas_adds_the_new_one_to_the_new_target(GameTestHelper h) {
        Area a = new Area(h, 5901);
        GasMinerBlockEntity m = rig(a);
        m.setTarget(150);
        untilDone(a, m, 60);
        h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "its state with O2");
        GasMixture before = a.zone(2, 2, 2).mixture();
        near(h, before.pressure(), 150, 0.01, "the room's pressure with O2");

        h.assertTrue(m.setGas(N2), "N2 was refused");
        m.setTarget(300);
        m.setTemperature(400);
        a.step();
        untilDone(a, m, 60);
        GasMixture after = a.zone(2, 2, 2).mixture();
        near(h, after.pressure(), 300, 3, "the room's pressure with N2");
        a.close(after.moles(O2), before.moles(O2), "O2 in the room after the change");
        h.assertTrue(after.moles(N2) > before.moles(N2), "no N2 went in");
        h.assertTrue(after.temperature() > before.temperature() + 1, "gas at 400 K didn't warm the room");

        ResourceLocation none = ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "no_such_gas");
        h.assertFalse(m.setGas(none), "an undefined gas was taken");
        h.assertValueEqual(m.gas(), N2, "its gas after an undefined one");
        m.setTarget(-5);
        h.assertValueEqual(m.target(), 0.0, "a target below 0");
        m.setTarget(1e9);
        h.assertValueEqual(m.target(), GasMinerBlockEntity.maxTarget(), "a target past its highest");
        m.setTemperature(0);
        h.assertValueEqual(m.temperature(), GasMinerBlockEntity.MIN_TEMPERATURE, "a temperature below its lowest");
        h.succeed();
    }

    /**
     * CONT-181: its window offers every gas the server defines. A button names a gas by its place in
     * that list, so the target, temperature and gas buttons set the miner, and an id past the list is
     * refused. The client's menu reads the list the server wrote.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_181_its_window_sets_its_gas_target_and_temperature(GameTestHelper h) {
        Area a = new Area(h, 5902);
        GasMinerBlockEntity m = rig(a);
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer p = new ServerPlayer(
                a.level.getServer(),
                a.level,
                new GameProfile(UUID.randomUUID(), "zas_gas_miner"),
                ClientInformation.createDefault());
        new ListeningPlayer.Recording(a.level, p, sent);
        p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        p.openMenu(m, m::writeMenu);
        h.assertTrue(p.containerMenu instanceof GasMinerMenu, "it opened " + p.containerMenu);
        GasMinerMenu menu = (GasMinerMenu) p.containerMenu;
        List<ResourceLocation> gases = menu.gases();
        h.assertValueEqual(gases, GasMinerBlockEntity.gases(), "the gases it offers");
        h.assertTrue(gases.contains(N2) && gases.contains(O2), "it offers " + gases);

        h.assertTrue(menu.clickMenuButton(p, GasMinerMenu.gasButton(gases.indexOf(N2))), "N2's button was refused");
        h.assertValueEqual(m.gas(), N2, "its gas after N2's button");
        h.assertFalse(
                menu.clickMenuButton(p, GasMinerMenu.gasButton(gases.size())), "a button past the list was taken");
        h.assertTrue(menu.clickMenuButton(p, GasMinerMenu.TARGET_DOWN_100 + 3), "+100 kPa was refused");
        a.close(m.target(), GasMinerBlockEntity.DEFAULT_TARGET + 100, "its target after +100 kPa");
        h.assertTrue(menu.clickMenuButton(p, GasMinerMenu.TARGET_DEFAULT), "Default was refused");
        a.close(m.target(), GasMinerBlockEntity.DEFAULT_TARGET, "its target after Default");
        h.assertTrue(menu.clickMenuButton(p, GasMinerMenu.TEMPERATURE_DOWN_100), "−100 K was refused");
        a.close(m.temperature(), GasMinerBlockEntity.DEFAULT_TEMPERATURE - 100, "its temperature after −100 K");
        h.assertTrue(menu.clickMenuButton(p, GasMinerMenu.TOGGLE), "the switch was refused");
        h.assertFalse(m.isOn(), "the switch left it on");
        h.assertValueEqual(menu.value(GasMinerMenu.DATA_GAS), gases.indexOf(N2), "the window's gas");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
        m.writeMenu(buf);
        GasMinerMenu client = new GasMinerMenu(1, p.getInventory(), buf);
        h.assertValueEqual(client.gases(), gases, "the client's list");
        p.closeContainer();
        h.succeed();
    }

    /**
     * CONT-182: what a miner adds is the API's to account for. With the conservation checker on,
     * every report while it fills is conserved, and the ledger's API category holds just what the
     * room gained.
     */
    @GameTest(template = "empty", batch = CHECKER_BATCH)
    public static void cont_182_what_it_adds_is_in_the_ledger(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            Area a = new Area(h, 5903);
            GasMinerBlockEntity m = rig(a);
            m.setTarget(120);
            untilDone(a, m, 60);
            int n2 = a.service().dimension(a.level).registry().indexOf(N2.toString());
            m.setGas(N2);
            m.setTarget(200);
            double start = a.zone(2, 2, 2).mixture().moles(N2);
            double recorded = 0;
            long read = Long.MIN_VALUE;
            for (int i = 0; i < 40; i++) {
                a.step();
                ConservationReport report = a.service().dimension(a.level).lastConservation();
                h.assertTrue(report != null, "the checker didn't run");
                h.assertTrue(report.conserved(), "unexplained: " + report.violations());
                if (report.tick() != read) {
                    read = report.tick();
                    recorded += report.recorded(LedgerCategory.API, n2);
                }
            }
            h.assertValueEqual(m.status(), GasMinerStatus.AT_TARGET, "its state");
            double gained = a.zone(2, 2, 2).mixture().moles(N2) - start;
            h.assertTrue(gained > 0, "the room gained no N2");
            near(h, recorded, gained, 1e-6 * gained, "N2 recorded under the API");
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
        h.succeed();
    }

    /**
     * CONT-181: in the open air, a miner's gas goes to the ambient and is lost. It then waits {@code
     * [gas_miner] open_air_wait} seconds before it tries again, saying Open air, so it costs the
     * engine one addition of at most its rate in that time.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_181_in_the_open_air_it_adds_once_and_waits(GameTestHelper h) {
        Area a = new Area(h, 5904);
        a.clear(-1, -1, 5, 5);
        a.place(2, 1, 2, miner(Direction.UP));
        GasMinerBlockEntity m = a.entity(2, 1, 2);
        m.setTarget(200); // above the open air's, which a new miner's target is
        m.setOn(true);
        h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 2, 2)).isEmpty(), "the open air has a zone");
        for (int i = 0; i < 20 && m.status() != GasMinerStatus.OPEN_AIR; i++) {
            a.step();
        }
        h.assertValueEqual(m.status(), GasMinerStatus.OPEN_AIR, "its state in the open");
        int wait = AtmosServerConfig.get(AtmosServerConfig.GAS_MINER_OPEN_AIR_WAIT);
        h.assertValueEqual(m.openAirWait(), wait, "the atmos ticks it waits");
        for (int i = 1; i < wait; i++) {
            a.step();
            h.assertValueEqual(m.openAirWait(), wait - i, "its wait after " + i + " steps");
            h.assertValueEqual(m.status(), GasMinerStatus.OPEN_AIR, "its state while it waits");
        }
        a.step(); // its last step of waiting
        h.assertValueEqual(m.openAirWait(), 0, "its wait at the end");
        a.step(); // it tries again, and is sent back to wait
        h.assertValueEqual(m.openAirWait(), wait, "its wait after it tried again");
        h.assertTrue(Atmospheres.zoneAt(a.level, a.abs(2, 2, 2)).isEmpty(), "the open air got a zone");
        h.succeed();
    }

    /**
     * CONT-183: only creative mode has a miner. It has no recipe (RecipeTests) and is in the creative
     * tab once. Survival can't break it, as it can't bedrock, and broken it drops nothing. A blast
     * doesn't break it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_183_a_miner_is_creative_only(GameTestHelper h) {
        Area a = new Area(h, 5905);
        a.clear(-1, -1, 5, 5);
        a.place(2, 1, 2, miner(Direction.NORTH));
        BlockPos pos = a.abs(2, 1, 2);
        BlockState state = a.level.getBlockState(pos);

        CreativeModeTab tab = AtmosCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(a.level.enabledFeatures(), false, a.level.registryAccess()));
        long listed = tab.getDisplayItems().stream()
                .filter(s -> s.is(AtmosItems.GAS_MINER.get()))
                .count();
        h.assertValueEqual(listed, 1L, "miners in the creative tab");

        FakePlayer survival = new FakePlayer(a.level, new GameProfile(UUID.randomUUID(), "zas_gas_miner_survival"));
        survival.setGameMode(GameType.SURVIVAL);
        survival.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 0)));
        h.assertTrue(state.getDestroySpeed(a.level, pos) < 0, "it has a breaking time");
        h.assertValueEqual(state.getDestroyProgress(survival, a.level, pos), 0.0f, "survival's progress on it");
        h.assertTrue(
                Block.getDrops(state, a.level, pos, a.level.getBlockEntity(pos)).isEmpty(),
                "broken, it drops something");
        h.assertTrue(
                state.getBlock().getExplosionResistance() >= 3_600_000f,
                "a blast breaks it: " + state.getBlock().getExplosionResistance());
        Vec3 blast = a.point(2, 2, 2);
        a.level.explode(null, blast.x, blast.y, blast.z, 4.0f, Level.ExplosionInteraction.TNT);
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.GAS_MINER.get()), "a blast broke it");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** CONT-180: saved and read back, a miner keeps its switch, gas, target and temperature. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_180_a_miner_keeps_its_settings_when_saved(GameTestHelper h) {
        Area a = new Area(h, 5906);
        GasMinerBlockEntity m = rig(a);
        m.setGas(N2);
        m.setTarget(321);
        m.setTemperature(77);
        var tag = m.saveWithFullMetadata(a.level.registryAccess());
        GasMinerBlockEntity read = new GasMinerBlockEntity(m.getBlockPos(), m.getBlockState());
        read.loadWithComponents(tag, a.level.registryAccess());
        h.assertTrue(read.isOn(), "it read back off");
        h.assertValueEqual(read.gas(), N2, "its gas read back");
        a.close(read.target(), 321, "its target read back");
        a.close(read.temperature(), 77, "its temperature read back");
        h.succeed();
    }
}
