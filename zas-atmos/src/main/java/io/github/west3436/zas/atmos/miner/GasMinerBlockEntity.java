package io.github.west3436.zas.atmos.miner;

import io.github.west3436.ss13.device.Comparators;
import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.ss13.device.RedstoneControl;
import io.github.west3436.ss13.device.RedstoneControlled;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * A creative gas miner (CONT-180, CONT-181): while on, it makes one gas, at a temperature of its own,
 * in the cell in front of its face, until the zone there reaches its target pressure. It needs no
 * power and has no store: the gas comes from nowhere, so it is a source of the conservation ledger,
 * recorded under {@link #SOURCE} as every API addition is.
 *
 * <p>It adds at most {@code [gas_miner] rate} mol a second. A cell in an enclosed area that holds no
 * zone yet reads as the ambient, and the miner's first addition forms its zone, as any API addition
 * does (CORE-055). An addition that goes to the open air instead is lost there, and the miner then
 * waits {@code [gas_miner] open_air_wait} seconds before it tries again, so one in the open costs the
 * engine one bounded addition in that time (CONT-181).
 *
 * <p>Held by its redstone setting (CONT-170), it adds nothing and its lamp goes out, but it still
 * reads its room. A comparator reads the room's pressure as a share of its target, on the
 * containers' scale ({@link #signal()}, CONT-175): the room is what it fills, and its target is full.
 */
public class GasMinerBlockEntity extends AtmosDevice implements MenuProvider, RedstoneControlled {
    /** Who a gas miner's additions are made by, for the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "gas_miner");

    /** A new miner's gas. */
    public static final ResourceLocation DEFAULT_GAS = StandardAir.OXYGEN;

    /** A new miner's target, kPa: one standard atmosphere. */
    public static final double DEFAULT_TARGET = StandardAir.PRESSURE;

    /** A new miner's gas temperature, K. */
    public static final double DEFAULT_TEMPERATURE = StandardAir.TEMPERATURE;

    /** The coldest gas it makes, K. */
    public static final double MIN_TEMPERATURE = 3;

    /** The hottest gas it makes, K. */
    public static final double MAX_TEMPERATURE = 2000;

    /** How far below its target, as a share of it, a room still counts as at its target. */
    public static final double AT_TARGET_SHARE = 0.001;

    private static final String ON = "On";
    private static final String GAS = "Gas";
    private static final String TARGET = "Target";
    private static final String TEMPERATURE = "Temperature";

    private final ZonePort port = new ZonePort(SOURCE);
    private final CellWatch cellWatch = new CellWatch();
    private final RedstoneControl redstone = new RedstoneControl(this);
    private final Comparators.Output comparator = new Comparators.Output();

    private boolean on;
    private ResourceLocation gas = DEFAULT_GAS;
    private double target = DEFAULT_TARGET;
    private double temperature = DEFAULT_TEMPERATURE;

    private GasMinerStatus status = GasMinerStatus.OFF;

    /** The room's pressure at its last step, kPa, or NaN if it had no reading. */
    private double room = Double.NaN;

    /** The atmos ticks left before it tries the open air again. */
    private int openAirWait;

    /** The gas list a window opened on it shows, which a button names by its place in. */
    private List<ResourceLocation> offered = List.of();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case GasMinerMenu.DATA_ON -> on ? 1 : 0;
                case GasMinerMenu.DATA_GAS -> offered.indexOf(gas);
                case GasMinerMenu.DATA_TARGET -> (int) Math.min(Short.MAX_VALUE, Math.round(target));
                case GasMinerMenu.DATA_TEMPERATURE -> (int) Math.round(temperature * 10);
                case GasMinerMenu.DATA_ROOM ->
                    Double.isNaN(room) ? GasMinerMenu.NO_ROOM : (int) Math.min(Short.MAX_VALUE, Math.round(room));
                case GasMinerMenu.DATA_STATUS -> status.ordinal();
                case GasMinerMenu.DATA_REDSTONE -> redstone.data();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return GasMinerMenu.DATA_COUNT;
        }
    };

    public GasMinerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.GAS_MINER.get(), pos, state);
    }

    // ---- Its settings -----------------------------------------------------------------------

    /** The highest target a miner takes, kPa: {@code [gas_miner] max_target}. */
    public static double maxTarget() {
        return AtmosServerConfig.get(AtmosServerConfig.GAS_MINER_MAX_TARGET);
    }

    /** A target clamped to 0 to {@link #maxTarget()} kPa; NaN is the default. */
    public static double clampTarget(double kpa) {
        return Double.isNaN(kpa) ? DEFAULT_TARGET : Math.max(0, Math.min(maxTarget(), kpa));
    }

    /** A temperature clamped to {@value #MIN_TEMPERATURE} to {@value #MAX_TEMPERATURE} K; NaN is the default. */
    public static double clampTemperature(double kelvin) {
        return Double.isNaN(kelvin)
                ? DEFAULT_TEMPERATURE
                : Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, kelvin));
    }

    /** Every gas a miner makes: each the server defines, in id order. */
    public static List<ResourceLocation> gases() {
        return List.copyOf(GasProperties.defined());
    }

    /** Whether it is switched on. */
    public boolean isOn() {
        return on;
    }

    /** The gas it makes. */
    public ResourceLocation gas() {
        return gas;
    }

    /** Its target, kPa. */
    public double target() {
        return target;
    }

    /** The temperature of the gas it makes, K. */
    public double temperature() {
        return temperature;
    }

    /** What it did at its last step. */
    public GasMinerStatus status() {
        return status;
    }

    /** The pressure in front of it at its last step, kPa, or NaN if it had no reading or is off. */
    public double roomPressure() {
        return room;
    }

    /** The atmos ticks left before it tries the open air again; 0 when it isn't waiting. */
    public int openAirWait() {
        return openAirWait;
    }

    /** Switches it on or off. Server side. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
            lamp();
        }
    }

    /** Lights its lamp while it is on and redstone doesn't hold it, telling clients. Server side. */
    private void lamp() {
        boolean lit = on && redstone.allows();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(GasMinerBlock.ON) && state.getValue(GasMinerBlock.ON) != lit) {
                level.setBlock(worldPosition, state.setValue(GasMinerBlock.ON, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public RedstoneControl redstone() {
        return redstone;
    }

    /**
     * What a comparator reads (CONT-175): the room's pressure at its last step over its target, on
     * the containers' scale, so 15 is within a fifteenth of its target or above it; 0 while it is
     * switched off or has no reading.
     */
    public int signal() {
        return Double.isNaN(room) ? 0 : Comparators.fill(room / target);
    }

    /**
     * Sets the gas it makes. Server side.
     *
     * @return false, changing nothing, if the server defines no such gas
     */
    public boolean setGas(ResourceLocation gas) {
        if (!GasProperties.isDefined(gas)) {
            return false;
        }
        if (!gas.equals(this.gas)) {
            this.gas = gas;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    /** Sets its target, clamped by {@link #clampTarget}. Server side. */
    public void setTarget(double kpa) {
        double next = clampTarget(kpa);
        if (next != target) {
            target = next;
            setChanged();
        }
    }

    /** Sets its gas's temperature, clamped by {@link #clampTemperature}. Server side. */
    public void setTemperature(double kelvin) {
        double next = clampTemperature(kelvin);
        if (next != temperature) {
            temperature = next;
            setChanged();
        }
    }

    // ---- What it does -----------------------------------------------------------------------

    /** The cell in front of its face: where its gas goes. */
    public Vec3 airPoint() {
        BlockState state = getBlockState();
        return state.hasProperty(GasMinerBlock.FACING)
                ? Vec3.atCenterOf(worldPosition.relative(state.getValue(GasMinerBlock.FACING)))
                : Vec3.atCenterOf(worldPosition.above());
    }

    /** The most it adds in one step, mol. */
    private static double mostPerStep() {
        return AtmosServerConfig.get(AtmosServerConfig.GAS_MINER_RATE) * DeviceTicks.SECONDS;
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    /** One step: its work, then its lamp and its comparators. */
    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch.step();
        boolean allowed = redstone.read();
        work(level, allowed);
        lamp();
        comparator.update(level, worldPosition, getBlockState().getBlock(), signal());
    }

    private void work(ServerLevel level, boolean allowed) {
        if (!on) {
            status = GasMinerStatus.OFF;
            room = Double.NaN;
            reportStopped();
            return;
        }
        Vec3 point = airPoint();
        AtmosphereSample sample = Atmospheres.at(level, point);
        if (!cellWatch.trust(sample)) {
            status = allowed ? GasMinerStatus.WAITING : GasMinerStatus.OFF;
            room = Double.NaN;
            return;
        }
        GasMixture mixture = sample.mixture();
        room = mixture.pressure();
        if (!allowed) {
            // Held by redstone (CONT-170): it reads its room, and adds nothing.
            status = GasMinerStatus.OFF;
            reportStopped();
            return;
        }
        if (openAirWait > 0) {
            openAirWait--;
            status = GasMinerStatus.OPEN_AIR;
            reportStopped();
            return;
        }
        if (sample.state() == AtmosphereState.PAUSED) {
            // A paused zone voids what it is given (GEO-036): nothing is gained by adding.
            status = GasMinerStatus.WAITING;
            reportStopped();
            return;
        }
        double n = GasMinerMath.moles(mixture.pressure(), mixture.volume(), target, temperature, mostPerStep());
        // Within a thousandth of its target it waits: hotter or colder gas than the room's walls
        // drifts as it settles, and topping that up every step would never read At target.
        if (n < PipeBlockEntity.MIN_FLOW || mixture.pressure() >= target * (1 - AT_TARGET_SHARE)) {
            status = GasMinerStatus.AT_TARGET;
            reportStopped();
            return;
        }
        status = GasMinerStatus.FILLING;
        reportMoved(n);
        port.add(level, point, new GasParcel(Map.of(gas, n), temperature), result -> {
            if (result.outcome() == MutationOutcome.AMBIENT) {
                openAirWait = Math.max(0, AtmosServerConfig.get(AtmosServerConfig.GAS_MINER_OPEN_AIR_WAIT))
                        * 20
                        / DeviceTicks.PERIOD;
                status = GasMinerStatus.OPEN_AIR;
            }
        });
    }

    // ---- Its window -------------------------------------------------------------------------

    /** What its window reads, live. */
    ContainerData data() {
        return data;
    }

    /** The gas list a window opening now shows, which it also keeps for the window's buttons. */
    List<ResourceLocation> offer() {
        List<ResourceLocation> list = new ArrayList<>(gases());
        if (!list.contains(gas)) {
            list.add(gas);
        }
        offered = List.copyOf(list);
        return offered;
    }

    /** Writes what the client's menu needs: the gas list its server menu was made with. */
    public void writeMenu(RegistryFriendlyByteBuf buf) {
        GasMinerMenu.GASES.encode(buf, offered.isEmpty() ? offer() : offered);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.zas_atmos.gas_miner");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GasMinerMenu(id, this, offer());
    }

    // ---- Saved, and sent ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(ON, on);
        tag.putString(GAS, gas.toString());
        tag.putDouble(TARGET, target);
        tag.putDouble(TEMPERATURE, temperature);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = tag.getBoolean(ON);
        ResourceLocation read = ResourceLocation.tryParse(tag.getString(GAS));
        gas = read == null ? DEFAULT_GAS : read;
        target = tag.contains(TARGET) ? clampTarget(tag.getDouble(TARGET)) : DEFAULT_TARGET;
        temperature = tag.contains(TEMPERATURE) ? clampTemperature(tag.getDouble(TEMPERATURE)) : DEFAULT_TEMPERATURE;
        redstone.load(tag);
    }

    /** Clients are sent its gas, which tints its lamp. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(GAS, gas.toString());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readGas(tag);
    }

    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readGas(packet.getTag());
    }

    /** On a client: takes the gas a server sent, and has the block drawn again in its colour. */
    private void readGas(CompoundTag tag) {
        ResourceLocation read = ResourceLocation.tryParse(tag.getString(GAS));
        gas = read == null ? DEFAULT_GAS : read;
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }
}
