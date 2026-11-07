package io.github.west3436.zas.atmos.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The electrolyzer's numbers (CONT-055, CONT-056). */
class ElectrolyzerMathTest {
    /** CONT-055: 1 mol/s of O₂ and 2 mol/s of H₂, from as many moles of water as of H₂. */
    @Test
    void cont_055_rates() {
        assertEquals(1, ElectrolyzerMath.O2_PER_SECOND, 0);
        assertEquals(2, ElectrolyzerMath.H2_PER_SECOND, 0);
        assertEquals(2, ElectrolyzerMath.waterPerSecond() * ElectrolyzerMath.WATER_MOLES_PER_MB, 1e-12);
    }

    /**
     * CONT-056: a 4-bucket tank, and a bucket, 1 m³ of water, about 55,500 mol, lasts about 7.7 h at
     * 2 mol/s.
     */
    @Test
    void cont_056_a_bucket_lasts_about_7_7_hours() {
        assertEquals(4000, ElectrolyzerMath.TANK_MB);
        assertEquals(55_509, ElectrolyzerMath.WATER_MOLES_PER_MB * ElectrolyzerMath.BUCKET_MB, 1);
        assertEquals(0.03603, ElectrolyzerMath.waterPerSecond(), 1e-5);
        assertEquals(7.7, ElectrolyzerMath.secondsPerBucket() / 3600, 0.05);
    }

    /** A step's share of a second's work: all of it with water to spare, in proportion below, none dry. */
    @Test
    void cont_056_a_step_takes_what_the_water_pays_for() {
        double second = ElectrolyzerMath.waterPerSecond();
        assertEquals(1, ElectrolyzerMath.fraction(4000, 1), 0);
        assertEquals(1, ElectrolyzerMath.fraction(second, 1), 1e-12);
        assertEquals(0.5, ElectrolyzerMath.fraction(second / 2, 1), 1e-12);
        assertEquals(0.25, ElectrolyzerMath.fraction(second / 2, 2), 1e-12);
        assertEquals(0, ElectrolyzerMath.fraction(0, 1), 0);
        assertEquals(0, ElectrolyzerMath.fraction(1e-12, 1), 0);
        assertEquals(0, ElectrolyzerMath.fraction(100, 0), 0);
    }

    /** The block shows a level for each bucket the tank has begun to fill, and none only when empty. */
    @Test
    void cont_056_the_level_it_shows() {
        assertEquals(0, ElectrolyzerMath.level(0));
        assertEquals(1, ElectrolyzerMath.level(0.001));
        assertEquals(1, ElectrolyzerMath.level(1000));
        assertEquals(2, ElectrolyzerMath.level(1000.5));
        assertEquals(3, ElectrolyzerMath.level(2500));
        assertEquals(4, ElectrolyzerMath.level(3999.96));
        assertEquals(4, ElectrolyzerMath.level(4000));
        assertEquals(4, ElectrolyzerMath.level(1e9));
    }
}
