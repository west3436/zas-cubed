package io.github.west3436.zas.atmos.vent;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.controller.VentControl;
import io.github.west3436.zas.atmos.controller.VentOrder;
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
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A vent's block entity (CONT-014, CONT-078): a 400 L network node that blows its network's gas
 * into the cell in front of its grille, or siphons that cell's zone into its network, within its
 * bounds, at most half a buffer's moles a second. In an open cell it blows into, or siphons from,
 * the ambient.
 *
 * <p>It runs only while powered (CONT-060), drawing {@code [power] vent} FE/t (CONT-065) whenever
 * it isn't off, in the life-support group.
 *
 * <p>An area air controller that covers it orders it every atmos tick ({@link #ventOrder},
 * CONT-041, which calls {@link #control}). While
 * those orders keep coming, the vent is controlled: its GUI shows the controller and its order, and
 * refuses hand settings ({@link #setByHand}). A vent no controller orders is set by hand. A vent
 * linked to an airlock controller takes that controller's orders alone ({@link #airlockOrder},
 * CONT-152), and moves only its share of what its zone needs, so that several vents on one chamber
 * reach the target together and don't pass it.
 *
 * <p>Its GUI also shows its network's pressure, which the internal bound is compared with, and
 * its {@link VentStatus}: whether it moved gas at its last atmos tick, or why not.
 */
public class VentBlockEntity extends PipeNodeEntity implements PoweredDevice, VentControl, MenuProvider {
    /** Who a vent's changes are made by. */
    public static final SourceId VENT = SourceId.of(ZasAtmosMod.MOD_ID, "vent");

    private static final String MODE = "Mode";
    private static final String EXTERNAL = "External";
    private static final String INTERNAL = "Internal";
    private static final String CONTROLLED = "Controlled";
    private static final String CONTROLLER = "Controller";

    private final ZonePort port = new ZonePort(VENT);
    private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, this::currentDraw, this::now);
    private VentSettings settings = VentSettings.OFF;
    private long controlledUntil = Long.MIN_VALUE;
    private long airlockUntil = Long.MIN_VALUE;
    /** Its airlock controller's share for it (CONT-152); never saved, as the controller sends it each step. */
    private double airlockShare = 1;

    private BlockPos controller;
    private VentStatus status = VentStatus.OFF;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case VentMenu.DATA_MODE -> settings.mode().ordinal();
                case VentMenu.DATA_EXTERNAL -> (int) Math.round(settings.external());
                case VentMenu.DATA_INTERNAL -> (int) Math.round(settings.internal());
                case VentMenu.DATA_POWERED -> power.isPowered() ? 1 : 0;
                case VentMenu.DATA_CONTROLLED -> !controlled() ? 0 : controller == null ? 2 : 1;
                case VentMenu.DATA_X_LOW -> low(controller == null ? 0 : controller.getX());
                case VentMenu.DATA_X_HIGH -> high(controller == null ? 0 : controller.getX());
                case VentMenu.DATA_Y -> controller == null ? 0 : controller.getY();
                case VentMenu.DATA_Z_LOW -> low(controller == null ? 0 : controller.getZ());
                case VentMenu.DATA_Z_HIGH -> high(controller == null ? 0 : controller.getZ());
                case VentMenu.DATA_NETWORK -> (int) Math.min(Short.MAX_VALUE, Math.round(networkPressure()));
                case VentMenu.DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return VentMenu.DATA_COUNT;
        }
    };

    public VentBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.VENT.get(), pos, state, PipeVolumes.VENT);
    }

    /** The low 16 bits of a coordinate: menu data syncs as shorts. */
    private static int low(int v) {
        return (short) v;
    }

    /** The high 16 bits of a coordinate. */
    private static int high(int v) {
        return (short) (v >> 16);
    }

    /** What the vent draws now: its configured draw unless it is off (CONT-065). */
    private int currentDraw() {
        return settings.mode() == VentMode.OFF ? 0 : AtmosServerConfig.get(AtmosServerConfig.VENT_DRAW);
    }

    /** The vent's settings. */
    public VentSettings settings() {
        return settings;
    }

    /**
     * Sets the vent's mode and bounds. The bounds are already clamped by {@link VentSettings}. It plays
     * its on or off sound as it goes from off to a mode or back (CONT-126). Server side. A player's
     * settings go through {@link #setByHand}, and a controller's through {@link #control}.
     *
     * @param settings the new settings; null is off
     */
    public void command(VentSettings settings) {
        VentSettings next = settings == null ? VentSettings.OFF : settings;
        if (!next.equals(this.settings)) { // a controller sends the same order every atmos tick
            boolean wasOff = this.settings.mode() == VentMode.OFF;
            boolean off = next.mode() == VentMode.OFF;
            this.settings = next;
            setChanged();
            if (wasOff != off && level != null) {
                AtmosSounds.play(level, worldPosition, off ? AtmosSounds.VENT_OFF : AtmosSounds.VENT_ON);
            }
        }
    }

    /**
     * An area air controller's order (CONT-041): off, blow to its target with the internal bound at 0,
     * or siphon to its target with the internal bound at its highest. The same order every atmos tick
     * changes nothing.
     */
    @Override
    public void ventOrder(VentOrder order) {
        if (airlockControlled()) {
            return; // an airlock controller's orders win (CONT-152)
        }
        // An area air controller names itself on every order, the first included. An order with
        // no sender falls back to the controller that paid for the vent last, if any.
        BlockPos from = order.sender() != null
                ? order.sender()
                : power.source() instanceof net.minecraft.world.level.block.entity.BlockEntity be
                        ? be.getBlockPos()
                        : controller().orElse(null);
        control(
                from,
                switch (order.mode()) {
                    case OFF -> VentSettings.OFF;
                    case BLOW -> VentSettings.blow(order.targetKpa());
                    case SIPHON -> VentSettings.siphon(order.targetKpa());
                });
    }

    /**
     * An area air controller's order: sets the vent and makes it controlled for the next {@link
     * DevicePower#LEASE_TICKS} game ticks. A controller orders its vents every atmos tick, so a vent
     * stays controlled while one covers it. Server side.
     *
     * @param controller where the controller is, for the GUI; null if not known
     * @param order the settings it orders
     */
    public void control(BlockPos controller, VentSettings order) {
        this.controller = controller == null ? null : controller.immutable();
        controlledUntil = now() + DevicePower.LEASE_TICKS;
        command(order);
    }

    /**
     * An airlock controller's order (CONT-139, CONT-152): as {@link #control}, and for the next {@link
     * DevicePower#LEASE_TICKS} game ticks the vent ignores area air controllers' orders, so the airlock
     * controller it's linked to runs it alone. It sends one every atmos tick, off between cycles.
     *
     * @param controller where the airlock controller is
     * @param order the settings it orders
     */
    public void airlockOrder(BlockPos controller, VentSettings order) {
        airlockOrder(controller, order, 1);
    }

    /**
     * An airlock controller's order to one of several vents on one zone: as {@link
     * #airlockOrder(BlockPos, VentSettings)}, and while the order lasts the vent moves only {@code
     * share} of what brings the zone to the external bound ({@link VentFlow#shares}).
     *
     * @param controller where the airlock controller is
     * @param order the settings it orders
     * @param share the vent's share, 0 to 1; NaN is 1
     */
    public void airlockOrder(BlockPos controller, VentSettings order, double share) {
        airlockUntil = now() + DevicePower.LEASE_TICKS;
        airlockShare = Double.isNaN(share) ? 1 : Math.max(0, Math.min(1, share));
        control(controller, order);
    }

    /** The share of what its zone needs the vent moves: its airlock controller's, or else 1. */
    public double zoneShare() {
        return airlockControlled() ? airlockShare : 1;
    }

    /**
     * What the vent could move in a second under {@code order} with no bound on the zone, the cell in
     * front of its grille reading {@code sample}: what its airlock controller splits the zone's need
     * by ({@link VentFlow#shares}). Server side.
     *
     * @return the moles, at least 0; 0 if the order is off
     */
    public double capacity(AtmosphereSample sample, VentSettings order) {
        if (isRemoved()) {
            return 0;
        }
        GasStore net = network().gas();
        GasMixture zone = sample.mixture();
        return switch (order.mode()) {
            case OFF -> 0;
            case BLOW -> VentFlow.blowCapacity(net.totalMoles(), net.volume(), net.temperature(), order);
            case SIPHON ->
                VentFlow.siphonCapacity(
                        zone.totalMoles(),
                        zone.volume(),
                        zone.temperature(),
                        net.totalMoles(),
                        net.volume(),
                        net.temperature(),
                        order);
        };
    }

    /** Whether an airlock controller has ordered the vent within the last {@link DevicePower#LEASE_TICKS} game ticks. */
    public boolean airlockControlled() {
        return now() < airlockUntil;
    }

    /** Whether an area air controller has ordered the vent within the last {@link DevicePower#LEASE_TICKS} game ticks. */
    public boolean controlled() {
        return now() < controlledUntil;
    }

    /** The controller that ordered the vent last, while it is controlled, if known. */
    public java.util.Optional<BlockPos> controller() {
        return controlled() ? java.util.Optional.ofNullable(controller) : java.util.Optional.empty();
    }

    /**
     * A player's settings, from the GUI: applied unless a controller controls the vent, whose orders
     * win (CONT-014, CONT-074).
     *
     * @return whether they were applied
     */
    public boolean setByHand(VentSettings settings) {
        if (controlled()) {
            return false;
        }
        command(settings);
        return true;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** The cell in front of the grille: where the vent blows and siphons. */
    @Override
    public Vec3 airPoint() {
        return Vec3.atCenterOf(worldPosition.relative(getBlockState().getValue(VentBlock.FACING)));
    }

    /**
     * The pressure of the vent's pipe network, kPa: what siphoning fills up to the internal bound,
     * and blowing empties down to it. Server side.
     */
    public double networkPressure() {
        if (isRemoved()) {
            return 0;
        }
        GasStore net = network().gas();
        return net.volume() <= 0 ? 0 : GasMixture.GAS_CONSTANT * net.temperature() * net.totalMoles() / net.volume();
    }

    /** What the vent did at its last atmos tick: off, moving gas, or idle and why. */
    public VentStatus status() {
        return status;
    }

    /** Whether the vent is running: on and powered. */
    public boolean running() {
        return settings.mode() != VentMode.OFF && power.isPowered();
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        GasStore net = network().gas();
        VentMode shown = running() ? settings.mode() : VentMode.OFF;
        BlockState state = getBlockState();
        if (state.getValue(VentMode.PROPERTY) != shown) {
            level.setBlock(worldPosition, state.setValue(VentMode.PROPERTY, shown), Block.UPDATE_CLIENTS);
        }
        if (shown == VentMode.OFF) {
            status = VentStatus.OFF;
            reportStopped();
            return;
        }
        Vec3 point = airPoint();
        AtmosphereSample sample = Atmospheres.at(level, point);
        if (!cellWatch().trust(sample)) {
            status = VentStatus.WAITING;
            return;
        }
        GasMixture zone = sample.mixture();
        double share = zoneShare();
        if (shown == VentMode.BLOW) {
            status = VentFlow.blowIdle(
                    zone.pressure(),
                    zone.volume(),
                    net.totalMoles(),
                    net.volume(),
                    net.temperature(),
                    settings,
                    share,
                    PipeBlockEntity.MIN_FLOW);
            if (status != VentStatus.MOVING) {
                return;
            }
            double n = VentFlow.blow(
                    zone.pressure(), zone.volume(), net.totalMoles(), net.volume(), net.temperature(), settings, share);
            reportMoved(n);
            GasParcel out = net.takeFraction(n / net.totalMoles());
            port.add(level, point, out, r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())));
        } else {
            status = VentFlow.siphonIdle(
                    zone.totalMoles(),
                    zone.volume(),
                    zone.temperature(),
                    net.totalMoles(),
                    net.volume(),
                    net.temperature(),
                    settings,
                    share,
                    PipeBlockEntity.MIN_FLOW);
            if (status != VentStatus.MOVING) {
                return;
            }
            double n = VentFlow.siphon(
                    zone.totalMoles(),
                    zone.volume(),
                    zone.temperature(),
                    net.totalMoles(),
                    net.volume(),
                    net.temperature(),
                    settings,
                    share);
            reportMoved(n);
            double total = zone.totalMoles();
            Map<ResourceLocation, Double> want = new TreeMap<>();
            zone.moles().forEach((gas, m) -> {
                double take = m * n / total;
                if (take > 0) {
                    want.put(gas, take);
                }
            });
            port.remove(level, point, want, r -> deliver(r.moved()));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(MODE, settings.mode().getSerializedName());
        tag.putDouble(EXTERNAL, settings.external());
        tag.putDouble(INTERNAL, settings.internal());
        power.save(tag);
        // Shown to /data get block while a controller controls the vent; never loaded, since a
        // controller's orders don't outlast a reload and it sends them again.
        if (controlled()) {
            tag.putBoolean(CONTROLLED, true);
            if (controller != null) {
                tag.put(CONTROLLER, net.minecraft.nbt.NbtUtils.writeBlockPos(controller));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        VentMode mode = VentMode.OFF;
        for (VentMode m : VentMode.values()) {
            if (m.getSerializedName().equals(tag.getString(MODE))) {
                mode = m;
            }
        }
        settings = new VentSettings(
                mode,
                tag.contains(EXTERNAL) ? tag.getDouble(EXTERNAL) : VentSettings.DEFAULT_EXTERNAL,
                tag.getDouble(INTERNAL));
        power.load(tag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.vent");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new VentMenu(id, data, ContainerLevelAccess.create(level, worldPosition), this);
    }
}
