package io.github.west3436.zas.atmos.teg;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A circulator (CONT-157): one of the TEG's two sides, set in a pipe line, gas in at the back and out
 * at the front, the way its {@link #FACING} points, as a pump's. It needs no power: it passes the gas
 * its two sides' pressures drive through it. Beside a {@linkplain TegBlock TEG core}, on one of the
 * core's two sides, it is the core's stream on that side. Its {@link #ON} shows gas passing.
 *
 * <p>Using it opens the window of the core it serves; one on its own has no window.
 */
public class CirculatorBlock extends InlineDeviceBlock {
    public static final MapCodec<CirculatorBlock> CODEC = simpleCodec(CirculatorBlock::new);

    /** Its model's shape, facing north: a drum on a base, with a flange at each end. */
    public static final VoxelShape SHAPE =
            Shapes.or(Block.box(1, 0, 2, 15, 14, 14), Block.box(5, 5, 0, 11, 11, 2), Block.box(5, 5, 14, 11, 11, 16));

    public CirculatorBlock(Properties properties) {
        super(properties, SHAPE);
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
