package io.github.west3436.zas.atmos.pipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe in a casing (#487): a full block that is still the pipe. The casing is a block of its own,
 * such as a station wall, and the pipe passes through it, so a pipe goes from one room to the next
 * with no gap in the wall between them.
 *
 * <ul>
 *   <li><b>For the air</b> it is the casing: a full cube, so its gas geometry is a full block's and
 *       the change from the casing to this block, or back, changes no geometry. No zone is rebuilt
 *       and no gas crosses.
 *   <li><b>For the pipes</b> it is a pipe: the same block entity, a 70 L node that joins every
 *       neighbour a pipe joins, carries its dye, bursts as a pipe does (CONT-108) and offers the gas
 *       handler a pipe offers (API-021). It has no open end: with fewer than two connections its
 *       casing closes it, where a bare pipe would leak (CONT-004).
 *   <li><b>For the player</b> it is the casing with a pipe's end in each face the pipe shows on: its
 *       hardness, blast resistance, tool and sound are the casing's, given at registration.
 * </ul>
 *
 * <p>It is made in either order, and has no item of its own: a pipe used on the casing's block goes
 * into it ({@link #insert}), and the casing's item used on a bare pipe goes round it ({@link
 * #encase}), the pipe keeping its block entity, so its network and its gas never notice. Mined, it
 * drops both. A wrench takes the pipe back out and leaves the casing ({@link #takeOut}), and so does
 * a burst ({@link #burst}).
 */
public class EncasedPipeBlock extends PipeBlock {
    public static final MapCodec<EncasedPipeBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    propertiesCodec(),
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("shell").forGetter(EncasedPipeBlock::shell))
            .apply(i, (properties, shell) -> new EncasedPipeBlock(properties, () -> shell)));

    private final Supplier<? extends Block> shell;

    /**
     * @param properties the casing's own properties, which this block keeps
     * @param shell the casing as a block of its own, such as the station wall
     */
    public EncasedPipeBlock(Properties properties, Supplier<? extends Block> shell) {
        super(properties, null);
        this.shell = shell;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** The casing as a block of its own: what the pipe was put into, and what is left when it goes. */
    public Block shell() {
        return shell.get();
    }

    /** The pipe-in-casing block of a casing's own block, or null if no pipe goes into that block. */
    public static @Nullable EncasedPipeBlock in(Block shell) {
        for (DeferredBlock<EncasedPipeBlock> cased : AtmosBlocks.encasedPipes()) {
            if (cased.get().shell() == shell) {
                return cased.get();
            }
        }
        return null;
    }

    /** The pipe-in-casing block a casing's item makes of a pipe, or null if the item is no casing. */
    public static @Nullable EncasedPipeBlock of(Item casing) {
        for (DeferredBlock<EncasedPipeBlock> cased : AtmosBlocks.encasedPipes()) {
            if (cased.get().shell().asItem() == casing) {
                return cased.get();
            }
        }
        return null;
    }

    /** Its casing closes it, so it never leaks, however few its connections. */
    @Override
    public boolean leaks(BlockState state) {
        return false;
    }

    /** A full cube in every state, which is what makes it gas-tight (GEO-041). */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Picking it gives its casing: it looks like one, and the pipe's item would place a bare pipe. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(shell());
    }

    /**
     * A pipe item used on a casing's block: the pipe goes into it, with the item's dye, and joins what
     * a pipe placed there would. The block was full and stays full, so nothing changes for the air.
     *
     * @param context the use, of a pipe item
     * @return {@link InteractionResult#PASS} if the block clicked takes no pipe, or the player may not
     *     change it
     */
    static InteractionResult insert(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        EncasedPipeBlock cased = in(level.getBlockState(pos).getBlock());
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (cased == null || player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState state = cased.defaultBlockState().setValue(PipeColor.PROPERTY, PipeItem.color(stack));
            cased.place(level, pos, cased.connect(state, level, pos), player);
            stack.consume(1, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A casing's item used on a bare pipe: the casing goes round it. The pipe keeps its dye, its
     * connections and its block entity, so its network is the same one, with the same gas. The cell
     * the pipe stood in becomes a full block, as when any block is placed. It refuses where something
     * stands in the way, as placing a block does.
     *
     * @return {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION} if the item is no casing,
     *     the pipe has one already, or the player may not change it
     */
    static ItemInteractionResult encase(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        EncasedPipeBlock cased = state.getBlock() instanceof EncasedPipeBlock ? null : of(stack.getItem());
        if (cased == null || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockState next = copy(state, cased.defaultBlockState());
        if (!level.isUnobstructed(next, pos, CollisionContext.of(player))) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            cased.place(level, pos, next, player);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sets the block, with the sound and game event of a block being placed. */
    private void place(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        // The block's capability is asked of its state, which is new, though its block entity may not be.
        level.invalidateCapabilities(pos);
        SoundType sound = state.getSoundType(level, pos, player);
        level.playSound(
                null,
                pos,
                sound.getPlaceSound(),
                SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2,
                sound.getPitch() * 0.8f);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
    }

    /** A state of another pipe block with a pipe state's dye and connections. */
    private static BlockState copy(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            to = copy(from, to, property);
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /**
     * Takes the pipe out and leaves the casing, as a wrench does (CONT-035): the pipe's item drops, in
     * its dye, unless the player is in creative, and its share of the gas is released as a removed
     * pipe's is (CONT-009), into the cell beside the face it is taken out of. The block stays full, so
     * nothing changes for the air but that gas arriving.
     *
     * @param level the level
     * @param pos the block
     * @param state its state
     * @param player who takes it out, or null
     * @param face the face it is taken out of, or null for none in particular
     */
    public void takeOut(
            ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player, @Nullable Direction face) {
        if (face != null && level.getBlockEntity(pos) instanceof PipeNodeEntity node) {
            node.releaseToward(face);
        }
        level.setBlock(pos, shell().defaultBlockState(), Block.UPDATE_ALL);
        if (player == null || !player.isCreative()) {
            ItemStack pipe = PipeItem.of(AtmosItems.PIPE.get(), state.getValue(PipeColor.PROPERTY), 1);
            if (face != null) {
                Block.popResourceFromFace(level, pos, face, pipe);
            } else {
                Block.popResource(level, pos, pipe);
            }
        }
    }

    /**
     * Destroys the pipe and leaves the casing, as a burst does (CONT-108): nothing drops, and the
     * caller, which has taken the network's gas, releases it at this block, where it goes into the
     * cell beside it.
     *
     * @param level the level
     * @param pos the block
     * @param state its state
     */
    void burst(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.setBlock(pos, shell().defaultBlockState(), Block.UPDATE_ALL);
    }
}
