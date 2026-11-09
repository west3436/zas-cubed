/**
 * The Ad Astra adapter (SPACE): with Ad Astra installed, ZAS³ is the only source of atmospheric
 * harm, Ad Astra's own checks are answered from ZAS³, and its planets, suits and mob tags feed
 * ZAS³ (D-51).
 *
 * <p>It is a reference integration: it uses the public API ({@code io.github.west3436.zas.api}) and
 * Ad Astra's API package ({@code earth.terrarium.adastra.api}) and nothing else from either, with
 * no mixin, so another mod can copy it (D-66, SPACE-016). The project's dependencies hold it to
 * that: nothing else of ZAS³ is on its compile classpath.
 *
 * <p>These classes ship in the {@code zas} jar. {@code zas} loads them only when Ad Astra is
 * installed in a version the adapter supports (SPACE-003), through {@link
 * io.github.west3436.zas.compat.adastra.AdAstraCompat#init}.
 */
package io.github.west3436.zas.compat.adastra;
