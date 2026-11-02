package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.gas.StandardAir;

/**
 * An area air controller's modes (CONT-041): what its scrubbers and vents do in each, and when
 * Cycle and Fill switch on by themselves.
 */
public enum ControllerMode {
    /** Scrub the preset's gases; vents blow to the middle of the safe band. */
    SCRUBBING("scrubbing"),
    /** Scrubbers panic; vents blow to target. */
    REPLACEMENT("replacement"),
    /** Scrubbers panic; vents off. */
    PANIC("panic"),
    /** Scrubbers panic until the zone is below 5 % of 1 atm, then Fill; vents off. */
    CYCLE("cycle"),
    /** Scrubbers off; vents blow to target, then Scrubbing at 95 % of target. */
    FILL("fill"),
    /** Everything off. */
    OFF("off");

    /** Below this, Cycle switches to Fill: 5 % of 1 atm, in kPa. */
    public static final double CYCLE_DONE_KPA = 0.05 * StandardAir.PRESSURE;

    /** At this fraction of the target, Fill switches to Scrubbing. */
    public static final double FILL_DONE_FRACTION = 0.95;

    private final String key;

    ControllerMode(String key) {
        this.key = key;
    }

    /** The mode's name in saves and lang keys. */
    public String key() {
        return key;
    }

    /** The mode with a key, or {@link #SCRUBBING} for an unknown one. */
    public static ControllerMode byKey(String key) {
        for (ControllerMode m : values()) {
            if (m.key.equals(key)) {
                return m;
            }
        }
        return SCRUBBING;
    }

    /**
     * What the vents do in this mode. The target of Replacement and Fill is the preset's target,
     * the same pressure Scrubbing blows to (architecture §11.6).
     */
    public VentOrder vent(ControllerPreset preset) {
        return switch (this) {
            case SCRUBBING, REPLACEMENT, FILL -> VentOrder.blow(preset.targetPressure());
            case PANIC, CYCLE, OFF -> VentOrder.OFF;
        };
    }

    /** What the scrubbers do in this mode. */
    public ScrubberOrder scrubber(ControllerPreset preset) {
        return switch (this) {
            case SCRUBBING -> ScrubberOrder.scrub(ControllerPreset.SCRUB_GASES, ControllerPreset.SCRUB_TAGS);
            case REPLACEMENT, PANIC, CYCLE -> ScrubberOrder.PANIC;
            case FILL, OFF -> ScrubberOrder.OFF;
        };
    }

    /**
     * The mode after a reading of the zone's pressure: Cycle becomes Fill below {@link
     * #CYCLE_DONE_KPA}, and Fill becomes Scrubbing at {@link #FILL_DONE_FRACTION} of the target.
     * Every other mode stays.
     */
    public ControllerMode after(double pressureKpa, ControllerPreset preset) {
        if (this == CYCLE && pressureKpa < CYCLE_DONE_KPA) {
            return FILL;
        }
        if (this == FILL && pressureKpa >= FILL_DONE_FRACTION * preset.targetPressure()) {
            return SCRUBBING;
        }
        return this;
    }
}
