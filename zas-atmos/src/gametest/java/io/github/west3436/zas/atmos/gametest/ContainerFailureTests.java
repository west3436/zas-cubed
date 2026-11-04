package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import io.github.west3436.zas.atmos.failure.TankFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.gametest.TestPlayers;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How tanks fail (CONT-038), what their explosions do to blocks under each preset (CONT-107) and how
 * strong they can be (CONT-109). Most build in their own {@link Area} and drive atmos ticks
 * themselves, in a batch of their own; explosions are watched through {@link Blasts}. The rule's
 * every threshold is in {@code ContainerFailureTest}.
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
}
