package io.github.west3436.zas.atmos.airlock;

import net.minecraft.util.StringRepresentable;

/** The firelock fitted to an airlock (CONT-137): none, or one open, closed or locked down. */
public enum AirlockFirelock implements StringRepresentable {
    NONE("none"),
    OPEN("open"),
    CLOSED("closed"),
    LOCKED("locked");

    private final String name;

    AirlockFirelock(String name) {
        this.name = name;
    }

    /** Whether a firelock is fitted. */
    public boolean fitted() {
        return this != NONE;
    }

    /** Whether a fitted firelock is shut, locked down or not, so the doorway is sealed. */
    public boolean shut() {
        return this == CLOSED || this == LOCKED;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
