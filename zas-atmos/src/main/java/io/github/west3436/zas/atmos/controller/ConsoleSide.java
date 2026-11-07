package io.github.west3436.zas.atmos.controller;

import net.minecraft.util.StringRepresentable;

/** Which column of the central atmospherics controller's console a block is, as seen from its front. */
public enum ConsoleSide implements StringRepresentable {
    LEFT("left"),
    RIGHT("right");

    private final String name;

    ConsoleSide(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
