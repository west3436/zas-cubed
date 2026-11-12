package io.github.west3436.zas.compat.slots.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Curios adapter with the content mod's tanks, masks and suits (COMPAT-040, COMPAT-042, COMPAT-043).
 * {@link Scenarios} has what each test checks.
 *
 * <p>The build turns this namespace on only for a run that loads a supported Curios and has the
 * content on. The class names nothing of Curios', so it loads in every run.
 */
@GameTestHolder(CuriosContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CuriosContentTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_curios_content";

    private CuriosContentTests() {}

    /** Internals look in Curios slots after the armor slots and before the offhand (COMPAT-040). */
    @GameTest(template = "empty")
    public static void compat_040_internals_look_in_curios_slots_after_the_armor_and_before_the_offhand(
            GameTestHelper h) {
        Scenarios.internalsOrder(h, new CuriosKit());
        h.succeed();
    }

    /** A mask in Curios' head slot seals and filters, and covers none of the body (COMPAT-042). */
    @GameTest(template = "empty")
    public static void compat_042_a_mask_in_the_curios_head_slot_is_the_head_piece_and_covers_none_of_the_body(
            GameTestHelper h) {
        Scenarios.headPiece(h, new CuriosKit());
        h.succeed();
    }

    /** Tanks fit Curios' back and belt slots, and masks its head slot (COMPAT-043). */
    @GameTest(template = "empty")
    public static void compat_043_tanks_fit_the_curios_back_and_belt_slots_and_masks_the_head_slot(GameTestHelper h) {
        Scenarios.gearFitsItsSlots(h, new CuriosKit());
        h.succeed();
    }
}
