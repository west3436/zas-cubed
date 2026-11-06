package io.github.west3436.zas.example;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block that opens and closes when used, whose collision shape comes from its block entity's data
 * and not its block state, so it says {@code dynamicShape()} (GEO-043). Without a seal provider, ZAS³
 * derives its gas cells from this shape. {@link ExampleContent#AIRLOCK} is this block with a provider,
 * and the provider's answer is used instead (API-023).
 */
public final class ShutterBlock extends Block implements EntityBlock {
    public ShutterBlock(Properties properties) {
        super(properties.dynamicShape().noOcclusion());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShutterBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ShutterBlockEntity shutter && shutter.isOpen()
                ? Shapes.empty()
                : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ShutterBlockEntity shutter) {
            shutter.setOpen(!shutter.isOpen());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
