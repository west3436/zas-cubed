package io.github.west3436.zas.atmos.inline;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.gas.ContainerReactions;
import io.github.west3436.zas.atmos.pipe.PipeHost;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipePort;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The block entity of an {@link InlineDeviceBlock}: a pump, filter or mixer (architecture §11.5). It
 * has one 200 L {@link PipePort} per port face (CONT-007), each in a network of its own, and moves
 * gas between them in {@link #work}, only while it is switched on and powered.
 *
 * <p>It is an area air controller's part in the gas-handling group (CONT-064), powered through its
 * zone or a multitool link like a vent. It draws {@link #drawWhenOn()} FE/t while it is on (CONT-065)
 * and works only on power that was paid for, so the lease it holds while off doesn't run it for
 * free.
 *
 * <p>A device extends this, gives {@link #work} and {@link #drawWhenOn}, and opens a menu that
 * extends {@link InlineDeviceMenu}.
 */
public abstract class InlineDeviceEntity extends AtmosDevice implements PipeHost, PoweredDevice, MenuProvider {
    private static final String PORTS = "Ports";
    private static final String ON = "On";

    private final PipePort[] ports;
    private final DevicePower power = new DevicePower(PowerPriority.GAS_HANDLING, this::currentDraw, this::now);
    private boolean on;
    private boolean unloading;
    private InlineStatus status = InlineStatus.OFF;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return dataValue(index);
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return dataCount();
        }
    };

    /**
     * @param type the block entity type
     * @param pos its position
     * @param state its block state
     * @param portCount its ports, as many as its block's {@link InlineDeviceBlock#portFaces}
     */
    protected InlineDeviceEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int portCount) {
        super(type, pos, state);
        ports = new PipePort[portCount];
        for (int i = 0; i < portCount; i++) {
            int index = i;
            ports[i] = new PipePort(PipeVolumes.PORT, () -> links(index));
        }
    }

    /** One atmos tick of moving gas, while it is on and powered; the status says what came of it. */
    protected abstract InlineStatus work(ServerLevel level);

    /** What it draws while it is on, in FE/t (CONT-065). */
    protected abstract int drawWhenOn();

    /** A port, in the order of its block's {@link InlineDeviceBlock#portFaces}. */
    public PipePort port(int index) {
        return ports[index];
    }

    /** The node across a port's face, if a neighbour is connected there. */
    private List<PipeNode> links(int index) {
        if (level == null) {
            return List.of();
        }
        BlockState self = level.getBlockState(worldPosition);
        if (!(self.getBlock() instanceof InlineDeviceBlock block)) {
            return List.of();
        }
        PipeNode n = PipeHost.across(
                level, worldPosition, self, block.portFaces(self).get(index));
        return n == null ? List.of() : List.of(n);
    }

    @Override
    public PipeNode nodeOn(Direction face) {
        BlockState self = getBlockState();
        int index = self.getBlock() instanceof InlineDeviceBlock block
                ? block.portFaces(self).indexOf(face)
                : -1;
        return index < 0 ? null : ports[index];
    }

    /**
     * Rebuilds its ports' networks, for a device that has just changed which faces its ports are on,
     * as a filter whose side port flips. Call it after setting the new block state.
     */
    protected void portsChanged() {
        for (PipePort p : ports) {
            p.dissolve();
            p.dissolveLinks();
        }
    }

    /** Whether it is switched on. */
    public boolean on() {
        return on;
    }

    /** Switches it on or off. Server side. */
    public void setOn(boolean on) {
        if (on != this.on) {
            this.on = on;
            setChanged();
        }
    }

    /** Whether it is running: on, with power to run on. */
    public boolean running() {
        return on && paid();
    }

    /** Whether it has power to run on: paid for, or any power at all when it is set to draw nothing. */
    private boolean paid() {
        return power.paidFor() || (drawWhenOn() == 0 && power.isPowered());
    }

    private int currentDraw() {
        return on ? drawWhenOn() : 0;
    }

    /** What it did at its last atmos tick: off, moving gas, or idle and why. */
    public InlineStatus status() {
        return status;
    }

    @Override
    public DevicePower power() {
        return power;
    }

    /** Its own cell: it isn't a full block, so it stands in its room. */
    @Override
    public Vec3 airPoint() {
        return Vec3.atCenterOf(worldPosition);
    }

    /**
     * Each port's turn in its network's atmos tick, as a {@link PipeNodeEntity} takes its own: a
     * network may hold only ports, with no pipe to run it, and it reacts all the same (CONT-100). This
     * also rebuilds dissolved networks, so their gas is shared over their new members even while the
     * device is off.
     */
    @Override
    protected final void beforeAtmosTick(ServerLevel level) {
        for (PipePort p : ports) {
            PipeNetwork net = p.network();
            if (net.startsAtmosTick(p)) {
                ContainerReactions.react(net.gas());
            }
        }
    }

    @Override
    protected final void atmosTick(ServerLevel level) {
        boolean running = running();
        BlockState state = getBlockState();
        if (state.hasProperty(InlineDeviceBlock.ON) && state.getValue(InlineDeviceBlock.ON) != running) {
            level.setBlock(worldPosition, state.setValue(InlineDeviceBlock.ON, running), Block.UPDATE_CLIENTS);
        }
        status = running ? work(level) : InlineStatus.OFF;
    }

    // ---- The menu's data ------------------------------------------------------------------------

    /** The data slots a menu syncs from this device: {@link #dataValue} by index. */
    public ContainerData data() {
        return data;
    }

    /** How many data slots it has; a device with values of its own adds them. */
    protected int dataCount() {
        return InlineDeviceMenu.DATA_COUNT;
    }

    /** A data slot's value. Menu data syncs as shorts. A device with values of its own adds them. */
    protected int dataValue(int index) {
        return switch (index) {
            case InlineDeviceMenu.DATA_ON -> on ? 1 : 0;
            case InlineDeviceMenu.DATA_POWERED -> power.isPowered() ? 1 : 0;
            case InlineDeviceMenu.DATA_STATUS -> status.ordinal();
            default -> {
                int port = index - InlineDeviceMenu.DATA_PRESSURE;
                yield port >= 0 && port < ports.length && !isRemoved()
                        ? (int) Math.min(
                                Short.MAX_VALUE,
                                Math.round(ports[port].network().gas().pressure()))
                        : 0;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    // ---- Its ports' lives, as a PipeNodeEntity's ---------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            unloading = false;
            portsChanged();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        for (PipePort p : ports) {
            p.dissolve();
        }
        super.onChunkUnloaded();
    }

    /** Removed from the world, it releases each port's share into its cell (CONT-009). */
    @Override
    public void setRemoved() {
        if (!unloading && level instanceof ServerLevel server) {
            for (PipePort p : ports) {
                GasParcel share = p.detach();
                if (!share.isEmpty()) {
                    GasMutations.add(server, Vec3.atCenterOf(worldPosition), share, PipeNodeEntity.RELEASE);
                }
            }
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag saved = new ListTag();
        for (PipePort p : ports) {
            saved.add(p.save());
        }
        tag.put(PORTS, saved);
        tag.putBoolean(ON, on);
        power.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag saved = tag.getList(PORTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < ports.length && i < saved.size(); i++) {
            ports[i].load(saved.getCompound(i));
        }
        on = tag.getBoolean(ON);
        power.load(tag);
    }
}
