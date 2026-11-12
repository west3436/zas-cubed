package io.github.west3436.zas.compat.curios;

import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * What an entity wears in its Curios slots (COMPAT-040, COMPAT-042), through Curios' API alone
 * (COMPAT-003): every slot type the entity has, in Curios' own order, which is the order its screen
 * shows them in, and each type's slots from the first. The slot type {@code head} is the head slot;
 * every other type is a slot of the other kind.
 *
 * <p>Left out: cosmetic slots, whose items only show, and from Curios 9.5 a slot that something has
 * switched off, as Curios itself gives the item in one no tick and no effect.
 *
 * <p>A call walks the slots once and copies nothing. Curios keeps its inventory on both sides, so
 * the answer is the same on the client.
 */
final class CuriosSlots {
    /** Curios' slot type on the head. */
    static final String HEAD = "head";

    /** Whether this Curios can switch slots off: 9.5 and up. Older ones have no such method to ask. */
    private static final boolean SWITCHABLE = switchable();

    private CuriosSlots() {}

    /** Gives each item in the entity's Curios slots. */
    static void items(LivingEntity entity, ExtraSlotVisitor visitor) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        if (inventory.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ICurioStacksHandler> type :
                inventory.get().getCurios().entrySet()) {
            ICurioStacksHandler slots = type.getValue();
            ExtraSlotKind kind = HEAD.equals(type.getKey()) ? ExtraSlotKind.HEAD : ExtraSlotKind.OTHER;
            IDynamicStackHandler stacks = slots.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && (!SWITCHABLE || Switches.on(slots, i))) {
                    visitor.visit(stack, kind);
                }
            }
        }
    }

    private static boolean switchable() {
        try {
            ICurioStacksHandler.class.getMethod("getActiveStates");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The one use of what Curios 9.5 added, apart so that an older Curios never links it. */
    private static final class Switches {
        static boolean on(ICurioStacksHandler slots, int index) {
            NonNullList<Boolean> states = slots.getActiveStates();
            return index >= states.size() || states.get(index);
        }
    }
}
