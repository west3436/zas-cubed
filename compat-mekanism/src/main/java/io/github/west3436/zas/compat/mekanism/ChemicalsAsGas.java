package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * A Mekanism tank or machine as ZAS³ sees it (MEK-013): the gas handler of a block that offers a
 * chemical handler on the same face. A pipe grows an arm to it and shares gas with it as with any
 * gas handler (API-025): each atmos tick it moves each gas toward an equal concentration on both
 * sides. Nothing is kept here: every call reads or changes Mekanism's tanks, converting at the unit
 * factor (MEK-002) through the chemical map (MEK-010).
 *
 * <p><b>What a pipe sees.</b> One vessel: the mapped chemicals in the tanks on that face, as their
 * gases, in a volume that is the size of the tanks that hold a mapped chemical or are empty and
 * would. Mekanism's storage has no pressure (MEK-002), so a tank's size in millibuckets stands for
 * a volume at the unit factor, and a full tank reads as one atmosphere at 293.15 K: a pipe above
 * that fills it, and a pipe below it drains it, as far as Mekanism lets that face be filled or
 * drained.
 *
 * <p><b>Where.</b> On a face with a tank that could ever hold a mapped chemical, whatever it holds
 * now, so a pipe's arm doesn't come and go with a tank's contents, which would open the pipe's end.
 * While such a tank holds some other chemical it has no room and no gas: the pipe stays joined and
 * moves nothing. A face with no such tank offers nothing, so no pipe joins it. A handler's {@code
 * isValid} decides, and it promises only that false means never: Mekanism's own blocks answer true
 * for every chemical on a face, so a pipe joins every face of theirs that has a tank, as a tube
 * does, and where no mapped chemical can go in, nothing moves.
 *
 * <p><b>In.</b> What a pipe offers follows {@link GasPurity}: the parcel's main gas alone, as much
 * of it as may leave, if the parcel is nearly all that gas and the tanks on this face hold no other
 * gas; with the rule off, each mapped gas on its own. The second condition matters because a pipe
 * offers only what the vessel has less of: a tank already holding one gas of a mixture would
 * otherwise draw the other out. Judging the parcel is as good as judging the pipe: what a pipe
 * offers is never purer than the pipe, so the pipe is left at least as pure as the rule asks. A gas
 * whose chemical is radioactive never goes in (MEK-023). With the rule off, a simulated fill answers
 * for each gas as if it were the only one offered, so for several at once it can say more than a
 * real fill takes; a real fill's answer is what went in.
 *
 * <p><b>Out.</b> Chemicals leave as their gases at {@link ChemicalUnits#STANDARD_TEMPERATURE}, or
 * at their mapping's least temperature if that is higher. A radioactive chemical that a pack mapped
 * releases its radiation here, once, as it leaves Mekanism.
 *
 * <p><b>Whole millibuckets.</b> Moles are rounded down to whole millibuckets before anything moves,
 * and the answer is the gas of exactly that many. The fraction stays with whoever offered or asked:
 * in the pipe on the way in, in the tank on the way out. Nothing is made or lost.
 *
 * <p><b>For no face in particular</b> the view is of every tank the block has, whatever its faces
 * are set to, and it is only read: it takes nothing and gives nothing, since no pipe joins a block
 * that way. It is there for what shows a block's gas, as the content mod's analyzer does, so that a
 * tank reads as itself on a face whose side configuration is off.
 *
 * <p>The view asks for the block's chemical handler anew on each call, so one that is kept holds
 * nothing once the block has gone or changed.
 */
final class ChemicalsAsGas implements IGasHandler {
    /** A vessel with no room and no gas: a pipe's sharing moves nothing into a volume this small. */
    static final GasMixture NOTHING = new GasMixture(Map.of(), ChemicalUnits.STANDARD_TEMPERATURE, 1e-30);

    private final ServerLevel level;
    private final BlockPos pos;

    /** The face it is a view of, or null for the whole block, which is only read. */
    @Nullable
    private final Direction side;

    private ChemicalsAsGas(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    /**
     * The capability provider: a view wherever a face has a tank that could ever hold a mapped
     * chemical, and for no face in particular wherever the block has one.
     */
    @Nullable
    static IGasHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        IChemicalHandler tanks = level.getCapability(ChemicalConnection.CHEMICAL, pos, state, entity, side);
        return tanks == null || ChemicalConnection.isView(tanks) || !joins(tanks)
                ? null
                : new ChemicalsAsGas(server, pos, side);
    }

    /** Whether some tank could ever hold a mapped chemical, which Mekanism answers without regard to what it holds. */
    private static boolean joins(IChemicalHandler tanks) {
        Collection<Holder<Chemical>> mapped = ChemicalGases.chemicals();
        for (int tank = 0; tank < tanks.getChemicalTanks(); tank++) {
            if (wouldHold(tanks, tank, mapped)) {
                return true;
            }
        }
        return false;
    }

    /** The block's chemical handler now, or null if it has none of its own any more. */
    @Nullable
    private IChemicalHandler tanks() {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IChemicalHandler tanks = level.getCapability(ChemicalConnection.CHEMICAL, pos, side);
        return ChemicalConnection.isView(tanks) ? null : tanks;
    }

    /** The volume the tanks stand for, in litres: those that hold a mapped chemical or would. */
    private static double litres(IChemicalHandler tanks) {
        Collection<Holder<Chemical>> mapped = ChemicalGases.chemicals();
        double millibuckets = 0;
        for (int tank = 0; tank < tanks.getChemicalTanks(); tank++) {
            ChemicalStack held = tanks.getChemicalInTank(tank);
            if (held.isEmpty()
                    ? wouldHold(tanks, tank, mapped)
                    : ChemicalGases.mapping(held).isPresent()) {
                millibuckets += tanks.getChemicalTankCapacity(tank);
            }
        }
        return millibuckets * MekanismSettings.litresPerMillibucket();
    }

    private static boolean wouldHold(IChemicalHandler tanks, int tank, Collection<Holder<Chemical>> mapped) {
        for (Holder<Chemical> chemical : mapped) {
            if (tanks.isValid(tank, new ChemicalStack(chemical, 1))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GasMixture contents() {
        IChemicalHandler tanks = tanks();
        double litres = tanks == null ? 0 : litres(tanks);
        if (!(litres > 0) || !Double.isFinite(litres)) {
            return NOTHING;
        }
        double factor = ChemicalGases.molesPerMillibucket();
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        double temperature = ChemicalUnits.STANDARD_TEMPERATURE;
        for (int tank = 0; tank < tanks.getChemicalTanks(); tank++) {
            ChemicalStack held = tanks.getChemicalInTank(tank);
            ChemicalMapping mapping = ChemicalGases.mapping(held).orElse(null);
            if (mapping != null) {
                GasParcel gas = mapping.gas(held.getAmount(), factor, ChemicalUnits.STANDARD_TEMPERATURE);
                gas.moles().forEach((id, n) -> moles.merge(id, n, Double::sum));
                temperature = Math.max(temperature, gas.temperature());
            }
        }
        return new GasMixture(moles, temperature, litres);
    }

    @Override
    public GasParcel fill(GasParcel gas, Action action) {
        IChemicalHandler tanks = side == null ? null : tanks();
        if (tanks == null || gas.isEmpty()) {
            return GasParcel.EMPTY;
        }
        Map<ResourceLocation, Double> took = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> entering : entering(tanks, gas).entrySet()) {
            ResourceLocation id = entering.getKey();
            Holder<Chemical> chemical = ChemicalConnection.leavesAs(id, gas.temperature());
            long offered = ChemicalGases.wholeMillibuckets(entering.getValue());
            if (chemical == null || offered <= 0) {
                continue;
            }
            ChemicalStack left = tanks.insertChemical(new ChemicalStack(chemical, offered), mekanism(action));
            long entered = offered - left.getAmount();
            if (entered > 0) {
                took.put(id, ChemicalGases.moles(entered));
            }
        }
        return took.isEmpty() ? GasParcel.EMPTY : new GasParcel(took, gas.temperature());
    }

    /** How much of each gas of a parcel may go in, by {@link GasPurity}. */
    private static Map<ResourceLocation, Double> entering(IChemicalHandler tanks, GasParcel gas) {
        double share = MekanismSettings.minPurity();
        if (!(share > 0)) {
            return gas.moles();
        }
        GasPurity.Leaving main = GasPurity.leaving(gas.moles(), share).orElse(null);
        if (main == null) {
            return Map.of();
        }
        Set<ResourceLocation> only = Set.of(main.gas());
        for (int tank = 0; tank < tanks.getChemicalTanks(); tank++) {
            ChemicalMapping held =
                    ChemicalGases.mapping(tanks.getChemicalInTank(tank)).orElse(null);
            if (held != null && !held.gases().keySet().equals(only)) {
                return Map.of();
            }
        }
        return Map.of(main.gas(), main.moles());
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
        IChemicalHandler tanks = side == null ? null : tanks();
        if (tanks == null) {
            return GasParcel.EMPTY;
        }
        double factor = ChemicalGases.molesPerMillibucket();
        Map<ResourceLocation, Double> wanted = new TreeMap<>(moles);
        GasParcel gave = GasParcel.EMPTY;
        for (int tank = 0; tank < tanks.getChemicalTanks(); tank++) {
            ChemicalStack held = tanks.getChemicalInTank(tank);
            ChemicalMapping mapping = ChemicalGases.mapping(held).orElse(null);
            if (mapping == null) {
                continue;
            }
            // A chemical that is several gases leaves only as far as every one of them is wanted.
            long whole = Math.min(held.getAmount(), mapping.wholeMillibuckets(wanted, factor));
            if (whole <= 0) {
                continue;
            }
            ChemicalStack left = tanks.extractChemical(tank, whole, mekanism(action));
            if (left.isEmpty() || !ChemicalStack.isSameChemical(left, held)) {
                if (!left.isEmpty() && action == Action.EXECUTE) {
                    // Not what the tank showed, so not something to turn into gas: back it goes.
                    tanks.insertChemical(left, mekanism.api.Action.EXECUTE);
                    ChemicalConnection.failed(level, pos, "Chemical handler's extract", null);
                }
                continue;
            }
            GasParcel gas = mapping.gas(left.getAmount(), factor, ChemicalUnits.STANDARD_TEMPERATURE);
            gas.moles().forEach((id, n) -> wanted.merge(id, -n, Double::sum));
            gave = gave.isEmpty() ? gas : GasProperties.mix(gave, gas);
            if (action == Action.EXECUTE) {
                ChemicalConnection.radiate(level, pos, left);
            }
        }
        return gave;
    }

    private static mekanism.api.Action mekanism(Action action) {
        return action == Action.EXECUTE ? mekanism.api.Action.EXECUTE : mekanism.api.Action.SIMULATE;
    }
}
