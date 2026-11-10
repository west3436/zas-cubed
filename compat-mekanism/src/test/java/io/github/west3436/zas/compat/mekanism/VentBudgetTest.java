package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The venting's cap: so much from each source in each second (MEK-021). */
class VentBudgetTest {
    private static final long CAP = 1_000;

    /** A source gets what it asks for until its second's cap is used up, and then nothing. */
    @Test
    void mek_021_a_source_is_granted_up_to_its_cap_in_one_second() {
        VentBudget budget = new VentBudget();
        assertEquals(400, budget.grant("tank", 0, 400, CAP));
        assertEquals(400, budget.grant("tank", 5, 400, CAP));
        // The third asks for more than is left, and gets what is left.
        assertEquals(200, budget.grant("tank", 19, 400, CAP));
        assertEquals(0, budget.grant("tank", 19, 400, CAP));
        // One large amount is cut to the cap at once.
        assertEquals(CAP, budget.grant("turbine", 19, 50_000, CAP));
        assertEquals(0, budget.grant("turbine", 19, 1, CAP));
    }

    /** The cap is counted for each source on its own: one source's use leaves another's whole. */
    @Test
    void mek_021_each_source_has_a_cap_of_its_own() {
        VentBudget budget = new VentBudget();
        assertEquals(CAP, budget.grant("first", 3, 5_000, CAP));
        assertEquals(CAP, budget.grant("second", 3, 5_000, CAP));
        assertEquals(0, budget.grant("first", 4, 5_000, CAP));
        assertEquals(2, budget.sources());
    }

    /** Twenty ticks are one second, and every count starts again with the next. */
    @Test
    void mek_021_the_count_starts_again_each_second() {
        VentBudget budget = new VentBudget();
        assertEquals(20, VentBudget.WINDOW_TICKS);
        assertEquals(CAP, budget.grant("tank", 0, CAP, CAP));
        assertEquals(0, budget.grant("tank", 19, CAP, CAP));
        assertEquals(CAP, budget.grant("tank", 20, CAP, CAP));
        assertEquals(0, budget.grant("tank", 39, CAP, CAP));
        assertEquals(CAP, budget.grant("tank", 40, CAP, CAP));
        // A tick count that went back, as a new server's does, is another second too.
        assertEquals(CAP, budget.grant("tank", 7, CAP, CAP));
        // And a negative one is counted in the same twenty-tick steps.
        assertEquals(CAP, budget.grant("tank", -1, CAP, CAP));
        assertEquals(0, budget.grant("tank", -20, CAP, CAP));
        assertEquals(CAP, budget.grant("tank", -21, CAP, CAP));
    }

    /** The record holds only the current second's sources, so it never grows with the world's age. */
    @Test
    void mek_021_only_the_current_seconds_sources_are_kept() {
        VentBudget budget = new VentBudget();
        for (int source = 0; source < 500; source++) {
            budget.grant(source, 0, 1, CAP);
        }
        assertEquals(500, budget.sources());
        budget.grant("one", 20, 1, CAP);
        assertEquals(1, budget.sources());
        budget.clear();
        assertEquals(0, budget.sources());
        assertEquals(CAP, budget.grant("one", 20, CAP, CAP));
    }

    /** A cap of nothing grants nothing, and nothing is granted of an amount that isn't one. */
    @Test
    void mek_021_a_cap_of_zero_grants_nothing() {
        VentBudget budget = new VentBudget();
        assertEquals(0, budget.grant("tank", 0, 400, 0));
        assertEquals(0, budget.grant("tank", 0, 400, -5));
        assertEquals(0, budget.grant("tank", 0, 0, CAP));
        assertEquals(0, budget.grant("tank", 0, -7, CAP));
        // None of those used any of the cap.
        assertEquals(CAP, budget.grant("tank", 0, CAP, CAP));
        // The largest cap there is never overflows.
        assertEquals(Long.MAX_VALUE, budget.grant("huge", 0, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0, budget.grant("huge", 0, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    /** The defaults: on, 20,000 mB a second from each source, and the rest deleted. */
    @Test
    void mek_021_the_defaults_cap_a_source_at_twenty_thousand_millibuckets_a_second() {
        assertEquals(true, VentBudget.DEFAULT_ENABLED);
        assertEquals(20_000L, VentBudget.DEFAULT_MAX_PER_SECOND);
        assertEquals(false, VentBudget.DEFAULT_RELEASE_ABOVE_CAP);
        // With no server config loaded, the settings answer with them.
        assertEquals(VentBudget.DEFAULT_ENABLED, MekanismSettings.ventingEnabled());
        assertEquals(VentBudget.DEFAULT_MAX_PER_SECOND, MekanismSettings.ventingMaxPerSecond());
        assertEquals(VentBudget.DEFAULT_RELEASE_ABOVE_CAP, MekanismSettings.ventingReleasesAboveCap());
    }
}
