package io.github.west3436.zas.hotspot;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.scheduler.FireIntent;
import io.github.west3436.zas.fire.FireAir;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Applies the hotspot phase's world writes on the server thread, with the result (architecture §5,
 * FIRE-018, FIRE-033, FIRE-034). Each is checked against the block as it is now, not against the
 * chunk's block generation: a gas fire is placed only in air and removed only where one is, and a
 * vanilla fire is lit only under vanilla's limits, so an intent the world has moved past does
 * nothing. A placement the world refused shows up in the next atmos tick as a missing block, and the
 * engine drops that hotspot.
 */
public final class FireIntents {
    private FireIntents() {}

    /**
     * Puts out every fire block in a box, now (API-057): {@code zas:gas_fire} and vanilla fire, in
     * loaded chunks. The box is assumed small.
     *
     * @param removed told the position ({@link BlockPos#asLong}) of each block removed
     * @return how many blocks were removed
     */
    public static int extinguish(ServerLevel level, BlockPos from, BlockPos to, LongConsumer removed) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState here = level.getBlockState(p);
            if ((here.is(HotspotBlocks.GAS_FIRE.get()) || here.getBlock() instanceof BaseFireBlock)
                    && level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                removed.accept(p.asLong());
                n++;
            }
        }
        return n;
    }

    /**
     * Places and removes gas fires and lights vanilla fires as the intents ask, in their order, in
     * loaded chunks only. An intent in a chunk that isn't at full status does nothing: reading the
     * block through the level would pull that chunk back up (GEO-034).
     *
     * @param loaded whether a chunk ({@link net.minecraft.world.level.ChunkPos#toLong}) is loaded, as
     *     the atmosphere sees it
     * @param blocked whether a fire may not be placed at a position ({@link BlockPos#asLong}): one
     *     put out since the tick's batch closed (API-057)
     * @return how many blocks changed
     */
    public static int apply(ServerLevel level, List<FireIntent> intents, LongPredicate loaded, LongPredicate blocked) {
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
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            BlockState here = chunk.getBlockState(pos);
            boolean done = switch (i.kind()) {
                case PLACE ->
                    here.isAir() && !blocked.test(i.block()) && level.setBlock(pos, gasFire, Block.UPDATE_ALL);
                case REMOVE ->
                    here.is(HotspotBlocks.GAS_FIRE.get())
                            && level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                case LIGHT -> !blocked.test(i.block()) && light(level, pos, here);
            };
            if (done) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Lights a vanilla fire as flint and steel would, in air or in place of a gas fire, whose cell may
     * be the only open one beside the block that caught (FIRE-033, FIRE-034). It keeps the limits on
     * placing fire: the {@code doFireTick} game rule, which may have changed since the batch closed,
     * the block it stands on or burns on, rain, and the O₂ every fire needs (FIRE-030, as M3 checks
     * it for flint and steel). The fire is soul fire on soul sand, as flint and steel would make it.
     */
    private static boolean light(ServerLevel level, BlockPos pos, BlockState here) {
        if (!(here.isAir() || here.is(HotspotBlocks.GAS_FIRE.get()))
                || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                || level.isRainingAt(pos)) {
            return false;
        }
        BlockState fire = BaseFireBlock.getState(level, pos);
        return fire.canSurvive(level, pos)
                && FireAir.sustains(level, pos)
                && level.setBlock(pos, fire, Block.UPDATE_ALL);
    }
}
