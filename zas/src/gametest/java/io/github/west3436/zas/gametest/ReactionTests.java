package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.ReactionContext;
import io.github.west3436.zas.api.reaction.ReactionType;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.reaction.ServerReactions;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reaction types implemented in code (API-020), registered from the GameTest source set only, and
 * named by the test datapack's {@code data/zas_gametest/zas/reaction/} definitions:
 *
 * <ul>
 *   <li>{@code zas_gametest:swap} turns the fraction its parameters give of the test gas into N₂
 *       each atmos tick ({@code code_swap.json});
 *   <li>{@code zas_gametest:broken} always throws ({@code code_broken.json}).
 * </ul>
 *
 * <p>Both definitions need {@code zas_gametest:plain_gas} as a catalyst, which no other test puts
 * in a zone, so they leave the other tests' zones alone.
 */
@EventBusSubscriber(modid = ZasMod.MOD_ID)
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactionTests {
    static final ResourceLocation SWAP = ResourceLocation.fromNamespaceAndPath("zas_gametest", "swap");
    static final ResourceLocation BROKEN = ResourceLocation.fromNamespaceAndPath("zas_gametest", "broken");
    static final ResourceLocation TEST_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "test_gas");
    static final ResourceLocation PLAIN_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "plain_gas");

    /** {@code code_swap.json}'s fraction. */
    private static final double FRACTION = 0.25;

    private ReactionTests() {}

    /** Turns a fraction of the test gas it may use into N₂, absorbing no heat. */
    private record Swap(double fraction) implements ReactionType {
        private static final Codec<Double> FRACTION_CODEC =
                Codec.DOUBLE.fieldOf("fraction").codec();

        @Override
        public ReactionChange react(ReactionContext context) {
            double n = context.available(TEST_GAS) * fraction;
            return new ReactionChange(Map.of(TEST_GAS, n), Map.of(N2, n), 0);
        }

        @Override
        public ReactionType withParameters(Dynamic<?> parameters) {
            return new Swap(FRACTION_CODEC.parse(parameters).getOrThrow(IllegalArgumentException::new));
        }
    }

    @SubscribeEvent
    static void register(FMLCommonSetupEvent event) {
        Reactions.registerType(SWAP, new Swap(0));
        Reactions.registerType(BROKEN, context -> {
            throw new IllegalStateException("zas_gametest:broken always fails");
        });
    }

    /**
     * API-020: a test reaction type, named from the test datapack, runs in a zone each atmos tick,
     * and a type that throws beside it changes nothing and doesn't stop the dimension (§3.6).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void api_020_a_code_reaction_type_runs_in_a_zone(GameTestHelper h) {
        h.assertTrue(
                ServerReactions.current().definitions().stream().anyMatch(d -> d.id().equals("zas_gametest:code_swap")),
                "zas_gametest:code_swap didn't load: " + DatapackErrors.recent());
        h.assertTrue(
                ServerReactions.current().definitions().stream()
                        .anyMatch(d -> d.id().equals("zas_gametest:code_broken")),
                "zas_gametest:code_broken didn't load");

        Rooms r = new Rooms(h, 211);
        r.clear(0, 6);
        r.shell(0, 4);
        MutationResult added = r.settle(GasMutations.add(
                r.level,
                r.point(2, 2, 2),
                new GasParcel(Map.of(TEST_GAS, 4.0, PLAIN_GAS, 1.0, O2, 2.0), 293.15),
                source("reaction")));
        h.assertValueEqual(added.outcome(), MutationOutcome.ZONE, "the room didn't zone");

        ZoneInfo before = r.zone(2, 2, 2);
        double gas = before.mixture().moles(TEST_GAS);
        double n2 = before.mixture().moles(N2);
        h.assertTrue(gas > 0, "no test gas left to react");
        r.tick();
        ZoneInfo after = r.zone(2, 2, 2);

        r.close(after.mixture().moles(TEST_GAS), gas * (1 - FRACTION), "test gas after a tick");
        r.close(after.mixture().moles(N2), n2 + gas * FRACTION, "nitrogen after a tick");
        r.close(after.mixture().moles(PLAIN_GAS), before.mixture().moles(PLAIN_GAS), "the catalyst");
        h.assertFalse(r.dimension().isFaulted(), "the broken type faulted the dimension");
        h.succeed();
    }

    /**
     * NFR-013: a type that throws changes nothing, the reaction beside it runs every atmos tick, and
     * the log has one line a minute, however many atmos ticks it throws in.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nfr_013_a_code_reaction_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 691);
        r.clear(0, 6);
        r.shell(0, 4);
        r.settle(GasMutations.add(
                r.level,
                r.point(2, 2, 2),
                new GasParcel(Map.of(TEST_GAS, 4.0, PLAIN_GAS, 1.0, O2, 2.0), 293.15),
                source("nfr_013")));
        String definition = "zas_gametest:code_broken";
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            double gas = r.zone(2, 2, 2).mixture().moles(TEST_GAS);
            r.ticks(3);
            r.close(
                    r.zone(2, 2, 2).mixture().moles(TEST_GAS),
                    gas * Math.pow(1 - FRACTION, 3),
                    "test gas after 3 atmos ticks");
            h.assertValueEqual(log.naming(definition), 1, "errors logged about it");

            FaultLog.aMinutePasses();
            r.tick();
            h.assertValueEqual(log.naming(definition), 2, "errors logged about it a minute later");
            h.assertFalse(r.dimension().isFaulted(), "the broken type faulted the dimension");
        }
        h.succeed();
    }
}
