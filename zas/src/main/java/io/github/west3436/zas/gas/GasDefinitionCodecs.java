package io.github.west3436.zas.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasVisibility;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack and network formats of gas definitions (CORE-010, CORE-014).
 * The datapack format is documented in {@code docs/datapacks.md}.
 */
public final class GasDefinitionCodecs {
    /** The datapack folder: {@code data/<namespace>/zas/gas/<path>.json} defines gas {@code <namespace>:<path>}. */
    public static final String DIRECTORY = "zas/gas";

    private static final Codec<Integer> RGB =
            Codec.STRING.comapFlatMap(GasDefinitionCodecs::parseRgb, rgb -> String.format("#%06X", rgb));

    private static final Codec<GasVisibility> VISIBILITY = RecordCodecBuilder.<GasVisibility>create(i -> i.group(
                    RGB.fieldOf("color").forGetter(GasVisibility::rgb),
                    Codec.DOUBLE.fieldOf("density_threshold").forGetter(GasVisibility::densityThreshold))
            .apply(i, GasVisibility::new));

    /** A definition file's content; the id comes from the file's path. */
    public record Body(
            String name,
            String formula,
            double molarMass,
            double molarHeatCapacity,
            Optional<GasVisibility> visibility) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("name").forGetter(Body::name),
                        Codec.STRING.fieldOf("formula").forGetter(Body::formula),
                        Codec.DOUBLE.fieldOf("molar_mass").forGetter(Body::molarMass),
                        Codec.DOUBLE.fieldOf("molar_heat_capacity").forGetter(Body::molarHeatCapacity),
                        VISIBILITY.optionalFieldOf("visibility").forGetter(Body::visibility))
                .apply(i, Body::new));

        /**
         * The definition with this body and an id.
         *
         * @throws IllegalArgumentException if a value is out of range
         */
        public GasDefinition toDefinition(ResourceLocation id) {
            return new GasDefinition(id.toString(), name, formula, molarMass, molarHeatCapacity, visibility);
        }
    }

    private static final StreamCodec<ByteBuf, GasVisibility> VISIBILITY_STREAM = StreamCodec.composite(
            ByteBufCodecs.INT,
            GasVisibility::rgb,
            ByteBufCodecs.DOUBLE,
            GasVisibility::densityThreshold,
            GasVisibility::new);

    /** The network form of one definition, id included. */
    public static final StreamCodec<ByteBuf, GasDefinition> STREAM = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            GasDefinition::id,
            ByteBufCodecs.STRING_UTF8,
            GasDefinition::nameKey,
            ByteBufCodecs.STRING_UTF8,
            GasDefinition::formula,
            ByteBufCodecs.DOUBLE,
            GasDefinition::molarMass,
            ByteBufCodecs.DOUBLE,
            GasDefinition::molarHeatCapacity,
            ByteBufCodecs.optional(VISIBILITY_STREAM),
            GasDefinition::visibility,
            GasDefinition::new);

    private GasDefinitionCodecs() {}

    private static DataResult<Integer> parseRgb(String text) {
        if (text.length() == 7 && text.charAt(0) == '#') {
            try {
                return DataResult.success(Integer.parseInt(text.substring(1), 16));
            } catch (NumberFormatException ignored) {
                // falls through to the error below
            }
        }
        return DataResult.error(() -> "color must be \"#RRGGBB\", got \"" + text + "\"");
    }
}
