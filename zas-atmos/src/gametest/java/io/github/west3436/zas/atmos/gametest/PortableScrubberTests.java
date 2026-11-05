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
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.portable.PortableGasMenu;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableScrubberMath;
import io.github.west3436.zas.atmos.portable.PortableScrubberMenu;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
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
 * The portable scrubber (CONT-018, D-73). Each test builds in its own {@link Site} and runs within one
 * game tick, in a batch of its own so other tests' devices never meet these.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortableScrubberTests {
    private static final String BATCH = "zas_atmos_portable_scrubber";
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final double T = 293.15;

    /** What a step of scrubbing costs the battery, FE. */
    private static final int STEP = 15 * AtmosTicks.PERIOD;

    private PortableScrubberTests() {}

    private static PortableScrubberBlockEntity scrubber(Site s, int x, int y, int z) {
        BlockState state =
                AtmosBlocks.PORTABLE_SCRUBBER.get().defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH);
        return s.place(x, y, z, state, PortableScrubberBlockEntity.class);
    }

    private static boolean lit(Site s, PortableScrubberBlockEntity scrubber) {
        return s.level.getBlockState(scrubber.getBlockPos()).getValue(PortableBlock.LIT);
    }

    private static double co2(Site s) {
        return s.zone(2, 2, 2).mixture().moles(CO2);
    }

    private static ItemStack tank(GasParcel gas) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, gas);
        return tank;
    }

    private static boolean dropped(Site s, int x, int y, int z, Item item) {
        return !s.level
                .getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(s.abs(x, y, z)).inflate(1),
                        e -> e.getItem().is(item))
                .isEmpty();
    }

    /** Steps it until it works, past the wait for its cell to settle. */
    private static void untilWorking(Site s, PortableScrubberBlockEntity scrubber, AreaAirControllerBlockEntity c) {
        for (int i = 0; i < 6 && scrubber.status() != PortableStatus.WORKING; i++) {
            if (c == null) {
                s.cycle(scrubber);
            } else {
                s.cycle(c, scrubber);
            }
        }
        s.h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "the scrubber after it settled");
    }

    /**
     * A portable scrubber in a sealed room scrubs the room's CO₂ into its store and leaves the rest,
     * through one change at a time, drawing 15 FE/t from its battery only while it moves gas. It does
     * nothing off, with no battery or in open air; its slots refuse what they don't hold; with too
     * little charge it moves what the charge pays for, then stops. Its GUI refuses unknown buttons and
     * shows what it did, and broken it drops its battery and tank (CONT-018, CONT-072, CONT-074,
     * CONT-112).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_portable_scrubber_scrubs_its_room_on_its_battery(GameTestHelper h) {
        Site s = new Site(h, 83);
        try {
            BatteryTests.clear(s);
            s.room(0, 4); // interior x 1–3
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2); // the room holds the Overworld's air
            s.addKpa(2, 2, 2, CO2, 2);
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            PortableScrubberMenu menu = (PortableScrubberMenu) scrubber.createMenu(1, hand.getInventory(), hand);
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.PORTABLE_SCRUBBER_DRAW), 15, "the draw");

            // Off, it does nothing.
            double co2 = co2(s);
            h.assertTrue(co2 > 0, "the room has no CO2");
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.OFF, "the new scrubber's status");
            h.assertValueEqual(menu.value(PortableGasMenu.DATA_AIR), PortableGasMenu.NO_AIR, "the GUI's air while off");
            s.close(co2(s), co2, 1e-9, "the room's CO2 under a scrubber that is off");

            // The GUI: on; unknown buttons refused.
            h.assertTrue(menu.clickMenuButton(hand, PortableScrubberMenu.TOGGLE), "switching on");
            h.assertFalse(menu.clickMenuButton(hand, -1), "button -1");
            h.assertFalse(menu.clickMenuButton(hand, 1), "button 1");
            h.assertTrue(scrubber.on(), "the scrubber is off");

            // On with no battery, it does nothing; each slot refuses coal, and the tank slot a battery.
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.NO_BATTERY, "the scrubber with no battery");
            h.assertValueEqual(
                    scrubber.battery()
                            .insertItem(0, new ItemStack(Items.COAL), false)
                            .getCount(),
                    1,
                    "coal went in the battery slot");
            h.assertValueEqual(
                    scrubber.tank()
                            .insertItem(0, new ItemStack(Items.COAL), false)
                            .getCount(),
                    1,
                    "coal went in the tank slot");
            h.assertValueEqual(
                    scrubber.tank()
                            .insertItem(0, AtmosItems.BATTERY.get().full(), false)
                            .getCount(),
                    1,
                    "a battery went in the tank slot");

            // A shift-click puts a battery and a tank in their own slots, and takes them out again.
            hand.getInventory().setItem(9, AtmosItems.BATTERY.get().full());
            hand.getInventory().setItem(10, tank(new GasParcel(Map.of(O2, 1.0), T)));
            menu.quickMoveStack(hand, 2);
            menu.quickMoveStack(hand, 3);
            h.assertTrue(scrubber.battery().battery().is(AtmosItems.BATTERY.get()), "the battery isn't in its slot");
            h.assertTrue(scrubber.tank().tank().is(AtmosItems.EMPTY_TANK.get()), "the tank isn't in its slot");
            menu.quickMoveStack(hand, 0);
            menu.quickMoveStack(hand, 1);
            h.assertTrue(scrubber.battery().battery().isEmpty(), "the battery stayed in its slot");
            h.assertTrue(scrubber.tank().tank().isEmpty(), "the tank stayed in its slot");

            // A full battery: it waits for the air to settle, then scrubs.
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            h.assertValueEqual(scrubber.energy(), 100_000, "the full battery");
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.WAITING, "the scrubber before its cell settled");
            h.assertValueEqual(scrubber.energy(), 100_000, "the battery while it waits");
            untilWorking(s, scrubber, null);
            h.assertTrue(lit(s, scrubber), "not lit while scrubbing");

            // A step takes what the math says of the CO2 and only the CO2, and costs 15 FE/t.
            GasMixture air = s.zone(2, 2, 2).mixture();
            double co2Before = air.moles(CO2);
            double storeBefore = scrubber.store().moles(CO2);
            int charge = scrubber.energy();
            double want = PortableScrubberMath.take(
                            air, GasSelection.DEFAULT, scrubber.store().mixture())
                    .get(CO2);
            h.assertTrue(want > 0, "the math takes nothing");
            scrubber.step();
            h.assertFalse(scrubber.readyToStep(), "it took a second change before the first finished (CONT-072)");
            s.tick();
            h.assertTrue(scrubber.readyToStep(), "the change never finished");
            GasMixture after = s.zone(2, 2, 2).mixture();
            s.close(co2Before - after.moles(CO2), want, 1e-6, "the room's CO2 lost in a step");
            s.close(scrubber.store().moles(CO2) - storeBefore, want, 1e-6, "the store's CO2 gained in a step");
            s.close(after.moles(O2), air.moles(O2), 1e-6, "the room's O2");
            s.close(after.moles(N2), air.moles(N2), 1e-6, "the room's N2");
            h.assertValueEqual(scrubber.energy(), charge - STEP, "the battery after a step");

            // The GUI shows it.
            h.assertValueEqual(menu.status(), PortableStatus.WORKING, "the GUI's status");
            s.close(menu.value(PortableGasMenu.DATA_AIR), air.pressure(), 1, "the GUI's room pressure");
            h.assertValueEqual(menu.value(PortableGasMenu.DATA_ON_TANK), 0, "the GUI says it works on a tank");
            s.close(menu.value(PortableGasMenu.DATA_STORE), scrubber.store().pressure(), 1, "the GUI's store");
            h.assertValueEqual(menu.energy(), scrubber.energy(), "the GUI's charge");

            // Too little charge: it moves what the charge pays for, then stops.
            BatteryTests.swap(scrubber.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), STEP / 2));
            air = s.zone(2, 2, 2).mixture();
            want = PortableScrubberMath.take(
                            air, GasSelection.DEFAULT, scrubber.store().mixture())
                    .get(CO2);
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "the scrubber on its last charge");
            s.close(air.moles(CO2) - co2(s), want / 2, 1e-6, "the CO2 half a step's charge moved");
            h.assertValueEqual(scrubber.energy(), 0, "the battery after its last step");
            co2 = co2(s);
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.BATTERY_EMPTY, "the scrubber on an empty battery");
            s.close(co2(s), co2, 1e-9, "the room's CO2 under an empty battery");
            h.assertFalse(lit(s, scrubber), "lit on an empty battery");

            // With nothing to scrub, it stops and draws nothing.
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            s.removeGas(2, 2, 2, CO2);
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.NOTHING_TO_MOVE, "the scrubber in clean air");
            h.assertValueEqual(scrubber.energy(), 100_000, "the battery in clean air");
            h.assertFalse(lit(s, scrubber), "lit in clean air");

            // Switched off, it stops.
            h.assertTrue(menu.clickMenuButton(hand, PortableScrubberMenu.TOGGLE), "switching off");
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.OFF, "the switched-off scrubber");

            // In open air it has no room, and leaves its battery alone.
            PortableScrubberBlockEntity outside = scrubber(s, 8, 1, 2);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.setOn(true);
            s.cycles(5, outside);
            h.assertValueEqual(outside.status(), PortableStatus.NO_ROOM, "the scrubber in open air");
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
     * A portable scrubber with a held tank scrubs the tank instead of the room, at once and with no wait
     * for a cell, in a room or in open air. The tank becomes the kind of what is left; a battery that
     * runs out stops it; and a store at 50 atm stops it, drawing nothing (CONT-018, CONT-039).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_portable_scrubber_scrubs_a_held_tank(GameTestHelper h) {
        Site s = new Site(h, 84);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            s.addKpa(2, 2, 2, CO2, 2);
            double room = co2(s);
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            h.assertTrue(
                    scrubber.tank()
                            .insertItem(0, tank(new GasParcel(Map.of(O2, 5.0, CO2, 2.0), T)), false)
                            .isEmpty(),
                    "the tank didn't go in");
            h.assertValueEqual(
                    scrubber.tank()
                            .insertItem(0, tank(new GasParcel(Map.of(O2, 5.0), T)), false)
                            .getCount(),
                    1,
                    "a second tank went in");
            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            PortableScrubberMenu menu = (PortableScrubberMenu) scrubber.createMenu(1, hand.getInventory(), hand);
            double tankPressure = GasTankItem.contents(scrubber.tank().tank()).pressure();
            scrubber.setOn(true);

            // One step: the tank's CO2 goes to the store, its O2 stays, and the room is untouched.
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "the scrubber on a tank");
            s.close(scrubber.store().moles(CO2), 2.0, 1e-9, "the store's CO2");
            GasParcel left = GasTankItem.gas(scrubber.tank().tank());
            s.close(left.moles().getOrDefault(CO2, 0.0), 0, 1e-9, "the tank's CO2");
            s.close(left.moles().getOrDefault(O2, 0.0), 5.0, 1e-9, "the tank's O2");
            h.assertTrue(
                    scrubber.tank().tank().is(AtmosItems.OXYGEN_TANK.get()),
                    "the tank of nothing but O2 is " + scrubber.tank().tank());
            s.close(co2(s), room, 1e-9, "the room's CO2 while it works on a tank");
            h.assertValueEqual(scrubber.energy(), 100_000 - STEP, "the battery after a step");
            h.assertValueEqual(menu.value(PortableGasMenu.DATA_ON_TANK), 1, "the GUI says it works on the room");
            s.close(menu.value(PortableGasMenu.DATA_AIR), tankPressure, 1, "the GUI's tank pressure");

            // Nothing left to scrub in the tank: it stops and draws nothing, and doesn't turn to the room.
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.NOTHING_TO_MOVE, "the scrubber on a clean tank");
            h.assertValueEqual(scrubber.energy(), 100_000 - STEP, "the battery on a clean tank");
            s.close(co2(s), room, 1e-9, "the room's CO2 beside a clean tank");

            // A tank taken out sends it back to the room.
            ItemStack out = scrubber.tank().extractItem(0, 1, false);
            h.assertTrue(out.is(AtmosItems.OXYGEN_TANK.get()), "the tank taken out is " + out);
            s.cycles(6, scrubber);
            h.assertTrue(co2(s) < room, "it didn't return to the room");

            // Half a step's charge scrubs half the tank's CO2; then an empty battery stops it.
            scrubber.tank().insertItem(0, tank(new GasParcel(Map.of(O2, 5.0, CO2, 2.0), T)), false);
            BatteryTests.swap(scrubber.battery(), BatteryTests.charged(AtmosItems.BATTERY.get(), STEP / 2));
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.WORKING, "the scrubber on its last charge");
            s.close(
                    GasTankItem.gas(scrubber.tank().tank()).moles().get(CO2),
                    1.0,
                    1e-9,
                    "the tank's CO2 at half a step");
            h.assertValueEqual(scrubber.energy(), 0, "the battery after its last step");
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.BATTERY_EMPTY, "the scrubber on an empty battery");
            s.close(GasTankItem.gas(scrubber.tank().tank()).moles().get(CO2), 1.0, 1e-9, "the tank's CO2 at empty");

            // A store at 50 atm stops it, drawing nothing.
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            double full = ScrubberMath.STALL_KPA * PortableGasDevice.STORE_LITRES / (GasMixture.GAS_CONSTANT * T);
            scrubber.store().set(new GasParcel(Map.of(CO2, full), T));
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.STORE_FULL, "the scrubber with a full store");
            h.assertValueEqual(scrubber.energy(), 100_000, "the battery with a full store");
            s.close(
                    GasTankItem.gas(scrubber.tank().tank()).moles().get(CO2),
                    1.0,
                    1e-9,
                    "the tank's CO2 at a full store");

            // In open air, a held tank still works: it needs no zone.
            PortableScrubberBlockEntity outside = scrubber(s, 8, 1, 2);
            BatteryTests.swap(outside.battery(), AtmosItems.BATTERY.get().full());
            outside.tank().insertItem(0, tank(new GasParcel(Map.of(O2, 5.0, CO2, 2.0), T)), false);
            outside.setOn(true);
            s.cycle(outside);
            h.assertValueEqual(outside.status(), PortableStatus.WORKING, "the scrubber on a tank in open air");
            s.close(outside.store().moles(CO2), 2.0, 1e-9, "the store's CO2 in open air");

            // Saved and loaded, it keeps its store, tank and battery.
            CompoundTag saved = outside.saveWithoutMetadata(s.level.registryAccess());
            PortableScrubberBlockEntity loaded =
                    new PortableScrubberBlockEntity(outside.getBlockPos(), outside.getBlockState());
            loaded.loadWithComponents(saved, s.level.registryAccess());
            s.close(loaded.store().moles(CO2), 2.0, 1e-9, "the loaded store's CO2");
            h.assertTrue(
                    loaded.tank().tank().is(AtmosItems.OXYGEN_TANK.get()),
                    "the loaded tank is " + loaded.tank().tank());
            h.assertValueEqual(loaded.energy(), outside.energy(), "the loaded battery");
            h.assertTrue(loaded.on(), "the loaded scrubber is off");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable scrubber in a controller's room runs on its own battery. The controller doesn't power
     * it, list it or pay for it, and a link to it doesn't hold (CONT-018, D-73).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_controller_leaves_a_portable_scrubber_in_its_room_alone(GameTestHelper h) {
        Site s = new Site(h, 85);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 3);
            BatteryTests.swap(scrubber.battery(), AtmosItems.BATTERY.get().full());
            scrubber.setOn(true);
            BatteryTests.formZone(s, 2, 2, 2);
            s.addKpa(2, 2, 2, CO2, 2);
            Object part = scrubber;
            h.assertFalse(part instanceof io.github.west3436.zas.atmos.power.PoweredDevice, "it is a part");
            untilWorking(s, scrubber, c);
            c.toggleLink(scrubber.getBlockPos());
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            int before = scrubber.energy();
            int buffer = c.buffer().getEnergyStored();
            s.cycle(c, scrubber);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(scrubber.energy(), before - STEP, "the battery after a step");
            h.assertTrue(buffer - c.buffer().getEnergyStored() <= own, "the buffer paid for the scrubber");
            h.assertTrue(
                    c.parts().stream().noneMatch(p -> p.pos().equals(scrubber.getBlockPos())),
                    "the controller lists the scrubber");
            h.assertFalse(c.links().contains(scrubber.getBlockPos()), "the link to the scrubber held");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    /**
     * A portable scrubber's item keeps its store's gas, as a canister's does: mined and placed again it
     * holds the same gas, and so it does when dropped before removal; broken in creative its gas is
     * destroyed; and removed with no drop its gas is released into its room (CONT-018, CONT-017).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_portable_scrubber_keeps_its_gas_as_a_canister_does(GameTestHelper h) {
        Site s = new Site(h, 86);
        try {
            BatteryTests.clear(s);
            s.room(0, 4);
            GasParcel gas = new GasParcel(Map.of(CO2, 12.5), T);

            // Mined by a player with a pickaxe: the block is removed before the drops are made.
            PortableScrubberBlockEntity mined = scrubber(s, 2, 1, 2);
            mined.store().add(gas);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos pos = s.abs(2, 1, 2);
            BlockState state = s.level.getBlockState(pos);
            BlockEntity be = s.level.getBlockEntity(pos);
            state.getBlock().playerWillDestroy(s.level, pos, state, player);
            s.level.removeBlock(pos, false);
            ItemStack item = scrubberIn(Block.getDrops(state, s.level, pos, be, player, player.getMainHandItem()));
            h.assertTrue(
                    gas.equals(item.get(AtmosComponents.GAS.get())),
                    "the item " + item + " carries " + item.get(AtmosComponents.GAS.get()));

            // Placed again, it holds the gas.
            PortableScrubberBlockEntity again = scrubber(s, 2, 1, 2);
            again.applyComponentsFromItemStack(item);
            s.close(again.store().moles(CO2), 12.5, 1e-9, "the CO2 after placing it again");

            // Dropped before removal, as by destroyBlock or an explosion: the same, and nothing released.
            state = s.level.getBlockState(pos);
            ItemStack second = scrubberIn(Block.getDrops(state, s.level, pos, again));
            s.level.removeBlock(pos, false);
            h.assertTrue(gas.equals(second.get(AtmosComponents.GAS.get())), "drops before removal lost the gas");
            s.close(
                    again.store().totalMoles(),
                    gas.totalMoles(),
                    1e-9,
                    "the removed scrubber kept its gas for the item");

            // An empty one's item has no gas.
            PortableScrubberBlockEntity empty = scrubber(s, 2, 1, 2);
            state = s.level.getBlockState(pos);
            h.assertFalse(
                    scrubberIn(Block.getDrops(state, s.level, pos, empty)).has(AtmosComponents.GAS.get()),
                    "an empty scrubber's item carries gas");
            s.level.removeBlock(pos, false);

            // Broken in creative: destroyed, not released into the room.
            PortableScrubberBlockEntity creative = scrubber(s, 2, 1, 2);
            BatteryTests.formZone(s, 2, 2, 2);
            s.removeGas(2, 2, 2, CO2);
            creative.store().add(gas);
            Player builder = h.makeMockPlayer(GameType.CREATIVE);
            state = s.level.getBlockState(pos);
            state.getBlock().playerWillDestroy(s.level, pos, state, builder);
            s.level.removeBlock(pos, false);
            s.ticks(3);
            s.close(creative.store().totalMoles(), 0, 1e-9, "a scrubber broken in creative kept its gas");
            s.close(co2(s), 0, 1e-9, "the room's CO2 after a scrubber was broken in creative");

            // Removed with no drop: released into the room.
            PortableScrubberBlockEntity removed = scrubber(s, 2, 1, 2);
            removed.store().add(gas);
            s.level.removeBlock(pos, false);
            s.ticks(3);
            s.close(co2(s), 12.5, 1e-6, "the room's CO2 after a scrubber was removed");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }

    private static ItemStack scrubberIn(java.util.List<ItemStack> drops) {
        return drops.stream()
                .filter(d -> d.is(AtmosItems.PORTABLE_SCRUBBER.get()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    /** Its store and a held tank react each atmos tick above autoignition, on or off (CONT-100). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_018_a_portable_scrubbers_store_and_tank_react(GameTestHelper h) {
        Site s = new Site(h, 87);
        try {
            BatteryTests.clear(s);
            PortableScrubberBlockEntity scrubber = scrubber(s, 2, 1, 2);
            GasParcel flammable = new GasParcel(Map.of(H2, 5.0, O2, 10.0, N2, 15.0), 900);
            scrubber.store().add(flammable);
            scrubber.tank().insertItem(0, tank(flammable), false);
            s.cycle(scrubber);
            h.assertValueEqual(scrubber.status(), PortableStatus.OFF, "the scrubber's status");
            h.assertTrue(scrubber.store().moles(H2) < 5.0, "the store's hydrogen didn't burn");
            h.assertTrue(
                    GasTankItem.gas(scrubber.tank().tank()).moles().getOrDefault(H2, 0.0) < 5.0,
                    "the tank's hydrogen didn't burn");
            h.succeed();
        } finally {
            BatteryTests.clear(s);
        }
    }
}
