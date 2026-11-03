package io.github.west3436.zas.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.ReactionType;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Reaction types implemented in code (API-020), named from datapack definitions. */
class CodeTypesTest {
    private static final ResourceLocation CONVERT = ResourceLocation.fromNamespaceAndPath("zas_test", "convert");
    private static final ResourceLocation BROKEN = ResourceLocation.fromNamespaceAndPath("zas_test", "broken");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");
    private static final ResourceLocation N2 = ResourceLocation.fromNamespaceAndPath("zas", "nitrogen");

    /** Turns a fraction of the N2O it may use into N2, the fraction given by its parameters. */
    private record Convert(double fraction) implements ReactionType {
        private static final Codec<Double> FRACTION =
                Codec.DOUBLE.fieldOf("fraction").codec();

        @Override
        public ReactionChange react(io.github.west3436.zas.api.reaction.ReactionContext context) {
            double n = context.available(N2O) * fraction;
            return new ReactionChange(Map.of(N2O, n), Map.of(N2, n), 1000 * n);
        }

        @Override
        public ReactionType withParameters(Dynamic<?> parameters) {
            return new Convert(FRACTION.parse(parameters).getOrThrow(IllegalArgumentException::new));
        }
    }

    @BeforeAll
    static void register() {
        if (Reactions.type(CONVERT).isEmpty()) {
            Reactions.registerType(CONVERT, new Convert(0));
        }
        if (Reactions.type(BROKEN).isEmpty()) {
            Reactions.registerType(BROKEN, context -> {
                throw new IllegalStateException("a test type that always fails");
            });
        }
    }

    private static ReactionSet compile(String json) throws IOException {
        ReactionDefinition d = ReactionFormatTest.parse(json).orElseThrow();
        List<String> skipped = new ArrayList<>();
        ReactionSet set =
                ReactionSet.compile(ReactionFormatTest.shippedGases(), List.of(d), (def, why) -> skipped.add(why));
        assertEquals(List.of(), skipped);
        return set;
    }

    @Test
    void api_020_a_definition_runs_a_registered_type_with_its_parameters() throws IOException {
        ReactionSet set = compile("""
                {"type": "zas_test:convert", "parameters": {"fraction": 0.25}, "min_temperature": 500}
                """);
        GasRegistry gases = set.registry();
        GasMixture m = new GasMixture(gases, 1000, 600);
        m.setMoles(gases.indexOf("zas:nitrous_oxide"), 4);
        double energy = m.heatCapacity() * m.temperature();

        assertTrue(set.react(m, 1, FireSettings.DEFAULT).reacted());

        assertEquals(3, m.moles(gases.indexOf("zas:nitrous_oxide")), 1e-12);
        assertEquals(1, m.moles(gases.indexOf("zas:nitrogen")), 1e-12);
        assertEquals(energy + 1000, m.heatCapacity() * m.temperature(), 1e-6);

        // The definition's own conditions still apply: below 500 K it isn't called.
        GasMixture cold = new GasMixture(gases, 1000, 300);
        cold.setMoles(gases.indexOf("zas:nitrous_oxide"), 4);
        assertFalse(set.react(cold, 1, FireSettings.DEFAULT).reacted());
    }

    @Test
    void api_020_a_failing_type_changes_nothing() throws IOException {
        ReactionSet set = compile("{\"type\": \"zas_test:broken\"}");
        GasRegistry gases = set.registry();
        GasMixture m = new GasMixture(gases, 1000, 300);
        m.setMoles(gases.indexOf("zas:nitrous_oxide"), 4);
        for (int i = 0; i < 3; i++) {
            assertFalse(set.react(m, 1, FireSettings.DEFAULT).reacted());
        }
        assertEquals(4, m.moles(gases.indexOf("zas:nitrous_oxide")));
    }

    @Test
    void api_020_invalid_definitions_of_code_types_are_skipped_naming_the_field() {
        ReactionFormatTest.assertSkipped(
                "{\"type\": \"zas_test:convert\", \"parameters\": {\"fraction\": \"x\"}}", "parameters:");
        ReactionFormatTest.assertSkipped("{\"type\": \"zas_test:convert\"}", "parameters:");
        ReactionFormatTest.assertSkipped("{\"type\": \"zas_test:unregistered\"}", "type:");
        ReactionFormatTest.assertSkipped(
                "{\"reactants\": {\"zas:oxygen\": 1}, \"enthalpy\": 0, \"parameters\": {}}", "parameters:");
        ReactionFormatTest.assertSkipped(
                "{\"type\": \"zas_test:convert\", \"parameters\": {\"fraction\": 0.5}, \"rate\": {\"per_second\": 1}}",
                "rate:");
    }

    @Test
    void api_020_types_are_registered_once() {
        assertThrows(IllegalArgumentException.class, () -> Reactions.registerType(CONVERT, new Convert(1)));
        assertThrows(IllegalArgumentException.class, () -> Reactions.registerType(Reactions.STANDARD, new Convert(1)));
        assertTrue(Reactions.type(CONVERT).isPresent());
    }
}
