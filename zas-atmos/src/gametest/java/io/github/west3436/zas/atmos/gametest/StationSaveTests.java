package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.REL;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity.Slot;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import io.github.west3436.zas.atmos.airlock.AirlockFirelock;
import io.github.west3436.zas.atmos.airlock.SensorStatus;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ConsoleSide;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlock;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.firelock.Lockdown;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.holder.TankHolderBlock;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.PortedBlock;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.portable.PortableDevice;
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.pump.PumpBlockEntity;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.HeatExchangerTint;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import io.github.west3436.zas.atmos.valve.ValveBlockEntity;
import io.github.west3436.zas.atmos.vent.PassiveVentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.atmos.vent.VentSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * S-09 with content (DATA-016): a station of zones, pipes, every device that keeps a setting or a
 * buffer, canisters and tanks is saved to the world's files and loaded from them again, and every
 * amount comes back within 1e-9. A zone across the chunk that loads last is paused until it does.
 * An airlock controller saved midway through a cycle takes it up again and finishes it.
 *
 * <p><b>What stands in for a restart.</b> A GameTest can't restart the server. The station's three
 * chunks really unload, with nothing else holding them: the game writes their block entities, their
 * entities and the framework's zone records to the region files, drops them from memory, and builds
 * new block entities and entities from what it reads back. The central controllers' claims, which
 * the level keeps as saved data, are written and read back through their own save and load while the
 * chunks are away, and so is a player's inventory in its own test, through the calls the game's
 * player files use. What only a real restart covers is a new JVM: the framework's engine and the
 * device schedule starting empty, saved data read from the disk at startup, and the final save at a
 * stop. The release checklist's "Saving and quitting mid-transfer" is that check, run by hand.
 *
 * <p>The station sits in three chunks held at full status only, so vanilla's block ticks, and so the
 * generator, don't run there, and entities don't tick: everything is idle but the framework and the
 * devices. The schedule steps devices only in chunks that tick (architecture §11.2), so the test
 * steps the station's devices itself, once an atmos tick, wherever their chunk is loaded in full, as
 * the schedule does near a player. Its devices are switched off or unpowered, so nothing moves while
 * it waits for chunks, which the test checks before it saves. It has a batch of its own, as it drives
 * atmos ticks with {@code tickNow} while building and turns the central controllers' {@code
 * force_load} off.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationSaveTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "zas_atmos_station_save";
    private static final String EDGE_BATCH = "zas_atmos_edge_valve";
    private static final ResourceLocation CO2 = ControllerPreset.CO2;
    private static final ResourceLocation H2 = ControllerPreset.H2;

    /** Game ticks for the station's devices to step a few times: three atmos ticks. */
    private static final int SETTLE = 3 * AtmosTicks.PERIOD;

    /** What the station batch's end undoes, even if the test failed or timed out. */
    private static final List<Runnable> CLEANUP = new ArrayList<>();

    private static final String LOCK_BATCH = "zas_atmos_airlock_save";

    /** What the airlock batch's end undoes. */
    private static final List<Runnable> LOCK_CLEANUP = new ArrayList<>();

    private StationSaveTests() {}

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        CLEANUP.forEach(Runnable::run);
        CLEANUP.clear();
    }

    @AfterBatch(batch = LOCK_BATCH)
    public static void cleanUpLock(ServerLevel level) {
        LOCK_CLEANUP.forEach(Runnable::run);
        LOCK_CLEANUP.clear();
    }

    // ---- S-09 and DATA-016: the station ---------------------------------------------------

    /**
     * The station is saved to disk and loaded again: zones, pipe networks split by volume, every
     * device's settings and buffers, canisters, tanks in a chest, a portable, a suit and a TTV, a
     * primed TTV, and a placed TTV with two tanks of gas. Chunks A and B load first: everything in them is as it was, and the zone across B
     * and C is paused with B's share. Then C loads, and that zone resumes with all of its gas.
     *
     * <p>Batch 5's blocks come back too: a tank holder's tanks in their order, each with its gas;
     * the networks of radiator pipe and of a pair of heat exchangers, and the pair's bands; airlocks
     * open, bolted and with a firelock locked down, and the large airlock's pending close, which its
     * scheduled tick then makes at its time; the door under a firelock; an airlock controller's
     * slots, links, armed slot and last door, without power; and each air sensor's status.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void s_09_a_station_saved_to_disk_comes_back_with_every_amount(GameTestHelper h) {
        boolean forceLoad = AtmosServerConfig.get(AtmosServerConfig.CENTRAL_FORCE_LOAD);
        AtmosServerConfig.CENTRAL_FORCE_LOAD.set(false); // a console would hold its chunks loaded
        CLEANUP.add(() -> AtmosServerConfig.CENTRAL_FORCE_LOAD.set(forceLoad));
        Station s = new Station(h);
        CLEANUP.add(0, s::done);
        h.onEachTick(s::tick);
        Snapshot[] before = new Snapshot[1];
        List<BlockEntity> old = new ArrayList<>();
        List<PrimedTransferValve> oldPrimed = new ArrayList<>();
        h.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(s::settings)
                .thenIdle(SETTLE)
                .thenExecute(() -> before[0] = s.snapshot())
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    // Idle means idle: whatever differs after the reload was the save's doing.
                    s.compare(s.snapshot(), before[0], "while idle, before saving", 0, 1, 2);
                    old.addAll(before[0].entities.values());
                    oldPrimed.add(s.primed()); // once unloaded, it can't be found by its id
                    s.hold(false, 0, 1, 2);
                })
                .thenWaitUntil(() -> {
                    for (BlockEntity be : old) {
                        h.assertTrue(be.isRemoved(), "the station hasn't unloaded: " + be.getBlockPos());
                    }
                    h.assertTrue(oldPrimed.get(0).isRemoved(), "the primed TTV hasn't unloaded");
                })
                // The engine hears of unloads when its next batch closes. A and B loading before
                // then takes their unloads back, and P stays in memory whole, paused because C is
                // away. Only once the zones have left memory are they in nothing but the save.
                .thenWaitUntil(() -> s.zonesGone(before[0]))
                .thenExecute(() -> {
                    s.assertWrittenByVolume(before[0]);
                    s.reloadClaims();
                    s.hold(true, 0, 1);
                    s.cAway = true;
                })
                .thenWaitUntil(() -> h.assertTrue(s.full(0) && s.full(1), "chunks A and B haven't loaded"))
                .thenWaitUntil(() -> s.zonesBack(before[0]))
                .thenWaitUntil(() -> h.assertTrue(
                        s.a.level.getEntity(before[0].primed) instanceof PrimedTransferValve,
                        "the primed TTV hasn't come back"))
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    h.assertFalse(s.full(2), "chunk C loaded with B");
                    h.assertFalse(s.cCame, "chunk C was loaded while only A and B were held");
                    s.cAway = false;
                    Snapshot after = s.snapshot();
                    LOGGER.info(
                            "s_09 station: comparing {} block states and {} block entities: {}",
                            before[0].states.size(),
                            before[0].saved.size(),
                            kinds(before[0]));
                    s.compare(after, before[0], "after A and B loaded", 0, 1);
                    s.assertPausedShare(after, before[0]);
                    s.hold(true, 2);
                })
                .thenWaitUntil(() -> h.assertTrue(s.full(2), "chunk C hasn't loaded"))
                .thenWaitUntil(() ->
                        h.assertFalse(s.zone(before[0].zones.get("P").id()).paused(), "P hasn't resumed"))
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    Snapshot after = s.snapshot();
                    s.compare(after, before[0], "after C loaded", 0, 1, 2);
                    s.sameZone(after.zones.get("P"), before[0].zones.get("P"), "P, resumed");
                    s.closeLargeAirlock();
                })
                .thenSucceed();
    }

    /** The kinds of block entity in a snapshot, with how many of each. */
    private static Map<String, Integer> kinds(Snapshot s) {
        Map<String, Integer> out = new TreeMap<>();
        s.entities.values().forEach(be -> out.merge(Station.name(be), 1, Integer::sum));
        return out;
    }

    /** What a station holds at one moment. */
    private static final class Snapshot {
        /** Each block entity's saved tag, by position. */
        final Map<BlockPos, CompoundTag> saved = new LinkedHashMap<>();

        /** The block entities themselves, to see them unload. */
        final Map<BlockPos, BlockEntity> entities = new LinkedHashMap<>();

        /** Every block state in the station's box. */
        final Map<BlockPos, BlockState> states = new HashMap<>();

        /** The zones, by room. */
        final Map<String, ZoneInfo> zones = new LinkedHashMap<>();

        /** The pipe networks' gas, by name, read at a node that's loaded; absent if it isn't. */
        final Map<String, GasParcel> networks = new LinkedHashMap<>();

        /** Each pipe node's volume, by position. */
        final Map<BlockPos, Double> volumes = new HashMap<>();

        /** The primed TTV's id and saved fields. */
        UUID primed;

        CompoundTag primedSaved;

        /** The central controllers' claims, as saved. */
        CompoundTag claims;
    }

    /**
     * Three chunks along x: A from 0 to 15, B from 16 to 31 and C from 32 to 47, all at z 0 to 15.
     *
     * <ul>
     *   <li>Room R (x 1 to 5, stone) has an area air controller, a fire alarm, a vent and a scrubber,
     *       and a shut firelock in its east wall to room Q (x 5 to 9), at a lower pressure, so the
     *       firelock is locked down. Q has a passive vent at Q's pressure.
     *   <li>Room P (x 24 to 40, station walls, so its 350 K stays) runs across B and C: the paused zone.
     *   <li>Network N1 runs across A and B at z = 10 through a manual valve at x = 15, on A's edge
     *       (#341), with a canister on a port. N2 runs across B and C at z = 12.
     *   <li>A canister on its own; a pressure pump, a volume pump, a filter, a mixer and an
     *       electrolyzer at z = 14; a central controller, an igniter, a chest of tanks, a primed TTV
     *       and a placed one in A. With the chest, that is a block entity of every kind the mod has.
     *   <li>A portable pump and scrubber, a heater, an air conditioner, a recharger and a generator at
     *       z = 8 in B.
     *   <li>In B's corner by P, an airlock controller, unpowered, with all five slots filled: a bolted
     *       airlock and the large airlock, open and waiting to close itself, and three air sensors, one
     *       of them in Q; and a vent linked to it. A spruce door with its firelock shut, and a tank
     *       holder of four tanks. In Q's east wall, an airlock whose firelock is locked down.
     *   <li>Radiator pipe, joined to a pipe by an adaptor, in the open air at the ambient's
     *       temperature, at z = 14 in B; and a pair of heat exchangers at 450 K at z = 12.
     * </ul>
     */
    private static final class Station {
        static final int INDEX = 2800;
        static final BlockState STONE = Area.STONE;
        static final BlockPos CONSOLE = new BlockPos(13, 1, 3);
        static final BlockPos CONTROLLER = new BlockPos(3, 2, 2);

        // Batch 5. The airlocks are at their origins, the lower (left) block.
        static final BlockPos BOLTED = new BlockPos(17, 1, 4);
        static final BlockPos LARGE = new BlockPos(20, 1, 4);
        static final BlockPos FIRELOCKED = new BlockPos(9, 1, 3);
        static final BlockPos DOOR = new BlockPos(22, 1, 6);
        static final BlockPos AIRLOCK_CONTROLLER = new BlockPos(19, 2, 1);
        static final BlockPos SENSOR_IN = new BlockPos(17, 2, 1);
        static final BlockPos SENSOR_OUT = new BlockPos(21, 2, 1);
        static final BlockPos SENSOR_Q = new BlockPos(8, 2, 2);
        static final BlockPos LOCK_VENT = new BlockPos(18, 1, 2);
        static final BlockPos HOLDER = new BlockPos(17, 1, 6);
        static final BlockPos RADIATOR = new BlockPos(21, 1, 14);
        static final BlockPos EXCHANGER = new BlockPos(18, 1, 12);
        static final BlockPos PARTNER = new BlockPos(19, 1, 12);
        static final double EXCHANGER_K = 450;

        /** The placed TTV (D-86), its timer facing east. */
        static final BlockPos PLACED_TTV = new BlockPos(10, 1, 8);

        static final int PLACED_TIMER = 321;

        final GameTestHelper h;
        final Area a;
        final HolderLookup.Provider registries;
        final ChunkPos[] chunks = new ChunkPos[3];
        UUID primed;

        /** The tanks put in the holder, in order, as they went in. */
        final List<ItemStack> held = new ArrayList<>();

        /** What the placed TTV was given. */
        ItemStack placedTtv = ItemStack.EMPTY;

        /** When the large airlock closes itself, a game time. */
        long largeCloseAt;

        /** Set while only chunks A and B are held, and chunk C must stay away. */
        boolean cAway;

        /** Whether chunk C was loaded in full, for any game tick, while it had to stay away. */
        boolean cCame;

        Station(GameTestHelper h) {
            this.h = h;
            this.a = new Area(h, INDEX);
            this.registries = a.level.registryAccess();
            for (int i = 0; i < 3; i++) {
                chunks[i] = new ChunkPos(a.abs(16 * i, 0, 0));
            }
            hold(true, 0, 1, 2);
            clear();
            build();
        }

        void hold(boolean on, int... which) {
            for (int i : which) {
                PipeSaveTests.hold(a, chunks[i], on);
            }
        }

        /**
         * Each game tick: watches chunk C while it must stay away, and once an atmos tick steps the
         * station's devices in the chunks loaded in full. The station's chunks don't tick, so the
         * schedule steps nothing there.
         */
        void tick() {
            if (cAway && full(2)) {
                cCame = true;
            }
            if (AtmosTicks.now(a.level) % AtmosTicks.PERIOD != 0) {
                return;
            }
            List<ChunkPos> station = Arrays.asList(chunks);
            for (AtmosDevice d : DeviceTicker.loaded(a.level)) {
                if (station.contains(new ChunkPos(d.getBlockPos()))) {
                    d.step(); // one in a chunk that isn't loaded in full waits
                }
            }
        }

        /** Whether a chunk is loaded in full: asking loads nothing. */
        boolean full(int i) {
            return a.level.getChunkSource().hasChunk(chunks[i].x, chunks[i].z);
        }

        BlockPos abs(BlockPos p) {
            return a.abs(p.getX(), p.getY(), p.getZ());
        }

        <E extends BlockEntity> E at(int x, int y, int z) {
            return a.entity(x, y, z);
        }

        <E extends BlockEntity> E at(BlockPos p) {
            return a.entity(p.getX(), p.getY(), p.getZ());
        }

        BlockState state(BlockPos p) {
            return a.level.getBlockState(abs(p));
        }

        /** A hollow box from (x0, 0, z0) to (x1, 4, z1), walls of one block, without clearing around it. */
        void shell(int x0, int z0, int x1, int z1, BlockState wall) {
            for (BlockPos p : BlockPos.betweenClosed(a.abs(x0, 0, z0), a.abs(x1, 4, z1))) {
                int x = p.getX() - a.origin.getX();
                int y = p.getY() - a.origin.getY();
                int z = p.getZ() - a.origin.getZ();
                boolean edge = x == x0 || x == x1 || y == 0 || y == 4 || z == z0 || z == z1;
                a.level.setBlockAndUpdate(p, edge ? wall : Area.AIR);
            }
        }

        void build() {
            // Rooms first, then what's in them, then their gas, so no zone changes shape afterwards.
            shell(1, 1, 5, 5, STONE);
            shell(5, 1, 9, 5, STONE);
            shell(24, 1, 40, 5, AtmosBlocks.STATION_WALL.get().defaultBlockState());
            BlockState lock = AtmosBlocks.FIRELOCK
                    .get()
                    .defaultBlockState()
                    .setValue(FirelockBlock.FACING, Direction.EAST)
                    .setValue(FirelockBlock.OPEN, false);
            a.set(5, 1, 3, lock);
            a.set(5, 2, 3, lock.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
            a.place(
                    CONTROLLER.getX(),
                    CONTROLLER.getY(),
                    CONTROLLER.getZ(),
                    AtmosBlocks.AREA_AIR_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AreaAirControllerBlock.FACING, Direction.SOUTH));
            a.place(
                    2,
                    2,
                    2,
                    AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.SOUTH));
            a.place(2, 1, 4, GasDeviceTests.vent(Direction.UP));
            a.place(
                    4,
                    1,
                    4,
                    AtmosBlocks.SCRUBBER.get().defaultBlockState().setValue(ScrubberBlock.FACING, Direction.UP));
            a.place(
                    7,
                    1,
                    4,
                    AtmosBlocks.PASSIVE_VENT.get().defaultBlockState().setValue(PassiveVentBlock.FACING, Direction.UP));

            // The central controller, two by two, its lower left block at CONSOLE and the rest west and up.
            BlockState console = AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get().defaultBlockState();
            List<BlockPos> parts = CentralControllerBlock.parts(abs(CONSOLE), Direction.NORTH);
            for (int i = 0; i < parts.size(); i++) {
                a.level.setBlockAndUpdate(parts.get(i), CentralControllerBlock.part(console, i));
            }
            Site.loaded(a.level.getBlockEntity(abs(CONSOLE)));

            // N1, across A and B through the valve on A's edge, with a canister on a port.
            row(10, 11, 20);
            a.place(
                    15,
                    1,
                    10,
                    AtmosBlocks.MANUAL_VALVE
                            .get()
                            .defaultBlockState()
                            .setValue(ValveBlock.AXIS, Direction.Axis.X)
                            .setValue(ValveBlock.OPEN, true));
            a.place(12, 2, 10, GasDeviceTests.port());
            a.place(12, 3, 10, GasDeviceTests.canister());
            // N2, across B and C.
            row(12, 28, 36);
            a.place(2, 1, 12, GasDeviceTests.canister());

            a.place(2, 1, 14, inline(AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState()));
            a.place(4, 1, 14, inline(AtmosBlocks.VOLUME_PUMP.get().defaultBlockState()));
            a.place(6, 1, 14, inline(AtmosBlocks.FILTER.get().defaultBlockState()));
            a.place(8, 1, 14, inline(AtmosBlocks.MIXER.get().defaultBlockState()));
            a.place(
                    10,
                    1,
                    14,
                    AtmosBlocks.ELECTROLYZER
                            .get()
                            .defaultBlockState()
                            .setValue(ElectrolyzerBlock.FACING, Direction.EAST));

            a.place(17, 1, 8, portable(AtmosBlocks.PORTABLE_PUMP.get().defaultBlockState()));
            a.place(19, 1, 8, portable(AtmosBlocks.PORTABLE_SCRUBBER.get().defaultBlockState()));
            a.place(21, 1, 8, portable(AtmosBlocks.HEATER.get().defaultBlockState()));
            a.place(23, 1, 8, portable(AtmosBlocks.AIR_CONDITIONER.get().defaultBlockState()));
            a.place(
                    25,
                    1,
                    8,
                    AtmosBlocks.RECHARGER.get().defaultBlockState().setValue(RechargerBlock.FACING, Direction.NORTH));
            a.place(27, 1, 8, AtmosBlocks.GENERATOR.get().defaultBlockState());
            a.set(2, 1, 8, Blocks.CHEST.defaultBlockState());
            a.place(12, 1, 6, AtmosBlocks.IGNITER.get().defaultBlockState()); // with no signal, it never sparks
            a.place(
                    PLACED_TTV.getX(),
                    PLACED_TTV.getY(),
                    PLACED_TTV.getZ(),
                    AtmosBlocks.TANK_TRANSFER_VALVE
                            .get()
                            .defaultBlockState()
                            .setValue(TransferValveBlock.FACING, Direction.EAST)
                            .setValue(TransferValveBlock.TANKS, 2));
            buildBatch5();

            // The rooms' gas: R near one atmosphere, Q at about a third, P hot and rich in CO₂.
            a.fill(3, 2, 3, Map.of(N2, 900.0, O2, 250.0, CO2, 2.0));
            a.fill(7, 2, 3, Map.of(N2, 400.0));
            a.settle(GasMutations.add(
                    a.level, a.point(30, 2, 3), new GasParcel(Map.of(CO2, 120.0, N2, 300.0), 350.0), Area.SOURCE));
        }

        /**
         * The airlocks, their controller and sensors, the firelock door, the tank holder, the
         * radiator pipe and the heat exchangers. Doors stand on stone; panels hang on a block to
         * their north. Links, gas and tanks come in {@link #settingsBatch5}, once all are loaded.
         */
        void buildBatch5() {
            // In Q's east wall, an airlock facing out: it seals its east face, and its cells are Q's.
            airlock(FIRELOCKED, Direction.EAST);
            a.place(SENSOR_Q.getX(), SENSOR_Q.getY(), SENSOR_Q.getZ(), sensor()); // on Q's north wall

            for (BlockPos panel : List.of(SENSOR_IN, AIRLOCK_CONTROLLER, SENSOR_OUT)) {
                a.set(panel.getX(), panel.getY(), panel.getZ() - 1, STONE);
            }
            a.place(SENSOR_IN.getX(), SENSOR_IN.getY(), SENSOR_IN.getZ(), sensor());
            a.place(SENSOR_OUT.getX(), SENSOR_OUT.getY(), SENSOR_OUT.getZ(), sensor());
            a.place(
                    AIRLOCK_CONTROLLER.getX(),
                    AIRLOCK_CONTROLLER.getY(),
                    AIRLOCK_CONTROLLER.getZ(),
                    AtmosBlocks.AIRLOCK_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AirlockControllerBlock.FACING, Direction.SOUTH));
            a.place(LOCK_VENT.getX(), LOCK_VENT.getY(), LOCK_VENT.getZ(), GasDeviceTests.vent(Direction.UP));

            a.set(BOLTED.getX(), 0, BOLTED.getZ(), STONE);
            airlock(BOLTED, Direction.EAST);
            // The large airlock facing east: its right column is south of its left, the origin.
            BlockState large = AtmosBlocks.LARGE_AIRLOCK
                    .get()
                    .defaultBlockState()
                    .setValue(AirlockBlock.FACING, Direction.EAST)
                    .setValue(AirlockBlock.OPEN, false);
            a.set(LARGE.getX(), 0, LARGE.getZ(), STONE);
            a.set(LARGE.getX(), 0, LARGE.getZ() + 1, STONE);
            for (int i = 0; i < 4; i++) {
                boolean right = (i & 1) == 1;
                boolean upper = i >= 2;
                a.set(
                        LARGE.getX(),
                        LARGE.getY() + (upper ? 1 : 0),
                        LARGE.getZ() + (right ? 1 : 0),
                        large.setValue(AirlockBlock.SIDE, right ? ConsoleSide.RIGHT : ConsoleSide.LEFT)
                                .setValue(AirlockBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER));
            }

            // A spruce door, which the firelock wraps in settings: not the iron door a lost one becomes.
            BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            a.set(DOOR.getX(), 0, DOOR.getZ(), STONE);
            a.set(DOOR.getX(), DOOR.getY(), DOOR.getZ(), door);
            a.set(DOOR.getX(), DOOR.getY() + 1, DOOR.getZ(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

            a.place(
                    HOLDER.getX(),
                    HOLDER.getY(),
                    HOLDER.getZ(),
                    AtmosBlocks.TANK_HOLDER
                            .get()
                            .defaultBlockState()
                            .setValue(TankHolderBlock.FACING, Direction.SOUTH));

            // A cap, a pipe, an adaptor and four segments of radiator pipe: one network.
            a.place(
                    17,
                    1,
                    14,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST));
            a.place(18, 1, 14, GasDeviceTests.pipe());
            a.place(
                    19,
                    1,
                    14,
                    AtmosBlocks.RADIATOR_ADAPTOR
                            .get()
                            .defaultBlockState()
                            .setValue(RadiatorAdaptorBlock.FACING, Direction.EAST));
            for (int x = 20; x <= 23; x++) {
                a.place(x, 1, 14, AtmosBlocks.RADIATOR_PIPE.get().defaultBlockState());
            }
            // A pair of heat exchangers, each capped on its pipe face: two networks.
            a.place(
                    17,
                    1,
                    12,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST));
            a.place(EXCHANGER.getX(), EXCHANGER.getY(), EXCHANGER.getZ(), exchanger(Direction.EAST));
            a.place(PARTNER.getX(), PARTNER.getY(), PARTNER.getZ(), exchanger(Direction.WEST));
            a.place(
                    20,
                    1,
                    12,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.WEST));
        }

        /** A closed 1 by 2 airlock, its origin at {@code at}. */
        void airlock(BlockPos at, Direction facing) {
            BlockState lower = AtmosBlocks.AIRLOCK
                    .get()
                    .defaultBlockState()
                    .setValue(AirlockBlock.FACING, facing)
                    .setValue(AirlockBlock.OPEN, false);
            a.set(at.getX(), at.getY(), at.getZ(), lower);
            a.set(at.getX(), at.getY() + 1, at.getZ(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER));
        }

        static BlockState sensor() {
            return AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH);
        }

        static BlockState exchanger(Direction box) {
            return AtmosBlocks.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, box);
        }

        /** A capped row at y = 1 from x0 to x1: caps at the ends facing in, pipes between. */
        void row(int z, int x0, int x1) {
            a.place(
                    x0,
                    1,
                    z,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.EAST));
            for (int x = x0 + 1; x < x1; x++) {
                a.place(x, 1, z, GasDeviceTests.pipe());
            }
            a.place(
                    x1,
                    1,
                    z,
                    AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, Direction.WEST));
        }

        static BlockState inline(BlockState state) {
            return state.setValue(InlineDeviceBlock.FACING, Direction.EAST);
        }

        static BlockState portable(BlockState state) {
            return state.setValue(PortableBlock.FACING, Direction.NORTH);
        }

        /**
         * Every setting and buffer away from its default, once the devices have stepped. Gas goes
         * straight into each network, and the devices that move it are off or unpowered.
         */
        void settings() {
            AreaAirControllerBlockEntity controller = at(CONTROLLER.getX(), CONTROLLER.getY(), CONTROLLER.getZ());
            controller.setMode(ControllerMode.SIPHON);
            controller.setPreset(ControllerPreset.VACUUM);
            controller.toggleLink(a.abs(7, 1, 4));
            controller.buffer().set(own(AtmosServerConfig.CONTROLLER_DRAW) - 1); // too little to run on

            CentralControllerBlockEntity central = at(CONSOLE.getX(), CONSOLE.getY(), CONSOLE.getZ());
            h.assertTrue(central.setRange(1), "the console's range");
            central.buffer().set(own(AtmosServerConfig.CENTRAL_DRAW) - 1);
            CentralClaims claims = CentralClaims.of(a.level);
            h.assertTrue(
                    claims.centralOf(abs(CONTROLLER)).equals(Optional.of(abs(CONSOLE))),
                    "the console hasn't claimed R's controller");
            claims.setPriority(abs(CONTROLLER), ZonePriority.CRITICAL);

            VentBlockEntity vent = at(2, 1, 4);
            vent.command(new VentSettings(VentMode.SIPHON, 42.5, 310.0));
            vent.network().gas().add(new GasParcel(Map.of(N2, 2.0, O2, 0.5), 295.0));
            ScrubberBlockEntity scrubber = at(4, 1, 4);
            scrubber.command(ScrubberMode.SIPHON, GasSelection.of(List.of(CO2, H2), List.of()));
            scrubber.network().gas().add(new GasParcel(Map.of(CO2, 0.75), 300.0));

            // The passive vent holds Q's pressure, so it moves nothing (CONT-016's threshold).
            ZoneInfo q = a.zone(7, 2, 3);
            PipeNodeEntity passive = at(7, 1, 4);
            double moles = q.mixture().pressure()
                    * passive.nodeVolume()
                    / (GasMixture.GAS_CONSTANT * q.mixture().temperature());
            passive.network()
                    .gas()
                    .add(new GasParcel(Map.of(N2, moles), q.mixture().temperature()));

            PipeNodeEntity n1 = at(12, 1, 10);
            n1.network().gas().add(new GasParcel(Map.of(N2, 60.0, O2, 15.0), 300.0));
            this.<CanisterBlockEntity>at(12, 3, 10).setReleasePressure(150);
            PipeNodeEntity n2 = at(30, 1, 12);
            n2.network().gas().add(new GasParcel(Map.of(CO2, 20.0), 280.0));
            CanisterBlockEntity canister = at(2, 1, 12);
            canister.network().gas().add(new GasParcel(Map.of(O2, 30.0, H2, 1.25), 280.0));
            canister.setReleasePressure(250);

            PumpBlockEntity pressure = at(2, 1, 14);
            pressure.set(345);
            PumpBlockEntity volume = at(4, 1, 14);
            volume.set(120);
            FilterBlockEntity filter = at(6, 1, 14);
            filter.setSelection(GasSelection.of(List.of(O2), List.of()));
            filter.setTarget(222);
            MixerBlockEntity mixer = at(8, 1, 14);
            mixer.setBackPercent(35);
            mixer.setTarget(180);
            ElectrolyzerBlockEntity electrolyzer = at(10, 1, 14);
            electrolyzer.tank().fill(new FluidStack(Fluids.WATER, 2500), IFluidHandler.FluidAction.EXECUTE);
            int gas = 0;
            for (InlineDeviceEntity device :
                    List.<InlineDeviceEntity>of(pressure, volume, filter, mixer, electrolyzer)) {
                device.setOn(true); // with no power to run on
                int ports = ((PortedBlock) device.getBlockState().getBlock())
                        .portFaces(device.getBlockState())
                        .size();
                for (int i = 0; i < ports; i++) {
                    gas++;
                    device.port(i)
                            .network()
                            .gas()
                            .add(new GasParcel(Map.of(N2, 1.0 + gas / 8.0, O2, gas / 16.0), 290.0 + gas));
                }
            }

            PortablePumpBlockEntity pump = at(17, 1, 8);
            pump.setSiphon(true);
            pump.setTarget(450);
            PortableGasDevice scrubberPortable = at(19, 1, 8);
            ThermoMachineBlockEntity heater = at(21, 1, 8);
            heater.setSetpoint(305);
            ThermoMachineBlockEntity conditioner = at(23, 1, 8);
            conditioner.setSetpoint(285);
            int charge = 1000;
            for (PortableDevice d : List.<PortableDevice>of(pump, scrubberPortable, heater, conditioner)) {
                BatteryTests.swap(d.battery(), battery(charge += 777));
            }
            pump.store().add(new GasParcel(Map.of(CO2, 4.0, N2, 1.5), 301.0));
            pump.tank().insertItem(0, tank(Map.of(O2, 3.0), 290.0), false);
            scrubberPortable.store().add(new GasParcel(Map.of(CO2, 2.25), 296.0));
            scrubberPortable.tank().insertItem(0, tank(Map.of(N2, 1.75, CO2, 0.25), 288.0), false);
            BatteryTests.swap(this.<RechargerBlockEntity>at(25, 1, 8).battery(), battery(4321));

            GeneratorBlockEntity generator = at(27, 1, 8);
            generator.fuel().insertItem(0, new ItemStack(Items.COAL, 5), false);
            generator.energy().set(12_345);
            CompoundTag burning = generator.saveCustomOnly(registries);
            burning.putInt("BurnLeft", 321);
            burning.putInt("BurnTotal", 1600);
            generator.loadCustomOnly(burning, registries);
            h.assertFalse(
                    a.level.shouldTickBlocksAt(generator.getBlockPos()),
                    "the generator's chunk ticks blocks, so its fuel would burn");

            ChestBlockEntity chest = at(2, 1, 8);
            chest.setItem(0, tank(Map.of(O2, 5.5, N2, 0.125), 293.0));
            chest.setItem(1, ttv(450));
            ItemStack suit = new ItemStack(AtmosItems.HARDSUIT_CHESTPLATE.get());
            SuitItem.setTank(suit, tank(Map.of(O2, 2.375), 291.0));
            chest.setItem(2, suit);
            ItemStack mined = new ItemStack(AtmosItems.CANISTER.get());
            mined.set(AtmosComponents.GAS.get(), new GasParcel(Map.of(N2, 40.0, CO2, 3.5), 287.0));
            chest.setItem(3, mined);
            chest.setItem(4, battery(2468));

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(Vec3.atBottomCenterOf(a.abs(6, 1, 8)));
            PrimedTransferValve entity = PrimedTransferValve.prime(a.level, player, ttv(TransferValve.MAX_TIMER));
            primed = entity.getUUID();

            // The placed TTV's gases are cold and apart, so they never react.
            placedTtv = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
            TransferValveItem.save(
                    placedTtv, new TransferValve(tank(Map.of(O2, 3.125), 290.0), tank(Map.of(H2, 2.25), 293.0), false));
            TransferValveItem.setTimer(placedTtv, PLACED_TIMER);
            TransferValveBlockEntity placed = at(PLACED_TTV);
            placed.applyComponentsFromItemStack(placedTtv);
            placed.setChanged();
            settingsBatch5();
        }

        /**
         * Batch 5's settings. The airlock controller is linked by arming each slot first, so each
         * slot is the one given and all five are sure, and is left with a slot armed. No area air
         * controller covers it, so it has no power and never cycles. The large airlock, its outer
         * door, opens and waits to close itself, which its scheduled tick does, and the station's
         * chunks don't tick. The radiator pipe's gas is at the open air's temperature, and the
         * exchangers' at one temperature, so no heat moves.
         */
        void settingsBatch5() {
            ServerLevel level = a.level;
            AirlockControllerBlockEntity lock = at(AIRLOCK_CONTROLLER);
            BlockPos[] slotted = slotted();
            for (Slot slot : Slot.values()) {
                lock.arm(slot);
                h.assertValueEqual(
                        lock.toggleLink(level, slotted[slot.ordinal()]),
                        AirlockControllerBlockEntity.LinkResult.LINKED,
                        "linking the airlock controller's " + slot.key());
            }
            lock.toggleLink(level, abs(LOCK_VENT));
            lock.arm(Slot.OUTER_SENSOR);
            AirlockBlock.setBolted(level, abs(BOLTED), true);
            h.assertTrue(AirlockBlock.setOpen(level, abs(LARGE), true, null), "the large airlock didn't open");
            largeCloseAt = this.<AirlockBlockEntity>at(LARGE).closeAt();
            h.assertTrue(largeCloseAt > level.getGameTime(), "the large airlock isn't waiting to close itself");
            h.assertValueEqual(lock.lastOpened(), AirlockCycle.OUTER, "the controller didn't see its outer door open");

            h.assertTrue(AirlockBlock.fitFirelock(level, abs(FIRELOCKED)), "no firelock fitted to the airlock");
            this.<AirlockBlockEntity>at(FIRELOCKED).firelockDoor().setOpen(level, false);
            h.assertTrue(FirelockDoorBlock.fit(level, abs(DOOR)), "no firelock fitted to the spruce door");
            this.<FirelockDoorBlockEntity>at(DOOR).firelockDoor().setOpen(level, false);

            TankHolderBlockEntity holder = at(HOLDER);
            for (ItemStack tank : List.of(
                    tank(AtmosItems.OXYGEN_TANK.get(), Map.of(O2, 4.25), 289.0),
                    tank(AtmosItems.AIR_TANK.get(), Map.of(N2, 3.0, O2, 0.8125), 294.0),
                    tank(AtmosItems.EMPTY_TANK.get(), Map.of(CO2, 0.5, N2, 0.375), 301.0),
                    tank(AtmosItems.EMERGENCY_OXYGEN_TANK.get(), Map.of(O2, 0.625), 285.0))) {
                held.add(tank.copy());
                h.assertTrue(holder.put(tank), "the tank holder refused " + tank);
            }

            AtmosphereSample air = Atmospheres.at(level, abs(RADIATOR));
            h.assertValueEqual(air.state(), AtmosphereState.AMBIENT, "the radiator pipe's cell");
            PipeNodeEntity radiator = at(RADIATOR);
            h.assertTrue(
                    this.<PipeNodeEntity>at(17, 1, 14).network() == radiator.network()
                            && this.<PipeNodeEntity>at(19, 1, 14).network() == radiator.network(),
                    "the cap, the pipe, the adaptor and the radiator pipe aren't one network");
            radiator.network()
                    .gas()
                    .add(new GasParcel(Map.of(N2, 6.0, O2, 1.5), air.mixture().temperature()));
            PipeNodeEntity exchanger = at(EXCHANGER);
            PipeNodeEntity partner = at(PARTNER);
            h.assertTrue(exchanger.network() != partner.network(), "the exchangers share a network");
            exchanger.network().gas().add(new GasParcel(Map.of(N2, 9.0), EXCHANGER_K));
            partner.network().gas().add(new GasParcel(Map.of(CO2, 4.5), EXCHANGER_K));
            this.<PipeNodeEntity>at(LOCK_VENT).network().gas().add(new GasParcel(Map.of(N2, 0.75), 290.0));
        }

        /** What fills each of the airlock controller's slots, in {@link Slot}'s order. */
        BlockPos[] slotted() {
            return new BlockPos[] {abs(BOLTED), abs(LARGE), abs(SENSOR_IN), abs(SENSOR_OUT), abs(SENSOR_Q)};
        }

        PrimedTransferValve primed() {
            if (!(a.level.getEntity(primed) instanceof PrimedTransferValve p)) {
                throw new GameTestAssertException("no primed TTV");
            }
            return p;
        }

        /** Everything the station holds now, in the chunks that are loaded. */
        Snapshot snapshot() {
            Snapshot s = new Snapshot();
            for (BlockPos p : BlockPos.betweenClosed(a.abs(0, 0, 0), a.abs(47, 6, 15))) {
                if (!a.level.isLoaded(p)) {
                    continue;
                }
                BlockPos pos = p.immutable();
                s.states.put(pos, a.level.getBlockState(pos));
                BlockEntity be = a.level.getBlockEntity(pos);
                if (be != null) {
                    s.saved.put(pos, be.saveWithoutMetadata(registries));
                    s.entities.put(pos, be);
                    if (be instanceof PipeNodeEntity node) {
                        s.volumes.put(pos, node.nodeVolume());
                    }
                }
            }
            zone(s, "R", 3, 2, 3);
            zone(s, "Q", 7, 2, 3);
            zone(s, "P", 30, 2, 3);
            network(s, "N1", 12, 1, 10);
            network(s, "N2 in B", 30, 1, 12); // all of N2 while C is loaded
            network(s, "the radiator pipe's", RADIATOR.getX(), RADIATOR.getY(), RADIATOR.getZ());
            network(s, "the leading exchanger's", EXCHANGER.getX(), EXCHANGER.getY(), EXCHANGER.getZ());
            network(s, "its partner's", PARTNER.getX(), PARTNER.getY(), PARTNER.getZ());
            if (a.level.getEntity(primed) instanceof PrimedTransferValve p) {
                s.primed = primed;
                s.primedSaved = p.saveWithoutId(new CompoundTag());
            }
            s.claims = CentralClaims.of(a.level).save(new CompoundTag(), registries);
            return s;
        }

        private void zone(Snapshot s, String name, int x, int y, int z) {
            Atmospheres.zoneAt(a.level, a.abs(x, y, z)).ifPresent(zone -> s.zones.put(name, zone));
        }

        private void network(Snapshot s, String name, int x, int y, int z) {
            if (a.level.isLoaded(a.abs(x, y, z))) {
                GasStore gas = this.<PipeNodeEntity>at(x, y, z).network().gas();
                s.networks.put(name, new GasParcel(gas.moles(), gas.temperature()));
            }
        }

        ZoneInfo zone(ZoneId id) {
            return Atmospheres.zone(a.level, id).orElseThrow(() -> new GameTestAssertException("no zone " + id));
        }

        /** Whether the zones have left memory, with every chunk they span unloaded: R, Q and P. */
        void zonesGone(Snapshot before) {
            for (String name : List.of("R", "Q", "P")) {
                h.assertTrue(
                        Atmospheres.zone(a.level, before.zones.get(name).id()).isEmpty(), name + " hasn't left memory");
            }
        }

        /** Whether the zones are back: R and Q, and P, paused, under the ids they had. */
        void zonesBack(Snapshot before) {
            for (String name : List.of("R", "Q", "P")) {
                h.assertTrue(
                        Atmospheres.zone(a.level, before.zones.get(name).id()).isPresent(), name + " isn't back");
            }
            h.assertTrue(zone(before.zones.get("P").id()).paused(), "P isn't paused");
        }

        /**
         * Compares what the given chunks hold with what they held. Block states and block entities'
         * saved tags match, numbers within 1e-9; R, Q, N1 and the primed TTV match; the claims
         * match. A network or zone across a chunk that isn't loaded is left to the caller.
         */
        void compare(Snapshot now, Snapshot then, String when, int... which) {
            for (int i : which) {
                ChunkPos chunk = chunks[i];
                then.states.forEach((pos, state) -> {
                    if (new ChunkPos(pos).equals(chunk)) {
                        h.assertValueEqual(now.states.get(pos), state, when + ": the block at " + rel(pos));
                    }
                });
                then.saved.forEach((pos, tag) -> {
                    if (new ChunkPos(pos).equals(chunk)) {
                        CompoundTag back = now.saved.get(pos);
                        h.assertTrue(back != null, when + ": no block entity at " + rel(pos));
                        sameTag(h, back, tag, when + ": " + name(then.entities.get(pos)) + " at " + rel(pos));
                    }
                });
            }
            sameZone(now.zones.get("R"), then.zones.get("R"), when + ": R");
            sameZone(now.zones.get("Q"), then.zones.get("Q"), when + ": Q");
            sameParcel(now.networks.get("N1"), then.networks.get("N1"), when + ": N1");
            for (String net : List.of("the radiator pipe's", "the leading exchanger's", "its partner's")) {
                sameParcel(now.networks.get(net), then.networks.get(net), when + ": " + net + " network");
            }
            batch5(now, when);
            placedTtv(when);
            h.assertTrue(now.primed != null, when + ": no primed TTV");
            for (String key : List.of("First", "Second", "Open", "Timer", "Fuse", "Settle")) {
                sameTag(h, now.primedSaved.get(key), then.primedSaved.get(key), when + ": the primed TTV's " + key);
            }
            sameTag(h, now.claims, then.claims, when + ": the central controllers' claims");
            if (Arrays.stream(which).anyMatch(i -> i == 2)) {
                sameParcel(now.networks.get("N2 in B"), then.networks.get("N2 in B"), when + ": N2");
            }
        }

        /**
         * The placed TTV holds what it was given, on top of its saved tag matching: both tanks, each
         * with its gas, its valve shut and its timer; and its block shows two tanks, facing east.
         */
        void placedTtv(String when) {
            ItemStack now = this.<TransferValveBlockEntity>at(PLACED_TTV).item();
            for (int place : List.of(TransferValve.FIRST, TransferValve.SECOND)) {
                sameGas(
                        h,
                        TransferValveItem.tank(now, place),
                        TransferValveItem.tank(placedTtv, place),
                        when + ": the placed TTV's tank " + place);
            }
            h.assertFalse(TransferValveItem.valve(now).isOpen(), when + ": the placed TTV's valve is open");
            h.assertValueEqual(TransferValveItem.timer(now), PLACED_TIMER, when + ": the placed TTV's timer");
            BlockState state = state(PLACED_TTV);
            h.assertValueEqual(state.getValue(TransferValveBlock.TANKS), 2, when + ": the tanks the placed TTV shows");
            h.assertValueEqual(
                    state.getValue(TransferValveBlock.FACING), Direction.EAST, when + ": the placed TTV's facing");
        }

        /**
         * Batch 5's parts hold what {@link #settingsBatch5} gave them, on top of their saved tags
         * matching: the holder's tanks in order, each with its gas; the exchangers' bands; the
         * airlocks' bolts, firelocks and pending close; the door under the firelock; the airlock
         * controller's slots, links and last door, with no power; and each sensor's status.
         */
        void batch5(Snapshot now, String when) {
            TankHolderBlockEntity holder = at(HOLDER);
            h.assertValueEqual(holder.count(), held.size(), when + ": the tanks in the holder");
            h.assertValueEqual(
                    state(HOLDER).getValue(TankHolderBlock.TANKS), held.size(), when + ": the tanks the holder shows");
            for (int i = 0; i < TankHolderBlockEntity.PLACES; i++) {
                ItemStack tank = holder.tanks().getStackInSlot(i);
                String place = when + ": the holder's place " + i;
                if (i >= held.size()) {
                    h.assertTrue(tank.isEmpty(), place + " holds " + tank);
                    continue;
                }
                ItemStack put = held.get(i);
                h.assertTrue(tank.is(put.getItem()), place + " holds " + tank + ", not " + put);
                sameGas(h, tank, put, place + "'s tank");
                close(
                        h,
                        GasTankItem.distributionPressure(tank),
                        GasTankItem.distributionPressure(put),
                        place + "'s distribution pressure");
            }

            for (BlockPos x : List.of(EXCHANGER, PARTNER)) {
                PipeNodeEntity node = at(x);
                int band = state(x).getValue(HeatExchangerBlock.HEAT);
                h.assertValueEqual(
                        band,
                        HeatExchangerTint.band(node.network().gas().temperature()),
                        when + ": the band of the exchanger at " + rel(abs(x)));
                h.assertValueEqual(band, HeatExchangerTint.band(EXCHANGER_K), when + ": the band at 450 K");
            }

            BlockState bolted = state(BOLTED);
            h.assertTrue(
                    bolted.getValue(AirlockBlock.BOLTED) && !bolted.getValue(AirlockBlock.OPEN),
                    when + ": the bolted airlock is " + bolted);
            h.assertTrue(state(LARGE).getValue(AirlockBlock.OPEN), when + ": the large airlock isn't open");
            AirlockBlockEntity large = at(LARGE);
            h.assertValueEqual(large.closeAt(), largeCloseAt, when + ": when the large airlock closes itself");
            h.assertTrue(
                    a.level.getBlockTicks().hasScheduledTick(abs(LARGE), AtmosBlocks.LARGE_AIRLOCK.get()),
                    when + ": the large airlock's closing isn't scheduled");
            h.assertValueEqual(
                    state(FIRELOCKED).getValue(AirlockBlock.FIRELOCK),
                    AirlockFirelock.LOCKED,
                    when + ": the airlock's firelock");
            AirlockBlockEntity firelocked = at(FIRELOCKED);
            h.assertTrue(
                    firelocked
                            .firelock()
                            .lockdown()
                            .map(l -> l.reason() == Lockdown.Reason.PRESSURE)
                            .orElse(false),
                    when + ": the airlock's firelock isn't locked down by pressure");

            h.assertValueEqual(
                    state(DOOR).getValue(FirelockDoorBlock.FIRELOCK),
                    AirlockFirelock.CLOSED,
                    when + ": the door's firelock");
            for (int y = 0; y <= 1; y++) {
                h.assertValueEqual(
                        this.<FirelockDoorBlockEntity>at(DOOR.above(y)).door(),
                        Blocks.SPRUCE_DOOR,
                        when + ": the door under the firelock, " + (y == 0 ? "lower" : "upper"));
            }

            AirlockControllerBlockEntity lock = at(AIRLOCK_CONTROLLER);
            BlockPos[] slotted = slotted();
            for (Slot slot : Slot.values()) {
                h.assertValueEqual(
                        lock.slot(slot), slotted[slot.ordinal()], when + ": the airlock controller's " + slot.key());
            }
            CompoundTag saved = lock.saveWithoutMetadata(registries);
            h.assertValueEqual(saved.getInt("Sure"), 0b11111, when + ": the slots the controller is sure of");
            h.assertValueEqual(lock.armed(), Slot.OUTER_SENSOR, when + ": the controller's armed slot");
            h.assertValueEqual(lock.linkedVents(), Set.of(abs(LOCK_VENT)), when + ": the controller's vents");
            h.assertValueEqual(lock.lastOpened(), AirlockCycle.OUTER, when + ": the door the controller opened last");
            h.assertValueEqual(lock.cycling(), -1, when + ": the controller is cycling");
            h.assertFalse(lock.power().isPowered(), when + ": the airlock controller has power");
            VentBlockEntity vent = at(LOCK_VENT);
            h.assertTrue(vent.airlockControlled(), when + ": the linked vent isn't the controller's");
            h.assertValueEqual(vent.settings().mode(), VentMode.OFF, when + ": the linked vent");

            double q = now.zones.get("Q").mixture().pressure();
            AirSensorBlockEntity inQ = at(SENSOR_Q);
            close(h, inQ.pressure(), q, when + ": the pressure the sensor in Q read");
            h.assertValueEqual(
                    state(SENSOR_Q).getValue(AirSensorBlock.STATUS),
                    SensorStatus.ALERT,
                    when + ": the sensor in Q, at " + q + " kPa");
            for (BlockPos sensor : List.of(SENSOR_IN, SENSOR_OUT)) {
                h.assertValueEqual(
                        state(sensor).getValue(AirSensorBlock.STATUS),
                        SensorStatus.STANDBY,
                        when + ": the sensor in the open air at " + rel(abs(sensor)));
            }
        }

        /**
         * The large airlock's scheduled tick, run here as it runs where a chunk ticks: once its time
         * has come, it closes the airlock. The time it reads is the one the save kept.
         */
        void closeLargeAirlock() {
            BlockPos pos = abs(LARGE);
            h.assertTrue(a.level.getGameTime() >= largeCloseAt, "the large airlock's closing isn't due");
            a.level.getBlockState(pos).tick(a.level, pos, a.level.random);
            h.assertFalse(state(LARGE).getValue(AirlockBlock.OPEN), "the large airlock didn't close itself");
            h.assertValueEqual(this.<AirlockBlockEntity>at(LARGE).closeAt(), -1L, "the large airlock's closing");
        }

        /**
         * With only A and B loaded: P is paused with B's share, the same pressure, temperature and
         * mixture by volume as before, and N2's part in B holds B's shares.
         */
        void assertPausedShare(Snapshot now, Snapshot then) {
            ZoneInfo before = then.zones.get("P");
            ZoneInfo paused = zone(before.id());
            h.assertTrue(paused.paused(), "P isn't paused");
            h.assertTrue(paused.cellCount() < before.cellCount(), "P has all its cells with C away");
            a.close(paused.mixture().pressure(), before.mixture().pressure(), "P's pressure, paused");
            a.close(paused.mixture().temperature(), before.mixture().temperature(), "P's temperature, paused");
            double share = paused.volume() / before.volume();
            for (ResourceLocation gas : before.mixture().moles().keySet()) {
                a.close(paused.mixture().moles(gas), before.mixture().moles(gas) * share, "P's " + gas + ", paused");
            }

            GasParcel whole = then.networks.get("N2 in B");
            double inB = 0;
            double all = 0;
            for (BlockPos pos : nodesOf(then, 12, 28, 36)) {
                all += then.volumes.get(pos);
                if (pos.getX() - a.origin.getX() < 32) {
                    inB += then.volumes.get(pos);
                }
            }
            GasParcel half = now.networks.get("N2 in B");
            for (ResourceLocation gas : whole.moles().keySet()) {
                a.close(half.moles(gas), whole.moles(gas) * inB / all, "N2's " + gas + " in B, C away");
            }
            a.close(half.temperature(), whole.temperature(), "N2's temperature in B, C away");
        }

        /** The positions of a row's nodes. */
        List<BlockPos> nodesOf(Snapshot s, int z, int x0, int x1) {
            List<BlockPos> out = new ArrayList<>();
            for (int x = x0; x <= x1; x++) {
                BlockPos pos = a.abs(x, 1, z);
                h.assertTrue(s.volumes.containsKey(pos), "no node at " + x + ", 1, " + z);
                out.add(pos);
            }
            return out;
        }

        /**
         * What the unloads wrote: each pipe part of N1 and N2 holds its share of its network by
         * volume (DATA-016), read back from the chunks' saved data.
         */
        void assertWrittenByVolume(Snapshot then) {
            Map<BlockPos, CompoundTag> written = new HashMap<>();
            for (ChunkPos chunk : chunks) {
                CompoundTag saved = a.level
                        .getChunkSource()
                        .chunkMap
                        .read(chunk)
                        .join()
                        .orElseThrow(() -> new GameTestAssertException("chunk " + chunk + " wasn't written"));
                ListTag list = saved.getList("block_entities", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag be = list.getCompound(i);
                    written.put(new BlockPos(be.getInt("x"), be.getInt("y"), be.getInt("z")), be);
                }
            }
            List<BlockPos> n1 = new ArrayList<>(nodesOf(then, 10, 11, 20));
            n1.add(a.abs(12, 2, 10));
            n1.add(a.abs(12, 3, 10));
            byVolume(written, n1, then.networks.get("N1"), then, "N1");
            byVolume(written, nodesOf(then, 12, 28, 36), then.networks.get("N2 in B"), then, "N2");
        }

        private void byVolume(
                Map<BlockPos, CompoundTag> written, List<BlockPos> nodes, GasParcel whole, Snapshot then, String name) {
            double volume = 0;
            for (BlockPos pos : nodes) {
                volume += then.volumes.get(pos);
            }
            for (BlockPos pos : nodes) {
                CompoundTag be = written.get(pos);
                h.assertTrue(be != null && be.contains("Gas"), name + ": nothing written at " + rel(pos));
                GasParcel share = GasStore.parcel(be.getCompound("Gas"));
                for (ResourceLocation gas : whole.moles().keySet()) {
                    a.close(
                            share.moles(gas),
                            whole.moles(gas) * then.volumes.get(pos) / volume,
                            name + ": the " + gas + " written at " + rel(pos));
                }
                a.close(share.temperature(), whole.temperature(), name + ": the temperature written at " + rel(pos));
            }
        }

        /**
         * The claims, written and read back as the level's saved data is at a restart, while the
         * station is away: the table in use from now on is the one read back.
         */
        void reloadClaims() {
            CompoundTag written = throughBytes(CentralClaims.of(a.level).save(new CompoundTag(), registries));
            a.level.getDataStorage().set(CentralClaims.NAME, CentralClaims.load(written, registries));
        }

        void sameZone(ZoneInfo now, ZoneInfo then, String what) {
            h.assertTrue(now != null, what + ": no zone");
            h.assertValueEqual(now.id(), then.id(), what + "'s id");
            h.assertFalse(now.paused(), what + " is paused");
            h.assertValueEqual(now.cellCount(), then.cellCount(), what + "'s cells");
            sameParcel(now.mixture().parcel(), then.mixture().parcel(), what);
        }

        void sameParcel(GasParcel now, GasParcel then, String what) {
            h.assertTrue(now != null, what + ": not loaded");
            h.assertValueEqual(now.moles().keySet(), then.moles().keySet(), what + "'s gases");
            then.moles().forEach((gas, n) -> a.close(now.moles(gas), n, what + "'s " + gas));
            a.close(now.temperature(), then.temperature(), what + "'s temperature");
        }

        String rel(BlockPos pos) {
            BlockPos r = pos.subtract(a.origin);
            return r.getX() + ", " + r.getY() + ", " + r.getZ();
        }

        static String name(BlockEntity be) {
            return be == null ? "?" : be.getClass().getSimpleName();
        }

        void done() {
            hold(true, 0, 1, 2);
            if (a.level.getEntity(primed) instanceof PrimedTransferValve p) {
                p.discard();
            }
            if (a.level.getBlockEntity(a.abs(2, 1, 8)) instanceof ChestBlockEntity chest) {
                chest.clearContent();
            }
            if (a.level.getBlockEntity(abs(HOLDER)) instanceof TankHolderBlockEntity holder) {
                while (holder.count() > 0) {
                    holder.takeLast(); // broken, it would drop them
                }
            }
            clear();
            hold(false, 0, 1, 2);
        }

        /** Clears the station's box, the console first by its lower left block, which takes the rest. */
        void clear() {
            if (a.level.getBlockState(abs(CONSOLE)).getBlock() instanceof CentralControllerBlock) {
                a.level.setBlockAndUpdate(abs(CONSOLE), Area.AIR);
            }
            a.clear(0, 0, 47, 15);
        }
    }

    /** An atmos tick's own draw for an FE key. */
    private static int own(ModConfigSpec.IntValue draw) {
        return AtmosServerConfig.get(draw) * AtmosTicks.PERIOD;
    }

    static ItemStack tank(Map<ResourceLocation, Double> moles, double kelvin) {
        return tank(AtmosItems.EMPTY_TANK.get(), moles, kelvin);
    }

    static ItemStack tank(Item kind, Map<ResourceLocation, Double> moles, double kelvin) {
        ItemStack tank = new ItemStack(kind);
        GasTankItem.setGas(tank, new GasParcel(moles, kelvin));
        GasTankItem.setDistributionPressure(tank, 55.5);
        return tank;
    }

    static ItemStack ttv(int timer) {
        ItemStack ttv = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(
                ttv,
                new TransferValve(tank(Map.of(H2, 1.5), 293.0), tank(Map.of(O2, 0.875, CO2, 0.0625), 460.0), false));
        TransferValveItem.setTimer(ttv, timer);
        return ttv;
    }

    static ItemStack battery(int energy) {
        ItemStack battery = AtmosItems.BATTERY.get().full();
        battery.set(AtmosComponents.ENERGY.get(), energy);
        return battery;
    }

    // ---- S-09 and DATA-016: an airlock cycle saved midway ------------------------------------

    /** The airlock's site, in {@link Site}'s numbering: one chunk, x 0 to 10 and z 0 to 4 of it. */
    private static final int LOCK_SITE = 2805;

    /**
     * An airlock controller saved to disk midway through a cycle and loaded again takes the cycle up
     * and finishes it (DATA-016, CONT-139, CONT-141). It is {@link AirlockTests#lock}'s airlock, with
     * B and the chamber at 200 kPa and A at 100. It cycles out to B, which opens B's door at once,
     * and then back toward A, siphoning; two steps in, its chunk unloads with nothing else holding it,
     * and loads again.
     *
     * <p>Every block entity's saved tag comes back as it was, numbers within 1e-9, and so do the
     * zones and the vent's network. The controller keeps its five slots, the slots it is sure of,
     * the armed slot, its vent, its cycle and the door it opened last. Its lease of power is never
     * saved, so it comes back unpowered, and so do its vent and its cycle's step. Once the area air
     * controller pays for it again, the cycle goes on, with the vent siphoning, and ends with A's
     * door open, unbolted, on a chamber within 5.07 kPa of A.
     *
     * <p>The chunk is held loaded in full only, so the schedule steps nothing there; the test steps
     * the airlock's devices, as {@link AirlockTests} does, and drives atmos ticks with {@code tickNow},
     * so it has a batch of its own.
     */
    @GameTest(template = "empty", batch = LOCK_BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void s_09_an_airlock_cycle_saved_midway_resumes_and_finishes(GameTestHelper h) {
        Site s = new Site(h, LOCK_SITE);
        ChunkPos chunk = new ChunkPos(s.origin);
        PipeSaveTests.hold(s.level, chunk, true);
        LOCK_CLEANUP.add(() -> {
            PipeSaveTests.hold(s.level, chunk, true);
            BatteryTests.clear(s);
            PipeSaveTests.hold(s.level, chunk, false);
        });
        AirlockTests.Lock before = AirlockTests.lock(s, true, false);
        AirlockControllerBlockEntity ac = before.controller();
        AirlockTests.fill(s, 8, 2, 2, 158, 42);
        AirlockTests.fill(s, 5, 2, 2, 158, 42);
        h.assertTrue(ac.requestCycle(AirlockCycle.OUTER), "the controller refused a cycle out to B");
        for (int i = 0; i < 4 && ac.cycling() >= 0; i++) {
            s.cycle(before.devices());
        }
        h.assertValueEqual(ac.cycling(), -1, "the cycle out to B, at the chamber's pressure, hasn't ended");
        h.assertValueEqual(ac.lastOpened(), AirlockCycle.OUTER, "the door opened last");
        h.assertTrue(ac.requestCycle(AirlockCycle.INNER), "the controller refused a cycle in to A");
        // Until the vent has siphoned: it waits a step or two for the chamber's cell to settle.
        GasStore siphoned = before.vents().get(0).network().gas();
        int stepped = 0;
        while (stepped < 8 && (stepped < 2 || siphoned.isEmpty())) {
            s.cycle(before.devices());
            stepped++;
        }
        s.tick();
        int steps = stepped;
        h.assertFalse(siphoned.isEmpty(), "the vent hasn't siphoned in " + steps + " steps");
        ac.arm(Slot.CHAMBER_SENSOR);
        h.assertValueEqual(ac.cycleStep(), AirlockCycle.Step.SIPHON, "the cycle's step as it's saved");
        double savedKpa = ac.chamberKpa();
        h.assertTrue(
                savedKpa > ac.beyondKpa(AirlockCycle.INNER) + AirlockCycle.TOLERANCE_KPA,
                "the chamber reached A's pressure before the save: " + savedKpa + " kPa");

        LockSave was = new LockSave(s);
        PipeSaveTests.hold(s.level, chunk, false);
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (BlockEntity be : was.entities.values()) {
                        h.assertTrue(be.isRemoved(), "the airlock hasn't unloaded: " + be.getBlockPos());
                    }
                })
                .thenWaitUntil(() -> {
                    for (ZoneInfo zone : was.zones.values()) {
                        h.assertTrue(Atmospheres.zone(s.level, zone.id()).isEmpty(), "a zone hasn't left memory");
                    }
                })
                .thenExecute(() -> PipeSaveTests.hold(s.level, chunk, true))
                .thenWaitUntil(() -> h.assertTrue(s.level.getChunkSource().hasChunk(chunk.x, chunk.z), "not loaded"))
                .thenWaitUntil(() -> {
                    for (Map.Entry<String, ZoneInfo> e : was.zones.entrySet()) {
                        h.assertTrue(
                                Atmospheres.zone(s.level, e.getValue().id()).isPresent(), e.getKey() + " isn't back");
                    }
                })
                .thenExecute(() -> {
                    LockSave now = new LockSave(s);
                    now.compare(h, was);
                    AirlockTests.Lock lock = now.lock(before);
                    AirlockControllerBlockEntity back = lock.controller();
                    h.assertValueEqual(back.cycling(), AirlockCycle.INNER, "the cycle, reloaded");
                    h.assertTrue(back.cycleStep() == null, "the cycle's step was saved: " + back.cycleStep());
                    h.assertValueEqual(back.lastOpened(), AirlockCycle.OUTER, "the door opened last, reloaded");
                    h.assertValueEqual(back.armed(), Slot.CHAMBER_SENSOR, "the armed slot, reloaded");
                    BlockPos[] slotted = before.slotted();
                    for (Slot slot : Slot.values()) {
                        h.assertValueEqual(back.slot(slot), slotted[slot.ordinal()], slot.key() + ", reloaded");
                    }
                    h.assertValueEqual(
                            back.linkedVents(), Set.of(before.vents().get(0).getBlockPos()), "the vents, reloaded");
                    h.assertFalse(back.power().isPowered(), "the airlock controller's lease was saved");
                    h.assertFalse(lock.vents().get(0).power().isPowered(), "the vent's lease was saved");
                    for (AirSensorBlockEntity sensor : lock.sensors()) {
                        h.assertValueEqual(
                                sensor.getBlockState().getValue(AirSensorBlock.STATUS),
                                SensorStatus.CYCLING,
                                "a sensor, reloaded");
                    }
                    for (AirlockBlockEntity airlock : List.of(lock.inner(), lock.outer())) {
                        BlockState st = airlock.getBlockState();
                        h.assertTrue(
                                st.getValue(AirlockBlock.BOLTED) && !st.getValue(AirlockBlock.OPEN),
                                "an airlock, reloaded: " + st);
                    }

                    int waited = 0;
                    while (waited < 8 && !back.power().isPowered()) {
                        s.cycle(lock.devices());
                        waited++;
                    }
                    h.assertTrue(back.power().source() == lock.area(), "the area air controller doesn't power it");
                    h.assertValueEqual(back.cycling(), AirlockCycle.INNER, "the cycle, once powered again");
                    h.assertValueEqual(
                            back.cycleStep(), AirlockCycle.Step.SIPHON, "the cycle's step, once powered again");
                    AirlockTests.cycle(h, s, lock, AirlockCycle.INNER, VentMode.SIPHON);
                    h.assertTrue(
                            lock.inner().getBlockState().getValue(AirlockBlock.OPEN)
                                    && !lock.inner().getBlockState().getValue(AirlockBlock.BOLTED),
                            "A's door isn't open and unbolted");
                    BlockState outer = lock.outer().getBlockState();
                    h.assertTrue(
                            outer.getValue(AirlockBlock.BOLTED) && !outer.getValue(AirlockBlock.OPEN),
                            "B's door isn't bolted shut");
                    h.assertValueEqual(back.lastOpened(), AirlockCycle.INNER, "the door opened last, at the end");
                    LOGGER.info(
                            "s_09 airlock: saved {} steps into the cycle at {} kPa, cycling to {} kPa, with {} mol in"
                                    + " the vent; reloaded, it waited {} steps for power and opened A's door at {} kPa",
                            steps,
                            savedKpa,
                            was.zones.get("A").mixture().pressure(),
                            was.vent.totalMoles(),
                            waited,
                            back.chamberKpa());
                })
                .thenSucceed();
    }

    /** What the airlock's site holds: each block entity's saved tag, its zones and its vent's network. */
    private static final class LockSave {
        final Site s;
        final Map<BlockPos, CompoundTag> saved = new LinkedHashMap<>();
        final Map<BlockPos, BlockEntity> entities = new LinkedHashMap<>();
        final Map<String, ZoneInfo> zones = new LinkedHashMap<>();
        final GasParcel vent;

        LockSave(Site s) {
            this.s = s;
            HolderLookup.Provider registries = s.level.registryAccess();
            for (BlockPos p : BlockPos.betweenClosed(s.abs(0, 0, 0), s.abs(10, 4, 4))) {
                BlockEntity be = s.level.getBlockEntity(p);
                if (be != null) {
                    CompoundTag tag = be.saveWithoutMetadata(registries);
                    // Shown while a controller orders the vent, and never loaded: it orders it again.
                    tag.remove("Controlled");
                    tag.remove("Controller");
                    saved.put(p.immutable(), tag);
                    entities.put(p.immutable(), be);
                }
            }
            for (String room : List.of("A", "chamber", "B")) {
                int x = room.equals("A") ? 2 : room.equals("chamber") ? 5 : 8;
                zones.put(room, s.zone(x, 2, 2));
            }
            GasStore gas = s.entity(5, 0, 2, VentBlockEntity.class).network().gas();
            vent = new GasParcel(gas.moles(), gas.temperature());
        }

        void compare(GameTestHelper h, LockSave then) {
            h.assertValueEqual(saved.keySet(), then.saved.keySet(), "where the block entities are");
            then.saved.forEach((pos, tag) -> sameTag(
                    h,
                    saved.get(pos),
                    tag,
                    Station.name(then.entities.get(pos)) + " at "
                            + pos.subtract(s.origin).toShortString()));
            then.zones.forEach((room, zone) -> {
                ZoneInfo back = zones.get(room);
                h.assertValueEqual(back.id(), zone.id(), room + "'s id");
                h.assertFalse(back.paused(), room + " is paused");
                h.assertValueEqual(back.cellCount(), zone.cellCount(), room + "'s cells");
                sameParcel(h, back.mixture().parcel(), zone.mixture().parcel(), room);
            });
            sameParcel(h, vent, then.vent, "the vent's network");
        }

        /** The airlock's parts as they are now, in place of {@code was}'s. */
        AirlockTests.Lock lock(AirlockTests.Lock was) {
            return new AirlockTests.Lock(
                    now(was.area()),
                    now(was.controller()),
                    now(was.inner()),
                    now(was.outer()),
                    now(was.sensor()),
                    now(was.sensorA()),
                    now(was.sensorB()),
                    was.vents().stream().map(this::now).toList());
        }

        @SuppressWarnings("unchecked")
        private <T extends BlockEntity> T now(T was) {
            BlockEntity be = s.level.getBlockEntity(was.getBlockPos());
            if (be == null || be.getClass() != was.getClass() || be == was) {
                throw new GameTestAssertException("no new " + Station.name(was) + " at " + was.getBlockPos());
            }
            Site.loaded(be);
            return (T) be;
        }
    }

    private static void sameParcel(GameTestHelper h, GasParcel now, GasParcel then, String what) {
        h.assertValueEqual(now.moles().keySet(), then.moles().keySet(), what + "'s gases");
        then.moles().forEach((gas, n) -> close(h, now.moles(gas), n, what + "'s " + gas));
        close(h, now.temperature(), then.temperature(), what + "'s temperature");
    }

    // ---- DATA-016: a player's inventory ---------------------------------------------------

    /**
     * Tanks a player carries, in the inventory, the off hand, a suit's tank slot and a TTV, keep
     * their gas through the calls the game's player files use: the player's tag is written to
     * compressed bytes and read back into a new player.
     */
    @GameTest(template = "empty")
    public static void data_016_tanks_a_player_carries_keep_their_gas(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, tank(Map.of(O2, 3.25, N2, 0.5), 290.0));
        player.getInventory().setItem(5, ttv(30));
        ItemStack suit = new ItemStack(AtmosItems.HARDSUIT_CHESTPLATE.get());
        SuitItem.setTank(suit, tank(Map.of(O2, 1.125), 292.0));
        player.setItemSlot(EquipmentSlot.CHEST, suit);
        player.setItemInHand(InteractionHand.OFF_HAND, tank(Map.of(N2, 7.0, H2, 0.03125), 301.0));

        CompoundTag written = throughBytes(player.saveWithoutId(new CompoundTag()));
        Player back = h.makeMockPlayer(GameType.SURVIVAL);
        back.load(written);

        sameGas(h, back.getInventory().getItem(0), player.getInventory().getItem(0), "the tank in the inventory");
        sameGas(h, back.getOffhandItem(), player.getOffhandItem(), "the tank in the off hand");
        sameGas(
                h,
                SuitItem.tank(back.getItemBySlot(EquipmentSlot.CHEST)),
                SuitItem.tank(player.getItemBySlot(EquipmentSlot.CHEST)),
                "the suit's tank");
        for (int place : List.of(TransferValve.FIRST, TransferValve.SECOND)) {
            sameGas(
                    h,
                    TransferValveItem.tank(back.getInventory().getItem(5), place),
                    TransferValveItem.tank(player.getInventory().getItem(5), place),
                    "the TTV's tank " + place);
        }
        sameTag(
                h,
                back.saveWithoutId(new CompoundTag()).get("Inventory"),
                written.get("Inventory"),
                "the inventory saved again");
        h.succeed();
    }

    private static void sameGas(GameTestHelper h, ItemStack now, ItemStack then, String what) {
        GasParcel a = now.get(AtmosComponents.GAS.get());
        GasParcel b = then.get(AtmosComponents.GAS.get());
        h.assertTrue(a != null && b != null && !b.isEmpty(), what + ": no gas");
        h.assertValueEqual(a.moles().keySet(), b.moles().keySet(), what + "'s gases");
        b.moles().forEach((gas, n) -> close(h, a.moles(gas), n, what + "'s " + gas));
        close(h, a.temperature(), b.temperature(), what + "'s temperature");
    }

    // ---- #341: a valve at a chunk's edge --------------------------------------------------

    /**
     * What a valve on a chunk's edge costs by rebuilding twice as it opens (#341), against the same
     * valve away from the edge, in a single row of 30 nodes and in a snake of 7 rows (216 nodes)
     * across two chunks. Each opens for an atmos tick and shuts for two, over and over, with the
     * atmos ticks driven here by stepping the rig's own devices. The rows cross the same border in
     * both, so they have the same nodes on chunk edges, and only the valve's place differs.
     *
     * <p>It checks the rebuilds: two of the valve's network as it opens on the edge and one away
     * from it, one as it shuts in both, and none while it stays open. It logs each rig's median
     * time per atmos tick; times aren't checked, as CI's machines vary too much.
     */
    @GameTest(template = "empty", batch = EDGE_BATCH)
    public static void s_09_a_valve_at_a_chunk_edge_rebuilds_once_more_as_it_opens(GameTestHelper h) {
        List<Snake> rigs = List.of(
                new Snake(h, 2801, 1, 15), new Snake(h, 2802, 1, 8),
                new Snake(h, 2803, 7, 15), new Snake(h, 2804, 7, 8));
        try {
            for (int i = 0; i < WARM_UP; i++) {
                for (Snake s : rigs) {
                    s.cycle(null, null);
                }
            }
            for (Snake s : rigs) {
                s.builds = 0;
            }
            for (int i = 0; i < RUNS; i++) {
                for (Snake s : rigs) {
                    s.cycle(s.opening, s.closing);
                }
            }
            for (Snake s : rigs) {
                h.assertValueEqual(s.openBuilds, (s.edge() ? 2 : 1) * RUNS, s + ": the valve's rebuilds as it opened");
                h.assertValueEqual(s.shutBuilds, RUNS, s + ": the valve's rebuilds as it shut");
                s.turn(true);
                s.step();
                s.builds = 0;
            }
            for (int i = 0; i < RUNS; i++) {
                for (Snake s : rigs) {
                    s.steady[i] = s.step();
                }
            }
            for (Snake s : rigs) {
                h.assertValueEqual(s.builds, 0, s + ": rebuilds while it stayed open");
                h.assertValueEqual(s.valve.network().members().size(), s.nodes, s + ": the open valve's network");
                LOGGER.info(
                        "s_09 edge valve: {}: per atmos tick, median of {}: opening {} us, shutting {} us, open {} us;"
                                + " rebuilds of the valve's network per opening {}, per shutting {}",
                        s,
                        RUNS,
                        micros(s.opening),
                        micros(s.closing),
                        micros(s.steady),
                        s.openBuilds / (double) RUNS,
                        s.shutBuilds / (double) RUNS);
            }
        } finally {
            rigs.forEach(Snake::done);
        }
        h.succeed();
    }

    /** Opening and shutting cycles measured, and atmos ticks measured with the valve open. */
    private static final int RUNS = 150;

    /** Cycles run first for the JIT, not measured. */
    private static final int WARM_UP = 40;

    private static String micros(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return String.format(Locale.ROOT, "%.1f", sorted[sorted.length / 2] / 1000.0);
    }

    /**
     * A snake of pipe rows at y = 1, from x = 1 to 30 across the border at x = 15 | 16, at z = 2, 4
     * and so on, joined at alternate ends and capped at both. A manual valve takes the place of the
     * first row's pipe at {@code valveX}. It counts the builds of the valve's network by watching
     * the valve's own store, which a build empties into the new network.
     */
    private static final class Snake {
        final Area a;
        final int rows;
        final int valveX;
        final int nodes;
        final ValveBlockEntity valve;
        final List<AtmosDevice> devices = new ArrayList<>();
        final long[] opening = new long[RUNS];
        final long[] closing = new long[RUNS];
        final long[] steady = new long[RUNS];
        int builds;
        int openBuilds;
        int shutBuilds;
        private int cycles;

        Snake(GameTestHelper h, int index, int rows, int valveX) {
            this.a = new Area(h, index);
            this.rows = rows;
            this.valveX = valveX;
            a.clear(0, 0, 31, 2 * rows + 2);
            int count = 0;
            for (int r = 0; r < rows; r++) {
                int z = 2 + 2 * r;
                for (int x = 1; x <= 30; x++) {
                    boolean start = r == 0 && x == 1;
                    boolean end = r == rows - 1 && x == (r % 2 == 0 ? 30 : 1);
                    if (start || end) {
                        Direction in = x == 1 ? Direction.EAST : Direction.WEST;
                        a.place(
                                x,
                                1,
                                z,
                                AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, in));
                    } else if (r == 0 && x == valveX) {
                        a.place(
                                x,
                                1,
                                z,
                                AtmosBlocks.MANUAL_VALVE
                                        .get()
                                        .defaultBlockState()
                                        .setValue(ValveBlock.AXIS, Direction.Axis.X));
                    } else {
                        a.place(x, 1, z, GasDeviceTests.pipe());
                    }
                    count++;
                }
                if (r < rows - 1) {
                    a.place(r % 2 == 0 ? 30 : 1, 1, z + 1, GasDeviceTests.pipe());
                    count++;
                }
            }
            this.nodes = count;
            this.valve = a.entity(valveX, 1, 2);
            for (AtmosDevice d : DeviceTicker.loaded(a.level)) {
                BlockPos p = d.getBlockPos().subtract(a.origin);
                if (p.getX() >= 0 && p.getX() <= 31 && p.getZ() >= 0 && p.getZ() <= 2 * rows + 2 && p.getY() == 1) {
                    devices.add(d);
                }
            }
            a.h.assertValueEqual(devices.size(), nodes, this + ": its devices");
            PipeNetwork west = a.<PipeNodeEntity>entity(2, 1, 2).network();
            PipeNetwork east = a.<PipeNodeEntity>entity(29, 1, 2).network();
            west.gas().add(new GasParcel(Map.of(N2, 0.5 * west.members().size()), Area.T));
            east.gas().add(new GasParcel(Map.of(N2, 0.5 * east.members().size()), Area.T));
            valve.stored().watch(() -> {
                if (valve.stored().isEmpty()) {
                    builds++; // a build has taken the valve's share into a new network
                }
            });
            step();
        }

        boolean edge() {
            return valveX == 15;
        }

        void turn(boolean open) {
            BlockPos pos = a.abs(valveX, 1, 2);
            BlockState state = a.level.getBlockState(pos);
            a.level.setBlock(
                    pos, state.setValue(ValveBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        /** One atmos tick of the rig's devices, in the schedule's order; returns its time in ns. */
        long step() {
            long start = System.nanoTime();
            for (AtmosDevice d : devices) {
                if (d.readyToStep()) {
                    d.step();
                }
            }
            return System.nanoTime() - start;
        }

        /**
         * Opens the valve for an atmos tick, shuts it for the next, and leaves it shut for a third,
         * recording the first two's times if asked. The third rebuilds the network on the valve's
         * near side, whose members stepped before the valve shut, so the valve next opens onto built
         * networks, as it does unless it's turned every atmos tick. Without it, the first of those
         * members to step would build across the open valve before the valve's own step rebuilt it,
         * wherever the valve is.
         */
        void cycle(long[] open, long[] shut) {
            turn(true);
            int b = builds;
            long t = step();
            if (open != null) {
                open[cycles] = t;
                openBuilds += builds - b;
            }
            turn(false);
            b = builds;
            t = step();
            if (shut != null) {
                shut[cycles] = t;
                shutBuilds += builds - b;
                cycles++;
            }
            step();
        }

        void done() {
            valve.stored().watch(null);
            a.clear(0, 0, 31, 2 * rows + 2);
        }

        @Override
        public String toString() {
            return nodes + " nodes, valve at x = " + valveX + (edge() ? " (chunk edge)" : " (mid-chunk)");
        }
    }

    // ---- Comparing saved data ---------------------------------------------------------------

    /** A tag written to compressed bytes and read back, as the game's data files are. */
    static CompoundTag throughBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Fails at the first place two saved tags differ: doubles and floats within 1e-9 (relative, or
     * absolute below 1), everything else exactly.
     */
    static void sameTag(GameTestHelper h, Tag now, Tag then, String path) {
        if (then == null) {
            h.assertTrue(now == null, path + ": saved now but not before: " + now);
        } else if (then instanceof CompoundTag t) {
            h.assertTrue(now instanceof CompoundTag, path + ": expected " + t + ", got " + now);
            CompoundTag n = (CompoundTag) now;
            h.assertValueEqual(n.getAllKeys(), t.getAllKeys(), path + "'s keys");
            for (String key : t.getAllKeys()) {
                sameTag(h, n.get(key), t.get(key), path + "." + key);
            }
        } else if (then instanceof ListTag t) {
            h.assertTrue(now instanceof ListTag l && l.size() == t.size(), path + ": expected " + t + ", got " + now);
            ListTag l = (ListTag) now;
            for (int i = 0; i < t.size(); i++) {
                sameTag(h, l.get(i), t.get(i), path + "[" + i + "]");
            }
        } else if (then instanceof DoubleTag || then instanceof FloatTag) {
            h.assertTrue(
                    now != null && now.getClass() == then.getClass(), path + ": expected " + then + ", got " + now);
            close(h, ((NumericTag) now).getAsDouble(), ((NumericTag) then).getAsDouble(), path);
        } else {
            h.assertValueEqual(now, then, path);
        }
    }

    static void close(GameTestHelper h, double now, double then, String what) {
        boolean same =
                (Double.isNaN(now) && Double.isNaN(then)) || Math.abs(now - then) <= REL * Math.max(1, Math.abs(then));
        h.assertTrue(same, what + ": expected " + then + ", got " + now);
    }
}
