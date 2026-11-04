package io.github.west3436.zas.config;

import io.github.west3436.zas.engine.fire.BlockIgnitionSettings;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.scheduler.HotspotSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config's {@code [fire]} section: the fire oxygen threshold (FIRE-030), the O₂
 * fraction and partial pressure every fire needs, hotspots, vanilla fire and lit fuel blocks alike;
 * how fast hotspots spread (FIRE-016); and how hot gas and gas fires set blocks alight (FIRE-033,
 * FIRE-034). Not preset-controlled.
 *
 * <p>{@link #settings()} reads the loaded config on each call, so an edited file applies from the
 * next read (for the worker, its next atmos tick), and gives the defaults while no world's config
 * is loaded. Any thread.
 */
public final class FireConfig {
    /** The least O₂ fraction a fire needs (FIRE-030). */
    public static ModConfigSpec.DoubleValue OXYGEN_MINIMUM_FRACTION;

    /** The least O₂ partial pressure a fire needs, kPa (FIRE-030). */
    public static ModConfigSpec.DoubleValue OXYGEN_MINIMUM_KPA;

    /** The O₂ fraction below which fuels make CO (FIRE-005). */
    public static ModConfigSpec.DoubleValue INCOMPLETE_BELOW_FRACTION;

    /** The chance per second a hotspot spreads to each neighbouring cell that can burn (FIRE-016). */
    public static ModConfigSpec.DoubleValue SPREAD_CHANCE;

    /** The gas temperature, K, at which a flammable block with no override catches fire (FIRE-033). */
    public static ModConfigSpec.DoubleValue BLOCK_IGNITION_TEMPERATURE;

    /** The chance per second a gas fire lights a neighbouring block of ignite odds 100 (FIRE-034). */
    public static ModConfigSpec.DoubleValue HOTSPOT_BLOCK_IGNITE_CHANCE;

    private static volatile FireSettings override;
    private static volatile HotspotSettings hotspotOverride;
    private static volatile BlockIgnitionSettings blockIgnitionOverride;

    private FireConfig() {}

    /** Defines the section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        builder.push("fire");
        OXYGEN_MINIMUM_FRACTION = builder.comment(
                        "The least O2 fraction (by moles) any fire needs: gas fires, vanilla fire, furnaces,",
                        "campfires and the fuel generator. Below it they go out or pause.")
                .defineInRange("oxygen_minimum_fraction", FireSettings.DEFAULT.minOxygenFraction(), 0.0, 1.0);
        OXYGEN_MINIMUM_KPA = builder.comment(
                        "The least O2 partial pressure, in kPa, any fire needs, whatever the fraction, so",
                        "nothing burns in a thin atmosphere.")
                .defineInRange("oxygen_minimum_kpa", FireSettings.DEFAULT.minOxygenKpa(), 0.0, 1000.0);
        INCOMPLETE_BELOW_FRACTION = builder.comment(
                        "The O2 fraction below which fuels burn incompletely: a share of their carbon forms CO",
                        "instead of CO2, rising linearly to all of it at oxygen_minimum_fraction.")
                .defineInRange("incomplete_below_fraction", FireSettings.DEFAULT.incompleteBelowFraction(), 0.0, 1.0);
        SPREAD_CHANCE = builder.comment(
                        "The chance per second, 0 to 1, that a gas fire spreads to each neighbouring cell of its",
                        "zone that is air and whose gas can burn.")
                .defineInRange("spread_chance", HotspotSettings.DEFAULT_SPREAD_CHANCE, 0.0, 1.0);
        BLOCK_IGNITION_TEMPERATURE = builder.comment(
                        "The gas temperature, in K, at which a zone sets the flammable blocks beside it alight.",
                        "data/<namespace>/zas/block_ignition/ overrides it for some blocks, such as wool and leaves.")
                .defineInRange("block_ignition_temperature", BlockIgnitionSettings.DEFAULT_TEMPERATURE, 0.0, 100_000.0);
        HOTSPOT_BLOCK_IGNITE_CHANCE = builder.comment(
                        "The chance per second, 0 to 1, that a gas fire lights a neighbouring block of vanilla",
                        "ignite odds 100. A block's own odds scale it: wool, at 30, catches at 30 % of this.")
                .defineInRange("hotspot_block_ignite_chance", BlockIgnitionSettings.DEFAULT_HOTSPOT_CHANCE, 0.0, 1.0);
        builder.pop();
    }

    /** The fire oxygen threshold from the config, or a GameTest's override; the defaults before it loads. */
    public static FireSettings settings() {
        FireSettings forced = override;
        if (forced != null) {
            return forced;
        }
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return FireSettings.DEFAULT;
        }
        return new FireSettings(
                OXYGEN_MINIMUM_FRACTION.get(), OXYGEN_MINIMUM_KPA.get(), INCOMPLETE_BELOW_FRACTION.get());
    }

    /** Uses {@code settings} instead of the config until called with null. For GameTests. */
    public static void overrideForTest(FireSettings settings) {
        override = settings;
    }

    /** The hotspot spread from the config, or a GameTest's override; the default before it loads. */
    public static HotspotSettings hotspots() {
        HotspotSettings forced = hotspotOverride;
        if (forced != null) {
            return forced;
        }
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return HotspotSettings.DEFAULT;
        }
        return new HotspotSettings(SPREAD_CHANCE.get());
    }

    /** Uses {@code settings} instead of the config's spread until called with null. For GameTests. */
    public static void overrideHotspotsForTest(HotspotSettings settings) {
        hotspotOverride = settings;
    }

    /**
     * How blocks catch fire, from the config or a GameTest's override; the defaults before it loads.
     * On, whatever the {@code doFireTick} game rule says: each dimension applies that itself.
     */
    public static BlockIgnitionSettings blockIgnition() {
        BlockIgnitionSettings forced = blockIgnitionOverride;
        if (forced != null) {
            return forced;
        }
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return BlockIgnitionSettings.DEFAULT;
        }
        return new BlockIgnitionSettings(true, BLOCK_IGNITION_TEMPERATURE.get(), HOTSPOT_BLOCK_IGNITE_CHANCE.get());
    }

    /** Uses {@code settings} instead of the config's until called with null. For GameTests. */
    public static void overrideBlockIgnitionForTest(BlockIgnitionSettings settings) {
        blockIgnitionOverride = settings;
    }
}
