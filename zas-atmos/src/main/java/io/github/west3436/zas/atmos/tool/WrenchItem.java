package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.pipe.EncasedPipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeHost;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The wrench turns and removes atmos parts (CONT-035): the blocks pipes join, which are pipes, caps,
 * valves, connector ports, vents, scrubbers, pumps, filters, mixers and large gas tanks. A canister is
 * a portable, mined into an item that keeps its gas (CONT-017), so it isn't one. A large tank is one
 * part: a click on either half acts on the tank, whose gas its lower half holds. It has no facing, so
 * nothing to turn.
 *
 * <ul>
 *   <li><b>Use</b> turns a part a quarter turn about the face clicked, clockwise as seen from that
 *       face. A part pointing along that axis turns around instead, and one that only faces
 *       sideways turns about the vertical. Only its facing changes, so a mirrored filter or mixer
 *       stays mirrored, its side port where its own rule puts it (D-77).
 *   <li><b>Sneak-use</b> removes it as mining would: it drops its item, except in creative, and what
 *       it held goes into its cell (CONT-009). It refuses while the part holds more than 2 atm over
 *       the ambient, and says by how much (CONT-091). From a pipe in a casing it takes the pipe and
 *       leaves the casing, a wall still whole ({@link EncasedPipeBlock#takeOut}), and the pipe's gas
 *       goes into the cell beside the face clicked.
 * </ul>
 *
 * <p>It acts before the block's own use, so a part's GUI or a valve's switch doesn't get the click.
 */
public class WrenchItem extends Item {
    /** How far over the ambient a part's pressure may be for it to come off, in kPa: 2 atm (CONT-091). */
    public static final double LOCK_KPA = 2 * StandardAir.PRESSURE;

    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Its stack checks first that the player may build here, as it does for placing a block. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!isPart(state)) {
            return InteractionResult.PASS;
        }
        if (state.getBlock() instanceof LargeTankBlock) {
            pos = LargeTankBlock.lower(state, pos);
            state = level.getBlockState(pos);
            if (!isPart(state)) {
                return InteractionResult.PASS; // half a tank, as a command can leave
            }
        }
        if (player != null && player.isSecondaryUseActive()) {
            if (level instanceof ServerLevel server) {
                remove(server, pos, player, stack, context.getClickedFace())
                        .ifPresent(refusal -> player.displayClientMessage(refusal, true));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState turned = turned(state, context.getClickedFace());
        if (turned == state) {
            return InteractionResult.FAIL; // a pipe or port, or a valve clicked on its end: nothing turns
        }
        if (level instanceof ServerLevel server) {
            turn(server, pos, turned);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether a block is an atmos part the wrench works on: one that pipes join, other than a canister. */
    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof PipeConnector && !(state.getBlock() instanceof CanisterBlock);
    }

    /**
     * A part's state turned by a click on one of its faces: its facing, or a valve's axis, a quarter
     * turn about that face's axis, clockwise as seen from the face; or, if it points along that axis,
     * turned around. A part that only faces sideways, such as the electrolyzer, turns about the
     * vertical: by that rule when clicked on its top or bottom, and clockwise as seen from above when
     * clicked on a side. Nothing else changes.
     *
     * @param state the part's state
     * @param face the face clicked
     * @return the turned state, or {@code state} itself if it has nothing to turn that way
     */
    public static BlockState turned(BlockState state, Direction face) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(
                    BlockStateProperties.FACING, turned(state.getValue(BlockStateProperties.FACING), face));
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction way = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return state.setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    face.getAxis() == Direction.Axis.Y ? turned(way, face) : way.getClockWise());
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction along = Direction.fromAxisAndDirection(
                    state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
            return state.setValue(BlockStateProperties.AXIS, turned(along, face).getAxis());
        }
        return state;
    }

    private static Direction turned(Direction way, Direction face) {
        Direction.Axis axis = face.getAxis();
        if (way.getAxis() == axis) {
            return way.getOpposite();
        }
        // Seen from a face on the positive side of its axis, the game's clockwise turn is clockwise.
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? way.getClockWise(axis)
                : way.getCounterClockWise(axis);
    }

    /**
     * Turns a part to a new state, and rebuilds the networks around it, so its nodes join the pipes
     * on their new faces. Each keeps its share of the gas, so a device's buffers go with it. A
     * capability a part offers on some faces turns with them: the game is told its capabilities
     * changed, as a state change alone doesn't.
     */
    static void turn(ServerLevel level, BlockPos pos, BlockState turned) {
        level.setBlock(pos, turned, Block.UPDATE_ALL);
        level.invalidateCapabilities(pos);
        if (level.getBlockEntity(pos) instanceof PipeHost host) {
            host.reconnect();
        }
        AtmosSounds.play(level, pos, AtmosSounds.WRENCH);
    }

    /**
     * How far a part's pressure is over the ambient, in kPa (CONT-091): the highest pressure of the
     * networks it is in, less the pressure where its gas would go if it came off. For a part standing
     * in a zone that is the zone's; for a full block, the cell beside it that its gas would reach.
     */
    public static double overAmbient(ServerLevel level, BlockPos pos) {
        return overAmbient(level, pos, null);
    }

    /**
     * {@link #overAmbient(ServerLevel, BlockPos)} for a part taken off from one face: a full block's
     * gas then goes into the cell beside that face, if there is one ({@link
     * PipeNodeEntity#releaseToward}).
     *
     * @param level the level
     * @param pos the part
     * @param face the face it is taken off from, or null for none in particular
     * @return the pressure over the ambient, in kPa; 0 for a block that holds no gas
     */
    public static double overAmbient(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        if (!(level.getBlockEntity(pos) instanceof PipeHost host)) {
            return 0;
        }
        Vec3 to = Vec3.atCenterOf(pos);
        return host.highestPressure()
                - Atmospheres.at(level, face == null ? to : to.relative(face, 0.49))
                        .pressure();
    }

    /**
     * Removes a part as a player mining it would, unless it holds more than {@link #LOCK_KPA} over the
     * ambient. It drops its item, except for a player in creative, and its gas goes into its cell as
     * a mined part's does (CONT-009). A mod that guards the block can refuse the break.
     *
     * @param level the level
     * @param pos the part
     * @param player who removes it, or null
     * @param tool the wrench, as the drops' tool
     * @return what to tell the player if it refused for the pressure; empty otherwise
     */
    public static Optional<Component> remove(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack tool) {
        return remove(level, pos, player, tool, null);
    }

    /**
     * {@link #remove(ServerLevel, BlockPos, Player, ItemStack)} from one face. A pipe in a casing
     * comes out of that face and leaves its casing, a full block still: only the pipe's item drops,
     * and its gas goes into the cell beside the face.
     *
     * @param level the level
     * @param pos the part
     * @param player who removes it, or null
     * @param tool the wrench, as the drops' tool
     * @param face the face clicked, or null for none in particular
     * @return what to tell the player if it refused for the pressure; empty otherwise
     */
    public static Optional<Component> remove(
            ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack tool, @Nullable Direction face) {
        BlockState state = level.getBlockState(pos);
        boolean cased = state.getBlock() instanceof EncasedPipeBlock;
        if (overAmbient(level, pos, cased ? face : null) > LOCK_KPA) {
            return Optional.of(Component.translatable("item.zas_atmos.wrench.pressurized"));
        }
        if (player instanceof ServerPlayer server
                && CommonHooks.fireBlockBreak(level, server.gameMode.getGameModeForPlayer(), server, pos, state)
                        .isCanceled()) {
            return Optional.empty();
        }
        if (state.getBlock() instanceof EncasedPipeBlock pipe) {
            pipe.takeOut(level, pos, state, player, face);
        } else {
            if (player == null || !player.isCreative()) {
                Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, tool);
            }
            level.destroyBlock(pos, false, player);
        }
        AtmosSounds.play(level, pos, AtmosSounds.WRENCH);
        return Optional.empty();
    }
}
