package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeTests {
    private RecipeTests() {}

    /**
     * Every item the content mod registers today has a recipe, but the O2 and air tanks, which are
     * made by filling an empty tank (CONT-039). Items still to come bring theirs:
     * their recipes ship already and load once the item exists (docs/crafting.md).
     */
    private static List<Item> craftable() {
        List<Item> items = new ArrayList<>(List.of(
                AtmosItems.PIPE.get(),
                AtmosItems.PIPE_CAP.get(),
                AtmosItems.CONNECTOR_PORT.get(),
                AtmosItems.CANISTER.get(),
                AtmosItems.VENT.get(),
                AtmosItems.AREA_AIR_CONTROLLER.get(),
                AtmosItems.GENERATOR.get(),
                AtmosItems.MULTITOOL.get(),
                AtmosItems.EMPTY_TANK.get(),
                AtmosItems.EMERGENCY_OXYGEN_TANK.get(),
                AtmosItems.BREATH_MASK.get(),
                AtmosItems.SOFTSUIT_HELMET.get(),
                AtmosItems.SOFTSUIT_CHESTPLATE.get(),
                AtmosItems.SOFTSUIT_LEGGINGS.get(),
                AtmosItems.SOFTSUIT_BOOTS.get()));
        AtmosItems.components().stream().map(DeferredItem::get).forEach(items::add);
        return items;
    }

    private static List<RecipeHolder<?>> modRecipes(GameTestHelper h) {
        return h.getLevel().getRecipeManager().getRecipes().stream()
                .filter(r -> r.id().getNamespace().equals(ZasAtmosMod.MOD_ID))
                .toList();
    }

    /**
     * The content is craftable from vanilla materials (CONT-051): each recipe's ingredients are
     * vanilla items or the mod's own, and each of the mod's own has a recipe too, so every chain
     * ends in vanilla materials. Each registered item and crafting component has a recipe.
     */
    @GameTest(template = "empty")
    public static void cont_051_recipes_use_vanilla_materials(GameTestHelper h) {
        List<RecipeHolder<?>> recipes = modRecipes(h);
        Set<Item> made = new HashSet<>();
        for (RecipeHolder<?> r : recipes) {
            made.add(r.value().getResultItem(h.getLevel().registryAccess()).getItem());
        }
        for (RecipeHolder<?> r : recipes) {
            for (Ingredient ingredient : r.value().getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    String ns = id.getNamespace();
                    h.assertTrue(
                            ns.equals("minecraft") || ns.equals(ZasAtmosMod.MOD_ID),
                            r.id() + " uses " + id + ", which is neither vanilla nor the mod's own");
                    h.assertTrue(
                            !ns.equals(ZasAtmosMod.MOD_ID) || made.contains(stack.getItem()),
                            r.id() + " uses " + id + ", which has no recipe");
                }
            }
        }
        for (Item item : craftable()) {
            h.assertTrue(made.contains(item), BuiltInRegistries.ITEM.getKey(item) + " has no recipe");
        }
        h.succeed();
    }

    /** A data pack can override the mod's recipes (CONT-059): see {@link RecipeTestPack}. */
    @GameTest(template = "empty")
    public static void cont_059_datapack_overrides_recipe(GameTestHelper h) {
        RecipeHolder<?> cap = h.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "pipe_cap"))
                .orElse(null);
        h.assertTrue(cap != null, "zas_atmos:pipe_cap isn't loaded");
        ItemStack out = cap.value().getResultItem(h.getLevel().registryAccess());
        h.assertTrue(out.is(AtmosItems.PIPE_CAP.get()), "the pipe cap recipe makes " + out);
        h.assertTrue(
                out.getCount() == RecipeTestPack.PIPE_CAP_COUNT,
                "the pipe cap recipe makes " + out.getCount() + ", not the test pack's "
                        + RecipeTestPack.PIPE_CAP_COUNT);
        h.succeed();
    }
}
