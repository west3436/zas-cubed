package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosCreativeTab;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.FireExtinguisherItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeTests {
    private RecipeTests() {}

    /**
     * The content mod's items with no recipe: the O2 and air tanks, made by filling an empty tank
     * from a canister (CONT-039), and the bike horn, found only in chests (CONT-154).
     */
    private static Set<Item> uncrafted() {
        return Set.of(AtmosItems.OXYGEN_TANK.get(), AtmosItems.AIR_TANK.get(), AtmosItems.BIKE_HORN.get());
    }

    /**
     * Every item the content mod registers but those of {@link #uncrafted}; not the test items the
     * GameTests add. A variant that is only a model or data on its item, such as a battery's charge,
     * the magnetic boots switched on, a transfer valve's tanks or a filled canister, is the same
     * item, so needs no recipe of its own.
     */
    private static List<Item> craftable() {
        List<Item> items = new ArrayList<>();
        for (DeferredHolder<Item, ? extends Item> entry : AtmosItems.ITEMS.getEntries()) {
            if (!uncrafted().contains(entry.get())) {
                items.add(entry.get());
            }
        }
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
     * ends in vanilla materials. Every item of the mod has a recipe that loads, but the O2 and air
     * tanks and the bike horn, which have none.
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
        List<Item> craftable = craftable();
        h.assertTrue(craftable.size() > 60, "only " + craftable.size() + " items of the mod");
        for (Item item : craftable) {
            h.assertTrue(made.contains(item), BuiltInRegistries.ITEM.getKey(item) + " has no recipe");
        }
        for (Item item : uncrafted()) {
            h.assertFalse(made.contains(item), BuiltInRegistries.ITEM.getKey(item) + " has a recipe");
        }
        h.succeed();
    }

    /**
     * The fire extinguisher's recipe (CONT-037): a lever over red dye and iron, with a water bucket
     * in the body. It comes out full, and the bucket comes back empty.
     */
    @GameTest(template = "empty")
    public static void cont_037_extinguisher_is_crafted_full_and_returns_the_bucket(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        CraftingInput input = CraftingInput.of(
                3,
                3,
                List.of(
                        ItemStack.EMPTY,
                        new ItemStack(Items.LEVER),
                        ItemStack.EMPTY,
                        iron,
                        new ItemStack(Items.RED_DYE),
                        iron,
                        iron,
                        new ItemStack(Items.WATER_BUCKET),
                        iron));
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElse(null);
        h.assertTrue(recipe != null, "nothing crafts from the extinguisher's pattern");
        ItemStack out = recipe.value().assemble(input, level.registryAccess());
        h.assertTrue(out.is(AtmosItems.FIRE_EXTINGUISHER.get()), "the pattern makes " + out);
        h.assertValueEqual(FireExtinguisherItem.charges(out), FireExtinguisherItem.CHARGES, "its charges");
        long buckets = recipe.value().getRemainingItems(input).stream()
                .filter(s -> s.is(Items.BUCKET))
                .count();
        h.assertValueEqual(buckets, 1L, "empty buckets left in the grid");
        h.succeed();
    }

    /**
     * Filled canisters are creative-only (CONT-086): one recipe makes a canister, and it makes an
     * empty one; the creative tab lists an empty canister and a filled one of each built-in gas.
     */
    @GameTest(template = "empty")
    public static void cont_086_filled_canisters_are_creative_only(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Item canister = AtmosItems.CANISTER.get();
        int recipes = 0;
        for (RecipeHolder<?> r : level.getRecipeManager().getRecipes()) {
            ItemStack out = r.value().getResultItem(level.registryAccess());
            if (out.is(canister)) {
                recipes++;
                h.assertTrue(out.get(AtmosComponents.GAS.get()) == null, r.id() + " makes a filled canister: " + out);
            }
        }
        h.assertValueEqual(recipes, 1, "recipes that make a canister");

        CreativeModeTab tab = AtmosCreativeTab.TAB.get();
        tab.buildContents(
                new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        int empty = 0;
        Set<ResourceLocation> gases = new HashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (!stack.is(canister)) {
                continue;
            }
            GasParcel gas = stack.get(AtmosComponents.GAS.get());
            if (gas == null || gas.isEmpty()) {
                empty++;
            } else {
                h.assertValueEqual(gas.moles().size(), 1, "the gases in " + stack);
                gases.addAll(gas.moles().keySet());
            }
        }
        h.assertValueEqual(empty, 1, "empty canisters in the creative tab");
        Set<ResourceLocation> builtIn = new HashSet<>();
        for (String gas : List.of(
                "oxygen",
                "nitrogen",
                "carbon_dioxide",
                "nitrous_oxide",
                "hydrogen",
                "water_vapor",
                "methane",
                "carbon_monoxide")) {
            builtIn.add(ResourceLocation.fromNamespaceAndPath("zas", gas));
        }
        h.assertValueEqual(gases, builtIn, "the gases of the creative canisters");
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
