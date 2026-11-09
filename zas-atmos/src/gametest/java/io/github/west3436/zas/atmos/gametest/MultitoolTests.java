package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The multitool through the game's own click path: a player's right click on a block as the server
 * handles a client's, so the block's own use gets its turn as it would in play (CONT-042, CONT-043,
 * CONT-137, CONT-138, CONT-141). A plain click selects a controller and links a part; with nothing to
 * link the block gets the click, as from an empty hand; a sneak-click keeps doing what it did.
 *
 * <p>The player is a server player whose packets are kept, so a test reads what it was told. Each
 * test runs within one game tick, as {@link AirlockTests} does, and the airlock ones build its
 * {@link AirlockTests#lock}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultitoolTests {
    private static final String BATCH = "zas_atmos_multitool";

    private MultitoolTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** A player holding a multitool, and what it was sent. */
    private record Hand(ServerPlayer player, List<Packet<?>> sent) {
        /** A player standing at (x, y, z) of a site, holding a new multitool. */
        static Hand at(Site s, int x, int y, int z) {
            ServerPlayer p = new ServerPlayer(
                    s.level.getServer(),
                    s.level,
                    new GameProfile(UUID.randomUUID(), "zas_multitool"),
                    ClientInformation.createDefault());
            List<Packet<?>> sent = new ArrayList<>();
            new ListeningPlayer.Recording(s.level, p, sent);
            p.moveTo(Vec3.atBottomCenterOf(s.abs(x, y, z)));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.MULTITOOL.get()));
            return new Hand(p, sent);
        }

        /** A right click on the block at (x, y, z), through the server's handling of a client's click. */
        InteractionResult click(Site s, int x, int y, int z) {
            return click(s.abs(x, y, z));
        }

        InteractionResult click(BlockPos pos) {
            return player.gameMode.useItemOn(
                    player,
                    player.serverLevel(),
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        }

        /** The same while sneaking. */
        InteractionResult sneakClick(Site s, int x, int y, int z) {
            player.setShiftKeyDown(true);
            try {
                return click(s, x, y, z);
            } finally {
                player.setShiftKeyDown(false);
            }
        }

        /** The controller its multitool has selected, or null. */
        BlockPos selected() {
            return MultitoolItem.selected(player.getMainHandItem(), player.serverLevel())
                    .orElse(null);
        }

        /** The key of the last thing it was told, or "" if nothing. */
        String told() {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof ClientboundSystemChatPacket chat) {
                    return chat.content().getContents() instanceof TranslatableContents t
                            ? t.getKey()
                            : chat.content().getString();
                }
            }
            return "";
        }

        /** Whether a window of its own is open, rather than the inventory. */
        boolean windowOpen() {
            return player.containerMenu != player.inventoryMenu;
        }

        /** Presses a button of the open window, as the client's packet does. */
        void press(int button) {
            player.connection.handleContainerButtonClick(
                    new ServerboundContainerButtonClickPacket(player.containerMenu.containerId, button));
        }
    }

    /** Runs a one-tick test's checks, then clears the site even if they fail. */
    private static void run(GameTestHelper h, Site s, Runnable checks) {
        try {
            s.clear();
            checks.run();
        } finally {
            BatteryTests.clear(s);
        }
        h.succeed();
    }

    /** A stone floor under x 0–14, z 0–4, and a wall two high along z = 0 for panels to hang on. */
    private static void floorAndWall(Site s) {
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 4; z++) {
                s.set(x, 0, z, Site.STONE);
            }
            s.set(x, 1, 0, Site.STONE);
            s.set(x, 2, 0, Site.STONE);
        }
    }

    /** Whether the door, firelock or airlock at a position is open: they share the property. */
    private static boolean open(Site s, BlockPos pos) {
        return s.level.getBlockState(pos).getValue(BlockStateProperties.OPEN);
    }

    // ---- CONT-042, CONT-068: an area air controller and its parts ---------------------------------

    /**
     * A plain click selects an area air controller, and then links each kind of powered part to it:
     * a vent, a scrubber, both pumps, a filter, a mixer, an electrolyzer, a recharger, an igniter and
     * a fire alarm. None of them opens its window or takes the click, and the recharger keeps its
     * battery. Clicking a linked part again unlinks it (CONT-042, CONT-068).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_042_a_plain_click_selects_an_area_air_controller_and_links_each_powered_part(
            GameTestHelper h) {
        Site s = new Site(h, 4400);
        run(h, s, () -> {
            floorAndWall(s);
            AreaAirControllerBlockEntity c = s.controller(1);
            List<Supplier<Block>> parts = List.of(
                    AtmosBlocks.VENT::get,
                    AtmosBlocks.SCRUBBER::get,
                    AtmosBlocks.PRESSURE_PUMP::get,
                    AtmosBlocks.VOLUME_PUMP::get,
                    AtmosBlocks.FILTER::get,
                    AtmosBlocks.MIXER::get,
                    AtmosBlocks.ELECTROLYZER::get,
                    AtmosBlocks.RECHARGER::get,
                    AtmosBlocks.IGNITER::get);
            List<BlockPos> placed = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                s.set(3 + i, 1, 3, parts.get(i).get().defaultBlockState());
                placed.add(s.abs(3 + i, 1, 3));
            }
            s.set(
                    13,
                    2,
                    1,
                    AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.SOUTH));
            placed.add(s.abs(13, 2, 1));
            RechargerBlockEntity recharger = s.entity(10, 1, 3, RechargerBlockEntity.class);
            recharger.battery().setStackInSlot(0, new ItemStack(AtmosItems.BATTERY.get()));
            h.assertTrue(
                    s.level.getBlockState(recharger.getBlockPos()).getValue(RechargerBlock.BATTERY),
                    "the recharger doesn't show its battery");

            Hand hand = Hand.at(s, 7, 1, 4);
            h.assertTrue(hand.click(s, 1, 2, 1).consumesAction(), "the click on the controller passed");
            h.assertValueEqual(hand.selected(), c.getBlockPos(), "the selection");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.selected", "what selecting says");
            h.assertFalse(hand.windowOpen(), "selecting opened the controller's window");

            for (BlockPos part : placed) {
                String what = s.level.getBlockState(part).getBlock().getDescriptionId();
                h.assertTrue(hand.click(part).consumesAction(), "the click on the " + what + " passed");
                h.assertTrue(c.links().contains(part), "a plain click didn't link the " + what);
                h.assertValueEqual(
                        hand.told(), "item.zas_atmos.multitool.linked", "what linking the " + what + " says");
                h.assertFalse(hand.windowOpen(), "the " + what + " opened its window");
            }
            h.assertValueEqual(c.links(), Set.copyOf(placed), "the controller's links");
            h.assertFalse(recharger.battery().battery().isEmpty(), "the click took the recharger's battery out");

            hand.click(placed.get(0));
            h.assertFalse(c.links().contains(placed.get(0)), "a second click didn't unlink the vent");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.unlinked", "what unlinking says");
        });
    }

    // ---- CONT-043, CONT-137: firelocks ---------------------------------------------------------

    /**
     * With an area air controller selected, a plain click on the upper half links a firelock on its
     * own, a door's and an airlock's, each by its lower half or its airlock's origin, and opens or
     * closes none of them (CONT-043, CONT-137).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_137_a_plain_click_links_a_firelock_on_its_own_on_a_door_and_on_an_airlock(
            GameTestHelper h) {
        Site s = new Site(h, 4401);
        run(h, s, () -> {
            floorAndWall(s);
            AreaAirControllerBlockEntity c = s.controller(1);
            BlockState firelock =
                    AtmosBlocks.FIRELOCK.get().defaultBlockState().setValue(FirelockBlock.FACING, Direction.EAST);
            s.set(4, 1, 3, firelock);
            s.set(4, 2, 3, firelock.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
            BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            s.set(7, 1, 3, oak);
            s.set(7, 2, 3, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            h.assertTrue(FirelockDoorBlock.fit(s.level, s.abs(7, 1, 3)), "the firelock didn't fit the door");
            AirlockTests.airlock(s, 10, 3, Direction.EAST, false);
            h.assertTrue(AirlockBlock.fitFirelock(s.level, s.abs(10, 1, 3)), "the firelock didn't fit the airlock");
            List<BlockPos> lowers = List.of(s.abs(4, 1, 3), s.abs(7, 1, 3), s.abs(10, 1, 3));
            List<Boolean> before = lowers.stream().map(p -> open(s, p)).toList();

            Hand hand = Hand.at(s, 7, 1, 1);
            hand.click(s, 1, 2, 1);
            h.assertValueEqual(hand.selected(), c.getBlockPos(), "the selection");
            for (BlockPos lower : lowers) {
                h.assertTrue(hand.click(lower.above()).consumesAction(), "the click on " + lower.above() + " passed");
                h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.linked", "what linking " + lower + " says");
            }
            h.assertValueEqual(c.links(), Set.copyOf(lowers), "the controller's links, by lower half or origin");
            h.assertValueEqual(lowers.stream().map(p -> open(s, p)).toList(), before, "open, before and after");
        });
    }

    // ---- CONT-138: an airlock controller's slots --------------------------------------------------

    /**
     * A plain click selects an airlock controller, and then links two airlocks, from either block,
     * three sensors, none of them pressed, and two vents, each airlock and sensor into the slot
     * guessed for it. Clicking the selected controller again opens its window, where a slot is
     * cleared and armed; the window closed, a plain click on a sensor fills that slot (CONT-138).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_138_plain_clicks_select_an_airlock_controller_and_fill_its_slots(GameTestHelper h) {
        Site s = new Site(h, 4402);
        run(h, s, () -> {
            AirlockTests.Lock lock = AirlockTests.lock(s, true, true);
            AirlockControllerBlockEntity ac = lock.controller();
            for (BlockPos p : lock.slotted()) {
                ac.toggleLink(s.level, p); // unlink what lock() linked
            }
            for (VentBlockEntity v : lock.vents()) {
                ac.toggleLink(s.level, v.getBlockPos());
            }
            for (Slot slot : Slot.values()) {
                h.assertTrue(ac.slot(slot) == null, slot.key() + " is still filled");
            }
            h.assertTrue(ac.linkedVents().isEmpty(), "a vent is still linked");

            Hand hand = Hand.at(s, 3, 1, 3);
            hand.click(s, 3, 2, 1);
            h.assertValueEqual(hand.selected(), ac.getBlockPos(), "the selection");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.selected_airlock", "what selecting says");
            h.assertFalse(hand.windowOpen(), "selecting opened the controller's window");
            hand.click(s, 4, 2, 2); // the inner airlock's upper block
            hand.click(s, 6, 1, 2);
            hand.click(s, 8, 2, 1);
            hand.click(s, 5, 2, 2);
            hand.click(s, 1, 2, 1);
            AirlockTests.assertSlots(h, lock, "linked by plain clicks");
            h.assertValueEqual(ac.cycling(), -1, "a sensor clicked to link it was pressed");
            hand.click(s, 5, 0, 2);
            hand.click(s, 5, 4, 2);
            h.assertValueEqual(
                    ac.linkedVents(), Set.of(s.abs(5, 0, 2), s.abs(5, 4, 2)), "the vents linked by plain clicks");
            h.assertFalse(hand.windowOpen(), "a part opened its window");

            hand.click(s, 3, 2, 1);
            h.assertTrue(
                    hand.player().containerMenu instanceof AirlockControllerMenu,
                    "clicking the selected controller again opened " + hand.player().containerMenu);
            h.assertValueEqual(hand.selected(), ac.getBlockPos(), "the selection, clicked again");
            hand.press(AirlockControllerMenu.CLEAR + Slot.CHAMBER_SENSOR.ordinal());
            hand.press(AirlockControllerMenu.ARM + Slot.CHAMBER_SENSOR.ordinal());
            h.assertTrue(ac.slot(Slot.CHAMBER_SENSOR) == null, "the window didn't clear the chamber sensor");
            h.assertValueEqual(ac.armed(), Slot.CHAMBER_SENSOR, "the slot the window armed");
            hand.player().closeContainer();

            hand.click(s, 5, 2, 2);
            h.assertValueEqual(ac.slot(Slot.CHAMBER_SENSOR), lock.sensor().getBlockPos(), "the armed slot, filled");
            h.assertTrue(ac.armed() == null, "filling the armed slot left it armed");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.linked_slot", "what filling a slot says");
            AirlockTests.assertSlots(h, lock, "the armed slot filled");
        });
    }

    // ---- CONT-141: an airlock controller as a part ------------------------------------------------

    /**
     * With an area air controller selected, a sneak-click links an airlock controller to it as a part,
     * and another unlinks it, as before; a plain click selects the airlock controller instead
     * (CONT-141).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_141_a_sneak_click_links_an_airlock_controller_and_a_plain_click_selects_it(
            GameTestHelper h) {
        Site s = new Site(h, 4403);
        run(h, s, () -> {
            AirlockTests.Lock lock = AirlockTests.lock(s, true, false);
            AreaAirControllerBlockEntity area = lock.area();
            BlockPos ac = lock.controller().getBlockPos();
            h.assertTrue(area.links().isEmpty(), "the area air controller has links");

            Hand hand = Hand.at(s, 2, 1, 3);
            hand.click(s, 2, 2, 1);
            h.assertValueEqual(hand.selected(), area.getBlockPos(), "the selection");
            hand.sneakClick(s, 3, 2, 1);
            h.assertValueEqual(area.links(), Set.of(ac), "the area air controller's links, sneaking");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.linked", "what linking it says");
            h.assertValueEqual(hand.selected(), area.getBlockPos(), "the selection, sneaking");
            hand.sneakClick(s, 3, 2, 1);
            h.assertTrue(area.links().isEmpty(), "a second sneak-click didn't unlink it");

            hand.click(s, 3, 2, 1);
            h.assertValueEqual(hand.selected(), ac, "the selection after a plain click");
            h.assertTrue(area.links().isEmpty(), "a plain click linked it");
            h.assertFalse(hand.windowOpen(), "a plain click opened its window");
        });
    }

    // ---- CONT-042: nothing to link ------------------------------------------------------------

    /**
     * Where the multitool has nothing to do, a plain click goes to the block as from an empty hand.
     * With nothing selected, a vent opens its window, a sensor is pressed and an airlock opens. With
     * an area air controller selected, which links neither a sensor nor an airlock with no firelock,
     * the sensor is pressed and the airlock closes, and the controller itself, clicked again, opens
     * its window. A sneak-click still says why it has nothing to do (CONT-042, CONT-138).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_042_with_nothing_to_link_a_plain_click_uses_the_block_as_an_empty_hand_does(
            GameTestHelper h) {
        Site s = new Site(h, 4404);
        run(h, s, () -> {
            AirlockTests.Lock lock = AirlockTests.lock(s, true, false);
            AirlockControllerBlockEntity ac = lock.controller();
            AreaAirControllerBlockEntity area = lock.area();
            AirlockTests.airlock(s, 10, 2, Direction.EAST, false);
            BlockPos third = s.abs(10, 1, 2);
            BlockPos vent = s.abs(5, 0, 2);

            Hand hand = Hand.at(s, 3, 1, 3);
            hand.click(vent);
            h.assertTrue(
                    hand.player().containerMenu instanceof VentMenu,
                    "the vent, nothing selected, opened " + hand.player().containerMenu);
            hand.player().closeContainer();
            hand.sneakClick(s, 5, 0, 2);
            h.assertFalse(hand.windowOpen(), "a sneak-click opened the vent's window");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.no_controller", "what a sneak-click says");
            h.assertTrue(ac.linkedVents().contains(vent), "a click with nothing selected unlinked the vent");

            hand.click(s, 1, 2, 1);
            h.assertTrue(ac.cycling() != -1, "A's sensor, nothing selected, wasn't pressed");
            h.assertTrue(ac.stop(), "stop");
            hand.click(third.above());
            h.assertTrue(open(s, third), "the airlock, nothing selected, didn't open");

            hand.click(s, 2, 2, 1);
            h.assertValueEqual(hand.selected(), area.getBlockPos(), "the selection");
            hand.click(s, 8, 2, 1);
            h.assertTrue(ac.cycling() != -1, "B's sensor, with an area air controller selected, wasn't pressed");
            h.assertTrue(ac.stop(), "stop");
            hand.click(third);
            h.assertFalse(open(s, third), "the airlock with no firelock didn't close");
            hand.sneakClick(s, 10, 1, 2);
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.no_firelock", "a sneak-click on it");
            h.assertFalse(open(s, third), "a sneak-click opened the airlock");
            h.assertTrue(area.links().isEmpty(), "the area air controller linked something");

            hand.click(s, 2, 2, 1);
            h.assertTrue(
                    hand.player().containerMenu instanceof ControllerMenu,
                    "the selected controller, clicked again, opened " + hand.player().containerMenu);
            h.assertValueEqual(hand.selected(), area.getBlockPos(), "the selection, clicked again");
            hand.player().closeContainer();
            hand.sneakClick(s, 2, 2, 1);
            h.assertFalse(hand.windowOpen(), "a sneak-click on the selected controller opened its window");
            h.assertValueEqual(hand.told(), "item.zas_atmos.multitool.selected", "a sneak-click selects it again");
        });
    }
}
