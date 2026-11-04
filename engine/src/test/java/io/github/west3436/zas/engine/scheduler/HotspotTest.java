package io.github.west3436.zas.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Hotspots (FIRE-010, FIRE-011, FIRE-014, FIRE-016, FIRE-017, FIRE-019, DATA-009, API-031) through
 * the whole pipeline, burning H₂ with FIRE-003's hydrogen combustion. The test plays the server thread:
 * after each apply it places and removes gas fires as the intents ask, checking the block first.
 */
class HotspotTest {
    static final GasRegistry REGISTRY = GasRegistry.of(List.of(
            Sim.gas("zas:nitrogen", 0.028, 20.8),
            Sim.gas("zas:oxygen", 0.032, 21.0),
            Sim.gas("zas:hydrogen", 0.002016, 20.52),
            Sim.gas("zas:water_vapor", 0.018, 25.3),
            Sim.gas("zas:carbon_dioxide", 0.044, 28.5)));
    static final int N2 = REGISTRY.indexOf("zas:nitrogen");
    static final int O2 = REGISTRY.indexOf("zas:oxygen");
    static final int H2 = REGISTRY.indexOf("zas:hydrogen");
    static final int H2O = REGISTRY.indexOf("zas:water_vapor");

    /** FIRE-003's hydrogen combustion, as the built-in datapack defines it. */
    static final ReactionSet REACTIONS = ReactionSet.compile(
            REGISTRY,
            List.of(ReactionDefinition.standard(
                    "zas:hydrogen_combustion",
                    Map.of("zas:hydrogen", 1.0, "zas:oxygen", 0.5),
                    Map.of("zas:water_vapor", 1.0),
                    -242_000,
                    ReactionConditions.combustion(
                            new ReactionConditions.FlammabilityLimits("zas:hydrogen", 0.04, 0.75, 858)),
                    Optional.empty(),
                    Optional.empty())),
            (d, why) -> {
                throw new AssertionError(why);
            });

