package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.PowerFeed;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The area air controller's work, once per atmos tick (CONT-040 to CONT-049, CONT-062 to CONT-068,
 * CONT-110, architecture §11.6 and §11.7):
 *
 * <ol>
 *   <li>Fills its buffer from FE blocks next to it (CONT-062).
 *   <li>Keeps its zone pinned: it pins the zone at its own cell when it loads, and again whenever
 *       that cell has no zone or a zone it hasn't pinned, so a room sealed after it was placed
 *       forms its zone then (CONT-110). It unpins when removed.
 *   <li>Pays its own draw; without it, it is unpowered and does nothing else.
 *   <li>Rates its zone's air (CONT-040) and updates its danger level (CONT-046).
 *   <li>Finds the parts in its zone and those linked to it (CONT-042), powers them in priority order
 *       (CONT-064, CONT-068) and commands its vents and scrubbers for its mode (CONT-041).
 * </ol>
 */
public class AreaAirControllerBlockEntity extends AtmosDevice implements MenuProvider {
    /** A part the controller covers, as the GUI lists it (CONT-049). */
    public record Part(ResourceLocation block, BlockPos pos, boolean inZone, boolean linked, boolean powered) {}

    private final EnergyStore buffer = new EnergyStore(
            () -> AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_BUFFER), true, false, this::setChanged);
    private final DangerLevel danger = new DangerLevel();
    private final Set<BlockPos> links = new LinkedHashSet<>();
    private ControllerPreset preset = ControllerPreset.HUMAN;
    private ControllerMode mode = ControllerMode.SCRUBBING;
    private boolean powered;
    private ControllerStatus status = ControllerStatus.UNPOWERED;

    private CompletableFuture<PinResult> pending;
    private ZoneId pinnedZone;
    private PinOutcome lastPin;
    private AtmosphereSample sample;
    private double[] values = new double[0];
    private int[] ratings = new int[0];
    private List<Part> parts = List.of();

    public AreaAirControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.AREA_AIR_CONTROLLER.get(), pos, state);
    }

    /**
     * The owner of the controller's pins: {@code zas_atmos:area_air_controller/x_y_z}, one per
     * position (API-017).
     */
    public SourceId owner() {
        BlockPos p = getBlockPos();
        return SourceId.of(ZasAtmosMod.MOD_ID, "area_air_controller/" + p.getX() + "_" + p.getY() + "_" + p.getZ());
    }

    /** The point the controller samples: the middle of its own block, in front of the wall. */
    public Vec3 airPoint() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    public boolean readyToStep() {
        return true;
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        pull(level);
        sample = Atmospheres.at(level, airPoint());
        keepPinned(level);

        long own = (long) AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
        powered = buffer.take(own);
        List<PoweredDevice> covered = covered(level);
        if (!powered) {
            danger.reset();
            parts = describe(covered, new boolean[covered.size()]);
            setStatus(level, ControllerStatus.UNPOWERED);
            return;
        }

        if (sample.state() != AtmosphereState.UNRESOLVED) {
            values = preset.values(sample.mixture());
            ratings = preset.rate(values);
            int worst = 0;
            for (int r : ratings) {
                worst = Math.max(worst, r);
            }
            danger.update(worst);
            mode = mode.after(sample.pressure(), preset);
        }

        boolean[] supplied = feed(covered);
        VentOrder vent = mode.vent(preset);
        ScrubberOrder scrubber = mode.scrubber(preset);
        for (PoweredDevice d : covered) {
            if (d instanceof VentControl v) {
                v.ventOrder(vent);
            }
            if (d instanceof ScrubberControl s) {
                s.scrubberOrder(scrubber);
            }
        }
        parts = describe(covered, supplied);
        setStatus(level, ControllerStatus.of(danger.level()));
    }

    /** Takes FE from every block next to it that gives it (CONT-062). */
    private void pull(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            if (buffer.space() <= 0) {
                return;
            }
            IEnergyStorage source = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, getBlockPos().relative(dir), dir.getOpposite());
            if (source != null && source.canExtract()) {
                int got = source.extractEnergy(buffer.space(), false);
                buffer.fill(got);
            }
        }
    }

    /**
     * Pins the zone at the controller's cell when it hasn't pinned the zone there: once after
     * loading, and whenever the cell's zone isn't the one it pinned, as when the room was open and
     * has just been sealed (CONT-110). A pin is idempotent, so pinning again costs nothing.
     */
    private void keepPinned(ServerLevel level) {
        if (pending != null || sample.state() == AtmosphereState.UNRESOLVED) {
            return;
        }
        Optional<ZoneId> here = sample.zone();
        if (pinnedZone != null && here.isPresent() && here.get().equals(pinnedZone)) {
            return;
        }
        CompletableFuture<PinResult> future = ZonePins.pin(level, airPoint(), owner());
        pending = future;
        future.whenComplete((result, failure) -> {
            if (pending != future) {
                return;
            }
            pending = null;
            lastPin = failure == null ? result.outcome() : PinOutcome.UNRESOLVED;
            pinnedZone = failure == null ? result.zone().orElse(null) : null;
        });
    }

    /** Called by the block when the controller is removed: unpins its zone (CONT-110). */
    void removedFromWorld(ServerLevel level) {
        pending = null;
        pinnedZone = null;
        ZonePins.unpin(level, owner());
    }

    /** The parts in its zone and those linked to it, in the order they joined the schedule. */
    private List<PoweredDevice> covered(ServerLevel level) {
        links.removeIf(p -> level.isLoaded(p) && !(level.getBlockEntity(p) instanceof PoweredDevice));
        Optional<ZoneId> zone = sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED
                ? sample.zone()
                : Optional.empty();
        List<PoweredDevice> out = new ArrayList<>();
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d == this || !(d instanceof PoweredDevice p)) {
                continue;
            }
            boolean linked = links.contains(d.getBlockPos());
            if (linked || (zone.isPresent() && inZone(level, p, zone.get()))) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean inZone(ServerLevel level, PoweredDevice d, ZoneId zone) {
        return Atmospheres.at(level, d.airPoint()).zone().filter(zone::equals).isPresent();
    }

    /** Powers the covered parts from the buffer, in priority order (CONT-064, CONT-068). */
    private boolean[] feed(List<PoweredDevice> covered) {
        List<PowerFeed.Demand> demands = new ArrayList<>();
        List<Integer> unpaid = new ArrayList<>();
        boolean[] supplied = new boolean[covered.size()];
        for (int i = 0; i < covered.size(); i++) {
            DevicePower p = covered.get(i).power();
            if (p.suppliedThisAtmosTick()) {
                supplied[i] = true; // paid already, by another controller or through a link
            } else {
                demands.add(new PowerFeed.Demand(p.priority(), p.drawPerAtmosTick()));
                unpaid.add(i);
            }
        }
        PowerFeed.Plan plan = PowerFeed.plan(buffer.getEnergyStored(), demands);
        buffer.take(plan.spent());
        for (int j = 0; j < unpaid.size(); j++) {
            if (plan.powered()[j]) {
                int i = unpaid.get(j);
                covered.get(i).power().supply(this);
                supplied[i] = true;
            }
        }
        return supplied;
    }

    private List<Part> describe(List<PoweredDevice> covered, boolean[] supplied) {
        Optional<ZoneId> zone = sample == null ? Optional.empty() : sample.zone();
        List<Part> out = new ArrayList<>();
        for (int i = 0; i < covered.size(); i++) {
            BlockEntity be = (BlockEntity) covered.get(i);
            ServerLevel level = (ServerLevel) be.getLevel();
            boolean in = zone.isPresent() && level != null && inZone(level, covered.get(i), zone.get());
            out.add(new Part(
                    BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()),
                    be.getBlockPos(),
                    in,
                    links.contains(be.getBlockPos()),
                    supplied[i]));
        }
        return List.copyOf(out);
    }

    private void setStatus(ServerLevel level, ControllerStatus s) {
        status = s;
        BlockState state = getBlockState();
        if (state.hasProperty(AreaAirControllerBlock.STATUS) && state.getValue(AreaAirControllerBlock.STATUS) != s) {
            level.setBlock(getBlockPos(), state.setValue(AreaAirControllerBlock.STATUS, s), 3);
            level.updateNeighborsAt(AreaAirControllerBlock.wall(state, getBlockPos()), state.getBlock());
        }
    }

    // ---- Settings, for the GUI and tests -------------------------------------------------

    public EnergyStore buffer() {
        return buffer;
    }

    public ControllerMode mode() {
        return mode;
    }

    public void setMode(ControllerMode mode) {
        this.mode = mode;
        setChanged();
    }

    public ControllerPreset preset() {
        return preset;
    }

    public void setPreset(ControllerPreset preset) {
        this.preset = preset;
        values = new double[0];
        ratings = new int[0];
        setChanged();
    }

    /** The danger level, 0 to 2. */
    public int danger() {
        return danger.level();
    }

    public ControllerStatus status() {
        return status;
    }

    /** Whether it paid its own draw at its last step. */
    public boolean powered() {
        return powered;
    }

    /** The last readings, numbered as in {@link ControllerPreset}; empty before the first. */
    public double[] values() {
        return values.clone();
    }

    /** The last readings' danger levels. */
    public int[] ratings() {
        return ratings.clone();
    }

    /** The parts it covered at its last step (CONT-049). */
    public List<Part> parts() {
        return parts;
    }

    /** The outcome of its last pin, or null before one completes. */
    public PinOutcome lastPin() {
        return lastPin;
    }

    /** The positions linked to it with the multitool (CONT-042). */
    public Set<BlockPos> links() {
        return Set.copyOf(links);
    }

    /**
     * Links or unlinks the part at a position.
     *
     * @return true if it is now linked
     */
    public boolean toggleLink(BlockPos pos) {
        boolean linked;
        if (links.remove(pos)) {
            linked = false;
        } else {
            links.add(pos.immutable());
            linked = true;
        }
        setChanged();
        return linked;
    }

    // ---- Saving and the menu -------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", buffer.getEnergyStored());
        tag.putString("Mode", mode.key());
        tag.put("Preset", preset.save());
        tag.putInt("Danger", danger.level());
        tag.putInt("DangerLower", danger.lowerReadings());
        tag.putInt("DangerLowerMax", danger.highestLower());
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.set(tag.getInt("Energy"));
        mode = ControllerMode.byKey(tag.getString("Mode"));
        preset = tag.contains("Preset") ? ControllerPreset.load(tag.getCompound("Preset")) : ControllerPreset.HUMAN;
        danger.restore(tag.getInt("Danger"), tag.getInt("DangerLower"), tag.getInt("DangerLowerMax"));
        links.clear();
        for (long p : tag.getLongArray("Links")) {
            links.add(BlockPos.of(p));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.area_air_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ControllerMenu(id, inventory, this);
    }
}
