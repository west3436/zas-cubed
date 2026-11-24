package io.github.west3436.zas.config;

import io.github.west3436.ss13.api.preset.Preset;
import io.github.west3436.ss13.api.preset.Presets;
import java.util.Locale;

/**
 * The atmosphere difficulty preset (D-14, OPS-007): ZAS³'s view of SS13 Framework's world-wide
 * Normal/Hardcore switch ({@link Presets}), which sets every mod's options at once. Datapacks name
 * an ambient's variant with {@link #id()}, and the {@code ambient_variant} option's values are these.
 */
public enum AtmospherePreset {
    NORMAL,
    HARDCORE;

    /** The lower-case name used in datapacks and config, such as {@code "hardcore"}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The framework's preset of the same name. */
    public Preset preset() {
        return this == HARDCORE ? Preset.HARDCORE : Preset.NORMAL;
    }

    /** ZAS³'s preset of the same name as one of the framework's. */
    public static AtmospherePreset of(Preset preset) {
        return preset == Preset.HARDCORE ? HARDCORE : NORMAL;
    }

    /**
     * The active preset: the framework's switch. {@link #NORMAL} while no world is loaded, and on a
     * client, which isn't sent the server's. Any thread.
     */
    public static AtmospherePreset active() {
        return of(Presets.active());
    }

    /**
     * Switches every mod to a preset, keeping every override, and saves it to the world's preset
     * file, as {@code /zas preset normal|hardcore} does ({@link Presets#switchTo}). Server thread.
     *
     * @throws IllegalStateException if no world is loaded
     */
    public static void switchTo(AtmospherePreset preset) {
        Presets.switchTo(preset.preset());
    }
}
