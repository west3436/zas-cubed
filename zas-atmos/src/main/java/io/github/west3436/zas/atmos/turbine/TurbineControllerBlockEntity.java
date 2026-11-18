package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.WorkToFe;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A gas turbine controller's block entity (CONT-163, CONT-164): it drives one engine, a turbine and its
 * compressor, and optionally an igniter, all linked with the multitool.
 *
 * <ul>
 *   <li><b>Settings.</b> Its switch, the fuel and the oxidizer its compressor feeds (any gas the
 *       engine's reactions burn as one, {@link TurbineFuels}), the burn rate in mol of fuel per
 *       second, 0 to {@code turbine_max_burn}, and whether it relights the chamber by itself.
 *   <li><b>Output.</b> The turbine's generator fills its FE store, and it offers that FE to the
 *       blocks beside it each game tick. It is the engine's one output (CONT-162).
 *   <li><b>Igniter.</b> {@link #ignite} sparks its linked igniter once ({@link IgniterBlockEntity#spark}).
 *       With auto on, while it is on, it sparks it every {@value #AUTO_SPARK_STEPS} atmos ticks
 *       while the chamber isn't burning but would if sparked.
 *   <li><b>Links.</b> One turbine, one compressor and one igniter. A turbine or compressor keeps its
 *       controller's position too, and a link holds only while both agree, so linking a part to
 *       another controller takes it from this one. A mover rebases them ({@link #rebase}).
 * </ul>
 *
 * <p>For a redstone or computer reading: {@link #on()}, {@link #status()}, the turbine's rpm and
 * {@link #energy()}.
 */
public class TurbineControllerBlockEntity extends AtmosDevice implements MenuProvider {
    /** Atmos ticks between auto sparks. */
    public static final int AUTO_SPARK_STEPS = 2;

    /** A new controller's burn rate, mol/s of fuel. */
    public static final double DEFAULT_BURN_RATE = 0.2;

    /** What linking a block does. */
    public enum LinkResult {
        LINKED,
        UNLINKED,
        NOT_LINKABLE
    }

    /** Its links, by what is linked. */
    public enum Part {
        TURBINE("Turbine"),
        COMPRESSOR("Compressor"),
        IGNITER("Igniter");

        final String tag;

        Part(String tag) {
            this.tag = tag;
        }

        /** Its word in the window. */
        public String key() {
            return "gui.zas_atmos.turbine_controller." + tag.toLowerCase(Locale.ROOT);
        }
    }

    private static final String ON = "On";
    private static final String AUTO = "Auto";
    private static final String FUEL = "Fuel";
    private static final String OXIDIZER = "Oxidizer";
    private static final String RATE = "BurnRate";
    private static final String ENERGY = "Energy";

    private final EnergyStore energy = new EnergyStore(
            () -> AtmosServerConfig.get(AtmosServerConfig.TURBINE_BUFFER), false, true, this::setChanged);

    private final BlockPos[] links = new BlockPos[Part.values().length];

    private boolean on;
    private boolean auto = true;
    private @Nullable ResourceLocation fuel;
    private @Nullable ResourceLocation oxidizer;
    private double burnRate = DEFAULT_BURN_RATE;

    private int sparkWait;
    private TurbineStatus status = TurbineStatus.UNLINKED;

    /** The ratio for {@link #ratioFuel} and {@link #ratioOxidizer}, worked out when first asked. */
    private double ratio = Double.NaN;

    private @Nullable ResourceLocation ratioFuel;
    private @Nullable ResourceLocation ratioOxidizer;

    /** The gas lists its window last offered. */
    private List<ResourceLocation> offeredFuels = List.of();

    private List<ResourceLocation> offeredOxidizers = List.of();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return dataValue(index);
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return TurbineControllerMenu.DATA_COUNT;
        }
    };

    public TurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.TURBINE_CONTROLLER.get(), pos, state);
    }

    // ---- Its links ---------------------------------------------------------------------------

    /**
     * The controller a turbine or compressor is linked to: the one at {@code at}, if it is loaded and
     * has {@code part} as its turbine or compressor.
     */
    static @Nullable TurbineControllerBlockEntity linked(@Nullable Level level, @Nullable BlockPos at, BlockPos part) {
        if (level == null || at == null) {
            return null;
        }
        BlockEntity be = level instanceof ServerLevel server ? LoadedBlocks.entity(server, at) : level.getBlockEntity(at);
        if (be instanceof TurbineControllerBlockEntity c
                && !c.isRemoved()
                && (part.equals(c.links[Part.TURBINE.ordinal()]) || part.equals(c.links[Part.COMPRESSOR.ordinal()]))) {
            return c;
        }
        return null;
    }

    private @Nullable BlockEntity at(@Nullable BlockPos pos) {
        if (pos == null || level == null) {
            return null;
        }
        BlockEntity be = level instanceof ServerLevel server ? LoadedBlocks.entity(server, pos) : level.getBlockEntity(pos);
        return be == null || be.isRemoved() ? null : be;
    }

    /** The linked turbine, if it is loaded and agrees. */
    public @Nullable TurbineBlockEntity turbine() {
        return at(links[Part.TURBINE.ordinal()]) instanceof TurbineBlockEntity t
                        && worldPosition.equals(t.controllerPos())
                ? t
                : null;
    }

    /** The linked compressor, if it is loaded and agrees. */
    public @Nullable CompressorBlockEntity compressor() {
        return at(links[Part.COMPRESSOR.ordinal()]) instanceof CompressorBlockEntity c
                        && worldPosition.equals(c.controllerPos())
                ? c
                : null;
    }

    /** The linked igniter, if it is loaded. */
    public @Nullable IgniterBlockEntity igniter() {
        return at(links[Part.IGNITER.ordinal()]) instanceof IgniterBlockEntity i ? i : null;
    }

    /** Where its compressor link points, or null. */
    public @Nullable BlockPos compressorPos() {
        return links[Part.COMPRESSOR.ordinal()];
    }

    /** Where a link points, or null. */
    public @Nullable BlockPos link(Part part) {
        return links[part.ordinal()];
    }

    /** Whether a link is made and its part is there and agrees. */
    public boolean linked(Part part) {
        return switch (part) {
            case TURBINE -> turbine() != null;
            case COMPRESSOR -> compressor() != null;
            case IGNITER -> igniter() != null;
        };
    }

    /** What part a block would be, or null. */
    static @Nullable Part partAt(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof TurbineBlock) {
            return Part.TURBINE;
        }
        if (block instanceof CompressorBlock) {
            return Part.COMPRESSOR;
        }
        return block instanceof IgniterBlock ? Part.IGNITER : null;
    }

    /**
     * Links the turbine, compressor or igniter at a position, or unlinks it if it is linked. A part of a
     * kind already linked replaces the one there. Server side.
     *
     * @param level the level
     * @param pos the part
     * @return what it did
     */
    public LinkResult toggleLink(ServerLevel level, BlockPos pos) {
        pos = pos.immutable();
        Part part = partAt(level, pos);
        if (part == null) {
            return LinkResult.NOT_LINKABLE;
        }
        int i = part.ordinal();
        boolean wasHere = pos.equals(links[i]) && (part == Part.IGNITER || linked(part));
        tell(level, links[i], null);
        links[i] = null;
        if (!wasHere) {
            links[i] = pos;
            tell(level, pos, worldPosition);
        }
        setChanged();
        return wasHere ? LinkResult.UNLINKED : LinkResult.LINKED;
    }

    /** Tells a turbine or compressor at {@code pos} which controller it has now: this one, or none if it was this one. */
    private void tell(ServerLevel level, @Nullable BlockPos pos, @Nullable BlockPos controller) {
        if (pos == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TurbineBlockEntity t && (controller != null || worldPosition.equals(t.controllerPos()))) {
            t.setController(controller);
        } else if (be instanceof CompressorBlockEntity c
                && (controller != null || worldPosition.equals(c.controllerPos()))) {
            c.setController(controller);
        }
    }

    /** Its links after a mover moved it (CONT-156): each goes where {@code rebase} says, or is dropped. */
    public void rebase(UnaryOperator<BlockPos> rebase) {
        for (int i = 0; i < links.length; i++) {
            links[i] = links[i] == null ? null : rebase.apply(links[i]);
        }
        setChanged();
    }

    // ---- Its step ----------------------------------------------------------------------------

    @Override
    protected void atmosTick(ServerLevel level) {
        TurbineBlockEntity t = turbine();
        TurbineStatus next;
        if (!on) {
            next = TurbineStatus.OFF;
        } else if (t == null) {
            next = TurbineStatus.UNLINKED;
        } else {
            next = t.status();
            if (next == TurbineStatus.IDLE && !Double.isFinite(ratio())) {
                next = TurbineStatus.NO_FUEL;
            }
        }
        status = next;
        boolean running = t != null && t.status() == TurbineStatus.RUNNING;
        BlockState state = getBlockState();
        if (state.hasProperty(TurbineControllerBlock.RUNNING) && state.getValue(TurbineControllerBlock.RUNNING) != running) {
            level.setBlock(worldPosition, state.setValue(TurbineControllerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (sparkWait > 0) {
            sparkWait--;
        }
        if (on && auto && t != null && sparkWait == 0 && wantsSpark(level, t) && ignite()) {
            sparkWait = AUTO_SPARK_STEPS;
        }
    }

    /** Whether its chamber isn't burning but would if sparked. */
    private boolean wantsSpark(ServerLevel level, TurbineBlockEntity t) {
        if (fuel == null || !Double.isFinite(ratio()) || (t.status() != TurbineStatus.IDLE && t.status() != TurbineStatus.RUNNING)) {
            return false;
        }
        AtmosphereSample chamber = Atmospheres.at(level, t.chamberPoint());
        if (chamber.state() != AtmosphereState.ZONED || chamber.zone().isEmpty()) {
            return false;
        }
        GasMixture m = chamber.mixture();
        return !Fires.isBurning(level, chamber.zone().get()) && TurbineFuels.wouldIgnite(m, fuel);
    }

    /**
     * Sparks its linked igniter once, from its window, a computer or its auto mode. Server side.
     *
     * @return false if it has no igniter linked and loaded
     */
    public boolean ignite() {
        IgniterBlockEntity igniter = igniter();
        if (igniter == null) {
            return false;
        }
        igniter.spark();
        return true;
    }

    /** One game tick: offers its FE to the blocks beside it. */
    void serverTick() {
        if (level instanceof ServerLevel server && energy.getEnergyStored() > 0) {
            WorkToFe.push(server, worldPosition, energy);
        }
    }

    // ---- Its settings ------------------------------------------------------------------------

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /** Switches the engine on or off. Off, its turbine lets nothing out, its compressor releases nothing and its shaft runs down. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
            if (level != null) {
                AtmosSounds.play(level, worldPosition, on ? AtmosSounds.TURBINE_ON : AtmosSounds.TURBINE_OFF);
            }
        }
    }

    /** Whether it relights its chamber by itself. */
    public boolean autoIgnite() {
        return auto;
    }

    public void setAutoIgnite(boolean auto) {
        this.auto = auto;
        setChanged();
    }

    /** The fuel its compressor feeds, or null. */
    public @Nullable ResourceLocation fuel() {
        return fuel;
    }

    /** The oxidizer its compressor feeds, or null. */
    public @Nullable ResourceLocation oxidizer() {
        return oxidizer;
    }

    /**
     * Sets the fuel, if the engine's reactions burn it with some oxidizer.
     *
     * @return false if they don't, leaving it as it was
     */
    public boolean setFuel(ResourceLocation gas) {
        if (!TurbineFuels.fuels().contains(gas)) {
            return false;
        }
        fuel = gas;
        setChanged();
        return true;
    }

    /**
     * Sets the oxidizer, if the engine's reactions burn some fuel with it.
     *
     * @return false if they don't, leaving it as it was
     */
    public boolean setOxidizer(ResourceLocation gas) {
        if (!TurbineFuels.oxidizers().contains(gas)) {
            return false;
        }
        oxidizer = gas;
        setChanged();
        return true;
    }

    /** Mol of oxidizer per mol of fuel for its two gases, or NaN if either is unset or they don't burn together. */
    public double ratio() {
        if (fuel == null || oxidizer == null) {
            return Double.NaN;
        }
        if (!Objects.equals(fuel, ratioFuel) || !Objects.equals(oxidizer, ratioOxidizer)) {
            ratioFuel = fuel;
            ratioOxidizer = oxidizer;
            ratio = TurbineFuels.ratio(fuel, oxidizer);
        }
        return ratio;
    }

    /** Its burn rate, mol of fuel per second. */
    public double burnRate() {
        return burnRate;
    }

    /** The most it can be set to burn, mol/s. */
    public static double maxBurnRate() {
        return AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_BURN);
    }

    /** Sets its burn rate, clamped to 0 to {@link #maxBurnRate}, to the hundredth. */
    public void setBurnRate(double rate) {
        double r = Double.isFinite(rate) ? rate : 0;
        burnRate = Math.round(Math.max(0, Math.min(maxBurnRate(), r)) * 100) / 100.0;
        setChanged();
    }

    /** What it did at its last atmos tick. */
    public TurbineStatus status() {
        return status;
    }

    /** Its FE store. */
    public EnergyStore energy() {
        return energy;
    }

    // ---- Its window --------------------------------------------------------------------------

    /** The lists its window offers, fixed as it opens: fuels, then oxidizers. */
    private void offer() {
        offeredFuels = TurbineFuels.fuels();
        offeredOxidizers = TurbineFuels.oxidizers();
    }

    /** Writes the gas lists for the client's window. */
    public void writeMenu(RegistryFriendlyByteBuf buf) {
        if (offeredFuels.isEmpty() && offeredOxidizers.isEmpty()) {
            offer();
        }
        TurbineControllerMenu.GASES.encode(buf, offeredFuels);
        TurbineControllerMenu.GASES.encode(buf, offeredOxidizers);
    }

    private int dataValue(int index) {
        TurbineBlockEntity t = turbine();
        CompressorBlockEntity c = compressor();
        return switch (index) {
            case TurbineControllerMenu.DATA_ON -> on ? 1 : 0;
            case TurbineControllerMenu.DATA_STATUS -> status.ordinal();
            case TurbineControllerMenu.DATA_RPM -> t == null ? 0 : TurbineControllerMenu.shortOf(Math.round(t.rpm()));
            case TurbineControllerMenu.DATA_CHAMBER_PRESSURE ->
                t == null ? 0 : TurbineControllerMenu.shortOf(Math.round(t.chamberPressure()));
            case TurbineControllerMenu.DATA_CHAMBER_TEMPERATURE ->
                t == null ? 0 : TurbineControllerMenu.shortOf(Math.round(t.chamberTemperature()));
            case TurbineControllerMenu.DATA_POWER ->
                t == null ? 0 : TurbineControllerMenu.shortOf(Math.round(t.powerPerTick()));
            case TurbineControllerMenu.DATA_STORED_HIGH -> energy.getEnergyStored() >>> 16;
            case TurbineControllerMenu.DATA_STORED_LOW -> energy.getEnergyStored() & 0xFFFF;
            case TurbineControllerMenu.DATA_CAPACITY_HIGH -> energy.getMaxEnergyStored() >>> 16;
            case TurbineControllerMenu.DATA_CAPACITY_LOW -> energy.getMaxEnergyStored() & 0xFFFF;
            case TurbineControllerMenu.DATA_TOP_RPM ->
                TurbineControllerMenu.shortOf(AtmosServerConfig.get(AtmosServerConfig.TURBINE_MAX_RPM));
            case TurbineControllerMenu.DATA_FUEL -> fuel == null ? -1 : offeredFuels.indexOf(fuel);
            case TurbineControllerMenu.DATA_OXIDIZER -> oxidizer == null ? -1 : offeredOxidizers.indexOf(oxidizer);
            case TurbineControllerMenu.DATA_RATE -> TurbineControllerMenu.shortOf(Math.round(burnRate * 100));
            case TurbineControllerMenu.DATA_AUTO -> auto ? 1 : 0;
            case TurbineControllerMenu.DATA_LINKS ->
                (linked(Part.TURBINE) ? 1 : 0) | (linked(Part.COMPRESSOR) ? 2 : 0) | (linked(Part.IGNITER) ? 4 : 0);
            case TurbineControllerMenu.DATA_BUFFER_PRESSURE ->
                c == null ? TurbineControllerMenu.NONE : TurbineControllerMenu.shortOf(Math.round(c.buffer().pressure()));
            case TurbineControllerMenu.DATA_BUFFER_FUEL ->
                c == null || fuel == null ? 0 : TurbineControllerMenu.shortOf(Math.round(c.buffer().partialPressure(fuel)));
            case TurbineControllerMenu.DATA_BUFFER_OXIDIZER ->
                c == null || oxidizer == null
                        ? 0
                        : TurbineControllerMenu.shortOf(Math.round(c.buffer().partialPressure(oxidizer)));
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.turbine_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        offer();
        return new TurbineControllerMenu(id, this, data, offeredFuels, offeredOxidizers);
    }

    // ---- Saved -------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putBoolean(AUTO, auto);
        tag.putDouble(RATE, burnRate);
        tag.putInt(ENERGY, energy.getEnergyStored());
        if (fuel != null) {
            tag.putString(FUEL, fuel.toString());
        }
        if (oxidizer != null) {
            tag.putString(OXIDIZER, oxidizer.toString());
        }
        for (Part p : Part.values()) {
            if (links[p.ordinal()] != null) {
                tag.putLong(p.tag, links[p.ordinal()].asLong());
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        auto = !tag.contains(AUTO) || tag.getBoolean(AUTO);
        burnRate = tag.contains(RATE) ? Math.max(0, tag.getDouble(RATE)) : DEFAULT_BURN_RATE;
        if (!Double.isFinite(burnRate)) {
            burnRate = DEFAULT_BURN_RATE;
        }
        energy.set(tag.getInt(ENERGY));
        fuel = tag.contains(FUEL) ? ResourceLocation.tryParse(tag.getString(FUEL)) : null;
        oxidizer = tag.contains(OXIDIZER) ? ResourceLocation.tryParse(tag.getString(OXIDIZER)) : null;
        for (Part p : Part.values()) {
            links[p.ordinal()] = tag.contains(p.tag) ? BlockPos.of(tag.getLong(p.tag)) : null;
        }
        ratioFuel = null;
        ratioOxidizer = null;
    }
}
