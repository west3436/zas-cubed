package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.Reactions;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The reactions API without ZAS³ (API-019, API-020, D-17). */
class ReactionsTest {
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation O2 = ResourceLocation.fromNamespaceAndPath("zas", "oxygen");

    @Test
    void api_019_without_zas_a_mixture_does_not_react() {
        GasMixture fuel = new GasMixture(Map.of(H2, 2.0, O2, 1.0), 900, 70);
        assertSame(fuel, Reactions.applyStandalone(fuel, 1, true));
    }

    @Test
    void api_019_arguments_are_checked() {
        GasMixture fuel = new GasMixture(Map.of(H2, 2.0, O2, 1.0), 300, 70);
        assertThrows(IllegalArgumentException.class, () -> Reactions.applyStandalone(fuel, -1, true));
        assertThrows(IllegalArgumentException.class, () -> Reactions.applyStandalone(fuel, Double.NaN, true));
        assertThrows(NullPointerException.class, () -> Reactions.applyStandalone(null, 1, true));
    }

    @Test
    void api_020_a_change_rejects_negative_or_non_finite_amounts() {
        assertThrows(IllegalArgumentException.class, () -> new ReactionChange(Map.of(H2, -1.0), Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new ReactionChange(Map.of(), Map.of(O2, Double.NaN), 0));
        assertThrows(IllegalArgumentException.class, () -> new ReactionChange(Map.of(), Map.of(), Double.NaN));
    }
}
