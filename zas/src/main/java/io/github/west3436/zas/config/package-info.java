/**
 * ZAS³'s NeoForge config in OPS-001's three tiers: the per-world server config, the common config
 * and the client config.
 *
 * <p>The atmosphere preset (D-14) is SS13 Framework's world-wide Normal/Hardcore switch. The options
 * it sets are in {@link io.github.west3436.zas.config.PresetOptions}, each registered with the
 * framework with a default under each preset and an optional override (OPS-007, OPS-014); {@link
 * io.github.west3436.zas.config.PresetMigration} carries a 1.2.0 world's preset over; zone
 * triggers are viewed through {@link io.github.west3436.zas.config.ZoneTrigger}.
 */
package io.github.west3436.zas.config;
