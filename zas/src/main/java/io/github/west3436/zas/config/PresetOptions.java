package io.github.west3436.zas.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The options the atmosphere preset sets (OPS-007, D-14), each with a Normal and a Hardcore
 * default and an optional override in the server config's {@code [preset_options]} (OPS-014).
 *
 * <p>This is the one place the rest of the mod asks for a preset-controlled value: read the
 * option's {@link PresetOption#get()}, on any thread, whenever the value is needed, rather than
 * keeping it, so a preset change or a config edit applies from the next read (OPS-016). Zone
 * triggers have their own view in {@link ZoneTrigger}.
 *
 * <p>Options whose feature doesn't exist yet are declared already, with {@link
 * PresetOption#waitsFor()} naming it, so the config and {@code /zas preset} list them and the
 * feature only has to read its option. Clear {@code waitsFor} when the feature lands. SPACE-004's
 * space-mod compat mode joins in R2 with its adapter (D-66).
 */
public final class PresetOptions {
    /**
     * The variant of each dimension's ambient that applies (ENV-002, ENV-010, ENV-012): under
     * Hardcore, the Nether is hot and CO2-rich and the End's air is thin (D-19).
     */
    public static final PresetOption<AtmospherePreset> AMBIENT_VARIANT = new PresetOption<>(
            "ambient_variant",
            PresetOption.enumType(AtmospherePreset.class),
            AtmospherePreset.NORMAL,
            AtmospherePreset.HARDCORE,
            "Which variant of each dimension's ambient atmosphere applies; Hardcore's Nether is hot and"
                    + " CO2-rich and its End thin",
            "ENV-002, ENV-010, ENV-012",
            null);

    /**
     * Whether combustion in an enclosed, unzoned area forms a zone: fire, lit furnaces and
     * campfires, and the fuel generator (CORE-056, FIRE-024, D-13). Read it through {@link
     * ZoneTrigger#COMBUSTION}.
     */
    public static final PresetOption<Boolean> COMBUSTION_FORMS_ZONES = new PresetOption<>(
            "combustion_forms_zones",
            PresetOption.BOOLEAN,
            false,
            true,
            "Whether combustion forms a zone in an enclosed, unzoned area: fire, lit furnaces and"
                    + " campfires, and the fuel generator",
            "CORE-056, FIRE-024",
            "FIRE-024");

    /**
     * Whether planting in an enclosed, unzoned area forms a zone (CORE-056, CONT-053, D-33). Read it
     * through {@link ZoneTrigger#PLANTING}.
     */
    public static final PresetOption<Boolean> PLANTING_FORMS_ZONES = new PresetOption<>(
            "planting_forms_zones",
            PresetOption.BOOLEAN,
            false,
            true,
            "Whether planting forms a zone in an enclosed, unzoned area",
            "CORE-056, CONT-053",
            "CORE-056");

    /**
     * Whether a pressure difference breaks blocks in the {@code zas:pressure_fragile} tag, such as
     * glass (FLOW-007, FLOW-016, D-28).
     */
    public static final PresetOption<Boolean> BLOCK_PRESSURE_DAMAGE = new PresetOption<>(
            "block_pressure_damage",
            PresetOption.BOOLEAN,
            false,
            true,
            "Whether a pressure difference breaks blocks in the zas:pressure_fragile tag, such as glass",
            "FLOW-007, FLOW-016",
            "FLOW-007");

    /** O2 a lit crop or sapling in a zone makes from CO2 at light 15, in mol/h (CONT-057, D-61). */
    public static final PresetOption<Double> CROP_PHOTOSYNTHESIS_RATE = new PresetOption<>(
            "crop_photosynthesis_rate",
            PresetOption.number(0, 100, "mol/h"),
            0.125,
            0.0625,
            "The O2 a lit crop or sapling in a zone makes from CO2 at light 15, in mol/h; it scales" + " with light",
            "CONT-057",
            "CONT-057");

    /** O2 a lit leaf block in a zone makes from CO2 at light 15, in mol/h (CONT-057, D-61). */
    public static final PresetOption<Double> LEAF_PHOTOSYNTHESIS_RATE = new PresetOption<>(
            "leaf_photosynthesis_rate",
            PresetOption.number(0, 100, "mol/h"),
            0.0625,
            0.03125,
            "The O2 a lit leaf block in a zone makes from CO2 at light 15, in mol/h; it scales with light",
            "CONT-057",
            "CONT-057");

    /**
     * Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107,
     * D-57).
     */
    public static final PresetOption<Boolean> CONTAINER_EXPLOSIONS_BREAK_BLOCKS = new PresetOption<>(
            "container_explosions_break_blocks",
            PresetOption.BOOLEAN,
            false,
            true,
            "Whether tank and canister explosions break blocks; they hurt entities either way",
            "CONT-107",
            "CONT-107");

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
            if (byKey.put(option.key(), option) != null) {
                throw new IllegalStateException("two preset options named " + option.key());
            }
        }
        BY_KEY = byKey;
    }

    private PresetOptions() {}

    /** Every preset-controlled option, in config order. */
    public static List<PresetOption<?>> all() {
        return ALL;
    }

    /** The option with a key, as the config and {@code /zas preset} name it. */
    public static Optional<PresetOption<?>> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** How many options are overridden in the loaded config. */
    public static int overridden() {
        int n = 0;
        for (PresetOption<?> option : ALL) {
            if (option.source() == PresetOption.Source.OVERRIDE) {
                n++;
            }
        }
        return n;
    }

    /** Defines every option's config entry in the current section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        for (PresetOption<?> option : ALL) {
            option.define(builder);
        }
    }
}
