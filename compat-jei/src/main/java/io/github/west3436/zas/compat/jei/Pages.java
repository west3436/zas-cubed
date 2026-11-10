package io.github.west3436.zas.compat.jei;

import io.github.west3436.zas.compat.viewer.ViewerDraw;
import io.github.west3436.zas.compat.viewer.ViewerLayout;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

/** What the two categories share: putting a laid-out page's slots and chip names into JEI. */
final class Pages {
    private Pages() {}

    /** Adds a page's slots. A slot's corner includes its border, and JEI's is the gas's. */
    static void slots(IRecipeLayoutBuilder builder, ViewerLayout.Page page) {
        for (ViewerLayout.Slot slot : page.slots()) {
            builder.addSlot(role(slot.role()), slot.x() + 1, slot.y() + 1)
                    .setStandardSlotBackground()
                    .addIngredient(GasIngredients.TYPE, slot.stack());
        }
    }

    /** Names the chip under the pointer. */
    static void hint(ITooltipBuilder tooltip, ViewerLayout.Page page, double mouseX, double mouseY) {
        page.hintAt(mouseX, mouseY).ifPresent(line -> tooltip.add(ViewerDraw.text(line)));
    }

    private static RecipeIngredientRole role(ViewerLayout.Role role) {
        return switch (role) {
            case INPUT -> RecipeIngredientRole.INPUT;
            case OUTPUT -> RecipeIngredientRole.OUTPUT;
            case CATALYST -> RecipeIngredientRole.CATALYST;
        };
    }
}
