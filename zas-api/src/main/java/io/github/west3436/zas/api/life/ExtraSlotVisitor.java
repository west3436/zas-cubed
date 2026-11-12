package io.github.west3436.zas.api.life;

import net.minecraft.world.item.ItemStack;

/**
 * Takes the items an entity wears in extra equipment slots, one at a time ({@link ExtraSlots}).
 *
 * @since 1.1.0
 */
@FunctionalInterface
public interface ExtraSlotVisitor {
    /**
     * Takes one worn item.
     *
     * <p>The stack is the one in the slot, not a copy: a tank that is breathed from changes it in
     * place, as it does in an armor slot.
     *
     * @param stack the stack in the slot. It may be empty, as a provider may give its empty slots
     *     too: ZAS³ passes over an empty one, and so should any other visitor
     * @param kind what the slot counts as
     */
    void visit(ItemStack stack, ExtraSlotKind kind);
}
