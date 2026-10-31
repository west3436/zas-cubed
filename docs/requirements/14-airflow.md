# 14 · Airflow (`FLOW`)

**Status:** Accepted 2026-10-30 (full form 2026-10-31). Requirements
with more than one verifiable claim have been split; new IDs follow the rule in
the [README](README.md#writing-rules).

**Scope.** Forces on entities and items caused by pressure differences, and
other decompression effects. Background:
[zas-core §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99).
Visual and sound effects are CLIENT-003.

## Forces

#### FLOW-001 · Trigger
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-05, S-02

When an active edge's pressure difference is at or above the lowest class
threshold (FLOW-011), airflow shall act on entities within a configurable
range (default 9 blocks, as in ZAS) of that edge's openings, in both zones.

- **Rationale:** vg: ZAS starts airflow at the lightest threshold, with a
  falloff of about 9 tiles ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** A GameTest checks items within 9 blocks of a breach move and
  items beyond don't.

#### FLOW-002 · Force model
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: [zas-core §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)

Each server tick, affected entities shall be pushed toward the lower-pressure
side, using the latest ΔP. The push scales with ΔP, falls linearly to zero at
the range limit, and is capped.

- **Rationale:** vg: ZAS pushes toward lower pressure with speed ∝ ΔP and a
  distance falloff ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99));
  continuous pushes replace tile stepping.
- **Verify:** A GameTest checks an item accelerates toward a breach, faster at
  higher ΔP, and never beyond the cap.

#### FLOW-010 · Initial force tuning
`Must` · `R1` · `Accepted` · Verify: Inspect, Manual

The default push shall reach 1 block/tick at 101 kPa next to an opening,
falling linearly to 0 at 9 blocks. Values are tuned in playtesting.

- **Rationale:** Inferred: a starting point for playtesting; 1 block/tick next
  to a breach to vacuum is dramatic but survivable.
- **Verify:** Inspect the defaults; Manual playtest tunes them.

#### FLOW-003 · Entity classes
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-03

The minimum ΔP to move an entity shall depend on its class, assigned by
entity tags.

- **Rationale:** vg: ZAS thresholds by weight class ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** A GameTest checks a tagged entity moves at its class threshold.

#### FLOW-011 · Default class thresholds
`Must` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: [zas-core §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)

| Class | Members | Threshold |
|---|---|---|
| Light | Items, XP orbs, projectiles | 20 kPa |
| Mobs | Mobs and players | 65 kPa |
| Heavy | Minecarts, boats, armor stands | 85 kPa |

- **Rationale:** vg: ZAS's 20, 65 and 85 kPa thresholds ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** Inspect the shipped tags; a GameTest checks each class just
  below and above its threshold.

#### FLOW-004 · Anchoring
`Must` · `R1` · `Accepted` · Verify: GameTest

Airflow shall not move leashed mobs, riding entities (they move with their
vehicle), or players sneaking on solid ground.

- **Rationale:** vg: ZAS spares magbooted and grabbed mobs ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99));
  leashing, riding and sneaking are the Minecraft equivalents.
- **Verify:** A GameTest checks a leashed mob, a rider and a sneaking player
  don't move.

#### FLOW-012 · Airflow resistance
`Must` · `R1` · `Accepted` · Verify: GameTest · Trace: D-04, API-022

Worn airflow resistance r, from 0 to 1 (API-022), shall scale the push by
(1 − r). Magnetic boots give r = 1 while on (CONT-033).

- **Rationale:** Decision: D-04 (resistance through the API); magnetic boots
  come from vg.
- **Verify:** A GameTest checks resistance 0.5 halves the push and magnetic
  boots stop it.

#### FLOW-005 · Impact damage
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: LIFE-030

An entity flung into a block shall take airflow-impact damage using vanilla's
fly-into-wall formula (speed lost × 10 − 3). Entity-to-entity impacts are not
in R1.

- **Rationale:** vg: ZAS impacts deal damage with speed ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99));
  vanilla's fly-into-wall formula fits Minecraft.
- **Verify:** A GameTest flings a mob into a wall and checks the damage
  against the formula.

## Cost

#### FLOW-006 · Cost scales with entities in range
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

Airflow cost shall scale with the number of entities within range of active
openings, not with zone size.

- **Rationale:** Inferred: ZAS visits every movable in each zone ([zas-core
  §9](../research/zas-core.md#9-quirks-dont-replicate-blindly), quirk 9),
  which doesn't scale.
- **Verify:** Bench checks cost stays flat as zone size grows with the same
  entities in range.

#### FLOW-013 · Per-tick airflow cap
`Must` · `R1` · `Accepted` · Verify: Bench · Trace: NFR-001

The number of entities airflow processes per server tick shall be capped
(configurable).

- **Rationale:** Inferred: vg itself calls airflow a server killer ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)); a
  cap bounds the worst case (NFR-001).
- **Verify:** Bench checks the per-tick count never exceeds the cap.

#### FLOW-014 · Airflow switches
`Must` · `R1` · `Accepted` · Verify: GameTest

Airflow shall be switchable off globally or per dimension.

- **Rationale:** vg: ZAS lets airflow be switched off ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** GameTests check the global and per-dimension switches.

## Blocks

#### FLOW-007 · Pressure damage to blocks
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-28, D-14

With the Hardcore preset, blocks in the `zas:pressure_fragile` tag (by default
glass and glass panes) shall break when the pressure difference across them
exceeds the tag's threshold.

- **Rationale:** Decision: D-28 and D-14.
- **Verify:** A GameTest under Hardcore checks glass breaks above the
  threshold.

#### FLOW-015 · Default fragility threshold
`Should` · `R1` · `Accepted` · Verify: Inspect, GameTest · Trace: D-28

The default threshold shall be 150 kPa, so windows hold 1 atm against vacuum
and blow out on overpressure.

- **Rationale:** Decision: D-28.
- **Verify:** Inspect the default; a GameTest checks a window holds 101 kPa
  against vacuum and breaks at 160 kPa.

#### FLOW-016 · Block damage off in Normal
`Should` · `R1` · `Accepted` · Verify: GameTest · Trace: D-14, D-28

With the Normal preset, block pressure damage shall be off. Either preset can
override it.

- **Rationale:** Decision: D-14 and D-28.
- **Verify:** A GameTest checks glass holds under Normal and that the override
  turns damage on.

## Players

#### FLOW-009 · One-off impulses reach players
`Must` · `R1` · `Accepted` · Verify: Manual, GameTest · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

Players control their own movement on the client, so one-off decompression
impulses shall be sent to the player's client and applied reliably.

- **Rationale:** Inferred: players move on the client, so server-set motion
  must be sent explicitly ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** A GameTest checks the impulse packet is sent; Manual checks the
  player is pushed.

#### FLOW-017 · Continuous airflow sync
`Must` · `R1` · `Accepted` · Verify: Manual · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

Continuous airflow shall be applied the same way on client and server, using
synced flow data, so there is no rubber-banding.

- **Rationale:** Inferred: continuous server pushes on players rubber-band, so
  the client applies the same flow, as with water currents ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** Manual playtest near a breach checks there is no rubber-banding.

#### FLOW-008 · Feedback
`Should` · `R1` · `Rejected` — duplicate of CLIENT-003, 2026-10-30.
