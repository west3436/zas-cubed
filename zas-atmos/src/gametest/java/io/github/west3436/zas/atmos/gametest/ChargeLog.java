package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;

/**
 * What an FE store pays over time, read at every game tick, for a GameTest that lets the game step
 * its devices in real time. A test that steps them itself, within one game tick, can't see a repeat
 * charge: the game clock stands still there, so a part once supplied counts as supplied for the rest
 * of the test ({@code DevicePower#suppliedThisAtmosTick}).
 *
 * <p>It suits a store that only falls, such as a controller's buffer with nothing beside it to fill
 * it, or an FE block it draws from. Give the test a batch of its own and force-load its chunks, and
 * start watching at least two atmos ticks after the last step the test drove itself, so the game's
 * own steps have settled into their places in the period.
 */
final class ChargeLog {
    /**
     * A fall in the store.
     *
     * @param tick the game tick it was read
     * @param fe the FE it fell by
     */
    record Charge(long tick, int fe) {}

    private final GameTestHelper h;
    private final String name;
    private final IntSupplier store;
    private final List<Charge> charges = new ArrayList<>();
    private int last;

    /**
     * @param h the test
     * @param name what the store is, for failure messages
     * @param store reads the store's FE
     */
    ChargeLog(GameTestHelper h, String name, IntSupplier store) {
        this.h = h;
        this.name = name;
        this.store = store;
    }

    /**
     * Adds to a sequence: read the logs at every game tick for a number of atmos ticks. A device that
     * pays once per atmos tick pays exactly that many times in the time. Watches can follow each other,
     * and a log keeps what it has seen.
     *
     * @param seq the sequence
     * @param h the test
     * @param atmosTicks how long to watch
     * @param eachTick also run at every game tick, for what the log doesn't read. It can't fail the test
     *     from here, so have it record what it finds
     * @param logs the logs
     * @return the sequence
     */
    static GameTestSequence watch(
            GameTestSequence seq, GameTestHelper h, int atmosTicks, Runnable eachTick, ChargeLog... logs) {
        long[] end = new long[1];
        return seq.thenExecute(() -> {
                    end[0] = now(h) + (long) atmosTicks * AtmosTicks.PERIOD;
                    for (ChargeLog log : logs) {
                        log.last = log.store.getAsInt();
                    }
                })
                .thenWaitUntil(() -> {
                    eachTick.run();
                    for (ChargeLog log : logs) {
                        log.read();
                    }
                    h.assertTrue(now(h) >= end[0], "still watching");
                });
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getServer().getTickCount();
    }

    private void read() {
        int current = store.getAsInt();
        if (current < last) {
            charges.add(new Charge(now(h), last - current));
        }
        last = current;
    }

    /** What the log has seen, in order. */
    List<Charge> charges() {
        return Collections.unmodifiableList(charges);
    }

    /**
     * Asserts the store paid exactly these amounts, in this order, each one atmos period after the
     * one before: no charge repeated within a period, none doubled and none missed.
     *
     * @param fe the amounts
     */
    void assertCharged(int... fe) {
        List<Integer> expected = new ArrayList<>();
        for (int f : fe) {
            expected.add(f);
        }
        List<Integer> actual = new ArrayList<>();
        for (Charge c : charges) {
            actual.add(c.fe());
        }
        h.assertValueEqual(actual, expected, name + "'s charges at " + ticks());
        for (int i = 1; i < charges.size(); i++) {
            h.assertValueEqual(
                    charges.get(i).tick() - charges.get(i - 1).tick(),
                    (long) AtmosTicks.PERIOD,
                    name + "'s game ticks between charges at " + ticks());
        }
    }

    /**
     * Asserts the store paid the same amount a number of times, each one atmos period after the one
     * before.
     *
     * @param fe the amount
     * @param times how many times
     */
    void assertChargedEach(int fe, int times) {
        int[] all = new int[times];
        Arrays.fill(all, fe);
        assertCharged(all);
    }

    private List<Long> ticks() {
        return charges.stream().map(Charge::tick).toList();
    }
}
