package io.github.west3436.zas.atmos.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A reading as the bytes Jade carries (COMPAT-030, COMPAT-031). */
class ReadingCodecTest {
    private static final Reading PIPE = new Reading(
            List.of(new Reading.Row(Reading.Lead.BODY, 101.325F, 293.15F)),
            Shares.of(Map.of("zas:nitrogen", 79.0, "zas:oxygen", 21.0)));

    private static final Reading FILTER = new Reading(
            List.of(
                    new Reading.Row(Reading.Lead.IN, 250F, 300F),
                    new Reading.Row(Reading.Lead.OUT, 0F, Float.NaN),
                    new Reading.Row(Reading.Lead.SIDE, 12.5F, 280F)),
            List.of());

    private static final Reading ELECTROLYZER = new Reading(
            List.of(
                    new Reading.Row(Reading.Lead.GAS, "zas:oxygen", 10F, 293.15F),
                    new Reading.Row(Reading.Lead.GAS, "zas:hydrogen", 20F, 293.15F)),
            List.of());

    private static final Reading HEATER = Reading.of(new Reading.Row(Reading.Lead.BODY, Float.NaN, 295F));

    /** The longest the adapter makes: a tank with the longest id, holding four gases with long ids. */
    private static final Reading LARGEST = new Reading(
            List.of(new Reading.Row(Reading.Lead.ITEM, "zas_atmos:emergency_oxygen_tank", 1013.25F, 293.15F)),
            Shares.of(Map.of(
                    "zas:carbon_dioxide", 1.0,
                    "zas:carbon_monoxide", 1.0,
                    "zas:nitrous_oxide", 1.0,
                    "zas:water_vapor", 1.0)));

    @Test
    void compat_030_every_kind_of_reading_comes_back_as_it_went() {
        for (Reading reading : List.of(PIPE, FILTER, ELECTROLYZER, HEATER, LARGEST)) {
            assertEquals(Optional.of(reading), ReadingCodec.decode(ReadingCodec.encode(reading)));
        }
    }

    /** NaN, which stands for no value, survives the trip. */
    @Test
    void compat_030_a_missing_value_stays_missing() {
        Reading filter = ReadingCodec.decode(ReadingCodec.encode(FILTER)).orElseThrow();
        assertTrue(filter.rows().get(1).hasPressure());
        assertFalse(filter.rows().get(1).hasTemperature());
        Reading heater = ReadingCodec.decode(ReadingCodec.encode(HEATER)).orElseThrow();
        assertFalse(heater.rows().get(0).hasPressure());
        assertTrue(heater.rows().get(0).hasTemperature());
    }

    @Test
    void compat_030_a_reading_is_a_few_dozen_bytes() {
        assertEquals(45, ReadingCodec.encode(PIPE).length);
        assertTrue(ReadingCodec.encode(LARGEST).length < 200, "the largest reading");
    }

    @Test
    void compat_030_bytes_that_are_no_reading_decode_to_nothing() {
        byte[] good = ReadingCodec.encode(PIPE);
        assertEquals(Optional.empty(), ReadingCodec.decode(new byte[0]));
        assertEquals(Optional.empty(), ReadingCodec.decode(Arrays.copyOf(good, good.length - 1)), "cut short");
        assertEquals(Optional.empty(), ReadingCodec.decode(Arrays.copyOf(good, good.length + 1)), "too long");
        byte[] manyRows = good.clone();
        manyRows[0] = (byte) (Reading.MOST_ROWS + 1);
        assertEquals(Optional.empty(), ReadingCodec.decode(manyRows), "more rows than a reading has");
        byte[] noSuchLead = good.clone();
        noSuchLead[1] = (byte) Reading.Lead.values().length;
        assertEquals(Optional.empty(), ReadingCodec.decode(noSuchLead), "a lead that doesn't exist");
        byte[] manyShares = good.clone();
        manyShares[10] = (byte) (Reading.MOST_SHARES + 1);
        assertEquals(Optional.empty(), ReadingCodec.decode(manyShares), "more shares than a reading has");
        assertEquals(Optional.empty(), ReadingCodec.decode(new byte[] {(byte) 0xFF, 0x7F, 0x7F, 0x7F}), "noise");
    }
}
