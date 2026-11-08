package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Gas;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Pipes (CONT-001), their open ends and end caps (CONT-004), dye (CONT-114) and the wrench (CONT-035). */
public final class PipeScenes {
    private PipeScenes() {}

    /** A run with a branch: what joins, an open end leaking and capped, a dye, and a wrench. */
    public static void pipes(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "pipe");

        BlockPos joint = util.grid().at(2, 1, 2);
        BlockPos end = util.grid().at(3, 1, 2);
        BlockPos branch = util.grid().at(2, 1, 1);
        BlockPos branchCap = util.grid().at(2, 1, 0);

        // A run along x, capped at its west end, with a capped branch: its east end is open.
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        Map<BlockPos, BlockState> run = new LinkedHashMap<>();
        run.put(util.grid().at(0, 1, 2), PumpScenes.cap(Direction.EAST));
        run.put(util.grid().at(1, 1, 2), pipe);
        run.put(joint, pipe);
        run.put(end, pipe);
        run.put(branch, pipe);
        run.put(branchCap, PumpScenes.cap(Direction.SOUTH));
        Build.revealAll(scene, util, run, Direction.DOWN);
        Say.at(scene, util.vector().topOf(joint), "pipe.joins");

        int leaking = Say.LINE + 20;
        Say.over(scene, leaking, util.vector().blockSurface(end, Direction.EAST), "pipe.open_end");
        Gas.puff(scene, util, end, Direction.EAST, leaking);
        scene.idle(leaking);

        BlockPos cap = end.east();
        Build.drop(scene, util, cap, PumpScenes.cap(Direction.WEST));
        Say.at(scene, util.vector().topOf(cap), "pipe.cap");

        BlockPos dyed = util.grid().at(1, 1, 2);
        Hand.use(scene, util, dyed, Direction.UP, new ItemStack(Items.BLUE_DYE));
        Build.change(scene, dyed, s -> s.setValue(PipeColor.PROPERTY, PipeColor.BLUE));
        Say.at(scene, util.vector().topOf(dyed), "pipe.dye");

        // A wrench takes the branch off, its cap and then its pipe, while the line is up. Each use's
        // button goes before the next shows, as the two would cover each other.
        ItemStack wrench = new ItemStack(AtmosItems.WRENCH.get());
        int each = Hand.SHOW + 5;
        Say.over(scene, 2 * each + Say.LINE, util.vector().topOf(branch), "wrench.remove");
        for (BlockPos part : new BlockPos[] {branchCap, branch}) {
            Hand.sneakUse(scene, util, part, Direction.UP, wrench);
            Build.remove(scene, util, part);
            scene.idle(each - Hand.PAUSE - Build.SHOW);
        }
        scene.idle(Say.LINE);

        // A part over 2 atm above its room stays on (CONT-091).
        Hand.sneakUse(scene, util, joint, Direction.UP, wrench);
        scene.overlay().showOutline(PonderPalette.RED, "held", util.select().position(joint), Say.LINE);
        Say.at(scene, util.vector().topOf(joint), "wrench.pressure");
        scene.markAsFinished();
    }
}
