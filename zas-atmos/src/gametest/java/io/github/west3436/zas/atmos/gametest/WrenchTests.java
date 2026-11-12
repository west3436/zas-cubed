package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.inline.SidePortEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.WrenchItem;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The wrench (CONT-035, CONT-091): it turns and removes pipes and devices, keeps a mirrored filter
 * mirrored, refuses a part holding more than 2 atm over the ambient, and makes and loses no gas. Each
 * test that builds does so in its own {@link Area} and drives atmos ticks itself, in a batch of its own; the one
 * with the conservation checker on has another to itself, as the checker covers the whole dimension.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WrenchTests {
    private static final String BATCH = "zas_atmos_wrench";
    private static final String CHECKER_BATCH = "zas_atmos_wrench_checker";

    /** A gas the ambient doesn't hold, so only what the test puts in counts. */
    private static final ResourceLocation CO2 = PipeSaveTests.GAS;

    private WrenchTests() {}

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    private static BlockState pump(Direction facing) {
        return AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, facing);
    }

    /** A survival player holding a wrench. */
    private static Player holding(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.WRENCH.get()));
        return player;
    }

    /** A click with the wrench on a face of a block, as the game makes it, sneaking or not. */
    private static InteractionResult use(Area a, Player player, int x, int y, int z, Direction face, boolean sneak) {
        BlockPos pos = a.abs(x, y, z);
        player.setShiftKeyDown(sneak);
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        return player.getMainHandItem()
                .onItemUseFirst(
                        new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)));
    }

    private static PipeNetwork network(Area a, int x, int y, int z) {
        return a.<PipeNodeEntity>entity(x, y, z).network();
    }

    private static final List<ItemEntity> DROPPED = new ArrayList<>();

    private static boolean listening;

    private static boolean watching;

    /**
     * Starts collecting dropped items afresh. A level only finds entities in a chunk that is ticking
     * them, so these tests see each one as it joins. Each runs within one call, so nothing else drops
     * an item in the meantime.
     */
    private static void watchDrops() {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (watching && e.getEntity() instanceof ItemEntity item) {
                    DROPPED.add(item);
                }
            });
        }
        DROPPED.clear();
        watching = true;
    }

    /** Takes the items dropped since {@link #watchDrops} away: fails unless one of them is {@code item}. */
    private static void assertDropped(GameTestHelper h, Item item, String what) {
        List<ItemEntity> drops = takeDrops();
        h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(item)), what + " dropped no " + item + ": " + drops);
    }

    /** Stops collecting, and removes from the level the items dropped since {@link #watchDrops}. */
    private static List<ItemEntity> takeDrops() {
        watching = false;
        List<ItemEntity> drops = new ArrayList<>(DROPPED);
        DROPPED.clear();
        drops.forEach(Entity::discard);
        return drops;
    }

    /**
     * CONT-035: a wrench turns a pump and a pipe's cap, and takes a pipe and the pump away as items. A
     * pipe joins every neighbour by itself, so it has nothing to turn; its cap does. The pump turns
     * around, its ports joining the pipes on their new faces with the shares they held, and the cap
     * turns away from its pipe, leaving it an open end.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_035_a_wrench_turns_and_removes_a_pipe_and_a_device(GameTestHelper h) {
        Area a = new Area(h, 3590);
        a.clear(0, 0, 8, 4);
        a.place(1, 1, 2, cap(Direction.EAST));
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(3, 1, 2, pump(Direction.EAST));
        a.place(4, 1, 2, GasDeviceTests.pipe());
        a.place(5, 1, 2, cap(Direction.WEST));
        PressurePumpBlockEntity pump = a.entity(3, 1, 2);
        PipeNetwork back = pump.port(0).network();
        h.assertTrue(back == network(a, 2, 1, 2), "the pump's back isn't on the west pipe");
        back.gas().add(new GasParcel(Map.of(O2, 34.0), T));
        double portShare = 34.0 * PipeVolumes.PORT / back.volume();
        Player player = holding(h);

        // A plain pipe has nothing to turn, so a click changes nothing and goes no further.
        BlockState pipe = a.get(2, 1, 2);
        h.assertValueEqual(use(a, player, 2, 1, 2, Direction.UP, false), InteractionResult.FAIL, "a click on a pipe");
        h.assertTrue(a.get(2, 1, 2) == pipe, "a click turned a pipe");

        // Clicked on its front, the pump turns around: its back port now joins the east pipe.
        h.assertTrue(use(a, player, 3, 1, 2, Direction.EAST, false).consumesAction(), "the pump didn't turn");
        h.assertValueEqual(a.get(3, 1, 2).getValue(InlineDeviceBlock.FACING), Direction.WEST, "the pump's facing");
        h.assertTrue(pump.port(0).network() == network(a, 4, 1, 2), "the back port isn't on the east pipe");
        h.assertTrue(pump.port(1).network() == network(a, 2, 1, 2), "the front port isn't on the west pipe");
        a.close(pump.port(0).network().gas().moles(O2), portShare, "the back port took its share with it");
        a.close(pump.port(1).network().gas().moles(O2), 34.0 - portShare, "the west pipe's O2");

        // Clicked on its top, the east cap turns a quarter clockwise, from west to north.
        h.assertTrue(use(a, player, 5, 1, 2, Direction.UP, false).consumesAction(), "the cap didn't turn");
        h.assertValueEqual(a.get(5, 1, 2).getValue(PipeCapBlock.FACING), Direction.NORTH, "the cap's facing");
        h.assertFalse(
                a.get(4, 1, 2).getValue(PipeBlock.CONNECTIONS.get(Direction.EAST)), "the pipe still joins the cap");
        h.assertTrue(network(a, 5, 1, 2) != network(a, 4, 1, 2), "the cap is still on the pipe's network");

        // Sneaking, it takes the pipe and then the pump away, each as its item.
        watchDrops();
        h.assertTrue(use(a, player, 2, 1, 2, Direction.UP, true).consumesAction(), "the pipe's removal");
        h.assertTrue(a.get(2, 1, 2).isAir(), "the pipe is still there");
        assertDropped(h, AtmosItems.PIPE.get(), "the pipe");
        watchDrops();
        h.assertTrue(use(a, player, 3, 1, 2, Direction.UP, true).consumesAction(), "the pump's removal");
        h.assertTrue(a.get(3, 1, 2).isAir(), "the pump is still there");
        assertDropped(h, AtmosItems.PRESSURE_PUMP.get(), "the pump");

        a.clear(0, 0, 8, 4);
        player.discard();
        h.succeed();
    }

    /**
     * CONT-035 and D-77: the wrench never mirrors a filter. Every click on a mirrored filter, in every
     * facing, turns it and leaves it mirrored. In the world, turned flat and then upright, its side
     * port is where a mirrored filter's goes, joins the pipe there, and the block still gives the
     * mirrored item.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_035_turning_a_mirrored_filter_keeps_it_mirrored(GameTestHelper h) {
        BlockState mirrored = AtmosBlocks.FILTER.get().defaultBlockState().setValue(SidePortBlock.MIRRORED, true);
        for (Direction facing : Direction.values()) {
            BlockState state = mirrored.setValue(InlineDeviceBlock.FACING, facing);
            for (Direction face : Direction.values()) {
                BlockState turned = WrenchItem.turned(state, face);
                h.assertTrue(turned.getValue(SidePortBlock.MIRRORED), facing + " clicked " + face + " unmirrored it");
                h.assertTrue(
                        turned.getValue(InlineDeviceBlock.FACING) != facing,
                        facing + " clicked " + face + " didn't turn");
            }
        }

        Area a = new Area(h, 3591);
        a.clear(0, 0, 8, 4);
        // Facing east, its side port is on its left, north. A capped pipe waits to its east.
        a.place(3, 1, 2, mirrored.setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.place(4, 1, 2, GasDeviceTests.pipe());
        a.place(5, 1, 2, cap(Direction.WEST));
        FilterBlockEntity filter = a.entity(3, 1, 2);
        h.assertValueEqual(SidePortBlock.sideFace(a.get(3, 1, 2)), Direction.NORTH, "the side port before");
        Player player = holding(h);

        // Clicked on its top, it turns to face south, its side port still on its left: east, on the pipe.
        h.assertTrue(use(a, player, 3, 1, 2, Direction.UP, false).consumesAction(), "the filter didn't turn");
        BlockState turned = a.get(3, 1, 2);
        h.assertValueEqual(turned.getValue(InlineDeviceBlock.FACING), Direction.SOUTH, "the filter's facing");
        h.assertTrue(turned.getValue(SidePortBlock.MIRRORED), "turning it flat unmirrored it");
        h.assertValueEqual(SidePortBlock.sideFace(turned), Direction.EAST, "the side port, flat");
        h.assertTrue(
                a.get(4, 1, 2).getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)),
                "the pipe doesn't join the side port");
        h.assertTrue(
                filter.port(SidePortEntity.SIDE).network() == network(a, 4, 1, 2), "the side port isn't on the pipe");

        // Clicked on its west face, it turns down, mirrored still, so its side port is west.
        h.assertTrue(use(a, player, 3, 1, 2, Direction.WEST, false).consumesAction(), "the filter didn't turn down");
        turned = a.get(3, 1, 2);
        h.assertValueEqual(turned.getValue(InlineDeviceBlock.FACING), Direction.DOWN, "the filter's facing, upright");
        h.assertTrue(turned.getValue(SidePortBlock.MIRRORED), "turning it upright unmirrored it");
        h.assertValueEqual(SidePortBlock.sideFace(turned), Direction.WEST, "the side port, upright");
        h.assertFalse(
                a.get(4, 1, 2).getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)), "the pipe still joins the filter");
        h.assertTrue(
                turned.getBlock()
                        .getCloneItemStack(a.level, a.abs(3, 1, 2), turned)
                        .is(AtmosItems.FILTER_MIRRORED.get()),
                "the turned filter doesn't give the mirrored item");

        a.clear(0, 0, 8, 4);
        player.discard();
        h.succeed();
    }

    /**
     * CONT-035: a part that only faces sideways, as the electrolyzer does, turns about the vertical. A
     * click on its top turns it clockwise as seen from above, on its bottom clockwise as seen from
     * below, and on any side clockwise as seen from above; four clicks bring it back. A furnace stands
     * in for it, as its facing is the same property, and nothing else about it changes.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_035_a_part_facing_only_sideways_turns_about_the_vertical(GameTestHelper h) {
        BlockState lit = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        for (Direction way : Direction.Plane.HORIZONTAL) {
            BlockState state = lit.setValue(BlockStateProperties.HORIZONTAL_FACING, way);
            for (Direction face : Direction.values()) {
                BlockState turned = WrenchItem.turned(state, face);
                Direction expected = face == Direction.DOWN ? way.getCounterClockWise() : way.getClockWise();
                h.assertValueEqual(
                        turned.getValue(BlockStateProperties.HORIZONTAL_FACING), expected, way + " clicked " + face);
                h.assertTrue(turned.getValue(BlockStateProperties.LIT), way + " clicked " + face + " changed more");
            }
            BlockState around = state;
            for (int i = 0; i < 4; i++) {
                around = WrenchItem.turned(around, Direction.UP);
            }
            h.assertTrue(around == state, way + " didn't come back after four turns");
        }
        h.succeed();
    }

    /**
     * CONT-091: a pipe holding 3 atm over its room's pressure doesn't come off, and the player is told
     * it's pressurized. Its cap turned away, it vents through its open end, and once it is no more than 2
     * atm over, it comes off.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_091_a_part_3_atm_over_ambient_comes_off_only_once_vented(GameTestHelper h) {
        Area a = new Area(h, 3592);
        a.box(0, 0, 6, 4);
        a.fill(3, 2, 2, Map.of(N2, 10.0));
        a.place(1, 1, 2, cap(Direction.EAST));
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(3, 1, 2, GasDeviceTests.pipe());
        a.place(4, 1, 2, cap(Direction.WEST));
        a.tick();
        BlockPos at = a.abs(2, 1, 2);
        double ambient = Atmospheres.at(a.level, Vec3.atCenterOf(at)).pressure();
        PipeNetwork net = network(a, 2, 1, 2);
        double kpa = ambient + 3 * StandardAir.PRESSURE;
        net.gas().add(new GasParcel(Map.of(N2, kpa * net.volume() / (GasMixture.GAS_CONSTANT * T)), T));
        a.close(WrenchItem.overAmbient(a.level, at), 3 * StandardAir.PRESSURE, "the pipe's pressure over the room's");
        double held = net.gas().totalMoles();
        Player player = holding(h);

        // Refused, by the click and by the call, which says why.
        use(a, player, 2, 1, 2, Direction.UP, true);
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "the pipe came off at 3 atm over");
        Optional<Component> refusal = WrenchItem.remove(a.level, at, player, player.getMainHandItem());
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "the call removed the pipe at 3 atm over");
        h.assertTrue(
                refusal.isPresent()
                        && refusal.get().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.wrench.pressurized")
                        && t.getArgs().length == 0,
                "the refusal: " + refusal);
        a.close(network(a, 2, 1, 2).gas().totalMoles(), held, "what the pipe holds after the refusals");

        // The east cap turned away, the run vents into the room through its open end.
        use(a, player, 4, 1, 2, Direction.UP, false);
        h.assertValueEqual(a.get(4, 1, 2).getValue(PipeCapBlock.FACING), Direction.NORTH, "the cap's facing");
        for (int i = 0; i < 40 && WrenchItem.overAmbient(a.level, at) > WrenchItem.LOCK_KPA; i++) {
            a.step();
        }
        h.assertTrue(
                WrenchItem.overAmbient(a.level, at) <= WrenchItem.LOCK_KPA,
                "the run didn't vent: " + WrenchItem.overAmbient(a.level, at) + " kPa over");
        watchDrops();
        use(a, player, 2, 1, 2, Direction.UP, true);
        h.assertTrue(a.get(2, 1, 2).isAir(), "the vented pipe didn't come off");
        assertDropped(h, AtmosItems.PIPE.get(), "the vented pipe");

        a.clear(-1, -1, 7, 5);
        player.discard();
        h.succeed();
    }

    /** Fails if the last atmos tick's report holds a change nothing explains. */
    private static void conserved(Area a, String when) {
        ConservationReport report = a.service().dimension(a.level).lastConservation();
        a.h.assertTrue(report != null, when + ": the checker didn't run");
        a.h.assertTrue(report.conserved(), when + ": unexplained: " + report.violations());
    }

    /**
     * CONT-035 and CONT-091: taking a pipe and then a pump off a run in a sealed room makes and loses no
     * gas. Each one's share goes into the room, the rest stays in the run, and the checker finds nothing
     * unexplained.
     */
    @GameTest(template = "empty", batch = CHECKER_BATCH)
    public static void cont_091_removing_a_part_with_a_wrench_conserves_its_gas(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            Area a = new Area(h, 3593);
            ChunkPos chunk = new ChunkPos(a.abs(0, 0, 0));
            a.box(0, 0, 6, 4);
            a.fill(3, 2, 2, Map.of(N2, 10.0));
            a.place(1, 1, 2, cap(Direction.EAST));
            a.place(2, 1, 2, GasDeviceTests.pipe());
            a.place(3, 1, 2, pump(Direction.EAST));
            a.place(4, 1, 2, GasDeviceTests.pipe());
            a.place(5, 1, 2, cap(Direction.WEST));
            a.tick();
            PressurePumpBlockEntity pump = a.entity(3, 1, 2);
            pump.port(0).network().gas().add(new GasParcel(Map.of(CO2, 3.0), T));
            pump.port(1).network().gas().add(new GasParcel(Map.of(CO2, 5.0), T));
            double total = GasCensus.total(a, CO2, chunk);
            a.close(total, 8.0, "what the run holds");
            double pipeShare = 3.0 * PipeVolumes.PIPE / pump.port(0).network().volume();
            Player player = holding(h);
            watchDrops();

            use(a, player, 2, 1, 2, Direction.UP, true);
            h.assertTrue(a.get(2, 1, 2).isAir(), "the pipe didn't come off");
            a.tick();
            a.close(GasCensus.total(a, CO2, chunk), total, "the room and the run, the pipe off");
            a.close(a.zone(3, 2, 2).mixture().moles(CO2), pipeShare, "the room has the pipe's share");
            conserved(a, "the pipe off");

            double portShares =
                    pump.port(0).share().moles(CO2) + pump.port(1).share().moles(CO2);
            use(a, player, 3, 1, 2, Direction.UP, true);
            h.assertTrue(a.get(3, 1, 2).isAir(), "the pump didn't come off");
            a.tick();
            a.close(GasCensus.total(a, CO2, chunk), total, "the room and the run, the pump off");
            a.close(a.zone(3, 2, 2).mixture().moles(CO2), pipeShare + portShares, "the room has the ports' shares");
            conserved(a, "the pump off");

            takeDrops();
            a.clear(-1, -1, 7, 5);
            player.discard();
            h.succeed();
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
    }
}