    private static final GeometryPalette.Builder BUILDER = GeometryPalette.builder();
    static final int FIRE = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_FIRE));
    static final int GAS_FIRE = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_GAS_FIRE));
    static final int TORCH = BUILDER.intern(Geometry.AIR.withFlags(Geometry.FLAG_IGNITION_SOURCE));
    static final GeometryPalette PALETTE = BUILDER.build();
    static final int AIR = GeometryPalette.AIR_ID;

    /** The middle of a 3×3×3 room at (0..2, 64..66, 0..2). */
    static final long MIDDLE = SectionKeys.block(1, 65, 1);

    /** A pipeline whose server thread applies fire intents, keeping every fire event. */
    static final class FireSim {
        final Sim sim;
        final List<FireEvent> events = new ArrayList<>();
        final List<FireIntent> intents = new ArrayList<>();

        FireSim(Ambient ambient, double spreadChance) {
            sim = new Sim(new TestWorld(ambient), PALETTE, REGISTRY).withoutHeat();
            sim.settings = sim.settings.withReactions(REACTIONS).withHotspots(new HotspotSettings(spreadChance));
        }

        DimensionPipeline.Applied step() {
            DimensionPipeline.Applied a = sim.step();
            events.addAll(a.fireEvents());
            intents.addAll(a.fireIntents());
            for (FireIntent i : a.fireIntents()) {
                int x = SectionKeys.blockX(i.block());
                int y = SectionKeys.blockY(i.block());
                int z = SectionKeys.blockZ(i.block());
                int here = sim.world.get(x, y, z);
                if (i.kind() == FireIntent.Kind.PLACE && here == AIR) {
                    sim.world.set(x, y, z, GAS_FIRE);
                } else if (i.kind() == FireIntent.Kind.REMOVE && here == GAS_FIRE) {
                    sim.world.set(x, y, z, AIR);
                }
            }
            return a;
        }

        void steps(int n) {
            for (int i = 0; i < n; i++) {
                step();
            }
        }

        /** Builds the 3×3×3 room and fills it with air plus {@code h2} mol of hydrogen. */
        FireSim room(double h2) {
            sim.world.room(0, 64, 0, 2, 66, 2);
            add(1, 65, 1, h2);
            return this;
        }

        long add(int x, int y, int z, double h2) {
            double[] m = new double[REGISTRY.size()];
            m[H2] = h2;
            return sim.pipeline.add(SectionKeys.block(x, y, z), m, Sim.ROOM_T, LedgerCategory.API, true, "test");
        }

        ZoneView zone() {
            return sim.zoneAt(1, 65, 1);
        }

        HotspotTable hotspots() {
            return sim.pipeline.hotspots();
        }
    }

    /** Moles of air in 27,000 L at 101.325 kPa and 293.15 K: about 1,122 mol. */
    private static double airMoles() {
        return Ambient.STANDARD_AIR.moles(Ambient.NITROGEN, 27_000)
                + Ambient.STANDARD_AIR.moles(Ambient.OXYGEN, 27_000);
    }

    /** A hydrogen amount making {@code share} of the room's moles. */
    private static double h2For(double share) {
        return airMoles() * share / (1 - share);
    }

    // ---- FIRE-011 ------------------------------------------------------------------------

    /** A hotspot burns 375 L of its zone's gas per second: 375/27,000 of a 27-cell room's H₂. */
    @Test
    void fire_011_parcel_is_375_litres_per_second() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.sim.world.set(1, 65, 1, FIRE);
        f.step();
        ZoneView before = f.zone();
        assertEquals(1, f.hotspots().size(), "the fire block is a hotspot");
        f.step();
        ZoneView after = f.zone();
        double burned = before.moles(H2) - after.moles(H2);
        assertEquals(before.moles(H2) * 375 / 27_000, burned, 1e-9 * before.moles(H2));
        assertEquals(burned, after.moles(H2O) - before.moles(H2O), 1e-9);
        assertEquals(burned / 2, before.moles(O2) - after.moles(O2), 1e-9);
        assertTrue(after.temperature() > before.temperature(), "burning heats the room");
    }

    /** With a 2 s period the parcel is 750 L (CORE-046). */
    @Test
    void fire_011_parcel_scales_with_the_period() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0);
        f.sim.settings = f.sim.settings.withPeriodTicks(40);
        f.room(h2For(0.10));
        f.sim.world.set(1, 65, 1, FIRE);
        f.step();
        ZoneView before = f.zone();
        f.step();
        double burned = before.moles(H2) - f.zone().moles(H2);
        assertEquals(before.moles(H2) * 750 / 27_000, burned, 1e-9 * before.moles(H2));
    }

    // ---- FIRE-014: ignition ----------------------------------------------------------------

    /**
     * An ignition in a flammable zone starts a gas fire at the position: the server is asked to
     * place it, and once the block is there it burns and the zone's ignition event fires.
     */
    @Test
    void fire_014_ignite_starts_a_gas_fire() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.step();
        long seq = f.sim.pipeline.ignite(MIDDLE, "test");
        DimensionPipeline.Applied a = f.step();
        OpOutcome o = f.sim.outcomes.get(seq);
        assertEquals(OpOutcome.IgnitionStatus.IGNITED, o.ignition().status());
        assertEquals(MIDDLE, o.ignition().block());
        assertEquals(f.zone().id(), o.zoneId());
        assertEquals(
                List.of(new FireIntent(FireIntent.Kind.PLACE, MIDDLE, f.zone().id())), a.fireIntents());
        assertEquals(0, f.hotspots().size(), "pending until the block is there");
        assertTrue(f.events.isEmpty());
        double h2 = f.zone().moles(H2);
        f.step();
        assertEquals(GAS_FIRE, f.sim.world.get(1, 65, 1));
        assertEquals(MIDDLE, f.hotspots().block(0));
        assertTrue(f.zone().moles(H2) < h2, "it burns once its block is there");
        assertEquals(List.of(new FireEvent(FireEvent.Kind.IGNITED, f.zone().id(), MIDDLE)), f.events);
    }

    /** An ignition at a position with a hotspot, or next to one, reports the fire burning there. */
    @Test
    void fire_014_ignite_where_burning() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.sim.world.set(1, 65, 1, FIRE);
        f.step();
        long seq = f.sim.pipeline.ignite(SectionKeys.block(1, 66, 1), "test");
        f.step();
        OpOutcome.Ignition i = f.sim.outcomes.get(seq).ignition();
        assertEquals(OpOutcome.IgnitionStatus.BURNING, i.status());
        assertEquals(MIDDLE, i.block());
    }

    /** Plain air doesn't ignite; outside any zone there is nothing to ignite. */
    @Test
    void fire_014_ignite_needs_a_flammable_zone() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(0);
        f.step();
        long inAir = f.sim.pipeline.ignite(MIDDLE, "test");
        long outside = f.sim.pipeline.ignite(SectionKeys.block(10, 65, 10), "test");
        f.step();
        assertEquals(
                OpOutcome.IgnitionStatus.NOT_FLAMMABLE,
                f.sim.outcomes.get(inAir).ignition().status());
        assertEquals(OpOutcome.Outcome.ZONE, f.sim.outcomes.get(inAir).outcome());
        assertEquals(
                OpOutcome.IgnitionStatus.OPEN,
                f.sim.outcomes.get(outside).ignition().status());
        assertEquals(OpOutcome.Outcome.AMBIENT, f.sim.outcomes.get(outside).outcome());
        assertTrue(f.intents.isEmpty());
    }

    /**
     * An ignition at a position that can't hold a fire, such as a wall next to the room, lights the
     * first neighbour that can.
     */
    @Test
    void fire_014_ignite_from_a_wall_lights_the_neighbour() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.step();
        long seq = f.sim.pipeline.ignite(SectionKeys.block(-1, 65, 1), "test");
        f.step();
        OpOutcome.Ignition i = f.sim.outcomes.get(seq).ignition();
        assertEquals(OpOutcome.IgnitionStatus.IGNITED, i.status());
        assertEquals(SectionKeys.block(0, 65, 1), i.block());
    }

    /** A standing ignition source, such as a torch, lights a flammable zone next to it each tick. */
    @Test
    void fire_014_ignition_source_block_lights_the_zone() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0);
        f.sim.world.room(0, 64, 0, 2, 66, 2);
        f.sim.world.set(1, 64, 1, TORCH);
        f.add(1, 65, 1, 0);
        f.step();
        assertTrue(f.intents.isEmpty(), "air doesn't burn");
        f.add(1, 65, 1, h2For(0.10));
        f.steps(2);
        assertFalse(f.intents.isEmpty(), "the torch lights the hydrogen");
        assertEquals(SectionKeys.block(1, 65, 1), f.intents.get(0).block(), "above the torch, the first open side");
        assertEquals(1, f.hotspots().size());
    }

    /** A gas fire that can't be placed is dropped, and the position isn't tried again for a while. */
    @Test
    void fire_014_refused_placement_is_dropped() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.step();
        long seq = f.sim.pipeline.ignite(MIDDLE, "test");
        // The world moves on: something else takes the position before the intent applies.
        f.sim.betweenCloseAndApply = s -> s.world.set(1, 65, 1, TORCH);
        f.step();
        f.sim.betweenCloseAndApply = s -> {};
        f.sim.world.set(1, 65, 1, AIR);
        assertEquals(
                OpOutcome.IgnitionStatus.IGNITED,
                f.sim.outcomes.get(seq).ignition().status());
        f.step();
        assertEquals(0, f.hotspots().size());
        long again = f.sim.pipeline.ignite(MIDDLE, "test");
        f.step();
        OpOutcome.Ignition i = f.sim.outcomes.get(again).ignition();
        assertEquals(OpOutcome.IgnitionStatus.IGNITED, i.status());
        assertTrue(i.block() != MIDDLE, "the refused position waits; a neighbour lights");
    }

    // ---- FIRE-010: autoignition -----------------------------------------------------------

    /** H₂ in air autoignites at 900 K, above its 858 K, and not at 800 K. */
    @Test
    void fire_010_autoignition_at_900_k_not_800_k() {
        assertTrue(autoignites(900));
        assertFalse(autoignites(800));
    }

    private static boolean autoignites(double kelvin) {
        TreeMap<String, Double> air = new TreeMap<>(Ambient.STANDARD_AIR.partialPressures());
        FireSim f = new FireSim(new Ambient(air, kelvin), 0);
        f.sim.world.room(0, 64, 0, 2, 66, 2);
        double h2 = f.sim.world.ambient.moles(Ambient.NITROGEN, 27_000) / 0.79 * 0.1;
        double[] m = new double[REGISTRY.size()];
        m[H2] = h2;
        f.sim.pipeline.add(SectionKeys.block(1, 65, 1), m, kelvin, LedgerCategory.API, true, "test");
        f.steps(3);
        return !f.intents.isEmpty();
    }

    // ---- FIRE-016: spread -----------------------------------------------------------------

    /**
     * Over many trials, a hotspot spreads to each of its six neighbours with the configured
     * chance per second: 0.3, so 1.8 neighbours a tick on average.
     */
    @Test
    void fire_016_spread_rate_matches_chance() {
        int trials = 400;
        int lit = 0;
        for (int t = 0; t < trials; t++) {
            FireSim f = new FireSim(Ambient.STANDARD_AIR, 0.3).room(h2For(0.10));
            f.sim.engine.setSeed(t);
            f.sim.world.set(1, 65, 1, FIRE);
            f.step();
            for (FireIntent i : f.intents) {
                assertEquals(FireIntent.Kind.PLACE, i.kind());
                lit++;
            }
        }
        double mean = (double) lit / trials;
        // 6 neighbours, binomial(6, 0.3): σ of the mean is √(6·0.3·0.7/400) ≈ 0.056.
        assertEquals(1.8, mean, 0.25, "neighbours lit per tick");
    }

    /** With a 2 s period, a per-second chance of 0.5 spreads with 1 − 0.5² = 0.75 per tick. */
    @Test
    void fire_016_chance_per_second() {
        assertEquals(0.75, new HotspotSettings(0.5).chanceIn(2), 1e-12);
        assertEquals(0.5, new HotspotSettings(0.5).chanceIn(1), 1e-12);
        assertEquals(0, new HotspotSettings(0).chanceIn(1));
    }

    /** With a spread chance of 1, a fire reaches every cell of a flammable room. */
    @Test
    void fire_016_fire_fills_the_room() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 1).room(h2For(0.20));
        f.sim.world.set(1, 65, 1, FIRE);
        f.steps(4);
        java.util.Set<Long> lit = new java.util.HashSet<>();
        for (FireIntent i : f.intents) {
            if (i.kind() == FireIntent.Kind.PLACE) {
                lit.add(i.block());
            }
        }
        assertEquals(26, lit.size(), "every cell but the vanilla fire's");
    }

    // ---- FIRE-017: going out -------------------------------------------------------------

    /**
     * A gas fire goes out once its zone's hydrogen falls below the flammability limit, and the
     * server is asked to remove its block; the zone's fire event goes out with it. With no heat lost,
     * its own heat widens the limit (FIRE-032) until the limit's floor is what stops it.
     */
    @Test
    void fire_017_goes_out_without_fuel() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.045));
        f.step();
        f.sim.pipeline.ignite(MIDDLE, "test");
        f.steps(2);
        assertEquals(1, f.hotspots().size());
        long zone = f.zone().id();
        for (int i = 0; i < 400 && f.hotspots().size() > 0; i++) {
            f.step();
        }
        assertEquals(0, f.hotspots().size(), "out once the hydrogen is under its limit");
        double share = f.zone().moles(H2) / f.zone().totalMoles();
        assertTrue(share < 0.001 && share > 0.0009, "share left: " + share);
        assertEquals(AIR, f.sim.world.get(1, 65, 1), "its block is removed");
        assertEquals(
                FireEvent.Kind.EXTINGUISHED, f.events.get(f.events.size() - 1).kind());
        assertEquals(zone, f.events.get(f.events.size() - 1).zoneId());
    }

    /** A gas fire goes out once O₂ falls under the fire threshold (FIRE-030), fuel or not. */
    @Test
    void fire_017_goes_out_without_oxygen() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.25));
        f.step();
        f.sim.pipeline.ignite(MIDDLE, "test");
        for (int i = 0; i < 200 && (f.hotspots().size() > 0 || i < 3); i++) {
            f.step();
        }
        assertEquals(0, f.hotspots().size());
        ZoneView z = f.zone();
        assertTrue(z.moles(O2) / z.totalMoles() < 0.14, "O2 below the threshold");
        assertTrue(z.moles(H2) / z.totalMoles() > 0.04, "fuel was left");
    }

    /** A vanilla fire is a hotspot but isn't put out here; it just stops burning the zone's gas. */
    @Test
    void fire_019_vanilla_fire_stays_without_fuel() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(0);
        f.sim.world.set(1, 65, 1, FIRE);
        f.steps(3);
        assertEquals(1, f.hotspots().size());
        assertTrue(f.intents.isEmpty());
        assertEquals(1, f.events.size(), "the zone is on fire");
        assertEquals(FireEvent.Kind.IGNITED, f.events.get(0).kind());
        f.sim.world.set(1, 65, 1, AIR);
        f.step();
        assertEquals(0, f.hotspots().size(), "gone with its block");
        assertEquals(FireEvent.Kind.EXTINGUISHED, f.events.get(1).kind());
    }

    /** A gas fire whose block is removed, by a player or water, is no longer a hotspot. */
    @Test
    void fire_018_removed_block_ends_the_hotspot() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.step();
        f.sim.pipeline.ignite(MIDDLE, "test");
        f.steps(2);
        assertEquals(1, f.hotspots().size());
        f.sim.world.set(1, 65, 1, AIR);
        f.step();
        assertEquals(0, f.hotspots().size());
    }

    /** A gas fire in the open, outside any zone, is removed. */
    @Test
    void fire_018_gas_fire_in_the_open_is_removed() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.sim.world.set(6, 65, 6, GAS_FIRE);
        f.step();
        assertNull(f.sim.zoneAt(6, 65, 6));
        assertEquals(List.of(new FireIntent(FireIntent.Kind.REMOVE, SectionKeys.block(6, 65, 6), 0)), f.intents);
        assertEquals(AIR, f.sim.world.get(6, 65, 6));
        assertEquals(0, f.hotspots().size());
    }

    // ---- DATA-009 -----------------------------------------------------------------------

    /**
     * Hotspots aren't saved: after the chunk unloads and loads again with its record, the gas
     * fire block brings its hotspot back.
     */
    @Test
    void data_009_hotspots_rebuilt_from_blocks() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.step();
        f.sim.pipeline.ignite(MIDDLE, "test");
        f.steps(2);
        assertEquals(1, f.hotspots().size());
        long chunk = SectionKeys.chunkOfBlockKey(MIDDLE);
        ChunkRecord saved = f.sim.unload(chunk);
        f.steps(2);
        f.sim.load(chunk, saved);
        f.steps(2);
        assertEquals(1, f.hotspots().size());
        assertEquals(MIDDLE, f.hotspots().block(0));
    }

    // ---- CLIENT-013 ----------------------------------------------------------------------

    /** The debug export lists the hotspots inside its regions. */
    @Test
    void client_013_debug_view_lists_hotspots() {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0).room(h2For(0.10));
        f.sim.world.set(1, 65, 1, FIRE);
        f.sim.scheduler.setDebugRegions(List.of(DebugRegion.around(1, 65, 1, 32)));
        f.step();
        DebugView view = f.sim.scheduler.debugView();
        assertEquals(1, view.hotspots().size());
        DebugView.HotspotEntry h = view.hotspots().get(0);
        assertEquals(MIDDLE, h.block());
        assertFalse(h.gas());
        assertNotNull(f.zone());
        assertEquals(f.zone().id(), h.zone());
    }

    // ---- Determinism ----------------------------------------------------------------------

    /** The same seed spreads the same way; another seed doesn't (§3.5). */
    @Test
    void fire_016_spread_is_seeded() {
        assertEquals(spreadOf(7), spreadOf(7));
        boolean differs = false;
        for (long s = 8; s < 20 && !differs; s++) {
            differs = !spreadOf(7).equals(spreadOf(s));
        }
        assertTrue(differs);
    }

    private static List<FireIntent> spreadOf(long seed) {
        FireSim f = new FireSim(Ambient.STANDARD_AIR, 0.3).room(h2For(0.10));
        f.sim.engine.setSeed(seed);
        f.sim.world.set(1, 65, 1, FIRE);
        f.steps(4);
        return f.intents;
    }
}
