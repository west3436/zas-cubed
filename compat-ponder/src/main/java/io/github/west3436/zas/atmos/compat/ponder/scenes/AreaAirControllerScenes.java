package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Power;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.vent.VentMode;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The area air controller (CONT-040, CONT-041, CONT-048, CONT-062, CONT-068, D-68): FE from a block
 * beside it, power for the parts in its room, its modes and its redstone signal.
 */
public final class AreaAirControllerScenes {
    private AreaAirControllerScenes() {}

    /** A controller on a wall, fed by a generator, runs a vent and a scrubber in its modes. */
    public static void controller(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "area_air_controller");

        BlockPos controller = util.grid().at(2, 2, 3);
        BlockPos generator = controller.below();
        BlockPos lamp = util.grid().at(2, 3, 4);
        BlockPos vent = util.grid().at(3, 0, 1);
        BlockPos scrubber = util.grid().at(1, 0, 1);
        Vec3 panel = Power.panel(util, controller, Direction.NORTH);

        // The wall, with a lamp on it, and the controller on it, unpowered.
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(util.grid().at(1, 1, 4), util.grid().at(3, 2, 4))) {
            wall.put(pos.immutable(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        wall.put(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.NORTH),
                Direction.SOUTH);

        // A generator under it fills its buffer.
        Build.drop(
                scene,
                util,
                generator,
                AtmosBlocks.GENERATOR.get().defaultBlockState().setValue(GeneratorBlock.FACING, Direction.NORTH));
        Build.change(scene, generator, s -> s.setValue(GeneratorBlock.LIT, true));
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
        Say.at(scene, util.vector().blockSurface(generator, Direction.NORTH), "controller.fe");

        // The parts in its room, in its floor.
        Map<BlockPos, BlockState> parts = new LinkedHashMap<>();
        parts.put(vent, AtmosBlocks.VENT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        parts.put(
                scrubber,
                AtmosBlocks.SCRUBBER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        Build.sink(scene, util, parts);
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.BLOW));
        Build.change(scene, scrubber, s -> s.setValue(ScrubberMode.PROPERTY, ScrubberMode.SCRUB));
        Say.at(scene, panel, "controller.powers");

        Hand.use(scene, util, controller, Direction.NORTH, ItemStack.EMPTY);
        Say.at(scene, panel, "controller.use");

        // Scrubbing: vents fill and scrubbers scrub.
        Gas.puff(scene, util, vent, Direction.UP, MODE);
        Gas.draw(scene, util, scrubber, Direction.UP, MODE);
        Say.over(scene, MODE, panel, "controller.scrubbing");
        scene.idle(MODE + 10);

        // Panic: vents off, scrubbers take everything, twice as fast.
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.OFF));
        Build.change(scene, scrubber, s -> s.setValue(ScrubberMode.PROPERTY, ScrubberMode.PANIC));
        Gas.draw(scene, util, scrubber, Direction.UP, MODE);
        Gas.draw(scene, util, scrubber, Direction.UP, MODE);
        Say.over(scene, MODE, panel, "controller.panic");
        scene.idle(MODE + 10);

        // Cycle: Panic until the room is nearly empty, then Fill.
        int half = MODE / 2;
        Say.over(scene, MODE, panel, "controller.cycle");
        Gas.draw(scene, util, scrubber, Direction.UP, half);
        Gas.draw(scene, util, scrubber, Direction.UP, half);
        scene.idle(half);
        Build.change(scene, scrubber, s -> s.setValue(ScrubberMode.PROPERTY, ScrubberMode.OFF));
        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.BLOW));
        Gas.puff(scene, util, vent, Direction.UP, half);
        scene.idle(half + 10);

        // Danger: its panel shows it, and its signal lights a lamp on the wall it powers.
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        Build.change(scene, lamp, s -> s.setValue(RedstoneLampBlock.LIT, true));
        scene.effects().indicateRedstone(lamp);
        Say.at(scene, panel, "controller.redstone");
        scene.markAsFinished();
    }

    /** How long each mode is shown, in ticks. */
    private static final int MODE = Say.LINE + 20;
}
