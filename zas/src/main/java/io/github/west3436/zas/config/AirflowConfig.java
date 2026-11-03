package io.github.west3436.zas.config;

import io.github.west3436.zas.engine.flow.FlowSettings;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config's {@code [airflow]} section (FLOW-001 to FLOW-014): the switches, the force
 * model, each class's threshold and the per-tick cap. None of them is preset-controlled.
 *
 * <p>Each getter reads the loaded config on each call, so an edited file applies from the next
 * read, and gives the default while no world's config is loaded. Any thread.
 */
public final class AirflowConfig {
    /** The light class's default threshold, kPa: items, XP orbs, projectiles (FLOW-011). */
    public static final double DEFAULT_LIGHT_KPA = 20;

    /** The mob class's default threshold, kPa: mobs and players (FLOW-011). */
    public static final double DEFAULT_MOB_KPA = 65;

    /** The heavy class's default threshold, kPa: minecarts, boats, armor stands (FLOW-011). */
    public static final double DEFAULT_HEAVY_KPA = 85;

    /** The default cap on entities airflow moves per game tick in each dimension (FLOW-013). */
    public static final int DEFAULT_MAX_ENTITIES = 256;

    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_DIMENSIONS;
    public static ModConfigSpec.DoubleValue RANGE;
    public static ModConfigSpec.DoubleValue SPEED_AT_ONE_ATM;
    public static ModConfigSpec.DoubleValue MAX_SPEED;
    public static ModConfigSpec.DoubleValue RESPONSE;
    public static ModConfigSpec.DoubleValue LIGHT_KPA;
    public static ModConfigSpec.DoubleValue MOB_KPA;
    public static ModConfigSpec.DoubleValue HEAVY_KPA;
    public static ModConfigSpec.IntValue MAX_ENTITIES;

    private AirflowConfig() {}

    /** Defines the section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        builder.push("airflow");
        ENABLED = builder.comment("Whether pressure differences push entities. false switches airflow off everywhere.")
                .define("enabled", true);
        DISABLED_DIMENSIONS = builder.comment(
                        "Dimension ids where airflow is off, such as \"minecraft:the_end\". Gas still flows there.")
                .defineListAllowEmpty(
                        "disabled_dimensions",
                        List.of(),
                        () -> "",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        RANGE = builder.comment(
                        "How far from an opening airflow reaches, in blocks. The push falls linearly to 0 there.")
                .defineInRange("range_blocks", FlowSettings.DEFAULT.range(), 1.0, 32.0);
        SPEED_AT_ONE_ATM = builder.comment(
                        "The flow's speed next to an opening at a pressure difference of 101.325 kPa, in blocks",
                        "per game tick. It scales with the difference.")
                .defineInRange("speed_at_1_atm", FlowSettings.DEFAULT.speedAtOneAtm(), 0.0, 10.0);
        MAX_SPEED = builder.comment("The most the flow's speed can be, in blocks per game tick.")
                .defineInRange("max_speed", FlowSettings.DEFAULT.maxSpeed(), 0.0, 10.0);
        RESPONSE = builder.comment(
                        "How quickly an entity takes up the flow's speed: the share of the difference closed each",
                        "game tick, from 0 (never) to 1 (at once).")
                .defineInRange("response", FlowSettings.DEFAULT.response(), 0.0, 1.0);
        LIGHT_KPA = builder.comment(
                        "The least pressure difference, kPa, that moves an entity in #zas:airflow/light",
                        "(by default items, XP orbs and projectiles).")
                .defineInRange("light_threshold_kpa", DEFAULT_LIGHT_KPA, 0.0, 100_000.0);
        MOB_KPA = builder.comment(
                        "The least pressure difference, kPa, that moves a player, a mob, or another entity in",
                        "#zas:airflow/mob. Living entities in no airflow tag are in this class.")
                .defineInRange("mob_threshold_kpa", DEFAULT_MOB_KPA, 0.0, 100_000.0);
        HEAVY_KPA = builder.comment(
                        "The least pressure difference, kPa, that moves an entity in #zas:airflow/heavy",
                        "(by default minecarts, boats and armor stands).")
                .defineInRange("heavy_threshold_kpa", DEFAULT_HEAVY_KPA, 0.0, 100_000.0);
        MAX_ENTITIES = builder.comment(
                        "The most entities airflow moves in one dimension each game tick. Beyond it, the entities",
                        "near openings take turns.")
                .defineInRange("max_entities_per_tick", DEFAULT_MAX_ENTITIES, 1, 100_000);
        builder.pop();
    }

    private static boolean loaded() {
        return ZasServerConfig.SPEC.isLoaded();
    }

    /** Whether airflow is on in a dimension: on globally and not switched off there (FLOW-014). */
    public static boolean enabledIn(ResourceLocation dimension) {
        if (!loaded()) {
            return true;
        }
        return ENABLED.get() && !DISABLED_DIMENSIONS.get().contains(dimension.toString());
    }

    /** The force model (FLOW-002, FLOW-010). */
    public static FlowSettings settings() {
        if (!loaded()) {
            return FlowSettings.DEFAULT;
        }
        return new FlowSettings(RANGE.get(), SPEED_AT_ONE_ATM.get(), MAX_SPEED.get(), RESPONSE.get());
    }

    public static double lightKpa() {
        return loaded() ? LIGHT_KPA.get() : DEFAULT_LIGHT_KPA;
    }

    public static double mobKpa() {
        return loaded() ? MOB_KPA.get() : DEFAULT_MOB_KPA;
    }

    public static double heavyKpa() {
        return loaded() ? HEAVY_KPA.get() : DEFAULT_HEAVY_KPA;
    }

    /**
     * The least difference at which the engine exports an edge's openings in a dimension: the
     * lowest class threshold, or infinity where airflow is off (FLOW-001, FLOW-014).
     */
    public static double exportKpa(ResourceLocation dimension) {
        if (!enabledIn(dimension)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.min(lightKpa(), Math.min(mobKpa(), heavyKpa()));
    }

    /** The per-tick cap (FLOW-013). */
    public static int maxEntitiesPerTick() {
        return loaded() ? MAX_ENTITIES.get() : DEFAULT_MAX_ENTITIES;
    }
}
