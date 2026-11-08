package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Power;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** The generator (CONT-061, CONT-066, CONT-067) and the recharger (CONT-113). */
public final class PowerScenes {
    private PowerScenes() {}

    /** A generator burns fuel, without the air, and powers the area air controller beside it. */
    public static void generator(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "generator");

        BlockPos generator = util.grid().at(2, 1, 2);
        Build.drop(
                scene,
                util,
                generator,
                AtmosBlocks.GENERATOR.get().defaultBlockState().setValue(GeneratorBlock.FACING, Direction.NORTH));

        Hand.use(scene, util, generator, Direction.UP, new ItemStack(Items.COAL));
        Build.change(scene, generator, s -> s.setValue(GeneratorBlock.LIT, true));
        Say.at(scene, util.vector().topOf(generator), "generator.fuel");
        Say.at(scene, util.vector().topOf(generator), "generator.air");

        // An unpowered controller on the wall above it, which its FE turns on.
        BlockPos controller = generator.above();
        Map<BlockPos, BlockState> wall = new LinkedHashMap<>();
        wall.put(generator.south(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        wall.put(controller.south(), AtmosBlocks.STATION_WALL.get().defaultBlockState());
        Build.revealAll(scene, util, wall, Direction.DOWN);
        Build.reveal(
                scene,
                util,
                controller,
                AtmosBlocks.AREA_AIR_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AreaAirControllerBlock.FACING, Direction.NORTH)
                        .setValue(AreaAirControllerBlock.STATUS, ControllerStatus.UNPOWERED),
                Direction.SOUTH);
        scene.idle(10);
        Build.change(scene, controller, s -> s.setValue(AreaAirControllerBlock.STATUS, ControllerStatus.SAFE));
        Say.at(scene, util.vector().blockSurface(controller, Direction.NORTH), "generator.controller");
        scene.markAsFinished();
    }

    /** A recharger on a controller's power charges the battery put in it, which an empty hand takes out. */
    public static void recharger(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "recharger");

        BlockPos recharger = util.grid().at(2, 1, 2);
        Build.drop(
                scene,
                util,
                recharger,
                AtmosBlocks.RECHARGER.get().defaultBlockState().setValue(RechargerBlock.FACING, Direction.NORTH));
        Power.controller(scene, util, util.grid().at(1, 2, 3), Direction.NORTH, recharger, s -> s);
        Power.multitool(scene, util, recharger);

        // A battery goes in, and its gauge fills while the line is up.
        Hand.use(scene, util, recharger, Direction.UP, new ItemStack(AtmosItems.BATTERY.get()));
        Build.change(scene, recharger, s -> s.setValue(RechargerBlock.BATTERY, true));
        Say.over(scene, Say.LINE + 4 * CHARGE_STEP, util.vector().topOf(recharger), "recharger.battery");
        for (int charge = 1; charge <= 4; charge++) {
            scene.idle(CHARGE_STEP);
            int now = charge;
            Build.change(scene, recharger, s -> s.setValue(RechargerBlock.CHARGE, now));
        }
        scene.idle(Say.LINE);

        Hand.use(scene, util, recharger, Direction.UP, ItemStack.EMPTY);
        Build.change(
                scene, recharger, s -> s.setValue(RechargerBlock.BATTERY, false).setValue(RechargerBlock.CHARGE, 0));
        Say.at(scene, util.vector().topOf(recharger), "recharger.take");
        scene.markAsFinished();
    }

    /** The ticks between the recharger's gauge steps. */
    private static final int CHARGE_STEP = 15;
}
