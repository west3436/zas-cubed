package io.github.west3436.zas.atmos.compat.ponder.scenes;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.compat.ponder.scene.Build;
import io.github.west3436.zas.atmos.compat.ponder.scene.Say;
import io.github.west3436.zas.atmos.compat.ponder.scene.Stage;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.HeatExchangerTint;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Radiator pipe and its adaptor (CONT-143 to CONT-145, HEAT-020, HEAT-021), and the heat exchanger
 * (CONT-147, CONT-148, HEAT-022). None needs power. Ponder's world runs no gas, so a network's
 * temperature shows as a reading or as an exchanger's colour, set by the scene.
 */
public final class RadiatorScenes {
    private RadiatorScenes() {}

    /** A run of radiator pipe joined to pipe by an adaptor, and what it does with heat. */
    public static void radiator(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "radiator_pipe");

        // A run of radiator pipe with bends, its first end where the adaptor will go.
        BlockPos first = util.grid().at(2, 1, 2);
        BlockPos bend = util.grid().at(4, 1, 3);
        BlockPos end = util.grid().at(2, 1, 4);
        BlockState radiator = AtmosBlocks.RADIATOR_PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> run = new LinkedHashMap<>();
        for (BlockPos pos : List.of(
                first,
                util.grid().at(3, 1, 2),
                util.grid().at(4, 1, 2),
                bend,
                util.grid().at(4, 1, 4),
                util.grid().at(3, 1, 4),
                end)) {
            run.put(pos, radiator);
        }
        Build.revealAll(scene, util, run, Direction.DOWN);
        Say.at(scene, util.vector().topOf(first), "radiator.joins");

        // Pipe, capped at its far end, and the adaptor between it and the radiator pipe.
        BlockPos adaptor = util.grid().at(1, 1, 2);
        BlockPos pipeEnd = util.grid().at(0, 1, 2);
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> pipes = new LinkedHashMap<>();
        pipes.put(util.grid().at(0, 1, 0), PumpScenes.cap(Direction.SOUTH));
        pipes.put(util.grid().at(0, 1, 1), pipe);
        pipes.put(pipeEnd, pipe);
        Build.revealAll(scene, util, pipes, Direction.DOWN);
        Build.drop(
                scene,
                util,
                adaptor,
                AtmosBlocks.RADIATOR_ADAPTOR
                        .get()
                        .defaultBlockState()
                        .setValue(RadiatorAdaptorBlock.FACING, Direction.EAST));
        scene.overlay().showOutline(PonderPalette.GREEN, "pipe", util.select().position(pipeEnd), Say.LINE);
        scene.overlay()
                .showOutline(PonderPalette.GREEN, "radiator", util.select().position(first), Say.LINE);
        Say.at(scene, util.vector().topOf(adaptor), "radiator.adaptor");

        // Unlike a pipe's, its end keeps its gas (CONT-144).
        scene.overlay().showOutline(PonderPalette.GREEN, "end", util.select().position(end), Say.LINE);
        Say.at(scene, util.vector().topOf(end), "radiator.ends");

        // Its gas and the gas around it trade heat (HEAT-020).
        Say.at(scene, util.vector().topOf(first), "radiator.air");

        // In a vacuum it radiates its heat away, and its gas cools (HEAT-021).
        Say.at(scene, util.vector().topOf(first), "radiator.vacuum");
        for (double kelvin : new double[] {873.15, 673.15, 473.15}) {
            Say.line(scene, READING, "reading.temperature", Readout.temperature(kelvin))
                    .pointAt(util.vector().topOf(first))
                    .placeNearTarget();
            scene.idle(READING);
        }
        scene.idle(10);

        // In a room with almost no air, it does nothing (HEAT-020).
        Say.at(scene, util.vector().topOf(first), "radiator.empty_room");
        scene.markAsFinished();
    }

    /** How long each of a cooling network's readings stays, in ticks. */
    private static final int READING = 30;

    /** A pair of heat exchangers, a hot network and a cold one coming to one temperature. */
    public static void exchanger(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "heat_exchanger");
        BlockPos hot = util.grid().at(2, 1, 2);
        BlockPos cold = util.grid().at(3, 1, 2);
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();

        // One exchanger, its box facing east and its pipe joining the west face.
        BlockPos hotPipe = util.grid().at(1, 1, 2);
        Map<BlockPos, BlockState> hotPipes = new LinkedHashMap<>();
        hotPipes.put(util.grid().at(1, 1, 0), PumpScenes.cap(Direction.SOUTH));
        hotPipes.put(util.grid().at(1, 1, 1), pipe);
        hotPipes.put(hotPipe, pipe);
        Build.revealAll(scene, util, hotPipes, Direction.DOWN);
        Build.drop(scene, util, hot, exchanger(Direction.EAST));
        scene.overlay().showOutline(PonderPalette.GREEN, "pipe", util.select().position(hotPipe), Say.LINE);
        Say.at(scene, util.vector().topOf(hot), "exchanger.pipe");

        // Another, box to box with the first, on its own network.
        Map<BlockPos, BlockState> coldPipes = new LinkedHashMap<>();
        coldPipes.put(util.grid().at(4, 1, 4), PumpScenes.cap(Direction.NORTH));
        coldPipes.put(util.grid().at(4, 1, 3), pipe);
        coldPipes.put(util.grid().at(4, 1, 2), pipe);
        Build.revealAll(scene, util, coldPipes, Direction.DOWN);
        Build.drop(scene, util, cold, exchanger(Direction.WEST));
        scene.overlay().showOutline(PonderPalette.GREEN, "pair", util.select().fromTo(hot, cold), Say.LINE);
        Say.at(scene, util.vector().topOf(cold), "exchanger.pair");

        // Hot gas on one side and cold on the other: each coil shows its gas's temperature (CONT-148).
        int[] hotBands = steps(HeatExchangerTint.band(1273.15), HeatExchangerTint.band(743.15));
        int[] coldBands = steps(HeatExchangerTint.band(213.15), HeatExchangerTint.band(743.15));
        Build.change(scene, hot, s -> s.setValue(HeatExchangerBlock.HEAT, hotBands[0]));
        Build.change(scene, cold, s -> s.setValue(HeatExchangerBlock.HEAT, coldBands[0]));
        Say.at(scene, util.vector().topOf(hot), "exchanger.colour");

        // The pair moves heat across until both networks, the same size, are at one temperature.
        Say.over(scene, (hotBands.length - 1) * SHARE + Say.LINE, util.vector().topOf(hot), "exchanger.share");
        for (int i = 1; i < hotBands.length; i++) {
            scene.idle(SHARE);
            int h = hotBands[i];
            int c = coldBands[i];
            Build.change(scene, hot, s -> s.setValue(HeatExchangerBlock.HEAT, h));
            Build.change(scene, cold, s -> s.setValue(HeatExchangerBlock.HEAT, c));
        }
        scene.idle(Say.LINE + 10);
        scene.markAsFinished();
    }

    /** The ticks between the colours' steps as the pair shares heat. */
    private static final int SHARE = 15;

    /** A heat exchanger with its box facing a way, at room temperature's colour. */
    private static BlockState exchanger(Direction box) {
        return AtmosBlocks.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, box);
    }

    /**
     * Bands from one to another, each step half of what's left, as a temperature closing on another
     * does: quickly at first, then slowly.
     */
    private static int[] steps(int from, int to) {
        int[] bands = new int[5];
        double band = from;
        for (int i = 0; i < bands.length - 1; i++) {
            bands[i] = (int) Math.round(band);
            band += (to - band) / 2;
        }
        bands[bands.length - 1] = to;
        return bands;
    }
}
