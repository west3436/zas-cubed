package io.github.west3436.zas.engine.gas;

import io.github.west3436.zas.engine.EngineLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Made-up gases with round numbers, so expected values are easy to compute by hand. */
final class TestGases {
    static final GasDefinition ALPHA = gas("test:alpha", 0.030, 20);
    static final GasDefinition BETA = gas("test:beta", 0.040, 30);
    static final GasDefinition GAMMA = gas("test:gamma", 0.004, 12.5);

    /** Listed out of order on purpose; the registry sorts by id. */
    static final GasRegistry REGISTRY = GasRegistry.of(List.of(GAMMA, ALPHA, BETA));

    static final int A = REGISTRY.indexOf("test:alpha");
    static final int B = REGISTRY.indexOf("test:beta");
    static final int G = REGISTRY.indexOf("test:gamma");

    private TestGases() {}

    static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(5), molarMass, heatCapacity, Optional.empty());
    }

    static GasMixture mixture(double volume, double temperature, double alpha, double beta, double gamma) {
        return mixture(GasConfig.DEFAULT, volume, temperature, alpha, beta, gamma);
    }

    static GasMixture mixture(
            GasConfig config, double volume, double temperature, double alpha, double beta, double gamma) {
        GasMixture m = new GasMixture(REGISTRY, config, volume, temperature);
        m.setMoles(A, alpha);
        m.setMoles(B, beta);
        m.setMoles(G, gamma);
        return m;
    }

    /** An {@link EngineLog} that keeps every message. */
    static final class RecordingLog implements EngineLog {
        final List<String> messages = new ArrayList<>();

        @Override
        public void warn(String message) {
            messages.add(message);
        }
    }
}
