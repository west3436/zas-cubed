package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import io.github.west3436.zas.atmos.airlock.AirlockFirelock;
import io.github.west3436.zas.atmos.airlock.SensorStatus;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Gas;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Power;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The airlocks (CONT-133 to CONT-137), the airlock controller (CONT-138 to CONT-141, CONT-152,
 * CONT-153) and the air sensor (CONT-142). Every state they show is a block-state property, so each
 * scene sets it; Ponder's world runs no cycle.
 */
public final class AirlockScenes {
    private AirlockScenes() {}

    /** An airlock opened by hand and by redstone, closing itself, and bolted. */
    public static void airlock(SceneBuilder scene, SceneBuildingUtil util) {
        doors(scene, util, "airlock", AtmosBlocks.AIRLOCK.get(), util.grid().at(2, 1, 2));
    }

    /** The same for the large airlock, two blocks wide. */
    public static void largeAirlock(SceneBuilder scene, SceneBuildingUtil util) {
        doors(
                scene,
                util,
                "large_airlock",
                AtmosBlocks.LARGE_AIRLOCK.get(),
                util.grid().at(1, 1, 2));
    }

    /**
     * An airlock in a wall across the floor, its passage running north and south, with a lever on the
     * wall beside it. Its right-hand column, as the viewer sees it, is at x 2 either size.
     */
    private static void doors(
            SceneBuilder scene, SceneBuildingUtil util, String id, AirlockBlock block, BlockPos origin) {
        Stage.open(scene, id);
        Map<BlockPos, BlockState> airlock = airlock(block, origin, Direction.NORTH);
        Selection doors = select(util, airlock);
        BlockPos upper = util.grid().at(2, 2, 2);
        Vec3 front = util.vector().blockSurface(upper, Direction.NORTH);
        BlockPos lever = util.grid().at(3, 1, 1);

        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(0, 1, 2), util.grid().at(4, 2, 2))) {
            if (!airlock.containsKey(pos)) {
                wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
        }
        wall.putAll(airlock);
        Build.revealAll(scene, util, wall, Direction.DOWN);

        // By hand, open and closed.
        Hand.use(scene, util, upper, Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, true));
        scene.idle(Hand.SHOW);
        Hand.use(scene, util, upper, Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, false));
        Say.at(scene, front, "airlock.use");

        // Opened, it closes itself 5 s later.
        Hand.use(scene, util, upper, Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, true));
        Say.over(scene, AUTO_CLOSE, front, "airlock.closes");
        scene.idle(AUTO_CLOSE);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        // A lever on the wall beside it holds it open, and it closes 5 s after the signal ends.
        BlockState off = Blocks.LEVER
                .defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.NORTH);
        Build.reveal(scene, util, lever, off, Direction.SOUTH);
        flick(scene, util, lever, true);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, true).setValue(AirlockBlock.POWERED, true));
        Say.at(scene, front, "airlock.redstone");
        flick(scene, util, lever, false);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.POWERED, false));
        Say.over(scene, AUTO_CLOSE, front, "airlock.signal");
        scene.idle(AUTO_CLOSE);
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        // An airlock controller bolts it, and then nothing opens it.
        Build.change(scene, doors, s -> s.setValue(AirlockBlock.BOLTED, true));
        Say.at(scene, front, "airlock.bolted");
        Hand.use(scene, util, upper, Direction.NORTH, ItemStack.EMPTY);
        refused(scene, doors);
        Hand.use(scene, util, upper, Direction.NORTH, new ItemStack(AtmosItems.CROWBAR.get()));
        refused(scene, doors);
        flick(scene, util, lever, true);
        refused(scene, doors);
        Say.at(scene, front, "airlock.stays");
        scene.markAsFinished();
    }

    /** Ticks an airlock waits before it closes itself, 5 s (CONT-135). */
    private static final int AUTO_CLOSE = AirlockBlock.AUTO_CLOSE_TICKS;

    /** A lever turned on or off, with its signal shown. */
    private static void flick(SceneBuilder scene, SceneBuildingUtil util, BlockPos lever, boolean on) {
        Hand.use(scene, util, lever, Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, lever, s -> s.setValue(LeverBlock.POWERED, on));
        if (on) {
            scene.effects().indicateRedstone(lever);
        }
    }

    /** A red outline on an airlock that stays shut. */
    private static void refused(SceneBuilder scene, Selection doors) {
        scene.overlay().showOutline(PonderPalette.RED, new Object(), doors, 30);
        scene.idle(30);
    }

    /**
     * A chamber between two airlocks, with a sensor in it and one past each airlock, run by a controller
     * on the station side. The multitool links each part into the slot the controller guesses. Opened
     * last from the station, a press of the outer sensor cycles the chamber out to the vacuum, its air
     * siphoned into its vent's pipes; the bolted inner airlock cycles it back.
     */
    public static void controller(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "airlock_controller");
        Chamber chamber = chamber(util);
        BlockPos outer = chamber.outer();
        BlockPos inner = chamber.inner();
        BlockPos vent = chamber.vent();
        BlockPos outerSensor = chamber.outerSensor();
        BlockPos chamberSensor = chamber.chamberSensor();
        BlockPos innerSensor = chamber.innerSensor();
        BlockPos controller = chamber.controller();
        List<BlockPos> run = chamber.run();
        Selection outerDoors = select(util, chamber.outerAirlock());
        Selection innerDoors = select(util, chamber.innerAirlock());
        Selection both = outerDoors.copy().add(innerDoors);
        Selection sensors = chamber.sensors(util);
        Vec3 panel = Power.panel(util, controller, Direction.NORTH);
        Vec3 outerPanel = Power.panel(util, outerSensor, Direction.NORTH);
        Vec3 chamberPanel = Power.panel(util, chamberSensor, Direction.NORTH);
        Vec3 innerPanel = Power.panel(util, innerSensor, Direction.NORTH);
        Vec3 outerFront = util.vector().blockSurface(outer.above(), Direction.NORTH);
        Vec3 innerFront = util.vector().blockSurface(inner.above(), Direction.NORTH);

        // A wall behind the passage, with the two airlocks across it, and a vent in the chamber's floor.
        Build.revealAll(scene, util, chamber.walls(util), Direction.DOWN);
        pipe(scene, util, chamber);

        // The sensors, each showing its own side's pressure: the vacuum's alerts. On the station side, the
        // controller, with an area air controller beside it.
        Build.revealAll(
                scene,
                util,
                chamber.panels(SensorStatus.ALERT, SensorStatus.STANDBY, SensorStatus.STANDBY),
                Direction.SOUTH);
        Say.at(scene, panel, "power.controller");

        // The multitool links each part, which takes the slot the controller guesses from the zones
        // around it, and then the vent, which takes none.
        Hand.use(scene, util, controller, Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        Say.at(scene, panel, "airlock_controller.links");
        link(
                scene,
                util,
                controller,
                inner.above(),
                innerDoors,
                innerFront,
                "airlock_controller.inner_door",
                "Airlock0",
                inner,
                0);
        link(
                scene,
                util,
                controller,
                innerSensor,
                util.select().position(innerSensor),
                innerPanel,
                "airlock_controller.inner_sensor",
                "InnerSensor",
                innerSensor,
                2);
        link(
                scene,
                util,
                controller,
                chamberSensor,
                util.select().position(chamberSensor),
                chamberPanel,
                "airlock_controller.chamber_sensor",
                "ChamberSensor",
                chamberSensor,
                4);
        link(
                scene,
                util,
                controller,
                outer.above(),
                outerDoors,
                outerFront,
                "airlock_controller.outer_door",
                "Airlock1",
                outer,
                1);
        link(
                scene,
                util,
                controller,
                outerSensor,
                util.select().position(outerSensor),
                outerPanel,
                "airlock_controller.outer_sensor",
                "OuterSensor",
                outerSensor,
                3);
        Hand.use(scene, util, vent, Direction.UP, new ItemStack(AtmosItems.MULTITOOL.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, "vent", util.select().position(vent), ROLE);
        prime(scene, util, controller, tag -> tag.putLongArray("Vents", new long[] {vent.asLong()}));
        scene.idle(ROLE);

        // Its window: a slot armed there takes the next part linked, and each side has a Cycle button.
        Hand.use(scene, util, controller, Direction.NORTH, ItemStack.EMPTY);
        Say.at(scene, panel, "airlock_controller.slots");
        Say.at(scene, panel, "airlock_controller.window");

        // Someone comes in from the station. The inner airlock, opened last, closes itself 5 s later.
        Hand.use(scene, util, inner.above(), Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, innerDoors, s -> s.setValue(AirlockBlock.OPEN, true));
        prime(scene, util, controller, tag -> tag.putInt("LastOpened", AirlockCycle.INNER));
        Say.over(scene, AUTO_CLOSE, innerFront, "airlock.closes");
        scene.idle(AUTO_CLOSE);
        Build.change(scene, innerDoors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        // From outside, a press of the outer sensor cycles toward it, away from the side opened last:
        // both airlocks bolt, every sensor shows the cycle, and the vent siphons the chamber.
        Hand.use(scene, util, outerSensor, Direction.NORTH, ItemStack.EMPTY);
        start(scene, util, controller, sensors, both, vent, VentMode.SIPHON, AirlockCycle.OUTER);
        Say.at(scene, outerPanel, "airlock_controller.press");
        Say.line(scene, CYCLE, "reading.pressure", Gas.pressure(0))
                .pointAt(outerPanel)
                .placeNearTarget();
        Gas.draw(scene, util, vent, Direction.UP, CYCLE);
        Say.over(scene, CYCLE, util.vector().topOf(vent), "airlock_controller.siphon");
        readings(scene, chamberPanel, run.reversed(), 101.325, 60, 25, 4);

        // Within 5.07 kPa of the vacuum, the outer airlock unbolts and opens, and closes itself 5 s later;
        // the inner stays bolted. Each sensor, idle again, shows its own side: the chamber's alerts.
        end(
                scene,
                util,
                controller,
                vent,
                Map.of(
                        outerSensor,
                        SensorStatus.ALERT,
                        chamberSensor,
                        SensorStatus.ALERT,
                        innerSensor,
                        SensorStatus.STANDBY));
        Build.change(
                scene, outerDoors, s -> s.setValue(AirlockBlock.BOLTED, false).setValue(AirlockBlock.OPEN, true));
        prime(scene, util, controller, tag -> tag.putInt("LastOpened", AirlockCycle.OUTER));
        scene.overlay().showOutline(PonderPalette.GREEN, "outer", outerDoors, AUTO_CLOSE);
        Say.over(scene, AUTO_CLOSE, outerFront, "airlock_controller.opens");
        scene.idle(AUTO_CLOSE);
        Build.change(scene, outerDoors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        // From the chamber, using the bolted inner airlock cycles back toward it, to the station's pressure.
        Hand.use(scene, util, inner.above(), Direction.WEST, ItemStack.EMPTY);
        start(scene, util, controller, sensors, both, vent, VentMode.BLOW, AirlockCycle.INNER);
        Say.at(scene, util.vector().blockSurface(inner.above(), Direction.WEST), "airlock_controller.ask");
        Say.line(scene, CYCLE, "reading.pressure", Gas.pressure(101.325))
                .pointAt(innerPanel)
                .placeNearTarget();
        Gas.puff(scene, util, vent, Direction.UP, CYCLE);
        Say.over(scene, CYCLE, util.vector().topOf(vent), "airlock_controller.blow");
        readings(scene, chamberPanel, run, 4, 45, 80, 98);

        end(
                scene,
                util,
                controller,
                vent,
                Map.of(
                        outerSensor,
                        SensorStatus.ALERT,
                        chamberSensor,
                        SensorStatus.STANDBY,
                        innerSensor,
                        SensorStatus.STANDBY));
        Build.change(
                scene, innerDoors, s -> s.setValue(AirlockBlock.BOLTED, false).setValue(AirlockBlock.OPEN, true));
        prime(scene, util, controller, tag -> tag.putInt("LastOpened", AirlockCycle.INNER));
        scene.idle(Say.LINE);
        scene.markAsFinished();
    }

    /** How long each way of a cycle is shown, in ticks. */
    private static final int CYCLE = 4 * 30;

    /** How long a part's slot is shown as it's linked, in ticks. */
    private static final int ROLE = 40;

    /**
     * The passage the controller's and the sensor's scenes are set in, on a large stage: space at x 0,
     * the chamber at x 2 and 3, the station past x 4. On the wall behind it are a sensor past each
     * airlock and one in the chamber, each clear of the airlock in front of it, and on the station side
     * the controller, under its sensor, with an area air controller beside it.
     *
     * @param outer the outer airlock's origin
     * @param inner the inner airlock's origin
     * @param vent the vent, in the chamber's floor
     * @param run the vent's pipe, from the floor's far end, under the floor to the vent
     */
    private record Chamber(
            BlockPos outer,
            BlockPos inner,
            BlockPos vent,
            BlockPos outerSensor,
            BlockPos chamberSensor,
            BlockPos innerSensor,
            BlockPos controller,
            BlockPos area,
            List<BlockPos> run) {

        Map<BlockPos, BlockState> outerAirlock() {
            return airlock(AtmosBlocks.AIRLOCK.get(), outer, Direction.WEST);
        }

        Map<BlockPos, BlockState> innerAirlock() {
            return airlock(AtmosBlocks.AIRLOCK.get(), inner, Direction.WEST);
        }

        Selection sensors(SceneBuildingUtil util) {
            return util.select()
                    .position(outerSensor)
                    .add(util.select().position(chamberSensor))
                    .add(util.select().position(innerSensor));
        }

        /** The wall behind the passage, with both airlocks across it. */
        Map<BlockPos, BlockState> walls(SceneBuildingUtil util) {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            for (BlockPos pos :
                    BlockPos.betweenClosed(util.grid().at(0, 1, 4), util.grid().at(6, 2, 4))) {
                out.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
            out.putAll(outerAirlock());
            out.putAll(innerAirlock());
            return out;
        }

        /** The sensors, each showing a status, the controller and the area air controller. */
        Map<BlockPos, BlockState> panels(SensorStatus outerShows, SensorStatus chamberShows, SensorStatus innerShows) {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            out.put(outerSensor, sensorState(outerShows));
            out.put(chamberSensor, sensorState(chamberShows));
            out.put(innerSensor, sensorState(innerShows));
            out.put(
                    controller,
                    AtmosBlocks.AIRLOCK_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AirlockControllerBlock.FACING, Direction.NORTH));
            out.put(
                    area,
                    AtmosBlocks.AREA_AIR_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                            .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
            return out;
        }
    }

    private static Chamber chamber(SceneBuildingUtil util) {
        BlockPos vent = util.grid().at(2, 0, 3);
        // The pipe runs in the floor from a cap at its edge and dips under it to the vent.
        BlockPos dip = util.grid().at(2, 0, 2);
        return new Chamber(
                util.grid().at(1, 1, 3),
                util.grid().at(4, 1, 3),
                vent,
                util.grid().at(0, 2, 3),
                util.grid().at(3, 2, 3),
                util.grid().at(6, 2, 3),
                util.grid().at(6, 1, 3),
                util.grid().at(5, 2, 3),
                List.of(util.grid().at(2, 0, 1), dip, dip.below(), vent.below(), vent));
    }

    /** The chamber's vent, set into its floor, on pipe from a cap at the floor's edge. */
    private static void pipe(SceneBuilder scene, SceneBuildingUtil util, Chamber chamber) {
        List<BlockPos> run = chamber.run();
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Build.place(scene, run.getFirst().north(), PumpScenes.cap(Direction.SOUTH));
        Build.place(scene, run.get(0), pipe);
        Build.place(scene, run.get(1), pipe);
        Map<BlockPos, BlockState> under = new LinkedHashMap<>();
        under.put(run.get(2), pipe);
        under.put(run.get(3), pipe);
        Build.under(scene, util, under);
        scene.idle(Build.SHOW);
        Build.sink(
                scene,
                util,
                chamber.vent(),
                AtmosBlocks.VENT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    /** An air sensor on a wall facing the viewer, showing a status. */
    private static BlockState sensorState(SensorStatus status) {
        return AtmosBlocks.AIR_SENSOR
                .get()
                .defaultBlockState()
                .setValue(AirSensorBlock.FACING, Direction.NORTH)
                .setValue(AirSensorBlock.STATUS, status);
    }

    /**
     * The multitool used on a part, which takes the slot the controller guesses: the part is outlined
     * and labelled with its slot, and the controller saves it there, as the game does.
     *
     * @param clicked the block the multitool is used on
     * @param part the part's blocks, to outline
     * @param at where its label points
     * @param role its label's key
     * @param slot the slot's save key
     * @param saved what the slot holds: an airlock's origin, or the sensor
     * @param place the slot's place in the window's order
     */
    private static void link(
            SceneBuilder scene,
            SceneBuildingUtil util,
            BlockPos controller,
            BlockPos clicked,
            Selection part,
            Vec3 at,
            String role,
            String slot,
            BlockPos saved,
            int place) {
        Hand.use(scene, util, clicked, Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), part, ROLE);
        Say.over(scene, ROLE, at, role);
        prime(scene, util, controller, tag -> {
            tag.putLong(slot, saved.asLong());
            // Each slot it filled by a guess.
            tag.putInt("Sure", tag.getInt("Sure") | 1 << place);
        });
        scene.idle(ROLE + 10);
    }

    /**
     * Sets what an airlock controller saves, through its save keys, to match what the scene shows. Ponder's
     * world runs nothing, so it's what the controller would hold by then.
     */
    private static void prime(
            SceneBuilder scene, SceneBuildingUtil util, BlockPos controller, Consumer<CompoundTag> change) {
        scene.world()
                .modifyBlockEntityNBT(util.select().position(controller), AirlockControllerBlockEntity.class, change);
    }

    /**
     * A cycle starting: the controller and every sensor show it, both airlocks close and bolt, the vent
     * runs.
     *
     * @param toward the door it cycles toward, {@link AirlockCycle#INNER} or {@code OUTER}
     */
    private static void start(
            SceneBuilder scene,
            SceneBuildingUtil util,
            BlockPos controller,
            Selection sensors,
            Selection airlocks,
            BlockPos vent,
            VentMode mode,
            int toward) {
        Build.change(scene, controller, s -> s.setValue(AirlockControllerBlock.CYCLING, true));
        Build.change(scene, sensors, s -> s.setValue(AirSensorBlock.STATUS, SensorStatus.CYCLING));
        Build.change(scene, airlocks, s -> s.setValue(AirlockBlock.OPEN, false).setValue(AirlockBlock.BOLTED, true));
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, mode));
        prime(scene, util, controller, tag -> tag.putInt("Cycling", toward));
    }

    /**
     * A cycle done: the vent stops, the controller is idle, and each sensor shows what its own side
     * reads.
     *
     * @param reads each sensor and what it shows
     */
    private static void end(
            SceneBuilder scene,
            SceneBuildingUtil util,
            BlockPos controller,
            BlockPos vent,
            Map<BlockPos, SensorStatus> reads) {
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.OFF));
        Build.change(scene, controller, s -> s.setValue(AirlockControllerBlock.CYCLING, false));
        prime(scene, util, controller, tag -> tag.putInt("Cycling", -1));
        reads.forEach((pos, status) -> Build.change(scene, pos, s -> s.setValue(AirSensorBlock.STATUS, status)));
    }

    /** The chamber's pressure at the sensor, a reading for each quarter of a way, as gas flows along the run. */
    private static void readings(SceneBuilder scene, Vec3 at, List<BlockPos> run, double... kpa) {
        int each = CYCLE / kpa.length;
        for (double reading : kpa) {
            Say.line(scene, each, "reading.pressure", Gas.pressure(reading))
                    .pointAt(at)
                    .placeNearTarget();
            Gas.flow(scene, each, run);
        }
    }

    /**
     * An air sensor on the station side of an airlock whose other parts are linked. Each sensor reads
     * its own side, the empty chamber's alerting. Linked to the controller, this one's press cycles the
     * airlock toward it. It needs no power.
     */
    public static void sensor(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "air_sensor");
        Chamber chamber = chamber(util);
        BlockPos sensor = chamber.innerSensor();
        BlockPos controller = chamber.controller();
        BlockPos vent = chamber.vent();
        Selection outerDoors = select(util, chamber.outerAirlock());
        Selection innerDoors = select(util, chamber.innerAirlock());
        Vec3 panel = Power.panel(util, sensor, Direction.NORTH);
        Vec3 chamberPanel = Power.panel(util, chamber.chamberSensor(), Direction.NORTH);
        Vec3 chamberAir = util.vector().centerOf(chamber.chamberSensor().below());
        Vec3 innerFront = util.vector().blockSurface(chamber.inner().above(), Direction.NORTH);

        // The airlock, last opened to the vacuum: the chamber is empty and the inner airlock bolted. Its
        // controller has both doors, the outer and chamber sensors, and the vent.
        Map<BlockPos, BlockState> walls = chamber.walls(util);
        for (BlockPos pos : chamber.innerAirlock().keySet()) {
            walls.computeIfPresent(pos, (at, state) -> state.setValue(AirlockBlock.BOLTED, true));
        }
        Build.revealAll(scene, util, walls, Direction.DOWN);
        pipe(scene, util, chamber);
        Build.revealAll(
                scene,
                util,
                chamber.panels(SensorStatus.ALERT, SensorStatus.ALERT, SensorStatus.STANDBY),
                Direction.SOUTH);
        prime(scene, util, controller, tag -> {
            tag.putLong("Airlock0", chamber.inner().asLong());
            tag.putLong("Airlock1", chamber.outer().asLong());
            tag.putLong("OuterSensor", chamber.outerSensor().asLong());
            tag.putLong("ChamberSensor", chamber.chamberSensor().asLong());
            tag.putLongArray("Vents", new long[] {vent.asLong()});
            // Each slot filled by a guess: both doors, then the outer and chamber sensors.
            tag.putInt("Sure", 1 | 1 << 1 | 1 << 3 | 1 << 4);
            tag.putInt("LastOpened", AirlockCycle.OUTER);
        });

        // It reads the air in front of it, the station's. Below 81 kPa, as in the empty chamber, a sensor
        // alerts.
        Say.line(scene, 40, "reading.pressure", Gas.pressure(101.325))
                .pointAt(panel)
                .placeNearTarget();
        scene.idle(50);
        Say.line(scene, Say.LINE + 10, "reading.pressure", Gas.pressure(0))
                .pointAt(chamberAir)
                .placeNearTarget();
        Say.at(
                scene,
                chamberPanel,
                "air_sensor.alert",
                Gas.pressure(SensorStatus.ALERT_BELOW_KPA),
                Gas.pressure(SensorStatus.ALERT_ABOVE_KPA));

        // The multitool links it to the controller, as the station side's sensor.
        Hand.use(scene, util, controller, Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        scene.idle(Hand.SHOW - Hand.PAUSE);
        Hand.use(scene, util, sensor, Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        scene.overlay().showOutline(PonderPalette.GREEN, "sensor", util.select().position(sensor), Say.LINE);
        prime(scene, util, controller, tag -> {
            tag.putLong("InnerSensor", sensor.asLong());
            tag.putInt("Sure", tag.getInt("Sure") | 1 << 2);
        });
        Say.at(scene, panel, "air_sensor.link");

        // Pressed, it cycles the airlock toward its own side, away from the side opened last: both airlocks
        // bolt, every sensor shows the cycle, and the vent blows the chamber full.
        Hand.use(scene, util, sensor, Direction.NORTH, ItemStack.EMPTY);
        start(
                scene,
                util,
                controller,
                chamber.sensors(util),
                outerDoors.copy().add(innerDoors),
                vent,
                VentMode.BLOW,
                AirlockCycle.INNER);
        Gas.puff(scene, util, vent, Direction.UP, 2 * (Say.LINE + 10));
        Say.at(scene, panel, "air_sensor.press");
        Say.at(scene, panel, "air_sensor.cycling");

        // The cycle done, the inner airlock unbolts and opens, and closes itself 5 s later.
        end(
                scene,
                util,
                controller,
                vent,
                Map.of(
                        chamber.outerSensor(),
                        SensorStatus.ALERT,
                        chamber.chamberSensor(),
                        SensorStatus.STANDBY,
                        sensor,
                        SensorStatus.STANDBY));
        Build.change(
                scene, innerDoors, s -> s.setValue(AirlockBlock.BOLTED, false).setValue(AirlockBlock.OPEN, true));
        prime(scene, util, controller, tag -> tag.putInt("LastOpened", AirlockCycle.INNER));
        scene.overlay().showOutline(PonderPalette.GREEN, "door", innerDoors, AUTO_CLOSE);
        Say.over(scene, AUTO_CLOSE, innerFront, "airlock_controller.opens");
        scene.idle(AUTO_CLOSE);
        Build.change(scene, innerDoors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        Say.at(scene, panel, "air_sensor.no_power");
        scene.markAsFinished();
    }

    /**
     * A firelock fitted to an airlock and to an oak door in one wall, both shutting when the room's
     * controller alarms, and opening again with the doors left shut (CONT-137).
     */
    public static void firelocks(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "firelock_doors");
        BlockPos airlockAt = util.grid().at(3, 1, 2);
        BlockPos doorAt = util.grid().at(1, 1, 2);
        BlockPos controller = util.grid().at(2, 2, 1);
        Map<BlockPos, BlockState> airlock = airlock(AtmosBlocks.AIRLOCK.get(), airlockAt, Direction.NORTH);
        Selection airlockDoors = select(util, airlock);
        Selection door = util.select().fromTo(doorAt, doorAt.above());
        Vec3 airlockFront = util.vector().blockSurface(airlockAt.above(), Direction.NORTH);
        Vec3 doorFront = util.vector().blockSurface(doorAt.above(), Direction.NORTH);

        // A wall with an airlock and an oak door in it, closed.
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(0, 1, 2), util.grid().at(4, 2, 2))) {
            if (pos.getX() != airlockAt.getX() && pos.getX() != doorAt.getX()) {
                wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
        }
        wall.putAll(airlock);
        BlockState oak = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        wall.put(doorAt, oak.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        wall.put(doorAt.above(), oak.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE),
                Direction.SOUTH);

        // A firelock used on each fits to it.
        ItemStack firelock = new ItemStack(AtmosItems.FIRELOCK.get());
        Hand.use(scene, util, airlockAt.above(), Direction.NORTH, firelock);
        Build.change(scene, airlockDoors, s -> s.setValue(AirlockBlock.FIRELOCK, AirlockFirelock.OPEN));
        Say.at(scene, airlockFront, "firelock_doors.airlock");

        Hand.use(scene, util, doorAt.above(), Direction.NORTH, firelock);
        BlockState fitted = AtmosBlocks.FIRELOCK_DOOR
                .get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(FirelockDoorBlock.FIRELOCK, AirlockFirelock.OPEN);
        Build.place(scene, doorAt, fitted.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        Build.place(scene, doorAt.above(), fitted.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Each half keeps the door it was, which it draws under the firelock.
        String oakId = BuiltInRegistries.BLOCK.getKey(Blocks.OAK_DOOR).toString();
        scene.world()
                .modifyBlockEntityNBT(door, FirelockDoorBlockEntity.class, tag -> tag.putString("Door", oakId), true);
        Build.settle(scene);
        Say.at(scene, doorFront, "firelock_doors.door");

        // Both open, then the room's controller alarms: each firelock shuts, and its door with it.
        Hand.use(scene, util, airlockAt.above(), Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, airlockDoors, s -> s.setValue(AirlockBlock.OPEN, true));
        Hand.use(scene, util, doorAt.above(), Direction.NORTH, ItemStack.EMPTY);
        Build.change(scene, door, s -> s.setValue(DoorBlock.OPEN, true));
        scene.idle(Hand.SHOW);
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        Build.change(
                scene,
                airlockDoors,
                s -> s.setValue(AirlockBlock.FIRELOCK, AirlockFirelock.CLOSED).setValue(AirlockBlock.OPEN, false));
        Build.change(
                scene,
                door,
                s -> s.setValue(FirelockDoorBlock.FIRELOCK, AirlockFirelock.CLOSED)
                        .setValue(DoorBlock.OPEN, false));
        Say.at(scene, airlockFront, "firelock_doors.shuts");

        // Safe again, the firelocks open; the doors stay shut.
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
        Build.change(scene, airlockDoors, s -> s.setValue(AirlockBlock.FIRELOCK, AirlockFirelock.OPEN));
        Build.change(scene, door, s -> s.setValue(FirelockDoorBlock.FIRELOCK, AirlockFirelock.OPEN));
        Say.at(scene, airlockFront, "firelock_doors.reopens");
        scene.markAsFinished();
    }

    /** An airlock's blocks and their states, closed and unbolted, its origin first. */
    private static Map<BlockPos, BlockState> airlock(AirlockBlock block, BlockPos origin, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(AirlockBlock.FACING, facing);
        List<BlockPos> parts = block.parts(origin, facing);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            out.put(parts.get(i), block.part(state, i));
        }
        return out;
    }

    /**
     * All of an airlock's blocks, to change together: each takes its partners' state as it settles, so
     * one changed alone would take back the old state.
     */
    private static Selection select(SceneBuildingUtil util, Map<BlockPos, BlockState> airlock) {
        List<BlockPos> parts = List.copyOf(airlock.keySet());
        return util.select().fromTo(parts.getFirst(), parts.getLast());
    }
}
