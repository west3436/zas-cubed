package io.github.west3436.zas.compat.slots.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Accessories adapter with the content mod's tanks, masks and suits (COMPAT-041, COMPAT-042, COMPAT-043).
 * {@link Scenarios} has what each test checks.
 *
 * <p>The build turns this namespace on only for a run that loads a supported Accessories and has the
 * content on. The class names nothing of Accessories', so it loads in every run.
 */
@GameTestHolder(AccessoriesContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AccessoriesContentTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_accessories_content";

    private AccessoriesContentTests() {}

    /** Internals look in Accessories slots after the armor slots and before the offhand (COMPAT-041). */
    @GameTest(template = "empty")
    public static void compat_041_internals_look_in_accessories_slots_after_the_armor_and_before_the_offhand(
            GameTestHelper h) {
        Scenarios.internalsOrder(h, new AccessoriesKit());
        h.succeed();
    }

    /** A mask in Accessories' face slot seals and filters, and covers none of the body (COMPAT-042). */
    @GameTest(template = "empty")
    public static void compat_042_a_mask_in_the_accessories_face_slot_is_the_head_piece_and_covers_none_of_the_body(
            GameTestHelper h) {
        Scenarios.headPiece(h, new AccessoriesKit());
        h.succeed();
    }

    /** Tanks fit Accessories' back and belt slots, and masks its face slot (COMPAT-043). */
    @GameTest(template = "empty")
    public static void compat_043_tanks_fit_the_accessories_back_and_belt_slots_and_masks_the_face_slot(
            GameTestHelper h) {
        Scenarios.gearFitsItsSlots(h, new AccessoriesKit());
        h.succeed();
    }
}
