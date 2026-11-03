package io.github.west3436.zas.hotspot;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.scheduler.FireIntent;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies the hotspot phase's world writes on the server thread, with the result (architecture §5,
 * FIRE-018). Each is checked against the block as it is now, not against the chunk's block
 * generation: a gas fire is placed only in air and removed only where one is, so an intent the world
 * has moved past does nothing. A placement the world refused shows up in the next atmos tick as a
 * missing block, and the engine drops that hotspot.
 */
public final class FireIntents {
    private FireIntents() {}

    /**
     * Places and removes gas fires as the intents ask, in their order, in loaded chunks only.
     *
     * @param loaded whether a chunk ({@link net.minecraft.world.level.ChunkPos#toLong}) is loaded, as
     *     the atmosphere sees it
     * @return how many blocks changed
     */
    public static int apply(ServerLevel level, List<FireIntent> intents, LongPredicate loaded) {
        if (intents.isEmpty()) {
            return 0;
        }
        BlockState gasFire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
        int changed = 0;
        for (FireIntent i : intents) {
            if (!loaded.test(SectionKeys.chunkOfBlockKey(i.block()))) {
                continue;
            }
            BlockPos pos = BlockPos.of(i.block());
            BlockState here = level.getBlockState(pos);
            boolean done = switch (i.kind()) {
                case PLACE -> here.isAir() && level.setBlock(pos, gasFire, Block.UPDATE_ALL);
                case REMOVE ->
                    here.is(HotspotBlocks.GAS_FIRE.get())
                            && level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            };
            if (done) {
                changed++;
            }
        }
        return changed;
    }
}
