package io.github.west3436.zas.atmos.teg;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A circulator (CONT-157): one of the TEG's two sides, set in a pipe line, gas in at the back and out
 * at the front, the way its {@link #FACING} points, as a pump's. It needs no power: it passes the gas
 * its two sides' pressures drive through it. Beside a {@linkplain TegBlock TEG core}, on one of the
 * core's two sides, it is the core's stream on that side. Its {@link #ON} shows gas passing, and its
 * {@link #CORE} the side its core is on, where its model shows a pipe joining the two.
 *
 * <p>Using it opens the window of the core it serves; one on its own has no window.
 */
public class CirculatorBlock extends InlineDeviceBlock {
    public static final MapCodec<CirculatorBlock> CODEC = simpleCodec(CirculatorBlock::new);

    /** Its model's shape, facing north: a drum on a base, with a flange at each end. */
    public static final VoxelShape SHAPE =
            Shapes.or(Block.box(1, 0, 2, 15, 14, 14), Block.box(5, 5, 0, 11, 11, 2), Block.box(5, 5, 14, 11, 11, 16));

    /** The side the core it serves is on, by the blocks' states alone ({@link TegBlock#coreSide}). */
    public static final EnumProperty<CoreSide> CORE = EnumProperty.create("core", CoreSide.class);

    public CirculatorBlock(Properties properties) {
        super(properties, SHAPE);
        registerDefaultState(defaultBlockState().setValue(CORE, CoreSide.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CORE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return withCore(state, context.getLevel(), context.getClickedPos());
    }

    /** A neighbour beside it changed: the core it serves may have come, gone or turned. */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbour,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? withCore(state, level, pos) : state;
    }

    /** Set or turned in place, as by a wrench: its side may have changed with its facing. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel server) {
            refreshCore(server, pos, state);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(CORE, state.getValue(CORE).rotate(rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(CORE, state.getValue(CORE).mirror(mirror));
    }

    /** Its state with {@link #CORE} worked out from the blocks around it. */
    static BlockState withCore(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(CORE, CoreSide.of(TegBlock.coreSide(level, pos, state)));
    }

    /**
     * Puts its {@link #CORE} right if it is wrong, as after a turn or in a world saved before it had
     * one. Server side; it asks only loaded blocks.
     *
     * @param level the level
     * @param pos its block
     * @param state its state
     */
    static void refreshCore(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CirculatorBlock)) {
            return;
        }
        BlockState right = withCore(state, level, pos);
        if (right != state) {
            level.setBlock(pos, right, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CirculatorBlockEntity(pos, state);
    }

    /** Opens its core's window, if it serves one. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            TegBlockEntity core = TegBlock.coreOf(level, pos, state);
            if (core == null) {
                return InteractionResult.PASS;
            }
            sp.openMenu(core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Whether a circulator in this state can serve a core on this side of it: the core is on neither
     * port face.
     *
     * @param state the circulator's state
     * @param toCore the way from the circulator to the core
     */
    public static boolean servesToward(BlockState state, Direction toCore) {
        return state.getBlock() instanceof CirculatorBlock
                && state.getValue(FACING).getAxis() != toCore.getAxis();
    }
}
