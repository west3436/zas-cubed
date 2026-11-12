package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.device.ZoneIndex;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockLogic;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.PowerFeed;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 *   <li>Rates its zone's air (CONT-040) and updates its danger level (CONT-046). A breach is danger
 *       2 at once ({@link BreachAlarm}, CONT-043), and so is a triggered fire alarm it covers
 *       ({@link FireAlarmBlockEntity}, CONT-044).
 *   <li>Finds the parts in its zone and those linked to it (CONT-042), powers them in priority order
 *       (CONT-064, CONT-068) and commands its vents and scrubbers for its mode (CONT-041).
 *   <li>When it reaches danger 2, steps the firelocks it covers at once, so they close in the same
 *       atmos tick (CONT-043).
 * </ol>
 *
 * <p>It records what a step costs ({@link #demand()}), so a central atmospherics controller that has
 * claimed it can pay what its buffer doesn't hold before its next step (CONT-120).
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
    private long demand;
    private ControllerStatus status = ControllerStatus.UNPOWERED;

    private CompletableFuture<PinResult> pending;
    private ZoneId pinnedZone;
    private PinOutcome lastPin;
    private AtmosphereSample sample;
    private double[] values = new double[0];
    private int[] ratings = new int[0];
    private List<Part> parts = List.of();
    private Optional<ZoneId> coveredZone = Optional.empty();
    private final BreachAlarm breachCheck = new BreachAlarm();
    private boolean breach;
    private boolean fire;

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
        boolean wasAlarming = alarming();
        pull(level);
        sample = Atmospheres.at(level, airPoint());
        keepPinned(level);

        long own = (long) AtmosServerConfig.get(AtmosServerConfig.CONTROLLER_DRAW) * AtmosTicks.PERIOD;
        powered = buffer.take(own);
        List<PoweredDevice> covered = covered(level);
        coverage();
        if (!powered) {
            demand = fullBill(own, covered);
            danger.reset();
            breachCheck.reset();
            breach = false;
            fire = false;
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
            boolean breached = breached(level);
            breach = breachCheck.check(
                    preset.pressure(),
                    sample.pressure(),
                    breached,
                    breached ? ambientPressure(level) : Double.NaN,
                    AtmosServerConfig.get(AtmosServerConfig.BREACH_DROP_KPA));
            if (breach) {
                worst = 2;
            }
            fire = fireAlarmed(covered);
            if (fire) {
                worst = 2;
            }
            danger.update(worst);
            mode = mode.after(sample.pressure(), preset);
        } else {
            breachCheck.reset();
        }

        // Orders first, then the bill: a part's draw follows its order (a vent draws nothing while
        // off), so paying first would let the tick a part is switched on run free.
        VentOrder vent = mode.vent(preset).from(getBlockPos());
        ScrubberOrder scrubber = mode.scrubber(preset).from(getBlockPos());
        for (PoweredDevice d : covered) {
            if (d instanceof VentControl v) {
                v.ventOrder(vent);
            }
            if (d instanceof ScrubberControl s) {
                s.scrubberOrder(scrubber);
            }
        }
        boolean[] supplied = feed(covered, own);
        parts = describe(covered, supplied);
        setStatus(level, ControllerStatus.of(danger.level()));
        if (!wasAlarming && alarming()) {
            // The firelocks it covers close now, not at their own steps up to an atmos tick later.
            FirelockLogic.alarmRaised(level, this);
        }
    }

    /** Whether its zone has an edge to the ambient, as of the last atmos tick. */
    private boolean breached(ServerLevel level) {
        if (sample.state() != AtmosphereState.ZONED && sample.state() != AtmosphereState.PAUSED) {
            return false;
        }
        return sample.zone()
                .flatMap(id -> Atmospheres.zone(level, id))
                .map(ZoneInfo::breached)
                .orElse(false);
    }

    /**
     * The dimension's ambient pressure, read at the top of the controller's column, which is open
     * to the ambient in almost every build; NaN if that cell isn't ambient. A dimension has one
     * ambient, so any ambient cell reads the same.
     */
    private double ambientPressure(ServerLevel level) {
        BlockPos top = new BlockPos(
                getBlockPos().getX(),
                level.getMaxBuildHeight() - 1,
                getBlockPos().getZ());
        AtmosphereSample at = Atmospheres.at(level, top);
        return at.state() == AtmosphereState.AMBIENT ? at.pressure() : Double.NaN;
    }

    /**
     * Takes FE from every block next to it that gives it (CONT-062). A central controller calls it
     * too, before it works out this controller's shortfall, so the blocks next to it pay first
     * (CONT-120).
     */
    void pull(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            if (buffer.space() <= 0) {
                return;
            }
            IEnergyStorage source = LoadedBlocks.capability(
                    level, Capabilities.EnergyStorage.BLOCK, getBlockPos().relative(dir), dir.getOpposite());
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

    /**
     * Called by the block when the controller is removed: unpins its zone (CONT-110) and leaves its
     * central controller (CONT-119).
     */
    void removedFromWorld(ServerLevel level) {
        pending = null;
        pinnedZone = null;
        ZonePins.unpin(level, owner());
        CentralClaims.of(level).release(getBlockPos());
    }

    /**
     * The parts in its zone and those linked to it, in the order they joined the schedule, from its
     * level's {@link ZoneIndex}. A link to a loaded block that is no longer a part or a firelock is
     * dropped; one in a chunk that isn't loaded in full is kept, and read without loading it.
     */
    private List<PoweredDevice> covered(ServerLevel level) {
        links.removeIf(p -> LoadedBlocks.chunk(level, p) != null && !linkable(LoadedBlocks.entity(level, p)));
        Optional<ZoneId> zone = sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED
                ? sample.zone()
                : Optional.empty();
        return ZoneIndex.covered(level, zone, links);
    }

    /** What can be linked with the multitool: a powered part (CONT-042) or a firelock (CONT-043). */
    public static boolean linkable(BlockEntity be) {
        return be instanceof PoweredDevice || (be instanceof FirelockLogic.Holder f && f.hasFirelock());
    }

    /** Records the zone the controller covers for firelocks (CONT-043): its own, as of this step. */
    private void coverage() {
        coveredZone = sample.state() == AtmosphereState.ZONED || sample.state() == AtmosphereState.PAUSED
                ? sample.zone()
                : Optional.empty();
    }

    /**
     * Whether the controller covers a firelock (CONT-043): the firelock is linked to it with the
     * multitool, or the cell on either side is in its zone. A link covers that firelock only, not
     * the room of a linked part. As of its last step.
     *
     * @param firelock the firelock's lower half
     * @param front the reading in front of its panel
     * @param back the reading behind it
     */
    public boolean covers(BlockPos firelock, AtmosphereSample front, AtmosphereSample back) {
        return links.contains(firelock) || inCoveredZone(front) || inCoveredZone(back);
    }

    private boolean inCoveredZone(AtmosphereSample side) {
        return coveredZone.isPresent()
                && side.zone().filter(coveredZone.get()::equals).isPresent();
    }

    /** The zone it covers, its own, as of its last step; empty in the open or before its first. */
    public Optional<ZoneId> zone() {
        return coveredZone;
    }

    /** Whether its last reading was a breach (CONT-043). */
    public boolean breach() {
        return breach;
    }

    /**
     * Whether a fire alarm it covers was triggered at its last reading (CONT-044): a covered
     * alarm, in its zone or linked, that is powered and saw a fire or heat at its own last step.
     */
    public boolean fire() {
        return fire;
    }

    private static boolean fireAlarmed(List<PoweredDevice> covered) {
        for (PoweredDevice d : covered) {
            if (d instanceof FireAlarmBlockEntity alarm && alarm.triggered()) {
                return true;
            }
        }
        return false;
    }

    /** Whether it is powered and at danger 2, so the firelocks it covers close (CONT-043). */
    public boolean alarming() {
        return powered && danger.level() >= 2;
    }

    /**
     * What a step costs with every covered part paid, at what each draws now: its own draw and theirs,
     * including those another provider paid for this time, which may fall to it next time.
     */
    private static long fullBill(long own, List<PoweredDevice> covered) {
        long cost = own;
        for (PoweredDevice d : covered) {
            cost += d.power().drawPerAtmosTick();
        }
        return cost;
    }

    /** Powers the covered parts from the buffer, in priority order (CONT-064, CONT-068). */
    private boolean[] feed(List<PoweredDevice> covered, long own) {
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
        demand = fullBill(own, covered);
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
            boolean in = zone.isPresent() && level != null && ZoneIndex.inZone(level, covered.get(i), zone.get());
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
            for (SoundEvent sound : soundsOf(state.getValue(AreaAirControllerBlock.STATUS), s)) {
                level.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS);
            }
            level.setBlock(getBlockPos(), state.setValue(AreaAirControllerBlock.STATUS, s), 3);
            level.updateNeighborsAt(AreaAirControllerBlock.wall(state, getBlockPos()), state.getBlock());
        }
    }

    /**
     * What a change of panel sounds like (CONT-127): its switch when power comes or goes, and its
     * alarm only when the danger rises, not while it stays or falls. The panel is saved with the
     * block, so a chunk coming back plays nothing.
     */
    private static List<SoundEvent> soundsOf(ControllerStatus was, ControllerStatus now) {
        List<SoundEvent> sounds = new ArrayList<>(2);
        if (was == ControllerStatus.UNPOWERED) {
            sounds.add(AtmosSounds.CONTROLLER_ON.get());
        } else if (now == ControllerStatus.UNPOWERED) {
            sounds.add(AtmosSounds.CONTROLLER_OFF.get());
        }
        if (now == ControllerStatus.DANGER2) {
            sounds.add(AtmosSounds.CONTROLLER_DANGER.get());
        } else if (now == ControllerStatus.DANGER1 && was != ControllerStatus.DANGER2) {
            sounds.add(AtmosSounds.CONTROLLER_WARNING.get());
        }
        return sounds;
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

    /**
     * What a step costs, as of its last: its own draw and that of every part it covered, at what each
     * drew then, whether or not it could pay and whether or not another provider paid for some
     * (CONT-120). A central controller tops its buffer up to this, so a part whose next charge comes
     * due finds it there. 0 before its first step.
     */
    public long demand() {
        return demand;
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
