package io.github.west3436.zas.compat.mekanism;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Optional;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.radiation.IRadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Releases as gas what Mekanism is deleting (MEK-020): the one place the adapter's hooks into
 * Mekanism's dumping hand a voided chemical to, so each hook only has to see the amount and say
 * where it leaves.
 *
 * <p>What it does with a voided chemical, in order:
 *
 * <ol>
 *   <li>Nothing, if the venting is switched off or the chemical map doesn't make the chemical a gas
 *       ({@link ChemicalGases#mapping}): Mekanism has deleted it, as it does without ZAS³.
 *   <li>Cuts the amount to what the source's cap still allows this second (MEK-021), unless the
 *       setting says to release what is above the cap as well. The rest stays deleted.
 *   <li>Adds that amount, converted at the unit factor, at the outlet through {@link GasMutations}.
 *       The API resolves the point: the block's own cell, or for a full block the nearest cell
 *       beside it, or the open air if every side is solid. The gas arrives at the temperature of
 *       the air it is released into, or the chemical's least temperature if that is higher, as
 *       steam's is.
 *   <li>Applies the radiation of what it released, if the chemical is radioactive and Mekanism
 *       doesn't radiate on that path itself (MEK-023).
 * </ol>
 *
 * <p><b>Radiation.</b> No shipped mapping is for a radioactive chemical; a datapack may add one.
 * Mekanism radiates what it voids on two of its paths, a gauge dropper emptying a tank and a broken
 * tube, and on the others deletes silently. A hook says which it is on with {@link Radiation}, and
 * the radiation is then applied once whatever the path: by Mekanism where it does so, in full, and
 * otherwise here, for the amount released.
 *
 * <p><b>Failing.</b> A hook runs inside Mekanism's own tick. Should releasing ever throw, the
 * chemical stays deleted, as without ZAS³, and the failure is logged: the first in full, the rest at
 * debug level.
 *
 * <p>Everything here is the public API's and Mekanism's API's, so a mod with its own source of
 * waste gas can copy it. Server thread only.
 */
public final class Venting {
    /** Who the released gas is from, in ZAS³'s ledger and logs. */
    public static final SourceId SOURCE = SourceId.of("zas", "mekanism_venting");

    /** Who applies a voided chemical's radiation. */
    public enum Radiation {
        /** Mekanism radiates what it voids on this path itself, so the venting leaves it alone. */
        BY_MEKANISM,
        /** Mekanism voids silently on this path, so the venting radiates what it releases. */
        BY_VENTING
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final VentBudget BUDGET = new VentBudget();

    /** Whether a failure has been logged in full; the rest are logged at debug level. */
    private static boolean failureLogged;

    private Venting() {}

    /**
     * Releases a chemical Mekanism has voided, or is about to, as gas.
     *
     * @param level the level it is voided in; nothing happens on a client's
     * @param source the block the cap counts for and radiation is applied at: the machine, the
     *     broken tube, or one fixed block of a multiblock
     * @param outlet the point the gas leaves at, which need not be in {@code source}
     * @param voided the chemical and the amount voided
     * @param radiation who applies its radiation on this path
     * @return the mB released as gas, from 0 to the amount voided
     */
    @ServerThread
    public static long release(Level level, BlockPos source, Vec3 outlet, ChemicalStack voided, Radiation radiation) {
        if (voided.isEmpty() || !(level instanceof ServerLevel server) || !MekanismSettings.ventingEnabled()) {
            return 0;
        }
        try {
            return releaseMapped(server, source, outlet, voided, radiation);
        } catch (RuntimeException | LinkageError e) {
            // A hook runs inside Mekanism's own tick, which a failure here must never stop.
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.error(
                        "Releasing {} voided at {} failed, so Mekanism deleted it; further failures are"
                                + " logged at debug level",
                        voided,
                        source,
                        e);
            } else {
                LOGGER.debug("Releasing {} voided at {} failed", voided, source, e);
            }
            return 0;
        }
    }

    private static long releaseMapped(
            ServerLevel server, BlockPos source, Vec3 outlet, ChemicalStack voided, Radiation radiation) {
        Optional<ChemicalMapping> mapping = ChemicalGases.mapping(voided);
        if (mapping.isEmpty()) {
            return 0;
        }
        long amount = voided.getAmount();
        if (!MekanismSettings.ventingReleasesAboveCap()) {
            amount = BUDGET.grant(
                    GlobalPos.of(server.dimension(), source.immutable()),
                    server.getServer().getTickCount(),
                    amount,
                    MekanismSettings.ventingMaxPerSecond());
        }
        if (amount <= 0) {
            return 0;
        }
        GasParcel gas = mapping.get().gas(amount, ChemicalGases.molesPerMillibucket(), temperatureAt(server, outlet));
        GasMutations.add(server, outlet, gas, SOURCE);
        if (radiation == Radiation.BY_VENTING && voided.getChemical().isRadioactive()) {
            IRadiationManager.INSTANCE.dumpRadiation(server, source, voided.copyWithAmount(amount));
        }
        return amount;
    }

    /** Forgets what each source has released this second. Called when a server stops. */
    static void forget() {
        BUDGET.clear();
    }

    /**
     * The temperature gas released at a point starts at: the air's there. A vacuum has none, so
     * gas released into one starts at standard air's.
     */
    private static double temperatureAt(ServerLevel level, Vec3 point) {
        double temperature = Atmospheres.at(level, point).temperature();
        return temperature > 0 && Double.isFinite(temperature) ? temperature : StandardAir.TEMPERATURE;
    }
}
