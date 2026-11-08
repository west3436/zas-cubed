package io.github.west3436.zas.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * What the player does in a scene, shown as Ponder's mouse button over the block, with the item in
 * hand beside it. Each waits a moment, so the scene then shows what the use does, usually with
 * {@link Build#change}.
 */
public final class Hand {
    /** How long the button shows, in ticks. */
    public static final int SHOW = 40;

    /** The ticks between the button showing and the use taking effect. */
    public static final int PAUSE = 10;

    private Hand() {}

    /**
     * A use (right-click) on a block's face.
     *
     * @param scene the scene
     * @param util the scene's helpers
     * @param pos the block
     * @param face the face clicked
     * @param held what's in hand, or {@link ItemStack#EMPTY} for an empty hand
     */
    public static void use(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction face, ItemStack held) {
        input(scene, util, pos, face, held);
        scene.idle(PAUSE);
    }

    /** A use while sneaking, such as a wrench taking a part off (CONT-035). */
    public static void sneakUse(
            SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction face, ItemStack held) {
        input(scene, util, pos, face, held).whileSneaking();
        scene.idle(PAUSE);
    }

    private static InputElementBuilder input(
            SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction face, ItemStack held) {
        InputElementBuilder input = scene.overlay()
                .showControls(util.vector().blockSurface(pos, face), Pointing.DOWN, SHOW)
                .rightClick();
        return held.isEmpty() ? input : input.withItem(held);
    }
}
