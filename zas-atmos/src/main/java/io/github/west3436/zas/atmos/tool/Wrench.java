package io.github.west3436.zas.atmos.tool;

import io.github.west3436.ss13.api.tool.Wrenchable;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.pipe.EncasedPipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeHost;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * How SS13 Framework's wrench turns and removes this mod's atmos parts (CONT-035), as their {@link
 * Wrenchable} capability: the blocks pipes join, which are pipes, caps, valves, connector ports,
 * vents, scrubbers, pumps, filters, mixers and large gas tanks. A canister is a portable, mined into
 * an item that keeps its gas (CONT-017), so it isn't one. A large tank is one part: a click on either
 * half acts on the tank, whose gas its lower half holds. It has no facing, so nothing to turn.
 *
 * <ul>
 *   <li><b>Use</b> turns a part a quarter turn about the face clicked, clockwise as seen from that
 *       face. A part pointing along that axis turns around instead, and one that only faces
 *       sideways turns about the vertical. Only its facing changes, so a mirrored filter or mixer
 *       stays mirrored, its side port where its own rule puts it (D-77).
 *   <li><b>Sneak-use</b> removes it as mining would: it drops its item, except in creative, and what
 *       it held goes into its cell (CONT-009). It refuses while the part holds more than 2 atm over
 *       the ambient, and says so (CONT-091). From a pipe in a casing it takes the pipe and leaves the
 *       casing, a wall still whole ({@link EncasedPipeBlock#takeOut}), and the pipe's gas goes into
 *       the cell beside the face clicked.
 * </ul>
 *
 * <p>The wrench acts before the block's own use, so a part's window or a valve's switch doesn't get
 * the click.
 */
public final class Wrench {
    /** How far over the ambient a part's pressure may be for it to come off, in kPa: 2 atm (CONT-091). */
    public static final double LOCK_KPA = 2 * StandardAir.PRESSURE;

    private Wrench() {}

    /** Registers the capability on every block pipes join, but the canister. */
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Wrenchable.BLOCK,
                (level, pos, state, be, context) ->
                        isPart(state) && isPart(level.getBlockState(part(state, pos))) ? PART : null,
                AtmosBlocks.BLOCKS.getEntries().stream()
                        .map(DeferredHolder::get)
                        .filter(block -> isPart(block.defaultBlockState()))
                        .toArray(Block[]::new));
    }

    /** An atmos part, as the wrench works on it. Half a tank, as a command can leave, isn't one. */
    private static final Wrenchable PART = new Wrenchable() {
        @Override
        public BlockState turned(UseOnContext use) {
            return Wrench.turned(use.getLevel().getBlockState(use.getClickedPos()), use.getClickedFace());
        }

        /** Rebuilds the networks around it, so its nodes join the pipes on their new faces. */
        @Override
        public void afterTurn(ServerLevel level, BlockPos pos, UseOnContext use) {
            if (level.getBlockEntity(pos) instanceof PipeHost host) {
                host.reconnect();
            }
            AtmosSounds.play(level, pos, AtmosSounds.WRENCH);
        }

        @Override
        public Optional<Component> refusal(ServerLevel level, BlockPos pos, UseOnContext use) {
            return Wrench.refusal(level, part(level.getBlockState(pos), pos), use.getClickedFace());
        }

        @Override
        public void remove(ServerLevel level, BlockPos pos, UseOnContext use) {
            takeOff(
                    level,
                    part(level.getBlockState(pos), pos),
                    use.getPlayer(),
                    use.getItemInHand(),
                    use.getClickedFace());
        }
    };

    /** Whether a block is an atmos part the wrench works on: one that pipes join, other than a canister. */
    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof PipeConnector && !(state.getBlock() instanceof CanisterBlock);
    }

    /** The block that stands for the part clicked: a tank's lower half, or the block itself. */
    private static BlockPos part(BlockState state, BlockPos pos) {
        return state.getBlock() instanceof LargeTankBlock ? LargeTankBlock.lower(state, pos) : pos;
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
            BlockState out = state.setValue(
                    BlockStateProperties.FACING, turned(state.getValue(BlockStateProperties.FACING), face));
            return out.getBlock() instanceof SidePortBlock ? SidePortBlock.settled(out) : out;
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
     * Why a part may not come off now: it holds more than {@link #LOCK_KPA} over the ambient
     * (CONT-091). A pipe in a casing is measured against the cell beside the face it comes out of.
     *
     * @param level the level
     * @param pos the part
     * @param face the face clicked, or null for none in particular
     * @return what to tell the player, or empty if it may come off
     */
    public static Optional<Component> refusal(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        boolean cased = level.getBlockState(pos).getBlock() instanceof EncasedPipeBlock;
        return overAmbient(level, pos, cased ? face : null) > LOCK_KPA
                ? Optional.of(Component.translatable("item.zas_atmos.wrench.pressurized"))
                : Optional.empty();
    }

    /**
     * Removes a part as a player with the wrench would, unless it holds more than {@link #LOCK_KPA}
     * over the ambient: as SS13 Framework's wrench does through {@link Wrenchable}, for a caller with
     * no click, such as a test.
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
        Optional<Component> refusal = refusal(level, pos, face);
        if (refusal.isPresent()) {
            return refusal;
        }
        if (player instanceof ServerPlayer server
                && CommonHooks.fireBlockBreak(
                                level, server.gameMode.getGameModeForPlayer(), server, pos, level.getBlockState(pos))
                        .isCanceled()) {
            return Optional.empty();
        }
        takeOff(level, pos, player, tool, face);
        return Optional.empty();
    }

    /**
     * Takes a part off, which may come off and whose break nobody refused: it drops its item, except
     * for a player in creative, and its gas goes into its cell as a mined part's does (CONT-009). A
     * pipe in a casing comes out and leaves the casing.
     */
    private static void takeOff(
            ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack tool, @Nullable Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EncasedPipeBlock pipe) {
            pipe.takeOut(level, pos, state, player, face);
        } else {
            Wrenchable.removeAsMined(level, pos, player, tool);
        }
        AtmosSounds.play(level, pos, AtmosSounds.WRENCH);
    }
}
