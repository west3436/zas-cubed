/**
 * The KubeJS adapter (COMPAT-050 to COMPAT-053): ZAS³'s events and calls in a pack's server
 * scripts, and hazards and ambients registered from them. What scripts get is the public API, so
 * the adapter works the same with the content off.
 *
 * <p>KubeJS finds {@link io.github.west3436.zas.compat.kubejs.ZasKubeJsPlugin} through the {@code
 * kubejs.plugins.txt} that ships beside these classes, and the plugin registers only with a KubeJS
 * the adapter supports ({@link io.github.west3436.zas.compat.kubejs.KubeJsGate}, COMPAT-002).
 * Without KubeJS nothing reads that file, and none of these classes is loaded.
 */
package io.github.west3436.zas.compat.kubejs;
