package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.compat.curios.CuriosCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Curios' slots for the tests. A test makes one only in a run that has Curios, or Accessories'
 * layer standing in for it.
 *
 * <p>Curios gives no entity a slot by itself. A player's head, back and belt slots come from the
 * content mod's data (COMPAT-043), so the tests' own data gives a zombie a head and a back slot,
 * for the tests that run with the content off too.
 */
final class CuriosKit implements Kit {
    @Override
    public String modId() {
        return Slots.CURIOS;
    }

    @Override
    public ResourceLocation provider() {
        return CuriosCompat.ID;
    }

    @Override
    public boolean adapterActive() {
        return CuriosCompat.isActive();
    }

    @Override
    public String head() {
        return "head";
    }

    @Override
    public List<String> headTypes() {
        return List.of("head");
    }

    @Override
    public boolean slotsFromContent() {
        return true;
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
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
        return inventory == null
                ? List.of()
                : new ArrayList<>(inventory.getCurios().keySet());
    }

    private static ICurioStacksHandler slots(GameTestHelper h, LivingEntity entity, String type) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
        h.assertTrue(inventory != null, entity + " has no Curios inventory");
        ICurioStacksHandler slots = inventory.getCurios().get(type);
        h.assertTrue(slots != null && slots.getSlots() > 0, entity + " has no Curios slot " + type);
        return slots;
    }

    @Override
    public void equip(GameTestHelper h, LivingEntity entity, String type, ItemStack stack) {
        slots(h, entity, type).getStacks().setStackInSlot(0, stack);
    }

    @Override
    public void equipCosmetic(GameTestHelper h, LivingEntity entity, String type, ItemStack stack) {
        slots(h, entity, type).getCosmeticStacks().setStackInSlot(0, stack);
    }

    @Override
    public ItemStack worn(GameTestHelper h, LivingEntity entity, String type) {
        return slots(h, entity, type).getStacks().getStackInSlot(0);
    }

    @Override
    public boolean fits(LivingEntity entity, String type, ItemStack stack) {
        return CuriosApi.isStackValid(new SlotContext(type, entity, 0, false, true), stack);
    }

    /** Curios 9.5 and up can switch a slot off. */
    @Override
    public boolean setActive(LivingEntity entity, String type, boolean active) {
        try {
            ICuriosItemHandler.class.getMethod("setSlotActive", String.class, int.class, boolean.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
        CuriosApi.getCuriosInventory(entity).orElseThrow().setSlotActive(type, 0, active);
        return true;
    }

    @Override
    public LivingEntity wearer(GameTestHelper h) {
        Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(zombie != null, "couldn't create a zombie");
        return zombie;
    }
}
