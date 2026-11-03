package io.github.west3436.zas.atmos.canister;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gas canister (CONT-017): 1,000 L, rated at 9,120 kPa. It stands on a connector port to join
 * the port's network (CONT-083), releases gas through its valve (CONT-084), and keeps its gas when
 * mined. A dye paints it.
 */
public class CanisterBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<CanisterBlock> CODEC = simpleCodec(CanisterBlock::new);

    /** The way the valve faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the valve is open. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public CanisterBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CanisterColor.PROPERTY, CanisterColor.YELLOW)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CanisterColor.PROPERTY, OPEN);
    }

    /** Only down, onto a connector port: a canister never joins a pipe directly. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == Direction.DOWN && neighbour.getBlock() instanceof ConnectorPortBlock;
    }

    /** The valve faces the player who places it. */
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** A dye paints the canister. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.getItem() instanceof GasTankItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CanisterBlockEntity canister) {
                double moved = canister.fillTank(stack);
                if (moved > 0) {
                    ItemStack kind = GasTankItem.ofItsKind(stack);
                    if (kind != stack) {
                        player.setItemInHand(hand, kind);
                    }
                }
                player.displayClientMessage(
                        moved > 0
                                ? Component.translatable(
                                        "item.zas_atmos.gas_tank.filled",
                                        String.format(Locale.ROOT, "%.2f", moved),
                                        String.format(
                                                Locale.ROOT,
                                                "%.0f",
                                                GasTankItem.contents(stack).pressure()))
                                : Component.translatable("item.zas_atmos.gas_tank.not_filled"),
                        true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        CanisterColor color = CanisterColor.of(dye.getDyeColor());
        if (state.getValue(CanisterColor.PROPERTY) == color) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(CanisterColor.PROPERTY, color), Block.UPDATE_ALL);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens the canister's GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CanisterBlockEntity canister) {
            player.openMenu(canister);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A player about to break the canister. Mining it into an item, it keeps its gas for the item;
     * broken in creative, its gas is destroyed with it. The game removes the block before it makes
     * the drops, so this is decided first.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof CanisterBlockEntity canister) {
            if (player.isCreative()) {
                canister.voidContents();
            } else if (state.canHarvestBlock(level, pos, player)
                    && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                canister.keepContents();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops made before the block goes, as by an explosion or {@code destroyBlock}, keep the gas too. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CanisterBlockEntity canister
                && drops.stream().anyMatch(s -> s.is(asItem()))) {
            canister.keepContents();
        }
        return drops;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CanisterBlockEntity(pos, state);
    }
}
