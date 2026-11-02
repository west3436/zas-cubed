package io.github.west3436.zas.atmos.controller;

import net.minecraft.util.StringRepresentable;

/** What an area air controller's panel shows: no power, or its danger level (CONT-040). */
public enum ControllerStatus implements StringRepresentable {
    UNPOWERED("unpowered", 0),
    SAFE("safe", 0),
    DANGER1("danger1", 7),
    DANGER2("danger2", 15);

    private final String name;
    private final int signal;

    ControllerStatus(String name, int signal) {
        this.name = name;
        this.signal = signal;
    }

    /** The panel of a powered controller at a danger level. */
    public static ControllerStatus of(int danger) {
        return switch (danger) {
            case 0 -> SAFE;
            case 1 -> DANGER1;
            default -> DANGER2;
        };
    }

    /** The redstone signal the controller emits (CONT-048): 7 at danger 1, 15 at danger 2. */
    public int signal() {
        return signal;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
