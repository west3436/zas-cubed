package io.github.west3436.zas.engine;

/** S0: a plain-Java class that must end up inside the zas mod jar. */
public final class Engine {
    private Engine() {}

    public static String hello() {
        return "engine-ok";
    }

    /** P = nRT/V, so the spike exercises real engine code from the mod. */
    public static double pressureKPa(double moles, double kelvin, double litres) {
        return moles * 8.314 * kelvin / litres;
    }
}
