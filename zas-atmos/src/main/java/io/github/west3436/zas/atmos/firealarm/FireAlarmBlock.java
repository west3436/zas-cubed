package io.github.west3436.zas.atmos.firealarm;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fire alarm (CONT-044): a small box on a wall, facing out of it into the room it watches. It
 * needs a sturdy wall behind it and drops when the wall goes, as the area air controller does. Its
 * {@link FireAlarmStatus} picks its model. The work is in {@link FireAlarmBlockEntity}.
 */
public class FireAlarmBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FireAlarmBlock> CODEC = simpleCodec(FireAlarmBlock::new);

    /** What the alarm shows. */
    public static final EnumProperty<FireAlarmStatus> STATUS = EnumProperty.create("status", FireAlarmStatus.class);

    /** The box's shape for each facing: on the wall behind it (models/block/fire_alarm.json). */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(3, 6, 14, 13, 14, 16),
            Direction.SOUTH, Block.box(3, 6, 0, 13, 14, 2),
            Direction.EAST, Block.box(0, 6, 3, 2, 14, 13),
            Direction.WEST, Block.box(14, 6, 3, 16, 14, 13));

    public FireAlarmBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, FireAlarmStatus.UNPOWERED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The wall behind an alarm. */
    public static BlockPos wall(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos wall = wall(state, pos);
        return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction looking : context.getNearestLookingDirections()) {
            if (looking.getAxis().isHorizontal()) {
                BlockState placed = state.setValue(FACING, looking.getOpposite());
                if (placed.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return placed;
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FireAlarmBlockEntity(pos, state);
    }

    /**
     * On a client, tells {@link FireAlarmVoice#LOCAL} each game tick that it is alarming. An alarm
     * that resets, is broken or unloads stops telling it, which is all it takes to silence it. The
     * work on a server has its own schedule.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != AtmosBlockEntities.FIRE_ALARM.get()) {
            return null;
        }
        return (l, p, s, be) -> report(s, p, FireAlarmVoice.LOCAL);
    }

    /** Tells a voice about an alarm at a position, if its state says it is alarming. */
    public static void report(BlockState state, BlockPos pos, FireAlarmVoice voice) {
        if (state.getValue(STATUS) == FireAlarmStatus.ALARM) {
            voice.report(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }
}
