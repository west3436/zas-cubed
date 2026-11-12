package io.github.west3436.zas.compat.accessories;

import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What an entity wears in its Accessories slots (COMPAT-041, COMPAT-042), through Accessories' API
 * alone (COMPAT-003): every slot type the entity has, in the order Accessories keeps them, and each
 * type's slots from the first. The slot types {@code hat} and {@code face}, which are Accessories'
 * {@code head} group as it ships, are head slots; every other type is a slot of the other kind.
 *
 * <p>The two names are fixed here. Accessories keeps its groups as data, which a pack can change,
 * but the class that looks a group up is outside its API package, so a pack's own slot type on the
 * head isn't a head slot to ZAS³.
 *
 * <p>Left out: cosmetic slots, whose items only show.
 *
 * <p>A call walks the slots once and copies nothing. Accessories keeps its slots on both sides, so
 * the answer is the same on the client.
 */
final class AccessoriesSlots {
    /** Accessories' slot types on the head. */
    static final Set<String> HEAD_SLOTS = Set.of("hat", "face");

    private AccessoriesSlots() {}

    /** Gives each item in the entity's Accessories slots. */
    static void items(LivingEntity entity, ExtraSlotVisitor visitor) {
        AccessoriesCapability worn = AccessoriesCapability.get(entity);
        if (worn == null) {
            return;
        }
        for (AccessoriesContainer type : worn.getContainers().values()) {
            ExtraSlotKind kind = HEAD_SLOTS.contains(type.getSlotName()) ? ExtraSlotKind.HEAD : ExtraSlotKind.OTHER;
            Container stacks = type.getAccessories();
            for (int i = 0; i < stacks.getContainerSize(); i++) {
                ItemStack stack = stacks.getItem(i);
                if (!stack.isEmpty()) {
                    visitor.visit(stack, kind);
                }
            }
        }
    }
}
