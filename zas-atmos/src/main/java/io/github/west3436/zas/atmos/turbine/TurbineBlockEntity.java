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
import io.github.west3436.zas.atmos.power.WorkToFe;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The gas turbine's block entity, which runs the engine's shaft (CONT-161, CONT-162).
 *
 * <p><b>Each atmos tick</b>, while its {@linkplain TurbineControllerBlockEntity controller} is on,
 * links it and its compressor, the compressor is its partner and its chamber is a zone, the turbine
 * lets out {@code turbine_nozzle} of what would even out the chamber and the exhaust, the cell in front
 * of it ({@link TurbineMath#turbineMoles}). When that gas arrives, the shaft takes its expansion work
 * ({@link TurbineMath#expansionWork}), never past top speed, and the gas is that much cooler. It goes
 * into the exhaust at the next atmos tick. The {@linkplain CompressorBlockEntity compressor} feeds the
 * chamber, and the shaft pays for any compression it needs ({@link #payCompression}).
 *
 * <p>Whether on or not, the generator then takes its share of the shaft's energy as FE into its
 * controller's store ({@link TurbineMath#generatorJoules}), none with no controller, and friction
 * takes {@code turbine_friction} of it each second, as heat into the chamber. So with nothing burning,
 * the shaft only slows. The turbine has no FE store of its own and offers FE to no one: its controller
 * is the engine's one output (CONT-162).
 *
 * <p><b>Conservation.</b> Every change to a zone goes through the API's mutations, each port with at
 * most one outstanding (CONT-071, CONT-072), as {@link #SOURCE}. Gas on its way between a removal and
 * an addition is held here and saved, and released into its cell if the turbine is broken. The gas's
 * thermal energy, the shaft's energy, the FE made and the friction's heat always sum to the same
 * ({@link TurbineMath}).
 */
public class TurbineBlockEntity extends AtmosDevice {
    /** Who the gas turbine's changes to zones are made by: the turbine and its compressor. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "gas_turbine");

    private static final String CONTROLLER = "Controller";
    private static final String SHAFT = "Shaft";
    private static final String TO_EXHAUST = "ToExhaust";

    /** How far its running loop is heard, in blocks: as far as any device's. */
    private static final double LOOP_RANGE = 8;

    private final WorkToFe work = new WorkToFe();

    private final ZonePort chamberTakePort = new ZonePort(SOURCE);
    private final ZonePort exhaustPort = new ZonePort(SOURCE);
    private final CellWatch chamberWatch = new CellWatch();
    private final CellWatch exhaustWatch = new CellWatch();

    /** The controller it is linked to, as it was told; the controller's own link decides. */
    private @Nullable BlockPos controller;

    /** The shaft's energy, J. */
    private double shaft;

    /** Expanded gas on its way into the exhaust. */
    private GasParcel toExhaust = GasParcel.EMPTY;

    private boolean unloading;
    private TurbineStatus status = TurbineStatus.UNLINKED;

    /** The last atmos tick's readings. */
    private double chamberPressure;

    private double chamberTemperature;
    private double powerPerTick;
    private double lastTurbineWork;
    private double lastCompressorWork;
    private double compressorWorkSince;
    private double lastFriction;

    public TurbineBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TURBINE.get(), pos, state);
    }

    // ---- Where it works ----------------------------------------------------------------------

    private Direction facing() {
        return getBlockState().getValue(TurbinePartBlock.FACING);
    }

    /** Its partner compressor's block, by where it stands, or null. */
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

    /** The controller that links it, if that controller is loaded and still has it as its turbine. */
    public @Nullable TurbineControllerBlockEntity controller() {
        return TurbineControllerBlockEntity.linked(level, controller, worldPosition);
    }

    /** Where it was told its controller is. */
    public @Nullable BlockPos controllerPos() {
        return controller;
    }

    /** Told by a controller that links or unlinks it. */
    void setController(@Nullable BlockPos pos) {
        controller = pos == null ? null : pos.immutable();
        setChanged();
    }

    /** Its controller's position after a mover moved them (CONT-156). */
    public void rebase(UnaryOperator<BlockPos> rebase) {
        if (controller != null) {
            controller = rebase.apply(controller);
            setChanged();
        }
    }

    // ---- Its step ----------------------------------------------------------------------------

    @Override
    public boolean readyToStep() {
        return !chamberTakePort.busy() && !exhaustPort.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        chamberWatch.step();
        exhaustWatch.step();
        deliver(level);

        TurbineControllerBlockEntity c = controller();
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
        lastCompressorWork = compressorWorkSince;
        compressorWorkSince = 0;

        TurbineStatus next;
        if (c == null) {
            next = TurbineStatus.UNLINKED;
        } else if (!c.runs()) {
            next = TurbineStatus.OFF;
        } else if (comp == null || !comp.equals(c.compressorPos())) {
            next = TurbineStatus.NO_COMPRESSOR;
        } else if (!chamberOk || !exhaustOk) {
            next = TurbineStatus.CHAMBER_OPEN;
        } else {
            next = TurbineStatus.IDLE;
            letOut(level, chamber.mixture(), exhaust);
        }

        double jpf = AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE);
        double speed = speed();
        double topJoules =
                AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_OUTPUT) * 20.0 * AtmosTicks.SECONDS * jpf;
        double generated = 0;
        if (c != null) {
            generated = TurbineMath.generatorJoules(shaft, speed, topJoules, work.room(c.energy(), jpf));
            shaft -= generated;
            work.add(generated, c.energy(), jpf);
        }
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

    /** Puts expanded gas it holds into the exhaust. */
    private void deliver(ServerLevel level) {
        if (!toExhaust.isEmpty() && !exhaustPort.busy()) {
            GasParcel gas = toExhaust;
            toExhaust = GasParcel.EMPTY;
            exhaustPort.add(level, exhaustPoint(), gas, r -> keep(level, untaken(gas, r)));
        }
    }

    private static GasParcel untaken(GasParcel offered, MutationResult r) {
        return new GasParcel(ZonePort.untaken(offered, r), offered.temperature());
    }

    /** Holds gas to deliver next atmos tick, or, once it is broken, releases it now. */
    private void keep(ServerLevel level, GasParcel gas) {
        if (gas.isEmpty()) {
            return;
        }
        if (isRemoved()) {
            GasMutations.add(level, exhaustPoint(), gas, SOURCE);
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
        keep(level, w > 0 ? new GasParcel(gas.moles(), Math.max(0, gas.temperature() - w / c)) : gas);
    }

    /**
     * Pays compression work for its compressor out of the shaft (CONT-160): as much of it as the shaft
     * holds.
     *
     * @param joules what the compression costs, J
     * @return what the shaft paid, J, at most {@code joules}
     */
    public double payCompression(double joules) {
        double paid = Math.max(0, Math.min(Double.isFinite(joules) ? joules : 0, shaft));
        if (paid > 0) {
            shaft -= paid;
            compressorWorkSince += paid;
            setChanged();
        }
        return paid;
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

    /** Work the shaft paid for its compressor between its last two steps, J. */
    public double compressorWork() {
        return lastCompressorWork;
    }

    /** The friction's heat at its last atmos tick, J. */
    public double friction() {
        return lastFriction;
    }

    /** Gas it holds on its way into the exhaust. */
    public GasParcel held() {
        return toExhaust;
    }

    /** Work kept toward the next FE, J. */
    public double workCarry() {
        return work.carry();
    }

    /** Whether a level has a turbine at a position: for a controller's links. */
    static boolean isTurbine(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof TurbineBlock;
    }

    // ---- Its life ----------------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        unloading = false;
        chamberWatch.reset();
        exhaustWatch.reset();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    /** Broken, it releases the gas it holds into the exhaust (CONT-009). */
    @Override
    public void setRemoved() {
        if (!unloading
                && level instanceof ServerLevel server
                && !Relocations.isMoving(server, worldPosition)) { // a mover carries it (CONT-156)
            if (!toExhaust.isEmpty()) {
                GasMutations.add(server, exhaustPoint(), toExhaust, SOURCE);
            }
            toExhaust = GasParcel.EMPTY;
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) {
            tag.putLong(CONTROLLER, controller.asLong());
        }
        tag.putDouble(SHAFT, shaft);
        work.save(tag);
        if (!toExhaust.isEmpty()) {
            tag.put(TO_EXHAUST, GasStore.save(toExhaust));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = tag.contains(CONTROLLER) ? BlockPos.of(tag.getLong(CONTROLLER)) : null;
        double saved = tag.getDouble(SHAFT);
        shaft = Double.isFinite(saved) ? Math.max(0, Math.min(saved, topEnergy())) : 0;
        work.load(tag);
        toExhaust = tag.contains(TO_EXHAUST) ? GasStore.parcel(tag.getCompound(TO_EXHAUST)) : GasParcel.EMPTY;
    }
}
