/**
 * Adapters for other mods: the table of them and the loader that starts each one only when its
 * target is installed in a supported version (COMPAT-002, SPACE-003, MEK-001).
 *
 * <p>{@link io.github.west3436.zas.compat.Adapters} holds the table and loads it; {@link
 * io.github.west3436.zas.compat.VersionGate} is the version check alone. The adapters themselves
 * live in projects of their own, such as {@code compat-adastra}, and nothing here names their
 * classes or their targets'.
 */
package io.github.west3436.zas.compat;
