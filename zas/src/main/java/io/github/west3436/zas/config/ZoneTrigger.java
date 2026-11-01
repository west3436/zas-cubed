package io.github.west3436.zas.config;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * What can form a zone when it modifies the air of an enclosed, unzoned area (D-13), and whether
 * it does under the active preset (CORE-055, CORE-056).
 *
 * <p>Normal's triggers, the deliberate changes of CORE-055, form zones under both presets: Hardcore
 * adds to them, it doesn't replace them. So they aren't preset options and can't be switched off.
 * The triggers Hardcore adds are preset options with Normal defaults of {@code false}, each
 * overridable on its own (OPS-014). A feature that changes gas asks its trigger whether the change
 * may form a zone and passes the answer with the operation; for a lit furnace that is {@code
 * ZoneTrigger.COMBUSTION.active()}. Any thread.
 */
public enum ZoneTrigger {
    /**
     * API calls (API-012): every change of gas through {@code GasMutations}, which includes the
     * devices and leaks of a content mod such as {@code zas_atmos}, and the {@code /zas gas}
     * commands (CORE-055).
     */
    API_CALLS(null),

    /**
     * A breach of a zone into an enclosed, unzoned area, which joins the zone (CORE-055, GEO-023).
     * The engine resolves breaches itself; this is listed so the whole set is in one place.
     */
    BREACHES(null),

    /** Combustion: fire, lit furnaces and campfires, and the fuel generator (CORE-056, FIRE-024). */
    COMBUSTION(PresetOptions.COMBUSTION_FORMS_ZONES),

    /** Planting in an enclosed area (CORE-056, CONT-053). */
    PLANTING(PresetOptions.PLANTING_FORMS_ZONES);

    private final @Nullable PresetOption<Boolean> option;

    ZoneTrigger(@Nullable PresetOption<Boolean> option) {
        this.option = option;
    }

    /** Whether the trigger forms zones under the active preset and its override, if any. */
    public boolean active() {
        return option == null || option.get();
    }

    /** Whether the trigger forms zones under a preset and the override, if any. */
    public boolean active(AtmospherePreset preset) {
        return option == null || option.get(preset);
    }

    /** The preset option that switches it, or empty for a trigger that is on under both presets. */
    public Optional<PresetOption<Boolean>> option() {
        return Optional.ofNullable(option);
    }
}
