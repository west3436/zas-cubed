package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** What a client makes of its jets: the caps per source and in total, the setting, and that they end (CLIENT-017). */
class JetEmittersTest {
    private static final double SPRAY = 5.0;

    private record Spawned(double x, double y, double z, double vx, double vy, double vz, float shade) {}

    private final JetEmitters emitters = new JetEmitters(new Random(1));
    private final List<Spawned> spawned = new ArrayList<>();

    private void tick(ParticleDensity density) {
        emitters.tick(0, 0, 0, density, (x, y, z, vx, vy, vz, lifetime, shade, size) -> {
            spawned.add(new Spawned(x, y, z, vx, vy, vz, shade));
        });
    }

    private void spray(double x) {
        emitters.add(GasJetKind.SPRAY, x, 0, 0, 1, 0, 0, SPRAY);
    }

    /** All a spray at the given place makes at a setting, over its whole life. */
    private int sprayTotal(ParticleDensity density) {
        spray(1);
        for (int i = 0; i < JetPlan.SPRAY_TICKS; i++) {
            tick(density);
        }
        int total = spawned.size();
        spawned.clear();
        return total;
    }

    @Test
    void client_017_shares_fill_the_smallest_wants_first_and_the_nearest_first() {
        assertArrayEquals(new int[] {2, 5, 5}, JetEmitters.share(new int[] {2, 30, 30}, 12));
        assertArrayEquals(new int[] {4, 4, 4}, JetEmitters.share(new int[] {30, 30, 30}, 12));
        assertArrayEquals(new int[] {1, 1, 0}, JetEmitters.share(new int[] {30, 30, 30}, 2));
        assertArrayEquals(new int[] {1, 2}, JetEmitters.share(new int[] {1, 2}, 100));
        assertArrayEquals(new int[] {0, 0}, JetEmitters.share(new int[] {3, 4}, 0));
    }

    @Test
    void client_017_a_jet_ends_by_itself() {
        spray(1);
        assertEquals(1, emitters.sources());
        for (int i = 0; i < JetPlan.SPRAY_TICKS; i++) {
            tick(ParticleDensity.ALL);
        }
        int made = spawned.size();
        tick(ParticleDensity.ALL);
        assertEquals(0, emitters.sources(), "the jet is still going");
        assertEquals(made, spawned.size(), "it made particles after it ended");
    }

    @Test
    void client_017_clear_ends_every_jet() {
        spray(1);
        spray(2);
        emitters.clear();
        tick(ParticleDensity.ALL);
        assertEquals(0, emitters.sources());
        assertEquals(0, spawned.size());
    }

    @Test
    void client_017_a_new_jet_at_the_same_place_takes_over() {
        spray(1);
        tick(ParticleDensity.ALL);
        spray(1);
        assertEquals(1, emitters.sources());
        for (int i = 0; i < JetPlan.SPRAY_TICKS; i++) {
            tick(ParticleDensity.ALL);
            assertEquals(1, emitters.sources());
        }
        tick(ParticleDensity.ALL);
        assertEquals(0, emitters.sources());
    }

    @Test
    void client_017_decreased_shows_two_thirds_and_minimal_none() {
        int all = sprayTotal(ParticleDensity.ALL);
        assertEquals(JetPlan.SPRAY_PARTICLES, all);
        assertEquals(Math.floor(all * 2.0 / 3.0), sprayTotal(ParticleDensity.DECREASED), 1);
        assertEquals(0, sprayTotal(ParticleDensity.MINIMAL));
    }

    @Test
    void client_017_a_source_out_of_range_makes_nothing_but_still_ends() {
        spray(JetEmitters.RANGE + 1);
        for (int i = 0; i < JetPlan.SPRAY_TICKS; i++) {
            tick(ParticleDensity.ALL);
        }
        assertEquals(0, spawned.size());
        tick(ParticleDensity.ALL);
        assertEquals(0, emitters.sources());
    }

    @Test
    void client_017_a_large_station_stays_within_the_total_each_tick() {
        // More sprays in range than can show: every tick stays within the total, at each setting.
        for (ParticleDensity density : ParticleDensity.values()) {
            emitters.clear();
            int cap = (int) Math.round(JetEmitters.TOTAL * density.keep());
            for (int i = 0; i < JetEmitters.MAX_SOURCES; i++) {
                emitters.add(GasJetKind.SPRAY, i % 16, (i / 16) % 16, i / 256, 1, 0, 0, SPRAY);
            }
            for (int t = 0; t < JetPlan.SPRAY_TICKS; t++) {
                spawned.clear();
                tick(density);
                assertTrue(spawned.size() <= cap, density + ": " + spawned.size() + " in a tick");
            }
            emitters.clear();
        }
    }

