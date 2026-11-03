package io.github.west3436.zas.reaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.reaction.CodeReaction;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionRate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack format of reaction definitions (FIRE-001, FIRE-006), documented in {@code
 * docs/datapacks.md}.
 */
public final class ReactionCodecs {
    /** The datapack folder: {@code data/<namespace>/zas/reaction/<path>.json} defines reaction {@code <namespace>:<path>}. */
    public static final String DIRECTORY = "zas/reaction";

    /** The fields of a definition file, which {@code sources} may cite. */
    private static final Set<String> FIELDS = Set.of(
            "type",
            "reactants",
            "products",
            "enthalpy",
            "min_temperature",
            "max_temperature",
            "concentrations",
            "catalysts",
            "requires_ignition",
            "flammability",
            "rate",
            "incomplete_combustion");

    private static final Codec<Map<String, Double>> COEFFICIENTS = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    private static final Codec<ReactionConditions.Concentration> CONCENTRATION = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(Codec.STRING, "gas").forGetter(ReactionConditions.Concentration::gas),
                    DatapackErrors.optionalField(Codec.DOUBLE, "min", 0.0)
                            .forGetter(ReactionConditions.Concentration::min),
                    DatapackErrors.optionalField(Codec.DOUBLE, "max", 1.0)
                            .forGetter(ReactionConditions.Concentration::max))
            .apply(i, ReactionConditions.Concentration::new));

    private static final Codec<ReactionConditions.Concentration> CATALYST = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(Codec.STRING, "gas").forGetter(ReactionConditions.Concentration::gas),
                    DatapackErrors.optionalField(Codec.DOUBLE, "min", 0.0)
                            .forGetter(ReactionConditions.Concentration::min))
            .apply(i, (gas, min) -> new ReactionConditions.Concentration(gas, min, 1)));

    private static final Codec<ReactionConditions.FlammabilityLimits> FLAMMABILITY =
            RecordCodecBuilder.create(i -> i.group(
                            DatapackErrors.field(Codec.STRING, "fuel")
                                    .forGetter(ReactionConditions.FlammabilityLimits::fuel),
                            DatapackErrors.field(Codec.DOUBLE, "lower")
                                    .forGetter(ReactionConditions.FlammabilityLimits::lower),
                            DatapackErrors.field(Codec.DOUBLE, "upper")
                                    .forGetter(ReactionConditions.FlammabilityLimits::upper),
                            DatapackErrors.optionalField(Codec.DOUBLE, "autoignition", Double.POSITIVE_INFINITY)
                                    .forGetter(ReactionConditions.FlammabilityLimits::autoignition))
                    .apply(i, ReactionConditions.FlammabilityLimits::new));

    private static final Codec<ReactionRate> RATE = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(Codec.DOUBLE, "per_second").forGetter(ReactionRate::perSecond),
                    DatapackErrors.optionalField(Codec.DOUBLE, "activation_energy", 0.0)
                            .forGetter(ReactionRate::activationEnergy),
                    DatapackErrors.optionalField(Codec.DOUBLE, "reference_temperature", 298.15)
                            .forGetter(ReactionRate::referenceTemperature))
            .apply(i, ReactionRate::new));

    private static final Codec<ReactionDefinition.Stoichiometry> STOICHIOMETRY = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(COEFFICIENTS, "reactants")
                            .forGetter(ReactionDefinition.Stoichiometry::reactants),
                    DatapackErrors.field(COEFFICIENTS, "products")
                            .forGetter(ReactionDefinition.Stoichiometry::products),
                    DatapackErrors.field(Codec.DOUBLE, "enthalpy")
                            .forGetter(ReactionDefinition.Stoichiometry::enthalpy))
            .apply(i, ReactionDefinition.Stoichiometry::new));

    /**
     * A definition file's content; the id comes from the file's path. The nested objects are built
     * as they are read, so a value out of range fails the read, naming its field.
     *
     * @param sources citations of the file's values, by field name (CORE-015); the game doesn't
     *     read them
     */
    public record Body(
            String type,
            Map<String, Double> reactants,
            Map<String, Double> products,
            Optional<Double> enthalpy,
            Optional<Double> minTemperature,
            Optional<Double> maxTemperature,
            List<ReactionConditions.Concentration> concentrations,
            List<ReactionConditions.Concentration> catalysts,
            boolean requiresIgnition,
            Optional<ReactionConditions.FlammabilityLimits> flammability,
            Optional<ReactionRate> rate,
            Optional<ReactionDefinition.Stoichiometry> incomplete,
            Map<String, String> sources) {
        public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                        DatapackErrors.optionalField(Codec.STRING, "type", ReactionDefinition.STANDARD)
                                .forGetter(Body::type),
                        DatapackErrors.optionalField(COEFFICIENTS, "reactants", Map.of())
                                .forGetter(Body::reactants),
                        DatapackErrors.optionalField(COEFFICIENTS, "products", Map.of())
                                .forGetter(Body::products),
                        DatapackErrors.optionalField(Codec.DOUBLE, "enthalpy").forGetter(Body::enthalpy),
                        DatapackErrors.optionalField(Codec.DOUBLE, "min_temperature")
                                .forGetter(Body::minTemperature),
                        DatapackErrors.optionalField(Codec.DOUBLE, "max_temperature")
                                .forGetter(Body::maxTemperature),
                        DatapackErrors.optionalField(CONCENTRATION.listOf(), "concentrations", List.of())
                                .forGetter(Body::concentrations),
                        DatapackErrors.optionalField(CATALYST.listOf(), "catalysts", List.of())
                                .forGetter(Body::catalysts),
                        DatapackErrors.optionalField(Codec.BOOL, "requires_ignition", false)
                                .forGetter(Body::requiresIgnition),
                        DatapackErrors.optionalField(FLAMMABILITY, "flammability")
                                .forGetter(Body::flammability),
                        DatapackErrors.optionalField(RATE, "rate").forGetter(Body::rate),
                        DatapackErrors.optionalField(STOICHIOMETRY, "incomplete_combustion")
                                .forGetter(Body::incomplete),
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
         * @param id the reaction id
         * @param code the implementation of the body's type, if it names a code type (API-020)
         * @throws IllegalArgumentException if a value is out of range or the fields contradict
         *     each other; the message starts with the field at fault (OPS-008)
         */
        public ReactionDefinition toDefinition(ResourceLocation id, Optional<CodeReaction> code) {
            boolean standard = type.equals(ReactionDefinition.STANDARD);
            if (standard && enthalpy.isEmpty()) {
                throw new IllegalArgumentException("enthalpy: is required");
            }
            ReactionConditions conditions = new ReactionConditions(
                    minTemperature.orElse(0.0),
                    maxTemperature.orElse(Double.POSITIVE_INFINITY),
                    concentrations,
                    catalysts,
                    requiresIgnition,
                    flammability);
            return new ReactionDefinition(
                    id.toString(),
                    type,
                    reactants,
                    products,
                    enthalpy.orElse(0.0),
                    conditions,
                    rate,
                    incomplete,
                    standard ? Optional.empty() : code);
        }

        /** The names in {@code sources} that aren't fields of the format, in order; they are ignored. */
        public List<String> unknownSources() {
            return new TreeMap<>(sources)
                    .keySet().stream().filter(k -> !FIELDS.contains(k)).toList();
        }
    }

    private ReactionCodecs() {}
}
