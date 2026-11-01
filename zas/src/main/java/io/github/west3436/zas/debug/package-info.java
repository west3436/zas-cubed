/**
 * The operator debug overlay (CLIENT-005, CLIENT-011 to CLIENT-013): the {@code /zas debug}
 * command, the server side that collects what the atmos workers export and sends it to
 * operators who switched it on ({@link io.github.west3436.zas.debug.DebugOverlay}), and the
 * payload. The client's renderer is in {@code io.github.west3436.zas.client}, which only the
 * physical client loads.
 */
package io.github.west3436.zas.debug;
