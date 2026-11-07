package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The hardsuit's weight (CONT-089): each hardsuit piece worn takes {@code [gear] hardsuit_slowdown}
 * of its wearer's movement speed away, 10 % by default, so the full suit leaves 60 %.
 *
 * <p>It is one transient modifier on {@link Attributes#MOVEMENT_SPEED}, {@link #MODIFIER}, that
 * multiplies the total by (1 − pieces × slowdown), so it stacks with sprinting and effects as
 * slowness does. The server sets it when the wearer's equipment changes, and again each second, so
 * a change to the setting reaches what is already worn; the game sends a player its speed. Being
 * transient, it isn't saved: the first tick after loading sets it again. A mob in a hardsuit is
 * slowed too.
 */
public final class Encumbrance {
    /** The modifier's id. */
    public static final ResourceLocation MODIFIER = ZasAtmosMod.id("hardsuit_encumbrance");

    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private Encumbrance() {}

    /** Listens for equipment changes and the once-a-second refresh, on the server. */
    public static void register(IEventBus bus) {
        bus.addListener((LivingEquipmentChangeEvent e) -> update(e.getEntity()));
        bus.addListener((EntityTickEvent.Post e) -> {
            if (e.getEntity() instanceof LivingEntity living && living.tickCount % 20 == 0) {
                update(living);
            }
        });
    }

    /** Whether a stack is a hardsuit piece. */
    public static boolean isHardsuit(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor && armor.getMaterial().is(GearMaterials.HARDSUIT.getKey());
    }

    /** The hardsuit pieces an entity wears, 0 to 4. */
    public static int pieces(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR) {
            if (isHardsuit(entity.getItemBySlot(slot))) {
                n++;
            }
        }
        return n;
    }

    /** The share of movement speed {@code pieces} hardsuit pieces leave, 0 to 1. */
    public static double speedFactor(int pieces) {
        return Math.max(0, 1 - pieces * AtmosServerConfig.get(AtmosServerConfig.HARDSUIT_SLOWDOWN));
    }

    /** Sets an entity's slowdown for what it wears now, on the server; a client's is left to the server. */
    public static void update(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = speedFactor(pieces(entity)) - 1;
        AttributeModifier now = speed.getModifier(MODIFIER);
        if (amount == 0) {
            if (now != null) {
                speed.removeModifier(MODIFIER);
            }
        } else if (now == null || now.amount() != amount) {
            speed.addOrUpdateTransientModifier(
                    new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
