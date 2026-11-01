package io.github.west3436.zas.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * One option the atmosphere preset sets (OPS-007): a default under each preset, and an optional
 * override in the server config's {@code [preset_options]} that holds under either preset, so it
 * survives a preset change (OPS-014).
 *
 * <p>Each option is one key whose value is either {@value #FOLLOW_PRESET}, meaning no override, or
 * a value of the option's type, which overrides the preset. {@link #get()} gives the effective
 * value and {@link #source()} says where it comes from (OPS-015). Values are read from the loaded
 * config on each call, so a preset change or an edited config file applies from the next read.
 * Before a world's config loads, every option has its Normal default. Any thread.
 *
 * <p>The options are declared in {@link PresetOptions}.
 *
 * @param <T> the type of the option's values
 */
public final class PresetOption<T> {
    /** The config value of an option with no override: the active preset's default applies. */
    public static final String FOLLOW_PRESET = "preset";

    /** Where an option's effective value comes from (OPS-015). */
    public enum Source {
        /** The active preset's default: the config says {@value #FOLLOW_PRESET}. */
        PRESET,
        /** An override in the config, which holds under either preset (OPS-014). */
        OVERRIDE;

        /** The lower-case name shown by commands, such as {@code "override"}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String key;
    private final Type<T> type;
    private final T normal;
    private final T hardcore;
    private final String description;
    private final String trace;
    private final @Nullable String waitsFor;
    private volatile ModConfigSpec.@Nullable ConfigValue<Object> entry;

    /**
     * Declares an option.
     *
     * @param key the key under {@code [preset_options]}, also its name in commands
     * @param type how its values are checked, stored and shown
     * @param normal the default under the Normal preset
     * @param hardcore the default under the Hardcore preset
     * @param description what it controls, one sentence for the config comment
     * @param trace the requirements it serves, such as {@code "CORE-056, FIRE-024"}
     * @param waitsFor the requirement of the feature that will read it, while that feature doesn't
     *     exist yet; null once it does
     */
    PresetOption(
            String key,
            Type<T> type,
            T normal,
            T hardcore,
            String description,
            String trace,
            @Nullable String waitsFor) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.normal = Objects.requireNonNull(normal, "normal");
        this.hardcore = Objects.requireNonNull(hardcore, "hardcore");
        this.description = Objects.requireNonNull(description, "description");
        this.trace = Objects.requireNonNull(trace, "trace");
        this.waitsFor = waitsFor;
    }

    /** The key under {@code [preset_options]}, also the option's name in {@code /zas preset}. */
    public String key() {
        return key;
    }

    /** The option's value type. */
    public Type<T> type() {
        return type;
    }

    /** The requirements the option serves, such as {@code "CORE-056, FIRE-024"}. */
    public String trace() {
        return trace;
    }

    /**
     * The requirement of the feature that will read this option, while that feature doesn't exist
     * yet. Until then the option can be set and shown, but changes nothing.
     */
    public Optional<String> waitsFor() {
        return Optional.ofNullable(waitsFor);
    }

    /** The default under a preset, ignoring any override. */
    public T defaultFor(AtmospherePreset preset) {
        return preset == AtmospherePreset.HARDCORE ? hardcore : normal;
    }

    /** The effective value: the override if there is one, else the active preset's default. */
    public T get() {
        return get(ZasServerConfig.preset());
    }

    /** The value under a preset: the override if there is one, else that preset's default. */
    public T get(AtmospherePreset preset) {
        return override().orElseGet(() -> defaultFor(preset));
    }

    /** The override in the config, if any; none while no world's config is loaded. */
    public Optional<T> override() {
        // Reading SPEC first initialises ZasServerConfig, which defines the entry.
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return Optional.empty();
        }
        Object raw = entry().get();
        return followsPreset(raw) ? Optional.empty() : type.parse(raw);
    }

    /** Where the effective value comes from. */
    public Source source() {
        return override().isPresent() ? Source.OVERRIDE : Source.PRESET;
    }

    /**
     * Overrides the option in the loaded config, under either preset. Not written to the file:
     * {@link ZasServerConfig#save()} does that.
     *
     * @throws IllegalStateException if no world's config is loaded
     */
    public void setOverride(T value) {
        requireLoaded();
        entry().set(type.write(Objects.requireNonNull(value, "value")));
    }

    /**
     * Removes the override from the loaded config, so the preset's default applies again. Not
     * written to the file: {@link ZasServerConfig#save()} does that.
     *
     * @throws IllegalStateException if no world's config is loaded
     */
    public void clearOverride() {
        requireLoaded();
        entry().set(FOLLOW_PRESET);
    }

    /** A value shown as commands and the config show it. */
    public String format(T value) {
        return type.format(value);
    }

    @Override
    public String toString() {
        return "PresetOption[" + key + "]";
    }

    /** Defines the option's config entry; called once, while the server config is built. */
    void define(ModConfigSpec.Builder builder) {
        List<String> comment = new ArrayList<>(wrap(description + " (" + trace + ")."));
        comment.add(type.describe() + ", or \"" + FOLLOW_PRESET + "\" to follow the preset.");
        comment.add("Normal: " + format(normal) + ". Hardcore: " + format(hardcore) + ".");
        if (waitsFor != null) {
            comment.add("Not used yet: it waits for " + waitsFor + ".");
        }
        entry = builder.comment(comment.toArray(String[]::new))
                .<Object>define(
                        key,
                        FOLLOW_PRESET,
                        raw -> followsPreset(raw) || type.parse(raw).isPresent());
    }

    private ModConfigSpec.ConfigValue<Object> entry() {
        ModConfigSpec.ConfigValue<Object> e = entry;
        if (e == null) {
            throw new IllegalStateException(this + " has no config entry");
        }
        return e;
    }

    private static void requireLoaded() {
        if (!ZasServerConfig.SPEC.isLoaded()) {
            throw new IllegalStateException("no world's server config is loaded");
        }
    }

    private static boolean followsPreset(@Nullable Object raw) {
        return raw instanceof String s && s.trim().equalsIgnoreCase(FOLLOW_PRESET);
    }

    /** Text split into comment lines of at most about 90 characters, at spaces. */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > 90) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    // ---- Value types -------------------------------------------------------------------

    /**
     * How an option's values are checked, stored in the config and shown.
     *
     * @param <T> the type of the values
     */
    public interface Type<T> {
        /**
         * A value from a config entry or a command argument, or empty if it isn't one. Accepts the
         * stored form and its text, such as {@code true} and {@code "true"}.
         */
        Optional<T> parse(@Nullable Object raw);

        /** The value as the config stores it. */
        Object write(T value);

        /** The value as commands show it and accept it. */
        String format(T value);

        /** The values the config comment says are valid, such as {@code "true or false"}. */
        String describe();

        /** Values to suggest when typing a command. */
        List<String> suggestions();
    }

    /** {@code true} or {@code false}. */
    public static final Type<Boolean> BOOLEAN = new Type<>() {
        @Override
        public Optional<Boolean> parse(@Nullable Object raw) {
            if (raw instanceof Boolean b) {
                return Optional.of(b);
            }
            if (raw instanceof String s) {
                String t = s.trim();
                if (t.equalsIgnoreCase("true")) {
                    return Optional.of(true);
                }
                if (t.equalsIgnoreCase("false")) {
                    return Optional.of(false);
                }
            }
            return Optional.empty();
        }

        @Override
        public Object write(Boolean value) {
            return value;
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }

        @Override
        public String describe() {
            return "true or false";
        }

        @Override
        public List<String> suggestions() {
            return List.of("true", "false");
        }
    };

    /** One of an enum's constants, stored by name. */
    public static <E extends Enum<E>> Type<E> enumType(Class<E> type) {
        E[] constants = type.getEnumConstants();
        List<String> names = Arrays.stream(constants).map(Enum::name).toList();
        return new Type<>() {
            @Override
            public Optional<E> parse(@Nullable Object raw) {
                if (raw instanceof String s) {
                    String t = s.trim();
                    for (E constant : constants) {
                        if (constant.name().equalsIgnoreCase(t)) {
                            return Optional.of(constant);
                        }
                    }
                }
                return type.isInstance(raw) ? Optional.of(type.cast(raw)) : Optional.empty();
            }

            @Override
            public Object write(E value) {
                return value.name();
            }

            @Override
            public String format(E value) {
                return value.name();
            }

            @Override
            public String describe() {
                return names.size() == 2
                        ? names.get(0) + " or " + names.get(1)
                        : String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
            }

            @Override
            public List<String> suggestions() {
                return names;
            }
        };
    }

    /** A finite number from {@code min} to {@code max}. */
    public static Type<Double> number(double min, double max, String unit) {
        return new Type<>() {
            @Override
            public Optional<Double> parse(@Nullable Object raw) {
                double v;
                if (raw instanceof Number n) {
                    v = n.doubleValue();
                } else if (raw instanceof String s) {
                    try {
                        v = Double.parseDouble(s.trim());
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
                return Double.isFinite(v) && v >= min && v <= max ? Optional.of(v) : Optional.empty();
            }

            @Override
            public Object write(Double value) {
                return value;
            }

            @Override
            public String format(Double value) {
                return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }

            @Override
            public String describe() {
                return "a number from " + format(min) + " to " + format(max) + " " + unit;
            }

            @Override
            public List<String> suggestions() {
                return List.of();
            }
        };
    }
}
