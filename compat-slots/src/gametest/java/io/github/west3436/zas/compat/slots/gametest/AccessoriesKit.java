package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.compat.accessories.AccessoriesCompat;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Accessories' slots for the tests. A test makes one only in a run that has Accessories, which
 * gives a player its slots itself: a hat and a face slot on the head, a back slot and a belt slot
 * among them.
 */
final class AccessoriesKit implements Kit {
    @Override
    public String modId() {
        return Slots.ACCESSORIES;
    }

    @Override
    public ResourceLocation provider() {
        return AccessoriesCompat.ID;
    }

    @Override
    public boolean adapterActive() {
        return AccessoriesCompat.isActive();
    }

    @Override
    public String head() {
        return "face";
    }

    @Override
    public List<String> headTypes() {
        return List.of("hat", "face");
    }

    @Override
    public boolean slotsFromContent() {
        return false;
    }

    @Override
    public String back() {
        return "back";
    }

    @Override
    public String belt() {
        return "belt";
    }

    @Override
    public List<String> slotTypes(LivingEntity entity) {
        AccessoriesCapability worn = AccessoriesCapability.get(entity);
        return worn == null ? List.of() : new ArrayList<>(worn.getContainers().keySet());
    }

    private static AccessoriesContainer slots(GameTestHelper h, LivingEntity entity, String type) {
        AccessoriesCapability worn = AccessoriesCapability.get(entity);
        h.assertTrue(worn != null, entity + " has no Accessories slots");
        AccessoriesContainer slots = worn.getContainers().get(type);
        h.assertTrue(
                slots != null && slots.getAccessories().getContainerSize() > 0,
                entity + " has no Accessories slot " + type);
        return slots;
    }

    @Override
    public void equip(GameTestHelper h, LivingEntity entity, String type, ItemStack stack) {
        slots(h, entity, type).getAccessories().setItem(0, stack);
    }

    @Override
    public void equipCosmetic(GameTestHelper h, LivingEntity entity, String type, ItemStack stack) {
        slots(h, entity, type).getCosmeticAccessories().setItem(0, stack);
    }

    @Override
    public ItemStack worn(GameTestHelper h, LivingEntity entity, String type) {
        return slots(h, entity, type).getAccessories().getItem(0);
    }

    @Override
    public boolean fits(LivingEntity entity, String type, ItemStack stack) {
        return AccessoriesAPI.canInsertIntoSlot(stack, SlotReference.of(entity, type, 0));
    }

    @Override
    public LivingEntity wearer(GameTestHelper h) {
        return Slots.player(h, "zas_accessories_wearer");
    }
}
