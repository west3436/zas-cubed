package io.github.west3436.zas.compat.slots.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Curios adapter on a server with a Curios it supports (COMPAT-040, COMPAT-004): the parts that
 * need nothing of the content mod, so they also run with the content off (CONT-155). {@link
 * Scenarios} has what each test checks.
 *
 * <p>The build turns this namespace on only for a run that loads such a Curios. The class names
 * nothing of Curios', so it loads in every run.
 */
@GameTestHolder(CuriosTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CuriosTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_curios";

    private CuriosTests() {}

    /** zas starts the adapter for a supported Curios, and the adapter registers its slots (COMPAT-002). */
    @GameTest(template = "empty")
    public static void compat_002_a_supported_curios_starts_the_adapter(GameTestHelper h) {
        Scenarios.adapterStarted(h, new CuriosKit());
        h.succeed();
    }

    /** What an entity wears in Curios slots is given once, in Curios' order, with each slot's kind (COMPAT-040). */
    @GameTest(template = "empty")
    public static void compat_040_what_is_worn_in_curios_slots_is_given_once_and_in_order(GameTestHelper h) {
        Scenarios.givesWhatIsWorn(h, new CuriosKit());
        h.succeed();
    }

    /** The tags and the player's slots are there with the content, and not without it (COMPAT-043). */
    @GameTest(template = "empty")
    public static void compat_043_the_curios_slot_data_comes_with_the_content(GameTestHelper h) {
        Scenarios.dataFollowsTheContent(h, new CuriosKit());
        h.succeed();
    }
}
