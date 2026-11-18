package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The compressor's block entity (CONT-160): a 200 L buffer in the pipe network on its back, which it
 * feeds into the combustion chamber in front of it.
 *
 * <p><b>Each atmos tick</b>, while its {@linkplain TurbineControllerBlockEntity controller} is on and
 * links it and its partner turbine, a fuel and an oxidizer are set that burn together, and its chamber
 * is a zone, it releases from its buffer what {@link TurbineMath#release} says, each gas on its own: the
 * fuel at the controller's burn rate and the oxidizer the chamber lacks, at the ratio the engine's
 * reactions burn them at ({@link TurbineFuels#ratio}). A chamber that isn't burning is primed: fed
 * until a spark would light it, then left to wait for one. With no controller, or nothing burnable in
 * its buffer, it releases nothing.
 *
 * <p><b>Energy.</b> Gas from a buffer above the chamber's pressure goes in as through a valve, at its
 * own temperature: no work is done or gained. Gas pushed into a chamber above the buffer's pressure is
 * compressed, and the turbine's shaft pays for it ({@link TurbineBlockEntity#payCompression},
 * {@link TurbineMath#compressionWork}), the gas warming by what was paid. What the shaft can't pay
 * for isn't released.
 *
 * <p><b>Conservation.</b> The gas leaves its network as it goes, and goes into the chamber through the
 * API as the turbine's {@link TurbineBlockEntity#SOURCE}; what the chamber doesn't take comes back to
 * the network.
 */
public class CompressorBlockEntity extends PipeNodeEntity {
    private static final String CONTROLLER = "Controller";

    private final ZonePort chamberPort = new ZonePort(TurbineBlockEntity.SOURCE);

    /** The controller it is linked to, as it was told; the controller's own link decides. */
    private @Nullable BlockPos controller;

    /** What it released at its last atmos tick, mol. */
    private double lastFuel;

    private double lastOxidizer;

    public CompressorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.COMPRESSOR.get(), pos, state, PipeVolumes.PORT);
    }

    /** The chamber's cell it feeds: the one in front of it. */
    public Vec3 chamberPoint() {
        return Vec3.atCenterOf(worldPosition.relative(getBlockState().getValue(TurbinePartBlock.FACING)));
    }

    /** The controller that links it, if that controller is loaded and still has it as its compressor. */
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

    /** Its buffer: its network's gas, of which it holds its share by volume. */
    public GasMixture buffer() {
        return network().gas().mixture();
    }

    /** The fuel it released at its last atmos tick, mol. */
    public double lastFuel() {
        return lastFuel;
    }

    /** The oxidizer it released at its last atmos tick, mol. */
    public double lastOxidizer() {
        return lastOxidizer;
    }

    @Override
    public boolean readyToStep() {
        return !chamberPort.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        lastFuel = 0;
        lastOxidizer = 0;
        TurbineControllerBlockEntity c = controller();
        if (c == null || !c.on()) {
            reportStopped();
            return;
        }
        TurbineBlockEntity turbine = c.turbine();
        ResourceLocation fuel = c.fuel();
        ResourceLocation oxidizer = c.oxidizer();
        double ratio = c.ratio();
        if (turbine == null
                || !worldPosition.equals(turbine.compressor())
                || fuel == null
                || oxidizer == null
                || !Double.isFinite(ratio)) {
            reportStopped();
            return;
        }
        AtmosphereSample chamber = Atmospheres.at(level, chamberPoint());
        if (!cellWatch().trust(chamber) || chamber.state() != AtmosphereState.ZONED || chamber.zone().isEmpty()) {
            reportStopped();
            return;
        }
        GasMixture room = chamber.mixture();
        GasStore buffer = network().gas();
        boolean burning = Fires.isBurning(level, chamber.zone().get());
        boolean primed = !burning && TurbineFuels.wouldIgnite(room, fuel);
        double[] want = TurbineMath.release(
                c.burnRate() * AtmosTicks.SECONDS,
                ratio,
                room.moles(fuel),
                room.moles(oxidizer),
                room.totalMoles(),
                AtmosServerConfig.get(AtmosServerConfig.TURBINE_OXIDIZER_FRACTION),
                primed,
                buffer.moles(fuel),
                buffer.moles(oxidizer));
        double f = want[0];
        double o = want[1];
        if (f + o < PipeBlockEntity.MIN_FLOW) {
            reportStopped();
            return;
        }

        // Pushing into a chamber above the buffer's pressure costs the shaft work; take only what it can pay for.
        double from = buffer.pressure();
        double to = room.pressure();
        double eff = AtmosServerConfig.get(AtmosServerConfig.COMPRESSOR_EFFICIENCY);
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        moles.put(fuel, f);
        moles.put(oxidizer, o);
        double w = TurbineMath.compressionWork(
                GasProperties.heatCapacity(moles), f + o, buffer.temperature(), from, to, eff);
        if (w > 0) {
            double can = turbine.shaftEnergy();
            if (w > can) {
                double k = can / w; // the work is in proportion to the moles
                f *= k;
                o *= k;
                if (f + o < PipeBlockEntity.MIN_FLOW) {
                    reportStopped();
                    return;
                }
                moles.put(fuel, f);
                moles.put(oxidizer, o);
            }
        }
        moles.values().removeIf(x -> !(x > GasStore.EPSILON));
        GasParcel gas = buffer.take(moles);
        if (gas.isEmpty()) {
            reportStopped();
            return;
        }
        double heat = GasProperties.heatCapacity(gas);
        double paid = 0;
        if (to > from) {
            double cost = TurbineMath.compressionWork(heat, gas.totalMoles(), gas.temperature(), from, to, eff);
            paid = turbine.payCompression(cost);
        }
        GasParcel sent = paid > 0 ? new GasParcel(gas.moles(), gas.temperature() + paid / heat) : gas;
        lastFuel = sent.moles(fuel);
        lastOxidizer = sent.moles(oxidizer);
        chamberPort.add(
                level,
                chamberPoint(),
                sent,
                r -> deliver(new GasParcel(ZonePort.untaken(sent, r), sent.temperature())));
        reportMoved(sent.totalMoles());
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) {
            tag.putLong(CONTROLLER, controller.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = tag.contains(CONTROLLER) ? BlockPos.of(tag.getLong(CONTROLLER)) : null;
    }
}
