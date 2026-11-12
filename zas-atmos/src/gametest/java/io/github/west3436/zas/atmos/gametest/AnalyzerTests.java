package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.LocalAtmosphereProvider;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.analyzer.AnalyzerMenu;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync.Kind;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.pipe.GasHandlers;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.GasVessels;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.command.ZasCommands;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

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
     * Used on either half of a large gas tank, it reads the tank's own gas, its share of its network's,
     * as it reads a canister's, and names the tank.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_either_half_of_a_large_tank_it_reads_the_tanks_own_gas(GameTestHelper h) {
        Area a = new Area(h, 4887);
        a.clear(0, 0, 6, 6);
        LargeTankBlockEntity tank = LargeTankTests.stand(a, 2, 2, 2); // its upper half at (2, 3, 2)
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.tick();
        tank.network().gas().add(new GasParcel(Map.of(N2, 1_200.0, O2, 300.0), T));
        h.assertTrue(tank.network().volume() > tank.nodeVolume(), "the tank is alone in its network");
        GasParcel share = tank.share();
        GasMixture own = new GasMixture(share.moles(), share.temperature(), tank.nodeVolume());
        ListeningPlayer l = player(a, 4, 2, 2);
        ServerPlayer p = l.player();

        for (int y = 2; y <= 3; y++) {
            BlockPos half = a.abs(2, y, 2);
            String which = y == 2 ? "its lower half" : "its upper half";
            for (Direction face : Direction.values()) {
                AnalyzerTarget target =
                        AnalyzerTarget.on(a.level, new BlockHitResult(faceCentre(half, face), face, half, false));
                h.assertValueEqual(target.kind(), Kind.CANISTER, "what the tank reads at " + which + " on " + face);
                h.assertValueEqual(target.pos(), a.abs(2, 2, 2), "the block read at " + which + " on " + face);
            }
            h.assertTrue(
                    useOn(p, half, Direction.EAST, faceCentre(half, Direction.EAST))
                            .consumesAction(),
                    "using it on " + which + " did nothing");
            menu(a, l);
            AnalyzerSync s = last(a, l);
            h.assertValueEqual(s.kind(), Kind.CANISTER, "what the tank reads");
            h.assertValueEqual(s.target(), AtmosBlocks.LARGE_GAS_TANK.get().getName(), "its name");
            same(a, s.gas(), own, "the reading at " + which);
            p.closeContainer();
        }
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

    // ---- CONT-034, API-021: another mod's store of gas ----------------------------------------------

    /** A block that provides the gas-handler capability as another mod's tank would, and its entity. */
    private static TestGasHandler.Entity store(Area a, int x, int y, int z) {
        a.place(x, y, z, TestGasHandler.block().defaultBlockState());
        return a.entity(x, y, z);
    }

    /** Uses the analyzer on the middle of a block's face, and gives what the window then reads. */
    private static AnalyzerSync readOn(Area a, ListeningPlayer l, BlockPos pos, Direction face, String what) {
        l.player().closeContainer();
        a.h.assertTrue(
                useOn(l.player(), pos, face, faceCentre(pos, face)).consumesAction(),
                "using it on " + what + " did nothing");
        return last(a, l);
    }

    /** Fails unless a reading is of the air in front of a block's face, outside zones. */
    private static void airInFront(Area a, AnalyzerSync s, BlockPos pos, Direction face, String what) {
        outsideZones(a, s, Kind.POINT, false, what);
        a.h.assertValueEqual(s.target(), Component.literal(coordsComma(pos.relative(face))), what + ": its name");
        same(
                a,
                s.gas(),
                Atmospheres.at(a.level, Vec3.atCenterOf(pos.relative(face))).mixture(),
                what);
    }

    /**
     * Used on a block that provides the gas-handler capability, as another mod's tank does, it reads
     * what the block holds, in the volume the block gives, under the block's name: on the face clicked,
     * or else for no face in particular, so a face the block offers nothing on still reads the block.
     * Where the block offers nothing either way it reads the air in front of the face, as for any other
     * block, and so it does for a handler with no room for gas, which only joins blocks. An empty store
     * with room reads as the store, empty. The window stays open while the block is there and provides
     * a handler, and the reading comes through its codec.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_another_mods_store_it_reads_what_the_store_holds(GameTestHelper h) {
        Area a = new Area(h, 3468);
        a.clear(0, 0, 6, 4);
        TestGasHandler.Entity store = store(a, 2, 1, 2);
        store.tank.add(new GasParcel(Map.of(N2, 2.0, O2, 0.5), 310));
        ListeningPlayer l = player(a, 4, 1, 2);
        ServerPlayer p = l.player();
        BlockPos pos = a.abs(2, 1, 2);
        Component name = TestGasHandler.block().getName();

        AnalyzerSync s = readOn(a, l, pos, Direction.EAST, "the store");
        AnalyzerMenu menu = menu(a, l);
        h.assertValueEqual(s.kind(), Kind.STORE, "what the store reads");
        h.assertValueEqual(s.target(), name, "its name");
        h.assertTrue(s.state() == null, "a store has an air state");
        h.assertValueEqual(s.zone(), -1L, "a store's zone");
        h.assertFalse(s.local(), "a store is read as another mod's air");
        same(a, s.gas(), store.tank.mixture(), "the store's reading");
        a.close(s.gas().volume(), TestGasHandler.VOLUME, "the store's volume, L");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
        AnalyzerSync.CODEC.encode(buf, s);
        AnalyzerSync decoded = AnalyzerSync.CODEC.decode(buf);
        h.assertValueEqual(buf.readableBytes(), 0, "bytes left after decoding");
        h.assertValueEqual(decoded.kind(), Kind.STORE, "the decoded reading's kind");
        h.assertTrue(decoded.showsSameAs(s), "the decoded reading shows something else: " + decoded);

        // The window stays open while the block provides a handler, and no longer.
        h.assertTrue(menu.stillValid(p), "the window isn't valid on the store");
        store.exposed = false;
        h.assertFalse(menu.stillValid(p), "the window is valid on a block that provides no handler any more");
        store.exposed = true;
        h.assertTrue(menu.stillValid(p), "the window isn't valid once the block provides a handler again");

        // What it holds now is what it reads, used again.
        store.tank.add(new GasParcel(Map.of(O2, 1.5), 310));
        same(a, readOn(a, l, pos, Direction.UP, "the store's top").gas(), store.tank.mixture(), "the store, with more");

        // A block that answers for no face alone: every face reads it.
        store.onlyUnsided = true;
        h.assertTrue(a.level.getCapability(IGasHandler.BLOCK, pos, Direction.EAST) == null, "it answers on a face");
        AnalyzerSync unsided = readOn(a, l, pos, Direction.EAST, "a face the store offers nothing on");
        h.assertValueEqual(unsided.kind(), Kind.STORE, "what a face the store offers nothing on reads");
        h.assertValueEqual(unsided.target(), name, "its name there");
        same(a, unsided.gas(), store.tank.mixture(), "the store's reading for no face");
        store.onlyUnsided = false;

        // One face only, and nothing for no face: that face reads the store, another the air in front.
        store.onlySide = Direction.UP;
        h.assertValueEqual(
                readOn(a, l, pos, Direction.UP, "the store's one face").kind(), Kind.STORE, "its one face");
        airInFront(
                a,
                readOn(a, l, pos, Direction.EAST, "a face of a store with one"),
                pos,
                Direction.EAST,
                "another face");
        store.onlySide = null;

        // No room: a handler that only joins blocks, on every face and on none.
        store.says = new GasMixture(Map.of(), T, 1e-30);
        airInFront(a, readOn(a, l, pos, Direction.EAST, "a joint"), pos, Direction.EAST, "a joint");
        store.says = new GasMixture(Map.of(O2, 1e-9), T, GasHandlers.LEAST_STORE_LITRES / 2);
        airInFront(a, readOn(a, l, pos, Direction.EAST, "a store too small"), pos, Direction.EAST, "a store too small");
        // Empty, with room: the store, empty.
        store.says = new GasMixture(Map.of(), T, GasHandlers.LEAST_STORE_LITRES);
        AnalyzerSync empty = readOn(a, l, pos, Direction.EAST, "an empty store");
        h.assertValueEqual(empty.kind(), Kind.STORE, "what an empty store reads");
        h.assertTrue(empty.gas().isEmpty(), "an empty store reads " + empty.gas());
        store.says = null;

        // The block gone: the window closes, as a canister's does.
        AnalyzerSync before = readOn(a, l, pos, Direction.EAST, "the store, once more");
        h.assertValueEqual(before.kind(), Kind.STORE, "what the store reads once more");
        AnalyzerMenu open = menu(a, l);
        a.set(2, 1, 2, Area.AIR);
        h.assertFalse(open.stillValid(p), "the window is valid with the block gone");
        p.closeContainer();
        a.clear(0, 0, 6, 4);
        h.succeed();
    }

    /**
     * A store that throws, from its contents or from the capability's lookup, is read as the air in
     * front of the face, and a window open on it closes; the fault is logged at most once a minute
     * (NFR-013).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nfr_013_a_store_that_throws_is_read_as_the_air_in_front_and_logged_once_a_minute(
            GameTestHelper h) {
        Area a = new Area(h, 3469);
        a.clear(0, 0, 6, 4);
        TestGasHandler.Entity store = store(a, 2, 1, 2);
        store.tank.add(new GasParcel(Map.of(N2, 2.0), T));
        ListeningPlayer l = player(a, 4, 1, 2);
        ServerPlayer p = l.player();
        BlockPos pos = a.abs(2, 1, 2);
        String source = TestGasHandler.ID.toString();
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            store.duringContents = () -> {
                throw new IllegalStateException("the test store failing");
            };
            airInFront(a, readOn(a, l, pos, Direction.EAST, "a store whose contents throw"), pos, Direction.EAST, "it");
            airInFront(a, readOn(a, l, pos, Direction.UP, "the same store, again"), pos, Direction.UP, "it, again");
            store.duringContents = () -> {};
            h.assertValueEqual(log.naming(source), 1, "errors logged about its contents");

            FaultLog.aMinutePasses();
            store.failsLookup = true;
            airInFront(a, readOn(a, l, pos, Direction.EAST, "a store whose lookup throws"), pos, Direction.EAST, "it");
            store.failsLookup = false;
            h.assertValueEqual(log.naming(source), 2, "errors logged about its lookup");

            h.assertValueEqual(readOn(a, l, pos, Direction.EAST, "the store").kind(), Kind.STORE, "once it stops");
            AnalyzerMenu menu = menu(a, l);
            FaultLog.aMinutePasses();
            store.failsLookup = true;
            h.assertFalse(menu.stillValid(p), "the window is valid on a store whose lookup throws");
            store.failsLookup = false;
            h.assertValueEqual(log.naming(source), 3, "errors logged about the open window's lookup");
            h.assertTrue(menu.stillValid(p), "the window isn't valid once the lookup stops throwing");
        } finally {
            p.closeContainer();
            a.clear(0, 0, 6, 4);
        }
        h.succeed();
    }

    /**
     * Our own parts read as they did, whatever handler they offer other mods' blocks. Each block of
     * ours that joins pipes, stood among blocks that provide the capability so that it offers its own
     * on every face it can, is read on each face as a canister, as its network, or as the air in front,
     * and never through its handler. A canister and a large gas tank read as a canister. The
     * electrolyzer is the one that offers a handler and is none of the first two: it reads the air in
     * front of the face.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_used_on_our_own_parts_it_never_reads_their_gas_handler(GameTestHelper h) {
        Area a = new Area(h, 3470);
        a.clear(-1, -1, 5, 5);
        BlockPos pos = a.abs(2, 2, 2);
        for (Direction d : Direction.values()) {
            BlockPos next = pos.relative(d);
            a.level.setBlockAndUpdate(next, TestGasHandler.block().defaultBlockState());
        }
        Set<Block> offering = new HashSet<>();
        Map<Kind, Integer> read = new EnumMap<>(Kind.class);
        int parts = 0;
        for (DeferredHolder<Block, ? extends Block> holder : AtmosBlocks.BLOCKS.getEntries()) {
            Block block = holder.get();
            if (!(block instanceof PipeConnector)) {
                continue;
            }
            parts++;
            if (block instanceof LargeTankBlock) {
                // Its upper half takes the place of the store above it, and goes with the tank.
                a.level.setBlockAndUpdate(pos.above(), LargeTankTests.upper());
            }
            a.place(2, 2, 2, block.defaultBlockState());
            BlockEntity be = a.level.getBlockEntity(pos);
            BlockState state = a.level.getBlockState(pos);
            h.assertTrue(state.is(block), holder.getId() + " didn't stand: " + state);
            Kind expected = GasVessels.at(a.level, pos) != null
                    ? Kind.CANISTER
                    : be instanceof PipeNodeEntity
                                    || be instanceof InlineDeviceEntity && state.getBlock() instanceof InlineDeviceBlock
                            ? Kind.NETWORK
                            : Kind.POINT;
            for (Direction face : Direction.values()) {
                if (a.level.getCapability(IGasHandler.BLOCK, pos, face) != null) {
                    offering.add(block);
                }
                AnalyzerTarget target =
                        AnalyzerTarget.on(a.level, new BlockHitResult(faceCentre(pos, face), face, pos, false));
                h.assertValueEqual(target.kind(), expected, "what " + holder.getId() + " reads on " + face);
            }
            read.merge(expected, 1, Integer::sum);
            a.level.setBlockAndUpdate(pos, Area.AIR);
            if (block instanceof LargeTankBlock) {
                a.level.setBlockAndUpdate(pos.above(), TestGasHandler.block().defaultBlockState());
            }
        }
        h.assertTrue(parts >= 12, "blocks of ours that join pipes: " + parts);
        h.assertTrue(offering.contains(AtmosBlocks.PIPE.get()), "a pipe beside a store offers no handler");
        h.assertTrue(
                offering.contains(AtmosBlocks.ELECTROLYZER.get()), "an electrolyzer beside a store offers no handler");
        h.assertValueEqual(read.get(Kind.CANISTER), 2, "blocks of ours read as a canister: it and the large tank");
        h.assertValueEqual(read.get(Kind.POINT), 1, "blocks of ours read as the air in front: the electrolyzer");
        h.assertValueEqual(read.get(Kind.NETWORK), parts - 3, "blocks of ours read as their network");
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    // ---- CONT-034, SPACE-017: another mod's air outside zones ---------------------------------------

    /** The id a test's provider of local air is registered under. */
    private static final ResourceLocation KEPT =
            ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "gametest_cont_034");

    /** Fails unless a reading is of air outside zones, and says whose air it is as expected. */
    private static void outsideZones(Area a, AnalyzerSync s, Kind kind, boolean local, String what) {
        a.h.assertValueEqual(s.kind(), kind, "what " + what + " reads");
        a.h.assertValueEqual(s.state(), AtmosphereState.AMBIENT, what + ": the atmosphere's state");
        a.h.assertValueEqual(s.zone(), -1L, what + ": the zone");
        a.h.assertValueEqual(s.local(), local, what + ": whether the air is another mod's");
    }

    /**
     * Outside zones, in a place another mod keeps breathable (SPACE-017), it reads that mod's air and
     * says so, in hand and used on a block there, and agrees with the HUD; {@code /zas at} still gives
     * the ambient. A zone inside the place is read as the zone, a point outside the place as the
     * ambient, and so is the place itself once no provider answers for it. The reading comes through
     * its codec with whose air it is, and a reading of the same gas that is the ambient's shows
     * something else.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_034_outside_zones_it_reads_another_mods_air_for_the_place(GameTestHelper h) {
        Area a = new Area(h, 3467);
        a.clear(-1, -1, 13, 5);
        a.box(0, 0, 4, 4);
        a.fill(2, 2, 2, Map.of(O2, 5.0, N2, 19.0));
        ZoneInfo zone = a.zone(2, 2, 2);
        a.set(7, 0, 2, Area.STONE);
        a.set(12, 0, 2, Area.STONE);
        // The room and the open air around it, as far as x 9, are the other mod's place.
        AABB place = AABB.encapsulatingFullBlocks(a.abs(-1, 0, -1), a.abs(9, 6, 5));
        GasMixture kept = new GasMixture(Map.of(O2, 12.0, N2, 20.0), 295.15, StandardAir.CELL_VOLUME);
        LocalAtmosphereProvider provider = (level, point) -> level == a.level && place.contains(point) ? kept : null;
        ListeningPlayer l = player(a, 7, 1, 2);
        ServerPlayer p = l.player();
        BlockPos wall = a.abs(4, 2, 2);
        BlockPos floor = a.abs(7, 0, 2);
        BlockPos far = a.abs(12, 0, 2);
        GasMixture ambient = Atmospheres.at(a.level, p.getEyePosition()).mixture();
        h.assertTrue(
                Math.abs(ambient.pressure() - kept.pressure()) > 1, "the test's air is as the ambient: " + ambient);

        LocalAtmospheres.register(KEPT, provider);
        try {
            useOnNothing(p);
            AnalyzerSync here = last(a, l);
            outsideZones(a, here, Kind.HERE, true, "the analyzer in hand");
            same(a, here.gas(), kept, "the reading in hand");
            AtmosHudPayload hud = HudLevels.of(p);
            h.assertTrue(hud != null, "the HUD has no state");
            h.assertTrue(
                    Math.abs(hud.pressureKpa() - here.gas().pressure()) < 1e-3,
                    "the HUD's " + hud.pressureKpa() + " kPa against the reading's "
                            + here.gas().pressure());
            h.assertTrue(
                    Math.abs(hud.temperatureK() - here.gas().temperature()) < 1e-3,
                    "the HUD's " + hud.temperatureK() + " K against the reading's "
                            + here.gas().temperature());
            String at = command(p, "zas at");
            h.assertTrue(at.contains("ambient, zone -"), "/zas at:\n" + at);
            says(a, at, ambient, false);

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
            AnalyzerSync.CODEC.encode(buf, here);
            AnalyzerSync decoded = AnalyzerSync.CODEC.decode(buf);
            h.assertValueEqual(buf.readableBytes(), 0, "bytes left after decoding");
            h.assertTrue(decoded.local(), "the decoded reading doesn't say the air is another mod's");
            h.assertTrue(decoded.showsSameAs(here), "the decoded reading shows something else: " + decoded);
            AnalyzerSync asAmbient = new AnalyzerSync(
                    here.containerId(),
                    here.kind(),
                    here.target(),
                    here.tank(),
                    here.state(),
                    here.zone(),
                    false,
                    kept);
            h.assertFalse(asAmbient.showsSameAs(here), "the same gas as the ambient's shows the same");
            p.closeContainer();

            h.assertTrue(
                    useOn(p, floor, Direction.UP, faceCentre(floor, Direction.UP))
                            .consumesAction(),
                    "using it on the floor in the place did nothing");
            AnalyzerSync onFloor = last(a, l);
            outsideZones(a, onFloor, Kind.POINT, true, "the floor in the place");
            h.assertValueEqual(onFloor.target(), Component.literal(coordsComma(a.abs(7, 1, 2))), "its name");
            same(a, onFloor.gas(), kept, "the floor's reading");
            p.closeContainer();

            h.assertTrue(
                    useOn(p, wall, Direction.EAST, faceCentre(wall, Direction.EAST))
                            .consumesAction(),
                    "using it on the room's wall from outside did nothing");
            AnalyzerSync onWall = last(a, l);
            outsideZones(a, onWall, Kind.POINT, true, "the room's wall from outside");
            same(a, onWall.gas(), kept, "the wall's reading from outside");
            p.closeContainer();

            // The room is in the place too, and its zone is what is read there.
            h.assertTrue(
                    useOn(p, wall, Direction.WEST, faceCentre(wall, Direction.WEST))
                            .consumesAction(),
                    "using it on the room's wall from inside did nothing");
            AnalyzerSync inRoom = last(a, l);
            h.assertValueEqual(inRoom.kind(), Kind.POINT, "what the wall's inside face reads");
            h.assertValueEqual(inRoom.state(), AtmosphereState.ZONED, "the room's state");
            h.assertValueEqual(inRoom.zone(), zone.id().value(), "the room's zone");
            h.assertFalse(inRoom.local(), "the room's zone is read as another mod's air");
            same(a, inRoom.gas(), a.zone(2, 2, 2).mixture(), "the room's reading");
            p.closeContainer();

            h.assertTrue(
                    useOn(p, far, Direction.UP, faceCentre(far, Direction.UP)).consumesAction(),
                    "using it on the block outside the place did nothing");
            AnalyzerSync outside = last(a, l);
            outsideZones(a, outside, Kind.POINT, false, "the block outside the place");
            same(a, outside.gas(), ambient, "the reading outside the place");
            p.closeContainer();
        } finally {
            h.assertTrue(LocalAtmospheres.unregister(KEPT), "the provider wasn't registered");
        }

        useOnNothing(p);
        AnalyzerSync bare = last(a, l);
        outsideZones(a, bare, Kind.HERE, false, "the analyzer in hand with no provider");
        same(a, bare.gas(), ambient, "the reading in hand with no provider");
        p.closeContainer();
        h.assertTrue(
                useOn(p, floor, Direction.UP, faceCentre(floor, Direction.UP)).consumesAction(),
                "using it on the floor with no provider did nothing");
        AnalyzerSync bareFloor = last(a, l);
        outsideZones(a, bareFloor, Kind.POINT, false, "the floor with no provider");
        same(a, bareFloor.gas(), ambient, "the floor's reading with no provider");
        p.closeContainer();
        a.clear(-1, -1, 13, 5);
        h.succeed();
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
