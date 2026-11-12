package io.github.west3436.zas.atmos.gauge;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.analyzer.AnalyzerTarget;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A gauge: a round dial on a short stem, fitted to a face of a pipe, a pipe part, a canister or a
 * large gas tank as a lever is fitted to a face, and reading the gas of what it is on as the analyzer
 * would read a click in the middle of that face ({@link AnalyzerTarget#onFace}). Its needle shows the
 * {@link GaugeReading} its block state names; a plain use turns it to the next, a sneaking use to the
 * one before, and the action bar names it.
 *
 * <p>It needs what it is on: it can only be placed on one, and drops when that goes. It doesn't care
 * which network that is, so a pipe joined, split or cased in a wall reads on. It has no collision, so
 * it seals nothing and the room's air passes it. A comparator reads how far round its needle is. Its
 * work is in {@link GaugeBlockEntity}.
 */
public class GaugeBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GaugeBlock> CODEC = simpleCodec(GaugeBlock::new);

    /** The dial's outline across the face, in pixels: a disc 12 across, of three boxes. */
    private static final int[][] DISC = {{2, 4, 14, 12}, {4, 2, 12, 14}, {3, 3, 13, 13}};

    /** How far the dial stands off the face it is on, in pixels. */
    private static final int DEPTH = 2;

    /** Its outline, by the way the dial faces. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction out : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (int[] d : DISC) {
                shape = Shapes.or(shape, box(out, d[0], d[1], d[2], d[3]));
            }
            SHAPES.put(out, shape.optimize());
        }
    }

    public GaugeBlock(Properties properties) {
        super(properties.noCollission());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(GaugeReading.PROPERTY, GaugeReading.PRESSURE));
    }

    /** A box of the dial facing {@code out}, from (u0, v0) to (u1, v1) across the face. */
    private static VoxelShape box(Direction out, int u0, int v0, int u1, int v1) {
        return switch (out) {
            case UP -> Block.box(u0, 0, v0, u1, DEPTH, v1);
            case DOWN -> Block.box(u0, 16 - DEPTH, v0, u1, 16, v1);
            case NORTH -> Block.box(u0, v0, 16 - DEPTH, u1, v1, 16);
            case SOUTH -> Block.box(u0, v0, 0, u1, v1, DEPTH);
            case EAST -> Block.box(0, v0, u0, DEPTH, v1, u1);
            case WEST -> Block.box(16 - DEPTH, v0, u0, 16, v1, u1);
        };
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, GaugeReading.PROPERTY);
    }

    /** The way a gauge's dial faces: away from what it is on. */
    public static Direction out(BlockState state) {
        return getConnectedDirection(state);
    }

    /** The block a gauge is on. */
    public static BlockPos host(BlockState state, BlockPos pos) {
        return pos.relative(out(state).getOpposite());
    }

    /**
     * What a gauge reads: what it is on, as the analyzer reads the middle of the face it is fitted to.
     *
     * @param level the level
     * @param pos the gauge
     * @param state its state
     * @return a vessel or a network, or null if what it is on is neither, or gone
     */
    public static @Nullable AnalyzerTarget reads(BlockGetter level, BlockPos pos, BlockState state) {
        return AnalyzerTarget.onFace(level, host(state, pos), out(state));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return reads(level, pos, state) != null;
    }

    /**
     * On the face clicked, if what is behind it can carry a gauge. On a wall the dial faces out; on a
     * floor or a ceiling its top is away from the player.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction out = context.getClickedFace();
        BlockState state = defaultBlockState()
                .setValue(
                        FACE,
                        switch (out) {
                            case UP -> AttachFace.FLOOR;
                            case DOWN -> AttachFace.CEILING;
                            default -> AttachFace.WALL;
                        })
                .setValue(FACING, out.getAxis().isHorizontal() ? out : context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(out(state));
    }

    /** Turns the dial to the next reading, or with a sneak to the one before, and names it. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            GaugeReading was = state.getValue(GaugeReading.PROPERTY);
            GaugeReading now = player.isSecondaryUseActive() ? was.previous() : was.next();
            server.setBlock(pos, state.setValue(GaugeReading.PROPERTY, now), Block.UPDATE_ALL);
            if (server.getBlockEntity(pos) instanceof GaugeBlockEntity gauge) {
                gauge.read(server);
            }
            server.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, 0.8f);
            player.displayClientMessage(now.label(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** How far round the needle is, 0 at rest to 15 at the top. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GaugeBlockEntity gauge ? gauge.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GaugeBlockEntity(pos, state);
    }
}
