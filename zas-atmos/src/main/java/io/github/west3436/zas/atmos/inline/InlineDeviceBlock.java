package io.github.west3436.zas.atmos.inline;

import io.github.west3436.zas.atmos.pipe.GasHandlers;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A device set in a pipe line, with a pipe port on each of two or more faces: a pump, filter or
 * mixer (architecture §11.5). Its {@link #FACING} is the way its gas goes. Pipes join it only on its
 * {@linkplain #portFaces port faces}, each of which leads to a network of its own. It isn't a full
 * block, so it stands in its room's cell and gas passes around it.
 *
 * <p>Its models are drawn facing north, the way the gas goes, and its blockstate turns them: y 90,
 * 180 and 270 for east, south and west, x 270 for up and x 90 for down.
 */
public abstract class InlineDeviceBlock extends BaseEntityBlock implements PipeConnector, PortedBlock {
    /** The way its gas goes: out of the front, in at the back. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Whether it shows running: on and powered. */
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /**
     * @param properties the block's properties
     * @param north its shape facing north, which it turns for the other five facings
     */
    protected InlineDeviceBlock(Properties properties, VoxelShape north) {
        super(properties.noOcclusion());
        for (Direction d : Direction.values()) {
            shapes.put(d, turned(north, d));
        }
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    /** A shape drawn facing north, turned as the blockstate turns the model. */
    public static VoxelShape turned(VoxelShape north, Direction facing) {
        VoxelShape[] out = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = turned(x0, y0, z0, facing);
            double[] b = turned(x1, y1, z1, facing);
            out[0] = Shapes.or(
                    out[0],
                    Shapes.box(
                            Math.min(a[0], b[0]),
                            Math.min(a[1], b[1]),
                            Math.min(a[2], b[2]),
                            Math.max(a[0], b[0]),
                            Math.max(a[1], b[1]),
                            Math.max(a[2], b[2])));
        });
        return out[0];
    }

    /** A point in a block, drawn facing north, turned. */
    private static double[] turned(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case NORTH -> new double[] {x, y, z};
            case EAST -> new double[] {1 - z, y, x};
            case SOUTH -> new double[] {1 - x, y, 1 - z};
            case WEST -> new double[] {z, y, 1 - x};
            case UP -> new double[] {x, 1 - z, y};
            case DOWN -> new double[] {x, z, 1 - y};
        };
    }

    /**
     * The faces it has a pipe port on, in the order of its block entity's ports. The default is two:
     * port 0 at the back, where gas comes in, and port 1 at the front, where it goes out.
     *
     * @param state the block's state
     * @return the faces, one per port
     */
    @Override
    public List<Direction> portFaces(BlockState state) {
        Direction front = state.getValue(FACING);
        return List.of(front.getOpposite(), front);
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return portFaces(self).contains(face);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    /**
     * Its gas goes the way the player looks. Nothing can turn it afterwards, so if that leaves its
     * ports clear of any pipe and another way joins one, it takes that way, the nearest to where the
     * player looks: looking down at a run on the floor, it lines up with the run.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return facingFor(defaultBlockState(), context);
    }

    /** A state with the facing {@link #getStateForPlacement} gives it, from what its ports would join. */
    protected final BlockState facingFor(BlockState base, BlockPlaceContext context) {
        BlockState looking = base.setValue(FACING, context.getNearestLookingDirection());
        if (joinsPipe(looking, context)) {
            return looking;
        }
        for (Direction d : context.getNearestLookingDirections()) {
            BlockState state = base.setValue(FACING, d);
            if (joinsPipe(state, context)) {
                return state;
            }
        }
        return looking;
    }

    private boolean joinsPipe(BlockState state, BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        for (Direction face : portFaces(state)) {
            if (PipeConnector.connected(state, face, context.getLevel().getBlockState(pos.relative(face)))
                    || GasHandlers.connects(context.getLevel(), pos, state, face)) {
                return true;
            }
        }
        return false;
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
