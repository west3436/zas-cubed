package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ConsoleScreen;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.controller.VentOrder;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The central atmospherics controller in a real level (CONT-115 to CONT-122, CONT-124), with the
 * stand-in parts of {@link TestParts}. The tests in {@link #BATCH} build and step everything within
 * one game tick, as {@link ControllerTests} does. Those in {@link #LIVE} and {@link #CHUNKS} wait in
 * real time, for what only the game's own schedule and chunk loading show; {@link #CHUNKS} has a
 * batch of its own because it turns {@code force_load} off.
 *
 * <p>A central controller reaches 10 chunks and the sites are 16 apart, so none reaches another
 * test's area air controllers while that test runs.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CentralControllerTests {
    private static final String BATCH = "zas_atmos_central";
    private static final String LIVE = "zas_atmos_central_live";
    private static final String CHUNKS = "zas_atmos_central_chunks";

    /** Game ticks: no test here waits more than about fifteen atmos ticks. */
    private static final int TIMEOUT = 600;

    /** How long a live test waits after its last driven step, as {@link PowerChargeTests} does. */
    private static final int SETTLE = 2 * AtmosTicks.PERIOD;

    /** What the end of a live batch undoes for a test that failed before it could. */
    private static final List<Runnable> CLEANUP = new ArrayList<>();

    private CentralControllerTests() {}

    @AfterBatch(batch = LIVE)
    public static void cleanUpLive(ServerLevel level) {
        cleanUp();
    }

    @AfterBatch(batch = CHUNKS)
    public static void cleanUpChunks(ServerLevel level) {
        cleanUp();
    }

    private static void cleanUp() {
        List<Runnable> left = new ArrayList<>(CLEANUP);
        CLEANUP.clear();
        left.forEach(Runnable::run);
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** The central controller's own bill for one atmos tick. */
    private static int own() {
        return AtmosServerConfig.get(AtmosServerConfig.CENTRAL_DRAW) * AtmosTicks.PERIOD;
    }

    /** An area air controller's own bill for one atmos tick. */
    private static int controllerOwn() {
        return AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
    }

    /** What an area air controller and one stand-in vent cost an atmos tick. */
    private static int ventBill() {
        return controllerOwn() + TestParts.Kind.VENT.draw * AtmosTicks.PERIOD;
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

    /**
     * Clears a site, each console first by its lower left block, which takes the rest with no drop.
     * Clearing another block of it first would drop the console's item.
     */
    private static void clearSite(Site s) {
        for (BlockPos p : BlockPos.betweenClosed(s.abs(0, 0, 0), s.abs(16, 6, 6))) {
            BlockState state = s.level.getBlockState(p);
            if (state.getBlock() instanceof CentralControllerBlock
                    && CentralControllerBlock.origin(state, p).equals(p)) {
                s.level.setBlockAndUpdate(p.immutable(), Site.AIR);
            }
        }
        s.clear();
    }

    /**
     * An area air controller at a point of a site, facing south, on a wall block just north of it,
     * anywhere: outside {@link Site#clear}'s box too. Both blocks are listed for {@link #clear}.
     */
    private static AreaAirControllerBlockEntity controllerOn(
            GameTestHelper h, Site s, List<BlockPos> placed, int x, int y, int z, BlockState wall) {
        s.set(x, y, z - 1, wall);
        placed.add(s.abs(x, y, z - 1));
        BlockState state = AtmosBlocks.AREA_AIR_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH);
        h.assertTrue(state.canSurvive(s.level, s.abs(x, y, z)), "the controller has no wall behind it");
        s.set(x, y, z, state);
        placed.add(s.abs(x, y, z));
        return s.entity(x, y, z, AreaAirControllerBlockEntity.class);
    }

    /** Removes what {@link #controllerOn} placed, each controller before its wall. */
    private static void clear(Site s, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            s.level.setBlockAndUpdate(placed.get(i), Site.AIR);
        }
        placed.clear();
    }

    /** Runs a one-tick test's checks on a cleared site, then clears it even if they fail. */
    private static void run(GameTestHelper h, Site s, List<BlockPos> placed, Runnable checks) {
        try {
            clearSite(s);
            checks.run();
        } finally {
            clear(s, placed);
            clearSite(s);
        }
        h.succeed();
    }

    private static TestParts.Vent vent(Site s, int x) {
        return s.place(x, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
    }

    /** Steps a controller until it covers a part in its zone, which takes a step to pin the zone. */
    private static void formZone(GameTestHelper h, Site s, AreaAirControllerBlockEntity c, TestParts.Part part) {
        for (int i = 0; i < 4 && !part.power().isPowered(); i++) {
            s.cycle(c);
        }
        h.assertTrue(part.power().isPowered(), "the controller didn't cover the part in its zone");
    }

    private static CentralClaims claims(Site s) {
        return CentralClaims.of(s.level);
    }

    /** The central controller that has claimed an area air controller, or null. */
    private static BlockPos owner(Site s, BlockEntity controller) {
        return claims(s).centralOf(controller.getBlockPos()).orElse(null);
    }

    /** Uses an item on the top of a block, as a right-click does. */
    private static InteractionResult use(Player player, ItemStack stack, BlockPos on) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** Sets a claimed controller's zone power priority with its row's button in the central controller's GUI. */
    private static void priority(
            GameTestHelper h, CentralMenu menu, Player player, AreaAirControllerBlockEntity c, ZonePriority p) {
        int row = menu.row(c.getBlockPos())
                .orElseThrow(() -> new GameTestAssertException("no row for " + c.getBlockPos()));
        int button = CentralMenu.PRIORITY + row * ZonePriority.values().length + p.ordinal();
        h.assertTrue(menu.clickMenuButton(player, button), "the " + p.key() + " button");
    }

    private static void forced(Site s, boolean on) {
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, on);
        s.level.setChunkForced(cx + 1, cz, on);
    }

    /**
     * Holds a live test's first two chunks with tickets of the test's own and clears them. What it
     * returns undoes that and removes what {@link #controllerOn} placed; the batch's end runs it if
     * the test doesn't.
     */
    private static Runnable hold(Site s, List<BlockPos> placed) {
        forced(s, true);
        clearSite(s);
        Runnable undo = () -> {
            clear(s, placed);
            clearSite(s);
            forced(s, false);
        };
        CLEANUP.add(undo);
        return undo;
    }

    /** Runs a live test's last checks, then undoes its site even if they fail. */
    private static void finish(GameTestSequence seq, Runnable undo, Runnable checks) {
        seq.thenExecute(() -> {
                    try {
                        checks.run();
                    } finally {
                        CLEANUP.remove(undo);
                        undo.run();
                    }
                })
                .thenSucceed();
    }

    /** A player with a connection that keeps what it's sent, at a point of a site; not in the level. */
    private static ServerPlayer player(Site s, int x, int y, int z) {
        ServerPlayer p = new ServerPlayer(
                s.level.getServer(),
                s.level,
                new GameProfile(UUID.randomUUID(), "zas_central"),
                ClientInformation.createDefault());
        List<Packet<?>> sent = new ArrayList<>();
        new ListeningPlayer.Recording(s.level, p, sent);
        p.moveTo(Vec3.atBottomCenterOf(s.abs(x, y, z)));
        return p;
    }

    /** Presses a button of the player's open menu, as the client's packet does. */
    private static void press(ServerPlayer p, int button) {
        p.connection.handleContainerButtonClick(
                new ServerboundContainerButtonClickPacket(p.containerMenu.containerId, button));
    }

    // ---- CONT-115 ---------------------------------------------------------------------------

    /**
     * It fills from a generator next to it, up to its 100,000 FE buffer and no further, and pays its
     * own 5 FE/t from it each atmos tick. With less than that it has no power (CONT-115).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_115_it_fills_from_a_generator_to_100000_fe_and_pays_5_fe_per_tick(GameTestHelper h) {
        Site s = new Site(h, 4300);
        run(h, s, new ArrayList<>(), () -> {
            CentralControllerBlockEntity c = central(s, 2, 1, 2);
            GeneratorBlockEntity g =
                    s.place(3, 1, 2, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            h.assertValueEqual(AtmosServerConfig.get(AtmosServerConfig.CENTRAL_DRAW), 5, "its draw in FE/t");
            int own = own();
            h.assertValueEqual(c.buffer().getMaxEnergyStored(), 100_000, "the buffer's size");

            g.energy().set(8_000);
            s.cycle(c);
            h.assertTrue(c.powered(), "it has no power after filling");
            h.assertValueEqual(g.energy().getEnergyStored(), 0, "the generator after a step");
            h.assertValueEqual(c.buffer().getEnergyStored(), 8_000 - own, "the buffer after a step from empty");

            c.buffer().set(100_000 - 10);
            g.energy().set(8_000);
            s.cycle(c);
            h.assertValueEqual(g.energy().getEnergyStored(), 8_000 - 10, "the generator, with only 10 FE of room");
            h.assertValueEqual(c.buffer().getEnergyStored(), 100_000 - own, "a full buffer less its draw");
            s.cycle(c);
            h.assertValueEqual(g.energy().getEnergyStored(), 8_000 - 10 - own, "the generator after the next step");
            h.assertValueEqual(c.buffer().getEnergyStored(), 100_000 - own, "the buffer refilled");

            g.energy().set(0);
            c.buffer().set(own - 1);
            s.cycle(c);
            h.assertFalse(c.powered(), "it has power with less than its draw");
            h.assertValueEqual(c.buffer().getEnergyStored(), own - 1, "what it kept without power");
            h.assertValueEqual(c.status(), ControllerStatus.UNPOWERED, "its status");
        });
    }

    /**
     * Placed from a stack of one, the console takes the item and fills its four spaces, facing the
     * player and growing up and to their right, with its block entity in the lower left block.
     * Breaking any one of the four with a pickaxe breaks them all and drops exactly one item; in
     * creative it drops none (CONT-115).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_115_placing_and_breaking_any_block_keeps_exactly_one_item(GameTestHelper h) {
        Site s = new Site(h, 4308);
        run(h, s, new ArrayList<>(), () -> {
            floor(s);
            ServerPlayer p = player(s, 2, 1, 5);
            p.setYRot(180); // looking north, so the console faces south and grows east
            List<BlockPos> parts = CentralControllerBlock.parts(s.abs(2, 1, 2), Direction.SOUTH);
            for (int broken = 0; broken < parts.size(); broken++) {
                ItemStack item = new ItemStack(AtmosItems.CENTRAL_ATMOS_CONTROLLER.get());
                p.setItemInHand(InteractionHand.MAIN_HAND, item);
                place(p, s, 2, 2);
                h.assertTrue(item.isEmpty(), "placing it didn't take the item");
                for (int i = 0; i < parts.size(); i++) {
                    BlockState state = s.level.getBlockState(parts.get(i));
                    h.assertValueEqual(
                            state,
                            CentralControllerBlock.part(
                                    AtmosBlocks.CENTRAL_ATMOS_CONTROLLER
                                            .get()
                                            .defaultBlockState()
                                            .setValue(CentralControllerBlock.FACING, Direction.SOUTH)
                                            .setValue(
                                                    CentralControllerBlock.SCREEN,
                                                    state.getValue(CentralControllerBlock.SCREEN)),
                                    i),
                            "block " + i + " of the console");
                    h.assertValueEqual(
                            s.level.getBlockEntity(parts.get(i)) instanceof CentralControllerBlockEntity,
                            i == 0,
                            "a block entity in block " + i);
                }

                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                h.assertTrue(p.gameMode.destroyBlock(parts.get(broken)), "block " + broken + " wasn't broken");
                for (BlockPos part : parts) {
                    h.assertTrue(s.level.getBlockState(part).isAir(), "a block left after breaking block " + broken);
                }
                h.assertValueEqual(drops(s), 1, "items dropped after breaking block " + broken);
            }

            p.setGameMode(GameType.CREATIVE);
            ItemStack item = new ItemStack(AtmosItems.CENTRAL_ATMOS_CONTROLLER.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, item);
            place(p, s, 2, 2);
            h.assertValueEqual(item.getCount(), 1, "the item placed in creative");
            h.assertTrue(p.gameMode.destroyBlock(parts.get(3)), "the upper right block wasn't broken in creative");
            for (BlockPos part : parts) {
                h.assertTrue(s.level.getBlockState(part).isAir(), "a block left after breaking in creative");
            }
            h.assertValueEqual(drops(s), 0, "items dropped in creative");
        });
    }

    /** With any of its four spaces taken, the console isn't placed, and the item stays (CONT-115). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_115_a_blocked_space_refuses_placing(GameTestHelper h) {
        Site s = new Site(h, 4309);
        run(h, s, new ArrayList<>(), () -> {
            floor(s);
            ServerPlayer p = player(s, 2, 1, 5);
            p.setYRot(180);
            List<BlockPos> parts = CentralControllerBlock.parts(s.abs(2, 1, 2), Direction.SOUTH);
            for (int blocked = 1; blocked < parts.size(); blocked++) {
                s.level.setBlockAndUpdate(parts.get(blocked), Site.STONE);
                ItemStack item = new ItemStack(AtmosItems.CENTRAL_ATMOS_CONTROLLER.get());
                p.setItemInHand(InteractionHand.MAIN_HAND, item);
                place(p, s, 2, 2);
                h.assertValueEqual(item.getCount(), 1, "the item, with block " + blocked + "'s space taken");
                for (BlockPos part : parts) {
                    h.assertFalse(
                            s.level.getBlockState(part).getBlock() instanceof CentralControllerBlock,
                            "placed with block " + blocked + "'s space taken");
                }
                s.level.setBlockAndUpdate(parts.get(blocked), Site.AIR);
            }
        });
    }

    /**
     * The console's four blocks act as one: each outlines the whole console and is a full block, so
     * gas-tight; using any opens its GUI; FE in any goes to its buffer; it takes FE from beside any;
     * and all four show its screen (CONT-115).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_115_its_four_blocks_act_as_one(GameTestHelper h) {
        Site s = new Site(h, 4311);
        run(h, s, new ArrayList<>(), () -> {
            CentralControllerBlockEntity c = central(s, 2, 1, 2);
            List<BlockPos> parts = c.parts();
            AABB whole = new AABB(s.abs(1, 1, 2)).minmax(new AABB(s.abs(2, 2, 2)));
            for (BlockPos part : parts) {
                BlockState state = s.level.getBlockState(part);
                h.assertValueEqual(
                        state.getShape(s.level, part).bounds().move(part), whole, "the outline from " + part);
                h.assertTrue(state.isCollisionShapeFullBlock(s.level, part), "a block of it isn't full: " + part);
            }

            // An FE block above the upper left block, which the lower left one holds the buffer for.
            s.set(2, 3, 2, TestParts.battery().defaultBlockState());
            TestParts.Battery battery = s.entity(2, 3, 2, TestParts.Battery.class);
            battery.store.set(1_000);
            s.cycle(c);
            h.assertValueEqual(battery.store.getEnergyStored(), 0, "the FE block beside the upper left block");
            h.assertValueEqual(c.buffer().getEnergyStored(), 1_000 - own(), "the buffer after a step");
            IEnergyStorage in = s.level.getCapability(Capabilities.EnergyStorage.BLOCK, parts.get(3), Direction.UP);
            h.assertTrue(in != null && !in.canExtract(), "the upper right block's FE");
            h.assertValueEqual(in.receiveEnergy(500, false), 500, "FE given to the upper right block");
            h.assertValueEqual(c.buffer().getEnergyStored(), 1_500 - own(), "the buffer after FE came in");
            for (BlockPos part : parts) {
                h.assertValueEqual(
                        s.level.getBlockState(part).getValue(CentralControllerBlock.SCREEN),
                        ConsoleScreen.NORMAL,
                        "the screen with power, of " + part);
            }

            battery.store.set(0);
            c.buffer().set(0);
            s.cycle(c);
            for (BlockPos part : parts) {
                h.assertValueEqual(
                        s.level.getBlockState(part).getValue(CentralControllerBlock.SCREEN),
                        ConsoleScreen.UNPOWERED,
                        "the screen without power, of " + part);
            }

            ServerPlayer p = player(s, 2, 1, 4);
            BlockHitResult hit =
                    new BlockHitResult(Vec3.atCenterOf(parts.get(3)), Direction.SOUTH, parts.get(3), false);
            p.gameMode.useItemOn(p, s.level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(
                    p.containerMenu instanceof CentralMenu m && m.pos().equals(c.getBlockPos()) && m.stillValid(p),
                    "using the upper right block opened " + p.containerMenu);
        });
    }

    /** A stone floor under a site, for a player to place on. */
    private static void floor(Site s) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 6; z++) {
                s.set(x, 0, z, Site.STONE);
            }
        }
    }

    /** Places what the player holds on the floor at (x, 0, z) of a site, as a right-click on its top. */
    private static void place(ServerPlayer p, Site s, int x, int z) {
        BlockPos on = s.abs(x, 0, z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
        p.gameMode.useItemOn(p, s.level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** How many console items lie dropped on a site; removes them. */
    private static int drops(Site s) {
        AABB box = new AABB(s.abs(0, 0, 0)).minmax(new AABB(s.abs(16, 6, 6))).inflate(2);
        int n = 0;
        for (ItemEntity e : s.level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.getItem().is(AtmosItems.CENTRAL_ATMOS_CONTROLLER.get())) {
                n += e.getItem().getCount();
            }
            e.discard();
        }
        return n;
    }

    // ---- CONT-116 ---------------------------------------------------------------------------

    /**
     * Of area air controllers in its range at other heights, out of it, and one a second central
     * controller has claimed, it claims only the unclaimed ones in range. Range is a square of chunks:
     * 10 chunks east or north is in, 11 is out (CONT-116).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_116_it_claims_only_unclaimed_controllers_in_range(GameTestHelper h) {
        Site s = new Site(h, 4301);
        List<BlockPos> placed = new ArrayList<>();
        run(h, s, placed, () -> {
            CentralControllerBlockEntity first = central(s, 2, 1, 2);
            CentralControllerBlockEntity second = central(s, 12, 1, 2);
            AreaAirControllerBlockEntity taken = controllerOn(h, s, placed, 8, 1, 5, Site.STONE);
            s.cycle(second);
            h.assertValueEqual(owner(s, taken), second.getBlockPos(), "the second central controller's claim");

            AreaAirControllerBlockEntity near = controllerOn(h, s, placed, 5, 1, 5, Site.STONE);
            AreaAirControllerBlockEntity east = controllerOn(h, s, placed, 10 * 16 + 3, 60, 5, Site.STONE);
            AreaAirControllerBlockEntity north = controllerOn(h, s, placed, 5, -40, 5 - 10 * 16, Site.STONE);
            AreaAirControllerBlockEntity eastOut = controllerOn(h, s, placed, 11 * 16 + 3, 1, 5, Site.STONE);
            AreaAirControllerBlockEntity northOut = controllerOn(h, s, placed, 5, 1, 5 - 11 * 16, Site.STONE);
            s.cycle(first);

            h.assertValueEqual(
                    claims(s).claimedBy(first.getBlockPos()),
                    List.of(near.getBlockPos(), east.getBlockPos(), north.getBlockPos()),
                    "the first central controller's claims");
            h.assertValueEqual(owner(s, taken), second.getBlockPos(), "the controller the second one had");
            h.assertTrue(owner(s, eastOut) == null, "a controller 11 chunks east was claimed");
            h.assertTrue(owner(s, northOut) == null, "a controller 11 chunks north was claimed");
            h.assertValueEqual(
                    claims(s).claimedBy(second.getBlockPos()),
                    List.of(taken.getBlockPos()),
                    "the second central controller's claims");
        });
    }

    // ---- CONT-117 ---------------------------------------------------------------------------

    /**
     * Its range starts at the server's maximum and can't be raised past it, even when the maximum is
     * lowered. Lowering it in the GUI releases the controller it no longer reaches, which it claims
     * again once the range is back (CONT-117).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_117_lowering_the_range_releases_what_it_no_longer_reaches(GameTestHelper h) {
        Site s = new Site(h, 4302);
        List<BlockPos> placed = new ArrayList<>();
        run(h, s, placed, () -> {
            CentralControllerBlockEntity c = central(s, 2, 1, 2);
            AreaAirControllerBlockEntity near = controllerOn(h, s, placed, 5, 1, 5, Site.STONE);
            AreaAirControllerBlockEntity far = controllerOn(h, s, placed, 10 * 16 + 3, 1, 5, Site.STONE);
            s.cycle(c);
            h.assertValueEqual(c.range(), 10, "the range when placed");
            h.assertValueEqual(owner(s, far), c.getBlockPos(), "the controller 10 chunks away");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            CentralMenu menu = new CentralMenu(0, player.getInventory(), c);
            h.assertFalse(menu.clickMenuButton(player, CentralMenu.RANGE_UP), "raised past the maximum");
            h.assertValueEqual(c.range(), 10, "the range after raising it at the maximum");
            h.assertTrue(menu.clickMenuButton(player, CentralMenu.RANGE_DOWN), "the range wasn't lowered");
            h.assertValueEqual(c.range(), 9, "the lowered range");
            h.assertTrue(owner(s, far) == null, "the controller out of range is still claimed");
            h.assertValueEqual(owner(s, near), c.getBlockPos(), "the controller in range");
            s.cycle(c);
            h.assertTrue(owner(s, far) == null, "the controller out of range was claimed again");
            h.assertFalse(c.setRange(0), "a range of 0 was set");
            h.assertFalse(c.setRange(11), "a range past the maximum was set");

            int max = AtmosServerConfig.get(AtmosServerConfig.CENTRAL_MAX_RANGE);
            try {
                AtmosServerConfig.CENTRAL_MAX_RANGE.set(4);
                h.assertValueEqual(c.range(), 4, "the range under a lower maximum");
                h.assertFalse(menu.clickMenuButton(player, CentralMenu.RANGE_UP), "raised past a lower maximum");
            } finally {
                AtmosServerConfig.CENTRAL_MAX_RANGE.set(max);
            }
            h.assertTrue(menu.clickMenuButton(player, CentralMenu.RANGE_UP), "the range wasn't raised");
            h.assertValueEqual(c.range(), 10, "back at the maximum");
            s.cycle(c);
            h.assertValueEqual(owner(s, far), c.getBlockPos(), "the controller back in range");
        });
    }

    // ---- CONT-118 ---------------------------------------------------------------------------

    /**
     * The multitool moves an area air controller from one central controller to another in range.
     * From then the new one pays what it needs, and the old one only its own draw, and its GUI names
     * the new one (CONT-118).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_118_the_multitool_moves_a_controller_to_another_central_controller(GameTestHelper h) {
        Site s = new Site(h, 4303);
        run(h, s, new ArrayList<>(), () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            TestParts.Vent vent = vent(s, 2);
            CentralControllerBlockEntity first = central(s, 8, 1, 2);
            CentralControllerBlockEntity second = central(s, 12, 1, 2);
            first.buffer().set(10_000);
            second.buffer().set(10_000);
            s.cycle(first, second);
            h.assertValueEqual(owner(s, c), first.getBlockPos(), "the first central controller's claim");

            // Used on the controller, then on the second console's upper right block.
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            use(player, tool, c.getBlockPos());
            h.assertValueEqual(
                    MultitoolItem.selected(tool, s.level).orElse(null), c.getBlockPos(), "the multitool's selection");
            use(player, tool, second.parts().get(3));
            h.assertValueEqual(owner(s, c), second.getBlockPos(), "the claim after the move");
            h.assertValueEqual(
                    key(MultitoolItem.move(tool, s.level, second)),
                    "item.zas_atmos.multitool.moved",
                    "what the multitool says");
            h.assertValueEqual(claims(s).claimedBy(first.getBlockPos()), List.of(), "the first's claims");

            // The controller has no FE block next to it, so it runs on what its central controller pays.
            long paid = 0;
            int steps = 0;
            for (; steps < 6 && !vent.power().isPowered(); steps++) {
                paid += Math.max(0, c.demand() - c.buffer().getEnergyStored());
                s.cycle(first, second, c);
            }
            h.assertTrue(vent.power().isPowered(), "the vent has no power");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't its controller's");
            h.assertTrue(paid >= ventBill(), "paid " + paid + ", less than the controller and its vent cost");
            h.assertValueEqual(
                    first.buffer().getEnergyStored(), 10_000 - (1 + steps) * own(), "the first: only its own draw");
            h.assertValueEqual(
                    second.buffer().getEnergyStored(),
                    (int) (10_000 - (1 + steps) * own() - paid),
                    "the second: its own draw and what the controller needed");

            ControllerSync sync =
                    new ControllerMenu(0, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), c).sync();
            h.assertValueEqual(sync.central(), second.getBlockPos(), "the central controller its GUI names");
        });
    }

    // ---- CONT-119 ---------------------------------------------------------------------------

    /** Breaking a central controller releases its area air controllers, and a second one in range claims them (CONT-119). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_119_breaking_a_central_controller_frees_its_controllers_for_another(GameTestHelper h) {
        Site s = new Site(h, 4304);
        List<BlockPos> placed = new ArrayList<>();
        run(h, s, placed, () -> {
            CentralControllerBlockEntity first = central(s, 2, 1, 2);
            AreaAirControllerBlockEntity a = controllerOn(h, s, placed, 6, 1, 5, Site.STONE);
            AreaAirControllerBlockEntity b = controllerOn(h, s, placed, 9, 1, 5, Site.STONE);
            s.cycle(first);
            CentralControllerBlockEntity second = central(s, 12, 1, 2);
            s.cycle(second);
            List<BlockPos> both = List.of(a.getBlockPos(), b.getBlockPos());
            h.assertValueEqual(claims(s).claimedBy(first.getBlockPos()), both, "the first's claims");
            h.assertValueEqual(claims(s).claimedBy(second.getBlockPos()), List.of(), "the second's claims beside it");

            h.assertTrue(s.level.destroyBlock(first.getBlockPos(), false), "the first wasn't broken");
            h.assertTrue(owner(s, a) == null && owner(s, b) == null, "claims left after breaking it");
            s.cycle(second);
            h.assertValueEqual(claims(s).claimedBy(second.getBlockPos()), both, "the second's claims after");
        });
    }

    // ---- CONT-120 ---------------------------------------------------------------------------

    /**
     * An area air controller with no FE block next to it runs its vent on what a central controller
     * pays it: each atmos tick, what its last step needed less what its buffer holds (CONT-120).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_120_a_controller_with_no_fe_block_runs_its_vent_from_the_central_one(GameTestHelper h) {
        Site s = new Site(h, 4305);
        run(h, s, new ArrayList<>(), () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            TestParts.Vent vent = vent(s, 2);
            for (Direction d : Direction.values()) {
                h.assertTrue(
                        s.level.getCapability(
                                        Capabilities.EnergyStorage.BLOCK,
                                        c.getBlockPos().relative(d),
                                        d.getOpposite())
                                == null,
                        "an FE block next to the controller, " + d);
            }
            CentralControllerBlockEntity central = central(s, 8, 1, 2);
            central.buffer().set(10_000);

            long paid = 0;
            int steps = 0;
            for (; steps < 6 && !vent.power().isPowered(); steps++) {
                paid += Math.max(0, c.demand() - c.buffer().getEnergyStored());
                s.cycle(central, c);
            }
            h.assertTrue(vent.power().isPowered(), "the vent has no power");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't its controller's");
            h.assertTrue(c.powered(), "the controller has no power");
            h.assertTrue(paid >= ventBill(), "paid " + paid + ", less than the controller and its vent cost");
            h.assertValueEqual(
                    central.buffer().getEnergyStored(),
                    (int) (10_000 - steps * own() - paid),
                    "the central controller's buffer");
            h.assertValueEqual(
                    c.buffer().getEnergyStored(), 0, "the controller's buffer, which spent what it was paid");
        });
    }

    /**
     * A central controller with a generator next to it and FE in its buffer runs an area air
     * controller with no FE block next to it. When the generator goes and both buffers are spent, the
     * central controller has no power and pays nothing, so the area air controller has none either
     * (CONT-060, CONT-115, CONT-120). Its parts then stop as their leases run out, as any unpowered
     * controller's do ({@code ScrubberStationTests}). The generator's FE alone wouldn't last: what a
     * central controller takes in and doesn't spend goes to its area air controllers' buffers.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_060_a_central_controller_stops_when_its_power_is_cut(GameTestHelper h) {
        Site s = new Site(h, 4315);
        run(h, s, new ArrayList<>(), () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            TestParts.Vent vent = vent(s, 2);
            CentralControllerBlockEntity central = central(s, 8, 1, 2);
            GeneratorBlockEntity g =
                    s.place(9, 1, 2, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            g.energy().set(8_000);
            central.buffer().set(10_000);
            for (int i = 0; i < 6 && !vent.power().isPowered(); i++) {
                s.cycle(central, c);
            }
            h.assertValueEqual(g.energy().getEnergyStored(), 0, "the generator, which the central controller drew on");
            h.assertTrue(central.powered(), "the central controller has no power");
            h.assertTrue(c.powered(), "the controller has no power");
            h.assertTrue(vent.power().source() == c, "the vent's power isn't its controller's");

            // Cut the power: the generator goes, and both buffers are spent.
            s.set(9, 1, 2, Site.AIR);
            central.buffer().set(0);
            c.buffer().set(0);
            s.cycle(central, c);
            h.assertFalse(central.powered(), "the central controller has power with no generator");
            h.assertValueEqual(central.status(), ControllerStatus.UNPOWERED, "the central controller's status");
            h.assertFalse(c.powered(), "the controller has power with its central controller cut");
            h.assertValueEqual(c.status(), ControllerStatus.UNPOWERED, "the controller's status");
            h.assertValueEqual(c.buffer().getEnergyStored(), 0, "the controller's buffer, paid nothing");
        });
    }

    /**
     * An area air controller with a generator next to it takes what the generator holds first, and
     * the central controller pays only the rest of what its last step needed, from its own buffer,
     * which tops up nothing (CONT-120).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_120_a_controller_with_a_generator_draws_only_its_shortfall(GameTestHelper h) {
        Site s = new Site(h, 4306);
        run(h, s, new ArrayList<>(), () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity c = s.controller(2);
            GeneratorBlockEntity g =
                    s.place(2, 1, 1, AtmosBlocks.GENERATOR.get().defaultBlockState(), GeneratorBlockEntity.class);
            TestParts.Vent vent = vent(s, 2);
            c.buffer().set(10_000);
            formZone(h, s, c, vent);
            int bill = ventBill();
            h.assertValueEqual(c.demand(), (long) bill, "what its last step needed");

            c.buffer().set(0);
            g.energy().set(bill / 2);
            CentralControllerBlockEntity central = central(s, 8, 1, 2);
            central.buffer().set(10_000);
            s.cycle(central);
            h.assertValueEqual(g.energy().getEnergyStored(), 0, "the generator, which pays first");
            h.assertValueEqual(
                    central.buffer().getEnergyStored(),
                    10_000 - own() - (bill - bill / 2),
                    "the central controller, which paid the shortfall");
            h.assertValueEqual(c.buffer().getEnergyStored(), bill, "the controller's buffer: what it needs, no more");
        });
    }

    /**
     * What a central controller takes in and doesn't spend is split evenly among its area air
     * controllers' buffers, after their shortfalls, and its own full buffer stays as it was. Nothing
     * is lost: the FE block's FE is all in one of the three buffers or paid as the draw (CONT-120).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_120_spare_intake_fills_the_controllers_buffers_evenly(GameTestHelper h) {
        Site s = new Site(h, 4307);
        run(h, s, new ArrayList<>(), () -> {
            s.room(0, 4);
            AreaAirControllerBlockEntity a = s.controller(2);
            TestParts.Vent ventA = vent(s, 2);
            s.room(6, 10);
            AreaAirControllerBlockEntity b = s.controller(8);
            TestParts.Vent ventB = vent(s, 8);
            a.buffer().set(10_000);
            b.buffer().set(10_000);
            formZone(h, s, a, ventA);
            formZone(h, s, b, ventB);
            a.buffer().set(0);
            b.buffer().set(0);
            int bill = ventBill();

            CentralControllerBlockEntity central = central(s, 13, 1, 2);
            s.set(14, 1, 2, TestParts.battery().defaultBlockState());
            TestParts.Battery battery = s.entity(14, 1, 2, TestParts.Battery.class);
            central.buffer().set(100_000);
            int spare = 4_000;
            battery.store.set(own() + 2 * bill + spare);
            s.cycle(central);
            h.assertValueEqual(battery.store.getEnergyStored(), 0, "left in the FE block");
            h.assertValueEqual(a.buffer().getEnergyStored(), bill + spare / 2, "the first controller's buffer");
            h.assertValueEqual(b.buffer().getEnergyStored(), bill + spare / 2, "the second controller's buffer");
            h.assertValueEqual(central.buffer().getEnergyStored(), 100_000, "the central controller's buffer");
        });
    }

    // ---- CONT-121 ---------------------------------------------------------------------------

    /**
     * A central controller with its own draw and one zone's bill each atmos tick, for a Critical and a
     * Low zone, pays the Critical one, though it claimed the Low one first. The Critical zone's vent
     * runs throughout; the Low zone's runs on its own buffer and stops once that is empty (CONT-121).
     */
    @GameTest(template = "empty", batch = LIVE, timeoutTicks = TIMEOUT)
    public static void cont_121_a_starved_central_controller_keeps_the_critical_zone_running(GameTestHelper h) {
        Site s = new Site(h, 4310);
        Runnable undo = hold(s, new ArrayList<>());
        s.room(0, 4);
        AreaAirControllerBlockEntity low = s.controller(2);
        TestParts.Vent lowVent = vent(s, 2);
        s.room(6, 10);
        AreaAirControllerBlockEntity critical = s.controller(8);
        TestParts.Vent criticalVent = vent(s, 8);
        low.buffer().set(10_000);
        critical.buffer().set(10_000);
        formZone(h, s, low, lowVent);
        formZone(h, s, critical, criticalVent);
        CentralControllerBlockEntity central = central(s, 13, 1, 2);
        s.cycle(central);
        h.assertValueEqual(
                claims(s).claimedBy(central.getBlockPos()),
                List.of(low.getBlockPos(), critical.getBlockPos()),
                "claimed in the order they joined");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CentralMenu menu = new CentralMenu(0, player.getInventory(), central);
        menu.sync();
        priority(h, menu, player, low, ZonePriority.LOW);
        priority(h, menu, player, critical, ZonePriority.CRITICAL);
        h.assertValueEqual(claims(s).priority(low.getBlockPos()), ZonePriority.LOW, "the first zone's priority");
        h.assertValueEqual(
                claims(s).priority(critical.getBlockPos()), ZonePriority.CRITICAL, "the second zone's priority");

        int bill = ventBill();
        Runnable starve = () -> central.buffer().set(own() + bill);
        low.buffer().set(4 * bill);
        critical.buffer().set(bill);
        boolean[] lowRan = new boolean[1];
        int[] criticalOff = new int[1];
        int[] lowBufferWhenOff = {-1};
        GameTestSequence seq = h.startSequence()
                .thenExecuteFor(SETTLE, starve)
                .thenExecute(() -> {
                    lowRan[0] = lowVent.power().isPowered();
                    // Forming the zones leased the vents, so the first step paid less than a bill.
                    // Start again on whole bills, so the Low zone's buffer runs out exactly.
                    low.buffer().set(4 * bill);
                })
                .thenExecuteFor(8 * AtmosTicks.PERIOD, () -> {
                    starve.run();
                    if (!criticalVent.power().isPowered()) {
                        criticalOff[0]++;
                    }
                    if (lowBufferWhenOff[0] < 0 && !lowVent.power().isPowered()) {
                        lowBufferWhenOff[0] = low.buffer().getEnergyStored();
                    }
                });
        finish(seq, undo, () -> {
            h.assertTrue(lowRan[0], "the Low zone's vent had no power while its buffer lasted");
            h.assertValueEqual(criticalOff[0], 0, "game ticks the Critical zone's vent had no power");
            h.assertFalse(lowVent.power().isPowered(), "the Low zone's vent still runs");
            h.assertValueEqual(lowBufferWhenOff[0], 0, "the Low zone's buffer when its vent stopped");
            h.assertFalse(low.powered(), "the Low zone's controller has power");
            h.assertTrue(critical.powered(), "the Critical zone's controller has no power");
        });
    }

    // ---- CONT-122 ---------------------------------------------------------------------------

    /**
     * Its chunk and the 8 around it load and tick with no player near and nothing else holding them,
     * and stop when it is removed. Placed again, they load again, and stop when {@code force_load}
     * turns false (CONT-122).
     */
    @GameTest(template = "empty", batch = CHUNKS, timeoutTicks = TIMEOUT)
    public static void cont_122_it_keeps_its_3x3_chunks_loaded_until_removed_or_turned_off(GameTestHelper h) {
        Site s = new Site(h, 4312);
        Runnable undo = () -> clearSite(s);
        CLEANUP.add(undo);
        clearSite(s);
        ChunkPos mid = new ChunkPos(s.abs(2, 1, 2));
        CentralControllerBlockEntity[] c = {central(s, 2, 1, 2)};
        c[0].step();
        h.assertTrue(c[0].holdingChunks(), "it doesn't hold its chunks");
        boolean before = AtmosServerConfig.get(AtmosServerConfig.CENTRAL_FORCE_LOAD);
        Runnable restore = () -> AtmosServerConfig.CENTRAL_FORCE_LOAD.set(before);
        GameTestSequence seq = h.startSequence()
                .thenWaitUntil(() -> loaded(h, s, mid, true))
                .thenIdle(3 * AtmosTicks.PERIOD)
                .thenExecute(() -> loaded(h, s, mid, true))
                .thenExecute(() -> s.set(2, 1, 2, Site.AIR))
                .thenWaitUntil(() -> loaded(h, s, mid, false))
                .thenExecute(() -> {
                    c[0] = central(s, 2, 1, 2);
                    c[0].step();
                })
                .thenWaitUntil(() -> loaded(h, s, mid, true))
                .thenExecute(() -> {
                    CLEANUP.add(0, restore);
                    AtmosServerConfig.CENTRAL_FORCE_LOAD.set(false);
                })
                .thenWaitUntil(() -> {
                    h.assertFalse(c[0].holdingChunks(), "it holds its chunks with force_load off");
                    loaded(h, s, mid, false);
                });
        seq.thenExecute(() -> {
                    CLEANUP.remove(restore);
                    CLEANUP.remove(undo);
                    restore.run();
                    undo.run();
                })
                .thenSucceed();
    }

    /** Asserts that every chunk of the 3 × 3 around {@code mid} is loaded and ticking, or that none ticks. */
    private static void loaded(GameTestHelper h, Site s, ChunkPos mid, boolean on) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = mid.x + dx;
                int z = mid.z + dz;
                boolean ticking = s.level.shouldTickBlocksAt(ChunkPos.asLong(x, z));
                if (on) {
                    h.assertTrue(
                            ticking && s.level.getChunkSource().hasChunk(x, z),
                            "chunk " + x + ", " + z + " isn't loaded and ticking");
                } else {
                    h.assertFalse(ticking, "chunk " + x + ", " + z + " still ticks");
                }
            }
        }
    }

    // ---- CONT-124 ---------------------------------------------------------------------------

    /**
     * From the central controller's GUI, a player opens a claimed controller's own GUI and changes
     * its mode, which takes effect; a press from out of reach of the central controller is refused,
     * as one out of reach of the controller is; and a controller whose chunk has unloaded can't be
     * opened, and is listed out of contact with its last readings (CONT-124, CONT-074).
     */
    @GameTest(template = "empty", batch = LIVE, timeoutTicks = TIMEOUT)
    public static void cont_124_a_claimed_controller_is_run_from_the_central_one_unless_unloaded(GameTestHelper h) {
        Site s = new Site(h, 4314);
        List<BlockPos> placed = new ArrayList<>();
        Runnable undo = hold(s, placed);
        s.room(0, 4);
        s.set(2, 2, 0, TestParts.battery().defaultBlockState());
        AreaAirControllerBlockEntity near = s.controller(2);
        TestParts.Vent vent = vent(s, 2);
        formZone(h, s, near, vent);
        h.assertValueEqual(vent.last().mode(), VentOrder.Mode.BLOW, "the vent under Scrubbing");

        // Six chunks east, out of what the test and the central controller hold, so its chunk
        // unloads once its own ticket goes. Until then it ticks, so it is in contact, and an FE block
        // behind it gives it power, so it has readings first.
        int farX = (s.origin.getX() >> 4) + 6;
        int farZ = s.origin.getZ() >> 4;
        Runnable unforce = () -> s.level.setChunkForced(farX, farZ, false);
        CLEANUP.add(0, unforce);
        s.level.setChunkForced(farX, farZ, true);
        AreaAirControllerBlockEntity far =
                controllerOn(h, s, placed, 6 * 16 + 2, 1, 5, TestParts.battery().defaultBlockState());
        // What the steps after the chunks tick leave for the last checks.
        class Run {
            ServerPlayer p;
            CentralClaims.Readings farLast;
        }
        Run run = new Run();

        GameTestSequence seq = h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(near.stepping(), "the site doesn't tick yet");
                    h.assertTrue(far.stepping(), "the far controller's chunk doesn't tick yet");
                })
                .thenExecute(() -> {
                    s.cycle(far);
                    CentralControllerBlockEntity central = central(s, 8, 1, 2);
                    s.cycle(central);
                    h.assertValueEqual(
                            claims(s).claimedBy(central.getBlockPos()),
                            List.of(near.getBlockPos(), far.getBlockPos()),
                            "its claims");
                    run.farLast = claims(s).last(far.getBlockPos());
                    h.assertTrue(Double.isFinite(run.farLast.pressure()), "no reading from the far controller");

                    ServerPlayer p = player(s, 8, 1, 4);
                    run.p = p;
                    BlockHitResult hit = new BlockHitResult(
                            Vec3.atCenterOf(central.getBlockPos()), Direction.SOUTH, central.getBlockPos(), false);
                    p.gameMode.useItemOn(p, s.level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                    h.assertTrue(p.containerMenu instanceof CentralMenu, "using it opened " + p.containerMenu);
                    CentralMenu menu = (CentralMenu) p.containerMenu;
                    menu.sync();
                    press(p, CentralMenu.OPEN + menu.row(near.getBlockPos()).orElseThrow());
                    h.assertTrue(
                            p.containerMenu instanceof ControllerMenu cm
                                    && cm.remote()
                                    && cm.pos().equals(near.getBlockPos()),
                            "the row's button opened " + p.containerMenu);
                    press(p, ControllerMode.PANIC.ordinal());
                    h.assertValueEqual(near.mode(), ControllerMode.PANIC, "the mode set from the central controller");
                    s.cycle(near);
                    h.assertValueEqual(vent.last().mode(), VentOrder.Mode.OFF, "the vent under Panic");

                    p.moveTo(Vec3.atBottomCenterOf(s.abs(8, 1, 40)));
                    press(p, ControllerMode.FILL.ordinal());
                    h.assertValueEqual(near.mode(), ControllerMode.PANIC, "the mode after a press from out of reach");
                    p.moveTo(Vec3.atBottomCenterOf(s.abs(8, 1, 4)));
                    press(p, ControllerMenu.BACK);
                    h.assertTrue(p.containerMenu instanceof CentralMenu, "going back opened " + p.containerMenu);
                })
                .thenExecute(() -> {
                    CLEANUP.remove(unforce);
                    unforce.run();
                })
                .thenWaitUntil(() ->
                        h.assertFalse(s.level.isLoaded(far.getBlockPos()), "the far controller's chunk is loaded"));
        finish(seq, undo, () -> {
            ServerPlayer p = run.p;
            CentralMenu list = (CentralMenu) p.containerMenu;
            CentralSync sync = list.sync();
            int row = list.row(far.getBlockPos())
                    .orElseThrow(() -> new GameTestAssertException("the far controller isn't listed"));
            CentralSync.Row r = sync.rows().get(row);
            h.assertFalse(r.contact(), "the unloaded controller is in contact");
            s.close(r.pressure(), run.farLast.pressure(), 1e-9, "its last pressure");
            s.close(r.temperature(), run.farLast.temperature(), 1e-9, "its last temperature");
            press(p, CentralMenu.OPEN + row);
            h.assertTrue(p.containerMenu == list, "the unloaded controller's GUI opened: " + p.containerMenu);
        });
    }

    // ---- CONT-123 ---------------------------------------------------------------------------

    /**
     * A claimed controller in a chunk loaded in full that doesn't tick, at the edge of what is
     * loaded, waits without stepping (architecture §11.2), so it is out of contact, as an unloaded
     * one is: its row shows its last readings, the central controller feeds it nothing, and its GUI
     * won't open from there, and closes there if it was open (CONT-123, CONT-124).
     */
    @GameTest(template = "empty", batch = LIVE, timeoutTicks = TIMEOUT)
    public static void cont_123_a_claimed_controller_in_a_chunk_that_doesnt_tick_is_out_of_contact(GameTestHelper h) {
        Site s = new Site(h, 4316);
        List<BlockPos> placed = new ArrayList<>();
        Runnable undo = hold(s, placed);
        // Three chunks east: two from the chunks the test and the central controller hold, so once
        // its own ticket goes it stays loaded in full, at the edge, but doesn't tick.
        int edgeX = (s.origin.getX() >> 4) + 3;
        int edgeZ = s.origin.getZ() >> 4;
        Runnable unforce = () -> s.level.setChunkForced(edgeX, edgeZ, false);
        CLEANUP.add(0, unforce);
        s.level.setChunkForced(edgeX, edgeZ, true);
        AreaAirControllerBlockEntity edge =
                controllerOn(h, s, placed, 3 * 16 + 2, 1, 5, TestParts.battery().defaultBlockState());
        // What the steps while it ticks leave for the last checks.
        class Run {
            CentralControllerBlockEntity central;
            CentralClaims.Readings last;
            ServerPlayer p;
            ControllerMenu remote;
            CentralMenu list;
        }
        Run run = new Run();

        GameTestSequence seq = h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(edge.stepping(), "the controller's chunk doesn't tick yet"))
                .thenExecute(() -> {
                    s.cycle(edge);
                    CentralControllerBlockEntity central = central(s, 8, 1, 2);
                    run.central = central;
                    s.cycle(central);
                    h.assertValueEqual(
                            claims(s).claimedBy(central.getBlockPos()), List.of(edge.getBlockPos()), "its claims");
                    run.last = claims(s).last(edge.getBlockPos());
                    h.assertTrue(
                            Double.isFinite(run.last.pressure()), "no reading from the controller while it ticked");

                    ServerPlayer p = player(s, 8, 1, 4);
                    run.p = p;
                    BlockHitResult hit = new BlockHitResult(
                            Vec3.atCenterOf(central.getBlockPos()), Direction.SOUTH, central.getBlockPos(), false);
                    p.gameMode.useItemOn(p, s.level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                    h.assertTrue(p.containerMenu instanceof CentralMenu, "using it opened " + p.containerMenu);
                    CentralMenu opened = (CentralMenu) p.containerMenu;
                    CentralSync first = opened.sync();
                    int before = opened.row(edge.getBlockPos())
                            .orElseThrow(() -> new GameTestAssertException("the controller isn't listed"));
                    h.assertTrue(first.rows().get(before).contact(), "the ticking controller is out of contact");
                    press(p, CentralMenu.OPEN + before);
                    h.assertTrue(
                            p.containerMenu instanceof ControllerMenu cm
                                    && cm.remote()
                                    && cm.pos().equals(edge.getBlockPos()),
                            "the row's button opened " + p.containerMenu);
                    run.remote = (ControllerMenu) p.containerMenu;
                    h.assertTrue(run.remote.stillValid(p), "its GUI from the central controller closes while it steps");
                    press(p, ControllerMenu.BACK);
                    h.assertTrue(p.containerMenu instanceof CentralMenu, "going back opened " + p.containerMenu);
                    run.list = (CentralMenu) p.containerMenu;
                })
                .thenExecute(() -> {
                    CLEANUP.remove(unforce);
                    unforce.run();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(s.level.isLoaded(edge.getBlockPos()), "the controller's chunk left full status");
                    h.assertFalse(
                            LoadedBlocks.ticking(s.level, edge.getBlockPos()), "the controller's chunk still ticks");
                });
        finish(seq, undo, () -> {
            ServerPlayer p = run.p;
            CentralMenu list = run.list;
            CentralClaims.Readings last = run.last;
            edge.buffer().set(0);
            s.cycles(2, run.central);
            h.assertFalse(edge.stepping(), "the controller at the edge steps");
            h.assertValueEqual(edge.buffer().getEnergyStored(), 0, "the FE the waiting controller was given");
            h.assertFalse(run.remote.stillValid(p), "its GUI from the central controller stays open while it waits");
            CentralSync sync = list.sync();
            int row = list.row(edge.getBlockPos())
                    .orElseThrow(() -> new GameTestAssertException("the controller isn't listed"));
            CentralSync.Row r = sync.rows().get(row);
            h.assertFalse(r.contact(), "the waiting controller is in contact");
            s.close(r.pressure(), last.pressure(), 1e-9, "its last pressure");
            s.close(r.temperature(), last.temperature(), 1e-9, "its last temperature");
            press(p, CentralMenu.OPEN + row);
            h.assertTrue(p.containerMenu == list, "the waiting controller's GUI opened: " + p.containerMenu);
        });
    }
}
