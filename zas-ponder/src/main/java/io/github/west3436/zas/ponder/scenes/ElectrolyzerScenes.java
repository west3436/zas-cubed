package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The water electrolyzer (CONT-055, CONT-056). */
public final class ElectrolyzerScenes {
    private ElectrolyzerScenes() {}

    /** Water goes in, by bucket or at its back inlet; O₂ and H₂ leave by their own sides. */
    public static void electrolyzer(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "electrolyzer");

        // Facing north, its O₂ leaves to the east and its H₂ to the west; its inlet is on the south.
        BlockPos electrolyzer = util.grid().at(2, 1, 2);
        BlockState facing =
                AtmosBlocks.ELECTROLYZER.get().defaultBlockState().setValue(ElectrolyzerBlock.FACING, Direction.NORTH);
        Build.drop(scene, util, electrolyzer, facing);

        Hand.use(scene, util, electrolyzer, Direction.UP, new ItemStack(Items.WATER_BUCKET));
        Build.change(scene, electrolyzer, s -> s.setValue(ElectrolyzerBlock.WATER, 1));
        Say.at(scene, util.vector().topOf(electrolyzer), "electrolyzer.water");

        // The view turns to its back, where the inlet is, and back again.
        scene.rotateCameraY(180);
        scene.idle(TURN);
        int x = electrolyzer.getX();
        int y = electrolyzer.getY();
        int z = electrolyzer.getZ();
        AABB inlet = new AABB(x + 5 / 16d, y + 5 / 16d, z + 15 / 16d, x + 11 / 16d, y + 11 / 16d, z + 1);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "inlet", inlet.inflate(1 / 32d), Say.LINE);
        Say.at(scene, util.vector().blockSurface(electrolyzer, Direction.SOUTH), "electrolyzer.inlet");
        scene.rotateCameraY(-180);
        scene.idle(TURN);

        // A capped pipe on each side it gives gas from.
        BlockPos oxygen = electrolyzer.east();
        BlockPos hydrogen = electrolyzer.west();
        Map<BlockPos, BlockState> pipes = new LinkedHashMap<>();
        pipes.put(oxygen, AtmosBlocks.PIPE.get().defaultBlockState());
        pipes.put(oxygen.east(), PumpScenes.cap(Direction.WEST));
        pipes.put(hydrogen, AtmosBlocks.PIPE.get().defaultBlockState());
        pipes.put(hydrogen.west(), PumpScenes.cap(Direction.EAST));
        Build.revealAll(scene, util, pipes, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "oxygen", util.select().position(oxygen), Say.LINE);
        Say.at(scene, util.vector().topOf(oxygen), "electrolyzer.oxygen");
        scene.overlay()
                .showOutline(PonderPalette.GREEN, "hydrogen", util.select().position(hydrogen), Say.LINE);
        Say.at(scene, util.vector().topOf(hydrogen), "electrolyzer.hydrogen");

        Power.controller(
                scene,
                util,
                util.grid().at(3, 2, 3),
                Direction.NORTH,
                electrolyzer,
                s -> s.setValue(ElectrolyzerBlock.ON, true));
        Power.multitool(scene, util, electrolyzer);

        int flowing = Say.LINE + 40;
        Say.over(scene, flowing, util.vector().topOf(electrolyzer), "electrolyzer.splits");
        Gas.flow(
                scene,
                flowing,
                List.of(electrolyzer, oxygen, oxygen.east()),
                List.of(electrolyzer, hydrogen, hydrogen.west()));
        scene.markAsFinished();
    }

    /** The ticks the view takes to turn. */
    private static final int TURN = 30;
}
