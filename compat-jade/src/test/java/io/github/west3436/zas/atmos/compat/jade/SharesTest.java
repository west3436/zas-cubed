package io.github.west3436.zas.atmos.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which gases a reading lists (COMPAT-031). */
class SharesTest {
    @Test
    void compat_031_standard_air_lists_both_gases_by_share() {
        List<Reading.Share> shares = Shares.of(Map.of("zas:nitrogen", 79.0, "zas:oxygen", 21.0));
        assertEquals(List.of("zas:nitrogen", "zas:oxygen"), gases(shares));
        assertEquals(0.79F, shares.get(0).fraction(), 1e-6F);
        assertEquals(0.21F, shares.get(1).fraction(), 1e-6F);
    }

    @Test
    void compat_031_a_trace_is_left_out_and_half_a_percent_is_not() {
        Map<String, Double> moles = new LinkedHashMap<>();
        moles.put("zas:nitrogen", 99.1);
        moles.put("zas:oxygen", 0.5);
        moles.put("zas:carbon_dioxide", 0.4);
        assertEquals(List.of("zas:nitrogen", "zas:oxygen"), gases(Shares.of(moles)));
    }

    @Test
    void compat_031_no_more_than_four_gases_and_those_the_largest() {
        Map<String, Double> moles = new LinkedHashMap<>();
        moles.put("zas:oxygen", 30.0);
        moles.put("zas:nitrogen", 40.0);
        moles.put("zas:hydrogen", 5.0);
        moles.put("zas:methane", 10.0);
        moles.put("zas:carbon_dioxide", 15.0);
        List<Reading.Share> shares = Shares.of(moles);
        // Hydrogen, the smallest, is the one left out. The rest are in the windows' order.
        assertEquals(List.of("zas:carbon_dioxide", "zas:methane", "zas:nitrogen", "zas:oxygen"), gases(shares));
        // A share is of the whole body, so the four don't add up to 1: the bar shows the rest in grey.
        assertEquals(0.15F, shares.get(0).fraction(), 1e-6F);
    }

    @Test
    void compat_031_gases_keep_the_windows_order_by_path_then_namespace() {
        List<Reading.Share> shares =
                Shares.of(Map.of("zas:oxygen", 1.0, "other:argon", 1.0, "zas:argon", 1.0, "other:zinc", 1.0));
        assertEquals(List.of("other:argon", "zas:argon", "zas:oxygen", "other:zinc"), gases(shares));
    }

    @Test
    void compat_031_a_vacuum_lists_nothing() {
        assertEquals(List.of(), Shares.of(Map.of()));
        assertEquals(List.of(), Shares.of(Map.of("zas:oxygen", 0.0)));
    }

    private static List<String> gases(List<Reading.Share> shares) {
        return shares.stream().map(Reading.Share::gas).toList();
    }
}
