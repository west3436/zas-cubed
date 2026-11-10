/**
 * Ambient atmospheres from code (ENV-004): a mod supplies a dimension's open air, such as a planet's
 * thin air or a temperature that follows the time of day, with an {@link
 * io.github.west3436.zas.api.environment.AmbientProvider} registered through {@link
 * io.github.west3436.zas.api.environment.AmbientProviders}.
 *
 * <p>A mod that only reads another mod's description of its dimensions, such as a space mod's list
 * of planets, registers an {@link io.github.west3436.zas.api.environment.AmbientSeed} instead
 * (SPACE-020): it applies only where no datapack and no provider defines the ambient.
 */
package io.github.west3436.zas.api.environment;
