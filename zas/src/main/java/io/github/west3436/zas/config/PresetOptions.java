package io.github.west3436.zas.config;

import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.PresetType;
import io.github.west3436.ss13.api.preset.Presets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The options the atmosphere preset sets (OPS-007, D-14), each with a Normal and a Hardcore default
 * and an optional override (OPS-014). They are registered with SS13 Framework's world-wide
 * Normal/Hardcore switch ({@link Presets}) as {@code zas:<key>}, so one switch sets them with every
 * other mod's options, and the framework keeps the preset and the overrides in the world's {@code
 * ss13_framework-presets.toml}. Before 2.0 they were in {@code zas-server.toml}, under {@code
 * preset} and {@code [preset_options]}; {@link PresetMigration} carries a 1.2.0 world's values over.
 *
 * <p>This is the one place the rest of the mod asks for a preset-controlled value: read the
 * option's {@link PresetOption#get()}, on any thread, whenever the value is needed, rather than
 * keeping it, so a preset change or an edit of the preset file applies from the next read
 * (OPS-016). Zone triggers have their own view in {@link ZoneTrigger}. Each description ends with
 * the requirements the option serves, which the preset file's comment shows.
 */
public final class PresetOptions {
    /**
     * The variant of each dimension's ambient that applies (ENV-002, ENV-010, ENV-012): under
     * Hardcore, the Nether is hot and CO2-rich and the End's air is thin (D-19).
     */
    public static final PresetOption<AtmospherePreset> AMBIENT_VARIANT = register(
            "ambient_variant",
            PresetType.ofEnum(AtmospherePreset.class),
            AtmospherePreset.NORMAL,
            AtmospherePreset.HARDCORE,
            "Which variant of each dimension's ambient atmosphere applies; Hardcore's Nether is hot and"
                    + " CO2-rich and its End thin (ENV-002, ENV-010, ENV-012)");

    /**
     * Whether combustion in an enclosed, unzoned area forms a zone: fire, and lit furnaces and
     * campfires (CORE-056, FIRE-024, D-13), the burning states of the blocks in {@code
     * #zas:combustion}. Read it through {@link ZoneTrigger#COMBUSTION}; the change
     * hook does, when a block catches fire or is lit ({@link
     * io.github.west3436.zas.change.TriggerStates}).
     */
    public static final PresetOption<Boolean> COMBUSTION_FORMS_ZONES = register(
            "combustion_forms_zones",
            PresetType.BOOLEAN,
            false,
            true,
            "Whether combustion forms a zone in an enclosed, unzoned area: a fire starting, or a"
                    + " furnace or campfire being lit (blocks in #zas:combustion) (CORE-056, FIRE-024)");

    /**
     * Whether planting in an enclosed, unzoned area forms a zone (CORE-056, CONT-053, D-33): placing
     * a block of {@code #zas:plants}. Read it through {@link ZoneTrigger#PLANTING}; the change hook
     * does ({@link io.github.west3436.zas.change.TriggerStates}).
     */
    public static final PresetOption<Boolean> PLANTING_FORMS_ZONES = register(
            "planting_forms_zones",
            PresetType.BOOLEAN,
            false,
            true,
            "Whether planting forms a zone in an enclosed, unzoned area: placing a crop, sapling or"
                    + " leaves (blocks in #zas:plants) (CORE-056, CONT-053)");

    /**
     * Whether a pressure difference breaks blocks in the {@code zas:pressure_fragile} tag, such as
     * glass (FLOW-007, FLOW-016, D-28).
     */
    public static final PresetOption<Boolean> BLOCK_PRESSURE_DAMAGE = register(
            "block_pressure_damage",
            PresetType.BOOLEAN,
            false,
            true,
            "Whether a pressure difference breaks blocks in the zas:pressure_fragile tag, such as glass (FLOW-007, FLOW-016)");

    /**
     * O2 a lit crop or sapling in a zone makes from CO2 at light 15, in mol/h (CONT-057, D-61): the
     * rate of every block of {@code #zas:plants} outside {@code #zas:leaf_plants}.
     */
    public static final PresetOption<Double> CROP_PHOTOSYNTHESIS_RATE = register(
            "crop_photosynthesis_rate",
            PresetType.number(0, 100, "mol/h"),
            0.125,
            0.0625,
            "The O2 a lit crop or sapling in a zone makes from CO2 at light 15, in mol/h; it scales"
                    + " with light (CONT-057)");

    /**
     * O2 a lit leaf block in a zone makes from CO2 at light 15, in mol/h (CONT-057, D-61): the rate of
     * the blocks of {@code #zas:leaf_plants}.
     */
    public static final PresetOption<Double> LEAF_PHOTOSYNTHESIS_RATE = register(
            "leaf_photosynthesis_rate",
            PresetType.number(0, 100, "mol/h"),
            0.0625,
            0.03125,
            "The O2 a lit leaf block in a zone makes from CO2 at light 15, in mol/h; it scales with light (CONT-057)");

    /**
     * Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107,
     * D-57).
     */
    public static final PresetOption<Boolean> CONTAINER_EXPLOSIONS_BREAK_BLOCKS = register(
            "container_explosions_break_blocks",
            PresetType.BOOLEAN,
            false,
            true,
            "Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107)");

    private static final List<PresetOption<?>> ALL = List.of(
            AMBIENT_VARIANT,
            COMBUSTION_FORMS_ZONES,
            PLANTING_FORMS_ZONES,
            BLOCK_PRESSURE_DAMAGE,
            CROP_PHOTOSYNTHESIS_RATE,
            LEAF_PHOTOSYNTHESIS_RATE,
            CONTAINER_EXPLOSIONS_BREAK_BLOCKS);

    private static final Map<String, PresetOption<?>> BY_KEY;

    static {
        Map<String, PresetOption<?>> byKey = new LinkedHashMap<>();
        for (PresetOption<?> option : ALL) {
            byKey.put(option.id().getPath(), option);
        }
        BY_KEY = byKey;
    }

    private PresetOptions() {}

    /**
     * Registers the options with the framework, if they aren't yet: loading this class does it. Called
     * as the mod is constructed, so the options are there before any world's preset file is read.
     */
    public static void register() {
        // The static fields have registered them.
    }

    /** Every preset-controlled option of ZAS³, in the order the preset file lists them. */
    public static List<PresetOption<?>> all() {
        return ALL;
    }

    /** The option with a key, the path of its id, as {@code /zas preset} names it. */
    public static Optional<PresetOption<?>> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** How many of ZAS³'s options are overridden. */
    public static int overridden() {
        int n = 0;
        for (PresetOption<?> option : ALL) {
            if (option.source() == PresetOption.Source.OVERRIDE) {
                n++;
            }
        }
        return n;
    }

    private static <T> PresetOption<T> register(
            String key, PresetType<T> type, T normal, T hardcore, String description) {
        return Presets.register(ResourceLocation.fromNamespaceAndPath("zas", key), type, normal, hardcore, description);
    }
}
