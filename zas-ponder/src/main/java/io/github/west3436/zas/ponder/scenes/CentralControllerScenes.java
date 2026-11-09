package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.ConsoleScreen;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Hand;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The central atmospherics controller (CONT-115 to CONT-124). */
public final class CentralControllerScenes {
    private CentralControllerScenes() {}

    /** A powered console claims, powers and lists the area air controllers around it. */
    public static void central(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "central_atmos_controller");

        // The console faces north; from its front, its right is to the west.
        Direction front = Direction.NORTH;
        BlockPos origin = util.grid().at(3, 1, 1);
        List<BlockPos> console = CentralControllerBlock.parts(origin, front);
        BlockState lowerLeft = AtmosBlocks.CENTRAL_ATMOS_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(CentralControllerBlock.FACING, front)
                .setValue(CentralControllerBlock.SCREEN, ConsoleScreen.UNPOWERED);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int i = 0; i < console.size(); i++) {
            blocks.put(console.get(i), CentralControllerBlock.part(lowerLeft, i));
        }
        Build.revealAll(scene, util, blocks, Direction.DOWN);

        BlockPos generator = origin.east();
        Build.drop(
                scene,
                util,
                generator,
                AtmosBlocks.GENERATOR
                        .get()
                        .defaultBlockState()
                        .setValue(GeneratorBlock.FACING, front)
                        .setValue(GeneratorBlock.LIT, true));
        screen(scene, util, console, ConsoleScreen.NORMAL);
        Say.at(scene, util.vector().topOf(generator), "central.power");

        // Area air controllers on the walls of rooms around it.
        List<BlockPos> controllers = List.of(
                util.grid().at(0, 2, 5), util.grid().at(3, 2, 5), util.grid().at(6, 2, 5));
        Map<BlockPos, BlockState> walls = new LinkedHashMap<>();
        for (BlockPos controller : controllers) {
            walls.put(controller.south().below(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
            walls.put(controller.south(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        }
        Build.revealAll(scene, util, walls, Direction.DOWN);
        Map<BlockPos, BlockState> panels = new LinkedHashMap<>();
        for (BlockPos controller : controllers) {
            panels.put(controller, panel(ControllerStatus.SAFE));
        }
        Build.revealAll(scene, util, panels, Direction.SOUTH);

        // A line from the console to each, while it's said that it claims them.
        int claiming = 2 * (Say.LINE + 10);
        Vec3 top = util.vector()
                .topOf(origin.above())
                .add(Vec3.atLowerCornerOf(front.getCounterClockWise().getNormal())
                        .scale(0.5));
        for (BlockPos controller : controllers) {
            scene.overlay()
                    .showLine(
                            PonderPalette.GREEN,
                            top,
                            util.vector().blockSurface(controller, Direction.NORTH),
                            claiming);
        }
        Say.at(scene, util.vector().blockSurface(controllers.get(0), Direction.NORTH), "central.claims");
        Say.at(scene, util.vector().blockSurface(controllers.get(1), Direction.NORTH), "central.powers");

        Hand.use(scene, util, origin.above(), front, ItemStack.EMPTY);
        Say.at(scene, util.vector().blockSurface(origin.above(), front), "central.window");

        // One room's air turns dangerous, and the console shows it.
        BlockPos alarmed = controllers.get(2);
        Build.change(scene, alarmed, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.DANGER2));
        screen(scene, util, console, ConsoleScreen.DANGER2);
        Say.at(scene, util.vector().blockSurface(origin.above(), front), "central.screen");

        // A multitool takes an area air controller, then the console.
        ItemStack multitool = new ItemStack(AtmosItems.MULTITOOL.get());
        Hand.use(scene, util, controllers.get(0), Direction.NORTH, multitool);
        scene.idle(10);
        Hand.use(scene, util, origin, front, multitool);
        Say.at(scene, util.vector().blockSurface(origin, front), "central.multitool");
        scene.markAsFinished();
    }

    /** An area air controller facing north. */
    private static BlockState panel(ControllerStatus status) {
        return AtmosBlocks.AREA_AIR_CONTROLLER
                .get()
                .defaultBlockState()
                .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                .setValue(AreaAirControllerBlock.STATUS, status);
    }

    /** Sets what all four of the console's blocks show, together. */
    private static void screen(
            SceneBuilder scene, SceneBuildingUtil util, List<BlockPos> console, ConsoleScreen screen) {
        Selection all = util.select().position(console.get(0));
        for (BlockPos part : console.subList(1, console.size())) {
            all = all.add(util.select().position(part));
        }
        Build.change(scene, all, s -> s.setValue(CentralControllerBlock.SCREEN, screen));
    }
}
