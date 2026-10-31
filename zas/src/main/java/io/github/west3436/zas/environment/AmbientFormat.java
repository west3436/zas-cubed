package io.github.west3436.zas.environment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.engine.Ambient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack format of ambient definitions (ENV-001, ENV-010, ENV-003, ENV-007), documented
 * in {@code docs/datapacks.md}. Errors name the field at fault (OPS-008).
 */
public final class AmbientFormat {
    /** The datapack folder: {@code data/<namespace>/zas/ambient/<path>.json} defines dimension {@code <namespace>:<path>}. */
    public static final String DIRECTORY = "zas/ambient";

    private static final Set<String> ATMOSPHERE_FIELDS = Set.of("partial_pressures", "moles", "vacuum", "temperature");
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("presets", "excluded");

    /** A definition that can't be read; the message names the field. */
    public static final class InvalidAmbientException extends Exception {
        InvalidAmbientException(String message) {
            super(message);
        }
    }

    private AmbientFormat() {}

    /**
     * Reads one definition file.
     *
     * @param json the file's content
     * @param warnings receives notes about fields that are ignored
     * @throws InvalidAmbientException if the file is invalid
     */
    public static DimensionAmbient parse(JsonElement json, Consumer<String> warnings) throws InvalidAmbientException {
        JsonObject root = object(json, "the file");
        for (String key : root.keySet()) {
            if (!ATMOSPHERE_FIELDS.contains(key) && !TOP_LEVEL_FIELDS.contains(key)) {
                warnings.accept("unknown field \"" + key + "\" is ignored");
            }
        }
        boolean hasBase = root.keySet().stream().anyMatch(ATMOSPHERE_FIELDS::contains);
        Optional<Ambient> base = hasBase ? Optional.of(atmosphere(root, "")) : Optional.empty();

        Optional<Ambient> normal = base;
        Optional<Ambient> hardcore = base;
        if (root.has("presets")) {
            JsonObject presets = object(root.get("presets"), "presets");
            for (String key : presets.keySet()) {
                String path = "presets." + key;
                AtmospherePreset preset = preset(key, path);
                JsonObject variant = object(presets.get(key), path);
                for (String field : variant.keySet()) {
                    if (!ATMOSPHERE_FIELDS.contains(field)) {
                        warnings.accept("unknown field \"" + path + "." + field + "\" is ignored");
                    }
                }
                Optional<Ambient> ambient = Optional.of(atmosphere(variant, path + "."));
                switch (preset) {
                    case NORMAL -> normal = ambient;
                    case HARDCORE -> hardcore = ambient;
                }
            }
        }

        boolean excluded = false;
        if (root.has("excluded")) {
            JsonPrimitive flag = primitive(root.get("excluded"), "excluded");
            if (!flag.isBoolean()) {
                throw new InvalidAmbientException("excluded must be true or false");
            }
            excluded = flag.getAsBoolean();
        }
        if (normal.isEmpty() && hardcore.isEmpty() && !excluded) {
            throw new InvalidAmbientException(
                    "defines no atmosphere: give partial_pressures, moles or vacuum, or presets");
        }
        return new DimensionAmbient(normal, hardcore, excluded);
    }

    /** One atmosphere: exactly one of partial_pressures, moles or vacuum, and a temperature. */
    private static Ambient atmosphere(JsonObject json, String prefix) throws InvalidAmbientException {
        List<String> given = new ArrayList<>();
        for (String field : List.of("partial_pressures", "moles", "vacuum")) {
            if (json.has(field)) {
                given.add(prefix + field);
            }
        }
        if (given.size() != 1) {
            throw new InvalidAmbientException(
                    given.isEmpty()
                            ? prefix + "partial_pressures, moles or vacuum is required"
                            : "give only one of " + String.join(", ", given));
        }
        Optional<Double> temperature = Optional.empty();
        if (json.has("temperature")) {
            double t = number(json.get("temperature"), prefix + "temperature");
            if (!Double.isFinite(t) || t < 0) {
                throw new InvalidAmbientException(prefix + "temperature must be finite and >= 0 K, got " + t);
            }
            temperature = Optional.of(t);
        }

        if (json.has("vacuum")) {
            JsonPrimitive flag = primitive(json.get("vacuum"), prefix + "vacuum");
            if (!flag.isBoolean() || !flag.getAsBoolean()) {
                throw new InvalidAmbientException(prefix + "vacuum must be true; leave it out otherwise");
            }
            return Ambient.vacuum(temperature.orElse(Ambient.COSMIC_BACKGROUND));
        }
        if (temperature.isEmpty()) {
            throw new InvalidAmbientException(prefix + "temperature is required");
        }
        double t = temperature.get();
        if (json.has("partial_pressures")) {
            TreeMap<String, Double> pressures = amounts(json.get("partial_pressures"), prefix + "partial_pressures");
            if (t == 0 && pressures.values().stream().anyMatch(p -> p > 0)) {
                throw new InvalidAmbientException(prefix + "temperature must be above 0 K for an ambient with gas");
            }
            return new Ambient(pressures, t);
        }
        TreeMap<String, Double> moles = amounts(json.get("moles"), prefix + "moles");
        if (t == 0 && moles.values().stream().anyMatch(n -> n > 0)) {
            throw new InvalidAmbientException(prefix + "temperature must be above 0 K for an ambient with gas");
        }
        return Ambient.ofMolesPerCubicMetre(moles, t);
    }

    /** A map of gas id to a non-negative amount. */
    private static TreeMap<String, Double> amounts(JsonElement json, String path) throws InvalidAmbientException {
        JsonObject map = object(json, path);
        TreeMap<String, Double> amounts = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : map.entrySet()) {
            String field = path + ".\"" + e.getKey() + "\"";
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null) {
                throw new InvalidAmbientException(field + ": not a valid gas id");
            }
            double amount = number(e.getValue(), field);
            if (!Double.isFinite(amount) || amount < 0) {
                throw new InvalidAmbientException(field + " must be finite and >= 0, got " + amount);
            }
            if (amounts.put(id.toString(), amount) != null) {
                throw new InvalidAmbientException(field + ": gas given twice");
            }
        }
        return amounts;
    }

    private static AtmospherePreset preset(String key, String path) throws InvalidAmbientException {
        for (AtmospherePreset preset : AtmospherePreset.values()) {
            if (preset.id().equals(key)) {
                return preset;
            }
        }
        throw new InvalidAmbientException(path + ": unknown preset; use \"normal\" or \"hardcore\"");
    }

    private static JsonObject object(JsonElement json, String path) throws InvalidAmbientException {
        if (json == null || !json.isJsonObject()) {
            throw new InvalidAmbientException(path + " must be an object");
        }
        return json.getAsJsonObject();
    }

    private static JsonPrimitive primitive(JsonElement json, String path) throws InvalidAmbientException {
        if (!json.isJsonPrimitive()) {
            throw new InvalidAmbientException(path + " must be a value, not " + json);
        }
        return json.getAsJsonPrimitive();
    }

    private static double number(JsonElement json, String path) throws InvalidAmbientException {
        JsonPrimitive value = primitive(json, path);
        if (!value.isNumber()) {
            throw new InvalidAmbientException(path + " must be a number, got " + value);
        }
        return value.getAsDouble();
    }
}
