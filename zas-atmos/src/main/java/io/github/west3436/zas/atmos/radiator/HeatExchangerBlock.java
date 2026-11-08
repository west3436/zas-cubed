package io.github.west3436.zas.atmos.radiator;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A heat exchanger (CONT-147): a box half a block deep against one face of its block, with its pipe
 * on the opposite face. Two facing each other from neighbouring blocks stand back to back, 2 px
 * apart, and share one temperature between their networks (HEAT-022). It needs no power. Its coil
 * and pipe show its gas's temperature ({@link #HEAT}, CONT-148).
 */
public class HeatExchangerBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<HeatExchangerBlock> CODEC = simpleCodec(HeatExchangerBlock::new);

    /** The way its box faces: toward its partner. Its pipe joins the opposite face. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** The band of its gas's temperature, which colours its coil and pipe ({@link HeatExchangerTint}). */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, HeatExchangerTint.BANDS - 1);

    /** By facing: the box against that face, and the pipe out to the other. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SHAPES.put(d, Shapes.or(box(d, 1, 2, 7, 15, 14, 15), box(d, 5, 5, 0, 11, 11, 7)));
        }
    }

    public HeatExchangerBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HEAT, HeatExchangerTint.COPPER));
    }

    /**
     * A box given for a block facing south (the model's own way), turned to face another way. The
     * boxes are symmetric across the facing's axis, so which way a quarter turn goes doesn't matter.
     */
    private static VoxelShape box(Direction d, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (d) {
            case SOUTH -> Block.box(x0, y0, z0, x1, y1, z1);
            case NORTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case UP -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case DOWN -> Block.box(x0, 16 - z1, y0, x1, 16 - z0, y1);
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEAT);
    }

    /** A pipe joins it only on the face opposite its box. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == self.getValue(FACING).getOpposite();
    }

    /**
     * Placed against a heat exchanger's box, it faces back, so the two make a pair. Placed against
     * anything else, its pipe faces the block clicked, so clicking a pipe's end joins it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState against =
                context.getLevel().getBlockState(context.getClickedPos().relative(clicked.getOpposite()));
        boolean partner = against.getBlock() instanceof HeatExchangerBlock && against.getValue(FACING) == clicked;
        return defaultBlockState().setValue(FACING, partner ? clicked.getOpposite() : clicked);
    }

    /**
     * Whether two blocks are a pair: heat exchangers facing each other from neighbouring blocks.
     *
     * @param self one's state
     * @param other the state of the block its box faces
     * @return true if they make a pair
     */
    public static boolean paired(BlockState self, BlockState other) {
        return self.getBlock() instanceof HeatExchangerBlock
                && other.getBlock() instanceof HeatExchangerBlock
                && other.getValue(FACING) == self.getValue(FACING).getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatExchangerBlockEntity(pos, state);
    }
}
