package io.github.west3436.zas.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.Icon;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import io.github.west3436.zas.compat.viewer.ViewerLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * A gas's page or a reaction's as an EMI recipe (COMPAT-062). It is laid out and drawn by the code
 * the JEI plugin uses, so the two viewers show the same page. EMI's recipe tree is off for both: a
 * reaction isn't crafting, and a gas's page gives what it takes.
 */
final class ViewerRecipe implements EmiRecipe {
    private final EmiRecipeCategory category;
    private final ResourceLocation id;
    private final List<GasStack> inputs;
    private final List<GasStack> catalysts;
    private final List<GasStack> outputs;
    private final Supplier<ViewerLayout.Page> page;

    private ViewerRecipe(
            EmiRecipeCategory category,
            ResourceLocation id,
            List<GasStack> inputs,
            List<GasStack> catalysts,
            List<GasStack> outputs,
            Supplier<ViewerLayout.Page> page) {
        this.category = category;
        this.id = id;
        this.inputs = inputs;
        this.catalysts = catalysts;
        this.outputs = outputs;
        this.page = page;
    }

    /** A gas's page, which comes up with both the gas's recipes and its uses (COMPAT-061). */
    static ViewerRecipe of(EmiRecipeCategory category, GasEntry gas) {
        List<GasStack> itself = List.of(GasStack.of(gas));
        return new ViewerRecipe(
                category,
                id("gas", gas.namespace(), gas.path()),
                itself,
                List.of(),
                itself,
                () -> ViewerDraw.page(gas));
    }

    /** A reaction's page (COMPAT-060). Its gases never change; its values are read when it is shown. */
    static ViewerRecipe of(EmiRecipeCategory category, ViewerData.Reaction reaction) {
        return new ViewerRecipe(
                category,
                id("reaction", reaction.namespace(), reaction.path()),
                reaction.now().reactants(),
                reaction.now().catalysts(),
                reaction.now().products(),
                () -> ViewerDraw.page(reaction.now()));
    }

    /** EMI's form for the id of a recipe that isn't in the recipe manager: a path that starts with a slash. */
    private static ResourceLocation id(String kind, String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "/" + kind + "/" + namespace + "/" + path);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return category;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return new ArrayList<>(stacks(inputs));
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return new ArrayList<>(stacks(catalysts));
    }

    @Override
    public List<EmiStack> getOutputs() {
        return stacks(outputs);
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public int getDisplayWidth() {
        return ViewerLayout.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return page.get().height();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        ViewerLayout.Page laidOut = page.get();
        if (laidOut.arrowX() >= 0) {
            widgets.addTexture(
                    EmiTexture.EMPTY_ARROW,
                    laidOut.arrowX() + (ViewerLayout.ARROW_WIDTH - EmiTexture.EMPTY_ARROW.width) / 2,
                    laidOut.arrowY() + (ViewerLayout.SLOT - EmiTexture.EMPTY_ARROW.height) / 2);
        }
        widgets.addDrawable(
                0,
                0,
                laidOut.width(),
                laidOut.height(),
                (graphics, mouseX, mouseY, delta) -> ViewerDraw.page(graphics, laidOut));
        for (ViewerLayout.PlacedChip chip : laidOut.chips()) {
            widgets.addTooltipText(
                    List.of(ViewerDraw.text(chip.chip().hint())), chip.x(), chip.y(), chip.width(), Icon.SIZE);
        }
        for (ViewerLayout.Slot slot : laidOut.slots()) {
            SlotWidget widget = widgets.addSlot(new GasEmiStack(slot.stack()), slot.x(), slot.y())
                    .catalyst(slot.role() == ViewerLayout.Role.CATALYST);
            // Only what the reaction gives is "made by" this page.
            if (slot.role() == ViewerLayout.Role.OUTPUT) {
                widget.recipeContext(this);
            }
        }
    }

    private static List<EmiStack> stacks(List<GasStack> gases) {
        List<EmiStack> out = new ArrayList<>(gases.size());
        gases.forEach(gas -> out.add(new GasEmiStack(gas)));
        return out;
    }
}
