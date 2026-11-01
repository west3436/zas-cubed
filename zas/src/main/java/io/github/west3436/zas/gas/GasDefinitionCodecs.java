package io.github.west3436.zas.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasPhaseData;
import io.github.west3436.zas.engine.gas.GasVisibility;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The datapack and network formats of gas definitions (CORE-010, CORE-014, CORE-017) and the
 * datapack folder of gas tags (CORE-012). The datapack formats are documented in {@code
 * docs/datapacks.md}.
 */
public final class GasDefinitionCodecs {
    /** The datapack folder: {@code data/<namespace>/zas/gas/<path>.json} defines gas {@code <namespace>:<path>}. */
    public static final String DIRECTORY = "zas/gas";

    /**
     * The datapack folder of gas tags: {@code data/<namespace>/tags/zas/gas/<path>.json} defines tag
     * {@code #<namespace>:<path>}, in Minecraft's tag format. It is where Minecraft would look for the
     * tags of a registry named {@code zas:gas}.
     */
    public static final String TAG_DIRECTORY = "tags/zas/gas";

    /** The fields of a definition file, which {@code sources} may cite. */
    private static final Set<String> FIELDS =
            Set.of("name", "formula", "molar_mass", "molar_heat_capacity", "visibility", "phase");

    private static final Codec<Integer> RGB =
            Codec.STRING.comapFlatMap(GasDefinitionCodecs::parseRgb, rgb -> String.format("#%06X", rgb));

    private static final Codec<GasVisibility> VISIBILITY = RecordCodecBuilder.<GasVisibility>create(i -> i.group(
                    DatapackErrors.field(RGB, "color").forGetter(GasVisibility::rgb),
                    DatapackErrors.field(Codec.DOUBLE, "density_threshold").forGetter(GasVisibility::densityThreshold))
            .apply(i, GasVisibility::new));

    /**
     * A definition file's {@code phase} object (CORE-017): temperatures in K at 101.325 kPa, latent
     * heats in J/mol, each optional.
     */
    public record PhaseBody(
            Optional<Double> meltingPoint,
            Optional<Double> boilingPoint,
            Optional<Double> sublimationPoint,
            Optional<Double> latentHeatFusion,
            Optional<Double> latentHeatVaporization,
            Optional<Double> latentHeatSublimation) {
        public static final Codec<PhaseBody> CODEC = RecordCodecBuilder.create(i -> i.group(
                        DatapackErrors.optionalField(Codec.DOUBLE, "melting_point")
                                .forGetter(PhaseBody::meltingPoint),
                        DatapackErrors.optionalField(Codec.DOUBLE, "boiling_point")
                                .forGetter(PhaseBody::boilingPoint),
                        DatapackErrors.optionalField(Codec.DOUBLE, "sublimation_point")
                                .forGetter(PhaseBody::sublimationPoint),
                        DatapackErrors.optionalField(Codec.DOUBLE, "latent_heat_fusion")
                                .forGetter(PhaseBody::latentHeatFusion),
                        DatapackErrors.optionalField(Codec.DOUBLE, "latent_heat_vaporization")
                                .forGetter(PhaseBody::latentHeatVaporization),
                        DatapackErrors.optionalField(Codec.DOUBLE, "latent_heat_sublimation")
                                .forGetter(PhaseBody::latentHeatSublimation))
                .apply(i, PhaseBody::new));

        /**
         * The phase data with these values.
         *
         * @throws IllegalArgumentException if a value is out of range or the values contradict each
         *     other; the message starts with the field at fault (OPS-008)
         */
        GasPhaseData toPhaseData() {
            positive("melting_point", meltingPoint);
            positive("boiling_point", boilingPoint);
            positive("sublimation_point", sublimationPoint);
            positive("latent_heat_fusion", latentHeatFusion);
            positive("latent_heat_vaporization", latentHeatVaporization);
            positive("latent_heat_sublimation", latentHeatSublimation);
            if (sublimationPoint.isPresent() && (meltingPoint.isPresent() || boilingPoint.isPresent())) {
                throw new IllegalArgumentException("sublimation_point: a gas that sublimes at 101.325 kPa has no "
                        + "melting_point or boiling_point there");
            }
            if (meltingPoint.isPresent() && boilingPoint.isPresent() && !(meltingPoint.get() < boilingPoint.get())) {
                throw new IllegalArgumentException("melting_point: must be below boiling_point, got "
                        + meltingPoint.get() + " and " + boilingPoint.get());
            }
            if (meltingPoint.isEmpty()
                    && boilingPoint.isEmpty()
                    && sublimationPoint.isEmpty()
                    && latentHeatFusion.isEmpty()
                    && latentHeatVaporization.isEmpty()
                    && latentHeatSublimation.isEmpty()) {
                throw new IllegalArgumentException("is empty; give at least one value, or leave phase out");
            }
            return new GasPhaseData(
                    optional(meltingPoint),
                    optional(boilingPoint),
                    optional(sublimationPoint),
                    optional(latentHeatFusion),
                    optional(latentHeatVaporization),
                    optional(latentHeatSublimation));
        }

        private static void positive(String field, Optional<Double> value) {
            if (value.isPresent() && (!Double.isFinite(value.get()) || value.get() <= 0)) {
                throw new IllegalArgumentException(field + ": must be finite and positive, got " + value.get());
            }
        }

        private static OptionalDouble optional(Optional<Double> value) {
            return value.map(OptionalDouble::of).orElse(OptionalDouble.empty());
        }
    }

