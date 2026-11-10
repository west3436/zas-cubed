package io.github.west3436.zas.compat.mekanism;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import java.util.Optional;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.radiation.IRadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * What an exhaust port does with the chemicals a tube or a machine offers it (MEK-022): it takes
 * those the chemical map makes a gas and releases them at once in front of its grille, converted at
 * the unit factor. It is Mekanism's chemical handler for the port's back face, with one tank that
 * is always empty.
 *
 * <p>It uses none of the venting hooks' code: not the mixins, which a new Mekanism build may leave
 * unapplied, and not {@link Venting}, which only they call. The port works whether or not they do,
 * and whether or not {@code [venting] enabled} is on, which is the hooks' switch.
 *
 * <p>What it takes of an amount offered:
 *
 * <ul>
 *   <li><b>Nothing of a chemical that isn't mapped.</b> The chemical stays where it was.
 *   <li><b>Nothing while its face is blocked</b>: the block in front of the grille leaves no room
 *       for gas, or the atmosphere there isn't resolved. Released into a full block, the gas would
 *       come out round a corner or through a wall.
 *   <li><b>No more than the venting's cap leaves it this second</b> (MEK-021), counted for each
 *       port. Unlike a hook, which deletes what is above the cap, a port leaves it in the tube. With
 *       {@code [venting] release_above_cap} on it takes all it is offered.
 * </ul>
 *
 * <p>What it takes is gone from Mekanism: it never gives anything back, and offers nothing to
 * ZAS³'s pipes. The gas arrives at the temperature of the air it joins, or the chemical's least
 * temperature if that is higher, as steam's is.
 *
 * <p><b>Radiation</b> (MEK-023). No shipped mapping is for a radioactive chemical. If a datapack
 * maps one, the port applies the radiation of what it takes once, through Mekanism's radiation
 * manager, at the port: the moment the chemical leaves Mekanism for the atmosphere.
 *
 * <p>Server thread only, as Mekanism's networks and machines call it.
 */
final class ExhaustPortHandler implements IChemicalHandler {
    /**
     * How far in front of the port's face the gas is released: the middle of the first sixteenth,
     * so that of a block with several spaces, such as a slab's or a pane's, it is the one at the
     * grille.
     */
    private static final double OUTLET_DEPTH = 1.0 / 32;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** What each port has taken this second. The hooks' sources are counted apart, in their own. */
    private static final VentBudget BUDGET = new VentBudget();

    /** Whether a failure has been logged in full; the rest are logged at debug level. */
    private static boolean failureLogged;

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockPos front;
    private final Vec3 outlet;

    /**
     * @param level the port's level
     * @param pos the port's position
     * @param facing the way its grille faces
     */
    ExhaustPortHandler(ServerLevel level, BlockPos pos, Direction facing) {
        this.level = level;
        this.pos = pos.immutable();
        this.front = this.pos.relative(facing);
        this.outlet = Vec3.atCenterOf(this.pos).relative(facing, 0.5 + OUTLET_DEPTH);
    }

    /** Forgets what each port has taken this second. Called when a server stops. */
    static void forget() {
        BUDGET.clear();
    }

    @Override
    public int getChemicalTanks() {
        return 1;
    }

    /** Always empty: what the port takes is released at once. */
    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return ChemicalStack.EMPTY;
    }

    /** Ignored: the port holds nothing. */
    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {}

    /** The most the port takes in a second, which is the nearest it has to a capacity. */
    @Override
    public long getChemicalTankCapacity(int tank) {
        if (tank != 0) {
            return 0;
        }
        return MekanismSettings.ventingReleasesAboveCap() ? Long.MAX_VALUE : MekanismSettings.ventingMaxPerSecond();
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return tank == 0 && !stack.isEmpty() && ChemicalGases.mapping(stack).isPresent();
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return tank == 0 ? insertChemical(stack, action) : stack;
    }

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        long taken;
        try {
            taken = take(stack, action.execute());
        } catch (RuntimeException | LinkageError e) {
            // This runs inside Mekanism's own tick, which a failure here must never stop.
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.error(
                        "The exhaust port at {} failed to release {}, so it took none; further failures are"
                                + " logged at debug level",
                        pos,
                        stack,
                        e);
            } else {
                LOGGER.debug("The exhaust port at {} failed to release {}", pos, stack, e);
            }
            taken = 0;
        }
        if (taken <= 0) {
            return stack;
        }
        return taken >= stack.getAmount() ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - taken);
    }

    /** Nothing: the port only takes. */
    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        return ChemicalStack.EMPTY;
    }

    /** Nothing: the port only takes. */
    @Override
    public ChemicalStack extractChemical(long amount, Action action) {
        return ChemicalStack.EMPTY;
    }

    /** Nothing: the port only takes. */
    @Override
    public ChemicalStack extractChemical(ChemicalStack stack, Action action) {
        return ChemicalStack.EMPTY;
    }

    /**
     * How much of an offer the port takes, releasing it if {@code execute} says so.
     *
     * @return the mB taken, from 0 to the amount offered
     */
    private long take(ChemicalStack offered, boolean execute) {
        if (offered.isEmpty()) {
            return 0;
        }
        Optional<ChemicalMapping> mapping = ChemicalGases.mapping(offered);
        if (mapping.isEmpty() || !Atmospheres.holdsGas(level, front)) {
            return 0;
        }
        AtmosphereSample air = Atmospheres.at(level, outlet);
        if (air.state() == AtmosphereState.UNRESOLVED) {
            return 0;
        }
        long amount = offered.getAmount();
        boolean capped = !MekanismSettings.ventingReleasesAboveCap();
        GlobalPos port = GlobalPos.of(level.dimension(), pos);
        long tick = level.getServer().getTickCount();
        long cap = MekanismSettings.ventingMaxPerSecond();
        if (capped) {
            amount = Math.min(amount, BUDGET.left(port, tick, cap));
        }
        if (amount <= 0 || !execute) {
            return Math.max(amount, 0);
        }
        GasParcel gas = mapping.get().gas(amount, ChemicalGases.molesPerMillibucket(), temperature(air));
        GasMutations.add(level, outlet, gas, ExhaustPort.SOURCE);
        // From here the gas is released, so the amount is taken whatever else happens.
        if (capped) {
            BUDGET.grant(port, tick, amount, cap);
        }
        if (offered.getChemical().isRadioactive()) {
            radiate(offered.copyWithAmount(amount));
        }
        return amount;
    }

    /** Applies a taken chemical's radiation at the port, once, as Mekanism does where it voids. */
    private void radiate(ChemicalStack taken) {
        try {
            IRadiationManager.INSTANCE.dumpRadiation(level, pos, taken);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("The exhaust port at {} released {} without its radiation", pos, taken, e);
        }
    }

    /**
     * The temperature released gas starts at: the air's in front of the grille. A vacuum has none,
     * so gas released into one starts at standard air's.
     */
    private static double temperature(AtmosphereSample air) {
        double temperature = air.temperature();
        return temperature > 0 && Double.isFinite(temperature) ? temperature : StandardAir.TEMPERATURE;
    }
}
