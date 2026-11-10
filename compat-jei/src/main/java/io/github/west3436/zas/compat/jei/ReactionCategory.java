package io.github.west3436.zas.compat.jei;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.compat.viewer.Icon;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import io.github.west3436.zas.compat.viewer.ViewerLayout;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The reactions (COMPAT-060): a page for each, with what it takes on the left, what it gives on the
 * right, its catalysts under the arrow, and its conditions, rate and heat as icons with values.
 */
final class ReactionCategory implements IRecipeCategory<ViewerData.Reaction> {
    static final RecipeType<ViewerData.Reaction> TYPE =
            RecipeType.create(ZasMod.MOD_ID, "reactions", ViewerData.Reaction.class);

    private final IDrawable icon;
    private final IDrawable arrow;
    private List<ViewerData.Reaction> measured = List.of();
    private List<Object> measuredUnder = List.of();
    private int height;

    ReactionCategory(IGuiHelper gui) {
        this.icon = gui.drawableBuilder(
                        ViewerDraw.ICONS, ViewerDraw.REACTIONS_ICON_U, ViewerDraw.REACTIONS_ICON_V, 16, 16)
                .setTextureSize(Icon.SHEET_WIDTH, Icon.SHEET_HEIGHT)
                .build();
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<ViewerData.Reaction> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("viewer.zas.reactions");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return ViewerLayout.WIDTH;
    }

    /**
     * JEI gives every page of a category one height: the tallest reaction's. A page's rows of chips
     * depend on how wide its values are written, so this is measured again when the player's units
     * or the fire settings change.
     */
    @Override
    public int getHeight() {
        List<Object> under = ViewerData.writtenUnder();
        if (measured.isEmpty() || !under.equals(measuredUnder)) {
            measured = ViewerData.reactions();
            measuredUnder = under;
            height = measured.stream()
                    .mapToInt(reaction -> ViewerDraw.page(reaction.now()).height())
                    .max()
                    .orElse(ViewerLayout.SLOT);
        }
        return height;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ViewerData.Reaction reaction, IFocusGroup focuses) {
        Pages.slots(builder, ViewerDraw.page(reaction.now()));
    }

    @Override
    public void draw(
            ViewerData.Reaction reaction, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        ViewerLayout.Page page = ViewerDraw.page(reaction.now());
        arrow.draw(
                graphics,
                page.arrowX() + (ViewerLayout.ARROW_WIDTH - arrow.getWidth()) / 2,
                page.arrowY() + (ViewerLayout.SLOT - arrow.getHeight()) / 2);
        ViewerDraw.page(graphics, page);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            ViewerData.Reaction reaction,
            IRecipeSlotsView slots,
            double mouseX,
            double mouseY) {
        Pages.hint(tooltip, ViewerDraw.page(reaction.now()), mouseX, mouseY);
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(ViewerData.Reaction reaction) {
        return ResourceLocation.fromNamespaceAndPath(reaction.namespace(), reaction.path());
    }
}
