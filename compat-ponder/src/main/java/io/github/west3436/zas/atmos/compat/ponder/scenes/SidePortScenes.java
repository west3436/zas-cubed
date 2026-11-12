package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Gas;
import io.github.west3436.zas.atmos.compat.ponder.scene.Hand;
import io.github.west3436.zas.atmos.compat.ponder.scene.Power;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
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

/**
 * The filter (CONT-013) and the mixer (CONT-077): inline devices with a third port on one side, on the
 * right of their gas, or on the left for the mirrored items (D-77).
 */
public final class SidePortScenes {
    private SidePortScenes() {}

    /** A filter sending the gases picked to its side and the rest out of its front. */
    public static void filter(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "filter");
        Layout at = new Layout(util);
        build(scene, util, at, AtmosBlocks.FILTER.get());
        scene.overlay().showOutline(PonderPalette.GREEN, "back", util.select().position(at.back), Say.LINE);
        Say.at(scene, util.vector().blockSurface(at.device, Direction.WEST), "side_port.back");
        power(scene, util, at);

        int ticks = Say.LINE + 40;
        Say.over(scene, ticks, util.vector().topOf(at.side), "filter.side");
        Gas.flow(scene, ticks, List.of(at.backCap, at.back, at.device, at.side, at.sideCap));
        Say.over(scene, ticks, util.vector().topOf(at.front), "filter.front");
        Gas.flow(scene, ticks, List.of(at.backCap, at.back, at.device, at.front, at.frontCap));

        Hand.use(scene, util, at.device, Direction.UP, ItemStack.EMPTY);
        Say.at(scene, util.vector().topOf(at.device), "filter.use");
        mirrored(scene, util, at);
        scene.markAsFinished();
    }

    /** A mixer taking gas at its back and side and sending the mix out of its front. */
    public static void mixer(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "mixer");
        Layout at = new Layout(util);
        build(scene, util, at, AtmosBlocks.MIXER.get());
        scene.overlay().showOutline(PonderPalette.GREEN, "back", util.select().position(at.back), Say.LINE);
        scene.overlay().showOutline(PonderPalette.GREEN, "side", util.select().position(at.side), Say.LINE);
        Say.at(scene, util.vector().topOf(at.device), "mixer.inputs");
        power(scene, util, at);

        int ticks = Say.LINE + 40;
        Say.over(scene, ticks, util.vector().topOf(at.front), "mixer.front");
        Gas.flow(
                scene,
                ticks,
                List.of(at.backCap, at.back, at.device, at.front, at.frontCap),
                List.of(at.sideCap, at.side, at.device, at.front, at.frontCap));

        Hand.use(scene, util, at.device, Direction.UP, ItemStack.EMPTY);
        Say.at(scene, util.vector().topOf(at.device), "mixer.use");
        mirrored(scene, util, at);
        scene.markAsFinished();
    }

    /**
     * Where everything goes: a run along x, its gas going east, with the device in the middle and its
     * side port to the south, the right of its gas. Each pipe ends in a cap.
     */
    private static final class Layout {
        final BlockPos backCap;
        final BlockPos back;
        final BlockPos device;
        final BlockPos front;
        final BlockPos frontCap;
        final BlockPos side;
        final BlockPos sideCap;
        final BlockPos controller;

        Layout(SceneBuildingUtil util) {
            backCap = util.grid().at(0, 1, 2);
            back = util.grid().at(1, 1, 2);
            device = util.grid().at(2, 1, 2);
            front = util.grid().at(3, 1, 2);
            frontCap = util.grid().at(4, 1, 2);
            side = util.grid().at(2, 1, 3);
            sideCap = util.grid().at(2, 1, 4);
            controller = util.grid().at(3, 2, 3);
        }
    }

    private static void build(SceneBuilder scene, SceneBuildingUtil util, Layout at, Block block) {
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> pipes = new LinkedHashMap<>();
        pipes.put(at.backCap, PumpScenes.cap(Direction.EAST));
        pipes.put(at.back, pipe);
        pipes.put(at.front, pipe);
        pipes.put(at.frontCap, PumpScenes.cap(Direction.WEST));
        pipes.put(at.side, pipe);
        pipes.put(at.sideCap, PumpScenes.cap(Direction.NORTH));
        Build.revealAll(scene, util, pipes, Direction.DOWN);
        Build.drop(
                scene, util, at.device, block.defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
    }

    private static void power(SceneBuilder scene, SceneBuildingUtil util, Layout at) {
        Power.controller(
                scene, util, at.controller, Direction.NORTH, at.device, s -> s.setValue(InlineDeviceBlock.ON, true));
        Power.multitool(scene, util, at.device);
    }

    /** The mirrored item's block: its side port on the left, so its side pipe moves across. */
    private static void mirrored(SceneBuilder scene, SceneBuildingUtil util, Layout at) {
        BlockPos side = at.device.north();
        BlockPos sideCap = side.north();
        Say.over(scene, 2 * Build.SHOW + Say.LINE, util.vector().topOf(side), "side_port.mirrored");
        Build.remove(scene, util, at.side, at.sideCap);
        Build.change(scene, at.device, s -> s.setValue(SidePortBlock.MIRRORED, true));
        Map<BlockPos, BlockState> left = new LinkedHashMap<>();
        left.put(side, AtmosBlocks.PIPE.get().defaultBlockState());
        left.put(sideCap, PumpScenes.cap(Direction.SOUTH));
        Build.revealAll(scene, util, left, Direction.DOWN);
        scene.idle(Say.LINE);
    }
}
