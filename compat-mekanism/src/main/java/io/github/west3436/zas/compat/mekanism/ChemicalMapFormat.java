package io.github.west3436.zas.compat.mekanism;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack format of the chemical map (MEK-010). Each file maps one chemical, the one its path
 * names, as ZAS³'s other formats do:
 *
 * <pre>data/&lt;namespace&gt;/zas/mekanism_chemical/&lt;path&gt;.json   →   chemical &lt;namespace&gt;:&lt;path&gt;</pre>
 *
 * <p>So {@code data/mekanism/zas/mekanism_chemical/steam.json} maps {@code mekanism:steam}:
 *
 * <pre>{ "gas": "zas:water_vapor", "min_temperature": 373.15 }</pre>
 *
 * <table>
 *   <caption>Fields</caption>
 *   <tr><th>Field</th><th>Meaning</th></tr>
 *   <tr><td>{@code gas}</td><td>The id of the gas the chemical is. This or {@code gases}</td></tr>
 *   <tr><td>{@code gases}</td><td>For a chemical that is several gases: the mole fraction of
 *       each, by gas id, adding up to 1. Such a chemical is one-way</td></tr>
 *   <tr><td>{@code min_temperature}</td><td>Optional. The least temperature the gas arrives at, in
 *       K. Where several chemicals are one gas, it also picks which the gas leaves as</td></tr>
 *   <tr><td>{@code one_way}</td><td>Optional, default false. True if the gas never becomes this
 *       chemical</td></tr>
 * </table>
 *
 * <p>The files are the allow-list: a chemical without one never becomes a gas. A datapack replaces
 * a shipped mapping with a file at the same path, and removes one with a file whose NeoForge
 * condition fails, {@code {"neoforge:conditions": [{"type": "neoforge:false"}]}}, as for a recipe.
 * A file for a chemical that isn't registered loads and never applies, so a pack can map another
 * mod's chemicals. An invalid file is logged, naming the file and the field at fault, and skipped.
 *
 * <p>A file that names a gas the server doesn't define loads and is then ignored with a warning,
 * when the map is first used on a server ({@link ChemicalGases}).
 */
public final class ChemicalMapFormat {
    /** The datapack folder, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/mekanism_chemical";

    /** What a file defines, in log messages. */
    public static final String KIND = "chemical mapping";

    private static final String GAS = "gas";
    private static final String GASES = "gases";
    private static final String MIN_TEMPERATURE = "min_temperature";
    private static final String ONE_WAY = "one_way";

    /** The fields of a file, and the key NeoForge reads conditions from. */
    private static final Set<String> FIELDS = Set.of(GAS, GASES, MIN_TEMPERATURE, ONE_WAY, "neoforge:conditions");

    private ChemicalMapFormat() {}

    /** A file that isn't a chemical mapping. Its message starts with the field at fault. */
    public static final class InvalidMappingException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidMappingException(String message) {
            super(message);
        }
    }

    /**
     * The path in a pack of the file that maps a chemical.
     *
     * @param chemical the chemical's id
     * @return the path, such as {@code data/mekanism/zas/mekanism_chemical/steam.json}
     */
    public static String file(ResourceLocation chemical) {
        return "data/" + chemical.getNamespace() + "/" + DIRECTORY + "/" + chemical.getPath() + ".json";
    }

    /**
     * Reads one file.
     *
     * @param chemical the chemical the file's path names
     * @param json the file's content
     * @param warnings told of anything in the file that is ignored while the rest loads
     * @return the mapping
     * @throws InvalidMappingException if the file isn't a mapping; the message starts with the
     *     field at fault
     */
    public static ChemicalMapping parse(ResourceLocation chemical, JsonElement json, Consumer<String> warnings)
            throws InvalidMappingException {
        if (!json.isJsonObject()) {
            throw new InvalidMappingException("must be a JSON object");
        }
        JsonObject object = json.getAsJsonObject();
        for (String name : new TreeMap<>(object.asMap()).keySet()) {
            if (!FIELDS.contains(name)) {
                warnings.accept("'" + name + "' names no field of a chemical mapping");
            }
        }
        Map<ResourceLocation, Double> gases = gases(object);
        double minTemperature = 0;
        if (object.has(MIN_TEMPERATURE)) {
            minTemperature = number(object.get(MIN_TEMPERATURE), MIN_TEMPERATURE);
        }
        boolean oneWay = false;
        if (object.has(ONE_WAY)) {
            JsonElement value = object.get(ONE_WAY);
            if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
                throw new InvalidMappingException(ONE_WAY + ": must be true or false, got " + value);
            }
            oneWay = primitive.getAsBoolean();
        }
        try {
            return new ChemicalMapping(chemical, gases, minTemperature, oneWay);
        } catch (IllegalArgumentException e) {
            throw new InvalidMappingException(e.getMessage());
        }
    }

    private static Map<ResourceLocation, Double> gases(JsonObject object) throws InvalidMappingException {
        if (object.has(GAS) && object.has(GASES)) {
            throw new InvalidMappingException(GASES + ": can't be given together with " + GAS);
        }
        if (object.has(GAS)) {
            return Map.of(id(object.get(GAS), GAS), 1.0);
        }
        if (!object.has(GASES)) {
            throw new InvalidMappingException(GAS + ": is required, or " + GASES + " for a chemical that is several");
        }
        JsonElement value = object.get(GASES);
        if (!value.isJsonObject()) {
            throw new InvalidMappingException(GASES + ": must be an object of gas ids and fractions, got " + value);
        }
        Map<ResourceLocation, Double> gases = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            ResourceLocation gas = ResourceLocation.tryParse(entry.getKey());
            if (gas == null) {
                throw new InvalidMappingException(GASES + ": not a gas id: \"" + entry.getKey() + "\"");
            }
            gases.put(gas, number(entry.getValue(), GASES + ": " + gas));
        }
        return gases;
    }

    private static ResourceLocation id(JsonElement value, String field) throws InvalidMappingException {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            ResourceLocation id = ResourceLocation.tryParse(primitive.getAsString());
            if (id != null) {
                return id;
            }
        }
        throw new InvalidMappingException(field + ": not a gas id: " + value);
    }

    private static double number(JsonElement value, String field) throws InvalidMappingException {
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        throw new InvalidMappingException(field + ": not a number: " + value);
    }
}
