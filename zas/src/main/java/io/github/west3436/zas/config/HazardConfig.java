package io.github.west3436.zas.config;

import io.github.west3436.zas.life.HazardType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * The server config's {@code [hazards]} section (LIFE-011, LIFE-031, LIFE-036): the temperature
 * comfort band, a switch and a scale for each hazard ({@link HazardType}), and per-dimension
 * overrides of either. None of them is preset-controlled.
 *
 * <p>Each getter reads the loaded config on each call, so an edited file applies from the next
 * read, and gives the default while no world's config is loaded. Any thread.
 */
public final class HazardConfig {
    /** The bottom of the temperature comfort band by default, in K (LIFE-011). */
    public static final double DEFAULT_COMFORT_MIN = 220.0;

    /** The top of the temperature comfort band by default, in K (LIFE-011). */
    public static final double DEFAULT_COMFORT_MAX = 360.0;

    /** The bottom of the comfort band in K. */
    public static ModConfigSpec.DoubleValue COMFORT_MIN;

    /** The top of the comfort band in K. */
    public static ModConfigSpec.DoubleValue COMFORT_MAX;

    /** Each hazard's switch. */
    public static final Map<HazardType, ModConfigSpec.BooleanValue> ENABLED = new EnumMap<>(HazardType.class);

    /** Each hazard's damage scale. */
    public static final Map<HazardType, ModConfigSpec.DoubleValue> SCALE = new EnumMap<>(HazardType.class);

    /** Per-dimension overrides, as {@code "<dimension> <hazard> <on|off|scale>"} (LIFE-036). */
    public static ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_OVERRIDES;

    /** The most a scale may be. */
    public static final double MAX_SCALE = 1000.0;

    /** The overrides last parsed, and the list they came from. */
    private static volatile Parsed parsed = new Parsed(List.of(), Map.of());

    private HazardConfig() {}

    /**
     * One dimension's override of one hazard: a switch, a scale, or both when two entries name the
     * same pair.
     *
     * @param enabled the switch, or null to keep the hazard's own
     * @param scale the scale, or null to keep the hazard's own
     */
    public record Override(
            @Nullable Boolean enabled, @Nullable Double scale) {
        Override with(Override later) {
            return new Override(
                    later.enabled != null ? later.enabled : enabled, later.scale != null ? later.scale : scale);
        }
    }

    private record Key(ResourceLocation dimension, HazardType hazard) {}

    private record Parsed(List<? extends String> from, Map<Key, Override> overrides) {}

    /** Defines the section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        builder.push("hazards");
        COMFORT_MIN = builder.comment(
                        "The bottom of the temperature comfort band, in K. Below it, frostbite deals 0.5 HP/s,",
                        "1 HP/s below 200 K and 2 HP/s below 120 K, whichever is highest.")
                .defineInRange("comfort_min_k", DEFAULT_COMFORT_MIN, 0.0, 100_000.0);
        COMFORT_MAX = builder.comment(
                        "The top of the temperature comfort band, in K. Above it, burns deal 1 HP/s,",
                        "2 HP/s above 400 K and 4 HP/s above 1000 K, whichever is highest.")
                .defineInRange("comfort_max_k", DEFAULT_COMFORT_MAX, 0.0, 100_000.0);
        DIMENSION_OVERRIDES = builder.comment(
                        "Per-dimension overrides of the hazards below, each \"<dimension> <hazard> <setting>\",",
                        "where the setting is on, off or a scale: \"minecraft:the_nether burns 0.5\",",
                        "\"minecraft:the_end frostbite off\". The hazards: " + String.join(", ", hazardIds()) + ".")
                .defineListAllowEmpty("dimension_overrides", List.of(), () -> "", HazardConfig::valid);
        for (HazardType t : HazardType.values()) {
            builder.comment(comment(t)).push(t.id());
            ENABLED.put(
                    t, builder.comment("Whether this hazard harms anything.").define("enabled", true));
            SCALE.put(
                    t,
                    builder.comment("Multiplies this hazard's damage; 0 leaves only its effects, if it has any.")
                            .defineInRange("scale", 1.0, 0.0, MAX_SCALE));
            builder.pop();
        }
        builder.pop();
    }

    private static String comment(HazardType t) {
        return switch (t) {
            case HYPOXIA -> "Hypoxia: the air meter drains in a breath with too little O2, then 2 HP/s (LIFE-003).";
            case TOXIC_GAS -> "Toxic gas: the gases' hazard profiles, their effects and damage (LIFE-004).";
            case DECOMPRESSION -> "Decompression: 1 HP/s below an effective 20 kPa (LIFE-010).";
            case BAROTRAUMA -> "Barotrauma: up to 2 HP/s at or above an effective 550 kPa (LIFE-014).";
            case FROSTBITE -> "Frostbite: below the comfort band (LIFE-011).";
            case BURNS -> "Burns: above the comfort band (LIFE-011).";
            case AIRFLOW_IMPACT -> "Airflow impact: being thrown against a block by airflow (FLOW).";
        };
    }

    private static List<String> hazardIds() {
        return Arrays.stream(HazardType.values()).map(HazardType::id).toList();
    }

    /** The bottom of the comfort band in K (LIFE-011). */
    public static double comfortMin() {
        return ZasServerConfig.SPEC.isLoaded() ? COMFORT_MIN.get() : DEFAULT_COMFORT_MIN;
    }

