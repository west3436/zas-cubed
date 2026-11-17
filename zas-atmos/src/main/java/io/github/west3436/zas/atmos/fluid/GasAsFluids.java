package io.github.west3436.zas.atmos.fluid;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasPurity;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A ZAS³ gas store as other mods' fluid pipes and machines see it (COMPAT-142): the fluid handler
 * of a block that offers a gas handler on the same face, such as a pipe. A fluid pipe connects to
 * it, a machine fills it with a gas it makes as a fluid, and a pump or a pipe set to pull drains it.
 * Nothing is kept here: every call reads or changes the gas store itself, converting at the unit
 * factor (COMPAT-143) through the fluid tags' gases (COMPAT-140).
 *
 * <p><b>In.</b> Any mapped fluid enters, whatever the store holds, as far as the store takes it: a
 * pipe stops at its pressure limit. An unmapped fluid, such as water, is refused whole. The gas
 * arrives at its mapping's temperature (COMPAT-145).
 *
 * <p><b>Out.</b> What the store shows as its tank follows {@link GasPurity}: the one gas it is
 * nearly all of, as much of it as may leave, or nothing; with the rule off, a tank for each gas. A
 * gas shows as the hottest of its fluids it is at least as hot as, so water vapour at 383 K shows
 * as a mod's 383 K steam and cold vapour as no fluid at all (COMPAT-145). A drain that names a
 * fluid gets that fluid, if the gas may leave as it.
 *
 * <p><b>Whole millibuckets</b> (COMPAT-144). Both ways the amount is rounded down to whole
 * millibuckets before anything moves, so what moves is exactly that many, and the fraction left
 * over stays where it was: in the store on the way out, and in the other mod, which never had a
 * fraction, on the way in. Nothing is made or lost.
 *
 * <p>The view asks for the block's gas handler anew on each call, so it is safe to keep, as other
 * mods' pipes do: a block that has gone, or changed into something else, holds nothing.
 */
final class GasAsFluids implements IFluidHandler {
    /** What a probe for a store's room offers, in moles: more than any store takes. */
    private static final double PROBE_MOLES = 1e9;

    private final ServerLevel level;
    private final BlockPos pos;

    @Nullable
    private final Direction side;

    /** One tank the store shows: a gas, the fluid it leaves as, and its whole millibuckets. */
    private record Tank(ResourceLocation gas, FluidGases.Mapped fluid, int millibuckets) {
        FluidStack stack() {
            return new FluidStack(fluid.fluid(), millibuckets);
        }
    }

