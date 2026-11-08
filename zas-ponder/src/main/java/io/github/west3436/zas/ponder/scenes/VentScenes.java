package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.ScrubberMode;
import io.github.west3436.zas.atmos.tool.WrenchItem;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Gas;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Power;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The devices with a grille that face any of six ways and take a pipe only on their back: the vent
 * (CONT-014), the scrubber (CONT-015, CONT-079) and the passive vent (CONT-016).
 */
public final class VentScenes {
    private VentScenes() {}

    /** A vent blows its pipes' gas into the room, or siphons the room's into its pipes. */
    public static void vent(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "vent");
        BlockPos vent = util.grid().at(2, 1, 2);
        List<BlockPos> run = onPipe(scene, util, vent, AtmosBlocks.VENT.get().defaultBlockState());

        Power.controller(
                scene,
                util,
                util.grid().at(3, 2, 3),
                Direction.NORTH,
                vent,
                s -> s.setValue(VentMode.PROPERTY, VentMode.BLOW));
        Power.multitool(scene, util, vent);

        Gas.puff(scene, util, vent, Direction.UP, FLOWING);
        Say.over(scene, FLOWING, util.vector().topOf(vent), "vent.blows");
        Gas.flow(scene, FLOWING, run);

        Build.change(scene, vent, s -> s.setValue(VentMode.PROPERTY, VentMode.SIPHON));
        Gas.draw(scene, util, vent, Direction.UP, FLOWING);
        Say.over(scene, FLOWING, util.vector().topOf(vent), "vent.siphons");
        Gas.flow(scene, FLOWING, run.reversed());
        scene.markAsFinished();
    }

    /** A scrubber takes the room's bad gases into its pipes, or all its air in Panic. */
    public static void scrubber(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "scrubber");
        BlockPos scrubber = util.grid().at(2, 1, 2);
        List<BlockPos> run =
                onPipe(scene, util, scrubber, AtmosBlocks.SCRUBBER.get().defaultBlockState());

        Power.controller(
                scene,
                util,
                util.grid().at(3, 2, 3),
                Direction.NORTH,
                scrubber,
                s -> s.setValue(ScrubberMode.PROPERTY, ScrubberMode.SCRUB));
        Power.multitool(scene, util, scrubber);

        Gas.draw(scene, util, scrubber, Direction.UP, FLOWING);
        Say.over(scene, FLOWING, util.vector().topOf(scrubber), "scrubber.scrubs");
        Gas.flow(scene, FLOWING, run.reversed());

        // Panic samples twice the volume, so twice the puffs.
        Build.change(scene, scrubber, s -> s.setValue(ScrubberMode.PROPERTY, ScrubberMode.PANIC));
        Gas.draw(scene, util, scrubber, Direction.UP, FLOWING);
        Gas.draw(scene, util, scrubber, Direction.UP, FLOWING);
        Say.over(scene, FLOWING, util.vector().topOf(scrubber), "scrubber.panic");
        Gas.flow(scene, FLOWING, run.reversed());
        scene.markAsFinished();
    }

    /** A passive vent evens its pipes with the room, with no power. */
    public static void passive(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "passive_vent");
        BlockPos vent = util.grid().at(2, 1, 2);
        List<BlockPos> run =
                onPipe(scene, util, vent, AtmosBlocks.PASSIVE_VENT.get().defaultBlockState());

        Say.at(scene, util.vector().topOf(vent), "passive_vent.no_power");

        // Out while the pipes are fuller, then in while the room is.
        Say.over(scene, 2 * FLOWING, util.vector().topOf(vent), "passive_vent.evens");
        Gas.puff(scene, util, vent, Direction.UP, FLOWING);
        Gas.flow(scene, FLOWING, run);
        Gas.draw(scene, util, vent, Direction.UP, FLOWING);
        Gas.flow(scene, FLOWING, run.reversed());
        scene.markAsFinished();
    }

    /** How long gas is shown moving for each line, in ticks. */
    private static final int FLOWING = Say.LINE + 40;

    /** How long a device stands the wrong way before the wrench turns it, in ticks. */
    private static final int WRONG_WAY = 30;

    /**
     * A device dropped on a pipe under the floor with its grille the wrong way, then turned with a
     * wrench to face up, so the pipe joins its back. It lifts to show the pipe.
     *
     * @param device where the device goes, on the floor
     * @param state the device, facing any way
     * @return the blocks gas runs through, from the pipe's far end to the device
     */
    private static List<BlockPos> onPipe(
            SceneBuilder scene, SceneBuildingUtil util, BlockPos device, BlockState state) {
        // The pipe runs under the floor from the floor's edge to the device.
        List<BlockPos> run = new ArrayList<>();
        for (int z = util.grid().at(0, 0, 4).getZ(); z > device.getZ(); z--) {
            run.add(new BlockPos(device.getX(), 0, z));
        }
        run.add(device.below());
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        for (BlockPos pos : run) {
            Build.place(scene, pos, pipe);
        }
        Build.settle(scene);
        scene.idle(Build.SHOW);

        // Its grille toward the viewer, so its back isn't on the pipe. A wrench on its west face turns
        // it to face up.
        BlockState wrong = state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        Build.drop(scene, util, device, wrong);
        ItemStack wrench = new ItemStack(AtmosItems.WRENCH.get());
        Say.over(
                scene,
                WRONG_WAY + Hand.PAUSE + Say.LINE,
                util.vector().blockSurface(device, Direction.WEST),
                "wrench.turns");
        scene.idle(WRONG_WAY);
        Hand.use(scene, util, device, Direction.WEST, wrench);
        Build.change(scene, device, s -> WrenchItem.turned(s, Direction.WEST));
        scene.idle(Say.LINE + 10);

        Build.Lifted lifted = Build.lift(scene, util, device, Direction.UP);
        scene.overlay().showOutline(PonderPalette.GREEN, "back", util.select().position(device.below()), Say.LINE);
        Say.at(scene, util.vector().topOf(device.below()), "grille.back");
        Build.lower(scene, lifted);

        run.add(device);
        return run;
    }
}
