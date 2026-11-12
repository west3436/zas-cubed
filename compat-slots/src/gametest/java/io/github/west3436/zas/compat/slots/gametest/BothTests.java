package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlots;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Curios and Accessories on one server, without Accessories' compatibility layer (COMPAT-040,
 * COMPAT-041): the two mods keep separate slots, both adapters run, and each worn item is given
 * once.
 *
 * <p>The build turns this namespace on only for the run that loads both. The class names nothing of
 * either target, so it loads in every run.
 */
@GameTestHolder(BothTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BothTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_slots_both";

    private BothTests() {}

    /**
     * Both adapters start, and their providers are asked in order of id: Accessories' slots, then
     * Curios'. An item in each mod's back slot is given once each, in that order, and a tank in each
     * is found in that order.
     */
    @GameTest(template = "empty")
    public static void compat_040_curios_and_accessories_slots_are_both_read_and_nothing_twice(GameTestHelper h) {
        Kit curios = new CuriosKit();
        Kit accessories = new AccessoriesKit();
        Scenarios.adapterStarted(h, curios);
        Scenarios.adapterStarted(h, accessories);
        List<ResourceLocation> ids = ExtraSlots.registered().stream()
                .map(ExtraSlots.Registration::id)
                .filter(id -> id.equals(curios.provider()) || id.equals(accessories.provider()))
                .toList();
        h.assertValueEqual(ids, List.of(accessories.provider(), curios.provider()), "the providers, in order");

        FakePlayer p = Slots.player(h, "zas_slots_both");
        accessories.equip(h, p, accessories.back(), new ItemStack(Items.STICK));
        Slots.Worn one = Slots.worn(p);
        h.assertValueEqual(one.stacks().size(), 1, "items given with a stick in Accessories' back slot");
        h.assertTrue(one.stacks().get(0) == accessories.worn(h, p, accessories.back()), "it isn't that stick");

        // A player's Curios slots come with the content (COMPAT-043); without it there are none to fill.
        if (!Slots.content()) {
            h.assertValueEqual(curios.slotTypes(p), List.of(), "a player's Curios slots without the content");
            h.succeed();
            return;
        }
        h.assertTrue(curios.worn(h, p, curios.back()).isEmpty(), "Curios' back slot holds Accessories' stick");
        curios.equip(h, p, curios.back(), new ItemStack(Items.BLAZE_ROD));
        h.assertTrue(
                accessories.worn(h, p, accessories.back()).is(Items.STICK),
                "Accessories' back slot lost its stick to Curios'");
        Slots.Worn two = Slots.worn(p);
        h.assertValueEqual(two.stacks().size(), 2, "items given with one in each mod's back slot");
        h.assertTrue(two.stacks().get(0).is(Items.STICK), "Accessories' item isn't first: " + two.stacks());
        h.assertTrue(two.stacks().get(1) == curios.worn(h, p, curios.back()), "the second isn't Curios' stack");
        h.assertValueEqual(two.kinds(), List.of(ExtraSlotKind.OTHER, ExtraSlotKind.OTHER), "the slots' kinds");

        Scenarios.tanksInBoth(h, p, accessories, curios);
        h.succeed();
    }
}
