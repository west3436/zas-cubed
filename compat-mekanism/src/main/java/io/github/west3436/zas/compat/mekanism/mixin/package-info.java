/**
 * The adapter's hooks into Mekanism's internals: the only package of the adapter that may name a
 * class of Mekanism's outside {@code mekanism.api} (D-11, COMPAT-003).
 *
 * <p><b>Adding a mixin.</b> Put the class here and name it in the {@code mixins} list of {@code
 * zas.mekanism.mixins.json}. The config applies only when Mekanism is installed, and {@link
 * io.github.west3436.zas.compat.mekanism.mixin.MekanismMixinPlugin} applies its mixins only to a
 * supported version. Mekanism's main and generators jars are on the compile classpath for the
 * targets. A mixin is injection only (NFR-021), says in its Javadoc why no public hook covers the
 * need, and has a GameTest of its own that fails if the hook didn't apply (MEK-003): no injector
 * in this config is required to match, so that a Mekanism build that moved a target still starts,
 * and the test is what notices.
 */
package io.github.west3436.zas.compat.mekanism.mixin;
