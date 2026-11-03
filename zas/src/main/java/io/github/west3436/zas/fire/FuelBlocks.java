package io.github.west3436.zas.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the furnace and campfire mixins do (M5, M6; FIRE-027, FIRE-020): a lit fuel block whose
 * cell fails the fire oxygen threshold (FIRE-030) pauses or goes out. Server thread.
 */
public final class FuelBlocks {
    private FuelBlocks() {}

    /**
     * Whether a furnace, smoker or blast furnace pauses this tick (FIRE-027): it burns or has fuel
     * to start burning, and the cell in front of it fails the threshold. Paused, it skips its tick,
     * so its burn time, fuel and cooking progress stay as they are until O₂ returns.
     *
     * @param burning whether it is burning ({@code litTime > 0})
     * @param hasFuel whether its fuel slot holds anything
     */
    public static boolean furnacePaused(Level level, BlockPos pos, BlockState state, boolean burning, boolean hasFuel) {
        if (!burning && !hasFuel) {
            return false;
        }
        return !FireAir.sustains(level, Burners.pointOf(pos, state));
    }

    /**
     * Puts a lit campfire out if its cell fails the threshold (FIRE-020, M6), as a shovel does:
     * the extinguishing sound, its items dropped, and {@code lit} false.
     *
     * @return whether it went out
     */
    public static boolean campfireOut(Level level, BlockPos pos, BlockState state) {
        if (FireAir.sustains(level, Burners.pointOf(pos, state))) {
            return false;
        }
        level.levelEvent(null, 1009, pos, 0);
        CampfireBlock.dowse(null, level, pos, state);
        level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 11);
        return true;
    }
}
