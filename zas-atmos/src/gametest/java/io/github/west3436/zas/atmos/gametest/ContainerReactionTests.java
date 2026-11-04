package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reactions in containers (CONT-100): a pipe network, a canister and a tank above autoignition each
 * react, with no ignition source, once per atmos tick. The tests of placed containers drive their
 * steps themselves, within one game tick, in a batch of their own; the tests of carried tanks wait
 * in real time, since a stack's atmos tick is set by the game's clock.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContainerReactionTests {
    private static final String BATCH = "zas_atmos_container_reactions";
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");

    /** Above hydrogen's autoignition point, 858 K. */
    private static final double HOT = 900;

    private ContainerReactionTests() {}

    /** Enough oxygen to stay above the fire threshold (FIRE-030) after one parcel has burned. */
    private static GasParcel flammable(double temperature) {
        return new GasParcel(Map.of(H2, 5.0, O2, 10.0, N2, 15.0), temperature);
    }

    /** The hydrogen in {@link #flammableInATank}, mol. */
    private static final double TANK_H2 = 1.0;

    /**
     * The same mixture for a 70 L tank, a fifth as much of it. A tank burns all its gas at once, and
     * the 30 mol of {@link #flammable} would reach some 96 atm and explode it (CONT-038). This reaches
     * some 19 atm, which a tank holds, so these tests see the reaction alone.
     */
    private static GasParcel flammableInATank(double temperature) {
        return new GasParcel(Map.of(H2, TANK_H2, O2, 2.0, N2, 3.0), temperature);
    }

    /** The store after one atmos tick is exactly the API's answer for what it held. */
    private static void assertReacted(Area a, GasStore gas, GasMixture before, String what) {
        GasMixture expected = Reactions.applyStandalone(before, 1, false);
        a.h.assertTrue(expected != before, what + ": the API says nothing reacts, so the test proves nothing");
        a.close(gas.temperature(), expected.temperature(), what + ": temperature");
        for (ResourceLocation id : expected.moles().keySet()) {
            a.close(gas.moles(id), expected.moles(id), what + ": " + id);
        }
    }

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /**
     * A capped run of eight nodes, 560 L, burns a 375 L parcel per atmos tick, once for the whole
     * network: if each member ran it, the hydrogen would be gone after the first step.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_100_a_pipe_network_reacts_once_per_atmos_tick(GameTestHelper h) {
        Area a = new Area(h, 120);
        a.clear(0, 0, 10, 4);
        a.place(1, 1, 2, cap(Direction.EAST));
        for (int x = 2; x <= 7; x++) {
            a.place(x, 1, 2, AtmosBlocks.PIPE.get().defaultBlockState());
        }
        a.place(8, 1, 2, cap(Direction.WEST));
        PipeNetwork net = a.<PipeNodeEntity>entity(4, 1, 2).network();
        a.close(net.volume(), 8 * PipeVolumes.PIPE, "the network's volume");
        net.gas().add(flammable(HOT));

        GasMixture before = net.gas().mixture();
        a.step();
        assertReacted(a, net.gas(), before, "the first step");
        double left = net.gas().moles(H2);
        a.close(left, 5.0 * (1 - 375.0 / net.volume()), "H2 after one 375 L parcel of 560 L");
        h.assertTrue(net.gas().moles(H2O) > 0, "no water formed");

        before = net.gas().mixture();
        a.step();
        assertReacted(a, net.gas(), before, "the second step");
        h.assertTrue(net.gas().moles(H2) < left, "the second step burned nothing");
        h.succeed();
    }

    /** A flammable network below autoignition waits: there's no ignition source in a pipe. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_100_a_network_below_autoignition_does_not_react(GameTestHelper h) {
        Area a = new Area(h, 121);
        a.clear(0, 0, 6, 4);
        a.place(1, 1, 2, cap(Direction.EAST));
        a.place(2, 1, 2, cap(Direction.WEST));
        PipeNetwork net = a.<PipeNodeEntity>entity(1, 1, 2).network();
        net.gas().add(flammable(800));
        a.step();
        a.step();
        h.assertValueEqual(net.gas().moles(H2), 5.0, "H2 at 800 K");
        h.assertValueEqual(net.gas().moles(H2O), 0.0, "water at 800 K");
        h.assertValueEqual(net.gas().temperature(), 800.0, "the temperature at 800 K");
        h.succeed();
    }

    /**
     * FIRE-035: a container goes by the rule a zone does. At 12 % O₂, a network at 900 K burns all its
     * hydrogen with no source, and one at 800 K waits.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void fire_035_a_hot_network_below_14_percent_burns(GameTestHelper h) {
        Area a = new Area(h, 126);
        a.clear(0, 0, 10, 4);
        a.place(1, 1, 2, cap(Direction.EAST));
        a.place(2, 1, 2, cap(Direction.WEST));
        a.place(6, 1, 2, cap(Direction.EAST));
        a.place(7, 1, 2, cap(Direction.WEST));
        PipeNetwork hot = a.<PipeNodeEntity>entity(1, 1, 2).network();
        PipeNetwork warm = a.<PipeNodeEntity>entity(6, 1, 2).network();
        hot.gas().add(new GasParcel(Map.of(H2, 1.0, O2, 1.0, N2, 6.33), HOT));
        warm.gas().add(new GasParcel(Map.of(H2, 1.0, O2, 1.0, N2, 6.33), 800));
        GasMixture before = hot.gas().mixture();
        h.assertTrue(before.moles(O2) / before.totalMoles() < 0.14, "the network isn't short of O₂");

        a.step();

        h.assertValueEqual(hot.gas().moles(H2), 0.0, "H2 at 900 K");
        a.close(hot.gas().moles(H2O), 1.0, "water at 900 K");
        h.assertValueEqual(warm.gas().moles(H2), 1.0, "H2 at 800 K");
        h.assertValueEqual(warm.gas().moles(H2O), 0.0, "water at 800 K");
        h.succeed();
    }

    /**
     * A canister's gas reacts in its 1,000 L: 37.5 % of it burns per atmos tick (FIRE-031), the
     * parcel being fixed in litres. A canister on a port reacts as one network with the port.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_100_a_canister_reacts_alone_and_on_a_port(GameTestHelper h) {
        Area a = new Area(h, 122);
        a.clear(0, 0, 8, 4);
        a.place(1, 1, 2, AtmosBlocks.CANISTER.get().defaultBlockState());
        a.place(4, 1, 2, AtmosBlocks.CONNECTOR_PORT.get().defaultBlockState());
        a.place(4, 2, 2, AtmosBlocks.CANISTER.get().defaultBlockState());

        PipeNetwork alone = a.<PipeNodeEntity>entity(1, 1, 2).network();
        PipeNetwork onPort = a.<PipeNodeEntity>entity(4, 2, 2).network();
        a.close(alone.volume(), PipeVolumes.CANISTER, "the lone canister's volume");
        a.close(onPort.volume(), PipeVolumes.CANISTER + PipeVolumes.PORT, "the canister and port's volume");
        alone.gas().add(flammable(HOT));
        onPort.gas().add(flammable(HOT));

        GasMixture beforeAlone = alone.gas().mixture();
        GasMixture beforeOnPort = onPort.gas().mixture();
        a.step();
        assertReacted(a, alone.gas(), beforeAlone, "the lone canister");
        assertReacted(a, onPort.gas(), beforeOnPort, "the canister on a port");
        a.close(alone.gas().moles(H2), 5.0 * (1 - 375.0 / PipeVolumes.CANISTER), "the lone canister's H2");
        a.close(onPort.gas().moles(H2), 5.0 * (1 - 375.0 / 1200.0), "H2 in the canister and port");
        double pressure = beforeAlone.pressure();
        h.assertTrue(alone.gas().pressure() > pressure, "the burn raised no pressure");
        h.succeed();
    }

    /**
     * A 70 L tank burns all its gas in one atmos tick (FIRE-031) wherever a player has it: in the
     * inventory, held, in the offhand and in a worn suit's tank slot. A tank below autoignition
     * doesn't react, and neither does a mined canister's gas below it; above it, that burns as well.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_100_a_tank_reacts_wherever_a_player_has_it(GameTestHelper h) {
        Area a = new Area(h, 123);
        FakePlayer p = FakePlayerFactory.get(a.level, new GameProfile(UUID.randomUUID(), "zas_atmos_tanks"));
        p.moveTo(a.point(1, 1, 1));
        ItemStack carried = tank(HOT);
        ItemStack held = tank(HOT);
        ItemStack offhand = tank(HOT);
        ItemStack cold = tank(800);
        ItemStack canister = new ItemStack(AtmosItems.CANISTER.get());
        canister.set(AtmosComponents.GAS.get(), flammable(HOT));
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, tank(HOT));
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, held);
        p.getInventory().setItem(9, carried);
        p.getInventory().setItem(10, cold);
        p.getInventory().setItem(11, canister);
        p.setItemSlot(EquipmentSlot.OFFHAND, offhand);
        p.setItemSlot(EquipmentSlot.CHEST, suit);

        int[] ticks = new int[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    p.getInventory().tick(); // what a real player's tick does
                    h.assertTrue(++ticks[0] >= 20, "waiting for one atmos tick");
                })
                .thenExecute(() -> {
                    for (ItemStack tank : new ItemStack[] {carried, held, offhand, SuitItem.tank(suit)}) {
                        h.assertValueEqual(GasTankItem.gas(tank).moles(H2), 0.0, "H2 left in a tank");
                        a.close(GasTankItem.gas(tank).moles(H2O), TANK_H2, "water in a tank");
                        h.assertTrue(GasTankItem.gas(tank).temperature() > HOT, "a tank didn't heat up");
                    }
                    h.assertTrue(
                            cold.get(AtmosComponents.GAS.get()).equals(flammableInATank(800)), "the cold tank reacted");
                    a.close(
                            canister.get(AtmosComponents.GAS.get()).moles(H2),
                            5.0 * (1 - 375.0 / PipeVolumes.CANISTER),
                            "the canister item's H2");
                })
                .thenSucceed();
    }

    private static ItemStack tank(double temperature) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, flammableInATank(temperature));
        return tank;
    }

    /**
     * A carried tank reacts once per atmos tick, whatever game ticks it is offered: N₂O decomposes a
     * little at a time, so each reaction shows. Over 40 game ticks that's two.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_100_a_carried_tank_reacts_once_per_atmos_tick(GameTestHelper h) {
        Area a = new Area(h, 124);
        FakePlayer p = FakePlayerFactory.get(a.level, new GameProfile(UUID.randomUUID(), "zas_atmos_cadence"));
        p.moveTo(a.point(1, 1, 1));
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasParcel start = new GasParcel(Map.of(N2O, 10.0), 1000);
        GasTankItem.setGas(tank, start);
        p.getInventory().setItem(9, tank);

        int[] ticks = new int[1];
        int[] reactions = new int[1];
        GasParcel[] last = {start};
        GasMixture[] expected = {new GasMixture(start.moles(), start.temperature(), 70)};
        h.startSequence()
                .thenWaitUntil(() -> {
                    p.getInventory().tick();
                    GasParcel now = GasTankItem.gas(tank);
                    if (!now.equals(last[0])) {
                        reactions[0]++;
                        last[0] = now;
                        expected[0] = Reactions.applyStandalone(expected[0], 1, false);
                        a.close(now.moles(N2O), expected[0].moles(N2O), "N2O after reaction " + reactions[0]);
                    }
                    h.assertTrue(++ticks[0] >= 40, "waiting for 40 game ticks");
                })
                .thenExecute(() -> h.assertValueEqual(reactions[0], 2, "reactions in 40 game ticks"))
                .thenSucceed();
    }

    /** A dropped tank reacts, and so does the tank in a dropped suit: the game ticks both entities. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_100_a_dropped_tank_reacts(GameTestHelper h) {
        Area a = new Area(h, 125);
        a.forceLoad(true);
        a.clear(0, 0, 6, 4);
        a.set(2, 0, 2, Area.STONE);
        a.set(4, 0, 2, Area.STONE);
        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, tank(HOT));
        ItemEntity droppedTank = drop(a, 2, tank(HOT));
        ItemEntity droppedSuit = drop(a, 4, suit);
        h.startSequence()
                .thenWaitUntil(() -> {
                    a.close(GasTankItem.gas(droppedTank.getItem()).moles(H2O), TANK_H2, "water in the dropped tank");
                    a.close(
                            GasTankItem.gas(SuitItem.tank(droppedSuit.getItem()))
                                    .moles(H2O),
                            TANK_H2,
                            "water in the dropped suit's tank");
                })
                .thenExecute(() -> {
                    droppedTank.discard();
                    droppedSuit.discard();
                    a.clear(0, 0, 6, 4);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    private static ItemEntity drop(Area a, int x, ItemStack stack) {
        Vec3 at = a.point(x, 1, 2);
        ItemEntity item = new ItemEntity(a.level, at.x, at.y, at.z, stack, 0, 0, 0);
        item.setUnlimitedLifetime();
        a.level.addFreshEntity(item);
        return item;
    }
}
