package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.label.LabelPayload;
import io.github.west3436.zas.atmos.label.Labelled;
import io.github.west3436.zas.atmos.label.LabelledMenu;
import io.github.west3436.zas.atmos.label.Labels;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMenu;
import io.github.west3436.zas.gametest.TestPlayers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

/**
 * Labels: the name a player gives a device a controller links or lists, so several of one kind can be
 * told apart. A label comes from the item that placed the block, from a name tag and from the title
 * bar of the device's own window; it is plain text of at most 32 characters, which the server cleans
 * whoever sent it; and it shows in the area air controller's and the central controller's lists, in
 * what the multitool says and in a window's title.
 *
 * <p>The player is a server player whose packets are kept, and every click goes through the server's
 * handling of a client's, so the name tag meets the block's own use as it would in play. Each test
 * runs within one game tick.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabelTests {
    private static final String BATCH = "zas_atmos_labels";

    /** Every block that takes a label, written out: one added later is a choice to make here. */
    private static final Set<String> LABELLED = Set.of(
            "vent",
            "scrubber",
            "pressure_pump",
            "volume_pump",
            "filter",
            "mixer",
            "electrolyzer",
            "igniter",
            "fire_alarm",
            "recharger",
            "air_sensor",
            "airlock",
            "large_airlock",
            "firelock",
            "firelock_door",
            "airlock_controller",
            "area_air_controller",
            "central_atmos_controller");

    private LabelTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** A player, what it holds, and what it was sent. */
    private record Who(ServerPlayer player, List<Packet<?>> sent) {
        /** A survival player standing at (x, y, z) of a site, looking north. */
        static Who at(Site s, int x, int y, int z) {
            ServerPlayer p = new ServerPlayer(
                    s.level.getServer(),
                    s.level,
                    new GameProfile(UUID.randomUUID(), "zas_labels"),
                    ClientInformation.createDefault());
            List<Packet<?>> sent = new ArrayList<>();
            new ListeningPlayer.Recording(s.level, p, sent);
            p.moveTo(Vec3.atBottomCenterOf(s.abs(x, y, z)));
            p.setYRot(180);
            p.setXRot(0);
            return new Who(p, sent);
        }

        void hold(ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }

        ItemStack held() {
            return player.getMainHandItem();
        }

        /** A right click on a face of a block, through the server's handling of a client's click. */
        InteractionResult click(BlockPos pos, Direction face) {
            Vec3 at = Vec3.atCenterOf(pos).relative(face, 0.5);
            return player.gameMode.useItemOn(
                    player,
                    player.serverLevel(),
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(at, face, pos, false));
        }

        InteractionResult click(BlockPos pos) {
            return click(pos, Direction.UP);
        }

        /** The last thing it was told, as {@link LabelTests#shown} writes it, or "" if nothing. */
        String told() {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof ClientboundSystemChatPacket chat) {
                    return shown(chat.content());
                }
            }
            return "";
        }

        /** The title of the last window it was sent. */
        Component title() {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof ClientboundOpenScreenPacket open) {
                    return open.getTitle();
                }
                // A window that is sent more than its title when it opens goes as NeoForge's own packet.
                if (sent.get(i) instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof AdvancedOpenScreenPayload open) {
                    return open.name();
                }
            }
            throw new GameTestAssertException("no window was opened");
        }

        boolean windowOpen() {
            return player.containerMenu != player.inventoryMenu;
        }

        void closeWindow() {
            player.doCloseContainer();
        }

        /** Sends a label for the window it has open, as the client's packet does. */
        void type(String text) {
            LabelPayload.handle(new LabelPayload(player.containerMenu.containerId, text), TestPlayers.sentBy(player));
        }
    }

    /**
     * A message or one of its arguments as a test compares it: a translation as its key and its
     * arguments in brackets, anything else as its text. The server has no language to render with.
     */
    private static String shown(Object part) {
        if (!(part instanceof Component c)) {
            return String.valueOf(part);
        }
        if (!(c.getContents() instanceof TranslatableContents t)) {
            return c.getString();
        }
        List<String> args = new ArrayList<>();
        for (Object arg : t.getArgs()) {
            args.add(shown(arg));
        }
        return t.getKey() + args;
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    /** Runs a one-tick test's checks on a cleared site, then clears it even if they fail. */
    private static void run(GameTestHelper h, Site s, Runnable checks) {
        try {
            clear(s);
            checks.run();
        } finally {
            clear(s);
        }
        h.succeed();
    }

    /** Clears a site, each console first by its lower left block, which takes the rest with no drop. */
    private static void clear(Site s) {
        for (BlockPos p : BlockPos.betweenClosed(s.abs(0, 0, 0), s.abs(16, 6, 6))) {
            BlockState state = s.level.getBlockState(p);
            if (state.getBlock() instanceof CentralControllerBlock
                    && CentralControllerBlock.origin(state, p).equals(p)) {
                s.level.setBlockAndUpdate(p.immutable(), Site.AIR);
            }
        }
        BatteryTests.clear(s);
    }

    /** A stone floor under x 0–16, z 0–6, and a wall two high along z = 0 for panels to hang on. */
    private static void floorAndWall(Site s) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 6; z++) {
                s.set(x, 0, z, Site.STONE);
            }
            s.set(x, 1, 0, Site.STONE);
            s.set(x, 2, 0, Site.STONE);
        }
    }

    private static VentBlockEntity vent(Site s, int x, int z) {
        return s.place(x, 1, z, AtmosBlocks.VENT.get().defaultBlockState(), VentBlockEntity.class);
    }

    /** A whole console facing north, its lower left block at a point of a site and the rest west of it and above. */
    private static CentralControllerBlockEntity central(Site s, int x, int y, int z) {
        BlockState state = AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get().defaultBlockState();
        List<BlockPos> parts = CentralControllerBlock.parts(s.abs(x, y, z), Direction.NORTH);
        for (int i = 0; i < parts.size(); i++) {
            s.level.setBlockAndUpdate(parts.get(i), CentralControllerBlock.part(state, i));
        }
        return s.entity(x, y, z, CentralControllerBlockEntity.class);
    }

    private static String label(Site s, BlockPos pos) {
        return Labels.of(s.level, pos);
    }

    // ---- Which devices take one ---------------------------------------------------------------------

    /**
     * The blocks that take a label are those a controller links or lists, and the controllers
     * themselves: every powered part and every firelock, which an area air controller links; the
     * airlock and the air sensor, which an airlock controller links; and the three controllers, which
     * the multitool selects and a central controller lists. No other block of the mod takes one.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void the_devices_a_controller_links_or_lists_take_a_label_and_no_others(GameTestHelper h) {
        Set<String> labelled = new TreeSet<>();
        Set<String> linked = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (!id.getNamespace().equals(ZasAtmosMod.MOD_ID)
                    || id.getPath().startsWith("test_")
                    || !(block instanceof EntityBlock entities)) {
                continue;
            }
            // A block of several parts has its block entity in one of them.
            BlockEntity be = null;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                be = entities.newBlockEntity(BlockPos.ZERO, state);
                if (be != null) {
                    break;
                }
            }
            if (be instanceof Labelled) {
                labelled.add(id.getPath());
                h.assertTrue(be instanceof AtmosDevice, id + " takes a label but isn't a device that keeps one");
            }
            if (be instanceof PoweredDevice
                    || be instanceof FirelockLogic.Holder
                    || be instanceof AirSensorBlockEntity
                    || be instanceof AirlockControllerBlockEntity
                    || be instanceof AreaAirControllerBlockEntity
                    || be instanceof CentralControllerBlockEntity) {
                linked.add(id.getPath());
            }
        }
        h.assertValueEqual(labelled, new TreeSet<>(LABELLED), "the blocks that take a label");
        h.assertValueEqual(labelled, linked, "the blocks a controller links or lists");
        h.succeed();
    }

    // ---- Set by the item that places it -------------------------------------------------------------

    /**
     * Each item that places a device that takes a label, renamed in an anvil, gives the block it
     * places its name as a label, readable from every block of a device of several; the same item
     * with no name places it with none. An item of a device that takes no label places it as before.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_renamed_item_labels_the_block_it_places(GameTestHelper h) {
        Site s = new Site(h, 4890);
        run(h, s, () -> {
            List<Item> items = List.of(
                    AtmosItems.VENT.get(),
                    AtmosItems.SCRUBBER.get(),
                    AtmosItems.PRESSURE_PUMP.get(),
                    AtmosItems.VOLUME_PUMP.get(),
                    AtmosItems.FILTER.get(),
                    AtmosItems.FILTER_MIRRORED.get(),
                    AtmosItems.MIXER.get(),
                    AtmosItems.MIXER_MIRRORED.get(),
                    AtmosItems.ELECTROLYZER.get(),
                    AtmosItems.IGNITER.get(),
                    AtmosItems.FIRE_ALARM.get(),
                    AtmosItems.RECHARGER.get(),
                    AtmosItems.AIR_SENSOR.get(),
                    AtmosItems.AIRLOCK.get(),
                    AtmosItems.LARGE_AIRLOCK.get(),
                    AtmosItems.FIRELOCK.get(),
                    AtmosItems.AIRLOCK_CONTROLLER.get(),
                    AtmosItems.AREA_AIR_CONTROLLER.get(),
                    AtmosItems.CENTRAL_ATMOS_CONTROLLER.get());
            Set<String> placedBlocks = new TreeSet<>();
            for (Item item : items) {
                for (String name : List.of("Deck 2 north", "")) {
                    clear(s);
                    floorAndWall(s);
                    Who who = Who.at(s, 8, 1, 6);
                    who.hold(name.isEmpty() ? new ItemStack(item) : named(item, name));
                    // On the floor, or for a panel, on the wall.
                    BlockPos placed = s.abs(8, 1, 3);
                    if (!who.click(s.abs(8, 0, 3), Direction.UP).consumesAction()) {
                        placed = s.abs(8, 2, 1);
                        h.assertTrue(
                                who.click(s.abs(8, 2, 0), Direction.SOUTH).consumesAction(),
                                item + " was placed neither on the floor nor on the wall");
                    }
                    Block block = s.level.getBlockState(placed).getBlock();
                    h.assertTrue(block.asItem() == item || block == Block.byItem(item), item + " placed " + block);
                    placedBlocks.add(BuiltInRegistries.BLOCK.getKey(block).getPath());
                    Labelled device = Labels.at(s.level, placed);
                    h.assertTrue(device != null, "the " + block + " takes no label");
                    h.assertValueEqual(device.label(), name, "the label of the " + block + " placed by " + item);
                    int blocks = 0;
                    for (BlockPos p : BlockPos.betweenClosed(placed.offset(-2, -1, -2), placed.offset(2, 2, 2))) {
                        if (s.level.getBlockState(p).is(block)) {
                            blocks++;
                            h.assertValueEqual(label(s, p), name, "the label read from " + p + " of the " + block);
                        }
                    }
                    h.assertTrue(blocks >= 1, "no block of the " + block);
                }
            }
            // Every block that takes a label has an item here but the door a firelock is fitted to.
            Set<String> expected = new TreeSet<>(LABELLED);
            expected.remove("firelock_door");
            h.assertValueEqual(placedBlocks, expected, "the blocks these items placed");

            // What the name was built from or styled with is dropped, and a long one is cut.
            clear(s);
            floorAndWall(s);
            Who who = Who.at(s, 8, 1, 6);
            ItemStack styled = new ItemStack(AtmosItems.VENT.get());
            styled.set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("§cHot§r  ")
                            .withStyle(ChatFormatting.RED, ChatFormatting.OBFUSCATED)
                            .append(Component.literal("side\n")
                                    .withStyle(style -> style.withHoverEvent(
                                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("x"))))));
            who.hold(styled);
            who.click(s.abs(8, 0, 3));
            h.assertValueEqual(label(s, s.abs(8, 1, 3)), "Hot side", "the label from a styled name");
            who.hold(named(AtmosItems.VENT.get(), "A".repeat(50)));
            who.click(s.abs(10, 0, 3));
            h.assertValueEqual(label(s, s.abs(10, 1, 3)), "A".repeat(Labels.MAX_LENGTH), "the label from a long name");

            // A heater is no controller's part: it takes no label, and its item places it as it did.
            who.hold(named(AtmosItems.HEATER.get(), "Mess heater"));
            h.assertTrue(who.click(s.abs(12, 0, 3)).consumesAction(), "the renamed heater wasn't placed");
            h.assertTrue(s.level.getBlockState(s.abs(12, 1, 3)).is(AtmosBlocks.HEATER.get()), "no heater placed");
            h.assertTrue(Labels.at(s.level, s.abs(12, 1, 3)) == null, "a heater takes a label");
            h.assertValueEqual(label(s, s.abs(12, 1, 3)), "", "a heater's label");
            h.assertValueEqual(
                    ((AtmosDevice) s.level.getBlockEntity(s.abs(12, 1, 3))).label(), "", "what the heater kept");
        });
    }

    // ---- Set and cleared by a name tag ----------------------------------------------------------------

    /**
     * A name tag renamed in an anvil, used on a device, sets its label and is used up, and the device
     * doesn't open its window. Used again with the same name it changes nothing and is kept; a
     * creative player's is kept too. A tag with no name clears the label and is kept, and on a device
     * with no label it does nothing, so the window opens as it always did. A spectator's does nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_name_tag_sets_a_label_and_one_with_no_name_clears_it(GameTestHelper h) {
        Site s = new Site(h, 4891);
        run(h, s, () -> {
            floorAndWall(s);
            VentBlockEntity vent = vent(s, 3, 3);
            BlockPos at = vent.getBlockPos();
            Who who = Who.at(s, 3, 1, 5);

            ItemStack tags = named(Items.NAME_TAG, "Supply 1");
            tags.setCount(3);
            who.hold(tags);
            h.assertTrue(who.click(at).consumesAction(), "the click with a named tag passed");
            h.assertValueEqual(vent.label(), "Supply 1", "the label a name tag set");
            h.assertValueEqual(who.held().getCount(), 2, "name tags left after setting a label");
            h.assertValueEqual(who.told(), "block.zas_atmos.label.set[Supply 1]", "what setting a label says");
            h.assertFalse(who.windowOpen(), "the vent's window opened under a name tag");

            h.assertTrue(who.click(at).consumesAction(), "the second click with the tag passed");
            h.assertValueEqual(vent.label(), "Supply 1", "the label after the same tag again");
            h.assertValueEqual(who.held().getCount(), 2, "name tags left after setting the same label");

            who.player.setGameMode(GameType.CREATIVE);
            who.hold(named(Items.NAME_TAG, "Supply 2"));
            who.click(at);
            h.assertValueEqual(vent.label(), "Supply 2", "the label a creative player's tag set");
            h.assertValueEqual(who.held().getCount(), 1, "a creative player's name tags");
            who.player.setGameMode(GameType.SURVIVAL);

            // What the tag's name was styled with is dropped, and a long one is cut.
            ItemStack styled = new ItemStack(Items.NAME_TAG);
            styled.set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("§lEngine\troom §kvent " + "x".repeat(40)).withStyle(ChatFormatting.GOLD));
            who.hold(styled);
            who.click(at);
            h.assertValueEqual(
                    vent.label(), "Engine room vent " + "x".repeat(15), "the label from a styled, long name");
            h.assertValueEqual(vent.label().length(), Labels.MAX_LENGTH, "its length");

            who.player.setGameMode(GameType.SPECTATOR);
            who.hold(named(Items.NAME_TAG, "Seen"));
            who.click(at);
            h.assertValueEqual(vent.label().length(), Labels.MAX_LENGTH, "the label after a spectator's tag");
            h.assertValueEqual(who.held().getCount(), 1, "a spectator's name tags");
            who.player.setGameMode(GameType.SURVIVAL);
            who.closeWindow();

            ItemStack plain = new ItemStack(Items.NAME_TAG, 2);
            who.hold(plain);
            h.assertTrue(who.click(at).consumesAction(), "the click with a tag with no name passed");
            h.assertValueEqual(vent.label(), "", "the label after a tag with no name");
            h.assertValueEqual(who.held().getCount(), 2, "tags with no name left after clearing");
            h.assertValueEqual(who.told(), "block.zas_atmos.label.cleared[]", "what clearing a label says");
            h.assertFalse(who.windowOpen(), "the vent's window opened under a tag with no name");

            // With no label to clear, the tag does nothing and the vent gets the click.
            who.click(at);
            h.assertTrue(who.player.containerMenu instanceof VentMenu, "a tag with no name kept the window shut");
            h.assertValueEqual(who.held().getCount(), 2, "tags with no name left after opening the window");
        });
    }

    /**
     * A name tag reaches a device from any of its blocks and keeps the click from it: an airlock and
     * a door with a firelock don't move, a sensor isn't pressed into a window, and a console doesn't
     * open. On a device that takes no label the tag does nothing, and its window opens.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_name_tag_labels_a_device_from_any_of_its_blocks_and_keeps_the_click_from_it(GameTestHelper h) {
        Site s = new Site(h, 4892);
        run(h, s, () -> {
            floorAndWall(s);
            Who who = Who.at(s, 8, 1, 5);

            AirlockBlockEntity airlock = AirlockTests.airlock(s, 2, 3, Direction.EAST, false);
            who.hold(named(Items.NAME_TAG, "Dock"));
            h.assertTrue(who.click(s.abs(2, 2, 3)).consumesAction(), "the click on the airlock's upper block");
            h.assertValueEqual(airlock.label(), "Dock", "the airlock's label, set from its upper block");
            h.assertFalse(s.level.getBlockState(s.abs(2, 1, 3)).getValue(AirlockBlock.OPEN), "the airlock opened");

            BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            s.set(4, 1, 3, oak);
            s.set(4, 2, 3, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            h.assertTrue(FirelockDoorBlock.fit(s.level, s.abs(4, 1, 3)), "the firelock didn't fit the door");
            who.hold(named(Items.NAME_TAG, "Galley door"));
            h.assertTrue(who.click(s.abs(4, 2, 3)).consumesAction(), "the click on the door's upper half");
            h.assertValueEqual(label(s, s.abs(4, 1, 3)), "Galley door", "the door's label, on its lower half");
            h.assertValueEqual(label(s, s.abs(4, 2, 3)), "Galley door", "the door's label, from its upper half");
            h.assertFalse(s.level.getBlockState(s.abs(4, 1, 3)).getValue(BlockStateProperties.OPEN), "the door opened");

            BlockState firelock =
                    AtmosBlocks.FIRELOCK.get().defaultBlockState().setValue(FirelockBlock.FACING, Direction.EAST);
            s.set(6, 1, 3, firelock);
            s.set(6, 2, 3, firelock.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
            who.hold(named(Items.NAME_TAG, "Corridor"));
            h.assertTrue(who.click(s.abs(6, 2, 3)).consumesAction(), "the click on the firelock's upper half");
            h.assertValueEqual(label(s, s.abs(6, 1, 3)), "Corridor", "the firelock's label");

            AirSensorBlockEntity sensor = AirlockTests.sensor(s, 8, 2, 1);
            who.hold(named(Items.NAME_TAG, "Chamber gauge"));
            h.assertTrue(who.click(sensor.getBlockPos(), Direction.SOUTH).consumesAction(), "the click on the sensor");
            h.assertValueEqual(sensor.label(), "Chamber gauge", "the sensor's label");

            CentralControllerBlockEntity console = central(s, 12, 1, 3);
            List<BlockPos> parts = CentralControllerBlock.parts(console.getBlockPos(), Direction.NORTH);
            who.hold(named(Items.NAME_TAG, "Atmos office"));
            h.assertTrue(who.click(parts.get(3), Direction.SOUTH).consumesAction(), "the click on the console");
            h.assertValueEqual(console.label(), "Atmos office", "the console's label, set from its upper right block");
            for (BlockPos part : parts) {
                h.assertValueEqual(label(s, part), "Atmos office", "the console's label read from " + part);
            }
            h.assertFalse(who.windowOpen(), "the console's window opened under a name tag");

            s.set(14, 1, 3, AtmosBlocks.HEATER.get().defaultBlockState().setValue(HeaterBlock.FACING, Direction.NORTH));
            who.hold(named(Items.NAME_TAG, "Mess heater"));
            who.click(s.abs(14, 1, 3));
            h.assertTrue(who.windowOpen(), "a name tag kept a heater's window shut");
            h.assertValueEqual(who.held().getCount(), 1, "name tags left after a heater");
            h.assertValueEqual(label(s, s.abs(14, 1, 3)), "", "a heater's label");
        });
    }

    // ---- Set and cleared from the window --------------------------------------------------------------

    /**
     * The title bar of a device's window sets its label and clears it. The server cleans what it is
     * sent: formatting, control characters and extra spaces go, and a long label is cut at 32
     * characters. More than a packet may carry is refused whole.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_window_sets_and_clears_its_devices_label_and_the_server_cleans_it(GameTestHelper h) {
        Site s = new Site(h, 4893);
        run(h, s, () -> {
            floorAndWall(s);
            VentBlockEntity vent = vent(s, 3, 3);
            Who who = Who.at(s, 3, 1, 5);
            who.click(vent.getBlockPos());
            h.assertTrue(
                    who.player.containerMenu instanceof VentMenu menu && menu.labelled() == vent,
                    "the vent's window names another device");

            who.type("North vent");
            h.assertValueEqual(vent.label(), "North vent", "the label the window set");
            who.type("");
            h.assertValueEqual(vent.label(), "", "the label the window cleared");
            who.type("y".repeat(100));
            h.assertValueEqual(vent.label(), "y".repeat(Labels.MAX_LENGTH), "a label of 100 characters");
            who.type("  §lBold§r\n\tvent\u0000 ‮​ x  ");
            h.assertValueEqual(vent.label(), "Bold vent x", "a label with formatting and control characters");
            who.type(" \n\t ");
            h.assertValueEqual(vent.label(), "", "a label of spaces alone");
            // A pair is kept or dropped whole at the cut, and never ends in a space.
            who.type("z".repeat(31) + "🚀");
            h.assertValueEqual(vent.label(), "z".repeat(31), "a label cut before a pair of characters");
            who.type("w".repeat(31) + " tail");
            h.assertValueEqual(vent.label(), "w".repeat(31), "a label cut at a space");
            who.type("Lüftung 東 🚀");
            h.assertValueEqual(vent.label(), "Lüftung 東 🚀", "a label in other scripts");

            // What the client can send at most is read, cut and kept; more is refused before it is read.
            ByteBuf wire = Unpooled.buffer();
            LabelPayload.CODEC.encode(wire, new LabelPayload(7, "q".repeat(LabelPayload.MAX_SENT)));
            LabelPayload read = LabelPayload.CODEC.decode(wire);
            h.assertValueEqual(read.containerId(), 7, "the window a packet names");
            h.assertValueEqual(read.text().length(), LabelPayload.MAX_SENT, "the longest text a packet carries");
            ByteBuf tooLong = Unpooled.buffer();
            VarInt.write(tooLong, 7);
            Utf8String.write(tooLong, "q".repeat(LabelPayload.MAX_SENT + 1), 4096);
            boolean refused = false;
            try {
                LabelPayload.CODEC.decode(tooLong);
            } catch (RuntimeException e) {
                refused = true;
            }
            h.assertTrue(refused, "a packet with more text than it may carry was read");
        });
    }

    /**
     * Only a player who may use the window sets a label: one with that window open, within reach, and
     * not spectating. A packet naming another window, sent with no window open, sent from out of
     * reach, sent by a spectator, or sent for the window of a device that takes no label changes
     * nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void only_a_player_who_may_use_the_window_sets_a_label(GameTestHelper h) {
        Site s = new Site(h, 4894);
        run(h, s, () -> {
            floorAndWall(s);
            VentBlockEntity vent = vent(s, 3, 3);
            s.set(6, 1, 3, AtmosBlocks.HEATER.get().defaultBlockState().setValue(HeaterBlock.FACING, Direction.NORTH));
            vent.setLabel("Kept");
            Who who = Who.at(s, 3, 1, 5);

            // No window open: the player's own inventory is window 0.
            h.assertFalse(
                    LabelPayload.apply(who.player, new LabelPayload(who.player.containerMenu.containerId, "Mine")),
                    "a label was taken with no window open");
            h.assertValueEqual(vent.label(), "Kept", "the label after a packet with no window open");

            who.click(vent.getBlockPos());
            int window = who.player.containerMenu.containerId;
            h.assertTrue(who.player.containerMenu instanceof LabelledMenu, "the vent's window takes no label");
            h.assertFalse(
                    LabelPayload.apply(who.player, new LabelPayload(window + 1, "Mine")),
                    "a label was taken for another window");
            h.assertValueEqual(vent.label(), "Kept", "the label after a packet for another window");

            who.player.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 45)));
            h.assertFalse(
                    LabelPayload.apply(who.player, new LabelPayload(window, "Mine")),
                    "a label was taken from out of reach");
            h.assertValueEqual(vent.label(), "Kept", "the label after a packet from out of reach");
            who.player.moveTo(Vec3.atBottomCenterOf(s.abs(3, 1, 5)));

            who.player.setGameMode(GameType.SPECTATOR);
            h.assertFalse(
                    LabelPayload.apply(who.player, new LabelPayload(window, "Mine")),
                    "a label was taken from a spectator");
            who.type("Mine");
            h.assertValueEqual(vent.label(), "Kept", "the label after a spectator's packet");
            who.player.setGameMode(GameType.SURVIVAL);

            // Another player, with no window of their own on it, can't reach it through this one's.
            Who other = Who.at(s, 3, 1, 5);
            h.assertFalse(
                    LabelPayload.apply(other.player, new LabelPayload(window, "Theirs")),
                    "a label was taken from a player without the window");
            h.assertValueEqual(vent.label(), "Kept", "the label after another player's packet");

            h.assertTrue(LabelPayload.apply(who.player, new LabelPayload(window, "Mine")), "the window's own label");
            h.assertValueEqual(vent.label(), "Mine", "the label the window's player set");

            // The vent broken under the window: nothing to label.
            s.set(3, 1, 3, Site.AIR);
            h.assertFalse(
                    LabelPayload.apply(who.player, new LabelPayload(window, "Gone")),
                    "a label was taken for a device that is gone");
            who.closeWindow();

            who.hold(ItemStack.EMPTY);
            who.click(s.abs(6, 1, 3));
            h.assertTrue(who.windowOpen(), "the heater's window didn't open");
            h.assertFalse(who.player.containerMenu instanceof LabelledMenu, "a heater's window takes a label");
            h.assertFalse(
                    LabelPayload.apply(
                            who.player, new LabelPayload(who.player.containerMenu.containerId, "Mess heater")),
                    "a label was taken for a heater's window");
            h.assertValueEqual(label(s, s.abs(6, 1, 3)), "", "a heater's label");
        });
    }

    /**
     * A labelled device's window is titled with its label, with its kind beside it for the window to
     * show; one with no label is titled with its kind, as it always was. The area air controller's
     * window opened from a central controller is titled the same way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_labelled_devices_window_is_titled_with_its_label(GameTestHelper h) {
        Site s = new Site(h, 4895);
        run(h, s, () -> {
            floorAndWall(s);
            VentBlockEntity vent = vent(s, 3, 3);
            AreaAirControllerBlockEntity controller = s.controller(6);
            CentralControllerBlockEntity console = central(s, 12, 1, 3);
            AirlockControllerBlockEntity airlockController = s.place(
                    9,
                    2,
                    1,
                    AtmosBlocks.AIRLOCK_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AirlockControllerBlock.FACING, Direction.SOUTH),
                    AirlockControllerBlockEntity.class);
            Who who = Who.at(s, 8, 1, 4);
            List<Labelled> devices = List.of(vent, controller, console, airlockController);
            List<Direction> faces = List.of(Direction.UP, Direction.SOUTH, Direction.SOUTH, Direction.SOUTH);
            for (int i = 0; i < devices.size(); i++) {
                Labelled device = devices.get(i);
                BlockPos at = ((BlockEntity) device).getBlockPos();
                Component kind =
                        ((BlockEntity) device).getBlockState().getBlock().getName();

                who.click(at, faces.get(i));
                h.assertTrue(who.player.containerMenu instanceof LabelledMenu, "no window for " + shown(kind));
                h.assertValueEqual(shown(who.title()), shown(kind), "the title with no label");
                h.assertValueEqual(Labels.labelIn(who.title()), "", "the label in a title with none");
                h.assertValueEqual(shown(Labels.kindIn(who.title())), shown(kind), "the kind in a title with no label");
                who.closeWindow();

                device.setLabel("Number " + i);
                who.click(at, faces.get(i));
                h.assertValueEqual(
                        shown(who.title()),
                        Labels.TITLE_KEY + List.of("Number " + i, shown(kind)),
                        "the title with a label");
                h.assertValueEqual(Labels.labelIn(who.title()), "Number " + i, "the label in the title");
                h.assertValueEqual(shown(Labels.kindIn(who.title())), shown(kind), "the kind in the title");
                who.closeWindow();

                // What reads a block's name reads the label, as it reads a named chest's.
                Nameable named = device;
                h.assertTrue(named.hasCustomName(), "a labelled device has no name of its own");
                h.assertValueEqual(named.getCustomName().getString(), "Number " + i, "its name");
                h.assertValueEqual(named.getName().getString(), "Number " + i, "the name it goes by");
                device.setLabel(null);
                h.assertFalse(named.hasCustomName(), "a device with no label has a name of its own");
                h.assertValueEqual(shown(named.getName()), shown(kind), "the name a device with no label goes by");
            }
        });
    }

    // ---- Shown in the controllers' lists --------------------------------------------------------------

    /**
     * What the server sends an area air controller's window lists each part with its label, or none,
     * and names the central controller that has it by its label; all of it survives the packet.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void an_area_air_controllers_window_is_sent_its_parts_labels(GameTestHelper h) {
        Site s = new Site(h, 4896);
        run(h, s, () -> {
            floorAndWall(s);
            AreaAirControllerBlockEntity c = s.controller(1);
            VentBlockEntity first = vent(s, 3, 3);
            VentBlockEntity second = vent(s, 4, 3);
            VentBlockEntity third = vent(s, 5, 3);
            for (VentBlockEntity v : List.of(first, second, third)) {
                c.toggleLink(v.getBlockPos());
            }
            first.setLabel("Supply, north");
            third.setLabel("M".repeat(Labels.MAX_LENGTH));
            c.buffer().set(c.buffer().getMaxEnergyStored());
            s.cycle(c);

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ControllerSync sync = new ControllerMenu(0, player.getInventory(), c).sync();
            h.assertValueEqual(sync.parts().size(), 3, "the parts listed");
            h.assertValueEqual(part(sync, first).label(), "Supply, north", "the first vent's label in the list");
            h.assertValueEqual(part(sync, second).label(), "", "the second vent's label in the list");
            h.assertValueEqual(part(sync, third).label(), "M".repeat(Labels.MAX_LENGTH), "the long label in the list");
            h.assertTrue(sync.central() == null, "a central controller with none placed");
            h.assertValueEqual(sync.centralLabel(), "", "a central controller's label with none placed");

            // A label changed shows at the controller's next step, as its other columns do.
            second.setLabel("Supply, south");
            first.setLabel("");
            s.cycle(c);
            sync = new ControllerMenu(0, player.getInventory(), c).sync();
            h.assertValueEqual(part(sync, first).label(), "", "the cleared label in the list");
            h.assertValueEqual(part(sync, second).label(), "Supply, south", "the new label in the list");

            CentralControllerBlockEntity console = central(s, 12, 1, 3);
            h.assertTrue(console.take(c.getBlockPos()), "the console couldn't take the controller");
            sync = new ControllerMenu(0, player.getInventory(), c).sync();
            h.assertValueEqual(sync.central(), console.getBlockPos(), "the central controller named");
            h.assertValueEqual(sync.centralLabel(), "", "its label, with none");
            console.setLabel("Atmos office");
            sync = new ControllerMenu(0, player.getInventory(), c).sync();
            h.assertValueEqual(sync.centralLabel(), "Atmos office", "the central controller's label");

            RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), s.level.registryAccess());
            ControllerSync.CODEC.encode(wire, sync);
            ControllerSync read = ControllerSync.CODEC.decode(wire);
            h.assertValueEqual(read.parts(), sync.parts(), "the parts a client reads");
            h.assertValueEqual(read.centralLabel(), "Atmos office", "the central controller's label a client reads");
            h.assertValueEqual(wire.readableBytes(), 0, "bytes left unread");
        });
    }

    private static AreaAirControllerBlockEntity.Part part(ControllerSync sync, BlockEntity device) {
        return sync.parts().stream()
                .filter(p -> p.pos().equals(device.getBlockPos()))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no part at " + device.getBlockPos()));
    }

    /**
     * What the server sends a central controller's window lists each area air controller with its
     * label: the one it has now while it is in contact, and the one it had last, kept with the claim
     * and saved with it, once it isn't.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_central_controllers_window_is_sent_its_controllers_labels(GameTestHelper h) {
        Site s = new Site(h, 4897);
        run(h, s, () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            for (int x = 6; x <= 14; x++) {
                for (int z = 0; z <= 4; z++) {
                    s.set(x, 0, z, Site.STONE);
                }
            }
            CentralControllerBlockEntity console = central(s, 10, 1, 2);
            h.assertTrue(console.take(c.getBlockPos()), "the console couldn't take the controller");
            c.setLabel("Bridge");
            for (int i = 0; i < 4; i++) {
                console.buffer().set(console.buffer().getMaxEnergyStored());
                c.buffer().set(c.buffer().getMaxEnergyStored());
                console.step();
                c.step();
                s.tick();
            }
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            CentralSync sync = new CentralMenu(0, player.getInventory(), console).sync();
            h.assertValueEqual(sync.rows().size(), 1, "the rows listed");
            h.assertTrue(sync.rows().get(0).contact(), "the controller beside the console is out of contact");
            h.assertValueEqual(sync.rows().get(0).label(), "Bridge", "the controller's label in the list");

            // In contact, the list has the label it has now.
            c.setLabel("Bridge, deck 1");
            sync = new CentralMenu(0, player.getInventory(), console).sync();
            h.assertValueEqual(sync.rows().get(0).label(), "Bridge, deck 1", "the changed label in the list");

            // The claim keeps the label with the readings, for when it is out of contact.
            console.buffer().set(console.buffer().getMaxEnergyStored());
            console.step();
            CentralClaims claims = CentralClaims.of(s.level);
            h.assertValueEqual(claims.last(c.getBlockPos()).label(), "Bridge, deck 1", "the label kept with the claim");
            CentralClaims saved = CentralClaims.load(
                    claims.save(new CompoundTag(), s.level.registryAccess()), s.level.registryAccess());
            h.assertValueEqual(saved.last(c.getBlockPos()).label(), "Bridge, deck 1", "the label saved with the claim");
            CompoundTag old = claims.save(new CompoundTag(), s.level.registryAccess());
            old.getList("Claims", CompoundTag.TAG_COMPOUND).forEach(e -> ((CompoundTag) e).remove("Label"));
            h.assertValueEqual(
                    CentralClaims.load(old, s.level.registryAccess())
                            .last(c.getBlockPos())
                            .label(),
                    "",
                    "the label of a claim saved before labels");

            // One out of contact: claimed, with what was last recorded, and nothing loaded there to ask.
            BlockPos far = s.abs(16, 5, 6);
            h.assertTrue(claims.claim(far, console.getBlockPos()), "the far claim");
            claims.record(far, new CentralClaims.Readings(101.3, 293.15, 0, true, "Far deck"));
            sync = new CentralMenu(0, player.getInventory(), console).sync();
            CentralSync.Row row = sync.rows().stream()
                    .filter(r -> r.pos().equals(far))
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException("the far claim isn't listed"));
            h.assertFalse(row.contact(), "a claim with no controller is in contact");
            h.assertValueEqual(row.label(), "Far deck", "the last label of a controller out of contact");

            RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), s.level.registryAccess());
            CentralSync.CODEC.encode(wire, sync);
            CentralSync read = CentralSync.CODEC.decode(wire);
            h.assertValueEqual(read.rows(), sync.rows(), "the rows a client reads");
            h.assertValueEqual(wire.readableBytes(), 0, "bytes left unread");
            claims.release(far);
        });
    }

    // ---- Shown by the multitool -----------------------------------------------------------------------

    /**
     * What the multitool says names a part and a controller by its label, and by its position without
     * one, word for word as before: selecting, linking and unlinking with an area air controller and
     * an airlock controller, filling a slot, and moving a controller to a central controller.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void the_multitool_names_a_labelled_device_by_its_label(GameTestHelper h) {
        Site s = new Site(h, 4898);
        run(h, s, () -> {
            floorAndWall(s);
            AreaAirControllerBlockEntity c = s.controller(1);
            VentBlockEntity vent = vent(s, 3, 3);
            AirlockControllerBlockEntity ac = s.place(
                    5,
                    2,
                    1,
                    AtmosBlocks.AIRLOCK_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AirlockControllerBlock.FACING, Direction.SOUTH),
                    AirlockControllerBlockEntity.class);
            AirSensorBlockEntity sensor = AirlockTests.sensor(s, 7, 2, 1);
            CentralControllerBlockEntity console = central(s, 12, 1, 3);
            String at = "item.zas_atmos.multitool.at";
            String cAt = at + List.of(xyz(c.getBlockPos()));
            String acAt = at + List.of(xyz(ac.getBlockPos()));
            String consoleAt = at + List.of(xyz(console.getBlockPos()));
            String ventAt = xyz(vent.getBlockPos());

            Who who = Who.at(s, 7, 1, 4);
            who.hold(new ItemStack(AtmosItems.MULTITOOL.get()));

            // With no label anywhere, each names a position, as it always did.
            who.click(c.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.selected" + List.of(cAt), "selecting");
            who.click(vent.getBlockPos());
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.linked" + List.of(ventAt, cAt), "linking");
            who.click(vent.getBlockPos());
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.unlinked" + List.of(ventAt, cAt), "unlinking");
            who.click(console.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.moved" + List.of(cAt, consoleAt), "moving");

            // Labelled, each is named by its label.
            c.setLabel("Bridge");
            vent.setLabel("Supply 1");
            console.setLabel("Atmos office");
            who.click(ac.getBlockPos(), Direction.SOUTH); // select another, to select this one again
            who.click(c.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.selected[Bridge]", "selecting a labelled one");
            who.click(vent.getBlockPos());
            h.assertValueEqual(
                    who.told(), "item.zas_atmos.multitool.linked[Supply 1, Bridge]", "linking a labelled vent");
            who.click(vent.getBlockPos());
            h.assertValueEqual(
                    who.told(), "item.zas_atmos.multitool.unlinked[Supply 1, Bridge]", "unlinking a labelled vent");
            who.click(console.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(
                    who.told(), "item.zas_atmos.multitool.moved[Bridge, Atmos office]", "moving a labelled one");

            // An airlock controller, its vents and its slots.
            who.click(ac.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(who.told(), "item.zas_atmos.multitool.selected_airlock" + List.of(acAt), "selecting it");
            who.click(vent.getBlockPos());
            h.assertValueEqual(
                    who.told(),
                    "item.zas_atmos.multitool.linked_airlock" + List.of("Supply 1", acAt),
                    "linking a labelled vent to it");
            ac.setLabel("Dock lock");
            who.click(vent.getBlockPos());
            h.assertValueEqual(
                    who.told(),
                    "item.zas_atmos.multitool.unlinked_airlock[Supply 1, Dock lock]",
                    "unlinking a labelled vent from it, labelled");
            who.click(sensor.getBlockPos(), Direction.SOUTH);
            AirlockControllerBlockEntity.Slot slot = ac.slotOf(sensor.getBlockPos());
            h.assertTrue(slot != null, "the sensor isn't in a slot");
            String slotName = "gui.zas_atmos.airlock_controller." + slot.key() + "[]";
            h.assertValueEqual(
                    who.told(),
                    "item.zas_atmos.multitool.linked_slot" + List.of(slotName, xyz(sensor.getBlockPos())),
                    "filling a slot");
            who.click(sensor.getBlockPos(), Direction.SOUTH); // unlinks it
            sensor.setLabel("Chamber gauge");
            who.click(sensor.getBlockPos(), Direction.SOUTH);
            h.assertValueEqual(
                    who.told(),
                    "item.zas_atmos.multitool.linked_slot" + List.of(slotName, "Chamber gauge"),
                    "filling a slot with a labelled sensor");
        });
    }

    // ---- Kept with the block ----------------------------------------------------------------------------

    /**
     * A label is saved with its block and read back, cleaned again; it is sent to clients, alone, with
     * the chunk and when it changes, and a client reads it and its clearing. A turn of the wrench keeps
     * it. The block's drops carry no name, as they carry none of its settings; an item that copies the
     * block, as a creative player's pick does, is named by it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_label_is_saved_sent_to_clients_and_kept_through_a_turn(GameTestHelper h) {
        Site s = new Site(h, 4899);
        run(h, s, () -> {
            floorAndWall(s);
            var registries = s.level.registryAccess();
            VentBlockEntity vent = vent(s, 3, 3);
            BlockPos at = vent.getBlockPos();
            h.assertTrue(vent.getUpdateTag(registries).isEmpty(), "a vent with no label sends clients something");
            vent.setLabel("Supply 1");

            // Saved and loaded, which is also what unloading and loading its chunk does.
            CompoundTag saved = vent.saveWithFullMetadata(registries);
            h.assertValueEqual(saved.getString("Label"), "Supply 1", "the label in the save");
            BlockEntity loaded = BlockEntity.loadStatic(at, vent.getBlockState(), saved, registries);
            h.assertTrue(loaded instanceof VentBlockEntity v && v.label().equals("Supply 1"), "the label loaded");
            saved.putString("Label", "§cA label\nfar longer than any label a player could ever have typed in");
            loaded = BlockEntity.loadStatic(at, vent.getBlockState(), saved, registries);
            h.assertValueEqual(
                    ((VentBlockEntity) loaded).label(), "A label far longer than any labe", "a label edited in a save");
            saved.remove("Label");
            loaded = BlockEntity.loadStatic(at, vent.getBlockState(), saved, registries);
            h.assertValueEqual(((VentBlockEntity) loaded).label(), "", "a save from before labels");

            // What clients are sent, and what one makes of it.
            CompoundTag sent = vent.getUpdateTag(registries);
            h.assertValueEqual(sent.getAllKeys(), Set.of("Label"), "what a client is sent with the chunk");
            VentBlockEntity client = new VentBlockEntity(at, vent.getBlockState());
            client.handleUpdateTag(sent, registries);
            h.assertValueEqual(client.label(), "Supply 1", "the label a client reads with the chunk");
            vent.setLabel("Supply 2");
            h.assertTrue(
                    vent.getUpdatePacket() instanceof ClientboundBlockEntityDataPacket,
                    "no packet for a changed label");
            ClientboundBlockEntityDataPacket packet = (ClientboundBlockEntityDataPacket) vent.getUpdatePacket();
            client.onDataPacket(null, packet, registries);
            h.assertValueEqual(client.label(), "Supply 2", "the label a client reads when it changes");
            vent.setLabel("");
            client.onDataPacket(null, (ClientboundBlockEntityDataPacket) vent.getUpdatePacket(), registries);
            h.assertValueEqual(client.label(), "", "the label a client reads when it is cleared");
            vent.setLabel("Supply 2");

            // A door with a firelock sends its label beside which door it was.
            BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            s.set(6, 1, 3, oak);
            s.set(6, 2, 3, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            h.assertTrue(FirelockDoorBlock.fit(s.level, s.abs(6, 1, 3)), "the firelock didn't fit the door");
            FirelockDoorBlockEntity door = s.entity(6, 1, 3, FirelockDoorBlockEntity.class);
            door.setLabel("Galley door");
            CompoundTag doorSent = door.getUpdateTag(registries);
            h.assertValueEqual(doorSent.getAllKeys(), Set.of("Label", "Door"), "what a client is sent of a door");
            FirelockDoorBlockEntity clientDoor = new FirelockDoorBlockEntity(door.getBlockPos(), door.getBlockState());
            clientDoor.handleUpdateTag(doorSent, registries);
            h.assertValueEqual(clientDoor.label(), "Galley door", "the door's label a client reads");
            h.assertValueEqual(clientDoor.door(), Blocks.OAK_DOOR, "the door a client reads");

            // A turn of the wrench keeps the block entity, and so its label.
            Who who = Who.at(s, 3, 1, 5);
            who.hold(new ItemStack(AtmosItems.WRENCH.get()));
            Direction before = vent.getBlockState().getValue(VentBlock.FACING);
            h.assertTrue(who.click(at, Direction.NORTH).consumesAction(), "the wrench didn't turn the vent");
            h.assertTrue(s.level.getBlockState(at).getValue(VentBlock.FACING) != before, "the vent faces as it did");
            h.assertTrue(s.level.getBlockEntity(at) == vent, "the turn replaced the vent");
            h.assertValueEqual(label(s, at), "Supply 2", "the label after a turn");

            // Its drops are plain, as they are for its other settings; a copy of the block is named.
            List<ItemStack> drops = Block.getDrops(s.level.getBlockState(at), s.level, at, vent);
            h.assertValueEqual(drops.size(), 1, "the vent's drops");
            h.assertTrue(drops.get(0).is(AtmosItems.VENT.get()), "the vent dropped " + drops.get(0));
            h.assertTrue(drops.get(0).get(DataComponents.CUSTOM_NAME) == null, "the dropped vent is named");
            ItemStack copy = new ItemStack(AtmosItems.VENT.get());
            vent.saveToItem(copy, registries);
            h.assertValueEqual(copy.getHoverName().getString(), "Supply 2", "the name of a copy of the vent");
            h.assertFalse(
                    copy.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
                            .copyTag()
                            .contains("Label"),
                    "a copy of the vent has its label twice");
            who.hold(copy);
            who.click(s.abs(9, 0, 3));
            h.assertValueEqual(label(s, s.abs(9, 1, 3)), "Supply 2", "the label of a vent placed from the copy");
        });
    }

    /**
     * A firelock renamed in an anvil and fitted to a door or an airlock gives it its name as a label,
     * as it would the firelock it places, unless that has a label of its own already.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_renamed_firelock_labels_the_door_or_airlock_it_is_fitted_to(GameTestHelper h) {
        Site s = new Site(h, 4900);
        run(h, s, () -> {
            floorAndWall(s);
            BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            s.set(3, 1, 3, oak);
            s.set(3, 2, 3, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            AirlockBlockEntity plain = AirlockTests.airlock(s, 6, 3, Direction.EAST, false);
            AirlockBlockEntity named = AirlockTests.airlock(s, 9, 3, Direction.EAST, false);
            named.setLabel("Dock");
            Who who = Who.at(s, 6, 1, 5);

            ItemStack firelocks = named(AtmosItems.FIRELOCK.get(), "Fire door");
            firelocks.setCount(3);
            who.hold(firelocks);
            h.assertTrue(who.click(s.abs(3, 2, 3)).consumesAction(), "the firelock wasn't fitted to the door");
            h.assertTrue(
                    s.level.getBlockState(s.abs(3, 1, 3)).getBlock() instanceof FirelockDoorBlock, "no firelock on it");
            h.assertValueEqual(label(s, s.abs(3, 1, 3)), "Fire door", "the door's label");

            h.assertTrue(who.click(s.abs(6, 2, 3)).consumesAction(), "the firelock wasn't fitted to the airlock");
            h.assertTrue(plain.hasFirelock(), "no firelock on the airlock");
            h.assertValueEqual(plain.label(), "Fire door", "the airlock's label");

            h.assertTrue(who.click(s.abs(9, 1, 3)).consumesAction(), "the firelock wasn't fitted to the named airlock");
            h.assertTrue(named.hasFirelock(), "no firelock on the named airlock");
            h.assertValueEqual(named.label(), "Dock", "the label the airlock had");
            h.assertTrue(who.held().isEmpty(), "firelocks left: " + who.held());
        });
    }
}
