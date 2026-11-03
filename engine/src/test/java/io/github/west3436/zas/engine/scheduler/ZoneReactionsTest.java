package io.github.west3436.zas.engine.scheduler;

import static io.github.west3436.zas.engine.scheduler.Sim.N2;
import static io.github.west3436.zas.engine.scheduler.Sim.O2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionRate;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Reactions in zones (FIRE-001, phase 7): each zone runs its reactions that don't need ignition
 * every atmos tick, with the conservation checker on (CORE-073).
 */
class ZoneReactionsTest {
    /** O₂ turning into N₂ at 0.1 per second, releasing 1 kJ/mol: a stand-in, with Sim's two gases. */
    private static final ReactionSet SET = ReactionSet.compile(
            Sim.REGISTRY,
            List.of(ReactionDefinition.standard(
                    "test:oxygen_to_nitrogen",
                    Map.of("zas:oxygen", 1.0),
                    Map.of("zas:nitrogen", 1.0),
                    -1000,
                    ReactionConditions.NONE,
                    Optional.of(ReactionRate.constant(0.1)),
                    Optional.empty())),
            (d, why) -> {
                throw new AssertionError(why);
            });

    @Test
    void fire_001_a_zone_reacts_each_atmos_tick_and_the_ledger_balances() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.settings = sim.settings.withHeat(HeatSettings.OFF);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 30, 10);
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        assertNotNull(before);
        double o2 = before.moles(O2);
        double n2 = before.moles(N2);

        sim.settings = sim.settings.withReactions(SET);
        sim.step(); // Sim.step checks conservation each tick
        ZoneView after = sim.zoneAt(1, 65, 1);
        double reacted = o2 * -Math.expm1(-0.1 * sim.settings.exchange().periodSeconds());
        assertEquals(o2 - reacted, after.moles(O2), 1e-9);
        assertEquals(n2 + reacted, after.moles(N2), 1e-9);
        // The heat went into the gas: C·T + Q at the new heat capacity.
        double energy = (n2 * 20.8 + o2 * 21.0) * before.temperature() + 1000 * reacted;
        assertEquals(energy / (after.moles(N2) * 20.8 + after.moles(O2) * 21.0), after.temperature(), 1e-9);
    }

    @Test
    void api_019_a_standalone_mixture_reacts_as_a_zone_does() {
        Sim sim = new Sim(Ambient.STANDARD_AIR);
        sim.settings = sim.settings.withHeat(HeatSettings.OFF);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 30, 10);
        sim.step();
        ZoneView before = sim.zoneAt(1, 65, 1);
        GasMixture standalone = new GasMixture(Sim.REGISTRY, before.volume(), before.temperature());
        standalone.setMoles(N2, before.moles(N2));
        standalone.setMoles(O2, before.moles(O2));

        sim.settings = sim.settings.withReactions(SET);
        sim.step();
        ZoneView zone = sim.zoneAt(1, 65, 1);
        SET.applyStandalone(standalone, sim.settings.exchange().periodSeconds(), false, sim.settings.fire());

        assertEquals(zone.moles(O2), standalone.moles(O2), 1e-12);
        assertEquals(zone.moles(N2), standalone.moles(N2), 1e-12);
        assertEquals(zone.temperature(), standalone.temperature(), 1e-9);
    }

    @Test
    void fire_001_zone_reactions_are_deterministic() {
        // CORE-044: the same ticks give the same zone, bit for bit.
        double[] inline = run(new Sim(Ambient.STANDARD_AIR));
        double[] again = run(new Sim(Ambient.STANDARD_AIR));
        assertEquals(inline[0], again[0], 0);
        assertEquals(inline[1], again[1], 0);
    }

    private static double[] run(Sim sim) {
        sim.settings = sim.settings.withReactions(SET);
        sim.world.room(0, 64, 0, 2, 66, 2);
        sim.add(1, 65, 1, 30, 10);
        sim.steps(5);
        ZoneView z = sim.zoneAt(1, 65, 1);
        return new double[] {z.moles(O2), z.temperature()};
    }
}
