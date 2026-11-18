package io.github.west3436.zas.atmos.teg;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.WorkToFe;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A TEG core's block entity (CONT-158, CONT-159). Once per atmos tick, while it is switched on, it
 * passes the gas of the circulator on each of its sides itself ({@link CirculatorBlockEntity#draw}),
 * so that it holds both streams at once, and exchanges heat between them ({@link TegMath#exchange}):
 * the hotter stream gives up Q, the colder takes Q − W, and W becomes FE in its store at {@code
 * joules_per_fe} J each ({@link WorkToFe}). Then each stream goes on to its circulator's outlet. No
 * gas is made or lost, and no zone is touched, so nothing goes through the API: pipe gas is the
 * content mod's own (architecture §11).
 *
 * <p>It makes nothing when its store is full, so the heat then just passes from one stream to the
 * other. Switched off, it passes nothing, and its circulators pass their gas on their own. New, it is
 * off. Every game tick it gives its FE to the blocks next to it that take FE ({@link WorkToFe#push});
 * an area air controller next to it takes it into its buffer, as from a generator.
 *
 * <p>For a redstone or computer reading: {@link #on()}, {@link #status()}, {@link #powerPerTick()}
 * (how hard) and {@link #energy()} (how full).
 */
public class TegBlockEntity extends AtmosDevice implements MenuProvider {
    private static final String ON = "On";
    private static final String ENERGY = "Energy";

    private final EnergyStore energy =
            new EnergyStore(() -> AtmosServerConfig.get(AtmosServerConfig.TEG_BUFFER), false, true, this::setChanged);
    private final WorkToFe work = new WorkToFe();

    private boolean on;
    private TegStatus status = TegStatus.OFF;

    /** The last atmos tick's readings: hot then cold side; inlet then outlet. */
    private final double[] temperature = new double[4];

    private final double[] pressure = new double[4];

    /** What it made over its last atmos tick, FE/t. */
    private double powerPerTick;

    /** Heat from the hot stream and to the cold one over its last atmos tick, J. */
    private double lastFromHot;

    private double lastToCold;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return dataValue(index);
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return TegMenu.DATA_COUNT;
        }
    };

    public TegBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TEG.get(), pos, state);
    }

    // ---- Its step ---------------------------------------------------------------------------

    /** The circulator on one of its sides, if one there serves it. */
    public @Nullable CirculatorBlockEntity circulator(int side) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        Direction d = TegBlock.sides(getBlockState())[side];
        BlockPos at = worldPosition.relative(d);
        BlockState state = LoadedBlocks.state(server, at);
        if (state == null
                || !CirculatorBlock.servesToward(state, d.getOpposite())
                || !(server.getBlockEntity(at) instanceof CirculatorBlockEntity c)
                || c.isRemoved()) {
            return null;
        }
        return TegBlock.coreOf(server, at, state) == this ? c : null;
    }

    /** Whether it passes its circulators' gas itself this atmos tick: on, and stepping. */
    public boolean drives() {
        return on && stepping();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        CirculatorBlockEntity left = circulator(0);
        CirculatorBlockEntity right = circulator(1);
        if (!on) {
            finish(level, TegStatus.OFF, 0);
            return;
        }
        GasParcel a = left == null ? GasParcel.EMPTY : left.draw();
        GasParcel b = right == null ? GasParcel.EMPTY : right.draw();
        double[] inT = {temperatureOf(a, left, true), temperatureOf(b, right, true)};
        // The hot side is the one whose stream, or inlet if none passes, is the hotter.
        boolean leftHot = inT[0] >= inT[1];
        GasParcel hot = leftHot ? a : b;
        GasParcel cold = leftHot ? b : a;
        double hotC = GasProperties.heatCapacity(hot);
        double coldC = GasProperties.heatCapacity(cold);
        double jpf = AtmosServerConfig.get(AtmosServerConfig.JOULES_PER_FE);
        TegMath.Exchange ex = TegMath.exchange(
                hotC,
                hot.temperature(),
                coldC,
                cold.temperature(),
                AtmosServerConfig.get(AtmosServerConfig.TEG_CARNOT_FRACTION),
                work.room(energy, jpf));
        if (ex.fromHot() > 0) {
            hot = new GasParcel(hot.moles(), Math.max(0, hot.temperature() - ex.fromHot() / hotC));
            cold = new GasParcel(cold.moles(), cold.temperature() + ex.toCold() / coldC);
        }
        int fe = work.add(ex.work(), energy, jpf);
        lastFromHot = ex.fromHot();
        lastToCold = ex.toCold();
        GasParcel outA = leftHot ? hot : cold;
        GasParcel outB = leftHot ? cold : hot;
        if (left != null) {
            left.pass(outA);
            left.drivenBy(a.totalMoles());
        }
        if (right != null) {
            right.pass(outB);
            right.drivenBy(b.totalMoles());
        }
        CirculatorBlockEntity hotSide = leftHot ? left : right;
        CirculatorBlockEntity coldSide = leftHot ? right : left;
        read(0, hotSide, leftHot ? outA : outB, leftHot ? inT[0] : inT[1]);
        read(1, coldSide, leftHot ? outB : outA, leftHot ? inT[1] : inT[0]);

        TegStatus next;
        if (left == null || right == null) {
            next = TegStatus.NO_CIRCULATORS;
        } else if (a.isEmpty() || b.isEmpty()) {
            next = TegStatus.NO_FLOW;
        } else if (fe > 0 || ex.work() > 0) {
            next = TegStatus.RUNNING;
        } else if (energy.space() <= 0) {
            next = TegStatus.FULL;
        } else {
            next = TegStatus.NO_DIFFERENCE;
        }
        finish(level, next, ex.work() / jpf / (AtmosTicks.SECONDS * 20));
    }

    private static double temperatureOf(GasParcel stream, @Nullable CirculatorBlockEntity c, boolean inlet) {
        if (!stream.isEmpty()) {
            return stream.temperature();
        }
        if (c == null) {
            return 0;
        }
        GasStore gas = (inlet ? c.inlet() : c.outlet()).network().gas();
        return gas.isEmpty() ? 0 : gas.temperature();
    }

    /** Records a side's readings: its stream in and out, and its two networks' pressures. */
    private void read(int side, @Nullable CirculatorBlockEntity c, GasParcel out, double inT) {
        temperature[2 * side] = inT;
        temperature[2 * side + 1] = out.isEmpty() ? temperatureOf(out, c, false) : out.temperature();
        pressure[2 * side] = c == null ? 0 : c.inlet().network().gas().pressure();
        pressure[2 * side + 1] = c == null ? 0 : c.outlet().network().gas().pressure();
    }

    private void finish(ServerLevel level, TegStatus next, double fePerTick) {
        status = next;
        powerPerTick = fePerTick;
        if (next == TegStatus.OFF) {
            Arrays.fill(temperature, 0);
            Arrays.fill(pressure, 0);
            lastFromHot = 0;
            lastToCold = 0;
        }
        if (fePerTick > 0) {
            reportRunning();
        } else {
            reportStopped();
        }
        int lit = TegMath.segments(fePerTick);
        BlockState state = getBlockState();
        if (state.getValue(TegBlock.OUTPUT) != lit) {
            level.setBlock(worldPosition, state.setValue(TegBlock.OUTPUT, lit), Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    /** One game tick: gives its FE to the blocks next to it that take it. */
    void serverTick() {
        if (level instanceof ServerLevel server && energy.getEnergyStored() > 0) {
            WorkToFe.push(server, worldPosition, energy);
        }
    }

    // ---- Its state --------------------------------------------------------------------------

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /** Switches it on or off, with a click. Server side. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
            if (level != null) {
                AtmosSounds.play(level, worldPosition, on ? AtmosSounds.TEG_ON : AtmosSounds.TEG_OFF);
            }
            if (!on && level instanceof ServerLevel server && !isRemoved()) {
                finish(server, TegStatus.OFF, 0);
                finishActivity(server);
            }
        }
    }

    /** What it did at its last atmos tick. */
    public TegStatus status() {
        return status;
    }

    /** What it made over its last atmos tick, FE/t. */
    public double powerPerTick() {
        return powerPerTick;
    }

    /** A reading of its last atmos tick, K: the hot (0) or cold (1) stream, in or out. */
    public double temperature(int side, boolean in) {
        return temperature[2 * side + (in ? 0 : 1)];
    }

    /** A reading of its last atmos tick, kPa: the hot (0) or cold (1) side's inlet or outlet network. */
    public double pressure(int side, boolean in) {
        return pressure[2 * side + (in ? 0 : 1)];
    }

    /** The heat it took from the hot stream at its last atmos tick, J. */
    public double heatFromHot() {
        return lastFromHot;
    }

    /** The heat it gave the cold stream at its last atmos tick, J. */
    public double heatToCold() {
        return lastToCold;
    }

    /** Its FE store. */
    public EnergyStore energy() {
        return energy;
    }

    /** Work kept toward the next FE, J. */
    public double workCarry() {
        return work.carry();
    }

    /** How many circulators serve it: 0 to 2. */
    public int circulatorCount() {
        return (circulator(0) == null ? 0 : 1) + (circulator(1) == null ? 0 : 1);
    }

    // ---- Its window ---------------------------------------------------------------------------

    private int dataValue(int index) {
        return switch (index) {
            case TegMenu.DATA_ON -> on ? 1 : 0;
            case TegMenu.DATA_STATUS -> status.ordinal();
            case TegMenu.DATA_POWER -> TegMenu.shortOf(Math.round(powerPerTick));
            case TegMenu.DATA_STORED_HIGH -> energy.getEnergyStored() >>> 16;
            case TegMenu.DATA_STORED_LOW -> energy.getEnergyStored() & 0xFFFF;
            case TegMenu.DATA_CAPACITY_HIGH -> energy.getMaxEnergyStored() >>> 16;
            case TegMenu.DATA_CAPACITY_LOW -> energy.getMaxEnergyStored() & 0xFFFF;
            default -> {
                int i = index - TegMenu.DATA_TEMPERATURE;
                if (i >= 0 && i < 4) {
                    yield TegMenu.shortOf(Math.round(temperature[i]));
                }
                int p = index - TegMenu.DATA_PRESSURE;
                yield p >= 0 && p < 4 ? TegMenu.shortOf(Math.round(pressure[p])) : 0;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.teg");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TegMenu(id, this, data);
    }

    // ---- Saving -------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putInt(ENERGY, energy.getEnergyStored());
        work.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        energy.set(tag.getInt(ENERGY));
        work.load(tag);
    }
}
