package io.github.west3436.zas.airflow;

import io.github.west3436.zas.engine.scheduler.PressureBreak;
import io.github.west3436.zas.geometry.Geometries;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Breaks the fragile blocks a pressure difference broke, on the server thread, with the result
 * (FLOW-007). Each break is vanilla's, as if with no tool: {@link ServerLevel#destroyBlock} plays the
 * block's sound and particles and drops what its loot table gives without a tool, which for glass is
 * nothing, and leaves a waterlogged block's water. To the atmosphere it is a block change like any
 * other, so the zones meet through the usual path.
 *
 * <p>A break is checked against the world as it is now, not against the chunk's block generation: a
 * block no longer fragile doesn't break. Nor does one whose chunk or a neighbouring block's chunk isn't
 * at full status, or has saved zones that haven't come back yet (API-010). Breaking it would
 * pull that chunk up through the neighbour updates (GEO-034), or judge it on a guess. The next atmos
 * tick asks again if the pressure still says so; nothing is kept between ticks.
 */
public final class PressureBreaks {
    private PressureBreaks() {}

    /**
     * Breaks each block in the list that may break, in its order.
     *
     * @param settled whether a chunk ({@link ChunkPos#toLong}) is loaded with its zones known, as the
     *     atmosphere sees it
     * @return how many blocks broke
     */
    public static int apply(ServerLevel level, List<PressureBreak> breaks, LongPredicate settled) {
        int broken = 0;
        for (PressureBreak b : breaks) {
            BlockPos pos = BlockPos.of(b.block());
            int x = pos.getX();
            int z = pos.getZ();
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null
                    || !settled.test(ChunkPos.asLong(x >> 4, z >> 4))
                    || !ready(level, settled, x - 1, z)
                    || !ready(level, settled, x + 1, z)
                    || !ready(level, settled, x, z - 1)
                    || !ready(level, settled, x, z + 1)) {
                continue;
            }
            BlockState here = chunk.getBlockState(pos);
            if (here.is(Geometries.PRESSURE_FRAGILE) && level.destroyBlock(pos, true)) {
                broken++;
            }
        }
        return broken;
    }

    private static boolean ready(ServerLevel level, LongPredicate settled, int x, int z) {
        return settled.test(ChunkPos.asLong(x >> 4, z >> 4))
                && level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }
}
