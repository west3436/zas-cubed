package io.github.west3436.zas.atmos.device;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * Reads blocks only where their chunk is loaded in full, and never loads one or keeps one loaded
 * (architecture §11.2). A read through the level ({@code Level.getBlockState}, {@code getBlockEntity}
 * or {@code getCapability}) asks the chunk source for the chunk, which on the server thread adds a
 * ticket to it whenever the read misses its four-chunk cache. A step that read a chunk that way once
 * an atmos tick kept it loaded after every player had gone, which the S-10 bench found. These read
 * the chunk the game already holds, or nothing.
 *
 * <p>Server thread only: off it, the chunk source hands out no chunk, and these read nothing.
 */
public final class LoadedBlocks {
    private LoadedBlocks() {}

    /**
     * The chunk at a position if it is loaded in full, as {@code ServerLevel.isLoaded} says.
     *
     * @return the chunk, or null if it isn't loaded in full or the position is outside the world
     */
    public static @Nullable LevelChunk chunk(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return null;
        }
        return level.getChunkSource()
                .getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** The block at a position, or null if its chunk isn't loaded in full. */
    public static @Nullable BlockState state(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    /**
     * The block entity at a position, made now if the block has one and it is missing, as the level
     * would; null if there is none or its chunk isn't loaded in full.
     */
    public static @Nullable BlockEntity entity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    /**
     * A block capability, with the block and block entity read here and handed to the lookup, so the
     * lookup reads neither through the level.
     *
     * @return the capability, or null if there is none or the chunk isn't loaded in full
     */
    public static <T, C> @Nullable T capability(
            ServerLevel level, BlockCapability<T, C> capability, BlockPos pos, C context) {
        LevelChunk chunk = chunk(level, pos);
        if (chunk == null) {
            return null;
        }
        BlockState state = chunk.getBlockState(pos);
        BlockEntity entity =
                state.hasBlockEntity() ? chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) : null;
        return level.getCapability(capability, pos, state, entity, context);
    }

    /**
     * Whether the chunk at a position ticks its block entities now, as vanilla decides for its own: it
     * is in block-ticking range of a player or a ticking ticket, at block-ticking status. A chunk
     * loaded in full only because a neighbour is, at the edge of what is loaded, doesn't. Unlike
     * vanilla, it doesn't also wait for the chunk's entities to load, which takes the entity storage's
     * own time after the chunk does, and has nothing to do with the devices.
     */
    public static boolean ticking(ServerLevel level, BlockPos pos) {
        long chunkPos = ChunkPos.asLong(pos);
        if (!level.shouldTickBlocksAt(chunkPos)) {
            return false;
        }
        LevelChunk chunk = chunk(level, pos);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING);
    }
}
