package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Power;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmStatus;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** The fire alarm (CONT-044), the firelock (CONT-043, CONT-092 to CONT-094) and the igniter (CONT-105). */
public final class FireScenes {
    private FireScenes() {}

    /** A fire alarm trips on fire in its room, and its controller goes to danger. */
    public static void fireAlarm(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "fire_alarm");
        BlockPos alarm = util.grid().at(4, 2, 3);
        // Below the alarm and to one side, so the alarm's lines don't cover it.
        BlockPos controller = util.grid().at(2, 1, 3);

        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(1, 1, 4), util.grid().at(4, 2, 4))) {
            wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                alarm,
                AtmosBlocks.FIRE_ALARM.get().defaultBlockState().setValue(FireAlarmBlock.FACING, Direction.NORTH),
                Direction.SOUTH);

        // The room's controller powers it. The wall is already up, so this doesn't use Power.controller.
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
        Build.change(scene, alarm, s -> s.setValue(FireAlarmBlock.STATUS, FireAlarmStatus.NORMAL));
        Say.at(scene, Power.panel(util, controller, Direction.NORTH), "power.controller");
        Power.multitool(scene, util, alarm);

        // A gas fire in the room.
        Map<BlockPos, BlockState> fire = new LinkedHashMap<>();
        for (BlockPos pos : List.of(
                util.grid().at(3, 1, 1), util.grid().at(4, 1, 1), util.grid().at(3, 1, 2))) {
            fire.put(pos, gasFire());
        }
        Build.revealAll(scene, util, fire, Direction.UP);
        Build.change(scene, alarm, s -> s.setValue(FireAlarmBlock.STATUS, FireAlarmStatus.ALARM));
        Say.at(scene, Power.panel(util, alarm, Direction.NORTH), "fire_alarm.trips");

        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        Say.at(scene, Power.panel(util, controller, Direction.NORTH), "fire_alarm.danger");
        scene.markAsFinished();
    }

    /**
     * A firelock between two rooms shuts when its controller alarms and opens again once it's safe.
     * Shut across a vacuum it locks, and a crowbar forces it.
     */
    public static void firelock(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "firelock");
        BlockPos lower = util.grid().at(2, 1, 2);
        BlockPos controller = util.grid().at(3, 2, 1);
        Selection firelock = util.select().fromTo(lower, lower.above());

        // A wall across the floor with the firelock in it, open, its passage running north and south.
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(0, 1, 2), util.grid().at(4, 2, 2))) {
            if (pos.getX() != lower.getX()) {
                wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            }
        }
        BlockState open = AtmosBlocks.FIRELOCK
                .get()
                .defaultBlockState()
                .setValue(FirelockBlock.FACING, Direction.NORTH)
                .setValue(FirelockBlock.OPEN, true);
        wall.put(lower, open.setValue(FirelockBlock.HALF, DoubleBlockHalf.LOWER));
        wall.put(lower.above(), open.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
        Build.revealAll(scene, util, wall, Direction.DOWN);

        // The room's controller, on the wall beside it.
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

        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.OPEN, false));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.NORTH), "firelock.shuts");

        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.OPEN, true));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.NORTH), "firelock.opens");

        // Shut again, with air on this side and vacuum on the other: it locks.
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.OPEN, false));
        scene.idle(10);
        // The near room's air, and above the wall the far room's vacuum.
        Say.line(scene, LOCKING, "reading.pressure", Gas.pressure(101.325))
                .pointAt(util.vector().centerOf(util.grid().at(1, 1, 1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        Say.line(scene, LOCKING - 20, "reading.pressure", Gas.pressure(0))
                .pointAt(util.vector().centerOf(util.grid().at(1, 3, 3)))
                .placeNearTarget();
        scene.idle(20);
        Build.change(scene, firelock, s -> s.setValue(FirelockBlock.LOCKED, true));
        scene.idle(LOCKING - 40 + 10);

        Hand.use(scene, util, lower.above(), Direction.NORTH, ItemStack.EMPTY);
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.NORTH), "firelock.locked");

        Hand.use(scene, util, lower.above(), Direction.NORTH, new ItemStack(AtmosItems.CROWBAR.get()));
        Build.change(
                scene, firelock, s -> s.setValue(FirelockBlock.LOCKED, false).setValue(FirelockBlock.OPEN, true));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.NORTH), "firelock.crowbar");

        Hand.use(scene, util, lower.above(), Direction.NORTH, new ItemStack(AtmosItems.MULTITOOL.get()));
        Say.at(scene, util.vector().blockSurface(lower.above(), Direction.NORTH), "firelock.multitool");
        scene.markAsFinished();
    }

    /** How long the two sides' readings stay, in ticks. */
    private static final int LOCKING = Say.LINE + 20;

    /** An igniter sparks on a redstone signal and lights flammable gas in its room. */
    public static void igniter(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "igniter");
        BlockPos igniter = util.grid().at(2, 1, 1);
        BlockPos lever = igniter.east();
        // The plate's top: it's 2 pixels thick.
        Vec3 plate = util.vector().centerOf(igniter).add(0, -0.375, 0);

        Build.drop(scene, util, igniter, AtmosBlocks.IGNITER.get().defaultBlockState());
        Power.controller(scene, util, util.grid().at(3, 2, 3), Direction.NORTH, igniter, s -> s);
        Power.multitool(scene, util, igniter);

        // A lever beside it gives it a signal, and it sparks.
        Build.drop(
                scene,
                util,
                lever,
                Blocks.LEVER
                        .defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                        .setValue(LeverBlock.FACING, Direction.NORTH));
        Hand.use(scene, util, lever, Direction.UP, ItemStack.EMPTY);
        Build.change(scene, lever, s -> s.setValue(LeverBlock.POWERED, true));
        Build.change(scene, igniter, s -> s.setValue(IgniterBlock.POWERED, true).setValue(IgniterBlock.LIT, true));
        scene.effects().indicateRedstone(lever);
        int sparking = Say.LINE + 10;
        // A spark lasts two or three ticks, so several a tick, spread over the plate and flying out.
        scene.effects()
                .emitParticles(
                        plate,
                        (level, x, y, z) -> level.addParticle(
                                ParticleTypes.ELECTRIC_SPARK,
                                x + (level.random.nextDouble() - 0.5) * 0.6,
                                y,
                                z + (level.random.nextDouble() - 0.5) * 0.6,
                                (level.random.nextDouble() - 0.5) * 2,
                                level.random.nextDouble() * 2,
                                (level.random.nextDouble() - 0.5) * 2),
                        3,
                        sparking);
        Say.over(scene, sparking, plate, "igniter.sparks");
        scene.idle(sparking + 10);

        // The room's flammable gas lights in the air beside it, and spreads.
        BlockPos first = igniter.south();
        Build.reveal(scene, util, first, gasFire(), Direction.UP);
        Map<BlockPos, BlockState> spread = new LinkedHashMap<>();
        for (BlockPos pos : List.of(first.west(), first.above(), first.south(), igniter.above())) {
            spread.put(pos, gasFire());
        }
        Build.revealAll(scene, util, spread, Direction.UP);
        Say.at(scene, util.vector().topOf(first), "igniter.lights");
        scene.markAsFinished();
    }

    /** A gas fire, the framework's block for a hotspot in mid air (FIRE-018). */
    private static BlockState gasFire() {
        return BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath("zas", "gas_fire"))
                .defaultBlockState();
    }
}
