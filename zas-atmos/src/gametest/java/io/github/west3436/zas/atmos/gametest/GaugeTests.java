package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.AIR;
import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.STONE;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync.Kind;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gauge.GaugeBlock;
import io.github.west3436.zas.atmos.gauge.GaugeBlockEntity;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.geometry.GeometryDerivation;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gauge (#488): which parts it fits to, and that it reads each of them as the analyzer reads a
 * click on that face; each of its readings against the analyzer's value, and where the needle stands
 * for it; turning it through its readings; what it saves; that it drops when its part goes, and reads
 * on as its part's network changes; that its needle moves, and so tells clients, only by whole steps;
 * and that it seals nothing. Each test builds in its own {@link Area} and steps the gauges itself,
 * within one game tick, in a batch of its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GaugeTests {
    private static final String BATCH = "zas_atmos_gauge";

    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    private GaugeTests() {}

    // ---- Helpers ----------------------------------------------------------------------------------

    /** A survival player the game's own paths take for a client, whose packets are kept. */
    private static ListeningPlayer listener(Area a, int x, int y, int z) {
        return ListeningPlayer.client(a.level, Vec3.atBottomCenterOf(a.abs(x, y, z)), false);
    }

    /** Uses a stack on the middle of a face of a block, as a right-click there does. */
    private static InteractionResult use(ServerPlayer p, ItemStack stack, BlockPos on, Direction face) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).relative(face, 0.5), face, on, false);
        return p.gameMode.useItemOn(p, p.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    /** Fits a gauge from its item to a face of a block, and fails unless it is there. */
    private static GaugeBlockEntity fit(Area a, ServerPlayer p, BlockPos on, Direction face) {
        ItemStack item = new ItemStack(AtmosItems.GAUGE.get());
        a.h.assertTrue(use(p, item, on, face).consumesAction(), "a gauge on " + face + " of " + on + " wasn't placed");
        a.h.assertTrue(item.isEmpty(), "placing a gauge on " + face + " of " + on + " didn't take the item");
        BlockPos pos = on.relative(face);
        BlockState state = a.level.getBlockState(pos);
        a.h.assertTrue(state.is(AtmosBlocks.GAUGE.get()), "on " + face + " of " + on + " there is " + state);
        a.h.assertValueEqual(GaugeBlock.out(state), face, "the way the gauge on " + face + " of " + on + " faces");
        a.h.assertValueEqual(GaugeBlock.host(state, pos), on, "the block the gauge is on");
        return (GaugeBlockEntity) a.level.getBlockEntity(pos);
    }

    /** What the analyzer reads in the middle of a face of a block. */
    private static AnalyzerTarget analyzerOn(Area a, BlockPos pos, Direction face) {
        return AnalyzerTarget.on(
                a.level, new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
    }

    /** What the analyzer reads used on a gauge: what the gauge is on. */
    private static AnalyzerTarget analyzerOnGauge(Area a, GaugeBlockEntity gauge) {
        Direction out = GaugeBlock.out(gauge.getBlockState());
        return analyzerOn(a, gauge.getBlockPos(), out);
    }

    /** Turns a gauge to a reading, as a command would, and steps it. */
    private static void show(GaugeBlockEntity gauge, GaugeReading reading) {
        BlockState state = gauge.getBlockState();
        gauge.getLevel()
                .setBlock(gauge.getBlockPos(), state.setValue(GaugeReading.PROPERTY, reading), Block.UPDATE_ALL);
        gauge.step();
    }

    /** The moles that make a pressure in a volume at a temperature. */
    private static double molesFor(double kPa, double volume, double kelvin) {
        return kPa * volume / (GasMixture.GAS_CONSTANT * kelvin);
    }

    /** Empties a gas store and fills it to a pressure with nitrogen at a temperature. */
    private static void fillTo(GasStore gas, double kPa, double kelvin) {
        gas.drain();
        gas.add(new GasParcel(Map.of(N2, molesFor(kPa, gas.volume(), kelvin)), kelvin));
    }

    private static int step(double along) {
        return (int) Math.round(along * GaugeBlockEntity.STEPS);
    }

    private static List<ItemStack> drops() {
        return WrenchTests.takeDrops().stream().map(ItemEntity::getItem).toList();
    }

    private static boolean oneGauge(List<ItemStack> stacks) {
        return stacks.size() == 1
                && stacks.get(0).is(AtmosItems.GAUGE.get())
                && stacks.get(0).getCount() == 1;
    }

    // ---- Where it goes ----------------------------------------------------------------------------

    /**
     * A gauge goes on the top and the side of a pipe, on a canister on its port, on either half of a
     * large tank, top and side, and on a pump's side, each from its item, and reads each as the analyzer
     * reads a click in the middle of that face: the canister and the tank as a vessel, from either half
     * the tank's lower one, and the pump's input port, the first of the two its side is as near. The
     * analyzer used on the gauge reads the same. On the canister the item places it rather than opening
     * the canister's window. On a wall it faces out; on a floor its top is away from the player. On
     * stone, or on its own kind, it isn't placed.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_fits_to_each_part_and_reads_it_as_the_analyzer_does(GameTestHelper h) {
        Area a = new Area(h, 4900);
        a.clear(0, 0, 14, 8);
        ServerPlayer p = listener(a, 1, 1, 7).player();
        p.moveTo(p.getX(), p.getY(), p.getZ(), Direction.NORTH.toYRot(), 0);
        // A pipe run, a canister on its port, a tank, a pump between two pipes, and a stone.
        a.place(1, 1, 2, GasDeviceTests.pipe());
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(3, 1, 2, GasDeviceTests.pipe());
        a.place(6, 1, 2, GasDeviceTests.port());
        a.place(6, 2, 2, GasDeviceTests.canister());
        LargeTankBlockEntity tank = LargeTankTests.stand(a, 9, 1, 2);
        a.place(1, 1, 5, GasDeviceTests.pipe());
        a.place(
                2,
                1,
                5,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.place(3, 1, 5, GasDeviceTests.pipe());
        a.set(12, 1, 2, STONE);

        record Fit(int x, int y, int z, Direction face, Kind kind, BlockPos reads, int port, String what) {}
        BlockPos canister = a.abs(6, 2, 2);
        BlockPos lower = a.abs(9, 1, 2);
        List<Fit> fits = List.of(
                new Fit(2, 1, 2, Direction.UP, Kind.NETWORK, a.abs(2, 1, 2), -1, "the pipe's top"),
                new Fit(2, 1, 2, Direction.NORTH, Kind.NETWORK, a.abs(2, 1, 2), -1, "the pipe's side"),
                new Fit(6, 2, 2, Direction.EAST, Kind.CANISTER, canister, -1, "the canister's side"),
                new Fit(6, 2, 2, Direction.UP, Kind.CANISTER, canister, -1, "the canister's top"),
                new Fit(9, 1, 2, Direction.SOUTH, Kind.CANISTER, lower, -1, "the tank's lower half"),
                new Fit(9, 2, 2, Direction.SOUTH, Kind.CANISTER, lower, -1, "the tank's upper half"),
                new Fit(9, 2, 2, Direction.UP, Kind.CANISTER, lower, -1, "the tank's top"),
                new Fit(2, 1, 5, Direction.NORTH, Kind.NETWORK, a.abs(2, 1, 5), 0, "the pump's side"));
        for (Fit f : fits) {
            BlockPos on = a.abs(f.x, f.y, f.z);
            GaugeBlockEntity gauge = fit(a, p, on, f.face);
            BlockState state = gauge.getBlockState();
            AnalyzerTarget reads = GaugeBlock.reads(a.level, gauge.getBlockPos(), state);
            h.assertTrue(reads != null, "the gauge on " + f.what + " reads nothing");
            h.assertValueEqual(reads.kind(), f.kind, "what the gauge on " + f.what + " reads");
            h.assertValueEqual(reads.pos(), f.reads, "the block the gauge on " + f.what + " reads");
            h.assertValueEqual(reads.port(), f.port, "the port the gauge on " + f.what + " reads");
            h.assertValueEqual(reads, analyzerOn(a, on, f.face), "the analyzer on " + f.what);
            h.assertValueEqual(analyzerOnGauge(a, gauge), reads, "the analyzer on the gauge on " + f.what);
            h.assertValueEqual(state.getValue(GaugeReading.PROPERTY), GaugeReading.PRESSURE, "a new gauge's reading");
            AttachFace attach = f.face == Direction.UP ? AttachFace.FLOOR : AttachFace.WALL;
            h.assertValueEqual(state.getValue(GaugeBlock.FACE), attach, "how the gauge on " + f.what + " is fitted");
            h.assertValueEqual(
                    state.getValue(GaugeBlock.FACING),
                    attach == AttachFace.FLOOR ? Direction.NORTH : f.face,
                    "the gauge on " + f.what + " faces");
        }
        h.assertTrue(p.containerMenu instanceof InventoryMenu, "placing a gauge opened " + p.containerMenu);
        h.assertTrue(tank.network().volume() > 0, "the tank has no network");

        // Nothing on stone, nor on a gauge.
        ItemStack item = new ItemStack(AtmosItems.GAUGE.get());
        use(p, item, a.abs(12, 1, 2), Direction.NORTH);
        h.assertTrue(a.get(12, 1, 1).isAir(), "a gauge went on a stone: " + a.get(12, 1, 1));
        h.assertValueEqual(item.getCount(), 1, "gauges left after trying the stone");
        use(p, item, a.abs(2, 2, 2), Direction.UP);
        h.assertTrue(a.get(2, 3, 2).isAir(), "a gauge went on a gauge: " + a.get(2, 3, 2));
        a.clear(0, 0, 14, 8);
        h.succeed();
    }

    // ---- What it reads ----------------------------------------------------------------------------

    /**
     * Each reading, on a pipe's network and on a canister, is the analyzer's value for that part, and
     * the needle stands where its scale puts it: a pipe's pressure on a network's bands and a canister's
     * on a vessel's, each band's end at the same place on the arc; the temperature from 0 to 1,000 K;
     * each gas's share from none to all. No gas reads nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_reads_each_value_as_the_analyzer_does(GameTestHelper h) {
        Area a = new Area(h, 4901);
        a.clear(0, 0, 8, 6);
        ServerPlayer p = listener(a, 1, 1, 5).player();
        a.place(1, 1, 2, GasDeviceTests.pipe());
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.place(5, 1, 2, GasDeviceTests.port());
        a.place(5, 2, 2, GasDeviceTests.canister());
        a.tick();
        GaugeBlockEntity onPipe = fit(a, p, a.abs(2, 1, 2), Direction.UP);
        GaugeBlockEntity onCanister = fit(a, p, a.abs(5, 2, 2), Direction.EAST);
        GasStore pipeGas = a.<PipeNodeEntity>entity(2, 1, 2).network().gas();
        GasStore canisterGas = a.<CanisterBlockEntity>entity(5, 2, 2).network().gas();

        // No gas: every reading rests.
        pipeGas.drain();
        canisterGas.drain();
        for (GaugeReading reading : GaugeReading.values()) {
            show(onPipe, reading);
            h.assertValueEqual(onPipe.needle(), 0, "the needle over no gas, reading " + reading);
        }

        // Pressure at each band's end.
        double burst = PipeBurst.THRESHOLD_KPA;
        double rated = ContainerFailure.CANISTER_RATED_KPA;
        double[][] pipePressures = {{burst / 4, 0.2}, {burst / 2, 0.4}, {burst, 0.8}, {burst * 1.125, 0.9}};
        double[][] canisterPressures = {
            {rated / 4, 0.2}, {rated, 0.8}, {(rated + ContainerFailure.CANISTER_EXPLODE_KPA) / 2, 0.9}
        };
        for (double[] at : pipePressures) {
            fillTo(pipeGas, at[0], T);
            show(onPipe, GaugeReading.PRESSURE);
            h.assertValueEqual(onPipe.needle(), step(at[1]), "the pipe's needle at " + at[0] + " kPa");
        }
        for (double[] at : canisterPressures) {
            fillTo(canisterGas, at[0], T);
            show(onCanister, GaugeReading.PRESSURE);
            h.assertValueEqual(onCanister.needle(), step(at[1]), "the canister's needle at " + at[0] + " kPa");
        }
        fillTo(pipeGas, burst * 2, T);
        show(onPipe, GaugeReading.PRESSURE);
        h.assertValueEqual(onPipe.needle(), GaugeBlockEntity.STEPS, "the needle past the top");

        // Every reading against the analyzer's, on each part, with a mixture of four gases.
        for (GaugeBlockEntity gauge : List.of(onPipe, onCanister)) {
            GasStore gas = gauge == onPipe ? pipeGas : canisterGas;
            gas.drain();
            gas.add(new GasParcel(Map.of(N2, 50.0, O2, 30.0, CO2, 10.0, GaugeReading.HYDROGEN.gas(), 10.0), 500));
            AnalyzerTarget target = analyzerOnGauge(a, gauge);
            GasMixture read = target.read(p).gas();
            boolean vessel = target.kind() == Kind.CANISTER;
            for (GaugeReading reading : GaugeReading.values()) {
                double expected = switch (reading) {
                    case PRESSURE -> read.pressure();
                    case TEMPERATURE -> read.temperature();
                    default -> read.moles(reading.gas()) / read.totalMoles();
                };
                String what = (vessel ? "the canister's " : "the pipe's ") + reading;
                a.close(reading.value(target.gas(a.level)), expected, what);
                show(gauge, reading);
                double along = reading.along(expected, vessel) * GaugeBlockEntity.STEPS;
                h.assertTrue(
                        Math.abs(gauge.needle() - along) <= 0.5 + 1e-6,
                        what + "'s needle is at step " + gauge.needle() + " for " + along);
            }
            show(gauge, GaugeReading.TEMPERATURE);
            h.assertValueEqual(gauge.needle(), step(0.5), "the needle at 500 K");
            show(gauge, GaugeReading.OXYGEN);
            h.assertValueEqual(gauge.needle(), step(0.3), "the needle at 30 % oxygen");
            show(gauge, GaugeReading.METHANE);
            h.assertValueEqual(gauge.needle(), 0, "the needle for a gas that isn't there");
        }
        a.clear(0, 0, 8, 6);
        h.succeed();
    }

    // ---- Turning it -------------------------------------------------------------------------------

    /**
     * A plain use with an empty hand turns a gauge to its next reading, round all of them and back to
     * the pressure; a sneaking use turns it back. Each turn names the new reading on the action bar,
     * and the needle shows it at once. With an item in hand, a use turns it too.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_turns_through_its_readings_and_names_each(GameTestHelper h) {
        Area a = new Area(h, 4902);
        a.clear(0, 0, 6, 6);
        ListeningPlayer l = listener(a, 1, 1, 4);
        ServerPlayer p = l.player();
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.tick();
        GaugeBlockEntity gauge = fit(a, p, a.abs(2, 1, 2), Direction.UP);
        GasStore gas = a.<PipeNodeEntity>entity(2, 1, 2).network().gas();
        gas.add(new GasParcel(Map.of(O2, 1.0), 500));
        gauge.step();
        BlockPos pos = gauge.getBlockPos();

        GaugeReading expected = GaugeReading.PRESSURE;
        for (int i = 0; i < GaugeReading.values().length; i++) {
            expected = expected.next();
            h.assertTrue(use(p, ItemStack.EMPTY, pos, Direction.UP).consumesAction(), "a use did nothing");
            BlockState state = a.level.getBlockState(pos);
            h.assertValueEqual(
                    state.getValue(GaugeReading.PROPERTY), expected, "the reading after " + (i + 1) + " uses");
            List<ClientboundSystemChatPacket> bar = l.packets(ClientboundSystemChatPacket.class).stream()
                    .filter(ClientboundSystemChatPacket::overlay)
                    .toList();
            h.assertFalse(bar.isEmpty(), "nothing on the action bar");
            h.assertValueEqual(
                    bar.get(bar.size() - 1).content(), expected.label(), "the action bar after " + (i + 1) + " uses");
            int shows = switch (expected) {
                case TEMPERATURE -> step(0.5);
                case OXYGEN -> GaugeBlockEntity.STEPS;
                case PRESSURE -> step(expected.along(gas.pressure(), false));
                default -> 0;
            };
            h.assertValueEqual(gauge.needle(), shows, "the needle at once, reading " + expected);
        }
        h.assertValueEqual(expected, GaugeReading.PRESSURE, "round every reading");

        p.setShiftKeyDown(true);
        use(p, ItemStack.EMPTY, pos, Direction.UP);
        h.assertValueEqual(
                a.level.getBlockState(pos).getValue(GaugeReading.PROPERTY), GaugeReading.WATER_VAPOR, "a sneak's turn");
        p.setShiftKeyDown(false);
        use(p, new ItemStack(Items.STICK), pos, Direction.UP);
        h.assertValueEqual(
                a.level.getBlockState(pos).getValue(GaugeReading.PROPERTY),
                GaugeReading.PRESSURE,
                "a use with a stick");

        // Each label has its key in the language file, and a gas's names the gas.
        for (GaugeReading reading : GaugeReading.values()) {
            Component label = reading.label();
            h.assertTrue(
                    label.getContents() instanceof TranslatableContents t
                            && t.getKey().startsWith("block.zas_atmos.gauge."),
                    reading + "'s label is " + label);
        }
        a.clear(0, 0, 6, 6);
        h.succeed();
    }

    // ---- What it saves ----------------------------------------------------------------------------

    /**
     * A gauge saves its needle with its block entity, and its reading with its block: loaded again,
     * it shows both, and clients are sent the needle.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_saves_its_reading_and_its_needle(GameTestHelper h) {
        Area a = new Area(h, 4903);
        a.clear(0, 0, 6, 6);
        ServerPlayer p = listener(a, 1, 1, 4).player();
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.tick();
        GaugeBlockEntity gauge = fit(a, p, a.abs(2, 1, 2), Direction.NORTH);
        a.<PipeNodeEntity>entity(2, 1, 2).network().gas().add(new GasParcel(Map.of(O2, 1.0, N2, 3.0), T));
        show(gauge, GaugeReading.OXYGEN);
        h.assertValueEqual(gauge.needle(), step(0.25), "the needle before saving");

        HolderLookup.Provider registries = a.level.registryAccess();
        CompoundTag saved = gauge.saveWithFullMetadata(registries);
        BlockState state = gauge.getBlockState();
        BlockState reloaded =
                NbtUtils.readBlockState(a.level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state));
        h.assertValueEqual(reloaded, state, "the block state, saved and loaded");
        BlockEntity loaded = BlockEntity.loadStatic(gauge.getBlockPos(), reloaded, saved, registries);
        h.assertTrue(loaded instanceof GaugeBlockEntity, "loaded, it is " + loaded);
        h.assertValueEqual(((GaugeBlockEntity) loaded).needle(), step(0.25), "the needle, loaded");

        GaugeBlockEntity client = new GaugeBlockEntity(gauge.getBlockPos(), state);
        client.handleUpdateTag(gauge.getUpdateTag(registries), registries);
        h.assertValueEqual(client.needle(), step(0.25), "the needle a client is sent");
        a.clear(0, 0, 6, 6);
        h.succeed();
    }

    // ---- When its part goes, or its network changes ------------------------------------------------

    /**
     * A gauge drops as an item when what it is on goes: a pipe taken away, a canister mined, a large
     * tank broken by either half, which takes the gauges on both its halves. One whose part went without
     * telling its neighbours drops at its next step.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_drops_when_what_it_is_on_goes(GameTestHelper h) {
        Area a = new Area(h, 4904);
        a.clear(0, 0, 12, 6);
        ServerPlayer p = listener(a, 1, 1, 5).player();

        a.place(2, 1, 2, GasDeviceTests.pipe());
        fit(a, p, a.abs(2, 1, 2), Direction.UP);
        WrenchTests.watchDrops();
        a.level.setBlockAndUpdate(a.abs(2, 1, 2), AIR);
        h.assertTrue(a.get(2, 2, 2).isAir(), "the gauge stayed without its pipe");
        h.assertTrue(oneGauge(drops()), "the pipe's gauge dropped no gauge");

        a.place(5, 1, 2, GasDeviceTests.canister());
        fit(a, p, a.abs(5, 1, 2), Direction.SOUTH);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        WrenchTests.watchDrops();
        h.assertTrue(p.gameMode.destroyBlock(a.abs(5, 1, 2)), "the canister didn't break");
        List<ItemStack> mined = drops();
        h.assertTrue(a.get(5, 1, 3).isAir(), "the gauge stayed without its canister");
        h.assertValueEqual(
                mined.stream().filter(s -> s.is(AtmosItems.GAUGE.get())).count(),
                1L,
                "gauges dropped with the canister");

        for (boolean byUpper : new boolean[] {false, true}) {
            LargeTankTests.stand(a, 9, 1, 2);
            fit(a, p, a.abs(9, 1, 2), Direction.SOUTH);
            fit(a, p, a.abs(9, 2, 2), Direction.SOUTH);
            fit(a, p, a.abs(9, 2, 2), Direction.UP);
            WrenchTests.watchDrops();
            h.assertTrue(p.gameMode.destroyBlock(a.abs(9, byUpper ? 2 : 1, 2)), "the tank didn't break");
            List<ItemStack> dropped = drops();
            String half = byUpper ? "upper" : "lower";
            h.assertTrue(
                    a.get(9, 1, 3).isAir()
                            && a.get(9, 2, 3).isAir()
                            && a.get(9, 3, 2).isAir(),
                    "a gauge stayed without the tank broken by its " + half + " half");
            h.assertValueEqual(
                    dropped.stream().filter(s -> s.is(AtmosItems.GAUGE.get())).count(),
                    3L,
                    "gauges dropped with the tank broken by its " + half + " half");
        }

        // Taken away with no word to its neighbours, as a command can.
        a.place(2, 1, 4, GasDeviceTests.pipe());
        GaugeBlockEntity silent = fit(a, p, a.abs(2, 1, 4), Direction.UP);
        a.level.setBlock(a.abs(2, 1, 4), AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        h.assertTrue(a.get(2, 2, 4).is(AtmosBlocks.GAUGE.get()), "the gauge went at once, though nothing told it");
        WrenchTests.watchDrops();
        silent.step();
        h.assertTrue(a.get(2, 2, 4).isAir(), "the gauge stayed past its step without its pipe");
        h.assertTrue(oneGauge(drops()), "the silent pipe's gauge dropped no gauge");
        a.clear(0, 0, 12, 6);
        h.succeed();
    }

    /**
     * A gauge on a pipe reads whatever network the pipe is in: joined to a pipe of other gas it reads
     * the mixture, cut off again its own share, and with its pipe put into a station wall, the gauge
     * fitted first, it stays and reads on. A gauge goes on the face of a wall with a pipe in it too,
     * fitted after, and reads the pipe.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_reads_on_as_its_network_changes(GameTestHelper h) {
        Area a = new Area(h, 4905);
        a.clear(0, 0, 8, 6);
        ServerPlayer p = listener(a, 1, 1, 5).player();
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.tick();
        GaugeBlockEntity gauge = fit(a, p, a.abs(2, 1, 2), Direction.NORTH);
        PipeNodeEntity pipe = a.entity(2, 1, 2);
        fillTo(pipe.network().gas(), 4_000, T);
        gauge.step();
        int alone = gauge.needle();
        h.assertTrue(alone > 0, "the gauge reads nothing");

        // Another pipe, empty, joins it: the pressure halves.
        a.place(3, 1, 2, GasDeviceTests.pipe());
        gauge.step();
        h.assertValueEqual(gauge.needle(), step(GaugeReading.PRESSURE.along(2_000, false)), "joined to an empty pipe");
        h.assertTrue(a.get(2, 1, 1).is(AtmosBlocks.GAUGE.get()), "the gauge went when its network changed");
        a.set(3, 1, 2, AIR);
        a.tick();
        gauge.step();
        h.assertValueEqual(gauge.needle(), step(GaugeReading.PRESSURE.along(2_000, false)), "cut off again");

        // The pipe goes into a station wall: the gauge stays and reads it.
        ItemStack wall = new ItemStack(AtmosItems.STATION_WALL.get());
        h.assertTrue(use(p, wall, a.abs(2, 1, 2), Direction.UP).consumesAction(), "the wall didn't go round the pipe");
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.STATION_WALL_PIPE.get()), "the pipe is in " + a.get(2, 1, 2));
        h.assertTrue(a.get(2, 1, 1).is(AtmosBlocks.GAUGE.get()), "the gauge went when its pipe went into a wall");
        gauge.step();
        h.assertValueEqual(gauge.needle(), step(GaugeReading.PRESSURE.along(2_000, false)), "in the wall");

        // And on the wall's other face, fitted after.
        GaugeBlockEntity after = fit(a, p, a.abs(2, 1, 2), Direction.SOUTH);
        after.step();
        h.assertValueEqual(after.needle(), gauge.needle(), "the gauge fitted to the wall after");
        a.clear(0, 0, 8, 6);
        h.succeed();
    }

    // ---- Its needle, and what it costs --------------------------------------------------------------

    /**
     * The needle moves in whole steps, so a change of less than half a step leaves it as it was and
     * tells no client; a step's change moves it one. A comparator reads it from 0 to 15. A pump
     * filling the pipe moves it along without the gauge walking its network.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_moves_its_needle_only_by_whole_steps(GameTestHelper h) {
        Area a = new Area(h, 4906);
        a.clear(0, 0, 8, 6);
        ServerPlayer p = listener(a, 1, 1, 5).player();
        a.place(2, 1, 2, GasDeviceTests.pipe());
        a.tick();
        GaugeBlockEntity gauge = fit(a, p, a.abs(2, 1, 2), Direction.UP);
        GasStore gas = a.<PipeNodeEntity>entity(2, 1, 2).network().gas();
        double top = PipeBurst.THRESHOLD_KPA * 1.25;
        double perStep = top / GaugeBlockEntity.STEPS;

        fillTo(gas, 30 * perStep, T);
        gauge.step();
        h.assertValueEqual(gauge.needle(), 30, "the needle at 30 steps");
        fillTo(gas, 30.4 * perStep, T);
        gauge.step();
        h.assertValueEqual(gauge.needle(), 30, "the needle after less than half a step");
        fillTo(gas, 31 * perStep, T);
        gauge.step();
        h.assertValueEqual(gauge.needle(), 31, "the needle after a step");

        BlockPos pos = gauge.getBlockPos();
        BlockState state = a.level.getBlockState(pos);
        h.assertTrue(state.hasAnalogOutputSignal(), "a comparator can't read the gauge");
        h.assertValueEqual(state.getAnalogOutputSignal(a.level, pos), 5, "a comparator at 31 steps");
        fillTo(gas, top, T);
        gauge.step();
        h.assertValueEqual(state.getAnalogOutputSignal(a.level, pos), 15, "a comparator at the top");
        gas.drain();
        gauge.step();
        h.assertValueEqual(state.getAnalogOutputSignal(a.level, pos), 0, "a comparator over no gas");

        // Many gauges on one network read its one mixture: none builds it again.
        a.place(1, 1, 2, GasDeviceTests.pipe());
        a.place(3, 1, 2, GasDeviceTests.pipe());
        var net = a.<PipeNodeEntity>entity(2, 1, 2).network();
        GaugeBlockEntity side = fit(a, p, a.abs(1, 1, 2), Direction.NORTH);
        GaugeBlockEntity other = fit(a, p, a.abs(3, 1, 2), Direction.SOUTH);
        for (int i = 0; i < 5; i++) {
            gauge.step();
            side.step();
            other.step();
        }
        h.assertTrue(a.<PipeNodeEntity>entity(2, 1, 2).network() == net, "stepping the gauges built the network again");
        a.clear(0, 0, 8, 6);
        h.succeed();
    }

    /**
     * A gauge has no collision, so it seals nothing and the zones see air where it is. It is in the
     * creative tab once, drops itself, and a pipe, copper, a glass pane and an iron nugget make one.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void gauge_seals_nothing_and_is_crafted_from_a_pipe(GameTestHelper h) {
        for (BlockState state : AtmosBlocks.GAUGE.get().getStateDefinition().getPossibleStates()) {
            h.assertTrue(GeometryDerivation.collisionShape(state).isEmpty(), "a gauge collides: " + state);
        }
        var level = h.getLevel();
        CraftingInput input = CraftingInput.of(
                2,
                2,
                List.of(
                        new ItemStack(AtmosItems.PIPE.get()),
                        new ItemStack(Items.COPPER_INGOT),
                        new ItemStack(Items.GLASS_PANE),
                        new ItemStack(Items.IRON_NUGGET)));
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElse(null);
        h.assertTrue(recipe != null, "nothing crafts from a pipe, copper, a glass pane and a nugget");
        ItemStack out = recipe.value().assemble(input, level.registryAccess());
        h.assertTrue(out.is(AtmosItems.GAUGE.get()) && out.getCount() == 1, "they make " + out);

        CreativeModeTab tab = AtmosCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        long listed = tab.getDisplayItems().stream()
                .filter(s -> s.is(AtmosItems.GAUGE.get()))
                .count();
        h.assertValueEqual(listed, 1L, "gauges in the creative tab");
        h.succeed();
    }
}
