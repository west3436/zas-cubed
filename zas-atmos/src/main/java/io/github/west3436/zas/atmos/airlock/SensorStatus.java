package io.github.west3436.zas.atmos.airlock;

import net.minecraft.util.StringRepresentable;

/** What an air sensor's panel shows (CONT-142). */
public enum SensorStatus implements StringRepresentable {
    STANDBY("standby"),
    CYCLING("cycling"),
    ALERT("alert");

    /** The Human preset's pressure min2, kPa (CONT-047): below it, a sensor alerts. */
    public static final double ALERT_BELOW_KPA = 81;

    /** The Human preset's pressure max2, kPa (CONT-047): above it, a sensor alerts. */
    public static final double ALERT_ABOVE_KPA = 122;

    private final String name;

    SensorStatus(String name) {
        this.name = name;
    }

    /**
     * What a sensor shows: cycling while a controller it's linked to cycles, otherwise alert while
     * the pressure it reads is below 81 kPa or above 122 kPa, and otherwise standby (CONT-142).
     *
     * @param cycling whether a controller it's linked to is cycling
     * @param kpa the pressure it reads
     */
    public static SensorStatus of(boolean cycling, double kpa) {
        if (cycling) {
            return CYCLING;
        }
        return kpa < ALERT_BELOW_KPA || kpa > ALERT_ABOVE_KPA ? ALERT : STANDBY;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
