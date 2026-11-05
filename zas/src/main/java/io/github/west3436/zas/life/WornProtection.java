package io.github.west3436.zas.life;

import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.ProtectionConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * An entity's protection where it is now (LIFE-020, LIFE-024, LIFE-025): the life support of the
 * pieces in its armor slots, each covering its slot's share of the body ({@link ProtectionConfig}),
 * and its own, which covers all of it ({@link Protections}).
 *
 * <p><b>Coverage.</b> A piece protects against a pressure P below 1 atm if its {@link
 * ILifeSupport#minPressure()} is at most P, and above 1 atm if its {@link
 * ILifeSupport#maxPressure()} is at least P: a low-pressure rating below 1 atm, a high-pressure one
 * above. Its thermal range covers a temperature T other than 293.15 K if T is within {@link
 * ILifeSupport#minTemperature()} to {@link ILifeSupport#maxTemperature()}. At exactly 1 atm or
 * 293.15 K nothing needs protecting, so nothing covers. The coverage c is the sum of the shares
 * of the pieces that protect, at most 1.
 *
 * <p>(1 − c⁵) of the pressure difference reaches the entity: without a helmet, c is at most 0.7
 * and 83 % still does (LIFE-020). (1 − c) of temperature damage is dealt (LIFE-025).
 *
 * <p>Server thread; it reads the entity's equipment once, when made.
 */
public final class WornProtection implements Shielding {
    private final List<Piece> pieces;

    /**
     * One source of life support and the share of the body it covers.
     *
     * @param slot its armor slot, or null for the entity's own
     */
    private record Piece(@Nullable EquipmentSlot slot, ILifeSupport support, double share) {}

    private WornProtection(List<Piece> pieces) {
        this.pieces = pieces;
    }

    /** An entity's protection now: {@link Shielding#NONE} if nothing gives it any. */
    public static Shielding of(LivingEntity entity) {
        List<Piece> pieces = pieces(entity);
        return pieces.isEmpty() ? Shielding.NONE : new WornProtection(pieces);
    }

    private static List<Piece> pieces(LivingEntity entity) {
        List<Piece> pieces = new ArrayList<>(5);
        for (EquipmentSlot slot : ProtectionConfig.SLOTS) {
            ILifeSupport s = Protections.of(entity.getItemBySlot(slot));
            if (s != null) {
                pieces.add(new Piece(slot, s, ProtectionConfig.coverage(slot)));
            }
        }
        ILifeSupport own = Protections.of(entity);
        if (own != null) {
            pieces.add(new Piece(null, own, 1));
        }
        return pieces;
    }

    /** The coverage c at a pressure in kPa, 0 to 1. */
    public double pressureCoverage(double pressure) {
        double c = 0;
        for (Piece p : pieces) {
            if (coversPressure(p.support, pressure)) {
                c += p.share;
            }
        }
        return total(c);
    }

    /** The coverage c at a temperature in K, 0 to 1. */
    public double thermalCoverage(double temperature) {
        double c = 0;
        for (Piece p : pieces) {
            if (coversTemperature(p.support, temperature)) {
                c += p.share;
            }
        }
        return total(c);
    }

    /**
     * A sum of shares as a coverage: at most 1, and 1 when rounding leaves it just short, as 0.3 +
     * 0.4 + 0.2 + 0.1 does, so a full suit lets nothing through.
     */
    private static double total(double c) {
        return c >= 1 - 1e-9 ? 1 : c;
    }

    @Override
    public double pressureFactor(double pressure) {
        return pressureFactorFor(pressureCoverage(pressure));
    }

    @Override
    public double thermalFactor(double temperature) {
        return 1 - thermalCoverage(temperature);
    }

    /** LIFE-020's fraction of the pressure difference that gets through a coverage: 1 − c⁵. */
    public static double pressureFactorFor(double coverage) {
        double c = Math.min(1, Math.max(0, coverage));
        return 1 - c * c * c * c * c;
    }

    /** Whether a piece is rated for a pressure in kPa, on its side of 1 atm. */
    public static boolean coversPressure(ILifeSupport s, double pressure) {
        if (pressure < StandardAir.PRESSURE) {
            return s.minPressure() <= pressure;
        }
        return pressure > StandardAir.PRESSURE && s.maxPressure() >= pressure;
    }

    /** Whether a piece's thermal range holds a temperature in K other than 293.15 K. */
    public static boolean coversTemperature(ILifeSupport s, double temperature) {
        return temperature != StandardAir.TEMPERATURE
                && s.minTemperature() <= temperature
                && temperature <= s.maxTemperature();
    }

    /**
     * Whether an entity's breathing is sealed, which internals need (LIFE-021): by the item in its
     * head slot, or by its own life support. Server thread.
     */
    public static boolean sealedBreathing(LivingEntity entity) {
        ILifeSupport head = Protections.of(entity.getItemBySlot(EquipmentSlot.HEAD));
        ILifeSupport own = Protections.of(entity);
        return (head != null && head.sealedBreathing()) || (own != null && own.sealedBreathing());
    }

    /**
     * Whether an entity's breath is filtered (API-022, CONT-088): by the item in its head slot, or by
     * its own life support. Server thread.
     */
    public static boolean gasFiltering(LivingEntity entity) {
        ILifeSupport head = Protections.of(entity.getItemBySlot(EquipmentSlot.HEAD));
        ILifeSupport own = Protections.of(entity);
        return (head != null && head.gasFiltering()) || (own != null && own.gasFiltering());
    }

    /**
     * An entity's airflow resistance, 0 to 1 (FLOW-012, API-022): the highest of its armor pieces'
     * and its own. Server thread.
     */
    public static double airflowResistance(LivingEntity entity) {
        double r = 0;
        for (Piece p : pieces(entity)) {
            double v = p.support.airflowResistance();
            if (v > r) {
                r = v;
            }
        }
        return Math.min(1, r);
    }
}
