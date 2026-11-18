package io.github.west3436.zas.atmos.teg;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The thermoelectric generator's core (CONT-158, CONT-159): vg's TEG, the middle of its three parts.
 * A {@linkplain CirculatorBlock circulator} on each of its two sides, left and right of its front,
 * brings it a stream of gas. It takes heat from the hotter stream, gives the colder one what it
 * doesn't turn into FE, and offers its FE to the blocks around it.
 *
 * <p>Its front shows {@link #OUTPUT} lit segments, more as it makes more, and it gives off light with
 * them. It is a full block, so it is a wall to the air.
 */
public class TegBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TegBlock> CODEC = simpleCodec(TegBlock::new);

    /** The most lit segments. */
    public static final int MAX_OUTPUT = 5;

    /** Its lit segments: 0 for none made, up to {@link #MAX_OUTPUT} ({@link TegMath#segments}). */
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, MAX_OUTPUT);

    public TegBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(OUTPUT) == 0 ? 0 : 3 + 2 * s.getValue(OUTPUT)));
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OUTPUT, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUTPUT);
    }

    /** Its two sides, where its circulators go: left of its front, then right. */
    public static Direction[] sides(BlockState state) {
        Direction front = state.getValue(FACING);
        return new Direction[] {front.getCounterClockWise(), front.getClockWise()};
    }

    /**
     * Its front faces the player, unless that leaves both its sides without a circulator that could
     * serve it while its front and back have one: then it turns a quarter, so the circulators are on
     * its sides.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState facing = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
        if (circulators(context.getLevel(), context.getClickedPos(), facing) == 0) {
            BlockState turned = facing.setValue(FACING, facing.getValue(FACING).getClockWise());
            if (circulators(context.getLevel(), context.getClickedPos(), turned) > 0) {
                return turned;
            }
        }
        return facing;
    }

    private static int circulators(BlockGetter level, BlockPos pos, BlockState core) {
        int n = 0;
        for (Direction side : sides(core)) {
            if (CirculatorBlock.servesToward(level.getBlockState(pos.relative(side)), side.getOpposite())) {
                n++;
            }
        }
        return n;
    }

    /**
     * The core a circulator serves: a core beside it that has the circulator on one of its sides, with
     * neither of the circulator's ports facing it. The first such, in the order of {@link
     * Direction#values()}.
     *
     * @param level the level
     * @param pos the circulator's block
     * @param state its state
     * @return the core's block entity, or null if it serves none
     */
    public static @Nullable TegBlockEntity coreOf(Level level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!CirculatorBlock.servesToward(state, d)) {
                continue;
            }
            BlockPos at = pos.relative(d);
            if (level instanceof ServerLevel server && LoadedBlocks.state(server, at) == null) {
                continue; // not loaded in full: asking would load it
            }
            BlockState core = level.getBlockState(at);
            if (core.getBlock() instanceof TegBlock) {
                for (Direction side : sides(core)) {
                    if (side == d.getOpposite() && level.getBlockEntity(at) instanceof TegBlockEntity teg) {
                        return teg;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TegBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AtmosBlockEntities.TEG.get()) {
            return null;
        }
        return (l, p, s, be) -> ((TegBlockEntity) be).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TegBlockEntity be && player instanceof ServerPlayer sp) {
            sp.openMenu(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
