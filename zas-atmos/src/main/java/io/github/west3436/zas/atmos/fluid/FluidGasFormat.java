package io.github.west3436.zas.atmos.fluid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack format that maps fluid tags to gases (COMPAT-140). Each file maps one fluid tag, the
 * one its path names, as ZAS³'s other formats do:
 *
 * <pre>data/&lt;namespace&gt;/zas/fluid_gas/&lt;path&gt;.json   →   fluid tag #&lt;namespace&gt;:&lt;path&gt;</pre>
 *
 * <p>So {@code data/c/zas/fluid_gas/oxygen.json} maps every fluid in {@code #c:oxygen}:
 *
 * <pre>{ "gas": "zas:oxygen" }</pre>
 *
 * <table>
 *   <caption>Fields</caption>
 *   <tr><th>Field</th><th>Meaning</th></tr>
 *   <tr><td>{@code gas}</td><td>The id of the gas the tag's fluids are. Required</td></tr>
 *   <tr><td>{@code temperature}</td><td>Optional. The temperature the gas arrives at, in K, or
 *       {@code "fluid"} for the fluid type's own. Without it, 293.15 K</td></tr>
 *   <tr><td>{@code min_temperature}</td><td>Optional. The least temperature the gas arrives at, in
 *       K. A gas leaves as one of the tag's fluids only once it is at least this hot</td></tr>
 *   <tr><td>{@code one_way}</td><td>Optional, default false. True if the gas never becomes the
 *       tag's fluids</td></tr>
 * </table>
 *
 * <p><b>One file a tag.</b> Since a file's path names its tag, a tag is mapped once: a datapack
 * that maps a tag a lower pack maps replaces that mapping, as for any datapack file, and the two
 * never add up. A datapack removes a mapping with a file whose NeoForge condition fails, {@code
 * {"neoforge:conditions": [{"type": "neoforge:false"}]}}, as for a recipe.
 *
 * <p>A file for a tag that no mod fills loads and applies to nothing. A file that names a gas the
 * server doesn't define loads and is then ignored with a warning, when the map is first used on a
 * server ({@link FluidGases}). An invalid file is logged, naming the file and the field at fault, and
 * skipped.
 */
public final class FluidGasFormat {
    /** The datapack folder, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/fluid_gas";

    /** What a file defines, in log messages. */
    public static final String KIND = "fluid gas mapping";

    /** The value of {@code temperature} that takes the fluid type's. */
    public static final String FLUID = "fluid";

    private static final String GAS = "gas";
    private static final String TEMPERATURE = "temperature";
    private static final String MIN_TEMPERATURE = "min_temperature";
    private static final String ONE_WAY = "one_way";

    /** The fields of a file, and the key NeoForge reads conditions from. */
    private static final Set<String> FIELDS = Set.of(GAS, TEMPERATURE, MIN_TEMPERATURE, ONE_WAY, "neoforge:conditions");

    private FluidGasFormat() {}

    /** A file that isn't a mapping. Its message starts with the field at fault. */
    public static final class InvalidMappingException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidMappingException(String message) {
            super(message);
        }
    }

    /**
     * The path in a pack of the file that maps a tag.
     *
     * @param tag the fluid tag's id
     * @return the path, such as {@code data/c/zas/fluid_gas/oxygen.json}
     */
    public static String file(ResourceLocation tag) {
        return "data/" + tag.getNamespace() + "/" + DIRECTORY + "/" + tag.getPath() + ".json";
    }

    /**
     * Reads one file.
     *
     * @param tag the fluid tag the file's path names
     * @param json the file's content
     * @param warnings told of anything in the file that is ignored while the rest loads
     * @return the mapping
     * @throws InvalidMappingException if the file isn't a mapping; the message starts with the
     *     field at fault
     */
    public static FluidGasMapping parse(ResourceLocation tag, JsonElement json, Consumer<String> warnings)
            throws InvalidMappingException {
        if (!json.isJsonObject()) {
            throw new InvalidMappingException("must be a JSON object");
        }
        JsonObject object = json.getAsJsonObject();
        for (String name : new TreeMap<>(object.asMap()).keySet()) {
            if (!FIELDS.contains(name)) {
                warnings.accept("'" + name + "' names no field of a " + KIND);
            }
        }
        if (!object.has(GAS)) {
            throw new InvalidMappingException(GAS + ": is required");
        }
        ResourceLocation gas = id(object.get(GAS));
        double temperature = Double.NaN;
        boolean fluidTemperature = false;
        if (object.has(TEMPERATURE)) {
            JsonElement value = object.get(TEMPERATURE);
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                if (!FLUID.equals(primitive.getAsString())) {
                    throw new InvalidMappingException(
                            TEMPERATURE + ": must be a number in K or \"" + FLUID + "\", got " + value);
                }
                fluidTemperature = true;
            } else {
                temperature = number(value, TEMPERATURE);
            }
        }
        double minTemperature = object.has(MIN_TEMPERATURE) ? number(object.get(MIN_TEMPERATURE), MIN_TEMPERATURE) : 0;
        boolean oneWay = false;
        if (object.has(ONE_WAY)) {
            JsonElement value = object.get(ONE_WAY);
            if (!(value instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
                throw new InvalidMappingException(ONE_WAY + ": must be true or false, got " + value);
            }
            oneWay = primitive.getAsBoolean();
        }
        try {
            return new FluidGasMapping(tag, gas, temperature, fluidTemperature, minTemperature, oneWay);
        } catch (IllegalArgumentException e) {
            throw new InvalidMappingException(e.getMessage());
        }
    }

    private static ResourceLocation id(JsonElement value) throws InvalidMappingException {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            ResourceLocation id = ResourceLocation.tryParse(primitive.getAsString());
            if (id != null) {
                return id;
            }
        }
        throw new InvalidMappingException(GAS + ": not a gas id: " + value);
    }

    private static double number(JsonElement value, String field) throws InvalidMappingException {
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        throw new InvalidMappingException(field + ": not a number: " + value);
    }
}
