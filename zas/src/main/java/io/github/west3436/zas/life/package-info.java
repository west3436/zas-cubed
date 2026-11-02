/**
 * Life support: breathing (LIFE-001 to LIFE-009, LIFE-040 to LIFE-042) and the breathability
 * verdict behind the API's helper (API-014, SPACE-002).
 *
 * <p>{@link io.github.west3436.zas.life.BreathingRules} makes the verdict; {@link
 * io.github.west3436.zas.life.Breathers} runs each breathing entity's breath check and air meter
 * through NeoForge's breathing hook; {@link io.github.west3436.zas.life.HazardProfiles} holds the
 * gases' data-driven hazard profiles; {@link io.github.west3436.zas.life.Respiration} turns
 * breathers' O2 into CO2 inside zones. Environmental hazards and protection come with #35 and
 * #36.
 */
package io.github.west3436.zas.life;
