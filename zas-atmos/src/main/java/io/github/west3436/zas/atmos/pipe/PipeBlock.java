package io.github.west3436.zas.atmos.pipe;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe segment (CONT-001, CONT-007): a 4 px tube with an arm toward each connected neighbour. It
 * connects on every face to any neighbour that offers a connection back ({@link PipeConnector}) or
 * provides a gas handler ({@link GasHandlers}, API-025), and it can be dyed (CONT-114).
 */
public class PipeBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);

    /** Whether the pipe connects on each face. */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN));

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 6),
            Direction.SOUTH, Block.box(5, 5, 10, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 6, 11, 11),
            Direction.EAST, Block.box(10, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 6, 11),
            Direction.UP, Block.box(5, 10, 5, 11, 16, 11)));
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(d));
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public PipeBlock(Properties properties) {
        super(properties.noOcclusion());
        BlockState state = stateDefinition.any().setValue(PipeColor.PROPERTY, PipeColor.NONE);
        for (BooleanProperty p : CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeColor.PROPERTY);
        CONNECTIONS.values().forEach(builder::add);
    }

    /** The number of faces a pipe state connects on. */
    public static int connections(BlockState state) {
        if (!(state.getBlock() instanceof PipeBlock)) {
            return 0;
        }
        int n = 0;
        for (BooleanProperty p : CONNECTIONS.values()) {
            if (state.getValue(p)) {
                n++;
            }
        }
        return n;
    }

    /**
     * The way a pipe state's open end looks out: away from its one connection.
     *
     * @return the direction; null for a pipe with no connection, open every way, or with more than one,
     *     which has no open end
     */
    @Nullable
    public static Direction openFace(BlockState state) {
        if (!(state.getBlock() instanceof PipeBlock)) {
            return null;
        }
        Direction only = null;
        for (Direction d : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(d))) {
                if (only != null) {
                    return null;
                }
                only = d;
            }
        }
        return only == null ? null : only.getOpposite();
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return true;
    }

    private BlockState connect(BlockState state, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(
                    CONNECTIONS.get(d),
                    PipeConnector.connected(state, d, level.getBlockState(pos.relative(d)))
                            || GasHandlers.connects(level, pos, state, d));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction face,
            BlockState neighbour,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos) {
        return state.setValue(
                CONNECTIONS.get(face),
                PipeConnector.connected(state, face, neighbour)
                        || GasHandlers.connects(level, neighbourPos, state, face, neighbour));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return dye(stack, state, level, pos, player);
    }

    /** Dyes a pipe or cap with the dye in hand (CONT-114), using up one dye outside creative. */
    static ItemInteractionResult dye(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        PipeColor color = PipeColor.of(dye.getDyeColor());
        if (state.getValue(PipeColor.PROPERTY) == color) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PipeColor.PROPERTY, color), Block.UPDATE_ALL);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Picking a dyed pipe gives a pipe of its colour (CONT-114). */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return dyed(super.getCloneItemStack(level, pos, state), state);
    }

    /** A stack of a pipe or cap item, given the colour of a block state. */
    static ItemStack dyed(ItemStack stack, BlockState state) {
        if (stack.getItem() instanceof PipeItem item) {
            return PipeItem.of(item, state.getValue(PipeColor.PROPERTY), stack.getCount());
        }
        return stack;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }
}
