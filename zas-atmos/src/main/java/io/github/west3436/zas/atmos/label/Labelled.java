package io.github.west3436.zas.atmos.label;

import io.github.west3436.zas.atmos.device.AtmosDevice;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A device a player can label: one a controller links or lists, or a controller itself. These are the
 * vent, the scrubber, both pumps, the filter, the mixer, the electrolyzer, the igniter, the fire
 * alarm, the recharger, the firelock on its own and on a door, the airlock, the air sensor, the
 * airlock controller, the area air controller and the central atmospherics controller.
 *
 * <p>Only an {@link AtmosDevice} implements it, and that class keeps the label, saves it and sends it
 * to clients, so a device takes labels by naming this interface and nothing more. A label is plain
 * text that {@link Labels#clean} has been through, at most {@link Labels#MAX_LENGTH} characters, and
 * the empty string is no label.
 *
 * <p>It is the game's own {@link Nameable}, so what reads a block's custom name reads the label:
 * Jade shows it as the block's name.
 */
public interface Labelled extends Nameable {
    /** The label, or the empty string if it has none. */
    String label();

    /**
     * Sets the label to what {@link Labels#clean} makes of some text, or clears it if that is nothing.
     * Server side. What allows a player to set one is the caller's to check.
     *
     * @param text what a player typed; null clears it
     * @return whether the label changed
     */
    boolean setLabel(@Nullable String text);

    /** What kind of device it is: its block's name. */
    default Component kind() {
        return ((BlockEntity) this).getBlockState().getBlock().getName();
    }

    /** Its label, or its kind without one. */
    @Override
    default Component getName() {
        return label().isEmpty() ? kind() : Component.literal(label());
    }

    @Override
    default @Nullable Component getCustomName() {
        return label().isEmpty() ? null : Component.literal(label());
    }

    /** What its window's title bar shows, which the window reads its label back from ({@link Labels#title}). */
    @Override
    default Component getDisplayName() {
        return Labels.title(label(), kind());
    }
}
