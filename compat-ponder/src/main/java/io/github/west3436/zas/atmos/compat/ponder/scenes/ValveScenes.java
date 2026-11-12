package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.valve.ValveBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** The manual and redstone valves (CONT-010), and an open valve joining its sides (CONT-075). */
public final class ValveScenes {
    private ValveScenes() {}

    /** A manual valve turned by hand, open and shut. */
    public static void manual(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "manual_valve");
        List<BlockPos> run = line(scene, util, AtmosBlocks.MANUAL_VALVE.get());
        BlockPos valve = run.get(2);

        Hand.use(scene, util, valve, Direction.UP, ItemStack.EMPTY);
        Build.change(scene, valve, s -> s.setValue(ValveBlock.OPEN, true));
        Say.at(scene, util.vector().topOf(valve), "manual_valve.use");

        flowing(scene, util, run, valve, true, "valve.open");
        Hand.use(scene, util, valve, Direction.UP, ItemStack.EMPTY);
        Build.change(scene, valve, s -> s.setValue(ValveBlock.OPEN, false));
        flowing(scene, util, run, valve, false, "valve.shut");
        scene.markAsFinished();
    }

    /** A redstone valve following a lever beside it, and not a hand. */
    public static void redstone(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "redstone_valve");
        List<BlockPos> run = line(scene, util, AtmosBlocks.REDSTONE_VALVE.get());
        BlockPos valve = run.get(2);

        BlockPos lever = valve.north();
        Build.drop(
                scene,
                util,
                lever,
                Blocks.LEVER
                        .defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                        .setValue(LeverBlock.FACING, Direction.EAST));

        Hand.use(scene, util, lever, Direction.UP, ItemStack.EMPTY);
        Build.change(scene, lever, s -> s.setValue(LeverBlock.POWERED, true));
        Build.change(scene, valve, s -> s.setValue(ValveBlock.OPEN, true));
        flowing(scene, util, run, valve, true, "redstone_valve.signal");

        Hand.use(scene, util, lever, Direction.UP, ItemStack.EMPTY);
        Build.change(scene, lever, s -> s.setValue(LeverBlock.POWERED, false));
        Build.change(scene, valve, s -> s.setValue(ValveBlock.OPEN, false));
        flowing(scene, util, run, valve, false, "redstone_valve.no_signal");

        Hand.use(scene, util, valve, Direction.UP, ItemStack.EMPTY);
        scene.overlay().showOutline(PonderPalette.RED, "shut", util.select().position(valve), Say.LINE);
        Say.at(scene, util.vector().topOf(valve), "redstone_valve.hand");
        scene.markAsFinished();
    }

    /**
     * A run along x, capped at both ends, with a shut valve in the middle, and the line saying pipes
     * join both its ends.
     *
     * @return the run from west to east, the valve third
     */
    private static List<BlockPos> line(SceneBuilder scene, SceneBuildingUtil util, Block valve) {
        List<BlockPos> run = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            run.add(util.grid().at(x, 1, 2));
        }
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> line = new LinkedHashMap<>();
        line.put(run.get(0), PumpScenes.cap(Direction.EAST));
        line.put(run.get(1), pipe);
        line.put(run.get(3), pipe);
        line.put(run.get(4), PumpScenes.cap(Direction.WEST));
        Build.revealAll(scene, util, line, Direction.DOWN);
        Build.drop(scene, util, run.get(2), valve.defaultBlockState().setValue(ValveBlock.AXIS, Direction.Axis.X));
        scene.overlay().showOutline(PonderPalette.GREEN, "west", util.select().position(run.get(1)), Say.LINE);
        scene.overlay().showOutline(PonderPalette.GREEN, "east", util.select().position(run.get(3)), Say.LINE);
        Say.at(scene, util.vector().topOf(run.get(2)), "valve.ends");
        return run;
    }

    /**
     * Gas moving from the run's west end while a line is up: through the valve and on when it's open,
     * stopping at it when it's shut.
     */
    private static void flowing(
            SceneBuilder scene, SceneBuildingUtil util, List<BlockPos> run, BlockPos valve, boolean open, String key) {
        int ticks = Say.LINE + 40;
        Say.over(scene, ticks, util.vector().topOf(valve), key);
        Gas.flow(scene, ticks, open ? run : run.subList(0, run.indexOf(valve) + 1));
    }
}