    /** The top of the comfort band in K (LIFE-011). */
    public static double comfortMax() {
        return ZasServerConfig.SPEC.isLoaded() ? COMFORT_MAX.get() : DEFAULT_COMFORT_MAX;
    }

    /** Whether a hazard is on in a dimension: its override there if it has one, else its switch. */
    public static boolean enabled(ResourceKey<Level> dimension, HazardType hazard) {
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return true;
        }
        Override o = override(dimension.location(), hazard);
        return o != null && o.enabled() != null
                ? o.enabled()
                : ENABLED.get(hazard).get();
    }

    /**
     * The factor a hazard's damage is multiplied by in a dimension: 0 while it is off, else its
     * override's scale there if it has one, else its own scale.
     */
    public static double scale(ResourceKey<Level> dimension, HazardType hazard) {
        if (!ZasServerConfig.SPEC.isLoaded()) {
            return 1.0;
        }
        if (!enabled(dimension, hazard)) {
            return 0;
        }
        Override o = override(dimension.location(), hazard);
        return o != null && o.scale() != null ? o.scale() : SCALE.get(hazard).get();
    }

    /** A dimension's override of a hazard, or null. */
    public static @Nullable Override override(ResourceLocation dimension, HazardType hazard) {
        return overrides().get(new Key(dimension, hazard));
    }

    private static Map<Key, Override> overrides() {
        List<? extends String> raw = DIMENSION_OVERRIDES.get();
        Parsed p = parsed;
        if (p.from() != raw) {
            Map<Key, Override> map = new HashMap<>();
            for (String entry : raw) {
                parse(entry)
                        .ifPresent(e -> map.merge(new Key(e.dimension(), e.hazard()), e.override(), Override::with));
            }
            p = new Parsed(raw, Map.copyOf(map));
            parsed = p;
        }
        return p.overrides();
    }

    /** One parsed entry of {@code dimension_overrides}. */
    public record Entry(ResourceLocation dimension, HazardType hazard, Override override) {}

    /**
     * Parses an entry of {@code dimension_overrides}: {@code "<dimension> <hazard> <on|off|scale>"}.
     *
     * @return the entry, or empty if it isn't one
     */
    public static Optional<Entry> parse(String entry) {
        String[] parts = entry.trim().split("\\s+");
        if (parts.length != 3) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(parts[0]);
        Optional<HazardType> hazard = HazardType.byId(parts[1].toLowerCase(Locale.ROOT));
        if (dimension == null || hazard.isEmpty()) {
            return Optional.empty();
        }
        String setting = parts[2].toLowerCase(Locale.ROOT);
        Override o;
        switch (setting) {
            case "on" -> o = new Override(true, null);
            case "off" -> o = new Override(false, null);
            default -> {
                double scale;
                try {
                    scale = Double.parseDouble(setting);
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
                if (!(scale >= 0 && scale <= MAX_SCALE)) {
                    return Optional.empty();
                }
                o = new Override(null, scale);
            }
        }
        return Optional.of(new Entry(dimension, hazard.get(), o));
    }

    private static boolean valid(Object o) {
        return o instanceof String s && parse(s).isPresent();
    }
}
