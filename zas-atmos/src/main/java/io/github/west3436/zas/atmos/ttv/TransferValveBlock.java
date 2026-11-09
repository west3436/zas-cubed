package io.github.west3436.zas.atmos.ttv;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tank transfer valve standing on the ground (CONT-103, D-86), which its {@linkplain
 * TransferValveItem item} places when sneak-used on a block. Its block entity holds its tanks, valve
 * and timer, and it breaks at once by hand, dropping the item with all three.
 *
 * <p>Using a tank on it fits the tank in the first free place. Using it with an empty hand opens its
 * {@linkplain TransferValveMenu screen}, the same as the item's, where starting the timer primes it
 * where it stands (CONT-104). Redstone does nothing to it.
 *
 * <p>{@link #FACING} is the way its timer faces: toward whoever placed it. {@link #TANKS} and {@link
 * #OPEN} pick its model, and follow what its block entity holds.
 */
public class TransferValveBlock extends BaseEntityBlock {
    public static final MapCodec<TransferValveBlock> CODEC = simpleCodec(TransferValveBlock::new);

    /** The way its timer faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How many tanks it holds, 0 to 2. A lone tank is drawn in the first place, as on the item. */
    public static final IntegerProperty TANKS = IntegerProperty.create("tanks", 0, 2);

    /** Whether its valve is open, which turns its lever. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** Its shapes with its timer to the north, by tank count, as its models are drawn: 7 px high. */
    private static final VoxelShape[] NORTH = {body(), Shapes.or(body(), tank(0)), Shapes.or(body(), tank(0), tank(12))
    };

    /** Its shapes by tank count, then by {@link Direction#get2DDataValue}: south, west, north, east. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[3][4];

    static {
        for (int tanks = 0; tanks < 3; tanks++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                SHAPES[tanks][facing.get2DDataValue()] = turned(NORTH[tanks], facing);
            }
        }
    }

    public TransferValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TANKS, 0)
                .setValue(OPEN, false));
    }

    private static VoxelShape body() {
        return box(4, 0, 4, 12, 7, 13);
    }

    private static VoxelShape tank(int x) {
        return box(x, 0, 1, x + 4, 4, 14);
    }

    /** A shape drawn facing north, turned clockwise from above until it faces the way given. */
    private static VoxelShape turned(VoxelShape north, Direction facing) {
        int turns = (facing.get2DDataValue() + 2) % 4;
        VoxelShape shape = north;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] out = {Shapes.empty()};
            shape.forAllBoxes(
                    (x1, y1, z1, x2, y2, z2) -> out[0] = Shapes.or(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            shape = out[0];
        }
        return shape.optimize();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TANKS, OPEN);
    }

    /** It faces whoever places it, and shows the tanks and valve of the item it was placed from. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return showing(
                defaultBlockState()
                        .setValue(FACING, context.getHorizontalDirection().getOpposite()),
                context.getItemInHand());
    }

    /** A state showing what a TTV stack holds: how many tanks, and whether its valve is open. */
    static BlockState showing(BlockState state, ItemStack ttv) {
        if (!state.hasProperty(TANKS)) {
            return state;
        }
        return state.setValue(TANKS, Math.min(2, TransferValveItem.tankCount(ttv)))
                .setValue(OPEN, TransferValveItem.valve(ttv).isOpen());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(TANKS)][state.getValue(FACING).get2DDataValue()];
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * A tank in hand goes on in the first free place, with the sound it makes going onto the item. On a
     * TTV with both tanks or its valve open, it stays in hand and nothing happens, not even the tank's
     * own use. Anything else goes on to {@link #useWithoutItem}, then to the item's own use. The client
     * judges by the block's state, as it doesn't hold the tanks.
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
        if (!(stack.getItem() instanceof GasTankItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(TANKS) >= 2 || state.getValue(OPEN) || stack.getCount() != 1) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof TransferValveBlockEntity ttv
                && ttv.fit(stack)) {
            stack.shrink(1);
            level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand opens its screen. A hand holding anything else passes, to that item's own use. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof TransferValveBlockEntity ttv) {
            TransferValveMenu.open(server, ttv);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Its drops, which its loot table makes the item with its tanks, valve and timer. A TTV that one of
     * its own tanks blows up drops nothing: its gas is already in the air, and it is gone.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TransferValveBlockEntity ttv
                && ttv.goingOff()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransferValveBlockEntity(pos, state);
    }
}
