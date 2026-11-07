package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.analyzer.AnalyzerMenu;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync.Kind;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.command.ZasCommands;
import io.github.west3436.zas.hud.HudLevels;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The atmos analyzer (CONT-034): what the server reads for each kind of target, checked against what
 * {@code /zas zone at}, {@code /zas at} or the target's own gas say; that a player with no rights can't
 * read a block beyond their reach through it; and that its window syncs at most four times a second,
 * and only when what it shows changes.
 *
 * <p>The player is one the syncs treat as a client, without operator rights, whose packets are kept.
 * Nothing ticks it but the test: a test that needs its window to read again ticks it once a game tick,
 * as its connection would.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnalyzerTests {
    private static final String BATCH = "zas_atmos_analyzer";

    private AnalyzerTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** A player without operator rights, standing at a block, holding an analyzer. */
    private static ListeningPlayer player(Area a, int x, int y, int z) {
        ListeningPlayer l = ListeningPlayer.client(a.level, Vec3.atBottomCenterOf(a.abs(x, y, z)), false);
        l.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.ATMOS_ANALYZER.get()));
        a.h.assertFalse(l.player().hasPermissions(2), "the player has operator rights");
        return l;
    }

    /** Uses the analyzer on nothing, as a right-click into the air does. */
    private static void useOnNothing(ServerPlayer p) {
        p.gameMode.useItem(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /** Uses the analyzer on a block's face at a point, as a right-click on the block does. */
    private static InteractionResult useOn(ServerPlayer p, BlockPos pos, Direction face, Vec3 at) {
        return p.gameMode.useItemOn(
                p,
                p.serverLevel(),
                p.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(at, face, pos, false));
    }

    /** The middle of a block's face. */
    private static Vec3 faceCentre(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    /** The readings the player was sent, in order. */
    private static List<AnalyzerSync> readings(ListeningPlayer l) {
        return l.payloads(AnalyzerSync.class);
    }

    private static AnalyzerSync last(Area a, ListeningPlayer l) {
        List<AnalyzerSync> all = readings(l);
        a.h.assertFalse(all.isEmpty(), "the player was sent no reading");
        return all.get(all.size() - 1);
    }

    private static AnalyzerMenu menu(Area a, ListeningPlayer l) {
        a.h.assertTrue(
                l.player().containerMenu instanceof AnalyzerMenu, "the open window is " + l.player().containerMenu);
        return (AnalyzerMenu) l.player().containerMenu;
    }

    private static void button(ServerPlayer p, int containerId, int id) {
        p.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(containerId, id));
    }

    /** Collects what a command sends to its source. */
    private static final class Capture implements CommandSource {
        final List<Component> messages = new CopyOnWriteArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (Component m : messages) {
                b.append(m.getString()).append('\n');
            }
            return b.toString();
        }
    }

    /** What an operator's command says, run as if by the player: {@code /zas at} reads their eyes. */
    private static String command(ServerPlayer p, String command) {
        Capture c = new Capture();
        CommandSourceStack source = new CommandSourceStack(
                c, p.position(), Vec2.ZERO, p.serverLevel(), 4, "zas_test", Component.literal("zas_test"), p.server, p);
        try {
            p.server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " failed: " + e.getMessage());
        }
        return c.text();
    }

    private static String coords(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /**
     * Fails unless a command's lines say what a reading's gas holds: its pressure and temperature, and
     * each gas's moles and partial pressure, to the command's six digits, and no other gas.
     */
    private static void says(Area a, String out, GasMixture gas, boolean zone) {
        String values = "  " + ZasCommands.num(gas.pressure()) + " kPa, " + ZasCommands.num(gas.temperature()) + " K"
                + (zone ? ", " + ZasCommands.num(gas.totalMoles()) + " mol\n" : "\n");
        a.h.assertTrue(out.contains(values), "the reading's " + values.strip() + " isn't in:\n" + out);
        for (Map.Entry<ResourceLocation, Double> e : gas.moles().entrySet()) {
            String line = " (" + e.getKey() + "): " + ZasCommands.num(e.getValue()) + " mol, "
                    + ZasCommands.num(gas.partialPressure(e.getKey())) + " kPa";
            a.h.assertTrue(out.contains(line), "the reading's" + line + " isn't in:\n" + out);
        }
        int lines = out.split(" mol, ", -1).length - 1;
        a.h.assertValueEqual(lines, gas.moles().size(), "gases the command lists");
    }

    /** Fails unless a reading's gas is the gas given, exactly. */
    private static void same(Area a, GasMixture read, GasMixture expected, String what) {
        a.h.assertValueEqual(read.moles().keySet(), expected.moles().keySet(), what + "'s gases");
        for (Map.Entry<ResourceLocation, Double> e : expected.moles().entrySet()) {
            a.close(read.moles(e.getKey()), e.getValue(), what + "'s " + e.getKey());
        }
        a.close(read.temperature(), expected.temperature(), what + "'s temperature");
        a.close(read.volume(), expected.volume(), what + "'s volume");
        a.close(read.pressure(), expected.pressure(), what + "'s pressure");
    }

    // ---- CONT-034: what each target reads -----------------------------------------------------------

    /**
     * Used on nothing, it reads the zone the player is in, as {@code /zas zone at} gives it; walking out
     * into the open air, the window follows within a quarter of a second, as {@code /zas at} gives it; and
     * back in, to the zone again.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_034_used_on_nothing_it_reads_where_the_player_is_and_follows_them(GameTestHelper h) {
        Area a = new Area(h, 3460);
        a.forceLoad(true);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(O2, 6.0, N2, 22.0));
        ZoneInfo zone = a.zone(2, 2, 2);
        ListeningPlayer l = player(a, 2, 1, 2);
        ServerPlayer p = l.player();

        useOnNothing(p);
        AnalyzerMenu menu = menu(a, l);
        AnalyzerSync first = last(a, l);
        h.assertValueEqual(readings(l).size(), 1, "readings sent on opening");
        h.assertValueEqual(first.containerId(), menu.containerId, "the reading's window");
        h.assertValueEqual(first.kind(), Kind.HERE, "what it reads");
        h.assertValueEqual(first.target(), Component.translatable("gui.zas_atmos.analyzer.here"), "its name");
        h.assertValueEqual(first.state(), AtmosphereState.ZONED, "the air's state");
        h.assertValueEqual(first.zone(), zone.id().value(), "the zone");
        String zoneAt = command(p, "zas zone at " + coords(BlockPos.containing(p.getEyePosition())));
        h.assertTrue(zoneAt.contains("Zone " + zone.id().value() + ":"), "/zas zone at:\n" + zoneAt);
        says(a, zoneAt, first.gas(), true);

        // Out through the roof into the open air, as if walking with the window open.
        h.onEachTick(p::tick);
        p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 7, 2)));
        h.startSequence()
                .thenExecuteAfter(AnalyzerMenu.SYNC_TICKS + 1, () -> {
                    AnalyzerSync out = last(a, l);
                    h.assertTrue(p.containerMenu == menu, "the window closed");
                    h.assertValueEqual(out.kind(), Kind.HERE, "what it reads outside");
                    h.assertValueEqual(out.state(), AtmosphereState.AMBIENT, "the air's state outside");
                    h.assertValueEqual(out.zone(), -1L, "the zone outside");
                    String at = command(p, "zas at");
                    h.assertTrue(at.contains("ambient, zone -"), "/zas at:\n" + at);
                    says(a, at, out.gas(), false);
                    p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 2)));
                })
                .thenExecuteAfter(AnalyzerMenu.SYNC_TICKS + 1, () -> {
                    AnalyzerSync back = last(a, l);
                    h.assertValueEqual(back.state(), AtmosphereState.ZONED, "the air's state back inside");
                    h.assertValueEqual(back.zone(), zone.id().value(), "the zone back inside");
                    p.closeContainer();
                    a.clear(-1, -1, 5, 5);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    /**
     * Used on a block, it reads the point just in front of the face clicked, as {@code /zas at point}
     * gives it: a wall's inside face reads the room's zone, and its outside face the open air.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_a_block_it_reads_the_air_in_front_of_the_face(GameTestHelper h) {
        Area a = new Area(h, 3461);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(O2, 5.0, N2, 19.0));
        ZoneInfo zone = a.zone(2, 2, 2);
        ListeningPlayer l = player(a, 2, 1, 2);
        ServerPlayer p = l.player();
        BlockPos wall = a.abs(4, 2, 2);

        Vec3 inside = faceCentre(wall, Direction.WEST);
        h.assertTrue(useOn(p, wall, Direction.WEST, inside).consumesAction(), "using it inside did nothing");
        AnalyzerSync in = last(a, l);
        Vec3 inPoint = inside.add(-0.01, 0, 0);
        h.assertValueEqual(in.kind(), Kind.POINT, "what the inside face reads");
        h.assertValueEqual(in.target(), Component.literal(coordsComma(a.abs(3, 2, 2))), "its name");
        h.assertValueEqual(in.state(), AtmosphereState.ZONED, "the inside's state");
        h.assertValueEqual(in.zone(), zone.id().value(), "the inside's zone");
        String atIn = command(p, "zas at point " + inPoint.x + " " + inPoint.y + " " + inPoint.z);
        h.assertTrue(atIn.contains("zoned, zone " + zone.id().value()), "/zas at point:\n" + atIn);
        says(a, atIn, in.gas(), false);
        p.closeContainer();

        Vec3 outside = faceCentre(wall, Direction.EAST);
        h.assertTrue(useOn(p, wall, Direction.EAST, outside).consumesAction(), "using it outside did nothing");
        AnalyzerSync out = last(a, l);
        Vec3 outPoint = outside.add(0.01, 0, 0);
        h.assertValueEqual(out.kind(), Kind.POINT, "what the outside face reads");
        h.assertValueEqual(out.target(), Component.literal(coordsComma(a.abs(5, 2, 2))), "its name");
        h.assertValueEqual(out.state(), AtmosphereState.AMBIENT, "the outside's state");
        String atOut = command(p, "zas at point " + outPoint.x + " " + outPoint.y + " " + outPoint.z);
        h.assertTrue(atOut.contains("ambient, zone -"), "/zas at point:\n" + atOut);
        says(a, atOut, out.gas(), false);
        p.closeContainer();
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    private static String coordsComma(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /**
     * Used on a pipe, it reads the pipe's network. Used on a pump, it reads the network on the port
     * whose face was clicked, or else on the port nearest the point clicked, and names the port as the
     * pump's window does.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_a_pipe_or_a_device_it_reads_that_network(GameTestHelper h) {
        Area a = new Area(h, 3462);
        a.clear(0, 0, 8, 6);
        a.place(2, 1, 2, GasDeviceTests.pipe());
        PipeNodeEntity pipe = a.entity(2, 1, 2);
        pipe.network().gas().add(new GasParcel(Map.of(N2, 30.0, O2, 4.0), 310));
        a.place(
                6,
                1,
                2,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        PressurePumpBlockEntity pump = a.entity(6, 1, 2);
        pump.port(0).network().gas().add(new GasParcel(Map.of(O2, 3.0), T));
        pump.port(1).network().gas().add(new GasParcel(Map.of(N2, 5.0, O2, 1.0), 280));
        ListeningPlayer l = player(a, 4, 1, 4);
        ServerPlayer p = l.player();
        Component pumpName = AtmosBlocks.PRESSURE_PUMP.get().getName();
        Component input = Component.translatable(
                "gui.zas_atmos.analyzer.port", pumpName, Component.translatable("gui.zas_atmos.device.input"));
        Component output = Component.translatable(
                "gui.zas_atmos.analyzer.port", pumpName, Component.translatable("gui.zas_atmos.device.output"));

        BlockPos pipePos = a.abs(2, 1, 2);
        h.assertTrue(
                useOn(p, pipePos, Direction.UP, faceCentre(pipePos, Direction.UP))
                        .consumesAction(),
                "using it on the pipe did nothing");
        AnalyzerSync onPipe = last(a, l);
        h.assertValueEqual(onPipe.kind(), Kind.NETWORK, "what the pipe reads");
        h.assertValueEqual(onPipe.target(), AtmosBlocks.PIPE.get().getName(), "its name");
        h.assertTrue(onPipe.state() == null, "a network has an air state");
        same(a, onPipe.gas(), pipe.network().gas().mixture(), "the pipe's reading");
        p.closeContainer();

        BlockPos pumpPos = a.abs(6, 1, 2);
        record Click(Direction face, Vec3 at, int port, Component name, String what) {}
        Vec3 top = faceCentre(pumpPos, Direction.UP);
        List<Click> clicks = new ArrayList<>(List.of(
                new Click(Direction.WEST, faceCentre(pumpPos, Direction.WEST), 0, input, "the input's face"),
                new Click(Direction.EAST, faceCentre(pumpPos, Direction.EAST), 1, output, "the output's face"),
                new Click(Direction.UP, top.add(-0.35, 0, 0), 0, input, "the top, near the input"),
                new Click(Direction.UP, top.add(0.35, 0, 0), 1, output, "the top, near the output")));
        for (Click c : clicks) {
            h.assertTrue(
                    useOn(p, pumpPos, c.face(), c.at()).consumesAction(), "using it on " + c.what() + " did nothing");
            AnalyzerSync s = last(a, l);
            h.assertValueEqual(s.kind(), Kind.NETWORK, "what " + c.what() + " reads");
            h.assertValueEqual(s.target(), c.name(), c.what() + "'s name");
            same(a, s.gas(), pump.port(c.port()).network().gas().mixture(), c.what() + "'s reading");
            p.closeContainer();
        }
        a.clear(0, 0, 8, 6);
        h.succeed();
    }

    /**
     * Used on a canister, it opens its own window, not the canister's, and reads the canister's own gas
     * as the canister's window shows it. The reading comes through its codec as it was sent.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_a_canister_it_reads_the_canisters_own_gas(GameTestHelper h) {
        Area a = new Area(h, 3463);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, 120.0, O2, 30.0), T));
        ListeningPlayer l = player(a, 4, 1, 2);
        ServerPlayer p = l.player();
        BlockPos pos = a.abs(2, 1, 2);

        h.assertTrue(
                useOn(p, pos, Direction.EAST, faceCentre(pos, Direction.EAST)).consumesAction(),
                "using it on the canister did nothing");
        menu(a, l);
        AnalyzerSync s = last(a, l);
        h.assertValueEqual(s.kind(), Kind.CANISTER, "what the canister reads");
        h.assertValueEqual(s.target(), AtmosBlocks.CANISTER.get().getName(), "its name");
        GasParcel share = can.share();
        same(a, s.gas(), new GasMixture(share.moles(), share.temperature(), can.nodeVolume()), "the reading");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
        AnalyzerSync.CODEC.encode(buf, s);
        AnalyzerSync decoded = AnalyzerSync.CODEC.decode(buf);
        h.assertValueEqual(buf.readableBytes(), 0, "bytes left after decoding");
        h.assertValueEqual(decoded.containerId(), s.containerId(), "the decoded window");
        h.assertTrue(decoded.showsSameAs(s), "the decoded reading shows something else: " + decoded);
        same(a, decoded.gas(), s.gas(), "the decoded reading");
        p.closeContainer();
        a.clear(0, 0, 6, 6);
        h.succeed();
    }

    /**
     * A tank in the other hand gets a tab: chosen, the window reads the tank as the tank's own contents
     * give it; the target's tab goes back to the target; and with the tank gone, the window goes back
     * to the target and refuses the tank's tab.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_034_a_tank_in_the_other_hand_has_a_tab_of_its_own(GameTestHelper h) {
        Area a = new Area(h, 3464);
        a.forceLoad(true);
        a.clear(0, 0, 4, 4);
        ListeningPlayer l = player(a, 2, 1, 2);
        ServerPlayer p = l.player();
        ItemStack tank = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        GasTankItem.setGas(tank, new GasParcel(Map.of(O2, 40.0, N2, 2.0), 290));
        p.setItemInHand(InteractionHand.OFF_HAND, tank);

        useOnNothing(p);
        AnalyzerMenu menu = menu(a, l);
        AnalyzerSync first = last(a, l);
        h.assertValueEqual(first.kind(), Kind.HERE, "what it reads first");
        h.assertValueEqual(first.tank(), Optional.of(tank.getHoverName()), "the tank's tab");
        button(p, menu.containerId, AnalyzerMenu.SHOW_TANK);
        h.assertTrue(menu.showingTank(), "the server didn't take the tank's tab");

        h.onEachTick(p::tick);
        h.startSequence()
                .thenExecuteAfter(AnalyzerMenu.SYNC_TICKS + 1, () -> {
                    AnalyzerSync s = last(a, l);
                    h.assertValueEqual(s.kind(), Kind.TANK, "what the tank's tab reads");
                    h.assertValueEqual(s.target(), first.target(), "the target's tab's name");
                    same(a, s.gas(), GasTankItem.contents(p.getOffhandItem()), "the tank's reading");
                    button(p, menu.containerId, AnalyzerMenu.SHOW_TARGET);
                })
                .thenExecuteAfter(AnalyzerMenu.SYNC_TICKS + 1, () -> {
                    h.assertValueEqual(last(a, l).kind(), Kind.HERE, "what the target's tab reads");
                    button(p, menu.containerId, AnalyzerMenu.SHOW_TANK);
                    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                })
                .thenExecuteAfter(AnalyzerMenu.SYNC_TICKS + 1, () -> {
                    AnalyzerSync s = last(a, l);
                    h.assertValueEqual(s.kind(), Kind.HERE, "what it reads with the tank gone");
                    h.assertTrue(s.tank().isEmpty(), "a tab for a tank that's gone");
                    h.assertFalse(menu.showingTank(), "the server still shows a tank that's gone");
                    h.assertFalse(menu.clickMenuButton(p, AnalyzerMenu.SHOW_TANK), "the tank's tab with no tank");
                    h.assertFalse(menu.showingTank(), "the server took the tank's tab with no tank");
                    p.closeContainer();
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    // ---- CONT-034, CONT-074: what a player can't do ------------------------------------------------

    /**
     * A player with no rights can't read a block beyond their reach: neither the game's packet nor a
     * direct use opens a window on it. A window opened in reach closes once the player is out of it, or
     * once the hand holds no analyzer, and sends nothing more. A client can only choose a tab: any other
     * button, and any button for a closed window, is refused.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_a_player_without_rights_cant_read_a_far_target(GameTestHelper h) {
        Area a = new Area(h, 3465);
        a.clear(0, 0, 16, 6);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, 50.0), T));
        ListeningPlayer l = player(a, 14, 1, 2);
        ServerPlayer p = l.player();
        BlockPos pos = a.abs(2, 1, 2);
        BlockHitResult hit = new BlockHitResult(faceCentre(pos, Direction.EAST), Direction.EAST, pos, false);

        p.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 1));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "the packet opened " + p.containerMenu);
        h.assertValueEqual(useOn(p, pos, Direction.EAST, hit.getLocation()), InteractionResult.FAIL, "using it far");
        h.assertTrue(p.containerMenu == p.inventoryMenu, "using it far opened " + p.containerMenu);
        h.assertFalse(
                AnalyzerMenu.open(p, InteractionHand.MAIN_HAND, AnalyzerTarget.on(a.level, hit)),
                "the menu opened on a far target");
        h.assertTrue(readings(l).isEmpty(), "a far target was read: " + readings(l));

        // In reach, it opens; buttons other than the tabs are refused.
        p.moveTo(Vec3.atBottomCenterOf(a.abs(4, 1, 2)));
        h.assertTrue(useOn(p, pos, Direction.EAST, hit.getLocation()).consumesAction(), "using it near did nothing");
        AnalyzerMenu menu = menu(a, l);
        h.assertValueEqual(readings(l).size(), 1, "readings on opening");
        for (int id : new int[] {-1, 2, 99}) {
            h.assertFalse(menu.clickMenuButton(p, id), "button " + id + " was taken");
        }
        h.assertFalse(menu.clickMenuButton(p, AnalyzerMenu.SHOW_TANK), "the tank's tab with no tank");
        p.tick();
        h.assertTrue(p.containerMenu == menu, "a tick in reach closed the window");

        // Walking out of reach closes it at the player's next tick.
        p.moveTo(Vec3.atBottomCenterOf(a.abs(14, 1, 2)));
        h.assertFalse(menu.stillValid(p), "the window is valid out of reach");
        p.tick();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "out of reach, the window stayed open");
        int closed = menu.containerId;
        button(p, closed, AnalyzerMenu.SHOW_TARGET);
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a button for the closed window opened " + p.containerMenu);
        h.assertValueEqual(readings(l).size(), 1, "readings after closing");

        // In reach again, putting the analyzer away closes it too.
        p.moveTo(Vec3.atBottomCenterOf(a.abs(4, 1, 2)));
        useOn(p, pos, Direction.EAST, hit.getLocation());
        AnalyzerMenu again = menu(a, l);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.tick();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "with no analyzer in hand, the window stayed open");
        h.assertFalse(again.stillValid(p), "the window is valid with no analyzer");
        a.clear(0, 0, 16, 6);
        h.succeed();
    }

    // ---- CONT-034, DATA-014: how often it syncs ----------------------------------------------------

    /**
     * Its window reads every five game ticks, four times a second, and sends only when what it shows has
     * changed: with the canister's gas changing every tick for 20 ticks, it sends on opening and then
     * once every five ticks; unchanged, it sends nothing more, and buttons sent in a burst don't make it
     * read sooner.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void cont_034_its_window_syncs_at_most_four_times_a_second_and_only_on_change(GameTestHelper h) {
        Area a = new Area(h, 3466);
        a.forceLoad(true);
        a.clear(0, 0, 6, 6);
        a.place(2, 1, 2, GasDeviceTests.canister());
        a.tick();
        CanisterBlockEntity can = a.entity(2, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(N2, 100.0), T));
        ListeningPlayer l = player(a, 4, 1, 2);
        ServerPlayer p = l.player();
        BlockPos pos = a.abs(2, 1, 2);
        useOn(p, pos, Direction.EAST, faceCentre(pos, Direction.EAST));
        AnalyzerMenu menu = menu(a, l);
        long start = p.server.getTickCount();
        List<Long> sentAt = new ArrayList<>(List.of(0L));

        h.onEachTick(() -> {
            long t = p.server.getTickCount() - start;
            if (t >= 1 && t <= 20) {
                can.network().gas().add(new GasParcel(Map.of(N2, 5.0), T));
            }
            if (t == 23) {
                for (int i = 0; i < 10; i++) {
                    button(p, menu.containerId, AnalyzerMenu.SHOW_TARGET);
                }
            }
            int before = readings(l).size();
            p.tick();
            if (readings(l).size() > before) {
                sentAt.add(t);
            }
        });
        h.runAfterDelay(45, () -> {
            h.assertValueEqual(sentAt, List.of(0L, 5L, 10L, 15L, 20L), "the ticks it sent on");
            h.assertValueEqual(readings(l).size(), 5, "readings sent");
            GasParcel share = can.share();
            same(a, last(a, l).gas(), new GasMixture(share.moles(), share.temperature(), can.nodeVolume()), "the last");
            p.closeContainer();
            a.clear(0, 0, 6, 6);
            a.forceLoad(false);
            h.succeed();
        });
    }

    // ---- CONT-034, CLIENT-008: the HUD while it's held ----------------------------------------------

    /**
     * Held in either hand, it makes the HUD show every reading, safe ones too: it's in {@code
     * #zas:shows_readings}. With empty hands, it doesn't.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_held_in_either_hand_it_shows_every_hud_reading(GameTestHelper h) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "zas_analyzer_hud"));
        ItemStack analyzer = new ItemStack(AtmosItems.ATMOS_ANALYZER.get());
        h.assertTrue(analyzer.is(HudLevels.SHOWS_READINGS), "the analyzer isn't in #zas:shows_readings");
        h.assertFalse(HudLevels.showsReadings(p), "every reading shows with empty hands");
        p.setItemInHand(InteractionHand.MAIN_HAND, analyzer);
        h.assertTrue(HudLevels.showsReadings(p), "every reading doesn't show with it in the main hand");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.OFF_HAND, analyzer);
        h.assertTrue(HudLevels.showsReadings(p), "every reading doesn't show with it in the off hand");
        h.succeed();
    }
}
