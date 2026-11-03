package io.github.west3436.zas.engine.reaction;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ZAS³'s shipped gases with their shipped heat capacities, and its built-in reactions as the
 * datapack defines them (FIRE-003), for engine tests.
 */
final class Chemistry {
    static final GasRegistry REGISTRY = GasRegistry.of(List.of(
            gas("zas:carbon_dioxide", 0.044009, 28.81),
            gas("zas:carbon_monoxide", 0.028010, 20.83),
            gas("zas:hydrogen", 0.002016, 20.52),
            gas("zas:methane", 0.016043, 27.32),
            gas("zas:nitrogen", 0.028014, 20.81),
            gas("zas:nitrous_oxide", 0.044013, 30.30),
            gas("zas:oxygen", 0.031998, 21.06),
            gas("zas:water_vapor", 0.018015, 25.28)));

    static final int CO2 = REGISTRY.indexOf("zas:carbon_dioxide");
    static final int CO = REGISTRY.indexOf("zas:carbon_monoxide");
    static final int H2 = REGISTRY.indexOf("zas:hydrogen");
    static final int CH4 = REGISTRY.indexOf("zas:methane");
    static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    static final int N2O = REGISTRY.indexOf("zas:nitrous_oxide");
    static final int O2 = REGISTRY.indexOf("zas:oxygen");
    static final int H2O = REGISTRY.indexOf("zas:water_vapor");

    static final FireSettings FIRE = FireSettings.DEFAULT;

    static final ReactionDefinition HYDROGEN = ReactionDefinition.standard(
            "zas:hydrogen_combustion",
            Map.of("zas:hydrogen", 1.0, "zas:oxygen", 0.5),
            Map.of("zas:water_vapor", 1.0),
            -242_000,
            ReactionConditions.combustion(new ReactionConditions.FlammabilityLimits("zas:hydrogen", 0.04, 0.75, 858)),
            Optional.empty(),
            Optional.empty());

    static final ReactionDefinition METHANE = ReactionDefinition.standard(
            "zas:methane_combustion",
            Map.of("zas:methane", 1.0, "zas:oxygen", 2.0),
            Map.of("zas:carbon_dioxide", 1.0, "zas:water_vapor", 2.0),
            -802_000,
            ReactionConditions.combustion(new ReactionConditions.FlammabilityLimits("zas:methane", 0.05, 0.15, 810)),
            Optional.empty(),
            Optional.empty());

    static final ReactionDefinition CARBON_MONOXIDE = ReactionDefinition.standard(
            "zas:carbon_monoxide_combustion",
            Map.of("zas:carbon_monoxide", 1.0, "zas:oxygen", 0.5),
            Map.of("zas:carbon_dioxide", 1.0),
            -283_000,
            ReactionConditions.combustion(
                    new ReactionConditions.FlammabilityLimits("zas:carbon_monoxide", 0.125, 0.74, 882)),
            Optional.empty(),
            Optional.empty());

    static final ReactionSet COMBUSTION = compile(HYDROGEN, METHANE, CARBON_MONOXIDE);

    private Chemistry() {}

    static ReactionSet compile(ReactionDefinition... definitions) {
        List<String> skipped = new ArrayList<>();
        ReactionSet set = ReactionSet.compile(REGISTRY, List.of(definitions), (d, why) -> skipped.add(why));
        if (!skipped.isEmpty()) {
            throw new AssertionError("skipped: " + skipped);
        }
        return set;
    }

    /** A mixture of the given gas indices and moles, alternating. */
    static GasMixture mixture(double volume, double temperature, double... gasAndMoles) {
        GasMixture m = new GasMixture(REGISTRY, volume, temperature);
        for (int i = 0; i < gasAndMoles.length; i += 2) {
            m.setMoles((int) gasAndMoles[i], gasAndMoles[i + 1]);
        }
        return m;
    }

    /** Air at 293.15 K and 101.325 kPa in a volume, with a fuel making up {@code fuelFraction} of it. */
    static GasMixture fuelInAir(double volume, double temperature, int fuel, double fuelFraction) {
        double total = 101.325 * volume / (8.314462618 * temperature);
        double air = total * (1 - fuelFraction);
        return mixture(volume, temperature, fuel, total * fuelFraction, O2, air * 0.21, N2, air * 0.79);
    }

    static double[] moles(GasMixture m) {
        double[] out = new double[REGISTRY.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = m.moles(i);
        }
        return out;
    }

    private static GasDefinition gas(String id, double molarMass, double cv) {
        return new GasDefinition(id, "gas." + id.replace(':', '.'), id, molarMass, cv, Optional.empty());
    }
}
