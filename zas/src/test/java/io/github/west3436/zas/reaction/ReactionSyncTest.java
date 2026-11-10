package io.github.west3436.zas.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The network form of reaction definitions, which clients receive for recipe viewers (COMPAT-060). */
class ReactionSyncTest {
    @Test
    void compat_060_every_shipped_reaction_survives_the_network_codec() throws IOException {
        List<ReactionDefinition> shipped = ReactionFormatTest.shippedReactions();
        assertEquals(4, shipped.size(), "the shipped reactions");
        assertEquals(shipped, roundTrip(shipped));
    }

    @Test
    void compat_060_a_definition_with_every_field_survives_the_network_codec() {
        ReactionDefinition everything = ReactionFormatTest.parse("""
                {
                  "reactants": {"zas:methane": 1, "zas:oxygen": 2},
                  "products": {"zas:carbon_dioxide": 1, "zas:water_vapor": 2},
                  "enthalpy": -802000,
                  "min_temperature": 250,
                  "max_temperature": 3000,
                  "concentrations": [{"gas": "zas:oxygen", "min": 0.1, "max": 0.9}, {"gas": "zas:nitrogen"}],
                  "catalysts": [{"gas": "zas:carbon_monoxide", "min": 0.001}, {"gas": "zas:hydrogen"}],
                  "requires_ignition": true,
                  "flammability": {"fuel": "zas:methane", "lower": 0.05, "upper": 0.15, "autoignition": 810},
                  "incomplete_combustion": {
                    "reactants": {"zas:methane": 1, "zas:oxygen": 1.5},
                    "products": {"zas:carbon_monoxide": 1, "zas:water_vapor": 2},
                    "enthalpy": -519000
                  }
                }
                """).orElseThrow();
        ReactionDefinition rated = ReactionFormatTest.parse("""
                {
                  "reactants": {"zas:nitrous_oxide": 1},
                  "products": {"zas:nitrogen": 1, "zas:oxygen": 0.5},
                  "enthalpy": 82050,
                  "rate": {"per_second": 0.012, "activation_energy": 250000, "reference_temperature": 1000}
                }
                """).orElseThrow();
        // The defaults that aren't finite: no upper temperature, and a fuel that never lights itself.
        ReactionDefinition unbounded = ReactionFormatTest.parse("""
                {
                  "reactants": {"zas:hydrogen": 1},
                  "enthalpy": 0,
                  "requires_ignition": true,
                  "flammability": {"fuel": "zas:hydrogen", "lower": 0.04, "upper": 0.75}
                }
                """).orElseThrow();
        assertEquals(Double.POSITIVE_INFINITY, unbounded.conditions().maxTemperature());
        assertEquals(
                Double.POSITIVE_INFINITY,
                unbounded.conditions().flammability().orElseThrow().autoignition());

        List<ReactionDefinition> sent = List.of(everything, rated, unbounded);
        assertEquals(sent, roundTrip(sent));
    }

    @Test
    void compat_060_a_code_type_arrives_as_its_id_without_its_implementation() {
        ReactionDefinition sent = new ReactionDefinition(
                "test:swap",
                "test:swap_type",
                Map.of(),
                Map.of("zas:nitrogen", 1.0),
                0,
                new ReactionConditions(
                        0,
                        Double.POSITIVE_INFINITY,
                        List.of(),
                        List.of(new ReactionConditions.Concentration("zas:oxygen", 0, 1)),
                        false,
                        Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(input -> {
                    throw new AssertionError("a client never runs a reaction");
                }));
        ReactionDefinition got = roundTrip(List.of(sent)).get(0);
        assertEquals("test:swap_type", got.type());
        assertSame(ReactionCodecs.NOT_RUN, got.code().orElseThrow());
        assertEquals(null, got.code().orElseThrow().compute(null), "the stand-in changes nothing");
        assertEquals(sent.products(), got.products());
        assertEquals(sent.conditions(), got.conditions());
        assertTrue(got.reactants().isEmpty());
    }

    /** The definitions as a client decodes them from the payload. */
    private static List<ReactionDefinition> roundTrip(List<ReactionDefinition> definitions) {
        ByteBuf buf = Unpooled.buffer();
        try {
            ReactionDefinitionsPayload.STREAM_CODEC.encode(buf, new ReactionDefinitionsPayload(definitions));
            ReactionDefinitionsPayload received = ReactionDefinitionsPayload.STREAM_CODEC.decode(buf);
            assertEquals(0, buf.readableBytes(), "the payload left unread bytes");
            return new ArrayList<>(received.definitions());
        } finally {
            buf.release();
        }
    }
}
