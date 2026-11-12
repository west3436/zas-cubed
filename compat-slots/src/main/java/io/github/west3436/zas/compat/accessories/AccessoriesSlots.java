package io.github.west3436.zas.compat.accessories;

import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotGroup;
import io.wispforest.accessories.data.SlotGroupLoader;
import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What an entity wears in its Accessories slots (COMPAT-041, COMPAT-042), through Accessories' own
 * public classes (COMPAT-003): every slot type the entity has, in the order Accessories keeps
 * them, and each type's slots from the first. A slot type in Accessories' {@code head} group, which
 * holds {@code hat} and {@code face} unless a datapack says otherwise, is a head slot; every other
 * type is a slot of the other kind.
 *
 * <p>Left out: cosmetic slots, whose items only show.
 *
 * <p>A call walks the slots once and copies nothing. Accessories keeps its slots on both sides, so
 * the answer is the same on the client.
 */
final class AccessoriesSlots {
    /** Accessories' slot group on the head. */
    static final String HEAD_GROUP = "head";

    /** That group's slot types as Accessories ships it, for a datapack that removed the group. */
    private static final Set<String> HEAD_SLOTS = Set.of("hat", "face");

    private AccessoriesSlots() {}

    /** Gives each item in the entity's Accessories slots. */
    static void items(LivingEntity entity, ExtraSlotVisitor visitor) {
        AccessoriesCapability worn = AccessoriesCapability.get(entity);
        if (worn == null) {
            return;
        }
        Set<String> head = SlotGroupLoader.getGroup(entity.level(), HEAD_GROUP)
                .map(SlotGroup::slots)
                .orElse(HEAD_SLOTS);
        for (AccessoriesContainer type : worn.getContainers().values()) {
            ExtraSlotKind kind = head.contains(type.getSlotName()) ? ExtraSlotKind.HEAD : ExtraSlotKind.OTHER;
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
