package io.github.west3436.zas.atmos.scrubber;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.ScrubberControl;
import io.github.west3436.zas.atmos.controller.ScrubberOrder;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A scrubber's block entity (CONT-015, CONT-079 to CONT-081): a 200 L network node that takes gas
 * from the cell in front of its grille into its network, as {@link ScrubberMath} says, through one
 * {@link ZonePort}. In an open cell it takes from the ambient.
 *
 * <p>It runs only while powered (CONT-060), in the life-support group, drawing {@code [power]
 * scrubber} FE/t while it scrubs or siphons and {@code [power] scrubber_panic} in panic (CONT-065).
 *
 * <p>An area air controller that covers it orders it every atmos tick ({@link #scrubberOrder},
 * CONT-041). While those orders keep coming, the scrubber is controlled: its GUI shows the controller
 * and its order, and it refuses hand settings ({@link #setByHand}), as a vent does (CONT-014).
 *
 * <p>Its GUI also shows its network's pressure, which its stall is read from, and its {@link
 * ScrubberStatus}: whether it took gas at its last atmos tick, or why not.
 */
public class ScrubberBlockEntity extends PipeNodeEntity implements PoweredDevice, ScrubberControl, MenuProvider {
    /** Who a scrubber's changes are made by. */
    public static final SourceId SCRUBBER = SourceId.of(ZasAtmosMod.MOD_ID, "scrubber");

    private static final String MODE = "Mode";
    private static final String SELECTION = "Selection";
    private static final String CONTROLLED = "Controlled";
    private static final String CONTROLLER = "Controller";

    private final ZonePort port = new ZonePort(SCRUBBER);
    private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, this::currentDraw, this::now);
    private ScrubberMode mode = ScrubberMode.OFF;
    private GasSelection selection = GasSelection.DEFAULT;
    private long controlledUntil = Long.MIN_VALUE;
    private BlockPos controller;
    private ScrubberStatus status = ScrubberStatus.OFF;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ScrubberMenu.DATA_MODE -> mode.ordinal();
                case ScrubberMenu.DATA_SELECTION -> ScrubberMenu.mask(selection);
                case ScrubberMenu.DATA_POWERED -> power.isPowered() ? 1 : 0;
                case ScrubberMenu.DATA_CONTROLLED -> !controlled() ? 0 : controller == null ? 2 : 1;
                case ScrubberMenu.DATA_X_LOW -> low(controller == null ? 0 : controller.getX());
                case ScrubberMenu.DATA_X_HIGH -> high(controller == null ? 0 : controller.getX());
                case ScrubberMenu.DATA_Y -> controller == null ? 0 : controller.getY();
                case ScrubberMenu.DATA_Z_LOW -> low(controller == null ? 0 : controller.getZ());
                case ScrubberMenu.DATA_Z_HIGH -> high(controller == null ? 0 : controller.getZ());
                case ScrubberMenu.DATA_NETWORK -> (int) Math.min(Short.MAX_VALUE, Math.round(networkPressure()));
                case ScrubberMenu.DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ScrubberMenu.DATA_COUNT;
        }
    };

    public ScrubberBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.SCRUBBER.get(), pos, state, PipeVolumes.PORT);
    }

    /** The low 16 bits of a coordinate: menu data syncs as shorts. */
    private static int low(int v) {
        return (short) v;
    }

    /** The high 16 bits of a coordinate. */
    private static int high(int v) {
        return (short) (v >> 16);
    }

    /** What the scrubber draws now: nothing while off, twice as much in panic (CONT-065). */
    private int currentDraw() {
        return switch (mode) {
            case OFF -> 0;
            case PANIC -> AtmosServerConfig.get(AtmosServerConfig.SCRUBBER_PANIC_DRAW);
            default -> AtmosServerConfig.get(AtmosServerConfig.SCRUBBER_DRAW);
        };
    }

    /** The scrubber's mode. */
    public ScrubberMode mode() {
        return mode;
    }

    /** The gases it keeps when it scrubs. */
    public GasSelection selection() {
        return selection;
    }

    /**
     * Sets the mode and the selection. It plays its on or off sound as it goes from off to a mode or
     * back (CONT-126). Server side. A player's settings go through {@link #setByHand}, and a
     * controller's through {@link #scrubberOrder}.
     *
     * @param mode the mode; null is off
     * @param selection the gases to keep when scrubbing
     */
    public void command(ScrubberMode mode, GasSelection selection) {
        ScrubberMode next = mode == null ? ScrubberMode.OFF : mode;
        if (next != this.mode || !selection.equals(this.selection)) { // a controller repeats its order
            boolean wasOff = this.mode == ScrubberMode.OFF;
            boolean off = next == ScrubberMode.OFF;
            this.mode = next;
            this.selection = selection;
            setChanged();
            if (wasOff != off && level != null) {
                AtmosSounds.play(level, worldPosition, off ? AtmosSounds.SCRUBBER_OFF : AtmosSounds.SCRUBBER_ON);
            }
        }
    }

    /**
     * An area air controller's order (CONT-041): sets the scrubber and makes it controlled for the
     * next {@link DevicePower#LEASE_TICKS} game ticks. A controller orders its scrubbers every atmos
     * tick, so one stays controlled while a controller covers it. Panic and off leave the selection
     * alone. The same order every atmos tick changes nothing.
     */
    @Override
    public void scrubberOrder(ScrubberOrder order) {
        // An area air controller names itself on every order, the first included. An order with no
        // sender falls back to the controller that paid for the scrubber last, if any.
        BlockPos from = order.sender() != null
                ? order.sender()
                : power.source() instanceof BlockEntity be
                        ? be.getBlockPos()
                        : controller().orElse(null);
        controller = from == null ? null : from.immutable();
        controlledUntil = now() + DevicePower.LEASE_TICKS;
        switch (order.mode()) {
            case OFF -> command(ScrubberMode.OFF, selection);
            case PANIC -> command(ScrubberMode.PANIC, selection);
            case SCRUB -> command(ScrubberMode.SCRUB, GasSelection.of(order.gases(), order.tags()));
        }
    }

    /** Whether an area air controller has ordered the scrubber within the last {@link DevicePower#LEASE_TICKS} game ticks. */
    public boolean controlled() {
        return now() < controlledUntil;
    }

    /** The controller that ordered the scrubber last, while it is controlled, if known. */
    public Optional<BlockPos> controller() {
        return controlled() ? Optional.ofNullable(controller) : Optional.empty();
    }

    /**
     * A player's settings, from the GUI: applied unless a controller controls the scrubber, whose
     * orders win (CONT-074).
     *
     * @return whether they were applied
     */
    public boolean setByHand(ScrubberMode mode, GasSelection selection) {
        if (controlled()) {
            return false;
        }
        command(mode, selection);
        return true;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** The cell in front of the grille: where the scrubber takes from. */
    @Override
    public Vec3 airPoint() {
        return Vec3.atCenterOf(worldPosition.relative(getBlockState().getValue(ScrubberBlock.FACING)));
    }

    /** The pressure of the scrubber's pipe network, kPa, which stalls it at 50 atm. Server side. */
    public double networkPressure() {
        return isRemoved() ? 0 : network().gas().pressure();
    }

    /** What the scrubber did at its last atmos tick: off, moving gas, or idle and why. */
    public ScrubberStatus status() {
        return status;
    }

    /** Whether the scrubber is running: on and powered. */
    public boolean running() {
        return mode != ScrubberMode.OFF && power.isPowered();
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        GasStore net = network().gas();
        ScrubberMode shown = running() ? mode : ScrubberMode.OFF;
        BlockState state = getBlockState();
        if (state.getValue(ScrubberMode.PROPERTY) != shown) {
            level.setBlock(worldPosition, state.setValue(ScrubberMode.PROPERTY, shown), Block.UPDATE_CLIENTS);
        }
        if (shown == ScrubberMode.OFF) {
            status = ScrubberStatus.OFF;
            reportStopped();
            return;
        }
        Vec3 point = airPoint();
        AtmosphereSample sample = Atmospheres.at(level, point);
        if (!cellWatch().trust(sample)) {
            status = ScrubberStatus.WAITING;
            return;
        }
        GasMixture buffer = net.mixture();
        if (ScrubberMath.stalled(buffer)) {
            status = ScrubberStatus.STALLED;
            return;
        }
        Map<ResourceLocation, Double> want = ScrubberMath.take(
                sample.mixture(),
                shown.litres() * AtmosTicks.SECONDS,
                shown == ScrubberMode.SCRUB ? selection : ScrubberMath.EVERY_GAS,
                buffer);
        double total = 0;
        for (double n : want.values()) {
            total += n;
        }
        if (total < PipeBlockEntity.MIN_FLOW) {
            status = ScrubberStatus.NOTHING;
            return;
        }
        status = ScrubberStatus.MOVING;
        reportMoved(total);
        port.remove(level, point, want, r -> deliver(r.moved()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(MODE, mode.getSerializedName());
        tag.put(SELECTION, selection.save());
        power.save(tag);
        // Shown to /data get block while a controller controls the scrubber; never loaded, since a
        // controller's orders don't outlast a reload and it sends them again.
        if (controlled()) {
            tag.putBoolean(CONTROLLED, true);
            if (controller != null) {
                tag.put(CONTROLLER, NbtUtils.writeBlockPos(controller));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = ScrubberMode.byName(tag.getString(MODE));
        selection = GasSelection.load(tag.getCompound(SELECTION));
        power.load(tag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.scrubber");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ScrubberMenu(id, data, ContainerLevelAccess.create(level, worldPosition), this);
    }
}
