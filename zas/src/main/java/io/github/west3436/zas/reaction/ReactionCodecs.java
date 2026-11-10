package io.github.west3436.zas.reaction;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.api.reaction.ReactionType;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.reaction.CodeReaction;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionRate;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The datapack format of reaction definitions (FIRE-001, FIRE-006), documented on the wiki's
 * Datapack-Fire-and-Reactions page.
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
            "incomplete_combustion",
            "parameters");

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
     * @param parameters a code type's parameters (API-020), passed to {@link
     *     ReactionType#withParameters}
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
            Optional<Dynamic<?>> parameters,
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
                        DatapackErrors.optionalField(Codec.PASSTHROUGH, "parameters")
                                .forGetter(Body::parameters),
                        DatapackErrors.optionalField(
                                        Codec.unboundedMap(Codec.STRING, Codec.STRING), "sources", Map.of())
                                .forGetter(Body::sources))
                .apply(i, Body::new));

        public Body {
            sources = Map.copyOf(sources);
        }

        /**
         * The definition with this body and an id. A code type (API-020) is looked up among the
         * types registered with {@link Reactions#registerType} and given the body's parameters.
         *
         * @param id the reaction id
         * @throws IllegalArgumentException if a value is out of range, the fields contradict each
         *     other, or the type isn't registered or rejects the parameters; the message starts
         *     with the field at fault (OPS-008)
         */
        public ReactionDefinition toDefinition(ResourceLocation id) {
            boolean standard = type.equals(ReactionDefinition.STANDARD);
            if (standard && enthalpy.isEmpty()) {
                throw new IllegalArgumentException("enthalpy: is required");
            }
            if (standard && parameters.isPresent()) {
                throw new IllegalArgumentException("parameters: only a reaction type implemented in code takes them");
            }
            Optional<CodeReaction> code = Optional.empty();
            ResourceLocation typeId = standard ? null : ResourceLocation.tryParse(type);
            Optional<ReactionType> registered = typeId == null ? Optional.empty() : Reactions.type(typeId);
            if (registered.isPresent()) {
                ReactionType bound;
                try {
                    bound = registered
                            .orElseThrow()
                            .withParameters(
                                    parameters.orElseGet(() -> new Dynamic<>(JsonOps.INSTANCE, new JsonObject())));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("parameters: " + e.getMessage(), e);
                }
                if (bound == null) {
                    throw new IllegalArgumentException("parameters: the type " + type + " gave no reaction for them");
                }
                code = Optional.of(new ApiReactionType(id.toString(), typeId, bound));
            }
            ReactionConditions conditions = new ReactionConditions(
                    minTemperature.orElse(0.0),
                    maxTemperature.orElse(Double.POSITIVE_INFINITY),
                    concentrations,
                    catalysts,
                    requiresIgnition,
                    flammability);
            return new ReactionDefinition(
                    id.toString(), type, reactants, products, enthalpy.orElse(0.0), conditions, rate, incomplete, code);
        }

        /** The names in {@code sources} that aren't fields of the format, in order; they are ignored. */
        public List<String> unknownSources() {
            return new TreeMap<>(sources)
                    .keySet().stream().filter(k -> !FIELDS.contains(k)).toList();
        }
    }

    /**
     * What a client holds in place of a code type's implementation (API-020), which stays on the
     * server: it changes nothing. A client never runs reactions; it only shows them.
     */
    public static final CodeReaction NOT_RUN = input -> null;

    private static final StreamCodec<ByteBuf, Map<String, Double>> COEFFICIENTS_STREAM =
            ByteBufCodecs.map(size -> new TreeMap<>(), ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE);

    private static final StreamCodec<ByteBuf, ReactionConditions.Concentration> CONCENTRATION_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ReactionConditions.Concentration::gas,
                    ByteBufCodecs.DOUBLE,
                    ReactionConditions.Concentration::min,
                    ByteBufCodecs.DOUBLE,
                    ReactionConditions.Concentration::max,
                    ReactionConditions.Concentration::new);

    private static final StreamCodec<ByteBuf, ReactionConditions.FlammabilityLimits> FLAMMABILITY_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    ReactionConditions.FlammabilityLimits::fuel,
                    ByteBufCodecs.DOUBLE,
                    ReactionConditions.FlammabilityLimits::lower,
                    ByteBufCodecs.DOUBLE,
                    ReactionConditions.FlammabilityLimits::upper,
                    ByteBufCodecs.DOUBLE,
                    ReactionConditions.FlammabilityLimits::autoignition,
                    ReactionConditions.FlammabilityLimits::new);

    private static final StreamCodec<ByteBuf, ReactionConditions> CONDITIONS_STREAM = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            ReactionConditions::minTemperature,
            ByteBufCodecs.DOUBLE,
            ReactionConditions::maxTemperature,
            CONCENTRATION_STREAM.apply(ByteBufCodecs.list()),
            ReactionConditions::concentrations,
            CONCENTRATION_STREAM.apply(ByteBufCodecs.list()),
            ReactionConditions::catalysts,
            ByteBufCodecs.BOOL,
            ReactionConditions::requiresIgnition,
            ByteBufCodecs.optional(FLAMMABILITY_STREAM),
            ReactionConditions::flammability,
            ReactionConditions::new);

    private static final StreamCodec<ByteBuf, Optional<ReactionRate>> RATE_STREAM =
            ByteBufCodecs.optional(StreamCodec.composite(
                    ByteBufCodecs.DOUBLE,
                    ReactionRate::perSecond,
                    ByteBufCodecs.DOUBLE,
                    ReactionRate::activationEnergy,
                    ByteBufCodecs.DOUBLE,
                    ReactionRate::referenceTemperature,
                    ReactionRate::new));

    private static final StreamCodec<ByteBuf, Optional<ReactionDefinition.Stoichiometry>> INCOMPLETE_STREAM =
            ByteBufCodecs.optional(StreamCodec.composite(
                    COEFFICIENTS_STREAM,
                    ReactionDefinition.Stoichiometry::reactants,
                    COEFFICIENTS_STREAM,
                    ReactionDefinition.Stoichiometry::products,
                    ByteBufCodecs.DOUBLE,
                    ReactionDefinition.Stoichiometry::enthalpy,
                    ReactionDefinition.Stoichiometry::new));

    /**
     * The network form of one compiled definition, with every condition of the datapack format
     * (COMPAT-060). A code type's parameters and implementation stay on the server: the client
     * reads the type's id, and gets {@link #NOT_RUN} as its implementation.
     */
    public static final StreamCodec<ByteBuf, ReactionDefinition> STREAM = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, d.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, d.type());
                COEFFICIENTS_STREAM.encode(buf, d.reactants());
                COEFFICIENTS_STREAM.encode(buf, d.products());
                buf.writeDouble(d.enthalpy());
                CONDITIONS_STREAM.encode(buf, d.conditions());
                RATE_STREAM.encode(buf, d.rate());
                INCOMPLETE_STREAM.encode(buf, d.incomplete());
            },
            buf -> {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String type = ByteBufCodecs.STRING_UTF8.decode(buf);
                Map<String, Double> reactants = COEFFICIENTS_STREAM.decode(buf);
                Map<String, Double> products = COEFFICIENTS_STREAM.decode(buf);
                double enthalpy = buf.readDouble();
                ReactionConditions conditions = CONDITIONS_STREAM.decode(buf);
                Optional<ReactionRate> rate = RATE_STREAM.decode(buf);
                Optional<ReactionDefinition.Stoichiometry> incomplete = INCOMPLETE_STREAM.decode(buf);
                Optional<CodeReaction> code =
                        type.equals(ReactionDefinition.STANDARD) ? Optional.empty() : Optional.of(NOT_RUN);
                return new ReactionDefinition(
                        id, type, reactants, products, enthalpy, conditions, rate, incomplete, code);
            });

    /** The names of a definition file's fields, as the datapack format writes them. */
    public static Set<String> fields() {
        return FIELDS;
    }

    private ReactionCodecs() {}
}
