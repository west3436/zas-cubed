package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Power;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
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
import net.minecraft.world.level.block.state.BlockState;

/** The pressure pump (CONT-011) and the volume pump (CONT-012). */
public final class PumpScenes {
    private PumpScenes() {}

    /** A pressure pump fills its output to a target pressure. */
    public static void pressure(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "pressure_pump");
        pump(scene, util, AtmosBlocks.PRESSURE_PUMP.get(), "pump.arrows", "pressure_pump.use");
    }

    /** A volume pump moves a set volume each second. */
    public static void volume(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "volume_pump");
        pump(scene, util, AtmosBlocks.VOLUME_PUMP.get(), "volume_pump.lamp", "volume_pump.use");
    }

    /**
     * A pump in a capped run of pipe: its two ports, its power, the way its gas goes, its window and
     * a wrench turning it (CLIENT-026).
     *
     * @param way the line saying which way its gas goes
     * @param use the line saying what its window sets
     */
    private static void pump(SceneBuilder scene, SceneBuildingUtil util, Block block, String way, String use) {
        BlockPos pump = util.grid().at(2, 1, 2);
        List<BlockPos> run = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            run.add(util.grid().at(x, 1, 2));
        }

        // A run of pipe, capped at both ends, with the pump in the middle, its gas going east.
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> line = new LinkedHashMap<>();
        line.put(run.get(0), cap(Direction.EAST));
        line.put(run.get(1), pipe);
        line.put(run.get(3), pipe);
        line.put(run.get(4), cap(Direction.WEST));
        Build.revealAll(scene, util, line, Direction.DOWN);
        Build.drop(scene, util, pump, block.defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        scene.overlay().showOutline(PonderPalette.GREEN, "back", util.select().position(run.get(1)), Say.LINE);
        scene.overlay().showOutline(PonderPalette.GREEN, "front", util.select().position(run.get(3)), Say.LINE);
        Say.at(scene, util.vector().topOf(pump), "inline.ends");

        Power.controller(
                scene,
                util,
                util.grid().at(3, 2, 3),
                Direction.NORTH,
                pump,
                s -> s.setValue(InlineDeviceBlock.ON, true));
        Power.multitool(scene, util, pump);

        int flowing = Say.LINE + 40;
        Say.over(scene, flowing, util.vector().topOf(pump), way);
        Gas.flow(scene, flowing, run);

        Hand.use(scene, util, pump, Direction.UP, ItemStack.EMPTY);
        Say.at(scene, util.vector().topOf(pump), use);

        // Each use of a wrench on its top turns it a quarter turn about the vertical, across the run and
        // then round, while its line is up. Then its gas goes the other way.
        ItemStack wrench = new ItemStack(AtmosItems.WRENCH.get());
        List<Direction> turns = List.of(Direction.SOUTH, Direction.WEST);
        Say.over(
                scene,
                turns.size() * (Hand.PAUSE + TURN) + Say.LINE,
                util.vector().topOf(pump),
                "wrench.turn");
        for (Direction to : turns) {
            Hand.use(scene, util, pump, Direction.UP, wrench);
            Build.change(scene, pump, s -> s.setValue(InlineDeviceBlock.FACING, to));
            scene.idle(TURN);
        }
        Gas.flow(scene, Say.LINE, run.reversed());
        scene.markAsFinished();
    }

    /** The ticks a wrench's turn stays before the next. */
    private static final int TURN = 25;

    /** A pipe end cap closing a pipe on one face. */
    static BlockState cap(Direction toward) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, toward);
    }
}
