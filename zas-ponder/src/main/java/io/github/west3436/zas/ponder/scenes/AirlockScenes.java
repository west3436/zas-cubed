package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
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
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
     * A chamber between two airlocks, cycled by a controller on the station side: out to the vacuum,
     * its air siphoned into its vent's pipes, then back in.
     */
    public static void controller(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "airlock_controller");
        // The passage runs east and west: space at x 0, the chamber at x 2 and 3, the station past x 4.
        BlockPos outer = util.grid().at(1, 1, 3);
        BlockPos inner = util.grid().at(4, 1, 3);
        BlockPos vent = util.grid().at(2, 0, 3);
        BlockPos sensor = util.grid().at(3, 2, 3);
        BlockPos controller = util.grid().at(5, 2, 3);
        BlockPos area = util.grid().at(6, 2, 3);
        Map<BlockPos, BlockState> outerAirlock = airlock(AtmosBlocks.AIRLOCK.get(), outer, Direction.WEST);
        Map<BlockPos, BlockState> innerAirlock = airlock(AtmosBlocks.AIRLOCK.get(), inner, Direction.WEST);
        Selection outerDoors = select(util, outerAirlock);
        Selection innerDoors = select(util, innerAirlock);
        Selection both = outerDoors.copy().add(innerDoors);
        Vec3 panel = Power.panel(util, controller, Direction.NORTH);
        Vec3 sensorPanel = Power.panel(util, sensor, Direction.NORTH);

        // A wall behind the chamber and its two airlocks.
        Map<BlockPos, BlockState> walls = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(1, 1, 4), util.grid().at(6, 2, 4))) {
            walls.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        walls.putAll(outerAirlock);
        walls.putAll(innerAirlock);
        Build.revealAll(scene, util, walls, Direction.DOWN);

        // A vent in the chamber's floor, on pipe that runs in the floor from a cap and dips under it.
        BlockPos dip = util.grid().at(2, 0, 2);
        List<BlockPos> run = List.of(util.grid().at(2, 0, 1), dip, dip.below(), vent.below(), vent);
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Build.place(scene, util.grid().at(2, 0, 0), PumpScenes.cap(Direction.SOUTH));
        Build.place(scene, run.get(0), pipe);
        Build.place(scene, dip, pipe);
        Map<BlockPos, BlockState> under = new LinkedHashMap<>();
        under.put(dip.below(), pipe);
        under.put(vent.below(), pipe);
        Build.under(scene, util, under);
        scene.idle(Build.SHOW);
        Build.sink(
                scene,
                util,
                vent,
                AtmosBlocks.VENT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));

        // The sensor in the chamber, and on the station side the controller and an area air controller.
        Build.reveal(
                scene,
                util,
                sensor,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.NORTH),
                Direction.SOUTH);
        Map<BlockPos, BlockState> panels = new LinkedHashMap<>();
        panels.put(
                controller,
                AtmosBlocks.AIRLOCK_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AirlockControllerBlock.FACING, Direction.NORTH));
        panels.put(
                area,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
        Build.revealAll(scene, util, panels, Direction.SOUTH);
        Say.at(scene, panel, "power.controller");

        // The multitool links its airlocks, its sensor and its vents.
        Hand.use(scene, util, controller, Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        Selection parts = both.copy()
                .add(util.select().position(sensor))
                .add(util.select().position(vent));
        scene.overlay().showOutline(PonderPalette.GREEN, "parts", parts, Say.LINE);
        Say.at(scene, panel, "airlock_controller.links");

        // Its window cycles toward the vacuum: both airlocks bolt, and the vent siphons the chamber.
        Hand.use(scene, util, controller, Direction.NORTH, ItemStack.EMPTY);
        Say.at(scene, panel, "airlock_controller.window");
        start(scene, controller, sensor, both, vent, VentMode.SIPHON);
        // Up level with the airlock's top, so it clears the vent's line below.
        Say.line(scene, CYCLE, "reading.pressure", Gas.pressure(0))
                .pointAt(util.vector().centerOf(util.grid().at(0, 2, 3)))
                .placeNearTarget();
        Gas.draw(scene, util, vent, Direction.UP, CYCLE);
        Say.over(scene, CYCLE, util.vector().topOf(vent), "airlock_controller.siphon");
        readings(scene, sensorPanel, run.reversed(), 101.325, 60, 25, 4);

        // Within 5.07 kPa of the vacuum, the outer airlock unbolts and opens; the inner stays bolted.
        // The sensor, idle again, reads the chamber's near vacuum.
        end(scene, controller, sensor, vent, SensorStatus.ALERT);
        Build.change(
                scene, outerDoors, s -> s.setValue(AirlockBlock.BOLTED, false).setValue(AirlockBlock.OPEN, true));
        scene.overlay().showOutline(PonderPalette.GREEN, "outer", outerDoors, Say.LINE);
        Say.at(scene, util.vector().blockSurface(outer.above(), Direction.NORTH), "airlock_controller.opens");
        Build.change(scene, outerDoors, s -> s.setValue(AirlockBlock.OPEN, false));
        scene.idle(20);

        // From the chamber, using the bolted inner airlock cycles back toward it.
        Hand.use(scene, util, inner.above(), Direction.WEST, ItemStack.EMPTY);
        start(scene, controller, sensor, both, vent, VentMode.BLOW);
        Say.at(scene, util.vector().blockSurface(inner.above(), Direction.WEST), "airlock_controller.ask");
        Say.line(scene, CYCLE, "reading.pressure", Gas.pressure(101.325))
                .pointAt(util.vector().centerOf(util.grid().at(5, 1, 1)))
                .placeNearTarget();
        Gas.puff(scene, util, vent, Direction.UP, CYCLE);
        Say.over(scene, CYCLE, util.vector().topOf(vent), "airlock_controller.blow");
        readings(scene, sensorPanel, run, 4, 45, 80, 98);

        end(scene, controller, sensor, vent, SensorStatus.STANDBY);
        Build.change(
                scene, innerDoors, s -> s.setValue(AirlockBlock.BOLTED, false).setValue(AirlockBlock.OPEN, true));
        scene.idle(Say.LINE);
        scene.markAsFinished();
    }

    /** How long each way of a cycle is shown, in ticks. */
    private static final int CYCLE = 4 * 30;

    /** A cycle starting: the controller and sensor show it, both airlocks close and bolt, the vent runs. */
    private static void start(
            SceneBuilder scene,
            BlockPos controller,
            BlockPos sensor,
            Selection airlocks,
            BlockPos vent,
            VentMode mode) {
        Build.change(scene, controller, s -> s.setValue(AirlockControllerBlock.CYCLING, true));
        Build.change(scene, sensor, s -> s.setValue(AirSensorBlock.STATUS, SensorStatus.CYCLING));
        Build.change(scene, airlocks, s -> s.setValue(AirlockBlock.OPEN, false).setValue(AirlockBlock.BOLTED, true));
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, mode));
    }

    /** A cycle done: the vent stops, the controller is idle, and the sensor shows what it reads. */
    private static void end(
            SceneBuilder scene, BlockPos controller, BlockPos sensor, BlockPos vent, SensorStatus reads) {
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.OFF));
        Build.change(scene, controller, s -> s.setValue(AirlockControllerBlock.CYCLING, false));
        Build.change(scene, sensor, s -> s.setValue(AirSensorBlock.STATUS, reads));
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

    /** An air sensor on a wall, alerting at a low pressure, and showing its controller's cycle. */
    public static void sensor(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "air_sensor");
        BlockPos sensor = util.grid().at(1, 2, 1);
        Vec3 panel = Power.panel(util, sensor, Direction.NORTH);
        Vec3 air = util.vector().centerOf(util.grid().at(1, 1, 1));

        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(0, 1, 2), util.grid().at(2, 2, 2))) {
            wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                sensor,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.NORTH),
                Direction.SOUTH);

        Say.line(scene, 40, "reading.pressure", Gas.pressure(101.325))
                .pointAt(air)
                .placeNearTarget();
        scene.idle(50);
        Say.line(scene, Say.LINE + 10, "reading.pressure", Gas.pressure(60))
                .pointAt(air)
                .placeNearTarget();
        Build.change(scene, sensor, s -> s.setValue(AirSensorBlock.STATUS, SensorStatus.ALERT));
        Say.at(
                scene,
                panel,
                "air_sensor.alert",
                Gas.pressure(SensorStatus.ALERT_BELOW_KPA),
                Gas.pressure(SensorStatus.ALERT_ABOVE_KPA));

        Build.change(scene, sensor, s -> s.setValue(AirSensorBlock.STATUS, SensorStatus.CYCLING));
        Say.at(scene, panel, "air_sensor.cycling");

        Build.change(scene, sensor, s -> s.setValue(AirSensorBlock.STATUS, SensorStatus.STANDBY));
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
