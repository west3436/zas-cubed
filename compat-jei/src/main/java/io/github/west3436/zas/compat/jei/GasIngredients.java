package io.github.west3436.zas.compat.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Gases as a JEI ingredient type (COMPAT-061): what JEI needs to list a gas, tell two apart, draw
 * one and remember a bookmarked one.
 */
public final class GasIngredients {
    /** The type. JEI tells ingredient types apart by their class. */
    public static final IIngredientType<GasStack> TYPE = () -> GasStack.class;

    /**
     * A gas as JEI saves it, by its id alone. A saved gas the server no longer defines fails to
     * load, and JEI drops it.
     */
    public static final Codec<GasStack> CODEC = Codec.STRING.comapFlatMap(
            id -> ViewerData.gas(id)
                    .map(GasStack::of)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "No gas " + id)),
            stack -> stack.gas().id());

    private GasIngredients() {}

    /** Tells gases apart by id: the amount is a reaction's, not the gas's. */
    public static final class Helper implements IIngredientHelper<GasStack> {
        @Override
        public IIngredientType<GasStack> getIngredientType() {
            return TYPE;
        }

        @Override
        public String getDisplayName(GasStack stack) {
            return Component.translatable(stack.gas().nameKey()).getString();
        }

        // JEI 19.21 still asks for this one; newer versions reach it through getUid.
        @SuppressWarnings("removal")
        @Override
        public String getUniqueId(GasStack stack, UidContext context) {
            return stack.gas().id();
        }

        @Override
        public ResourceLocation getResourceLocation(GasStack stack) {
            return ResourceLocation.parse(stack.gas().id());
        }

        @Override
        public Iterable<Integer> getColors(GasStack stack) {
            return List.of(0xFF000000 | stack.gas().colour());
        }

        @Override
        public GasStack copyIngredient(GasStack stack) {
            return stack;
        }

        @Override
        public GasStack normalizeIngredient(GasStack stack) {
            return GasStack.of(stack.gas());
        }

        @Override
        public String getErrorInfo(@Nullable GasStack stack) {
            return stack == null ? "null" : stack.gas().id();
        }
    }

    /** Draws a gas as the EMI plugin does: its icon in its colour, with its amount in the corner. */
    public static final class Renderer implements IIngredientRenderer<GasStack> {
        @Override
        public void render(GuiGraphics graphics, GasStack stack) {
            ViewerDraw.gas(graphics, stack, 0, 0);
        }

        // JEI 19.21 still asks for this one; newer versions reach it through the tooltip builder.
        @SuppressWarnings("removal")
        @Override
        public List<Component> getTooltip(GasStack stack, TooltipFlag flag) {
            return ViewerDraw.tooltip(stack, flag.isAdvanced());
        }
    }
}