    /**
     * A definition file's content; the id comes from the file's path.
     *
     * @param sources citations of the file's values, by field name, such as {@code "molar_mass"}
     *     (CORE-015). The game doesn't read them; they are there for whoever checks the values
     */
    public record Body(
            String name,
            String formula,
            double molarMass,
            double molarHeatCapacity,
            Optional<GasVisibility> visibility,
            Optional<PhaseBody> phase,
            Map<String, String> sources) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                        DatapackErrors.field(Codec.STRING, "name").forGetter(Body::name),
                        DatapackErrors.field(Codec.STRING, "formula").forGetter(Body::formula),
                        DatapackErrors.field(Codec.DOUBLE, "molar_mass").forGetter(Body::molarMass),
                        DatapackErrors.field(Codec.DOUBLE, "molar_heat_capacity")
                                .forGetter(Body::molarHeatCapacity),
                        DatapackErrors.optionalField(VISIBILITY, "visibility").forGetter(Body::visibility),
                        DatapackErrors.optionalField(PhaseBody.CODEC, "phase").forGetter(Body::phase),
                        DatapackErrors.optionalField(
                                        Codec.unboundedMap(Codec.STRING, Codec.STRING), "sources", Map.of())
                                .forGetter(Body::sources))
                .apply(i, Body::new));

        public Body {
            sources = Map.copyOf(sources);
        }

        /**
         * The definition with this body and an id.
         *
         * @throws IllegalArgumentException if a value is out of range; the message starts with the
         *     field at fault (OPS-008)
         */
        public GasDefinition toDefinition(ResourceLocation id) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name: must not be blank");
            }
            if (formula.isBlank()) {
                throw new IllegalArgumentException("formula: must not be blank");
            }
            if (!Double.isFinite(molarMass) || molarMass <= 0) {
                throw new IllegalArgumentException("molar_mass: must be finite and positive, got " + molarMass);
            }
            if (!Double.isFinite(molarHeatCapacity) || molarHeatCapacity <= 0) {
                throw new IllegalArgumentException(
                        "molar_heat_capacity: must be finite and positive, got " + molarHeatCapacity);
            }
            Optional<GasPhaseData> phaseData = Optional.empty();
            if (phase.isPresent()) {
                try {
                    phaseData = Optional.of(phase.get().toPhaseData());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("phase: " + e.getMessage(), e);
                }
            }
            return new GasDefinition(id.toString(), name, formula, molarMass, molarHeatCapacity, visibility, phaseData);
        }

        /** The names in {@code sources} that aren't fields of the format, in order; they are ignored. */
        public List<String> unknownSources() {
            return new TreeMap<>(sources)
                    .keySet().stream().filter(k -> !FIELDS.contains(k)).toList();
        }
    }

    private static final StreamCodec<ByteBuf, GasVisibility> VISIBILITY_STREAM = StreamCodec.composite(
            ByteBufCodecs.INT,
            GasVisibility::rgb,
            ByteBufCodecs.DOUBLE,
            GasVisibility::densityThreshold,
            GasVisibility::new);

    private static final StreamCodec<ByteBuf, OptionalDouble> OPTIONAL_DOUBLE = ByteBufCodecs.optional(
                    ByteBufCodecs.DOUBLE)
            .map(
                    o -> o.map(OptionalDouble::of).orElse(OptionalDouble.empty()),
                    o -> o.isPresent() ? Optional.of(o.getAsDouble()) : Optional.empty());

    private static final StreamCodec<ByteBuf, GasPhaseData> PHASE_STREAM = StreamCodec.composite(
            OPTIONAL_DOUBLE,
            GasPhaseData::meltingPoint,
            OPTIONAL_DOUBLE,
            GasPhaseData::boilingPoint,
            OPTIONAL_DOUBLE,
            GasPhaseData::sublimationPoint,
            OPTIONAL_DOUBLE,
            GasPhaseData::latentHeatFusion,
            OPTIONAL_DOUBLE,
            GasPhaseData::latentHeatVaporization,
            OPTIONAL_DOUBLE,
            GasPhaseData::latentHeatSublimation,
            GasPhaseData::new);

    /** The network form of one definition, id and phase data included. */
    public static final StreamCodec<ByteBuf, GasDefinition> STREAM = NeoForgeStreamCodecs.composite(
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
            ByteBufCodecs.optional(PHASE_STREAM),
            GasDefinition::phase,
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
