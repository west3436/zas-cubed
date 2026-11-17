package io.github.west3436.zas.atmos.fluid;

import io.github.west3436.zas.api.capability.GasHandlerView;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasPurity;
import io.github.west3436.zas.api.gas.Millibuckets;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's fluid tanks as ZAS³ sees them (COMPAT-141): the gas handler of a block that offers
 * a fluid handler on the same face. A pipe grows an arm to it and shares gas with it as with any gas
 * handler (API-025): each atmos tick it moves each gas toward an equal concentration on both sides.
 * Nothing is kept here: every call reads or changes the tanks, converting at the unit factor
 * (COMPAT-143) through the fluid tags' gases (COMPAT-140).
 *
 * <p><b>Where.</b> On a face whose tanks hold a mapped fluid, or have an empty tank that would hold
 * one, or would take one on a simulated fill. A tank of water, or one that holds only water, offers
 * nothing, so no pipe reaches toward it. A tank's {@code isFluidValid} answers without regard to
 * what it holds, so an empty output tank of a machine stays joined while it waits for its gas. A
 * tank that accepts any fluid is joined while it is empty or holds a gas, and the pipe's arm goes
 * if another fluid is put in it.
 *
 * <p><b>What a pipe sees.</b> One vessel: the mapped fluids in those tanks, as their gases, in a
 * volume that is the size of the tanks, at the unit factor. A fluid tank has no pressure, so a full
 * tank reads as one atmosphere at 293.15 K: a pipe above that fills it, and a pipe below it drains
 * it, as far as the tanks let it.
 *
 * <p><b>In.</b> What a pipe offers follows {@link GasPurity}, as the Mekanism adapter's does: the
 * parcel's main gas alone, as much of it as may leave, if the parcel is nearly all that gas and the
 * tanks hold no other gas. It goes in as the first of the gas's fluids that the tanks take, trying
 * a fluid they already hold first, then the others hottest first ({@link FluidGases#leavingAs}): a
 * gas enters as a fluid only once it is at least as hot as that fluid (COMPAT-145).
 *
 * <p><b>Out.</b> Fluids leave as their gases at their mapping's temperature (COMPAT-145).
 *
 * <p><b>Whole millibuckets</b> (COMPAT-144). Moles are rounded down to whole millibuckets before
 * anything moves, and the answer is the gas of exactly that many. The fraction stays with whoever
 * offered or asked: in the pipe on the way in, in the tank on the way out. Nothing is made or lost.
 *
 * <p><b>For no face in particular</b> the view is only read, for what shows a block's gas, as the
 * analyzer does. The view asks for the block's fluid handler anew on each call, so one that is kept
 * holds nothing once the block has gone or changed.
 */
final class FluidsAsGas implements GasHandlerView {
    /** A vessel with no room and no gas: a pipe's sharing moves nothing into a volume this small. */
    static final GasMixture NOTHING = new GasMixture(Map.of(), Millibuckets.STANDARD_TEMPERATURE, 1e-30);

    private final ServerLevel level;
    private final BlockPos pos;

    /** The face it is a view of, or null for the whole block, which is only read. */
    @Nullable
    private final Direction side;

    private FluidsAsGas(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    /** The capability provider: a view wherever the tanks hold a gas, or would. */
    @Nullable
    static IGasHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        IFluidHandler tanks = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, entity, side);
        return tanks == null || FluidConnection.isView(tanks) || !joins(tanks)
                ? null
                : new FluidsAsGas(server, pos, side);
    }

    /** Whether the tanks hold a mapped fluid, or would. */
    static boolean joins(IFluidHandler tanks) {
        List<FluidGases.Mapped> sources = FluidGases.sources();
        if (sources.isEmpty()) {
            return false;
        }
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            if (counts(tanks, tank, sources)) {
                return true;
            }
        }
        for (FluidGases.Mapped mapped : sources) {
            if (tanks.fill(mapped.unit(), IFluidHandler.FluidAction.SIMULATE) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a tank is part of the vessel: it holds a mapped fluid, or is empty and would. */
    private static boolean counts(IFluidHandler tanks, int tank, List<FluidGases.Mapped> sources) {
        FluidStack held = tanks.getFluidInTank(tank);
        if (!held.isEmpty()) {
            return FluidGases.mapping(held).isPresent();
        }
        for (FluidGases.Mapped mapped : sources) {
            if (tanks.isFluidValid(tank, mapped.unit())) {
                return true;
            }
        }
        return false;
    }

    /** The block's fluid handler now, or null if it has none of its own any more. */
    @Nullable
    private IFluidHandler tanks() {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IFluidHandler tanks = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        return FluidConnection.isView(tanks) ? null : tanks;
    }

    /** The volume the tanks stand for, in litres: those that hold a mapped fluid or would. */
    private static double litres(IFluidHandler tanks) {
        List<FluidGases.Mapped> sources = FluidGases.sources();
        double millibuckets = 0;
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            if (counts(tanks, tank, sources)) {
                millibuckets += Math.max(0, tanks.getTankCapacity(tank));
            }
        }
        return millibuckets * Millibuckets.litresPerMillibucket();
    }

    @Override
    public GasMixture contents() {
        IFluidHandler tanks = tanks();
        double litres = tanks == null ? 0 : litres(tanks);
        if (!(litres > 0) || !Double.isFinite(litres)) {
            return NOTHING;
        }
        GasParcel held = GasParcel.EMPTY;
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            FluidStack fluid = tanks.getFluidInTank(tank);
            FluidGases.Mapped mapped = FluidGases.mapping(fluid).orElse(null);
            if (mapped != null) {
                GasParcel gas = mapped.gas(fluid, fluid.getAmount());
                held = held.isEmpty() ? gas : gas.isEmpty() ? held : GasProperties.mix(held, gas);
            }
        }
        double temperature = held.isEmpty() ? Millibuckets.STANDARD_TEMPERATURE : held.temperature();
        return new GasMixture(held.moles(), temperature, litres);
    }

    @Override
    public GasParcel fill(GasParcel gas, Action action) {
        IFluidHandler tanks = side == null ? null : tanks();
        if (tanks == null || gas.isEmpty()) {
            return GasParcel.EMPTY;
        }
        Map<ResourceLocation, Double> took = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> entering : entering(tanks, gas).entrySet()) {
            ResourceLocation id = entering.getKey();
            long offered = Math.min(Integer.MAX_VALUE, FluidGases.wholeMillibuckets(entering.getValue()));
            if (offered <= 0) {
                continue;
            }
            for (FluidGases.Mapped mapped : candidates(tanks, id, gas.temperature())) {
                FluidStack stack = new FluidStack(mapped.fluid(), (int) offered);
                int would = Math.min((int) offered, tanks.fill(stack, IFluidHandler.FluidAction.SIMULATE));
                if (would <= 0) {
                    continue;
                }
                int entered = action == Action.EXECUTE
                        ? Math.min(would, tanks.fill(stack.copyWithAmount(would), IFluidHandler.FluidAction.EXECUTE))
                        : would;
                if (entered > 0) {
                    took.put(id, FluidGases.moles(entered));
                }
                break;
            }
        }
        return took.isEmpty() ? GasParcel.EMPTY : new GasParcel(took, gas.temperature());
    }

    /**
     * The fluids a gas may enter as, in the order to try them: those the tanks hold already first,
     * then the rest hottest first.
     */
    private static List<FluidGases.Mapped> candidates(IFluidHandler tanks, ResourceLocation gas, double temperature) {
        List<FluidGases.Mapped> all = FluidGases.leavingAs(gas, temperature);
        if (all.size() < 2) {
            return all;
        }
        List<FluidGases.Mapped> held = new ArrayList<>();
        List<FluidGases.Mapped> rest = new ArrayList<>();
        for (FluidGases.Mapped mapped : all) {
            (holds(tanks, mapped) ? held : rest).add(mapped);
        }
        held.addAll(rest);
        return held;
    }

    private static boolean holds(IFluidHandler tanks, FluidGases.Mapped mapped) {
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            if (tanks.getFluidInTank(tank).is(mapped.fluid())) {
                return true;
            }
        }
        return false;
    }

    /** How much of each gas of a parcel may go in, by {@link GasPurity}. */
    private static Map<ResourceLocation, Double> entering(IFluidHandler tanks, GasParcel gas) {
        double share = AtmosServerConfig.get(AtmosServerConfig.FLUID_MIN_PURITY);
        if (!(share > 0)) {
            return gas.moles();
        }
        GasPurity.Leaving main = GasPurity.leaving(gas.moles(), share).orElse(null);
        if (main == null) {
            return Map.of();
        }
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            FluidGases.Mapped held =
                    FluidGases.mapping(tanks.getFluidInTank(tank)).orElse(null);
            if (held != null && !held.gas().equals(main.gas())) {
                return Map.of();
            }
        }
        return Map.of(main.gas(), main.moles());
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
        IFluidHandler tanks = side == null ? null : tanks();
        if (tanks == null) {
            return GasParcel.EMPTY;
        }
        IFluidHandler.FluidAction fluidAction =
                action == Action.EXECUTE ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        Map<ResourceLocation, Double> wanted = new TreeMap<>(moles);
        GasParcel gave = GasParcel.EMPTY;
        for (int tank = 0; tank < tanks.getTanks(); tank++) {
            FluidStack held = tanks.getFluidInTank(tank);
            FluidGases.Mapped mapped = FluidGases.mapping(held).orElse(null);
            if (mapped == null) {
                continue;
            }
            long whole =
                    Math.min(held.getAmount(), FluidGases.wholeMillibuckets(wanted.getOrDefault(mapped.gas(), 0.0)));
            if (whole <= 0) {
                continue;
            }
            FluidStack out = tanks.drain(held.copyWithAmount((int) whole), fluidAction);
            if (out.isEmpty() || !FluidStack.isSameFluidSameComponents(out, held) || out.getAmount() > whole) {
                if (!out.isEmpty() && action == Action.EXECUTE) {
                    // Not what was asked for, so not something to turn into gas: back it goes.
                    tanks.fill(out, IFluidHandler.FluidAction.EXECUTE);
                    FluidConnection.failed(level, pos, "Fluid handler's drain", null);
                }
                continue;
            }
            GasParcel gas = mapped.gas(out, out.getAmount());
            wanted.merge(mapped.gas(), -gas.moles(mapped.gas()), Double::sum);
            gave = gave.isEmpty() ? gas : GasProperties.mix(gave, gas);
        }
        return gave;
    }
}
