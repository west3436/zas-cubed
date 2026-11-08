package io.github.west3436.zas.s10;

import java.util.LinkedHashMap;
import java.util.Map;

/** NFR-001's list of what atmos work is, by the class a part's code lives in. */
final class Categories {
    static final String APPLY = "Snapshotting and applying results (CORE-047)";
    static final String DEVICES = "Devices (D-50)";
    static final String HAZARDS = "Hazards and breathing (LIFE-033)";
    static final String AIRFLOW = "Airflow (FLOW-013)";
    static final String SYNC = "Client sync (DATA-005)";
    static final String PROVIDERS = "Ambient and seal providers (ENV-015, API-028)";
    static final String CHANGES = "Change tracking (M1, GEO-010)";
    static final String FIRE = "Fire and ignition";
    static final String PERSISTENCE = "Saving and loading (DATA-002)";
    static final String BENCH = "The bench's own API calls (stand-ins)";
    static final String OTHER = "Other";

    /** First match wins: a class name prefix, then its category. */
    private static final Map<String, String> PREFIXES = new LinkedHashMap<>();

    static {
        String z = "io.github.west3436.zas.";
        PREFIXES.put(z + "atmos.device.DeviceActivitySync", SYNC);
        PREFIXES.put(z + "atmos.particle.", SYNC);
        PREFIXES.put(z + "atmos.analyzer.", SYNC);
        PREFIXES.put(z + "atmos.", DEVICES);
        PREFIXES.put(z + "simulation.", APPLY);
        PREFIXES.put(z + "change.", CHANGES);
        PREFIXES.put(z + "life.", HAZARDS);
        PREFIXES.put(z + "airflow.", AIRFLOW);
        PREFIXES.put(z + "hud.", SYNC);
        PREFIXES.put(z + "debug.", SYNC);
        PREFIXES.put(z + "gas.", SYNC);
        PREFIXES.put(z + "environment.", PROVIDERS);
        PREFIXES.put(z + "geometry.", PROVIDERS);
        PREFIXES.put(z + "fire.", FIRE);
        PREFIXES.put(z + "ignition.", FIRE);
        PREFIXES.put(z + "hotspot.", FIRE);
        PREFIXES.put(z + "persistence.", PERSISTENCE);
        PREFIXES.put("bench", BENCH);
    }

    private Categories() {}

    static String of(String owner) {
        for (Map.Entry<String, String> e : PREFIXES.entrySet()) {
            if (owner.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return OTHER;
    }
}
