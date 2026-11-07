package io.github.west3436.zas.atmos.controller;

import net.minecraft.util.StringRepresentable;

/**
 * What the central atmospherics controller's screen shows, for its model (CONT-115): no power, or
 * the worst danger among the area air controllers it has claimed, by their last readings.
 */
public enum ConsoleScreen implements StringRepresentable {
    NORMAL("normal"),
    DANGER1("danger1"),
    DANGER2("danger2"),
    UNPOWERED("unpowered");

    private final String name;

    ConsoleScreen(String name) {
        this.name = name;
    }

    /** The screen for a controller status. */
    public static ConsoleScreen of(ControllerStatus status) {
        return switch (status) {
            case UNPOWERED -> UNPOWERED;
            case SAFE -> NORMAL;
            case DANGER1 -> DANGER1;
            case DANGER2 -> DANGER2;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
