package io.github.west3436.zas.atmos.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pipe segment (CONT-001, CONT-007). The skeleton registers it so the registries and the creative
 * tab have something in them; connections and networks come with #47.
 */
public class PipeBlock extends Block {
    /** The pipe's core, the 4 px tube's middle (models/block/pipe_core.json). */
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);

    public PipeBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CORE;
    }
}
