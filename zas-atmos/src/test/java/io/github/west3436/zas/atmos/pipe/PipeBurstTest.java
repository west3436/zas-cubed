package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** The pipe burst's threshold and choice (CONT-108). */
class PipeBurstTest {
    private static final double OVER = PipeBurst.THRESHOLD_KPA;

    @Test
    void cont_108_the_threshold_is_100_atm_over_the_cell() {
        assertEquals(100 * 101.325, OVER, 1e-9);
        assertFalse(PipeBurst.exceeds(OVER - 0.01, 0), "just below, over a vacuum");
        assertFalse(PipeBurst.exceeds(OVER, 0), "exactly 100 atm isn't more than it");
        assertTrue(PipeBurst.exceeds(OVER + 0.01, 0), "just above, over a vacuum");
        assertFalse(PipeBurst.exceeds(OVER + 50, 101.325), "above 100 atm, but not over the cell");
        assertTrue(PipeBurst.exceeds(OVER + 101.325 + 0.01, 101.325), "just above, over 1 atm");
        assertFalse(PipeBurst.exceeds(OVER * 2, Double.NaN), "a member that can't burst");
    }

    @Test
    void cont_108_only_a_segment_over_the_threshold_is_chosen_and_each_can_be() {
        Map<String, Double> cells = Map.of("low", 100.0, "alsoLow", 200.0, "high", 5000.0, "device", Double.NaN);
        List<String> members = List.of("low", "device", "high", "alsoLow");
        double network = OVER + 300;
        RandomSource random = RandomSource.create(1);
        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            chosen.add(PipeBurst.choose(members, network, cells::get, random));
        }
        assertEquals(Set.of("low", "alsoLow"), chosen);
    }

    @Test
    void cont_108_none_is_chosen_when_none_is_over_the_threshold() {
        assertNull(PipeBurst.choose(List.of("a", "b"), OVER - 1, m -> 0.0, RandomSource.create(1)));
    }

    @Test
    void cont_108_a_seed_gives_the_same_choice() {
        List<Integer> members = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(
                    PipeBurst.choose(members, OVER + 1, m -> 0.0, RandomSource.create(seed)),
                    PipeBurst.choose(members, OVER + 1, m -> 0.0, RandomSource.create(seed)));
        }
    }
}
