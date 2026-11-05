package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortablePumpMath;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The portable pump (CONT-018, D-73). Each test builds in its own {@link Site} and runs within one
 * game tick, in a batch of its own so other tests' devices never meet these.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortablePumpTests {
    private static final String BATCH = "zas_atmos_portable_pump";
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final double T = 293.15;

    /** What a step of pumping costs the battery, FE. */
    private static final int STEP = 20 * AtmosTicks.PERIOD;

    private PortablePumpTests() {}

    private static PortablePumpBlockEntity pump(Site s, int x, int y, int z) {
        BlockState state =
                AtmosBlocks.PORTABLE_PUMP.get().defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH);
        return s.place(x, y, z, state, PortablePumpBlockEntity.class);
    }

    private static boolean lit(Site s, PortablePumpBlockEntity pump) {
        return s.level.getBlockState(pump.getBlockPos()).getValue(PortableBlock.LIT);
    }

    private static GasMixture air(Site s) {
        return s.zone(2, 2, 2).mixture();
    }

    private static GasMixture tankGas(PortablePumpBlockEntity pump) {
        return GasTankItem.contents(pump.tank().tank());
    }

    private static ItemStack tank(GasParcel gas) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, gas);
        return tank;
    }

    /** O₂ at a pressure, in a 70 L tank. */
    private static GasParcel o2AtKpa(double kPa) {
        return new GasParcel(Map.of(O2, kPa * 70 / (GasMixture.GAS_CONSTANT * T)), T);
    }

    private static boolean dropped(Site s, int x, int y, int z, Item item) {
        return !s.level
                .getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(s.abs(x, y, z)).inflate(1),
                        e -> e.getItem().is(item))
                .isEmpty();
    }

    /**
     * A portable pump in a sealed room siphons the room's air into its store up to its target, then
     * releases it to the room up to its target, through one change at a time, drawing 20 FE/t from its
     * battery only while it moves gas. It does nothing off, with no battery or in open air; with too
     * little charge it moves what the charge pays for, then stops. Its GUI refuses unknown buttons and
     * clamps the target, and broken it drops its battery and tank (CONT-018, CONT-011, CONT-072,
     * CONT-074, CONT-112).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_portable_pump_works_on_its_room_on_its_battery(GameTestHelper h) {
        Site s = new Site(h, 88);
        try {
            BatteryTests.clear(s);
            s.room(0, 4); // interior x 1–3
            PortablePumpBlockEntity pump = pump(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2); // the room holds the Overworld's air
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            PortablePumpMenu menu = (PortablePumpMenu) pump.createMenu(1, hand.getInventory(), hand);
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.PORTABLE_PUMP_DRAW), 20, "the draw");

            // New and off, it does nothing.
            GasMixture start = air(s);
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.OFF, "the new pump's status");
            h.assertFalse(pump.siphon(), "a new pump siphons");
            s.close(pump.target(), PortablePumpMath.DEFAULT_TARGET, 0, "a new pump's target");
            s.close(air(s).totalMoles(), start.totalMoles(), 1e-6, "the room under a pump that is off");

            // The GUI: on, the direction, the target clamped; unknown buttons refused.
            h.assertTrue(menu.clickMenuButton(hand, PortablePumpMenu.TOGGLE), "switching on");
            h.assertTrue(pump.on(), "the pump is off");
            h.assertTrue(menu.clickMenuButton(hand, PortablePumpMenu.TOGGLE_DIRECTION), "the direction");
            h.assertTrue(pump.siphon(), "the pump doesn't siphon");
            h.assertValueEqual(menu.value(PortablePumpMenu.DATA_SIPHON), 1, "the GUI's direction");
            h.assertFalse(menu.clickMenuButton(hand, -1), "button -1");
            h.assertFalse(menu.clickMenuButton(hand, PortablePumpMenu.LAST_BUTTON + 1), "button 6");
            h.assertTrue(menu.clickMenuButton(hand, PortablePumpMenu.LAST_BUTTON), "target +100");
            s.close(pump.target(), PortablePumpMath.DEFAULT_TARGET + 100, 1e-9, "the target after +100");
            h.assertTrue(menu.clickMenuButton(hand, PortablePumpMenu.TARGET_DOWN_100 + 1), "target -10");
            s.close(pump.target(), PortablePumpMath.DEFAULT_TARGET + 90, 1e-9, "the target after -10");
            for (int i = 0; i < 3; i++) {
                menu.clickMenuButton(hand, PortablePumpMenu.TARGET_DOWN_100);
            }
            s.close(pump.target(), 0, 0, "the lowest target");
            for (int i = 0; i < 50; i++) {
                menu.clickMenuButton(hand, PortablePumpMenu.LAST_BUTTON);
            }
            s.close(pump.target(), 4_500, 0, "the highest target");
            h.assertValueEqual(menu.value(PortablePumpMenu.DATA_TARGET), 4_500, "the GUI's target");
            pump.setTarget(300);

            // On with no battery, it does nothing.
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.NO_BATTERY, "the pump with no battery");

            // A full battery: it waits for the air to settle, then siphons 150 L of the air each second, in
            // the air's proportions, and costs 20 FE/t.
            BatteryTests.swap(pump.battery(), AtmosItems.BATTERY.get().full());
            for (int i = 0; i < 6 && pump.status() != PortableStatus.WORKING; i++) {
                s.cycle(pump);
            }
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump after it settled");
            h.assertTrue(lit(s, pump), "not lit while pumping");
            s.close(
                    pump.store().totalMoles(),
                    start.totalMoles() * PortablePumpMath.LITRES / start.volume(),
                    1e-6,
                    "the store after a second");
            h.assertValueEqual(pump.energy(), 100_000 - STEP, "the battery after a step");

            // It carries on up to the target.
            int steps = 1;
            for (; steps < 100 && pump.store().pressure() < 299.9; steps++) {
                s.cycle(pump);
            }
            s.close(pump.store().pressure(), 300, 1, "the store after a siphon");
            GasMixture after = air(s);
            s.close(after.moles(O2) + pump.store().moles(O2), start.moles(O2), 1e-6, "O2 lost in the siphon");
            s.close(after.moles(N2) + pump.store().moles(N2), start.moles(N2), 1e-6, "N2 lost in the siphon");
            s.close(
                    pump.store().moles(O2) / pump.store().moles(N2),
                    start.moles(O2) / start.moles(N2),
                    1e-6,
                    "the store's proportions");
            h.assertValueEqual(pump.energy(), 100_000 - steps * STEP, "the battery after the siphon");

            // At the target, it stops and draws nothing.
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.AT_TARGET, "the pump at its target");
            h.assertValueEqual(pump.energy(), 100_000 - steps * STEP, "the battery at the target");
            h.assertFalse(lit(s, pump), "lit at the target");

            // Released: 150 L of the store a second, one change at a time.
            pump.setSiphon(false);
            double goal = air(s).pressure() + 5;
            pump.setTarget(goal);
            GasMixture room = air(s);
            GasMixture store = pump.store().mixture();
            double moles = PortablePumpMath.release(goal, store, room);
            s.close(moles, store.totalMoles() * 150 / 2000, 1e-9, "a step's release");
            pump.step();
            h.assertFalse(pump.readyToStep(), "it took a second change before the first finished (CONT-072)");
            s.tick();
            h.assertTrue(pump.readyToStep(), "the change never finished");
            s.close(air(s).totalMoles() - room.totalMoles(), moles, 1e-6, "the room's gain in a step");
            s.close(store.totalMoles() - pump.store().totalMoles(), moles, 1e-6, "the store's loss in a step");

            // It brings the room to the target and no further, then stops.
            for (int i = 0; i < 60 && pump.status() != PortableStatus.AT_TARGET; i++) {
                s.cycle(pump);
                h.assertTrue(air(s).pressure() <= goal + 0.5, "the room passed the target: " + air(s).pressure());
            }
            h.assertValueEqual(pump.status(), PortableStatus.AT_TARGET, "the pump at the room's target");
            s.close(air(s).pressure(), goal, 0.5, "the room at the target");

            // Too little charge: it moves what the charge pays for, then stops.
            pump.store().set(new GasParcel(Map.of(O2, 100.0), T));
            pump.setTarget(4_500);
            BatteryTests.swap(pump.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), STEP / 2));
            room = air(s);
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump on its last charge");
            s.close(air(s).totalMoles() - room.totalMoles(), 100.0 * 150 / 2000 / 2, 1e-6, "half a step's release");
            h.assertValueEqual(pump.energy(), 0, "the battery after its last step");
            double moleAtEmpty = air(s).totalMoles();
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.BATTERY_EMPTY, "the pump on an empty battery");
            s.close(air(s).totalMoles(), moleAtEmpty, 1e-6, "the room under an empty battery");
            h.assertFalse(lit(s, pump), "lit on an empty battery");

            // With an empty store it has nothing to release, and draws nothing.
            BatteryTests.swap(pump.battery(), AtmosItems.BATTERY.get().full());
            pump.store().drain();
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.NOTHING_TO_MOVE, "the pump with an empty store");
            h.assertValueEqual(pump.energy(), 100_000, "the battery with an empty store");

            // In open air it has no room, and leaves its battery alone.
            PortablePumpBlockEntity outside = pump(s, 8, 1, 2);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.setOn(true);
            s.cycles(5, outside);
            h.assertValueEqual(outside.status(), PortableStatus.NO_ROOM, "the pump in open air");
            h.assertValueEqual(outside.energy(), 100_000, "the battery in open air");

            // Broken, it drops its battery and its tank.
            outside.tank().insertItem(0, tank(new GasParcel(Map.of(O2, 1.0), T)), false);
            s.set(8, 1, 2, Site.AIR);
            h.assertTrue(dropped(s, 8, 1, 2, AtmosItems.BATTERY.get()), "it dropped no battery");
            h.assertTrue(dropped(s, 8, 1, 2, AtmosItems.EMPTY_TANK.get()), "it dropped no tank");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable pump with a held tank works on the tank instead of the room, at once and with no wait
     * for a cell, in a room or in open air. It fills the tank to its target, siphons it into its store,
     * and stops when its battery is empty; the tank takes the kind of what it holds; and a tank it
     * overfills leaks and ruptures where the pump stands (CONT-018, CONT-038, CONT-039).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_portable_pump_works_on_a_held_tank(GameTestHelper h) {
        Site s = new Site(h, 89);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            PortablePumpBlockEntity pump = pump(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            GasMixture room = air(s);
            BatteryTests.swap(pump.battery(), AtmosItems.BATTERY.get().full());
            pump.store().set(new GasParcel(Map.of(O2, 100.0), T));
            pump.tank().insertItem(0, new ItemStack(AtmosItems.EMPTY_TANK.get()), false);
            pump.setTarget(1_000);
            pump.setOn(true);

            // One step: 150 L of the store goes to the tank, at once, and the room is untouched.
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump on a tank");
            s.close(tankGas(pump).moles(O2), 100.0 * 150 / 2000, 1e-9, "the tank's O2 after a step");
            s.close(pump.store().moles(O2), 100.0 - 100.0 * 150 / 2000, 1e-9, "the store's O2 after a step");
            h.assertTrue(
                    pump.tank().tank().is(AtmosItems.OXYGEN_TANK.get()),
                    "the tank of O2 is " + pump.tank().tank());
            s.close(air(s).totalMoles(), room.totalMoles(), 1e-6, "the room while it fills a tank");
            h.assertValueEqual(pump.energy(), 100_000 - STEP, "the battery after a step");

            // It brings the tank to its target and stops.
            for (int i = 0; i < 20 && pump.status() != PortableStatus.AT_TARGET; i++) {
                s.cycle(pump);
            }
            h.assertValueEqual(pump.status(), PortableStatus.AT_TARGET, "the pump at the tank's target");
            s.close(tankGas(pump).pressure(), 1_000, 0.5, "the tank at its target");
            s.close(air(s).totalMoles(), room.totalMoles(), 1e-6, "the room beside a filled tank");

            // Saved and loaded, it keeps its direction, target, store, tank and battery.
            CompoundTag saved = pump.saveWithoutMetadata(s.level.registryAccess());
            PortablePumpBlockEntity loaded = new PortablePumpBlockEntity(pump.getBlockPos(), pump.getBlockState());
            loaded.loadWithComponents(saved, s.level.registryAccess());
            s.close(loaded.target(), 1_000, 0, "the loaded target");
            h.assertFalse(loaded.siphon(), "the loaded pump siphons");
            s.close(loaded.store().moles(O2), pump.store().moles(O2), 1e-9, "the loaded store's O2");
            h.assertTrue(loaded.tank().tank().is(AtmosItems.OXYGEN_TANK.get()), "the loaded tank");
            h.assertValueEqual(loaded.energy(), pump.energy(), "the loaded battery");

            // Siphoned: the tank's gas comes back to the store, at once, and the tank is empty again.
            pump.setSiphon(true);
            pump.setTarget(4_500);
            int charge = pump.energy();
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump siphoning a tank");
            s.close(pump.store().moles(O2), 100, 1e-6, "the store after siphoning the tank");
            h.assertTrue(
                    pump.tank().tank().is(AtmosItems.EMPTY_TANK.get()),
                    "the emptied tank is " + pump.tank().tank());
            h.assertValueEqual(pump.energy(), charge - STEP, "the battery after siphoning");
            s.close(air(s).totalMoles(), room.totalMoles(), 1e-6, "the room while it siphons a tank");

            // Nothing left in the tank: it stops and draws nothing, and doesn't turn to the room.
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.NOTHING_TO_MOVE, "the pump on an empty tank");
            h.assertValueEqual(pump.energy(), charge - STEP, "the battery on an empty tank");
            s.close(air(s).totalMoles(), room.totalMoles(), 1e-6, "the room beside an empty tank");

            // Half a step's charge moves half; then an empty battery stops it.
            pump.setSiphon(false);
            BatteryTests.swap(pump.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), STEP / 2));
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump on its last charge");
            s.close(tankGas(pump).moles(O2), 100.0 * 150 / 2000 / 2, 1e-9, "the tank's O2 at half a step");
            h.assertValueEqual(pump.energy(), 0, "the battery after its last step");
            double oxygen = tankGas(pump).moles(O2);
            s.cycle(pump);
            h.assertValueEqual(pump.status(), PortableStatus.BATTERY_EMPTY, "the pump on an empty battery");
            s.close(tankGas(pump).moles(O2), oxygen, 1e-9, "the tank's O2 at an empty battery");

            // A tank above 30 atm leaks into the room where the pump stands, on or off.
            pump.setOn(false);
            pump.tank().setStackInSlot(0, tank(o2AtKpa(3_200)));
            double roomO2 = air(s).moles(O2);
            double full = tankGas(pump).moles(O2);
            s.cycle(pump);
            h.assertTrue(tankGas(pump).moles(O2) < full, "a tank above 30 atm didn't leak");
            s.close(air(s).moles(O2) - roomO2, full - tankGas(pump).moles(O2), 1e-6, "the room's O2 from the leak");

            // Above 40 atm it ruptures: the tank is gone and its gas in the room, and the pump stands.
            pump.tank().setStackInSlot(0, tank(o2AtKpa(4_300)));
            roomO2 = air(s).moles(O2);
            full = tankGas(pump).moles(O2);
            s.cycle(pump);
            h.assertTrue(pump.tank().tank().isEmpty(), "a tank above 40 atm didn't rupture");
            s.close(air(s).moles(O2) - roomO2, full, 1e-6, "the room's O2 from the rupture");
            h.assertFalse(pump.isRemoved(), "the rupture broke the pump");

            // In open air, a held tank still works: it needs no zone.
            PortablePumpBlockEntity outside = pump(s, 8, 1, 2);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.tank().insertItem(0, tank(new GasParcel(Map.of(O2, 5.0), T)), false);
            outside.setSiphon(true);
            outside.setTarget(1_000);
            outside.setOn(true);
            s.cycle(outside);
            h.assertValueEqual(outside.status(), PortableStatus.WORKING, "the pump on a tank in open air");
            s.close(outside.store().moles(O2), 5.0, 1e-9, "the store in open air");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable pump in a controller's room runs on its own battery. The controller doesn't power it,
     * list it or pay for it, and a link to it doesn't hold (CONT-018, D-73).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_controller_leaves_a_portable_pump_in_its_room_alone(GameTestHelper h) {
        Site s = new Site(h, 90);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            PortablePumpBlockEntity pump = pump(s, 2, 1, 3);
            BatteryTests.swap(pump.battery(), AtmosItems.BATTERY.get().full());
            pump.setSiphon(true);
            pump.setTarget(4_500);
            pump.setOn(true);
            BatteryTests.formZone(s, 2, 2, 2);
            Object part = pump;
            h.assertFalse(part instanceof io.github.west3436.zas.atmos.power.PoweredDevice, "it is a part");
            for (int i = 0; i < 6 && pump.status() != PortableStatus.WORKING; i++) {
                s.cycle(c, pump);
            }
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump in the controller's room");
            pump.setSiphon(false);
            c.toggleLink(pump.getBlockPos());
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            int before = pump.energy();
            int buffer = c.buffer().getEnergyStored();
            s.cycle(c, pump);
            h.assertValueEqual(pump.status(), PortableStatus.WORKING, "the pump after the link");
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(pump.energy(), before - STEP, "the battery after a step");
            h.assertTrue(buffer - c.buffer().getEnergyStored() <= own, "the buffer paid for the pump");
            h.assertTrue(
                    c.parts().stream().noneMatch(p -> p.pos().equals(pump.getBlockPos())),
                    "the controller lists the pump");
            h.assertFalse(c.links().contains(pump.getBlockPos()), "the link to the pump held");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /** A portable pump's item keeps its store's gas when mined, as a canister's does (CONT-018, CONT-017). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_mined_portable_pump_keeps_its_gas(GameTestHelper h) {
        Site s = new Site(h, 91);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            GasParcel gas = new GasParcel(Map.of(O2, 12.5), T);
            PortablePumpBlockEntity mined = pump(s, 2, 1, 2);
            mined.store().add(gas);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos pos = s.abs(2, 1, 2);
            BlockState state = s.level.getBlockState(pos);
            BlockEntity be = s.level.getBlockEntity(pos);
            state.getBlock().playerWillDestroy(s.level, pos, state, player);
            s.level.removeBlock(pos, false);
            ItemStack item = Block.getDrops(state, s.level, pos, be, player, player.getMainHandItem()).stream()
                    .filter(d -> d.is(AtmosItems.PORTABLE_PUMP.get()))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            h.assertTrue(
                    gas.equals(item.get(AtmosComponents.GAS.get())),
                    "the item " + item + " carries " + item.get(AtmosComponents.GAS.get()));
            PortablePumpBlockEntity again = pump(s, 2, 1, 2);
            again.applyComponentsFromItemStack(item);
            s.close(again.store().moles(O2), 12.5, 1e-9, "the O2 after placing it again");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }
}
