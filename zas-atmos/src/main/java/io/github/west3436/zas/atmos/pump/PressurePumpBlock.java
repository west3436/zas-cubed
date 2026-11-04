package io.github.west3436.zas.atmos.pump;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A pressure pump (CONT-011): on the pipe, with a flange at each end and an 8 px round body. */
public class PressurePumpBlock extends InlineDeviceBlock {
    public static final MapCodec<PressurePumpBlock> CODEC = simpleCodec(PressurePumpBlock::new);

    /** The shape of a pump's model, facing north: its body, the tube through it and its flanges. */
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 4, 3, 12, 12, 13),
            Block.box(6, 6, 0, 10, 10, 16),
            Block.box(5, 5, 0, 11, 11, 1),
            Block.box(5, 5, 15, 11, 11, 16));

    public PressurePumpBlock(Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressurePumpBlockEntity(pos, state);
    }
}
