package io.github.west3436.zas.compat.slots.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * One target's slots as the tests use them, so that the two adapters are held to the same tests
 * ({@link Scenarios}). An implementation is the only place that names its target's classes: a test
 * class names none, and so loads in a run without that target.
 */
interface Kit {
    /** The target's mod id. */
    String modId();

    /** The id its adapter registers its provider under. */
    ResourceLocation provider();

    /** Whether its adapter says it started. */
    boolean adapterActive();

    /** The slot type a mask is tagged for, a head slot. */
    String head();

    /** Every slot type of the target's own that is a head slot. */
    List<String> headTypes();

    /** Whether a player's head, back and belt slots come from the content mod's data, not from the target. */
    boolean slotsFromContent();

    /** The slot type on the back. */
    String back();

    /** The slot type on the belt. */
    String belt();

    /** The slot types an entity has, in the target's order: none if it has no slots. */
    List<String> slotTypes(LivingEntity entity);

    /** Puts a stack in the first slot of a type, which the entity must have. */
    void equip(GameTestHelper h, LivingEntity entity, String type, ItemStack stack);

    /** Puts a stack in the first cosmetic slot of a type. */
    void equipCosmetic(GameTestHelper h, LivingEntity entity, String type, ItemStack stack);

    /** The stack in the first slot of a type. */
    ItemStack worn(GameTestHelper h, LivingEntity entity, String type);

    /** Whether the target lets a stack into the first slot of a type. */
    boolean fits(LivingEntity entity, String type, ItemStack stack);

    /**
     * Switches the first slot of a type off or on again, where the target can.
     *
     * @return false if this target, or this version of it, has no such switch
     */
    default boolean setActive(LivingEntity entity, String type, boolean active) {
        return false;
    }

    /**
     * An entity that has a head slot and a back slot whether or not the content is on: one the
     * target gives slots itself, or one the tests' own data gives them.
     */
    LivingEntity wearer(GameTestHelper h);
}
