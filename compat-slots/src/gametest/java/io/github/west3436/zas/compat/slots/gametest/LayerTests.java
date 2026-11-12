package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.accessories.AccessoriesCompat;
import io.github.west3436.zas.compat.curios.CuriosCompat;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Accessories with its compatibility layer for Curios (COMPAT-040, COMPAT-041). The layer answers to
 * the mod id {@code curios} and serves Curios' API from Accessories' own slots, so one item would be
 * read through both adapters. The Curios adapter therefore registers nothing beside the layer, and
 * the Accessories adapter reads the slots: each worn item is given once.
 *
 * <p>The build turns this namespace on only for the run that loads the layer. The class names
 * nothing of either target, so it loads in every run.
 */
@GameTestHolder(LayerTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class LayerTests {
    /** The namespace of these tests. */
    public static final String NAMESPACE = "zas_slots_layer";

    private LayerTests() {}

    /**
     * The layer is the {@code curios} the game reports, in a version the Curios adapter supports,
     * so zas starts that adapter; it registers no slots. An item put in a slot through Curios' API
     * is in Accessories' slot of that place, and is given once, by the Accessories adapter.
     */
    @GameTest(template = "empty")
    public static void compat_040_beside_accessories_curios_layer_each_worn_item_is_given_once(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded(CuriosCompat.LAYER), "the layer isn't installed");
        Status status = Slots.status(h, Slots.CURIOS);
        h.assertValueEqual(status.state(), State.ACTIVE, "the Curios adapter's state");
        h.assertFalse(CuriosCompat.isActive(), "the Curios adapter reads slots beside the layer");
        h.assertFalse(Slots.registered(CuriosCompat.ID), CuriosCompat.ID + " is registered");
        h.assertTrue(Slots.registered(AccessoriesCompat.ID), AccessoriesCompat.ID + " isn't registered");

        Kit curios = new CuriosKit();
        Kit accessories = new AccessoriesKit();
        FakePlayer p = Slots.player(h, "zas_slots_layer");
        h.assertTrue(
                curios.slotTypes(p).containsAll(List.of(curios.head(), curios.back())),
                "the layer gives a player no head or back slot: " + curios.slotTypes(p));
        curios.equip(h, p, curios.back(), new ItemStack(Items.STICK));
        h.assertTrue(
                accessories.worn(h, p, accessories.back()).is(Items.STICK),
                "the stick put in through Curios' API isn't in Accessories' back slot");
        Slots.Worn one = Slots.worn(p);
        h.assertValueEqual(one.stacks().size(), 1, "items given with a stick in the back slot");
        h.assertTrue(one.stacks().get(0) == accessories.worn(h, p, accessories.back()), "it isn't that stick");
        h.assertValueEqual(one.kinds(), List.of(ExtraSlotKind.OTHER), "its slot's kind");

        // Curios' head slot is Accessories' hat slot, a head slot.
        curios.equip(h, p, curios.head(), new ItemStack(Items.CARVED_PUMPKIN));
        h.assertTrue(
                accessories.worn(h, p, "hat").is(Items.CARVED_PUMPKIN),
                "the pumpkin put in Curios' head slot isn't in Accessories' hat slot");
        Slots.Worn two = Slots.worn(p);
        h.assertValueEqual(two.stacks().size(), 2, "items given with a pumpkin on the head too");
        h.assertValueEqual(
                two.kinds().stream().filter(kind -> kind == ExtraSlotKind.HEAD).count(), 1L, "head pieces given");
        h.succeed();
    }
}
