package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The armor materials of the breathing gear. Their layers are the worn textures in {@code
 * textures/models/armor}; what the gear protects against is its life support ({@link SuitItem}),
 * not its armor points, which are small.
 */
public final class GearMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ZasAtmosMod.MOD_ID);

    /** The softsuit (CONT-032): little armor, as leather's. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SOFTSUIT =
            MATERIALS.register("softsuit", () -> material("softsuit", 1, 2, 3, 1, SoundEvents.ARMOR_EQUIP_LEATHER));

    /** The breath mask (CONT-031): no armor. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BREATH_MASK = MATERIALS.register(
            "breath_mask", () -> material("breath_mask", 0, 0, 0, 0, SoundEvents.ARMOR_EQUIP_LEATHER));

    /** The gas mask (CONT-088): no armor, as the breath mask. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK =
            MATERIALS.register("gas_mask", () -> material("gas_mask", 0, 0, 0, 0, SoundEvents.ARMOR_EQUIP_LEATHER));

    /**
     * The hardsuit (CONT-036): chainmail's armor, a little more than the softsuit's. Its pieces are
     * what {@link Encumbrance} counts.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HARDSUIT =
            MATERIALS.register("hardsuit", () -> material("hardsuit", 1, 4, 5, 2, SoundEvents.ARMOR_EQUIP_IRON));

    private GearMaterials() {}

    private static ArmorMaterial material(
            String layer, int boots, int leggings, int chestplate, int helmet, Holder<SoundEvent> equip) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.LEGGINGS, leggings);
        defense.put(ArmorItem.Type.CHESTPLATE, chestplate);
        defense.put(ArmorItem.Type.HELMET, helmet);
        defense.put(ArmorItem.Type.BODY, 0);
        return new ArmorMaterial(
                defense,
                0,
                equip,
                () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(ZasAtmosMod.id(layer))),
                0,
                0);
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
    }
}
