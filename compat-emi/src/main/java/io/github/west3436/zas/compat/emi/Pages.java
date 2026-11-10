package io.github.west3436.zas.compat.emi;

import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import io.github.west3436.zas.compat.viewer.ViewerEntries;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What the plugin registers once its gate is open. It is apart from the plugin so that the plugin's
 * own class names nothing of EMI's that needs a client, and can be made anywhere.
 */
final class Pages {
    private Pages() {}

    /** Lets EMI save a favourited gas and load it back. */
    static void serializer(EmiInitRegistry registry) {
        registry.addIngredientSerializer(GasEmiStack.class, new GasEmiStack.Serializer());
    }

    /** Adds the two categories, every gas to EMI's list with its page, and every reaction's page. */
    static void register(EmiRegistry registry, List<GasEntry> gases, List<ViewerData.Reaction> reactions) {
        EmiRecipeCategory gasPages = new Category("gases", new GasEmiStack(GasStack.of(ViewerEntries.emblem(gases))));
        EmiRecipeCategory reactionPages =
                new Category("reactions", (graphics, x, y, delta) -> ViewerDraw.reactionsIcon(graphics, x, y));
        registry.addCategory(gasPages);
        registry.addCategory(reactionPages);
        for (GasEntry gas : gases) {
            registry.addEmiStack(new GasEmiStack(GasStack.of(gas)));
            registry.addRecipe(ViewerRecipe.of(gasPages, gas));
        }
        for (ViewerData.Reaction reaction : reactions) {
            registry.addRecipe(ViewerRecipe.of(reactionPages, reaction));
        }
    }

    /** A category named by the viewer's own translation key, the one the JEI plugin's has. */
    private static final class Category extends EmiRecipeCategory {
        private final String name;

        Category(String name, EmiRenderable icon) {
            super(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, name), icon);
            this.name = name;
        }

        @Override
        public Component getName() {
            return Component.translatable("viewer.zas." + name);
        }
    }
}
