package io.github.west3436.zas.atmos.electrolyzer;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.PortedBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The water electrolyzer (CONT-055, CONT-056): two glass gas columns on a water tank, standing on the
 * floor with its front to the player who placed it. Facing its front, the O₂ outlet is on the left and
 * the H₂ outlet on the right, and gas pipes join only those. Fluid pipes join only the water inlet on
 * its back, where it offers the fluid capability. A water bucket fills it from any side.
 *
 * <p>Its models are drawn facing north, and its blockstate turns them y 90, 180 and 270 for east,
 * south and west. Its water shows through the window in its front, one level per bucket it has begun
 * to fill.
 */
public class ElectrolyzerBlock extends BaseEntityBlock implements PipeConnector, PortedBlock {
    public static final MapCodec<ElectrolyzerBlock> CODEC = simpleCodec(ElectrolyzerBlock::new);

    /** The way its front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether it shows making gas: its lamp lit and its columns bubbling. */
    public static final BooleanProperty ON = InlineDeviceBlock.ON;

    /** Its water level, 0 to 4: one for each bucket the tank has begun to fill. */
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 4);

    /** Its shape facing north: the tank, the two columns with their caps, the outlets and the inlet. */
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 6, 15),
            Block.box(1, 6, 5, 7, 16, 11),
            Block.box(9, 6, 5, 15, 16, 11),
            Block.box(0, 5, 5, 1, 11, 11),
            Block.box(15, 5, 5, 16, 11, 11),
            Block.box(6, 6, 11, 10, 13, 15),
            Block.box(5, 5, 15, 11, 11, 16));

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public ElectrolyzerBlock(Properties properties) {
        super(properties.noOcclusion());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes.put(d, InlineDeviceBlock.turned(SHAPE, d));
        }
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ON, false)
                .setValue(WATER, 0));
    }

    /** The face its O₂ leaves by: on the left, facing its front. */
    public static Direction oxygenFace(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** The face its H₂ leaves by: on the right, facing its front. */
    public static Direction hydrogenFace(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    /** The face of its water inlet: its back. */
    public static Direction inletFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectrolyzerBlockEntity(pos, state);
    }

    /** Its two outlets: port 0 for O₂ and port 1 for H₂. */
    @Override
    public List<Direction> portFaces(BlockState state) {
        return List.of(oxygenFace(state), hydrogenFace(state));
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return portFaces(self).contains(face);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, WATER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Whether an item holds water to pour in: a water bucket, or any container of water. */
    private static boolean holdsWater(ItemStack stack) {
        return FluidUtil.getFluidContained(stack)
                .filter(fluid -> fluid.is(Fluids.WATER))
                .isPresent();
    }

    /**
     * Pours water from what the player holds into its tank (CONT-056), as much as the container gives
     * at once and the tank has room for: a bucket only into room for a whole bucket. Anything else
     * opens its GUI.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!holdsWater(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity e) {
            FluidUtil.interactWithFluidHandler(player, hand, e.tank());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens its GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider device) {
            player.openMenu(device);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
