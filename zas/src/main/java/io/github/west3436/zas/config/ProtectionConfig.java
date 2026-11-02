package io.github.west3436.zas.config;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config's {@code [protection]} section (LIFE-024): the share of the body each armor
 * slot covers, which worn pressure and thermal protection add up (LIFE-020, LIFE-025).
 *
 * <p>Each getter reads the loaded config on each call, so an edited file applies from the next
 * read, and gives the default while no world's config is loaded. Any thread.
 */
public final class ProtectionConfig {
    /** The armor slots, in the order the config lists them. */
    public static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Each slot's coverage by default: head 0.3, chest 0.4 (torso and arms), legs 0.2, feet 0.1. */
    public static final Map<EquipmentSlot, Double> DEFAULT_COVERAGE = Map.of(
            EquipmentSlot.HEAD, 0.3,
            EquipmentSlot.CHEST, 0.4,
            EquipmentSlot.LEGS, 0.2,
            EquipmentSlot.FEET, 0.1);

    /** Each slot's coverage. */
    public static final Map<EquipmentSlot, ModConfigSpec.DoubleValue> COVERAGE = new EnumMap<>(EquipmentSlot.class);

    private ProtectionConfig() {}

    /** Defines the section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        builder.comment(
                        "Worn protection (LIFE-024). Each armor slot covers a share of the body; pieces rated for",
                        "the pressure or temperature around the wearer add up their slots' shares to its",
                        "coverage c. (1 - c^5) of the pressure difference from 1 atm and (1 - c) of",
                        "temperature damage still reach it. The defaults add up to 1.")
                .push("protection");
        for (EquipmentSlot slot : SLOTS) {
            COVERAGE.put(
                    slot,
                    builder.comment("The share of the body the " + slot.getName() + " slot covers.")
                            .defineInRange(slot.getName() + "_coverage", DEFAULT_COVERAGE.get(slot), 0.0, 1.0));
        }
        builder.pop();
    }

    /** The share of the body an armor slot covers, 0 to 1; 0 for any other slot. */
    public static double coverage(EquipmentSlot slot) {
        Double fallback = DEFAULT_COVERAGE.get(slot);
        if (fallback == null) {
            return 0;
        }
        ModConfigSpec.DoubleValue value = COVERAGE.get(slot);
        return ZasServerConfig.SPEC.isLoaded() && value != null ? value.get() : fallback;
    }
}
