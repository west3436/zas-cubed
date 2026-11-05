package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import io.github.west3436.zas.atmos.failure.TankFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJetKind;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.gametest.TestPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How tanks and canisters fail (CONT-038, CONT-085, CONT-101, CONT-102), what their explosions do to
 * blocks under each preset (CONT-107) and how strong they can be (CONT-109). Most build in their own
 * {@link Area} and drive atmos ticks themselves, in a batch of their own; explosions are watched
 * through {@link Blasts}. The rule's every threshold is in {@code ContainerFailureTest}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContainerFailureTests {
    private static final String BATCH = "zas_atmos_container_failure";
    private static final double ATM = ContainerFailure.ATM_KPA;

    private ContainerFailureTests() {}

    /** A 70 L tank of N2 at a pressure. */
    private static ItemStack tank(double atm) {
        ItemStack stack = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(stack, GasTankItem.fill(Map.of(N2, 1.0), atm * ATM, 70));
        return stack;
    }

    private static double moles(ItemStack tank) {
        return GasTankItem.gas(tank).totalMoles();
    }

    private static double roomMoles(Area a) {
        return a.zone(2, 2, 2).mixture().totalMoles();
    }

    private static void power(GameTestHelper h, Explosion blast, double expected) {
        h.assertTrue(
                Math.abs(blast.radius() - expected) < 1e-4,
                "the explosion's power: expected " + expected + ", got " + blast.radius());
    }

    /** The one explosion near a point; fails if there are none or more. */
    private static Explosion oneBlast(GameTestHelper h, Vec3 near) {
        List<Explosion> blasts = Blasts.take(near);
        h.assertValueEqual(blasts.size(), 1, "explosions");
        return blasts.get(0);
    }

    /** Runs checks under a preset, then restores the configured one. */
    private static void withPreset(AtmospherePreset preset, Runnable checks) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(preset);
            checks.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }

    // ---- CONT-038 -------------------------------------------------------------------------------

    // ---- CLIENT-018 ------------------------------------------------------------------------------

    /** The one jet the server sent around a point, of a kind; fails if there are none or more. */
    private static GasJet oneJet(GameTestHelper h, Vec3 near, GasJetKind kind) {
        List<GasJet> jets = JetLog.take(near);
        h.assertValueEqual(jets.size(), 1, "jets sent");
        h.assertValueEqual(jets.get(0).kind(), kind, "the jet's kind");
        return jets.get(0);
    }

    private static void jetAt(GameTestHelper h, GasJet jet, Vec3 point) {
        h.assertTrue(
                new Vec3(jet.x(), jet.y(), jet.z()).distanceTo(point) < 1e-6,
                "the jet is at " + jet.x() + ", " + jet.y() + ", " + jet.z() + ", not " + point);
    }

    /** A jet's moles, as near as the float it is sent in holds them. */
    private static void jetMoles(GameTestHelper h, GasJet jet, double expected) {
        h.assertTrue(
                Math.abs(jet.amount() - expected) <= expected * 1e-6,
                "the jet's moles: " + jet.amount() + ", not " + expected);
    }

    private static void noDirection(GameTestHelper h, GasJet jet) {
        h.assertTrue(jet.dx() == 0 && jet.dy() == 0 && jet.dz() == 0, "a burst has a direction: " + jet);
    }

    /**
     * A tank holding sends nothing. A leaking tank sends a jet each atmos tick, from where it is and in
     * a level direction, sized by what it lost in that tick. Once it stops leaking, so do the jets.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_leaking_tank_sends_a_jet_of_what_it_lost(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 860);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);

        TankFailure.check(a.level, at, tank(29.9), null);
        h.assertTrue(JetLog.take(at).isEmpty(), "a tank at 29.9 atm sent a jet");

        ItemStack t = tank(39.9);
        double start = moles(t);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null).kind(), Kind.LEAK, "at 39.9 atm");
        GasJet jet = oneJet(h, at, GasJetKind.LEAK);
        jetAt(h, jet, at);
        jetMoles(h, jet, start - moles(t));
        h.assertTrue(
                jet.dy() == 0 && Math.abs(Math.hypot(jet.dx(), jet.dz()) - 1) < 1e-4,
                "the jet isn't level and a unit long: " + jet);

        for (int i = 0; i < 8 && TankFailure.check(a.level, at, t, null).kind() == Kind.LEAK; i++) {
            a.tick();
        }
        JetLog.take(at);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.NONE, "at 30 atm");
        h.assertTrue(JetLog.take(at).isEmpty(), "a tank that stopped leaking sent a jet");
        a.tick();
        h.succeed();
    }

    /** A leak's jets all point the way it chose as it started, until it stops. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_leak_keeps_one_direction(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 950);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);

        ItemStack t = tank(39.9);
        List<GasJet> jets = new ArrayList<>();
        for (int i = 0; i < 8 && TankFailure.check(a.level, at, t, null).kind() == Kind.LEAK; i++) {
            jets.addAll(JetLog.take(at));
            a.tick();
        }
        h.assertTrue(jets.size() >= 2, "too few jets to compare: " + jets);
        for (GasJet jet : jets) {
            h.assertTrue(
                    jet.dx() == jets.get(0).dx() && jet.dz() == jets.get(0).dz(),
                    "the leak changed direction: " + jets);
        }
        h.assertFalse(TankFailure.leaking(t), "the tank is still marked as leaking");
        a.tick();
        h.succeed();
    }

    /** A rupturing tank sends one burst, in every direction, of all it held. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_rupturing_tank_sends_a_burst_of_all_its_gas(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 861);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ItemStack t = tank(45);
        double held = moles(t);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.RUPTURE, "at 45 atm");
        GasJet jet = oneJet(h, at, GasJetKind.BURST);
        jetAt(h, jet, at);
        noDirection(h, jet);
        jetMoles(h, jet, held);
        a.tick();
        h.succeed();
    }

    /** An explosion shows vanilla's own particles, so an exploding tank sends no burst as well. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_an_exploding_tank_sends_no_burst(GameTestHelper h) {
        Blasts.listen();
        JetLog.listen();
        Area a = new Area(h, 862);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        h.assertValueEqual(TankFailure.check(a.level, at, tank(51), null).kind(), Kind.EXPLODE, "at 51 atm");
        h.assertValueEqual(Blasts.take(at).size(), 1, "explosions");
        h.assertTrue(JetLog.take(at).isEmpty(), "an exploding tank sent a jet");
        a.tick();
        h.succeed();
    }

    /** A canister over its rating sends one burst from its middle, of what it held. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_rupturing_canister_sends_a_burst(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 863);
        GasDeviceTests.room(a);
        CanisterBlockEntity can = canisterAt(a, 9200);
        double held = can.network().gas().totalMoles();
        JetLog.take(a.point(2, 1, 2));
        a.step();
        h.assertTrue(canisterGone(a), "a canister at 9,200 kPa held");
        GasJet jet = oneJet(h, a.point(2, 1, 2), GasJetKind.BURST);
        jetAt(h, jet, a.point(2, 1, 2));
        noDirection(h, jet);
        jetMoles(h, jet, held);
        h.succeed();
    }

    /** A canister in a network lets out only its share, so that is the size of its burst. */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_canister_in_a_network_sends_a_burst_of_its_share(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 864);
        GasDeviceTests.room(a);
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(2, 2, 2, GasDeviceTests.port());
        a.place(2, 3, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 3, 2);
        PipeNetwork net = can.network();
        double total = molesFor(9200, net.volume(), T);
        double share = total * PipeVolumes.CANISTER / net.volume();
        net.gas().add(new GasParcel(Map.of(N2, total), T));
        JetLog.take(a.point(2, 3, 2));
        a.step();
        h.assertFalse(a.get(2, 3, 2).is(AtmosBlocks.CANISTER.get()), "the canister held above its rating");
        GasJet jet = oneJet(h, a.point(2, 3, 2), GasJetKind.BURST);
        jetMoles(h, jet, share);
        h.succeed();
    }

    /**
     * A canister caught in a blast bursts too, sized by what it held. An empty one has nothing to let
     * out, so it shows nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void client_018_a_canister_caught_in_a_blast_sends_a_burst(GameTestHelper h) {
        JetLog.listen();
        Area a = new Area(h, 865);
        GasDeviceTests.room(a);
        CanisterBlockEntity can = canisterAt(a, 2000);
        double held = can.network().gas().totalMoles();
        Vec3 near = a.point(2, 2, 2);
        JetLog.take(near);
        a.level.explode(null, near.x, near.y, near.z, 4f, Level.ExplosionInteraction.NONE);
        h.assertTrue(canisterGone(a), "the blast left the canister whole");
        GasJet jet = oneJet(h, near, GasJetKind.BURST);
        jetMoles(h, jet, held);
        jetAt(h, jet, a.point(2, 1, 2));
        a.tick();

        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        JetLog.take(near);
        a.level.explode(null, near.x, near.y, near.z, 4f, Level.ExplosionInteraction.NONE);
        h.assertTrue(canisterGone(a), "the second blast left the canister whole");
        h.assertTrue(JetLog.take(near).isEmpty(), "an empty canister sent a burst");
        h.succeed();
    }

    /** Up to 30 atm a tank holds, and neither the stack nor the room changes. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_tank_below_30_atm_holds(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 300);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        Vec3 at = a.point(2, 2, 2);

        ItemStack t = tank(29.9);
        double held = moles(t);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.NONE, "at 29.9 atm");
        a.tick();
        a.close(moles(t), held, "the tank's gas");
        a.close(roomMoles(a), room, "the room's gas");
        h.assertTrue(Blasts.take(at).isEmpty(), "a tank at 29.9 atm exploded");
        h.succeed();
    }

    /**
     * Above 30 atm a tank leaks into its cell, a tenth of its gas an atmos tick, and stops at 30 atm.
     * At 39.9 atm it still only leaks. It stays a tank throughout.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_tank_above_30_atm_leaks_down_to_30_atm(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 301);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        Vec3 at = a.point(2, 2, 2);

        ItemStack t = tank(39.9);
        double start = moles(t);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null).kind(), Kind.LEAK, "at 39.9 atm");
        a.close(moles(t), start * 0.9, "the tank's gas after one tick");
        h.assertFalse(t.isEmpty(), "a leaking tank was destroyed");
        a.tick();
        a.close(roomMoles(a), room + start * 0.1, "the room's gas after one tick");

        for (int i = 0; i < 8 && TankFailure.check(a.level, at, t, null).kind() == Kind.LEAK; i++) {
            a.tick();
        }
        a.tick();
        double p = GasTankItem.contents(t).pressure();
        h.assertTrue(Math.abs(p - 30 * ATM) < 1e-6, "the tank stopped leaking at " + p + " kPa, not 30 atm");
        h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.NONE, "at 30 atm");
        a.close(roomMoles(a) + moles(t), room + start, "the gas between them");
        h.assertTrue(Blasts.take(at).isEmpty(), "a leaking tank exploded");
        h.succeed();
    }

    /** Above 40 atm a tank is destroyed and all its gas goes into its cell, with no explosion. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_tank_above_40_atm_ruptures_into_its_cell(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 302);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        Vec3 at = a.point(2, 2, 2);

        for (double atm : new double[] {40.1, 49.9}) {
            ItemStack t = tank(atm);
            double held = moles(t);
            h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.RUPTURE, "at " + atm + " atm");
            h.assertTrue(t.isEmpty(), "a tank at " + atm + " atm wasn't used up");
            a.tick();
            a.close(roomMoles(a), room + held, "the room's gas after a rupture at " + atm + " atm");
            room += held;
        }
        h.assertTrue(Blasts.take(at).isEmpty(), "a rupturing tank exploded");
        h.succeed();
    }

    /**
     * Above 50 atm a tank explodes where it is with the formula's power, (P − 50 atm) / 10 atm, and
     * its gas goes into its cell.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_tank_above_50_atm_explodes_with_the_formulas_power(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 303);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        Vec3 at = a.point(2, 2, 2);

        for (double atm : new double[] {50.1, 51, 60, 90}) {
            ItemStack t = tank(atm);
            double held = moles(t);
            Verdict v = TankFailure.check(a.level, at, t, null);
            h.assertValueEqual(v.kind(), Kind.EXPLODE, "at " + atm + " atm");
            h.assertTrue(t.isEmpty(), "a tank at " + atm + " atm wasn't used up");
            Explosion blast = oneBlast(h, at);
            power(h, blast, (atm - 50) / 10);
            h.assertTrue(blast.center().distanceTo(at) < 1e-9, "the explosion wasn't where the tank is");
            a.tick();
            a.close(roomMoles(a), room + held, "the room's gas after an explosion at " + atm + " atm");
            room += held;
        }
        h.succeed();
    }

    /**
     * A blast from a tank someone carries hurts them too: vanilla spares an explosion's source, so
     * the holder can't be it. The explosion is at the middle of their body, because vanilla also
     * skips an entity whose eyes are exactly at its centre. It hurts under both presets (CONT-107).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_038_a_blast_hurts_the_tanks_holder_under_each_preset(GameTestHelper h) {
        Area a = new Area(h, 304);
        a.forceLoad(true); // entities need a ticking chunk for an explosion to find them
        a.clear(0, 0, 8, 8);
        a.set(2, 0, 2, Area.STONE);
        a.set(6, 0, 6, Area.STONE);
        Pig normal = pig(a, 2, 1, 2);
        Pig hardcore = pig(a, 6, 1, 6);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        a.level.getEntity(normal.getUUID()) != null && a.level.getEntity(hardcore.getUUID()) != null,
                        "the pigs aren't in the level yet"))
                .thenIdle(5)
                .thenExecute(() -> {
                    float full = normal.getMaxHealth();
                    withPreset(AtmospherePreset.NORMAL, () -> {
                        TankFailure.check(a.level, TankFailure.pointOf(normal), tank(58), null);
                    });
                    h.assertTrue(normal.getHealth() < full, "the blast didn't hurt its holder under Normal");
                    withPreset(AtmospherePreset.HARDCORE, () -> {
                        TankFailure.check(a.level, TankFailure.pointOf(hardcore), tank(58), null);
                    });
                    h.assertTrue(hardcore.getHealth() < full, "the blast didn't hurt its holder under Hardcore");
                })
                .thenExecute(() -> {
                    normal.discard();
                    hardcore.discard();
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    private static Pig pig(Area a, int x, int y, int z) {
        Pig pig = EntityType.PIG.create(a.level);
        pig.setNoAi(true);
        pig.moveTo(Vec3.atBottomCenterOf(a.abs(x, y, z)));
        a.level.addFreshEntity(pig);
        return pig;
    }

    /**
     * Tanks fail wherever they are carried, in real time: in a main slot, in the offhand, in a worn
     * suit's slot, on the ground and in a dropped suit's slot. A fake player doesn't tick, so its
     * inventory is ticked here as a player's would be. The gas goes into the room the holder or the
     * item is in.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void cont_038_a_tank_fails_in_an_inventory_a_suit_and_on_the_ground(GameTestHelper h) {
        Area a = new Area(h, 305);
        a.forceLoad(true); // it waits in real time
        GasDeviceTests.room(a);
        double room = roomMoles(a);

        FakePlayer player = TestPlayers.hurtable(a.level, "zas_cont_038");
        player.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        Inventory inventory = player.getInventory();
        ItemStack main = tank(41);
        ItemStack offhand = tank(41);
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        ItemStack slotted = tank(41);
        ItemStack ground = tank(41);
        double each = moles(main);
        SuitItem.setTank(suit, slotted);
        inventory.setItem(9, main);
        player.setItemSlot(EquipmentSlot.OFFHAND, offhand);
        player.setItemSlot(EquipmentSlot.CHEST, suit);
        Vec3 groundAt = a.point(3, 1, 3);
        ItemEntity item = new ItemEntity(a.level, groundAt.x, groundAt.y, groundAt.z, ground);
        item.setNoPickUpDelay();
        a.level.addFreshEntity(item);
        ItemStack droppedSuit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(droppedSuit, tank(41));
        Vec3 suitAt = a.point(1, 1, 3);
        ItemEntity suitItem = new ItemEntity(a.level, suitAt.x, suitAt.y, suitAt.z, droppedSuit);
        suitItem.setNoPickUpDelay();
        a.level.addFreshEntity(suitItem);

        h.onEachTick(inventory::tick);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(player.getInventory().getItem(9).isEmpty(), "the tank in a main slot is still there");
                    h.assertTrue(player.getOffhandItem().isEmpty(), "the tank in the offhand is still there");
                    h.assertTrue(
                            SuitItem.tank(player.getItemBySlot(EquipmentSlot.CHEST))
                                    .isEmpty(),
                            "the tank in the suit's slot is still there");
                    h.assertTrue(item.isRemoved(), "the tank on the ground is still there");
                    h.assertTrue(SuitItem.tank(suitItem.getItem()).isEmpty(), "the dropped suit's tank is still there");
                    a.close(roomMoles(a), room + 5 * each, "the room's gas");
                })
                .thenExecute(() -> {
                    h.assertFalse(
                            player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                            "the suit went with the tank in its slot");
                    h.assertFalse(suitItem.isRemoved(), "the dropped suit went with the tank in its slot");
                    suitItem.discard();
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    /** A tank at a safe pressure in a suit's slot keeps its gas and its place. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_038_a_tank_holding_in_a_suit_stays_as_it_was(GameTestHelper h) {
        Area a = new Area(h, 306);
        FakePlayer player = TestPlayers.hurtable(a.level, "zas_cont_038_holding");
        player.moveTo(a.point(2, 2, 2));
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, tank(6));
        double held = moles(SuitItem.tank(suit));
        player.setItemSlot(EquipmentSlot.CHEST, suit);

        h.onEachTick(player.getInventory()::tick);
        h.runAfterDelay(30, () -> {
            ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
            a.close(moles(SuitItem.tank(worn)), held, "the suit's tank's gas");
            h.succeed();
        });
    }

    // ---- CONT-107 -------------------------------------------------------------------------------

    /** Glass all around a point, which every blast breaks some of if it breaks blocks at all. */
    private static void shell(Area a) {
        a.clear(0, 0, 6, 6);
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(a.abs(2, 1, 2), a.abs(4, 3, 4))) {
            if (!p.equals(a.abs(3, 2, 3))) {
                a.level.setBlockAndUpdate(p, glass);
            }
        }
    }

    private static int glassLeft(Area a) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(a.abs(2, 1, 2), a.abs(4, 3, 4))) {
            if (a.level.getBlockState(p).is(Blocks.GLASS)) {
                n++;
            }
        }
        return n;
    }

    /** Explodes a tank inside a shell of glass and says whether any of the glass broke. */
    private static boolean blastBreaks(Area a) {
        shell(a);
        TankFailure.check(a.level, a.point(3, 2, 3), tank(70), null);
        return glassLeft(a) < 26;
    }

    /** A tank's explosion breaks blocks under Hardcore, and under Normal leaves them all intact. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_107_a_tank_breaks_blocks_only_under_hardcore(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 307);
        withPreset(AtmospherePreset.NORMAL, () -> {
            h.assertFalse(blastBreaks(a), "a blast broke blocks under Normal");
            h.assertValueEqual(glassLeft(a), 26, "the glass left under Normal");
        });
        withPreset(
                AtmospherePreset.HARDCORE, () -> h.assertTrue(blastBreaks(a), "a blast broke nothing under Hardcore"));
        h.assertValueEqual(Blasts.take(a.point(3, 2, 3)).size(), 2, "explosions, one under each preset");
        h.succeed();
    }

    /** An override holds under either preset (OPS-007). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_107_either_preset_can_override_it(GameTestHelper h) {
        Area a = new Area(h, 308);
        try {
            PresetOptions.CONTAINER_EXPLOSIONS_BREAK_BLOCKS.setOverride(true);
            withPreset(AtmospherePreset.NORMAL, () -> h.assertTrue(blastBreaks(a), "overridden on under Normal"));
            PresetOptions.CONTAINER_EXPLOSIONS_BREAK_BLOCKS.setOverride(false);
            withPreset(AtmospherePreset.HARDCORE, () -> h.assertFalse(blastBreaks(a), "overridden off under Hardcore"));
        } finally {
            PresetOptions.CONTAINER_EXPLOSIONS_BREAK_BLOCKS.clearOverride();
        }
        h.succeed();
    }

    // ---- CONT-109 -------------------------------------------------------------------------------

    /** A pressure whose formula gives more than 16 explodes with 16; just under it, its own power. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_109_a_tanks_power_is_capped_at_16(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 309);
        a.clear(0, 0, 4, 4);
        Vec3 at = a.point(2, 2, 2);
        h.assertValueEqual(AtmosServerConfig.EXPLOSION_POWER_CAP.get(), 16.0, "the configured cap");

        withPreset(AtmospherePreset.NORMAL, () -> {
            TankFailure.check(a.level, at, tank(200), null); // the formula says 15
            power(h, oneBlast(h, at), 15);
            TankFailure.check(a.level, at, tank(300), null); // the formula says 25
            power(h, oneBlast(h, at), 16);
        });
        h.succeed();
    }

    /** The cap comes from the config. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_109_the_cap_is_configurable(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 310);
        a.clear(0, 0, 4, 4);
        Vec3 at = a.point(2, 2, 2);
        double before = AtmosServerConfig.EXPLOSION_POWER_CAP.get();
        try {
            AtmosServerConfig.EXPLOSION_POWER_CAP.set(3.0);
            withPreset(AtmospherePreset.NORMAL, () -> {
                TankFailure.check(a.level, at, tank(300), null); // the formula says 25
                power(h, oneBlast(h, at), 3);
            });
        } finally {
            AtmosServerConfig.EXPLOSION_POWER_CAP.set(before);
        }
        h.succeed();
    }

    /** A hot tank of the same gas fails at the pressure its temperature gives. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_hot_tank_fails_by_its_pressure(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 311);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        // 20 atm of N2 is safe at room temperature, and at three times that it is 60 atm.
        ItemStack t = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasParcel cold = GasTankItem.fill(Map.of(N2, 1.0), 20 * ATM, 70);
        GasTankItem.setGas(t, cold);
        h.assertValueEqual(TankFailure.check(a.level, at, t, null), Verdict.NONE, "cold");
        GasTankItem.setGas(t, new GasParcel(cold.moles(), T * 3));
        h.assertValueEqual(TankFailure.check(a.level, at, t, null).kind(), Kind.EXPLODE, "hot");
        power(h, oneBlast(h, at), 1);
        h.succeed();
    }

    /**
     * The check sees what a reaction raised in the same atmos tick (CONT-100 goes first): hydrogen
     * burning in a tank takes it from below 30 atm to above 50 atm in one call, and it explodes with
     * the power of the pressure it reached. Had the check gone first, the tank would have held.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_038_a_burn_explodes_a_tank_in_the_same_atmos_tick(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 312);
        GasDeviceTests.room(a);
        ItemStack t = new ItemStack(AtmosItems.EMPTY_TANK.get());
        ResourceLocation hydrogen = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
        GasTankItem.setGas(t, new GasParcel(Map.of(hydrogen, 5.0, O2, 10.0, N2, 13.0), 900));

        GasMixture before = GasTankItem.contents(t);
        GasMixture burned = Reactions.applyStandalone(before, AtmosTicks.SECONDS, false);
        h.assertTrue(before.pressure() < 30 * ATM, "it starts at " + before.pressure() + " kPa, not below 30 atm");
        h.assertTrue(burned.pressure() > 50 * ATM, "the burn reaches " + burned.pressure() + " kPa, not above 50 atm");

        Vec3 at = a.point(2, 2, 2);
        ItemEntity holder = new ItemEntity(a.level, at.x, at.y, at.z, t);
        h.assertTrue(GasTankItem.atmosTick(t, a.level, holder), "the tank's atmos tick changed nothing");
        h.assertTrue(t.isEmpty(), "the tank is still there after its burn");
        power(h, oneBlast(h, TankFailure.pointOf(holder)), (burned.pressure() - 50 * ATM) / (10 * ATM));
        h.succeed();
    }

    // ---- CONT-085, CONT-101, CONT-102 -----------------------------------------------------------

    /** The moles of N2 that make a pressure in a volume at a temperature. */
    private static double molesFor(double kPa, double volume, double kelvin) {
        return kPa * volume / (GasMixture.GAS_CONSTANT * kelvin);
    }

    /** A canister in a sealed room, standing alone, holding N2 at a pressure. */
    private static CanisterBlockEntity canisterAt(Area a, double kPa) {
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, molesFor(kPa, PipeVolumes.CANISTER, T)), T));
        return can;
    }

    private static boolean canisterGone(Area a) {
        return !a.get(2, 1, 2).is(AtmosBlocks.CANISTER.get());
    }

    /** Just below its rating a canister holds; just above it, it ruptures into its cell, with no explosion. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_canister_ruptures_just_above_90_atm_and_not_below(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 313);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        CanisterBlockEntity can = canisterAt(a, 9119);
        double below = can.network().gas().totalMoles();
        a.step();
        h.assertFalse(canisterGone(a), "a canister at 9,119 kPa ruptured");
        a.close(roomMoles(a), room, "the room's gas with the canister whole");

        double raised = molesFor(9121, PipeVolumes.CANISTER, T);
        can.network().gas().add(new GasParcel(Map.of(N2, raised - below), T));
        a.step();
        h.assertTrue(canisterGone(a), "a canister at 9,121 kPa held");
        a.tick();
        a.close(roomMoles(a), room + raised, "the room's gas after the rupture");
        h.assertTrue(Blasts.take(a.point(2, 1, 2)).isEmpty(), "a rupturing canister exploded");
        h.succeed();
    }

    /**
     * CONT-102's plan: from below 90 atm to above 135 atm in one tick. It goes straight to the
     * explosion, with the formula's power, and its gas goes into its cell.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_102_a_canister_raised_past_135_atm_in_one_tick_explodes(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 314);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        CanisterBlockEntity can = canisterAt(a, 5000);
        double start = can.network().gas().totalMoles();
        a.step();
        h.assertFalse(canisterGone(a), "a canister at 5,000 kPa failed");

        double kPa = ContainerFailure.CANISTER_EXPLODE_KPA + 25 * ATM;
        double raised = molesFor(kPa, PipeVolumes.CANISTER, T);
        can.network().gas().add(new GasParcel(Map.of(N2, raised - start), T));
        a.step();
        h.assertTrue(canisterGone(a), "the canister is still there");
        power(h, oneBlast(h, a.point(2, 1, 2)), 2.5);
        a.tick();
        a.close(roomMoles(a), room + raised, "the room's gas after the explosion");
        h.succeed();
    }

    /**
     * CONT-102's plan by way of a burn: hydrogen in a lone canister takes it from below 90 atm to above
     * 135 atm in one atmos tick, and the check sees that in the same tick. Had the check gone first,
     * the canister would have held.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_102_a_burn_explodes_a_canister_in_the_same_atmos_tick(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 323);
        GasDeviceTests.room(a);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        ResourceLocation hydrogen = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
        can.network().gas().add(new GasParcel(Map.of(hydrogen, 400.0, O2, 400.0, N2, 400.0), 900));

        GasMixture before = can.network().gas().mixture();
        GasMixture burned = Reactions.applyStandalone(before, AtmosTicks.SECONDS, false);
        h.assertTrue(
                before.pressure() < ContainerFailure.CANISTER_RATED_KPA,
                "it starts at " + before.pressure() + " kPa, not below 90 atm");
        h.assertTrue(
                burned.pressure() > ContainerFailure.CANISTER_EXPLODE_KPA,
                "the burn reaches " + burned.pressure() + " kPa, not above 135 atm");

        a.step();
        h.assertTrue(canisterGone(a), "the canister is still there after its burn");
        power(
                h,
                oneBlast(h, a.point(2, 1, 2)),
                (burned.pressure() - ContainerFailure.CANISTER_EXPLODE_KPA) / ContainerFailure.POWER_SCALE_KPA);
        h.succeed();
    }

    /** A canister's power is capped too (CONT-109). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_109_a_canisters_power_is_capped_at_16(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 315);
        GasDeviceTests.room(a);
        canisterAt(a, ContainerFailure.CANISTER_EXPLODE_KPA + 300 * ATM);
        a.step();
        h.assertTrue(canisterGone(a), "the canister is still there");
        power(h, oneBlast(h, a.point(2, 1, 2)), 16);
        h.succeed();
    }

    /** A canister's explosion follows the preset, as a tank's does (CONT-107). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_107_a_canister_breaks_blocks_only_under_hardcore(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 316);
        withPreset(AtmospherePreset.NORMAL, () -> h.assertFalse(canisterBlastBreaks(a), "broke blocks under Normal"));
        withPreset(
                AtmospherePreset.HARDCORE, () -> h.assertTrue(canisterBlastBreaks(a), "broke nothing under Hardcore"));
        h.assertValueEqual(Blasts.take(a.point(3, 2, 3)).size(), 2, "explosions, one under each preset");
        h.succeed();
    }

    /** Explodes a canister inside a shell of glass and says whether any of the glass broke. */
    private static boolean canisterBlastBreaks(Area a) {
        shell(a);
        a.place(3, 2, 3, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(3, 2, 3);
        double kPa = ContainerFailure.CANISTER_EXPLODE_KPA + 20 * ATM;
        can.network().gas().add(new GasParcel(Map.of(N2, molesFor(kPa, PipeVolumes.CANISTER, T)), T));
        a.step();
        return glassLeft(a) < 26;
    }

    /**
     * A canister in a network fails at the network's pressure. Its share goes into its cell, and the
     * rest stays in the pipes. This stays below the 100 atm at which the pipe would burst first.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_canister_in_a_network_ruptures_with_its_share(GameTestHelper h) {
        Area a = new Area(h, 317);
        GasDeviceTests.room(a);
        double room = a.zone(1, 2, 1).mixture().totalMoles();
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(2, 2, 2, GasDeviceTests.port());
        a.place(2, 3, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 3, 2);
        PipeNetwork net = can.network();
        double total = molesFor(9200, net.volume(), T);
        double share = total * PipeVolumes.CANISTER / net.volume();
        net.gas().add(new GasParcel(Map.of(N2, total), T));
        a.step();
        h.assertFalse(a.get(2, 3, 2).is(AtmosBlocks.CANISTER.get()), "the canister held above its rating");
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "the pipe went with the canister");
        a.tick();
        a.close(a.zone(1, 2, 1).mixture().totalMoles(), room + share, "the room's gas: the canister's share");
        h.succeed();
    }

    /** An item above the rating doesn't fail while it's an item. Placed, it ruptures at its first atmos tick. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_101_a_canister_placed_above_its_rating_ruptures(GameTestHelper h) {
        Area a = new Area(h, 324);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        double moles = molesFor(9200, PipeVolumes.CANISTER, T);
        ItemStack item = new ItemStack(AtmosItems.CANISTER.get());
        item.set(AtmosComponents.GAS.get(), new GasParcel(Map.of(N2, moles), T));

        a.place(2, 1, 2, GasDeviceTests.canister());
        a.<CanisterBlockEntity>entity(2, 1, 2).applyComponentsFromItemStack(item);
        a.step();
        h.assertTrue(canisterGone(a), "a canister placed at 9,200 kPa held");
        a.tick();
        a.close(roomMoles(a), room + moles, "the room's gas after the rupture");
        h.succeed();
    }

    /**
     * Any explosion a canister is caught in ruptures it (CONT-085), also one that leaves blocks
     * intact. Its gas goes into its cell, not into an item.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_085_a_canister_caught_in_an_explosion_ruptures(GameTestHelper h) {
        Area a = new Area(h, 318);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        CanisterBlockEntity can = canisterAt(a, 2000);
        double held = can.network().gas().totalMoles();
        Vec3 near = a.point(2, 2, 2);
        a.level.explode(null, near.x, near.y, near.z, 4f, Level.ExplosionInteraction.NONE);
        h.assertTrue(canisterGone(a), "the blast left the canister whole");
        h.assertTrue(a.get(0, 2, 2).is(Blocks.STONE), "a blast that keeps blocks broke the wall");
        a.tick();
        a.close(roomMoles(a), room + held, "the room's gas after the rupture");
        h.succeed();
    }

    /**
     * A blast that breaks blocks ruptures a canister too, so its gas goes into its cell rather than
     * into an item. Power 2 reaches the metal canister next to it but not the stone walls, so the room
     * stays sealed and can show where the gas went.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_085_a_blast_that_breaks_blocks_ruptures_a_canister(GameTestHelper h) {
        Area a = new Area(h, 319);
        GasDeviceTests.room(a);
        double room = roomMoles(a);
        CanisterBlockEntity can = canisterAt(a, 2000);
        double held = can.network().gas().totalMoles();
        Vec3 near = a.point(2, 2, 2);
        a.level.explode(null, near.x, near.y, near.z, 2f, Level.ExplosionInteraction.BLOCK);
        h.assertTrue(canisterGone(a), "the blast left the canister whole");
        h.assertTrue(a.get(0, 2, 2).is(Blocks.STONE), "the blast broke the wall, so the room proves nothing");
        a.tick();
        a.close(roomMoles(a), room + held, "the room's gas after the rupture");
        h.succeed();
    }

    /** A wind charge's blast only pushes, and triggers doors: it leaves a canister alone. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_085_a_wind_burst_leaves_a_canister_whole(GameTestHelper h) {
        Area a = new Area(h, 320);
        a.clear(0, 0, 6, 6);
        a.place(3, 1, 3, GasDeviceTests.canister());
        Vec3 near = a.point(3, 2, 3);
        a.level.explode(null, near.x, near.y, near.z, 4f, Level.ExplosionInteraction.TRIGGER);
        h.assertTrue(a.get(3, 1, 3).is(AtmosBlocks.CANISTER.get()), "a wind burst ruptured the canister");
        h.succeed();
    }

    /**
     * Air above 1,273 K around it ruptures a canister, even an empty one. Its cell's reading can lag
     * behind placing it, so this allows a few atmos ticks.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_085_a_canister_in_air_above_1273_k_ruptures(GameTestHelper h) {
        Area a = new Area(h, 322);
        a.box(0, 0, 4, 4);
        a.settle(GasMutations.add(a.level, a.point(2, 2, 2), new GasParcel(Map.of(N2, 5000.0), 3000), Area.SOURCE));
        double air = a.zone(2, 2, 2).mixture().temperature();
        h.assertTrue(air > ContainerFailure.CANISTER_MAX_K, "the room is at " + air + " K, so this proves nothing");

        a.place(2, 1, 2, GasDeviceTests.canister());
        for (int i = 0; i < 8 && !canisterGone(a); i++) {
            a.step();
        }
        h.assertTrue(canisterGone(a), "an empty canister in a room at " + air + " K held");
        h.succeed();
    }

    /**
     * "Above 1,273 K" means the air around a canister, not the gas inside it. Gas at 2,000 K inside one
     * in a cool room leaves it whole, and so does a burn (cont_100_a_canister_reacts_alone_and_on_a_port).
     * Heat the room past 1,273 K and it ruptures into its cell, with no explosion.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_085_hot_air_ruptures_a_canister_and_hot_gas_inside_alone_does_not(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 325);
        GasDeviceTests.room(a);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, 100.0), 2000));

        double air = a.zone(2, 2, 2).mixture().temperature();
        h.assertTrue(air < ContainerFailure.CANISTER_MAX_K, "the room is at " + air + " K, so this proves nothing");
        for (int i = 0; i < 4; i++) {
            a.step();
        }
        h.assertFalse(canisterGone(a), "gas at 2,000 K inside a canister in a room at " + air + " K ruptured it");
        a.close(can.network().gas().temperature(), 2000, "the gas inside the canister");
        double held = can.network().gas().totalMoles();

        a.settle(GasMutations.add(a.level, a.point(1, 2, 1), new GasParcel(Map.of(N2, 5000.0), 3000), Area.SOURCE));
        air = a.zone(2, 2, 2).mixture().temperature();
        h.assertTrue(air > ContainerFailure.CANISTER_MAX_K, "the room is at " + air + " K, so this proves nothing");
        double room = roomMoles(a);
        for (int i = 0; i < 8 && !canisterGone(a); i++) {
            a.step();
        }
        h.assertTrue(canisterGone(a), "a canister in a room at " + air + " K held");
        a.tick();
        a.close(roomMoles(a), room + held, "the room's gas after the rupture");
        h.assertTrue(Blasts.take(a.point(2, 1, 2)).isEmpty(), "a canister well below its rating exploded");
        h.succeed();
    }

    /** A tank plays nothing while it holds, its leak while it leaks and its burst as it ruptures (CONT-127). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_tank_plays_its_leak_and_its_burst(GameTestHelper h) {
        SoundLog.listen();
        Area a = new Area(h, 880);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);

        TankFailure.check(a.level, at, tank(29.9), null);
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "holding");

        h.assertValueEqual(TankFailure.check(a.level, at, tank(39.9), null).kind(), Kind.LEAK, "at 39.9 atm");
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "leaking", AtmosSounds.CONTAINER_LEAK);

        h.assertValueEqual(TankFailure.check(a.level, at, tank(45), null), Verdict.RUPTURE, "at 45 atm");
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "rupturing", AtmosSounds.CONTAINER_BURST);
        a.tick();
        h.succeed();
    }

    /**
     * A leak hisses once, as it starts, though it loses gas and sends a jet at each of its atmos ticks:
     * 3 of them from 39.9 atm. A tank that stopped leaking and was refilled hisses again, and one refilled
     * while it still leaks doesn't (CONT-127, CONT-038).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_tank_hisses_once_as_a_leak_starts(GameTestHelper h) {
        SoundLog.listen();
        JetLog.listen();
        Area a = new Area(h, 884);
        GasDeviceTests.room(a);
        Vec3 at = a.point(2, 2, 2);
        ItemStack t = tank(39.9);
        double start = moles(t);

        int leaks = 0;
        while (leaks < 8 && TankFailure.check(a.level, at, t, null).kind() == Kind.LEAK) {
            leaks++;
            a.tick();
        }
        List<GasJet> jets = JetLog.take(at);
        h.assertValueEqual(jets.size(), leaks, "the jets of a whole leak");
        // Rounding can leave it a hair over 30 atm, for one more check that loses next to nothing.
        h.assertValueEqual(
                jets.stream().filter(jet -> jet.amount() > 1e-6).count(),
                3L,
                "the jets of a whole leak with gas in them");
        a.close(moles(t), start * 30 / 39.9, "the tank's gas after a whole leak");
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "a whole leak", AtmosSounds.CONTAINER_LEAK);

        GasTankItem.setGas(t, GasTankItem.gas(tank(39.9)));
        h.assertValueEqual(TankFailure.check(a.level, at, t, null).kind(), Kind.LEAK, "refilled");
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "a new leak after a refill", AtmosSounds.CONTAINER_LEAK);

        GasTankItem.setGas(t, GasTankItem.gas(tank(39.9)));
        h.assertValueEqual(TankFailure.check(a.level, at, t, null).kind(), Kind.LEAK, "refilled while it leaks");
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "refilled while it leaks");
        a.tick();
        h.succeed();
    }

    /** The tank in a worn suit's slot: a copy each time it is read. */
    private static ItemStack wornTank(FakePlayer player) {
        return SuitItem.tank(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /**
     * A suit keeps a copy of its tank and writes it back only if the tank says it changed. So a leak in
     * a suit's slot hisses once, the check that ends it reaches the suit, and refilled it hisses again,
     * in real time (CONT-127).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void cont_127_a_leak_in_a_suit_hisses_once_and_again_when_refilled(GameTestHelper h) {
        SoundLog.listen();
        Area a = new Area(h, 885);
        a.forceLoad(true); // it waits in real time
        GasDeviceTests.room(a);
        FakePlayer player = TestPlayers.hurtable(a.level, "zas_suit_leak");
        player.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, tank(39.9));
        player.setItemSlot(EquipmentSlot.CHEST, suit);
        Vec3 at = TankFailure.pointOf(player);
        Runnable leakOver = () -> {
            ItemStack tank = wornTank(player);
            h.assertTrue(GasTankItem.contents(tank).pressure() < 30 * ATM + 1e-6, "still leaking");
            h.assertFalse(TankFailure.leaking(tank), "the suit's tank is still marked as leaking");
        };

        h.onEachTick(player.getInventory()::tick);
        h.startSequence()
                .thenWaitUntil(leakOver)
                .thenExecute(() -> {
                    DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "a whole leak", AtmosSounds.CONTAINER_LEAK);
                    ItemStack tank = wornTank(player);
                    GasTankItem.setGas(tank, GasTankItem.gas(tank(39.9)));
                    SuitItem.setTank(player.getItemBySlot(EquipmentSlot.CHEST), tank);
                })
                .thenWaitUntil(leakOver)
                .thenExecute(() -> {
                    DeviceSoundTests.heard(
                            h, at, SoundSource.BLOCKS, "a leak after a refill", AtmosSounds.CONTAINER_LEAK);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    /**
     * A TTV keeps copies of its tanks too, so a leak on one hisses once, the check that ends it reaches
     * the TTV, and refilled it hisses again (CONT-127).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_127_a_leak_on_a_ttv_hisses_once_and_again_when_refilled(GameTestHelper h) {
        SoundLog.listen();
        Area a = new Area(h, 886);
        GasDeviceTests.room(a);
        FakePlayer player = TestPlayers.hurtable(a.level, "zas_ttv_leak");
        player.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
        Vec3 at = TankFailure.pointOf(player);
        ItemStack ttv = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(ttv, new TransferValve(tank(39.9), ItemStack.EMPTY, false));

        for (int i = 0; i < 8; i++) {
            h.assertFalse(TransferValveItem.step(ttv, a.level, player), "the leak destroyed the TTV");
            a.tick();
        }
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "a whole leak", AtmosSounds.CONTAINER_LEAK);
        h.assertFalse(TankFailure.leaking(TransferValveItem.valve(ttv).first()), "the TTV's tank is still marked");

        TransferValve valve = TransferValveItem.valve(ttv);
        GasTankItem.setGas(valve.first(), GasTankItem.gas(tank(39.9)));
        TransferValveItem.save(ttv, valve);
        TransferValveItem.step(ttv, a.level, player);
        DeviceSoundTests.heard(h, at, SoundSource.BLOCKS, "a leak after a refill", AtmosSounds.CONTAINER_LEAK);
        a.tick();
        h.succeed();
    }
}
