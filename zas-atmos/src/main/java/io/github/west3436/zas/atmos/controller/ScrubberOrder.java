package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.gas.GasTag;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * What an area air controller tells its scrubbers (CONT-041, CONT-079): off, scrub some gases, or
 * panic, and who sent it.
 *
 * <p>The modes' orders ({@link ControllerMode#scrubber}) have no sender; a controller adds its own
 * position with {@link #from} before it gives one, so a scrubber knows its controller from the first
 * order, for its GUI (CONT-074).
 *
 * @param mode what the scrubber does
 * @param gases the gases to scrub, for {@link Mode#SCRUB}; empty otherwise
 * @param tags the gas tags to scrub, for {@link Mode#SCRUB}; empty otherwise
 * @param sender where the controller that gave the order is; null if it isn't known
 */
public record ScrubberOrder(Mode mode, List<ResourceLocation> gases, List<GasTag> tags, BlockPos sender) {
    /** A scrubber's modes as a controller sets them. */
    public enum Mode {
        OFF,
        SCRUB,
        PANIC
    }

    public static final ScrubberOrder OFF = new ScrubberOrder(Mode.OFF, List.of(), List.of());
    public static final ScrubberOrder PANIC = new ScrubberOrder(Mode.PANIC, List.of(), List.of());

    public ScrubberOrder {
        gases = mode == Mode.SCRUB ? List.copyOf(gases) : List.of();
        tags = mode == Mode.SCRUB ? List.copyOf(tags) : List.of();
        sender = sender == null ? null : sender.immutable();
    }

    /** An order with no sender. */
    public ScrubberOrder(Mode mode, List<ResourceLocation> gases, List<GasTag> tags) {
        this(mode, gases, tags, null);
    }

    /** Scrub these gases and tags. */
    public static ScrubberOrder scrub(List<ResourceLocation> gases, List<GasTag> tags) {
        return new ScrubberOrder(Mode.SCRUB, gases, tags);
    }

    /** The same order, sent by the controller at a position. */
    public ScrubberOrder from(BlockPos controller) {
        return new ScrubberOrder(mode, gases, tags, controller);
    }
}
