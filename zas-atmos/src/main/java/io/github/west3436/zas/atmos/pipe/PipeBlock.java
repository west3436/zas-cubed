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
 * A pipe segment (CONT-001, CONT-007): a 4 px tube with an arm toward each connected neighbour, and a
 * flared mouth on each face it leaks from ({@link PipeMouths}, CONT-004). It connects on every face to
 * any neighbour that offers a connection back ({@link PipeConnector}) or provides a gas handler
 * ({@link GasHandlers}, API-025), and it can be dyed (CONT-114).
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
    /** The 8 px lip of a mouth, at the block's edge. The rest of a mouth fits in its arm's box. */
    private static final Map<Direction, VoxelShape> LIPS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(4, 4, 0, 12, 12, 1),
            Direction.SOUTH, Block.box(4, 4, 15, 12, 12, 16),
            Direction.WEST, Block.box(0, 4, 4, 1, 12, 12),
            Direction.EAST, Block.box(15, 4, 4, 16, 12, 12),
            Direction.DOWN, Block.box(4, 0, 4, 12, 1, 12),
            Direction.UP, Block.box(4, 15, 4, 12, 16, 12)));
    /** By the bits of the faces it connects on, as {@link PipeMouths} counts them. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            int mouths = PipeMouths.of(mask);
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                int bit = 1 << d.ordinal();
                if ((mask & bit) != 0) {
                    shape = Shapes.or(shape, ARMS.get(d));
                }
                if ((mouths & bit) != 0) {
                    shape = Shapes.or(shape, ARMS.get(d), LIPS.get(d));
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public PipeBlock(Properties properties) {
        this(properties.noOcclusion(), null);
    }

    /**
     * For a pipe in a casing ({@link EncasedPipeBlock}), which keeps its casing's properties: a wall
     * hides what is behind it, and a bare pipe never does.
     *
     * @param properties the block's properties, taken as they are
     * @param cased unused: it tells this constructor from the public one
     */
    protected PipeBlock(Properties properties, @Nullable Void cased) {
        super(properties);
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

    /**
     * Whether a pipe state has an open end, which leaks into its cell (CONT-004): a bare pipe with
     * fewer than two connections. A pipe in a casing has none ({@link EncasedPipeBlock}).
     *
     * @param state a state of this block
     * @return true if it leaks
     */
    public boolean leaks(BlockState state) {
        return connections(state) < 2;
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return true;
    }

    /**
     * A state with an arm toward each neighbour that joins it now, as a newly placed pipe has.
     *
     * @param state a state of this block
     * @param level the level
     * @param pos where the pipe is, or is about to be
     * @return the state with its arms set
     */
    public BlockState connect(BlockState state, Level level, BlockPos pos) {
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
        ItemInteractionResult dyed = dye(stack, state, level, pos, player);
        return dyed != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? dyed
                : EncasedPipeBlock.encase(stack, state, level, pos, player, hit);
    }

    /**
     * A pipe keeps its block entity when its block becomes another pipe block, as when a casing goes
     * on it: it is the same pipe, with its place in its network and its share of the gas.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!(newState.getBlock() instanceof PipeBlock)) {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
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
