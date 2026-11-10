package io.github.west3436.zas.compat.emi;

import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiStackSerializer;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerDraw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A gas as an EMI stack (COMPAT-062). EMI's own amounts are whole numbers and a reaction's
 * coefficients aren't, so the stack keeps the coefficient itself and draws it as the JEI plugin
 * does; EMI's amount stays 1.
 */
final class GasEmiStack extends EmiStack {
    /** The type name EMI saves a favourited gas under, before the gas's id. */
    static final String SERIALIZED_TYPE = "zas_gas";

    private final GasStack stack;

    GasEmiStack(GasStack stack) {
        this.stack = stack;
    }

    /** What EMI tells stacks apart by: the gas's id, in a type no other mod's stacks use. */
    record Key(String gas) {}

    @Override
    public EmiStack copy() {
        GasEmiStack copy = new GasEmiStack(stack);
        copy.setChance(chance);
        copy.setRemainder(getRemainder());
        copy.comparison = comparison;
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public DataComponentPatch getComponentChanges() {
        return DataComponentPatch.EMPTY;
    }

    @Override
    public Object getKey() {
        return new Key(stack.gas().id());
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.parse(stack.gas().id());
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        // The icon and the amount are drawn together, as an item and its count are.
        if ((flags & RENDER_ICON) != 0) {
            ViewerDraw.gas(graphics, (flags & RENDER_AMOUNT) != 0 ? stack : GasStack.of(stack.gas()), x, y);
        }
    }

    @Override
    public List<Component> getTooltipText() {
        return ViewerDraw.tooltip(stack, Minecraft.getInstance().options.advancedItemTooltips);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> out = new ArrayList<>();
        getTooltipText().forEach(line -> out.add(EmiTooltipComponents.of(line)));
        EmiTooltipComponents.appendModName(out, stack.gas().namespace());
        out.addAll(super.getTooltip());
        return out;
    }

    @Override
    public Component getName() {
        return ViewerDraw.name(stack.gas());
    }

    /** Saves a gas by its id, and loads it back if the server still defines it. */
    static final class Serializer implements EmiStackSerializer<GasEmiStack> {
        @Override
        public String getType() {
            return SERIALIZED_TYPE;
        }

        @Override
        public EmiStack create(ResourceLocation id, DataComponentPatch componentChanges, long amount) {
            return ViewerData.gas(id.toString())
                    .<EmiStack>map(gas -> new GasEmiStack(GasStack.of(gas)))
                    .orElse(EmiStack.EMPTY);
        }
    }
}
