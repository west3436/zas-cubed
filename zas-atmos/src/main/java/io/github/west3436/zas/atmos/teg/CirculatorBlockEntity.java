package io.github.west3436.zas.atmos.teg;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.inline.InlineDeviceEntity;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipePort;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A circulator's block entity (CONT-157): two 200 L ports, an inlet at the back and an outlet at the
 * front, each in a network of its own, as a pump's. It needs no power and has no switch: every atmos
 * tick it passes what brings its inlet down to its outlet plus {@code circulator_friction_kpa}, at
 * most {@code circulator_max_flow} L/s, as vg's circulator does.
 *
 * <p>Beside a running {@linkplain TegBlockEntity core} that it serves, the core passes its gas
 * instead, in the core's own step, so that the core has both sides' streams at once ({@link #draw},
 * {@link #pass}); the circulator then only shows what the core passed.
 */
public class CirculatorBlockEntity extends InlineDeviceEntity {
    /** The inlet's port: at the back. */
    public static final int INLET = 0;

    /** The outlet's port: at the front. */
    public static final int OUTLET = 1;

    /** What its core passed through it since its last step, mol. */
    private double driven;

    /** What its core's last pass did, or null if no core has driven it since its last step. */
    private @Nullable InlineStatus drivenStatus;

    /** Why its last {@link #draw} took what it took. */
    private InlineStatus lastDraw = InlineStatus.TARGET_REACHED;

    public CirculatorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.CIRCULATOR.get(), pos, state, 2);
    }

    /** The inlet's port. */
    public PipePort inlet() {
        return port(INLET);
    }

    /** The outlet's port. */
    public PipePort outlet() {
        return port(OUTLET);
    }

    /** It always runs: it is passive, and needs neither power nor a switch. */
    @Override
    public boolean running() {
        return !isRemoved();
    }

    @Override
    protected int drawWhenOn() {
        return 0;
    }

    @Override
    protected @Nullable Supplier<SoundEvent> switchSound(boolean on) {
        return null;
    }

    /** Its block shows running only while gas passes. */
    @Override
    protected boolean showsRunning(InlineStatus status) {
        return status == InlineStatus.MOVING;
    }

    @Override
    protected InlineStatus work(ServerLevel level) {
        TegBlockEntity core = TegBlock.coreOf(level, worldPosition, getBlockState());
        if (core != null && core.drives()) {
            InlineStatus status = drivenStatus == null ? InlineStatus.TARGET_REACHED : drivenStatus;
            reportMoved(driven);
            driven = 0;
            drivenStatus = null;
            return status;
        }
        driven = 0;
        drivenStatus = null;
        GasParcel stream = draw();
        InlineStatus status = lastDraw;
        if (!stream.isEmpty()) {
            reportMoved(stream.totalMoles());
            pass(stream);
        }
        return status;
    }

    /**
     * Takes from its inlet's network the gas it passes this atmos tick (CONT-157), which the caller
     * then gives back to {@link #pass}. Empty when its sides are within the friction of each other,
     * its inlet is empty, or one network joins both ports.
     *
     * @return the stream
     */
    GasParcel draw() {
        PipeNetwork in = inlet().network();
        PipeNetwork out = outlet().network();
        if (in == out || !in.valid()) {
            lastDraw = InlineStatus.SAME_NETWORK;
            return GasParcel.EMPTY;
        }
        GasStore from = in.gas();
        GasStore to = out.gas();
        double n = TegMath.circulatorMoles(
                from.totalMoles(),
                from.temperature(),
                from.volume(),
                from.pressure(),
                to.volume(),
                to.pressure(),
                AtmosServerConfig.get(AtmosServerConfig.CIRCULATOR_FRICTION_KPA),
                AtmosServerConfig.get(AtmosServerConfig.CIRCULATOR_MAX_FLOW) * AtmosTicks.SECONDS);
        if (n < PipeBlockEntity.MIN_FLOW) {
            lastDraw = from.isEmpty() ? InlineStatus.INPUT_EMPTY : InlineStatus.TARGET_REACHED;
            return GasParcel.EMPTY;
        }
        lastDraw = InlineStatus.MOVING;
        return from.takeFraction(n / from.totalMoles());
    }

    /**
     * Puts a stream {@link #draw} took into its outlet's network.
     *
     * @param stream the stream, as drawn or with its temperature changed
     */
    void pass(GasParcel stream) {
        if (!stream.isEmpty()) {
            outlet().network().gas().add(stream);
        }
    }

    /**
     * Its core says what its step did with this circulator, for the circulator's own next step.
     *
     * @param moles what passed, mol
     */
    void drivenBy(double moles) {
        driven += moles;
        drivenStatus = lastDraw;
    }

    /** No window of its own: using it opens its core's. */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }
}
