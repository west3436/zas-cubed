package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.ContainerReactions;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A portable that moves gas (CONT-018): the portable pump and scrubber. It has a store of its own and
 * works on the air around it: a held tank, if it holds one, or else the zone it stands in. With
 * neither, it does nothing.
 *
 * <p>Each atmos tick, while it is on, a subclass {@linkplain #plan plans} what to move between that
 * air and the store, and it pays its battery its draw over the tick, only if there is something to
 * move. With too little charge left it moves what the charge pays for, then stops. A zone's gas moves
 * through one {@link ZonePort}, so one change is outstanding at a time (CONT-072), and a tank's moves
 * at once. Its store and a held tank react each atmos tick, on or off (CONT-100).
 *
 * <p>Its item carries the store's gas, as a canister's does: a player who mines it gets the gas with
 * the item, one who breaks it in creative destroys the gas, and removed any other way it releases the
 * gas where it stands.
 */
public abstract class PortableGasDevice extends PortableDevice {
    /** The store's volume, L (vg's portable pump and scrubber). */
    public static final double STORE_LITRES = 2000;

    private static final String GAS = "Gas";
    private static final String TANK = "Tank";

    /** A plan for one second: an idle reason, or the gas to take from the air or give to it. */
    protected record Move(PortableStatus idle, Map<ResourceLocation, Double> take, double give) {
        /** Nothing to move, and why. */
        public static Move idle(PortableStatus why) {
            return new Move(why, Map.of(), 0);
        }

        /** Take these moles per gas from the air into the store. */
        public static Move take(Map<ResourceLocation, Double> moles) {
            return new Move(null, moles, 0);
        }

        /** Give this many moles of the store, in its proportions, to the air. */
        public static Move give(double moles) {
            return new Move(null, Map.of(), moles);
        }
    }

    private final GasStore store = new GasStore(STORE_LITRES);
    private final TankSlot tank = new TankSlot(this::setChanged);
    private final CellWatch watch = new CellWatch();
    private final SourceId source;
    private final ZonePort port;
    private PortableStatus status = PortableStatus.OFF;
    private double airPressure = Double.NaN;
    private boolean keepContents;
    private boolean voidContents;
    private boolean unloading;

    /**
     * @param type its block entity type
     * @param pos where it stands
     * @param state its block state
     * @param source who its changes to zones are made by
     */
    protected PortableGasDevice(BlockEntityType<?> type, BlockPos pos, BlockState state, SourceId source) {
        super(type, pos, state);
        this.source = source;
        this.port = new ZonePort(source);
    }

    /**
     * What to move in one second.
     *
     * @param air the air it works on: a held tank's gas or the zone's
     * @return the plan; gas taken is what the air holds and gas given what the store holds
     */
    protected abstract Move plan(GasMixture air);

    /** What it draws from its battery while it moves gas, FE/t. */
    protected abstract int drawPerTick();

    /** Its store. */
    public GasStore store() {
        return store;
    }

    /** Its held tank's slot. */
    public TankSlot tank() {
        return tank;
    }

    /** What it did at its last step. */
    public PortableStatus status() {
        return status;
    }

    /**
     * The pressure of the air it works on, kPa, as of its last step; NaN when there was none or it
     * was off.
     */
    public double airPressure() {
        return airPressure;
    }

    /** Called when it is about to drop as an item carrying its gas. */
    void keepContents() {
        keepContents = true;
    }

    /** Called when a creative player is about to break it: its gas is destroyed with it. */
    void voidContents() {
        voidContents = true;
    }

    @Override
    public List<ItemStack> heldItems() {
        return List.of(battery().battery(), tank.tank());
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        watch.step();
        react();
        airPressure = Double.NaN;
        status = on() ? step(level) : PortableStatus.OFF;
        showWorking(level, status == PortableStatus.WORKING);
    }

    /** The gas it holds reacts, on or off (CONT-100): its store's and a held tank's. */
    private void react() {
        boolean reacted = ContainerReactions.react(store);
        if (tank.tank().getItem() instanceof GasTankItem item) {
            reacted |= ContainerReactions.react(tank.tank(), item.volume());
        }
        if (reacted) {
            setChanged();
        }
    }

    /** One step's work on the air. */
    private PortableStatus step(ServerLevel level) {
        IEnergyStorage energy = battery().energy();
        if (energy == null) {
            return PortableStatus.NO_BATTERY;
        }
        ItemStack held = tank.tank();
        GasMixture air;
        if (held.isEmpty()) {
            AtmosphereSample sample = Atmospheres.at(level, point());
            boolean trusted = watch.trust(sample);
            if (sample.state() != AtmosphereState.ZONED) {
                return PortableStatus.NO_ROOM;
            }
            if (!trusted) {
                return PortableStatus.WAITING;
            }
            air = sample.mixture();
        } else {
            air = GasTankItem.contents(held);
        }
        airPressure = air.pressure();
        Move move = plan(air);
        if (move.idle() != null) {
            return move.idle();
        }
        double paid = pay(energy);
        if (paid <= 0) {
            return PortableStatus.BATTERY_EMPTY;
        }
        Map<ResourceLocation, Double> take = new TreeMap<>(move.take());
        take.replaceAll((gas, n) -> n * paid);
        double give = move.give() * paid;
        if (held.isEmpty()) {
            onZone(level, take, give);
        } else {
            onTank(held, take, give);
        }
        setChanged();
        return PortableStatus.WORKING;
    }

    /** Pays its battery for a step. @return the share of the step the charge paid for, 0 to 1. */
    private double pay(IEnergyStorage energy) {
        int cost = drawPerTick() * AtmosTicks.PERIOD;
        return cost <= 0 ? 1 : (double) energy.extractEnergy(cost, false) / cost;
    }

    private void onZone(ServerLevel level, Map<ResourceLocation, Double> take, double give) {
        if (give > 0) {
            GasParcel out = store.takeFraction(give / store.totalMoles());
            port.add(level, point(), out, r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())));
        } else {
            port.remove(level, point(), take, r -> deliver(r.moved()));
        }
    }

    private void onTank(ItemStack held, Map<ResourceLocation, Double> take, double give) {
        if (give > 0) {
            GasTankItem.add(held, store.takeFraction(give / store.totalMoles()));
        } else {
            store.add(GasTankItem.take(held, take));
        }
        ItemStack kind = GasTankItem.ofItsKind(held);
        if (kind != held) {
            tank.setStackInSlot(0, kind);
        }
    }

    /** Puts gas that came back from a zone in the store, or, if it has been removed since, releases it. */
    private void deliver(GasParcel gas) {
        if (gas.isEmpty()) {
            return;
        }
        if (!isRemoved()) {
            store.add(gas);
            setChanged();
        } else if (level instanceof ServerLevel server) {
            GasMutations.add(server, point(), gas, source);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            unloading = false;
            watch.reset();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (!unloading && !keepContents && level instanceof ServerLevel server) {
            GasParcel gas = store.drain();
            if (!gas.isEmpty() && !voidContents) {
                GasMutations.add(server, point(), gas, source);
            }
        }
        super.setRemoved();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!store.isEmpty()) {
            components.set(AtmosComponents.GAS.get(), new GasParcel(store.moles(), store.temperature()));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        GasParcel gas = input.get(AtmosComponents.GAS.get());
        if (gas != null) {
            store.set(gas);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(GAS);
    }

    /** The largest value of a pressure in a menu's data, which syncs as shorts. */
    private static int kilopascals(double kPa) {
        return (int) Math.min(Short.MAX_VALUE, Math.round(kPa));
    }

    @Override
    protected int menuValue(int index) {
        return switch (index) {
            case PortableGasMenu.DATA_STATUS -> status.ordinal();
            case PortableGasMenu.DATA_AIR ->
                Double.isNaN(airPressure) ? PortableGasMenu.NO_AIR : kilopascals(airPressure);
            case PortableGasMenu.DATA_ON_TANK -> tank.tank().isEmpty() ? 0 : 1;
            case PortableGasMenu.DATA_STORE -> kilopascals(store.pressure());
            default -> index < PortableMenu.DATA_COUNT ? super.menuValue(index) : 0;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(GAS, GasStore.save(new GasParcel(store.moles(), store.temperature())));
        tag.put(TANK, tank.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(GAS)) {
            store.set(GasStore.parcel(tag.getCompound(GAS)));
        }
        if (tag.contains(TANK)) {
            tank.deserializeNBT(registries, tag.getCompound(TANK));
        }
    }
}
