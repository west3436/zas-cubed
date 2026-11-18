package io.github.west3436.zas.atmos.device;

/** A device with a redstone setting (CONT-170): see {@link RedstoneControl} for how a device takes one. */
public interface RedstoneControlled {
    /** Its redstone setting. */
    RedstoneControl redstone();
}
