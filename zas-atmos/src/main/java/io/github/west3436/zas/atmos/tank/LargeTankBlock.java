package io.github.west3436.zas.atmos.tank;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A large gas tank: a fixed store of {@link io.github.west3436.zas.atmos.pipe.PipeVolumes#LARGE_TANK
 * 10,000 L}, ten canisters' worth, a vessel lying on its side, two blocks long and one high. Its
 * {@link Part#INLET inlet} half holds the gas, in {@link LargeTankBlockEntity}, and is the one a pipe
 * joins: from below, and nowhere else. A connector port below it joins it as it joins a canister. Its
 * {@code facing} is the way from the inlet half to the other, the way the player looked placing it.
 *
 * <p>It is a pipe part, not a portable: it has no valve and no window, a wrench takes it down
 * (CONT-035), and broken any way it drops empty, its gas going into its cell as a pipe's does
 * (CONT-009). It is rated as a canister is, and fails the same way (CONT-101, CONT-102). Like a
 * canister it seals no face, so the room's air passes round it. It starts grey, and a dye colours it
 * as it does a pipe (CONT-114); its item keeps the colour.
 *
 * <p>The two halves are placed and removed together, as a bed's are: each takes the other's state
 * when it changes, so a dye on either colours both, and goes when the other goes.
 */
public class LargeTankBlock extends HorizontalDirectionalBlock implements EntityBlock, PipeConnector {
    public static final MapCodec<LargeTankBlock> CODEC = simpleCodec(LargeTankBlock::new);

    /** Which half of the tank the block is. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** The tint of an undyed tank: vg's grey, from the light shell drawn for the dyes. */
    public static final int GREY = 0xA4A4A4;

    /** The two halves of a tank. */
    public enum Part implements StringRepresentable {
        /** The half that holds the gas and takes the pipe, where the player placed it. */
        INLET,
        /** The other half, past it the way the player looked. */
        END;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The shapes facing north, the end half to the north: the round shell, its legs and the inlet's stub. */
    private static final double[][] INLET_BOXES = {
        {2, 5, 0, 14, 13, 15},
        {3, 4, 0, 13, 14, 15},
        {4, 3, 0, 12, 15, 15},
        {4, 5, 15, 12, 13, 16},
        {3, 0, 11, 5, 4, 13},
        {11, 0, 11, 13, 4, 13},
        {5, 0, 5, 11, 3, 11}
    };

    private static final double[][] END_BOXES = {
        {2, 5, 1, 14, 13, 16},
        {3, 4, 1, 13, 14, 16},
        {4, 3, 1, 12, 15, 16},
        {4, 5, 0, 12, 13, 1},
        {3, 0, 3, 5, 4, 5},
        {11, 0, 3, 13, 4, 5}
    };

    private static final Map<Direction, VoxelShape> INLET_SHAPES = shapes(INLET_BOXES);
    private static final Map<Direction, VoxelShape> END_SHAPES = shapes(END_BOXES);

    public LargeTankBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.INLET)
                .setValue(PipeColor.PROPERTY, PipeColor.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, PipeColor.PROPERTY);
    }

    /**
     * Only the bottom of the inlet half, toward a pipe, a connector port, or another mod's block that
     * offers gas there. One face of one block keeps the tank one node, met once.
     */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == Direction.DOWN && self.getValue(PART) == Part.INLET;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PART) == Part.INLET ? INLET_SHAPES : END_SHAPES).get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** A dye colours the tank, both halves, as it does a pipe (CONT-114). */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return PipeBlock.dye(stack, state, level, pos, player);
    }

    /** Picking a dyed tank gives a tank of its colour. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return PipeBlock.dyed(super.getCloneItemStack(level, pos, state), state);
    }

    /**
     * The colour a tank is drawn in: {@link #GREY} undyed, its dye's otherwise.
     *
     * @param color its colour
     * @return the tint, as 0xRRGGBB
     */
    public static int tint(PipeColor color) {
        return color == PipeColor.NONE ? GREY : color.tint();
    }

    // ---- Placing and removing, as a bed ------------------------------------------------------

    /** The inlet half goes where the player places it, the other half past it the way they look. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos other = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(other)
                || !level.getBlockState(other).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(partner(state, pos), state.setValue(PART, Part.END), Block.UPDATE_ALL);
    }

    /**
     * Each half takes the other's state when it changes, so they are dyed together, and goes when the
     * other goes or no longer lies the same way.
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == toPartner(state)) {
            Part part = state.getValue(PART);
            return neighbor.is(this)
                            && neighbor.getValue(PART) != part
                            && neighbor.getValue(FACING) == state.getValue(FACING)
                    ? neighbor.setValue(PART, part)
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * Breaking the end half in creative, or without the right tool, removes the inlet half with no
     * drop, as for a bed; otherwise the inlet half drops the item when it goes.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && (player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))
                && state.getValue(PART) == Part.END) {
            BlockPos inlet = partner(state, pos);
            BlockState other = level.getBlockState(inlet);
            if (other.is(this) && other.getValue(PART) == Part.INLET) {
                level.setBlock(inlet, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, inlet, Block.getId(other));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.INLET ? new LargeTankBlockEntity(pos, state) : null;
    }

    // ---- Either half stands for the tank -------------------------------------------------------

    /** The way from a half to the other. */
    private static Direction toPartner(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.INLET ? facing : facing.getOpposite();
    }

    /**
     * The other half's position.
     *
     * @param state a tank's state
     * @param pos its position
     * @return where the other half is, or should be
     */
    public static BlockPos partner(BlockState state, BlockPos pos) {
        return pos.relative(toPartner(state));
    }

    /**
     * The inlet half's position, which holds the tank's gas, from either half.
     *
     * @param state a tank's state
     * @param pos its position
     * @return {@code pos}, or the other half's for an end half
     */
    public static BlockPos inlet(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.INLET ? pos : partner(state, pos);
    }

    /**
     * The tank a block is part of, from either half.
     *
     * @param level the level
     * @param pos a position
     * @return the tank's block entity, or null if the block there is no tank's
     */
    @Nullable
    public static LargeTankBlockEntity entity(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LargeTankBlock
                        && level.getBlockEntity(inlet(state, pos)) instanceof LargeTankBlockEntity tank
                ? tank
                : null;
    }

    // ---- Shapes ----------------------------------------------------------------------------------

    /** Boxes drawn facing north, turned to each horizontal facing. */
    private static Map<Direction, VoxelShape> shapes(double[][] boxes) {
        Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, turned(b, facing));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }

    /** A box drawn facing north, turned clockwise seen from above to face another way. */
    private static VoxelShape turned(double[] b, Direction facing) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
