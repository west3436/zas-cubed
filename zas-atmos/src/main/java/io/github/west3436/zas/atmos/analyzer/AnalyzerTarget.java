package io.github.west3436.zas.atmos.analyzer;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync.Kind;
import io.github.west3436.zas.atmos.filter.FilterBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.mixer.MixerBlock;
import io.github.west3436.zas.atmos.pipe.GasHandlers;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.tank.GasVessels;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What an analyzer was used on (CONT-034), and how the server reads it, through the public API for
 * the air (API-010) and from the content mod's own gas for the rest (CONT-095):
 *
 * <ul>
 *   <li><b>Nothing:</b> the player's eyes, as the HUD reads them, so it follows the player: its
 *       zone, or the open air.
 *   <li><b>A pipe, or a device on a network:</b> that network's gas. A device with several ports, a
 *       pump, filter or mixer, reads the port on the face clicked, or else the one nearest the point
 *       clicked.
 *   <li><b>A canister or a large gas tank:</b> its own gas, its share of the network it's on. Either
 *       half of a tank reads the tank.
 *   <li><b>Another mod's tank or machine:</b> a block that provides the gas-handler capability
 *       (API-021), and isn't a pipe part of ours, is read through it under the block's name: what it
 *       holds on the face clicked, or else for no face in particular, in the volume it gives ({@link
 *       GasHandlers#stored}). One with no room for gas, as a block that only joins two others has, is
 *       passed over. What a click reads is settled when it is made.
 *   <li><b>Any other block:</b> the point just in front of the face clicked, so a pane's two sides
 *       read their own cells.
 * </ul>
 *
 * <p>Air outside zones is the dimension's ambient, except in a place another mod keeps breathable by
 * its own means, such as the area of a space mod's oxygen machine (SPACE-017). There it reads that
 * mod's air ({@link LocalAtmospheres#at}), which is what the breathing rules apply and the HUD shows,
 * and says so; {@code /zas at} still gives the ambient.
 *
 * <p>A tank isn't a target: the window has a tab for one in the other hand. Server thread only.
 *
 * @param kind what it is: {@link Kind#HERE}, {@link Kind#POINT}, {@link Kind#NETWORK}, {@link
 *     Kind#CANISTER} or {@link Kind#STORE}
 * @param pos the block it was used on, a large tank's inlet half for either of its halves, or the
 *     block holding the point; null for {@link Kind#HERE}
 * @param point the point read, for {@link Kind#POINT}
 * @param port the device's port for a network, or -1 for a node that is one network
 * @param face the face it was used on, for {@link Kind#STORE}: the one its handler is asked on first
 */
public record AnalyzerTarget(
        Kind kind,
        @Nullable BlockPos pos,
        @Nullable Vec3 point,
        int port,
        @Nullable Direction face) {
    /**
     * How far past the player's reach a block target stays readable, as a container's window does: the
     * window closes beyond it.
     */
    public static final double REACH = 4.0;

    /** How far in front of a face its point is, inside the next cell. */
    private static final double FRONT = 0.01;

    /** What a store reads as once it has no handler with room: nothing, in a litre. */
    private static final GasMixture NOTHING = new GasMixture(Map.of(), 0, 1);

    /**
     * What a reading says.
     *
     * @param name what it read
     * @param air the atmosphere there, where it read air
     * @param local whether the gas is another mod's air for the place, outside zones, rather than the
     *     atmosphere's
     * @param gas its gas
     */
    public record Reading(Component name, @Nullable AtmosphereSample air, boolean local, GasMixture gas) {}

    public AnalyzerTarget {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.TANK) {
            throw new IllegalArgumentException("a tank is read from the other hand, not as a target");
        }
        if ((kind == Kind.HERE) != (pos == null)
                || (kind == Kind.POINT) != (point != null)
                || (kind == Kind.STORE) != (face != null)) {
            throw new IllegalArgumentException(
                    "a " + kind + " target with pos " + pos + ", point " + point + " and face " + face);
        }
    }

    /** The player's own eyes. */
    public static AnalyzerTarget here() {
        return new AnalyzerTarget(Kind.HERE, null, null, -1, null);
    }

    /** What a click on a block reads. */
    public static AnalyzerTarget on(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        PipeNodeEntity vessel = GasVessels.at(level, pos);
        if (vessel != null) {
            return new AnalyzerTarget(Kind.CANISTER, vessel.getBlockPos(), null, -1, null);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PipeNodeEntity) {
            return new AnalyzerTarget(Kind.NETWORK, pos, null, -1, null);
        }
        BlockState state = level.getBlockState(pos);
        if (be instanceof InlineDeviceEntity && state.getBlock() instanceof InlineDeviceBlock block) {
            return new AnalyzerTarget(Kind.NETWORK, pos, null, portAt(block.portFaces(state), pos, hit), null);
        }
        Direction face = hit.getDirection();
        if (level instanceof ServerLevel server && GasHandlers.stored(server, pos, face) != null) {
            return new AnalyzerTarget(Kind.STORE, pos, null, -1, face);
        }
        Vec3 point = hit.getLocation().add(face.getStepX() * FRONT, face.getStepY() * FRONT, face.getStepZ() * FRONT);
        return new AnalyzerTarget(Kind.POINT, BlockPos.containing(point), point, -1, null);
    }

    /** The port on the face clicked, or else the one whose face is nearest the point clicked. */
    private static int portAt(List<Direction> faces, BlockPos pos, BlockHitResult hit) {
        int on = faces.indexOf(hit.getDirection());
        if (on >= 0) {
            return on;
        }
        Vec3 from = hit.getLocation().subtract(Vec3.atCenterOf(pos));
        int best = 0;
        double bestToward = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < faces.size(); i++) {
            Direction d = faces.get(i);
            double toward = from.x * d.getStepX() + from.y * d.getStepY() + from.z * d.getStepZ();
            if (toward > bestToward) {
                bestToward = toward;
                best = i;
            }
        }
        return best;
    }

    /**
     * Whether a player can still read it: always for its own eyes; for a block, while the block is still
     * there and within {@link #REACH} of the player's reach, and for another mod's store while it still
     * provides a handler.
     */
    public boolean readableBy(Player player) {
        if (kind == Kind.HERE) {
            return true;
        }
        Level level = player.level();
        return level.isLoaded(pos) && player.canInteractWithBlock(pos, REACH) && present(level);
    }

    private boolean present(Level level) {
        BlockEntity be = level.getBlockEntity(pos);
        return switch (kind) {
            case CANISTER -> GasVessels.at(level, pos) instanceof PipeNodeEntity v && !v.isRemoved();
            case STORE -> level instanceof ServerLevel server && GasHandlers.providesStore(server, pos, face);
            case NETWORK ->
                port < 0
                        ? be instanceof PipeNodeEntity n && !n.isRemoved()
                        : be instanceof InlineDeviceEntity d
                                && !d.isRemoved()
                                && level.getBlockState(pos).getBlock() instanceof InlineDeviceBlock block
                                && port
                                        < block.portFaces(level.getBlockState(pos))
                                                .size();
            default -> true;
        };
    }

    /**
     * Reads it now. Call it only while {@link #readableBy} the player.
     *
     * @param player the player using the analyzer
     * @return what it is, and its gas
     */
    public Reading read(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        switch (kind) {
            case HERE -> {
                return air(Component.translatable("gui.zas_atmos.analyzer.here"), level, player.getEyePosition());
            }
            case POINT -> {
                return air(coordinates(pos), level, point);
            }
            case CANISTER -> {
                PipeNodeEntity vessel = GasVessels.at(level, pos);
                GasParcel share = vessel.share();
                GasMixture gas = new GasMixture(share.moles(), share.temperature(), vessel.nodeVolume());
                return new Reading(blockName(level), null, false, gas);
            }
            case STORE -> {
                GasMixture held = GasHandlers.stored(level, pos, face);
                return new Reading(blockName(level), null, false, held != null ? held : NOTHING);
            }
            default -> {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof InlineDeviceEntity device) {
                    Component name = Component.translatable(
                            "gui.zas_atmos.analyzer.port", blockName(level), portName(level.getBlockState(pos)));
                    return new Reading(
                            name, null, false, device.port(port).network().gas().mixture());
                }
                return new Reading(
                        blockName(level),
                        null,
                        false,
                        ((PipeNodeEntity) be).network().gas().mixture());
            }
        }
    }

    /**
     * The air at a point: the atmosphere's, or outside zones another mod's for the place, where one
     * answers for it.
     */
    private static Reading air(Component name, ServerLevel level, Vec3 point) {
        AtmosphereSample air = Atmospheres.at(level, point);
        GasMixture local = air.state() == AtmosphereState.AMBIENT ? LocalAtmospheres.at(level, point) : null;
        return new Reading(name, air, local != null, local != null ? local : air.mixture());
    }

    private Component blockName(Level level) {
        Block block = level.getBlockState(pos).getBlock();
        return block.getName();
    }

    /** A port's name, as the device's own window labels it. */
    private Component portName(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof MixerBlock) {
            return switch (port) {
                case 0 -> Component.translatable("gui.zas_atmos.device.input_1");
                case 1 -> Component.translatable("gui.zas_atmos.device.output");
                default -> Component.translatable("gui.zas_atmos.device.input_2");
            };
        }
        return switch (port) {
            case 0 -> Component.translatable("gui.zas_atmos.device.input");
            case 1 -> Component.translatable("gui.zas_atmos.device.output");
            default ->
                block instanceof FilterBlock
                        ? Component.translatable("gui.zas_atmos.device.filtered")
                        : Component.translatable("gui.zas_atmos.device.port");
        };
    }

    private static Component coordinates(BlockPos p) {
        return Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
    }
}
