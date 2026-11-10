package io.github.west3436.zas.compat.jei;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import io.github.west3436.zas.compat.viewer.ViewerEntries;
import io.github.west3436.zas.compat.viewer.ViewerLayout;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The gases (COMPAT-061): a page for each, with its name, formula, molar mass, heat capacity and
 * tags. A gas's page comes up with both its recipes and its uses.
 */
final class GasCategory implements IRecipeCategory<GasEntry> {
    static final RecipeType<GasEntry> TYPE = RecipeType.create(ZasMod.MOD_ID, "gases", GasEntry.class);

    private final List<GasEntry> gases;
    private final IDrawable icon;
    private int height = -1;

    GasCategory(IGuiHelper gui, List<GasEntry> gases) {
        this.gases = gases;
        this.icon = gui.createDrawableIngredient(GasIngredients.TYPE, GasStack.of(ViewerEntries.emblem(gases)));
    }

    @Override
    public RecipeType<GasEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("viewer.zas.gases");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return ViewerLayout.WIDTH;
    }

    /** JEI gives every page of a category one height: the tallest gas's. */
    @Override
    public int getHeight() {
        if (height < 0) {
            height = gases.stream()
                    .mapToInt(gas -> ViewerDraw.page(gas).height())
                    .max()
                    .orElse(ViewerLayout.SLOT);
        }
        return height;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GasEntry gas, IFocusGroup focuses) {
        Pages.slots(builder, ViewerDraw.page(gas));
        // The slot is an input, so this makes the page one of the gas's recipes too.
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addIngredient(GasIngredients.TYPE, GasStack.of(gas));
    }

    @Override
    public void draw(GasEntry gas, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        ViewerDraw.page(graphics, ViewerDraw.page(gas));
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip, GasEntry gas, IRecipeSlotsView slots, double mouseX, double mouseY) {
        Pages.hint(tooltip, ViewerDraw.page(gas), mouseX, mouseY);
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(GasEntry gas) {
        return ResourceLocation.parse(gas.id());
    }
}
