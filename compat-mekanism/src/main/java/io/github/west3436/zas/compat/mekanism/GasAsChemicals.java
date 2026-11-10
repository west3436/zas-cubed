package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A ZAS³ gas store as Mekanism sees it (MEK-013): the chemical handler of a block that offers a gas
 * handler on the same face, such as a pipe. A tube connects to it, a machine ejects into it, and a
 * tube set to pull draws from it. Nothing is kept here: every call reads or changes the gas store
 * itself, converting at the unit factor (MEK-002) through the chemical map (MEK-010).
 *
 * <p><b>In.</b> Any mapped chemical enters, whatever the store holds, as far as the store takes it:
 * a pipe stops at its pressure limit. An unmapped chemical is refused whole. The gas arrives at
 * {@link ChemicalUnits#STANDARD_TEMPERATURE}, since a chemical has no temperature, or at its
 * mapping's least temperature if that is higher, as steam's is. A radioactive chemical that a pack
 * mapped releases its radiation here, once, as it arrives (MEK-023).
 *
 * <p><b>Out.</b> What the store shows as its tank follows {@link GasPurity}: the one gas it is
 * nearly all of, as much of it as may leave, or nothing; with the rule off, a tank for each mapped
 * gas. A gas shows as the
 * chemical its temperature picks, so hot water vapour shows as steam, and never as a radioactive
 * chemical.
 *
 * <p><b>Whole millibuckets.</b> Mekanism counts whole millibuckets and a store counts moles. Both
 * ways the amount is rounded down to whole millibuckets before anything moves, so what moves is
 * exactly that many, and the fraction left over stays where it was: in the store on the way out,
 * and in Mekanism, which never had a fraction, on the way in. Nothing is made or lost.
 *
 * <p>The view asks for the block's gas handler anew on each call, so it is safe to keep, as
 * Mekanism's tubes do: a block that has gone, or changed into something else, holds nothing.
 */
final class GasAsChemicals implements IChemicalHandler {
    /** What a probe for a store's room offers, in moles: more than any store takes. */
    private static final double PROBE_MOLES = 1e9;

    private final ServerLevel level;
    private final BlockPos pos;

    @Nullable
    private final Direction side;

    /** One tank the store shows: a gas, the chemical it leaves as, and its whole millibuckets. */
    private record Tank(ResourceLocation gas, Holder<Chemical> chemical, long millibuckets) {}

    private GasAsChemicals(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    /** The capability provider: a view wherever the block offers a gas handler of its own. */
    @Nullable
    static IChemicalHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        IGasHandler store = level.getCapability(IGasHandler.BLOCK, pos, state, entity, side);
        return store == null || ChemicalConnection.isView(store) ? null : new GasAsChemicals(server, pos, side);
    }

    /** The block's gas handler now, or null if it has none of its own any more. */
    @Nullable
    private IGasHandler store() {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IGasHandler store = level.getCapability(IGasHandler.BLOCK, pos, side);
        return ChemicalConnection.isView(store) ? null : store;
    }

    /** The tanks the store shows now, in the order of their gases' ids. */
    private static List<Tank> tanks(IGasHandler store) {
        GasMixture held = store.contents();
        double share = MekanismSettings.minPurity();
        Map<ResourceLocation, Double> leaving = share > 0
                ? GasPurity.leaving(held.moles(), share)
                        .map(main -> Map.of(main.gas(), main.moles()))
                        .orElse(Map.of())
                : held.moles();
        List<Tank> tanks = new ArrayList<>();
        leaving.forEach((gas, moles) -> {
            Holder<Chemical> chemical = ChemicalConnection.leavesAs(gas, held.temperature());
            long millibuckets = ChemicalGases.wholeMillibuckets(moles);
            if (chemical != null && millibuckets > 0) {
                tanks.add(new Tank(gas, chemical, millibuckets));
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
     * whoever goes through the tanks finds room for a chemical the store doesn't hold yet.
     */
    @Override
    public int getChemicalTanks() {
        if (MekanismSettings.minPurity() > 0) {
            return 1;
        }
        try {
            IGasHandler store = store();
            return store == null ? 1 : tanks(store).size() + 1;
        } catch (RuntimeException | LinkageError e) {
            ChemicalConnection.failed(level, pos, "Gas handler", e);
            return 1;
        }
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        try {
            IGasHandler store = store();
            Tank held = store == null ? null : tank(store, tank);
            return held == null ? ChemicalStack.EMPTY : new ChemicalStack(held.chemical(), held.millibuckets());
        } catch (RuntimeException | LinkageError e) {
            ChemicalConnection.failed(level, pos, "Gas handler", e);
            return ChemicalStack.EMPTY;
        }
    }

    /**
     * Nothing: the store's gas is its own, and can't be replaced from here. Mekanism's own handlers
     * ignore the call the same way where they are read-only.
     */
    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {}

    /**
     * What the tank holds and what more the store would take of that gas now. A store has no size in
     * millibuckets: a pipe's limit is a pressure, which depends on what it holds and how hot it is.
     * For an empty tank the room is that for the first mapped gas.
     */
    @Override
    public long getChemicalTankCapacity(int tank) {
        try {
            IGasHandler store = store();
            if (store == null) {
                return 0;
            }
            Tank held = tank(store, tank);
            ResourceLocation gas = held != null
                    ? held.gas()
                    : ChemicalGases.map().gases().stream().findFirst().orElse(null);
            if (gas == null) {
                return 0;
            }
            GasParcel probe = new GasParcel(Map.of(gas, PROBE_MOLES), ChemicalUnits.STANDARD_TEMPERATURE);
            long room = ChemicalGases.wholeMillibuckets(
                    store.fill(probe, IGasHandler.Action.SIMULATE).moles(gas));
            return (held == null ? 0 : held.millibuckets()) + room;
        } catch (RuntimeException | LinkageError e) {
            ChemicalConnection.failed(level, pos, "Gas handler", e);
            return 0;
        }
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return ChemicalGases.mapping(stack).isPresent();
    }

    /** Every tank is the same store, so a chemical goes in whichever tank is named. */
    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return insertChemical(stack, action);
    }

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        if (stack.isEmpty()) {
            return ChemicalStack.EMPTY;
        }
        ChemicalMapping mapping = ChemicalGases.mapping(stack).orElse(null);
        if (mapping == null) {
            return stack;
        }
        long amount = stack.getAmount();
        long taken;
        try {
            IGasHandler store = store();
            taken = store == null ? 0 : fill(store, mapping, amount, action.execute());
        } catch (RuntimeException | LinkageError e) {
            ChemicalConnection.failed(level, pos, "Gas handler", e);
            return stack;
        }
        if (taken <= 0) {
            return stack;
        }
        if (action.execute()) {
            ChemicalConnection.radiate(level, pos, stack.copyWithAmount(taken));
        }
        return taken >= amount ? ChemicalStack.EMPTY : stack.copyWithAmount(amount - taken);
    }

    /**
     * Fills the store with as many whole millibuckets of a chemical as it takes.
     *
     * @return how many it took, or would take
     */
    private long fill(IGasHandler store, ChemicalMapping mapping, long amount, boolean execute) {
        double factor = ChemicalGases.molesPerMillibucket();
        double temperature = ChemicalUnits.STANDARD_TEMPERATURE;
        GasParcel would = store.fill(mapping.gas(amount, factor, temperature), IGasHandler.Action.SIMULATE);
        long whole = Math.min(amount, mapping.wholeMillibuckets(would.moles(), factor));
        if (whole <= 0 || !execute) {
            return Math.max(0, whole);
        }
        GasParcel took = store.fill(mapping.gas(whole, factor, temperature), IGasHandler.Action.EXECUTE);
        long done = Math.min(whole, mapping.wholeMillibuckets(took.moles(), factor));
        if (done < whole) {
            // It took less than it said it would. Whole millibuckets count; the rest goes back out.
            Map<ResourceLocation, Double> over = over(took.moles(), mapping.gas(done, factor, temperature));
            if (!over.isEmpty()) {
                store.drain(over, IGasHandler.Action.EXECUTE);
            }
            ChemicalConnection.failed(level, pos, "Gas handler's fill", null);
        }
        return done;
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (amount <= 0) {
            return ChemicalStack.EMPTY;
        }
        try {
            IGasHandler store = store();
            Tank held = store == null ? null : tank(store, tank);
            if (held == null) {
                return ChemicalStack.EMPTY;
            }
            long whole = drain(store, held.gas(), Math.min(amount, held.millibuckets()), action.execute());
            return whole > 0 ? new ChemicalStack(held.chemical(), whole) : ChemicalStack.EMPTY;
        } catch (RuntimeException | LinkageError e) {
            ChemicalConnection.failed(level, pos, "Gas handler", e);
            return ChemicalStack.EMPTY;
        }
    }

    /**
     * Drains whole millibuckets of one gas from the store.
     *
     * @return how many it gave, or would give
     */
    private long drain(IGasHandler store, ResourceLocation gas, long amount, boolean execute) {
        GasParcel would = store.drain(Map.of(gas, ChemicalGases.moles(amount)), IGasHandler.Action.SIMULATE);
        long whole = Math.min(amount, ChemicalGases.wholeMillibuckets(would.moles(gas)));
        if (whole <= 0 || !execute) {
            return Math.max(0, whole);
        }
        double wanted = ChemicalGases.moles(whole);
        GasParcel gave = store.drain(Map.of(gas, wanted), IGasHandler.Action.EXECUTE);
        long done = Math.min(whole, ChemicalGases.wholeMillibuckets(gave.moles(gas)));
        // What it gave beyond whole millibuckets of the gas asked for goes back in.
        Map<ResourceLocation, Double> over =
                over(gave.moles(), new GasParcel(Map.of(gas, ChemicalGases.moles(done)), gave.temperature()));
        if (!over.isEmpty()) {
            store.fill(new GasParcel(over, gave.temperature()), IGasHandler.Action.EXECUTE);
        }
        if (done < whole) {
            ChemicalConnection.failed(level, pos, "Gas handler's drain", null);
        }
        return done;
    }

    /** What some moles hold beyond a parcel, gas by gas. */
    private static Map<ResourceLocation, Double> over(Map<ResourceLocation, Double> moles, GasParcel counted) {
        Map<ResourceLocation, Double> over = new TreeMap<>();
        moles.forEach((gas, n) -> {
            double more = n - counted.moles(gas);
            if (more > 0) {
                over.put(gas, more);
            }
        });
        return over;
    }
}
