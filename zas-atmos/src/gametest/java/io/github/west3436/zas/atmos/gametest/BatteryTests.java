package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterMath;
import io.github.west3436.zas.atmos.heater.ThermoStatus;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The batteries (CONT-111), the battery tag (CONT-112) and the recharger (CONT-113), and the heater
 * outside its controller's power (D-73). Each test builds in its own {@link Site} within one game
 * tick, in a batch of its own so other tests' devices never meet these.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatteryTests {
    private static final String BATCH = "zas_atmos_battery";

    private BatteryTests() {}

    /** A stack of an FE item holding {@code fe}. */
    static ItemStack charged(Item item, int fe) {
        ItemStack stack = new ItemStack(item);
        stack.set(AtmosComponents.ENERGY.get(), fe);
        return stack;
    }

    static int energy(ItemStack stack) {
        IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return e == null ? -1 : e.getEnergyStored();
    }

    /** Swaps the battery in a slot, returning the one taken out. */
    static ItemStack swap(Batteries.Slot slot, ItemStack battery) {
        ItemStack old = slot.extractItem(0, 1, false);
        ItemStack left = slot.insertItem(0, battery, false);
        if (!left.isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("the slot refused " + battery);
        }
        return old;
    }

    /** Forms a zone of nitrogen in a sealed room by adding a mole at a point inside it. */
    static void formZone(Site s, int x, int y, int z) {
        s.settle(GasMutations.add(
                s.level, s.point(x, y, z), new GasParcel(Map.of(StandardAir.NITROGEN, 1.0), 293.15), Site.SOURCE));
    }

    /** Clears a site and the items its devices dropped as they were broken. */
    static void clear(Site s) {
        s.clear();
        s.level
                .getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(s.abs(-2, -2, -2).getCenter(), s.abs(18, 8, 8).getCenter()))
                .forEach(Entity::discard);
    }

    private static HeaterBlockEntity heater(Site s, int x, int y, int z) {
        BlockState state = AtmosBlocks.HEATER.get().defaultBlockState().setValue(HeaterBlock.FACING, Direction.NORTH);
        return s.place(x, y, z, state, HeaterBlockEntity.class);
    }

    private static RechargerBlockEntity recharger(Site s, int x, int y, int z) {
        BlockState state =
                AtmosBlocks.RECHARGER.get().defaultBlockState().setValue(RechargerBlock.FACING, Direction.NORTH);
        return s.place(x, y, z, state, RechargerBlockEntity.class);
    }

    /**
     * A right-click on it, in the order the game asks a block. {@code GameTestHelper.useBlock} takes a
     * relative position, which doesn't lead back to a block in a {@link Site}.
     */
    private static void rightClick(Site s, RechargerBlockEntity r, Player player) {
        BlockPos at = r.getBlockPos();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, true);
        BlockState state = s.level.getBlockState(at);
        ItemInteractionResult result =
                state.useItemOn(player.getMainHandItem(), s.level, player, InteractionHand.MAIN_HAND, hit);
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            state.useWithoutItem(s.level, player, hit);
        }
    }

    // ---- CONT-111 -------------------------------------------------------------------------

    /**
     * Each battery fills through the FE item capability to its capacity, 100,000, 1,000,000 and
     * 2,000,000 FE, and no further; it gives FE back the same way; and the creative tab's full copy
     * holds its capacity (CONT-111).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_111_batteries_fill_to_their_capacity(GameTestHelper h) {
        List<Item> batteries = List.of(
                AtmosItems.BATTERY.get(),
                AtmosItems.HIGH_CAPACITY_BATTERY.get(),
                AtmosItems.SUPER_CAPACITY_BATTERY.get());
        int[] capacities = {100_000, 1_000_000, 2_000_000};
        for (int i = 0; i < batteries.size(); i++) {
            Item item = batteries.get(i);
            ItemStack stack = new ItemStack(item);
            h.assertTrue(Batteries.isBattery(stack), item + " isn't a battery");
            h.assertTrue(stack.is(Batteries.TAG), item + " isn't in the tag");
            h.assertValueEqual(stack.getMaxStackSize(), 1, item + "'s stack size");
            IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(e != null, item + " has no FE item capability");
            h.assertValueEqual(e.getMaxEnergyStored(), capacities[i], item + "'s capacity");
            h.assertValueEqual(e.getEnergyStored(), 0, item + " new");
            long total = 0;
            for (int n = 0; n < 1000; n++) {
                int got = e.receiveEnergy(30_000, false);
                if (got == 0) {
                    break;
                }
                total += got;
            }
            h.assertValueEqual(total, (long) capacities[i], item + ": FE taken in");
            h.assertValueEqual(energy(stack), capacities[i], item + " filled");
            h.assertValueEqual(BatteryItem.energy(stack), capacities[i], item + "'s component");
            h.assertValueEqual(e.receiveEnergy(1, false), 0, item + " full, taking more");
            h.assertValueEqual(e.extractEnergy(1000, false), 1000, item + " giving FE");
            h.assertValueEqual(energy(stack), capacities[i] - 1000, item + " after giving 1,000 FE");
            h.assertValueEqual(energy(((BatteryItem) item).full()), capacities[i], item + "'s full copy");
        }
        h.succeed();
    }

    // ---- CONT-112 -------------------------------------------------------------------------

    /**
     * Another mod's FE item, added to {@code zas_atmos:batteries} by a data pack, runs a heater and
     * charges in a recharger; an FE item outside the tag, and an item with no FE, are refused by
     * both slots (CONT-112).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_112_the_tag_decides_what_is_a_battery(GameTestHelper h) {
        Site s = new Site(h, 15);
        try {
            clear(s);
            ItemStack tagged = charged(TestParts.feItem(), TestParts.FE_ITEM_CAPACITY);
            ItemStack untagged = charged(TestParts.untaggedFeItem(), TestParts.FE_ITEM_CAPACITY);
            h.assertTrue(
                    untagged.getCapability(Capabilities.EnergyStorage.ITEM) != null, "the untagged item has no FE");
            h.assertTrue(Batteries.isBattery(tagged), "the test pack's FE item isn't a battery");
            h.assertFalse(Batteries.isBattery(untagged), "an FE item outside the tag is a battery");
            h.assertFalse(Batteries.isBattery(new ItemStack(Items.COAL)), "coal is a battery");

            s.room(0, 4);
            formZone(s, 2, 2, 2);
            HeaterBlockEntity heater = heater(s, 2, 1, 2);
            RechargerBlockEntity recharger = recharger(s, 8, 1, 2);
            for (Batteries.Slot slot : List.of(heater.battery(), recharger.battery())) {
                h.assertFalse(slot.isItemValid(0, untagged), "a slot takes an FE item outside the tag");
                h.assertValueEqual(
                        slot.insertItem(0, untagged.copy(), false).getCount(), 1, "the untagged item went in");
                h.assertValueEqual(
                        slot.insertItem(0, new ItemStack(Items.COAL), false).getCount(), 1, "coal went in");
                h.assertTrue(slot.battery().isEmpty(), "a slot holds a refused item");
            }

            // The heater runs from the tagged item at full power: 60 FE/t over the atmos tick.
            swap(heater.battery(), tagged.copy());
            heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            heater.setOn(true);
            s.cycle(heater);
            h.assertValueEqual(heater.status(), ThermoStatus.HEATING, "the heater on the test FE item");
            h.assertValueEqual(
                    heater.energy(),
                    TestParts.FE_ITEM_CAPACITY - 60 * AtmosTicks.PERIOD,
                    "the test FE item after a step of heating");

            // The recharger charges an empty one: 28 FE/t over the atmos tick.
            swap(recharger.battery(), charged(TestParts.feItem(), 0));
            recharger.power().setTestPowered(true);
            s.cycle(recharger);
            h.assertValueEqual(recharger.status(), RechargerBlockEntity.Status.CHARGING, "the recharger");
            h.assertValueEqual(recharger.energy(), 28 * AtmosTicks.PERIOD, "the test FE item after a step of charging");
            h.succeed();
        } finally {
            clear(s);
        }
    }

    // ---- CONT-113 -------------------------------------------------------------------------

    /**
     * A recharger in a controller's room, in the last group, draws 40 FE/t from the controller's
     * buffer while its battery isn't full and puts 28 FE/t into it; full, it stops drawing; in a
     * room whose controller has no power, it charges nothing. Its block shows the battery and its
     * gauge, and broken it drops its battery (CONT-064, CONT-068, CONT-113).
     *
     * <p>The test runs within one game tick, so a lease, which counts game ticks, lasts all of it,
     * and a controller pays for a part once: the buffer is checked on the step that pays.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_113_a_recharger_charges_at_28_drawing_40(GameTestHelper h) {
        Site s = new Site(h, 16);
        try {
            clear(s);
            s.room(0, 4);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            RechargerBlockEntity r = recharger(s, 2, 1, 3);
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.RECHARGER_DRAW), 40, "the configured draw");
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.RECHARGER_RATE), 28, "the configured rate");
            h.assertValueEqual(r.power().priority(), PowerPriority.AUXILIARY, "the recharger's group");

            // Empty: it draws nothing.
            s.cycle(r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.NO_BATTERY, "the empty recharger");
            h.assertValueEqual(r.power().draw(), 0, "the empty recharger's draw");
            h.assertFalse(s.level.getBlockState(r.getBlockPos()).getValue(RechargerBlock.BATTERY), "a battery shown");

            // An empty battery: 40 FE/t from the buffer, 28 FE/t into the battery.
            swap(r.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            BlockState shown = s.level.getBlockState(r.getBlockPos());
            h.assertTrue(shown.getValue(RechargerBlock.BATTERY), "no battery shown");
            h.assertValueEqual(shown.getValue(RechargerBlock.CHARGE), 0, "the empty battery's gauge");
            h.assertValueEqual(r.power().drawPerAtmosTick(), 40L * AtmosTicks.PERIOD, "the draw per atmos tick");
            for (int i = 0; i < 4 && !r.power().isPowered(); i++) {
                s.cycle(c, r);
            }
            h.assertTrue(r.power().source() == c, "the controller didn't power the recharger in its room");
            h.assertValueEqual(
                    c.buffer().getEnergyStored(),
                    10_000 - own - 40 * AtmosTicks.PERIOD,
                    "the buffer on the paying step");
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.CHARGING, "the recharger in a powered room");
            h.assertValueEqual(r.energy(), 28 * AtmosTicks.PERIOD, "FE into the battery on the paying step");
            int before = r.energy();
            s.cycle(c, r);
            h.assertValueEqual(r.energy() - before, 28 * AtmosTicks.PERIOD, "FE into the battery over an atmos tick");
            h.assertTrue(
                    c.parts().stream().anyMatch(p -> p.pos().equals(r.getBlockPos()) && p.powered()),
                    "the controller doesn't list the recharger as powered");
            h.assertValueEqual(
                    s.level.getBlockState(r.getBlockPos()).getValue(RechargerBlock.CHARGE),
                    1,
                    "the gauge, part charged");

            // Nearly full: it tops the battery up, then stops drawing.
            swap(r.battery(), charged(AtmosItems.BATTERY.get(), 100_000 - 100));
            s.cycle(c, r);
            h.assertValueEqual(r.energy(), 100_000, "the battery topped up");
            s.cycle(c, r);
            h.assertValueEqual(r.status(), RechargerBlockEntity.Status.FULL, "the recharger with a full battery");
            h.assertValueEqual(r.power().draw(), 0, "the draw with a full battery");
            s.cycle(c, r);
            h.assertValueEqual(r.energy(), 100_000, "the full battery");
            h.assertValueEqual(
                    s.level.getBlockState(r.getBlockPos()).getValue(RechargerBlock.CHARGE), 4, "the full gauge");

            // In a room whose controller has no FE, an empty battery waits.
            s.room(6, 10);
            AreaAirControllerBlockEntity d = s.controller(8);
            RechargerBlockEntity unpowered = recharger(s, 8, 1, 3);
            swap(unpowered.battery(), new ItemStack(AtmosItems.BATTERY.get()));
            s.cycles(4, d, unpowered);
            h.assertFalse(d.powered(), "the controller with no FE is powered");
            h.assertValueEqual(
                    unpowered.status(), RechargerBlockEntity.Status.UNPOWERED, "the recharger with no power");
            h.assertValueEqual(unpowered.energy(), 0, "the battery charged with no power");

            // Broken, it drops its battery.
            BlockPos pos = r.getBlockPos();
            s.set(2, 1, 3, Site.AIR);
            h.assertTrue(
                    !s.level
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(pos).inflate(1),
                                    e -> e.getItem().is(AtmosItems.BATTERY.get()))
                            .isEmpty(),
                    "the broken recharger dropped no battery");
            h.succeed();
        } finally {
            clear(s);
        }
    }

    /**
     * A recharger has no screen. A right-click with a battery puts it in, swapping with any it held,
     * and any other takes its battery out, to an empty hand or else the inventory. With none in it,
     * a click with anything else leaves it alone. The block shows the change at once (CONT-113).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_113_right_click_puts_a_battery_in_and_takes_it_out(GameTestHelper h) {
        Site s = new Site(h, 18);
        try {
            clear(s);
            RechargerBlockEntity r = recharger(s, 2, 1, 2);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);

            // Nothing to take, and a stick isn't a battery.
            rightClick(s, r, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            rightClick(s, r, player);
            h.assertTrue(r.battery().battery().isEmpty(), "a stick went in");
            h.assertTrue(player.getMainHandItem().is(Items.STICK), "the stick left the hand");

            // In: it leaves the hand, with its charge, and the block shows it.
            player.setItemInHand(InteractionHand.MAIN_HAND, charged(AtmosItems.BATTERY.get(), 100));
            rightClick(s, r, player);
            h.assertTrue(player.getMainHandItem().isEmpty(), "the battery stayed in hand");
            h.assertValueEqual(r.energy(), 100, "the battery's charge in the recharger");
            h.assertTrue(s.level.getBlockState(r.getBlockPos()).getValue(RechargerBlock.BATTERY), "no battery shown");

            // Out, to the empty hand.
            rightClick(s, r, player);
            h.assertTrue(player.getMainHandItem().is(AtmosItems.BATTERY.get()), "the battery didn't reach the hand");
            h.assertValueEqual(Batteries.energy(player.getMainHandItem()).getEnergyStored(), 100, "its charge");
            h.assertTrue(r.battery().battery().isEmpty(), "the battery stayed in");
            h.assertFalse(s.level.getBlockState(r.getBlockPos()).getValue(RechargerBlock.BATTERY), "a battery shown");

            // A second battery swaps with the first.
            rightClick(s, r, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, charged(AtmosItems.BATTERY.get(), 200));
            rightClick(s, r, player);
            h.assertValueEqual(r.energy(), 200, "the charge of the battery swapped in");
            h.assertValueEqual(
                    Batteries.energy(player.getMainHandItem()).getEnergyStored(), 100, "the one swapped out");

            // With a stick in hand, the battery goes to the inventory and the stick stays.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            rightClick(s, r, player);
            h.assertTrue(r.battery().battery().isEmpty(), "the battery stayed in, with a stick in hand");
            h.assertTrue(player.getMainHandItem().is(Items.STICK), "the stick left the hand");
            h.assertTrue(
                    player.getInventory().items.stream().anyMatch(i -> i.is(AtmosItems.BATTERY.get())),
                    "no battery in the inventory");
            h.succeed();
        } finally {
            clear(s);
        }
    }

    // ---- CONT-019, D-73 -------------------------------------------------------------------

    /**
     * A heater in a controller's room runs on its own battery. The controller doesn't power it,
     * list it or pay for it, and a link to it doesn't hold (CONT-019, CONT-087, D-73).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_019_a_controller_leaves_a_heater_in_its_room_alone(GameTestHelper h) {
        Site s = new Site(h, 17);
        try {
            clear(s);
            s.room(0, 4);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            HeaterBlockEntity heater = heater(s, 2, 1, 3);
            swap(heater.battery(), ((BatteryItem) AtmosItems.BATTERY.get()).full());
            heater.setSetpoint(HeaterMath.MAX_SETPOINT);
            heater.setOn(true);
            Object part = heater;
            h.assertFalse(part instanceof io.github.west3436.zas.atmos.power.PoweredDevice, "the heater is a part");
            int own = AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
            for (int i = 0; i < 4 && heater.status() != ThermoStatus.HEATING; i++) {
                s.cycle(c, heater);
            }
            h.assertValueEqual(heater.status(), ThermoStatus.HEATING, "the heater in the controller's room");
            c.toggleLink(heater.getBlockPos());
            int before = heater.energy();
            s.cycle(c, heater);
            h.assertTrue(c.powered(), "the controller is unpowered");
            h.assertValueEqual(heater.energy(), before - 60 * AtmosTicks.PERIOD, "the heater's battery after a step");
            h.assertValueEqual(c.buffer().getEnergyStored(), 10_000 - own, "the buffer paid for the heater");
            h.assertTrue(
                    c.parts().stream().noneMatch(p -> p.pos().equals(heater.getBlockPos())),
                    "the controller lists the heater");
            h.assertFalse(c.links().contains(heater.getBlockPos()), "the link to the heater held");
            h.succeed();
        } finally {
            clear(s);
        }
    }
}
