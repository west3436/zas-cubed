package io.github.west3436.zas.example;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/**
 * What the addon's gear gives its wearer (API-022). An implementation overrides only what it gives:
 * every part defaults to nothing. ZAS³ asks at each breath check, every 2 s by default, so keep these
 * cheap. Without ZAS³ nothing asks.
 */
public final class ExampleLifeSupport {
    /** Every part but a supply. A helmet counts for its slot's share of the body (LIFE-024). */
    public static final ILifeSupport PRESSURE_HELMET = new ILifeSupport() {
        @Override
        public double minPressure() {
            return 0; // kPa: down to vacuum
        }

        @Override
        public double maxPressure() {
            return 300; // kPa
        }

        @Override
        public double minTemperature() {
            return 200; // K
        }

        @Override
        public double maxTemperature() {
            return 400; // K
        }

        @Override
        public boolean sealedBreathing() {
            return true; // internals work
        }

        @Override
        public boolean gasFiltering() {
            return true; // the #zas:toxic gases stay out of the breath
        }

        @Override
        public double airflowResistance() {
            return 0.5; // half the push
        }
    };

    /** An entity's own protection covers all of it: strays don't feel the cold. */
    public static final ILifeSupport COLD_PROOF = new ILifeSupport() {
        @Override
        public double minTemperature() {
            return 0;
        }
    };

    private ExampleLifeSupport() {}

    /**
     * A tank of O₂ that internals breathe from (LIFE-028), reading and writing its gas on the stack as
     * it is when ZAS³ asks.
     *
     * @param stack the tank
     */
    public record OxygenTank(ItemStack stack) implements ILifeSupport, IInternalSupply {
        /** A full tank's O₂: about 6 atm in its {@value #VOLUME} L. */
        public static final double FULL_MOLES = 17.5;

        /** Its volume, in L. */
        public static final double VOLUME = 70;

        /** What it delivers at, in kPa: the O₂ of standard air. */
        public static final double DISTRIBUTION_PRESSURE = 21.3;

        @Override
        public Optional<IInternalSupply> internalSupply() {
            return Optional.of(this);
        }

        @Override
        public GasMixture contents() {
            return new GasMixture(Map.of(StandardAir.OXYGEN, moles()), StandardAir.TEMPERATURE, VOLUME);
        }

        @Override
        public double distributionPressure() {
            return DISTRIBUTION_PRESSURE;
        }

        @Override
        public GasParcel draw(double moles) {
            double taken = Math.min(moles, moles());
            stack.set(ExampleContent.OXYGEN_MOLES.get(), moles() - taken);
            return new GasParcel(Map.of(StandardAir.OXYGEN, taken), StandardAir.TEMPERATURE);
        }

        private double moles() {
            return stack.getOrDefault(ExampleContent.OXYGEN_MOLES.get(), 0.0);
        }
    }
}
