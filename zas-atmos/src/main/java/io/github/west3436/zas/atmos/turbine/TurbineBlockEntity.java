package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.WorkToFe;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The gas turbine's block entity, which does the work of the pair (CONT-160 to CONT-164).
 *
 * <p><b>Each atmos tick</b>, while it is switched on and paired with a compressor and its chamber is
 * a zone:
 * <ul>
 *   <li>The turbine lets out {@code turbine_nozzle} of what would even out the chamber and the
 *       exhaust, the cell in front of it ({@link TurbineMath#turbineMoles}). When that gas arrives,
 *       the shaft takes its expansion work ({@link TurbineMath#expansionWork}), never past top
 *       speed, and the gas is that much cooler. It goes into the exhaust at the next atmos tick.
 *   <li>The compressor draws from its intake, the cell behind it, in proportion to the shaft's speed
 *       ({@link TurbineMath#compressorMoles}), no more than the shaft can pay to compress. When that
 *       gas arrives, the shaft pays its compression work ({@link TurbineMath#compressionWork}) and the
 *       gas is that much warmer. It goes into the chamber at the next atmos tick.
 * </ul>
 * Whether on or not, the generator then takes its share of the shaft's energy as FE ({@link
 * TurbineMath#generatorJoules}), and friction takes {@code turbine_friction} of it each second, as
 * heat into the chamber. So with nothing burning, the shaft only slows.
 *
 * <p><b>Conservation.</b> Every change to a zone goes through the API's mutations, each port with at
 * most one outstanding (CONT-071, CONT-072), as {@link #SOURCE}. Gas on its way between a removal and
 * an addition is held here and saved, and released into its cells if the turbine is broken. The
 * gas's thermal energy, the shaft's energy, the FE made and the friction's heat always sum to the same
 * ({@link TurbineMath}).
 *
 * <p>For a redstone or computer reading: {@link #on()}, {@link #status()}, {@link #rpm()} (how hard)
 * and {@link #energy()} (how full).
 */
public class TurbineBlockEntity extends AtmosDevice implements MenuProvider {
    /** Who the turbine's changes to zones are made by. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "gas_turbine");

    private static final String ON = "On";
    private static final String SHAFT = "Shaft";
    private static final String ENERGY = "Energy";
    private static final String TO_CHAMBER = "ToChamber";
    private static final String TO_EXHAUST = "ToExhaust";

    /** How far its running loop is heard, in blocks: as far as any device's. */
    private static final double LOOP_RANGE = 8;

    private final EnergyStore energy = new EnergyStore(
            () -> AtmosServerConfig.get(AtmosServerConfig.TURBINE_BUFFER), false, true, this::setChanged);
    private final WorkToFe work = new WorkToFe();

    private final ZonePort intakePort = new ZonePort(SOURCE);
    private final ZonePort chamberAddPort = new ZonePort(SOURCE);
    private final ZonePort chamberTakePort = new ZonePort(SOURCE);
    private final ZonePort exhaustPort = new ZonePort(SOURCE);
    private final CellWatch intakeWatch = new CellWatch();
    private final CellWatch chamberWatch = new CellWatch();
    private final CellWatch exhaustWatch = new CellWatch();

    private boolean on;

    /** The shaft's energy, J. */
    private double shaft;

    /** Compression work expected for gas still on its way from the intake, J. */
    private double reserved;

    /** Compressed gas on its way into the chamber. */
    private GasParcel toChamber = GasParcel.EMPTY;

    /** Expanded gas on its way into the exhaust. */
    private GasParcel toExhaust = GasParcel.EMPTY;

    private boolean unloading;
    private TurbineStatus status = TurbineStatus.OFF;

    /** The last atmos tick's readings. */
    private double chamberPressure;

    private double chamberTemperature;
    private double powerPerTick;
    private double lastTurbineWork;
    private double lastCompressorWork;
    private double lastFriction;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return dataValue(index);
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return TurbineMenu.DATA_COUNT;
        }
    };

    public TurbineBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TURBINE.get(), pos, state);
    }

    // ---- Where it works ----------------------------------------------------------------------

    private Direction facing() {
        return getBlockState().getValue(TurbinePartBlock.FACING);
    }

    /** Its paired compressor's block, or null. */
    public @Nullable BlockPos compressor() {
        return level == null ? null : TurbinePartBlock.partner(level, worldPosition, getBlockState());
    }

    /** The chamber's cell it draws from: the one behind it. */
    public Vec3 chamberPoint() {
        return Vec3.atCenterOf(worldPosition.relative(facing().getOpposite()));
    }

    /** The exhaust's cell: the one in front of it. */
    public Vec3 exhaustPoint() {
        return Vec3.atCenterOf(worldPosition.relative(facing()));
    }

    // ---- Its step ----------------------------------------------------------------------------

    @Override
    public boolean readyToStep() {
        return !intakePort.busy() && !chamberAddPort.busy() && !chamberTakePort.busy() && !exhaustPort.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        intakeWatch.step();
        chamberWatch.step();
        exhaustWatch.step();
        deliver(level);

        BlockPos comp = compressor();
        AtmosphereSample chamber = Atmospheres.at(level, chamberPoint());
        AtmosphereSample exhaust = Atmospheres.at(level, exhaustPoint());
        boolean chamberOk = chamberWatch.trust(chamber) && chamber.state() == AtmosphereState.ZONED;
        boolean exhaustOk = exhaustWatch.trust(exhaust);
        chamberPressure =
                chamber.state() == AtmosphereState.ZONED ? chamber.mixture().pressure() : 0;
        chamberTemperature =
                chamber.state() == AtmosphereState.ZONED ? chamber.mixture().temperature() : 0;
        lastTurbineWork = 0;
        lastCompressorWork = 0;

        TurbineStatus next;
        if (!on) {
            next = TurbineStatus.OFF;
        } else if (comp == null) {
            next = TurbineStatus.NO_COMPRESSOR;
        } else if (!chamberOk || !exhaustOk) {
            next = TurbineStatus.CHAMBER_OPEN;
        } else {
            next = TurbineStatus.IDLE;
            letOut(level, chamber.mixture(), exhaust);
            Vec3 intakePoint = Vec3.atCenterOf(comp.relative(facing().getOpposite()));
            AtmosphereSample intake = Atmospheres.at(level, intakePoint);
            if (intakeWatch.trust(intake)) {
                drawIn(level, intakePoint, intake, chamber.mixture().pressure());
            }
        }

        double jpf = AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE);
        double topEnergy = topEnergy();
        double speed = Math.sqrt(Math.min(1, shaft / topEnergy));
        double topJoules =
                AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_OUTPUT) * 20.0 * AtmosTicks.SECONDS * jpf;
        double generated = TurbineMath.generatorJoules(shaft, speed, topJoules, work.room(energy, jpf));
        shaft -= generated;
        work.add(generated, energy, jpf);
        powerPerTick = generated / jpf / (20.0 * AtmosTicks.SECONDS);

        double friction =
                shaft * Math.min(1, AtmosServerConfig.get(AtmosServerConfig.TURBINE_FRICTION) * AtmosTicks.SECONDS);
        lastFriction = friction;
        if (friction > 0) {
            shaft -= friction;
            HeatMutations.add(level, chamberPoint(), friction, SOURCE);
        }
        if (shaft < 1e-9) {
            shaft = 0;
        }

        int spin = TurbineMath.spin(speed());
        if (spin > 0 && next == TurbineStatus.IDLE) {
            next = TurbineStatus.RUNNING;
        }
        status = next;
        show(level, comp, spin);
        if (spin > 0) {
            reportRunning();
        } else {
            reportStopped();
        }
        setChanged();
    }

    /** Puts gas it holds into its cells: compressed gas into the chamber, expanded gas into the exhaust. */
    private void deliver(ServerLevel level) {
        if (!toChamber.isEmpty() && !chamberAddPort.busy()) {
            GasParcel gas = toChamber;
            toChamber = GasParcel.EMPTY;
            chamberAddPort.add(level, chamberPoint(), gas, r -> keep(level, true, untaken(gas, r)));
        }
        if (!toExhaust.isEmpty() && !exhaustPort.busy()) {
            GasParcel gas = toExhaust;
            toExhaust = GasParcel.EMPTY;
            exhaustPort.add(level, exhaustPoint(), gas, r -> keep(level, false, untaken(gas, r)));
        }
    }

    private static GasParcel untaken(GasParcel offered, MutationResult r) {
        return new GasParcel(ZonePort.untaken(offered, r), offered.temperature());
    }

    /** Holds gas to deliver next atmos tick, or, once it is broken, releases it now. */
    private void keep(ServerLevel level, boolean chamber, GasParcel gas) {
        if (gas.isEmpty()) {
            return;
        }
        if (isRemoved()) {
            GasMutations.add(level, chamber ? chamberPoint() : exhaustPoint(), gas, SOURCE);
        } else if (chamber) {
            toChamber = GasProperties.mix(toChamber, gas);
        } else {
            toExhaust = GasProperties.mix(toExhaust, gas);
        }
        setChanged();
    }

    /** The turbine's half: lets chamber gas out, and takes its work when it arrives. */
    private void letOut(ServerLevel level, GasMixture chamber, AtmosphereSample exhaust) {
        double exhaustP = exhaust.mixture().pressure();
        double exhaustV = exhaust.state() == AtmosphereState.AMBIENT
                ? Double.POSITIVE_INFINITY
                : exhaust.mixture().volume();
        double n = TurbineMath.turbineMoles(
                chamber.totalMoles(),
                chamber.temperature(),
                chamber.volume(),
                chamber.pressure(),
                exhaustV,
                exhaustP,
                AtmosServerConfig.get(AtmosServerConfig.TURBINE_NOZZLE) * AtmosTicks.SECONDS,
                AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_FLOW) * AtmosTicks.SECONDS);
        if (n < PipeBlockEntity.MIN_FLOW) {
            return;
        }
        double from = chamber.pressure();
        chamberTakePort.remove(
                level, chamberPoint(), share(chamber, n), r -> expanded(level, r.moved(), from, exhaustP));
        reportMoved(n);
    }

    /** Gas let out of the chamber has arrived: the shaft takes its work, and it waits for the exhaust. */
    private void expanded(ServerLevel level, GasParcel gas, double from, double to) {
        if (gas.isEmpty()) {
            return;
        }
        double c = GasProperties.heatCapacity(gas);
        double w = 0;
        if (!isRemoved()) {
            w = TurbineMath.expansionWork(
                    c,
                    gas.totalMoles(),
                    gas.temperature(),
                    from,
                    to,
                    AtmosServerConfig.get(AtmosServerConfig.TURBINE_EFFICIENCY));
            w = Math.max(0, Math.min(w, topEnergy() - shaft));
            shaft += w;
            lastTurbineWork += w;
        }
        keep(level, false, w > 0 ? new GasParcel(gas.moles(), Math.max(0, gas.temperature() - w / c)) : gas);
    }

    /** The compressor's half: draws intake gas, as much as the shaft can pay to compress. */
    private void drawIn(ServerLevel level, Vec3 point, AtmosphereSample intake, double chamberP) {
        GasMixture air = intake.mixture();
        double n = TurbineMath.compressorMoles(
                speed(),
                AtmosServerConfig.get(AtmosServerConfig.COMPRESSOR_DRAW) * AtmosTicks.SECONDS,
                air.pressure(),
                air.temperature(),
                intake.state() == AtmosphereState.AMBIENT ? Double.POSITIVE_INFINITY : air.totalMoles());
        if (n < PipeBlockEntity.MIN_FLOW || air.totalMoles() <= 0) {
            return;
        }
        double c = GasProperties.heatCapacity(air.moles()) * n / air.totalMoles();
        double eff = AtmosServerConfig.get(AtmosServerConfig.COMPRESSOR_EFFICIENCY);
        double w = TurbineMath.compressionWork(c, n, air.temperature(), air.pressure(), chamberP, eff);
        double free = Math.max(0, shaft - reserved);
        if (w > free) {
            n *= free / w; // the work is in proportion to the moles
            w = free;
        }
        if (n < PipeBlockEntity.MIN_FLOW) {
            return;
        }
        double expected = w;
        reserved += expected;
        double from = air.pressure();
        intakePort.remove(level, point, share(air, n), r -> compressed(level, r.moved(), from, chamberP, expected));
        reportMoved(n);
    }

    /** Intake gas has arrived: the shaft pays to compress it, and it waits for the chamber. */
    private void compressed(ServerLevel level, GasParcel gas, double from, double to, double expected) {
        reserved = Math.max(0, reserved - expected);
        if (gas.isEmpty()) {
            return;
        }
        double c = GasProperties.heatCapacity(gas);
        double w = 0;
        if (!isRemoved()) {
            w = TurbineMath.compressionWork(
                    c,
                    gas.totalMoles(),
                    gas.temperature(),
                    from,
                    to,
                    AtmosServerConfig.get(AtmosServerConfig.COMPRESSOR_EFFICIENCY));
            w = Math.max(0, Math.min(w, shaft));
            shaft -= w;
            lastCompressorWork += w;
        }
        keep(level, true, w > 0 ? new GasParcel(gas.moles(), gas.temperature() + w / c) : gas);
    }

    /** A mixture's gases in proportion, n moles in all. */
    private static Map<ResourceLocation, Double> share(GasMixture m, double n) {
        Map<ResourceLocation, Double> out = new TreeMap<>();
        double total = m.totalMoles();
        m.moles().forEach((gas, moles) -> {
            double x = moles * n / total;
            if (x > GasStore.EPSILON) {
                out.put(gas, x);
            }
        });
        return out;
    }

    /** Sets its blades' and its compressor's to the shaft's speed, telling clients only. */
    private void show(ServerLevel level, @Nullable BlockPos comp, int spin) {
        BlockState state = getBlockState();
        if (state.getValue(TurbinePartBlock.SPIN) != spin) {
            level.setBlock(worldPosition, state.setValue(TurbinePartBlock.SPIN, spin), Block.UPDATE_CLIENTS);
        }
        if (comp != null) {
            BlockState c = LoadedBlocks.state(level, comp);
            if (c != null && c.getBlock() instanceof CompressorBlock && c.getValue(TurbinePartBlock.SPIN) != spin) {
                level.setBlock(comp, c.setValue(TurbinePartBlock.SPIN, spin), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** One game tick: gives its FE to the blocks next to it that take it. */
    void serverTick() {
        if (level instanceof ServerLevel server && energy.getEnergyStored() > 0) {
            WorkToFe.push(server, worldPosition, energy);
        }
    }

    /**
     * One game tick on a client: its running loop while its blades turn, higher the faster they do
     * (CONT-164). A block entity that has unloaded or been broken has no ticker, so its loop fades.
     */
    static void clientTick(BlockState state, BlockPos pos) {
        int spin = state.getValue(TurbinePartBlock.SPIN);
        if (spin > 0) {
            ClientSounds.loop(new LoopRequest(
                    pos.immutable(),
                    AtmosSounds.TURBINE_RUN.get(),
                    SoundSource.BLOCKS,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    DeviceActivity.volume(DeviceActivity.WORKING),
                    0.55f + 0.25f * spin,
                    LOOP_RANGE));
        }
    }

    // ---- Its state ---------------------------------------------------------------------------

    private static double topEnergy() {
        return AtmosServerConfig.get(AtmosServerConfig.TURBINE_SPIN_ENERGY_KJ) * 1000;
    }

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /**
     * Switches the pair on or off, with a click. Off, it lets nothing out and its compressor draws
     * nothing, and its shaft runs down. Server side.
     */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
            if (level != null) {
                AtmosSounds.play(level, worldPosition, on ? AtmosSounds.TURBINE_ON : AtmosSounds.TURBINE_OFF);
            }
        }
    }

    /** What it did at its last atmos tick. */
    public TurbineStatus status() {
        return status;
    }

    /** The shaft's energy, J. */
    public double shaftEnergy() {
        return shaft;
    }

    /** Sets the shaft's energy, clamped to top speed's: for tests. */
    public void setShaftEnergy(double joules) {
        shaft = Math.max(0, Math.min(Double.isFinite(joules) ? joules : 0, topEnergy()));
        setChanged();
    }

    /** The shaft's share of its top speed, 0 to 1. */
    public double speed() {
        return Math.sqrt(Math.max(0, Math.min(1, shaft / topEnergy())));
    }

    /** The shaft's speed, rpm. */
    public double rpm() {
        return TurbineMath.rpm(shaft, topEnergy(), AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM));
    }

    /** What its generator made over its last atmos tick, FE/t. */
    public double powerPerTick() {
        return powerPerTick;
    }

    /** The chamber's pressure at its last atmos tick, kPa; 0 if it wasn't a zone. */
    public double chamberPressure() {
        return chamberPressure;
    }

    /** The chamber's temperature at its last atmos tick, K; 0 if it wasn't a zone. */
    public double chamberTemperature() {
        return chamberTemperature;
    }

    /** Work the turbine took from gas that arrived since its last step began, J. */
    public double turbineWork() {
        return lastTurbineWork;
    }

    /** Work the compressor paid for gas that arrived since its last step began, J. */
    public double compressorWork() {
        return lastCompressorWork;
    }

    /** The friction's heat at its last atmos tick, J. */
    public double friction() {
        return lastFriction;
    }

    /** Gas it holds on its way into the chamber and the exhaust, together. */
    public GasParcel held() {
        return GasProperties.mix(toChamber, toExhaust);
    }

    /** Work kept toward the next FE, J. */
    public double workCarry() {
        return work.carry();
    }

    /** Its FE store. */
    public EnergyStore energy() {
        return energy;
    }

    // ---- Its window --------------------------------------------------------------------------

    private int dataValue(int index) {
        return switch (index) {
            case TurbineMenu.DATA_ON -> on ? 1 : 0;
            case TurbineMenu.DATA_STATUS -> status.ordinal();
            case TurbineMenu.DATA_RPM -> TurbineMenu.shortOf(Math.round(rpm()));
            case TurbineMenu.DATA_CHAMBER_PRESSURE -> TurbineMenu.shortOf(Math.round(chamberPressure));
            case TurbineMenu.DATA_CHAMBER_TEMPERATURE -> TurbineMenu.shortOf(Math.round(chamberTemperature));
            case TurbineMenu.DATA_POWER -> TurbineMenu.shortOf(Math.round(powerPerTick));
            case TurbineMenu.DATA_STORED_HIGH -> energy.getEnergyStored() >>> 16;
            case TurbineMenu.DATA_STORED_LOW -> energy.getEnergyStored() & 0xFFFF;
            case TurbineMenu.DATA_CAPACITY_HIGH -> energy.getMaxEnergyStored() >>> 16;
            case TurbineMenu.DATA_CAPACITY_LOW -> energy.getMaxEnergyStored() & 0xFFFF;
            case TurbineMenu.DATA_TOP_RPM ->
                TurbineMenu.shortOf(AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM));
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.turbine");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TurbineMenu(id, this, data);
    }

    // ---- Its life ----------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        unloading = false;
        intakeWatch.reset();
        chamberWatch.reset();
        exhaustWatch.reset();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    /** Broken, it releases the gas it holds into the chamber and the exhaust (CONT-009). */
    @Override
    public void setRemoved() {
        if (!unloading
                && level instanceof ServerLevel server
                && !Relocations.isMoving(server, worldPosition)) { // a mover carries it (CONT-156)
            if (!toChamber.isEmpty()) {
                GasMutations.add(server, chamberPoint(), toChamber, SOURCE);
            }
            if (!toExhaust.isEmpty()) {
                GasMutations.add(server, exhaustPoint(), toExhaust, SOURCE);
            }
            toChamber = GasParcel.EMPTY;
            toExhaust = GasParcel.EMPTY;
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putDouble(SHAFT, shaft);
        tag.putInt(ENERGY, energy.getEnergyStored());
        work.save(tag);
        if (!toChamber.isEmpty()) {
            tag.put(TO_CHAMBER, GasStore.save(toChamber));
        }
        if (!toExhaust.isEmpty()) {
            tag.put(TO_EXHAUST, GasStore.save(toExhaust));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        double saved = tag.getDouble(SHAFT);
        shaft = Double.isFinite(saved) ? Math.max(0, Math.min(saved, topEnergy())) : 0;
        energy.set(tag.getInt(ENERGY));
        work.load(tag);
        toChamber = tag.contains(TO_CHAMBER) ? GasStore.parcel(tag.getCompound(TO_CHAMBER)) : GasParcel.EMPTY;
        toExhaust = tag.contains(TO_EXHAUST) ? GasStore.parcel(tag.getCompound(TO_EXHAUST)) : GasParcel.EMPTY;
    }
}