    private GasAsFluids(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    /**
     * The capability provider: a view wherever the block offers a gas handler of its own and some
     * fluid is a gas, so that no fluid pipe connects to a pipe while nothing could pass.
     */
    @Nullable
    static IFluidHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server) || FluidGases.sources().isEmpty()) {
            return null;
        }
        IGasHandler store = level.getCapability(IGasHandler.BLOCK, pos, state, entity, side);
        return store == null || FluidConnection.isView(store) ? null : new GasAsFluids(server, pos, side);
    }

    /** The block's gas handler now, or null if it has none of its own any more. */
    @Nullable
    private IGasHandler store() {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IGasHandler store = level.getCapability(IGasHandler.BLOCK, pos, side);
        return FluidConnection.isView(store) ? null : store;
    }

    private static double minPurity() {
        return AtmosServerConfig.get(AtmosServerConfig.FLUID_MIN_PURITY);
    }

    /** The tanks the store shows now, in the order of their gases' ids. */
    private static List<Tank> tanks(IGasHandler store) {
        GasMixture held = store.contents();
        double share = minPurity();
        Map<ResourceLocation, Double> leaving = share > 0
                ? GasPurity.leaving(held.moles(), share)
                        .map(main -> Map.of(main.gas(), main.moles()))
                        .orElse(Map.of())
                : new TreeMap<>(held.moles());
        List<Tank> tanks = new ArrayList<>();
        leaving.forEach((gas, moles) -> {
            List<FluidGases.Mapped> fluids = FluidGases.leavingAs(gas, held.temperature());
            long millibuckets = Math.min(Integer.MAX_VALUE, FluidGases.wholeMillibuckets(moles));
            if (!fluids.isEmpty() && millibuckets > 0) {
                tanks.add(new Tank(gas, fluids.get(0), (int) millibuckets));
            }
        });
        return tanks;
    }

    @Nullable
    private static Tank tank(IGasHandler store, int tank) {
        List<Tank> tanks = tanks(store);
        return tank >= 0 && tank < tanks.size() ? tanks.get(tank) : null;
    }

    /**
     * One tank while the purity rule is on, which is empty unless the store is nearly all one gas.
     * With the rule off, a tank for each gas that can leave and an empty one after them, so that
     * whoever goes through the tanks finds room for a fluid the store doesn't hold yet.
     */
    @Override
    public int getTanks() {
        if (minPurity() > 0) {
            return 1;
        }
        try {
            IGasHandler store = store();
            return store == null ? 1 : tanks(store).size() + 1;
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return 1;
        }
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        try {
            IGasHandler store = store();
            Tank held = store == null ? null : tank(store, tank);
            return held == null ? FluidStack.EMPTY : held.stack();
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return FluidStack.EMPTY;
        }
    }

    /**
     * What the tank holds and what more the store would take of that gas now. A store has no size in
     * millibuckets: a pipe's limit is a pressure, which depends on what it holds and how hot it is.
     * For an empty tank the room is that for the first gas a fluid leads to.
     */
    @Override
    public int getTankCapacity(int tank) {
        try {
            IGasHandler store = store();
            if (store == null) {
                return 0;
            }
            Tank held = tank(store, tank);
            ResourceLocation gas = held != null
                    ? held.gas()
                    : FluidGases.sources().isEmpty()
                            ? null
                            : FluidGases.sources().get(0).gas();
            if (gas == null) {
                return 0;
            }
            GasParcel probe =
                    new GasParcel(Map.of(gas, PROBE_MOLES), store.contents().temperature());
            long room = FluidGases.wholeMillibuckets(
                    store.fill(probe, IGasHandler.Action.SIMULATE).moles(gas));
            long capacity = (held == null ? 0 : held.millibuckets()) + room;
            return (int) Math.min(Integer.MAX_VALUE, capacity);
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return 0;
        }
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return FluidGases.mapping(stack).isPresent();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        FluidGases.Mapped mapped = FluidGases.mapping(resource).orElse(null);
        if (mapped == null) {
            return 0;
        }
        try {
            IGasHandler store = store();
            return store == null ? 0 : (int) fill(store, mapped, resource, action.execute());
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return 0;
        }
    }

    /**
     * Fills the store with as many whole millibuckets of a fluid as it takes.
     *
     * @return how many it took, or would take
     */
    private long fill(IGasHandler store, FluidGases.Mapped mapped, FluidStack resource, boolean execute) {
        long amount = resource.getAmount();
        ResourceLocation gas = mapped.gas();
        GasParcel would = store.fill(mapped.gas(resource, amount), IGasHandler.Action.SIMULATE);
        long whole = Math.min(amount, FluidGases.wholeMillibuckets(would.moles(gas)));
        if (whole <= 0 || !execute) {
            return Math.max(0, whole);
        }
        GasParcel offered = mapped.gas(resource, whole);
        GasParcel took = store.fill(offered, IGasHandler.Action.EXECUTE);
        long done = Math.min(whole, FluidGases.wholeMillibuckets(took.moles(gas)));
        // Whole millibuckets count; what it took beyond them goes back out.
        double over = took.moles(gas) - FluidGases.moles(done);
        if (over > 0) {
            store.drain(Map.of(gas, over), IGasHandler.Action.EXECUTE);
        }
        if (done < whole) {
            FluidConnection.failed(level, pos, "Gas handler's fill", null);
        }
        return done;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        try {
            IGasHandler store = store();
            if (store == null) {
                return FluidStack.EMPTY;
            }
            for (Tank held : tanks(store)) {
                if (mayLeaveAs(store, held, resource)) {
                    long whole = drain(store, held.gas(), Math.min(resource.getAmount(), held.millibuckets()), action);
                    return whole > 0 ? resource.copyWithAmount((int) whole) : FluidStack.EMPTY;
                }
            }
            return FluidStack.EMPTY;
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return FluidStack.EMPTY;
        }
    }

    /** Whether a tank's gas may leave as the fluid a drain names: one of its fluids at its temperature. */
    private static boolean mayLeaveAs(IGasHandler store, Tank held, FluidStack resource) {
        if (resource.is(held.fluid().fluid())) {
            return true;
        }
        for (FluidGases.Mapped mapped :
                FluidGases.leavingAs(held.gas(), store.contents().temperature())) {
            if (resource.is(mapped.fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        try {
            IGasHandler store = store();
            Tank held = store == null ? null : tank(store, 0);
            if (held == null) {
                return FluidStack.EMPTY;
            }
            long whole = drain(store, held.gas(), Math.min(maxDrain, held.millibuckets()), action);
            return whole > 0 ? new FluidStack(held.fluid().fluid(), (int) whole) : FluidStack.EMPTY;
        } catch (RuntimeException | LinkageError e) {
            FluidConnection.failed(level, pos, "Gas handler", e);
            return FluidStack.EMPTY;
        }
    }

    /**
     * Drains whole millibuckets of one gas from the store.
     *
     * @return how many it gave, or would give
     */
    private long drain(IGasHandler store, ResourceLocation gas, long amount, FluidAction action) {
        GasParcel would = store.drain(Map.of(gas, FluidGases.moles(amount)), IGasHandler.Action.SIMULATE);
        long whole = Math.min(amount, FluidGases.wholeMillibuckets(would.moles(gas)));
        if (whole <= 0 || !action.execute()) {
            return Math.max(0, whole);
        }
        GasParcel gave = store.drain(Map.of(gas, FluidGases.moles(whole)), IGasHandler.Action.EXECUTE);
        long done = Math.min(whole, FluidGases.wholeMillibuckets(gave.moles(gas)));
        // What it gave beyond whole millibuckets of the gas asked for goes back in.
        Map<ResourceLocation, Double> over = new TreeMap<>();
        gave.moles().forEach((id, n) -> {
            double more = n - (id.equals(gas) ? FluidGases.moles(done) : 0);
            if (more > 0) {
                over.put(id, more);
            }
        });
        if (!over.isEmpty()) {
            store.fill(new GasParcel(over, gave.temperature()), IGasHandler.Action.EXECUTE);
        }
        if (done < whole) {
            FluidConnection.failed(level, pos, "Gas handler's drain", null);
        }
        return done;
    }
}
