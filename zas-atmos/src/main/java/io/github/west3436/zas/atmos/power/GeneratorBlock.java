package io.github.west3436.zas.atmos.power;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The solid-fuel generator (CONT-061, CONT-066, CONT-067): it burns coal or charcoal and offers the
 * FE it makes through the FE capability, to an area air controller next to it (CONT-062) or any
 * other mod's FE block.
 *
 * <p>Its {@link #LIT} property is true while it burns. The block is in {@code #zas:combustion}, so
 * the framework's change hook sees it being lit as it sees a furnace: a zone trigger under Hardcore
 * (CORE-056, FIRE-024), and a lit fuel block for FIRE-021 and HEAT-007 once those are built.
 */
public class GeneratorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GeneratorBlock> CODEC = simpleCodec(GeneratorBlock::new);

    /** Whether it is burning. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** The frame's outline (models/block/generator.json). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

    public GeneratorBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(s -> s.getValue(LIT) ? 13 : 0));
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AtmosBlockEntities.GENERATOR.get()) {
            return null;
        }
        return (l, p, s, be) -> ((GeneratorBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof GeneratorBlockEntity be && player instanceof ServerPlayer sp) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GeneratorBlockEntity be) {
            Containers.dropItemStack(
                    level, pos.getX(), pos.getY(), pos.getZ(), be.fuel().getStackInSlot(0));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
