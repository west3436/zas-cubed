/**
 * What a mod's client code asks of ZAS³ for sound: how much of a sound a listener in thin air hears,
 * and looping sounds that ZAS³ plays, fades and stops (API-060, API-061). Nothing here refers to a
 * client-only class, so a dedicated server may load it; there, as without ZAS³, loops play nothing
 * and every listener hears normally.
 */
package io.github.west3436.zas.api.client;