    @Test
    void client_017_when_the_total_runs_out_the_nearest_show() {
        // Far sources first, so the order they arrive in doesn't decide it.
        for (int i = 0; i < 200; i++) {
            emitters.add(GasJetKind.SPRAY, 30, i % 8, i / 8, 1, 0, 0, SPRAY);
        }
        for (int i = 0; i < JetEmitters.TOTAL; i++) {
            emitters.add(GasJetKind.SPRAY, 2, i % 8, i / 8, 1, 0, 0, SPRAY);
        }
        // All of them want 2 in the first tick; the nearest sources take the whole total.
        tick(ParticleDensity.ALL);
        assertEquals(JetEmitters.TOTAL, spawned.size());
        for (Spawned s : spawned) {
            assertTrue(s.x < 10, "a source at " + s.x + " showed before nearer ones");
        }
    }

    @Test
    void client_017_a_jet_is_tinted_neutral() {
        spray(1);
        tick(ParticleDensity.ALL);
        assertTrue(spawned.size() > 0);
        for (Spawned s : spawned) {
            assertTrue(s.shade > 0 && s.shade <= 1, "shade " + s.shade);
        }
    }

    @Test
    void client_018_a_burst_shows_all_its_particles_and_goes_every_way() {
        emitters.add(GasJetKind.BURST, 1, 1, 1, 0, 0, 0, JetPlan.BURST_HIGH);
        for (int i = 0; i < JetPlan.BURST_TICKS; i++) {
            tick(ParticleDensity.ALL);
        }
        assertEquals(JetPlan.BURST_MOST, spawned.size());
        boolean east = false;
        boolean west = false;
        for (Spawned s : spawned) {
            east |= s.vx > 0;
            west |= s.vx < 0;
        }
        assertTrue(east && west, "it only went one way");
        tick(ParticleDensity.ALL);
        assertEquals(0, emitters.sources());
    }

    /** Runs an open end's jet long enough to make some particles. */
    private void openEnd(double molesPerSecond) {
        emitters.add(GasJetKind.OPEN_END, 1, 0, 0, 1, 0, 0, molesPerSecond);
        for (int i = 0; i < 10; i++) {
            tick(ParticleDensity.ALL);
        }
        assertTrue(spawned.size() > 0, "it made nothing");
    }

    @Test
    void client_019_gas_going_out_flies_away_from_the_mouth() {
        openEnd(4);
        for (Spawned s : spawned) {
            assertTrue(s.x >= 1, "it started behind the mouth: " + s.x);
            assertTrue(s.vx > 0, "it isn't flying away: " + s.vx);
        }
    }

    @Test
    void client_019_gas_going_in_flies_into_the_mouth() {
        openEnd(-4);
        JetPlan plan = JetPlan.of(GasJetKind.OPEN_END, -4);
        for (Spawned s : spawned) {
            assertTrue(s.x > 1 + plan.distance() * 0.5, "it started too near the mouth: " + s.x);
            assertTrue(s.vx < 0, "it isn't flying in: " + s.vx);
            // Fly it the rest of its life, as the sprite's friction would, and see where it ends.
            double at = s.x;
            double speed = s.vx;
            for (int i = 0; i < 8; i++) {
                at += speed;
                speed *= JetPlan.FRICTION;
            }
            assertEquals(1, at, 0.5, "it ended away from the mouth");
        }
    }

    @Test
    void client_019_a_flow_that_stops_goes_quiet_by_itself() {
        emitters.add(GasJetKind.OPEN_END, 1, 0, 0, 1, 0, 0, 4);
        for (int i = 0; i < JetPlan.OPEN_END_TICKS; i++) {
            tick(ParticleDensity.ALL);
        }
        int made = spawned.size();
        assertTrue(made > 0);
        for (int i = 0; i < 40; i++) {
            tick(ParticleDensity.ALL);
        }
        assertEquals(made, spawned.size(), "it kept going with no new word from the server");
        assertEquals(0, emitters.sources());
    }

    @Test
    void client_019_a_flow_that_goes_on_never_stops_or_piles_up() {
        // The server sends one every atmos tick: one source, on for good, at its plan's rate.
        double perTick = JetPlan.of(GasJetKind.OPEN_END, 4).perTick();
        int ticks = 0;
        for (int second = 0; second < 10; second++) {
            emitters.add(GasJetKind.OPEN_END, 1, 0, 0, 1, 0, 0, 4);
            assertEquals(1, emitters.sources());
            for (int i = 0; i < 20; i++) {
                tick(ParticleDensity.ALL);
                ticks++;
            }
        }
        assertEquals(perTick * ticks, spawned.size(), 1.0);
    }

    @Test
    void client_018_a_spray_goes_along_its_direction() {
        spray(1);
        tick(ParticleDensity.ALL);
        assertTrue(spawned.size() > 0);
        for (Spawned s : spawned) {
            assertTrue(s.x >= 1, "it started behind the nozzle: " + s.x);
            double speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            assertTrue(s.vx / speed >= Math.cos(JetPlan.SPRAY_SPREAD) - 1e-9, "outside the cone");
        }
    }
}
