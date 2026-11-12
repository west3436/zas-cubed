package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.LevelThread;
import net.minecraft.world.entity.LivingEntity;

/**
 * Says what an entity wears in the equipment slots a mod adds beside the armor slots, such as a
 * back, belt or mask slot. Register one with {@link ExtraSlots}.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it, leaves out the items it hadn't yet given for
 * that call, and logs the fault at most once a minute.
 *
 * @since 1.1.0
 */
@FunctionalInterface
public interface ExtraSlotProvider {
    /**
     * Gives the visitor each item the entity wears in this provider's slots, in an order that stays
     * the same from call to call, such as the order the slots are shown in. Internals breathe from
     * the first supply given (LIFE-028).
     *
     * <p>Give the stack that is in the slot, not a copy, and give each stack once. Empty slots may
     * be left out. Leave out a slot whose item only shows and has no effect, such as a cosmetic
     * one.
     *
     * <p>ZAS³ asks several times in each breathing check of an entity, every 2 s by default, and
     * several times every 5 game ticks for a player whose HUD shows. Keep it to a walk over the
     * slots: no copies, no searches.
     *
     * <p>Thread: the main thread of the entity's logical side. ZAS³ itself asks only on the server
     * thread.
     *
     * @param entity the entity, server or client side
     * @param visitor takes each worn item
     */
    @LevelThread
    void items(LivingEntity entity, ExtraSlotVisitor visitor);
}
