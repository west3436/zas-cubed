package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.atmos.device.LoadedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One of the gas turbine's two blocks (CONT-160, CONT-161): the {@linkplain CompressorBlock
 * compressor} or the {@linkplain TurbineBlock turbine}. Both are full blocks, set in the walls of a
 * small sealed room, the combustion chamber, facing the same way, the way the gas goes: in at the
 * back and out at the front.
 *
 * <pre>
 *   intake  [compressor] chamber (1 to 4 open cells) [turbine]  exhaust
 *           -- facing --&gt;                            -- facing --&gt;
 * </pre>
 *
 * The compressor draws air from the cell behind it into the chamber, gas burns there with ZAS³'s own
 * fire, lit by an igniter, and the turbine lets the chamber's gas out to the cell in front of it. The
 * two share a shaft along the line between them, so every cell between them must be open: no full
 * block.
 *
 * <p>Its {@link #SPIN} shows how fast the shaft turns, for its blades' texture.
 */
public abstract class TurbinePartBlock extends HorizontalDirectionalBlock {
    /** The most open cells between the compressor and the turbine. */
    public static final int MAX_CHAMBER = 4;

    /** The fastest {@link #SPIN}. */
    public static final int MAX_SPIN = 3;

    /** How fast its blades turn: 0 still, then 1 to {@link #MAX_SPIN} ({@link TurbineMath#spin}). */
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, MAX_SPIN);

    protected TurbinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPIN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN);
    }

    /** Whether this is the compressor; the turbine otherwise. */
    protected abstract boolean compressor();

    /**
     * Its gas goes the way the player looks, unless the other block of a pair lies the other way along
     * that line and would pair with it turned round: then it turns round.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, look);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (partner(level, pos, state) == null) {
            BlockState turned = state.setValue(FACING, look.getOpposite());
            if (partner(level, pos, turned) != null) {
                return turned;
            }
        }
        return state;
    }

    /**
     * The other block of this one's pair: along its facing for the compressor, against it for the
     * turbine, past one to {@link #MAX_CHAMBER} open cells, with the same facing.
     *
     * @param level the level
     * @param pos this block
     * @param state its state
     * @return the partner's position, or null if it has none or the line isn't loaded in full
     */
    public static @Nullable BlockPos partner(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof TurbinePartBlock part)) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        Direction toward = part.compressor() ? facing : facing.getOpposite();
        for (int k = 1; k <= MAX_CHAMBER + 1; k++) {
            BlockPos at = pos.relative(toward, k);
            BlockState there;
            if (level instanceof ServerLevel server) {
                there = LoadedBlocks.state(server, at);
                if (there == null) {
                    return null; // asking would load it
                }
            } else {
                there = level.getBlockState(at);
            }
            if (there.getBlock() instanceof TurbinePartBlock other) {
                return k > 1 && other.compressor() != part.compressor() && there.getValue(FACING) == facing ? at : null;
            }
            if (there.isCollisionShapeFullBlock(level, at)) {
                return null;
            }
        }
        return null;
    }

    /** The turbine of a pair this block is in, if any: itself, or the compressor's partner. */
    public static @Nullable TurbineBlockEntity turbineOf(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TurbineBlock) {
            return level.getBlockEntity(pos) instanceof TurbineBlockEntity t ? t : null;
        }
        BlockPos at = partner(level, pos, state);
        return at != null && level.getBlockEntity(at) instanceof TurbineBlockEntity t ? t : null;
    }

    /** Opens the pair's window, from either block. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            TurbineBlockEntity turbine = turbineOf(level, pos, state);
            if (turbine == null) {
                return InteractionResult.PASS;
            }
            sp.openMenu(turbine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
