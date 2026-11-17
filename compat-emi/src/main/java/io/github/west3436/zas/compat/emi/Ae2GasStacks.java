package io.github.west3436.zas.compat.emi;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.stack.EmiStack;
import io.github.west3436.zas.compat.ae2.GasKey;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ViewerData;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Gases between EMI and AE2's screens (COMPAT-070, COMPAT-073): a gas dragged from EMI onto an
 * export or storage bus's filter, or a level emitter's, sets that gas; a gas under the cursor in an
 * ME terminal shows its reactions in EMI. AE2 asks its converters for this, one per kind of EMI
 * stack.
 *
 * <p>Only {@link ZasEmiPlugin} names this class, and only with the AE2 adapter on, so nothing here
 * loads without AE2.
 */
final class Ae2GasStacks implements EmiStackConverter {
    private Ae2GasStacks() {}

    /** Adds the converter once; AE2 refuses a second one for the same kind of stack. */
    static void register() {
        EmiStackConverters.register(new Ae2GasStacks());
    }

    @Override
    public Class<?> getKeyType() {
        return GasEmiStack.Key.class;
    }

    @Override
    public @Nullable EmiStack toEmiStack(GenericStack stack) {
        if (!(stack.what() instanceof GasKey key)) {
            return null;
        }
        return ViewerData.gas(key.gas().toString())
                .<EmiStack>map(gas -> new GasEmiStack(GasStack.of(gas)))
                .orElse(null);
    }

    @Override
    public @Nullable GenericStack toGenericStack(EmiStack stack) {
        GasEmiStack.Key key = stack.getKeyOfType(GasEmiStack.Key.class);
        if (key == null) {
            return null;
        }
        // EMI's amount of a gas is always 1, which AE2 reads as one unit: a millimole.
        return new GenericStack(GasKey.of(ResourceLocation.parse(key.gas())), Math.max(1, stack.getAmount()));
    }
}
