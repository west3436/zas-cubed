package io.github.west3436.zas.atmos.label;

import org.jetbrains.annotations.Nullable;

/**
 * The window of a device that takes a label. Its title bar is where a player types one, and the
 * server sets it on the device this names ({@link LabelPayload}).
 */
public interface LabelledMenu {
    /** The device the window is of, on the server; null on the client. */
    @Nullable
    Labelled labelled();
}
