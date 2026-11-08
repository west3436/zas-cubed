package io.github.west3436.zas.ponder.scenes;

import io.github.west3436.zas.atmos.portable.PortableBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.ponder.scene.Build;
import io.github.west3436.zas.ponder.scene.Gas;
import io.github.west3436.zas.ponder.scene.Hand;
import io.github.west3436.zas.ponder.scene.Say;
import io.github.west3436.zas.ponder.scene.Stage;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The portable scrubber and the portable pump (CONT-018): blocks that run on a battery and work on the
 * room they stand in, or a tank in their slot, with no area air controller (D-73).
 */
public final class PortableScenes {
    private PortableScenes() {}

    /** A portable scrubber takes CO₂ and toxic gases from its room. */
    public static void scrubber(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "portable_scrubber");
        BlockPos scrubber = switchOn(scene, util, AtmosBlocks.PORTABLE_SCRUBBER.get());

        int working = Say.LINE + 20;
        draw(scene, scrubber, working);
        Say.over(scene, working, util.vector().topOf(scrubber), "portable_scrubber.scrubs");
        scene.idle(working);

        rest(scene, util, scrubber);
    }

    /** A portable pump fills its room from its store, or siphons the room into it. */
    public static void pump(SceneBuilder scene, SceneBuildingUtil util) {
        Stage.open(scene, "portable_pump");
        BlockPos pump = switchOn(scene, util, AtmosBlocks.PORTABLE_PUMP.get());

        int working = Say.LINE + 20;
        Gas.puff(scene, util, pump, Direction.UP, working);
        Say.over(scene, working, util.vector().topOf(pump), "portable_pump.release");
        scene.idle(working);
        draw(scene, pump, working);
        Say.over(scene, working, util.vector().topOf(pump), "portable_pump.siphon");
        scene.idle(working);

        rest(scene, util, pump);
    }

    /** Drops the portable in, and shows a battery going in and it switching on. */
    private static BlockPos switchOn(SceneBuilder scene, SceneBuildingUtil util, Block block) {
        BlockPos at = util.grid().at(2, 1, 2);
        Build.drop(scene, util, at, block.defaultBlockState().setValue(PortableBlock.FACING, Direction.NORTH));
        Hand.use(scene, util, at, Direction.UP, new ItemStack(AtmosItems.BATTERY.get()));
        Build.change(scene, at, s -> s.setValue(PortableBlock.LIT, true));
        Say.at(scene, util.vector().topOf(at), "portable.battery");
        return at;
    }

    /**
     * Puffs of gas drawn in from all round a block, low down, so they don't look like the puffs a
     * release sends up from its top. A stand-in until the shared {@code Gas.draw} reaches this branch.
     */
    private static void draw(SceneBuilder scene, BlockPos pos, int cycles) {
        Vec3 centre = Vec3.atBottomCenterOf(pos).add(0, 0.2, 0);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 out = Vec3.atLowerCornerOf(side.getNormal());
            scene.effects()
                    .emitParticles(
                            centre.add(out.scale(1.1)),
                            scene.effects().simpleParticleEmitter(ParticleTypes.POOF, out.scale(-0.08)),
                            0.4f,
                            cycles);
        }
    }

    /** What both say last: a held tank, and no controller. */
    private static void rest(SceneBuilder scene, SceneBuildingUtil util, BlockPos at) {
        Hand.use(scene, util, at, Direction.UP, new ItemStack(AtmosItems.EMPTY_TANK.get()));
        Say.at(scene, util.vector().topOf(at), "portable.tank");
        Say.at(scene, util.vector().topOf(at), "portable.no_controller");
        scene.markAsFinished();
    }
}
