package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A listener of an API event that throws (NFR-013). It drives atmos ticks itself, within one game
 * tick, so it has a batch of its own. Area 690.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ListenerFaultTests {
    private static final SourceId SOURCE = Rooms.source("nfr_013");
    private static final String BUS_LOGGER = "net.neoforged.bus.EventBus";

    private ListenerFaultTests() {}

    private static Consumer<OpenAirGasEvent> counting(AtomicInteger heard) {
        return e -> {
            if (SOURCE.equals(e.source())) {
                heard.incrementAndGet();
            }
        };
    }

    /**
     * Each event reaches the listeners before and after the one that throws, the mutation still
     * completes, and only one error is logged a minute, with nothing from the bus. The last part shows
     * what the bus does by itself, which is why ZAS³ doesn't simply post: it rethrows, stops before the
     * later listener, and logs an error each time.
     */
    @GameTest(template = "empty", batch = "nfr_013_listeners", timeoutTicks = 100)
    public static void nfr_013_an_event_listener_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 690);
        Vec3 point = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        AtomicInteger thrown = new AtomicInteger();
        Consumer<OpenAirGasEvent> first = counting(before);
        Consumer<OpenAirGasEvent> last = counting(after);
        Consumer<OpenAirGasEvent> bad = e -> {
            if (SOURCE.equals(e.source())) {
                thrown.incrementAndGet();
                throw new IllegalStateException("the test listener failing");
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, OpenAirGasEvent.class, first);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, OpenAirGasEvent.class, bad);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, OpenAirGasEvent.class, last);
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            for (int i = 1; i <= 3; i++) {
                MutationResult result = r.settle(GasMutations.add(r.level, point, Rooms.oxygen(1), SOURCE));
                h.assertValueEqual(result.outcome(), MutationOutcome.AMBIENT, "the mutation's outcome");
                h.assertValueEqual(thrown.get(), i, "the times it threw");
                h.assertValueEqual(before.get(), i, "what the listener before it heard");
                h.assertValueEqual(after.get(), i, "what the listener after it heard");
            }
            h.assertValueEqual(log.naming("OpenAirGasEvent"), 1, "errors logged about it");
            h.assertValueEqual(log.naming(BUS_LOGGER), 0, "errors the bus logged");

            FaultLog.aMinutePasses();
            r.settle(GasMutations.add(r.level, point, Rooms.oxygen(1), SOURCE));
            h.assertValueEqual(thrown.get(), 4, "the times it threw");
            h.assertValueEqual(log.naming("OpenAirGasEvent"), 2, "errors logged about it a minute later");
            h.assertFalse(r.dimension().isFaulted(), "the listener faulted the dimension");

            boolean propagated = false;
            try {
                NeoForge.EVENT_BUS.post(new OpenAirGasEvent(r.level, BlockPos.ZERO, true, Rooms.oxygen(1), SOURCE));
            } catch (IllegalStateException expected) {
                propagated = true;
            }
            h.assertTrue(propagated, "the bus by itself no longer rethrows");
            h.assertValueEqual(after.get(), 4, "the listener after it, when the bus posts by itself");
            h.assertValueEqual(log.naming(BUS_LOGGER), 1, "errors the bus logged when posting by itself");
        } finally {
            NeoForge.EVENT_BUS.unregister(first);
            NeoForge.EVENT_BUS.unregister(bad);
            NeoForge.EVENT_BUS.unregister(last);
        }
        h.succeed();
    }
}
