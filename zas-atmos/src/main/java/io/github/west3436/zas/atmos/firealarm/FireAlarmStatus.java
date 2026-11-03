package io.github.west3436.zas.atmos.firealarm;

import net.minecraft.util.StringRepresentable;

/** What a fire alarm shows (CONT-044): no power, watching, or a fire. */
public enum FireAlarmStatus implements StringRepresentable {
    UNPOWERED("unpowered"),
    NORMAL("normal"),
    ALARM("alarm");

    private final String name;

    FireAlarmStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
