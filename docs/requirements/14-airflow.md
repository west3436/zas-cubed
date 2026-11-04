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
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-05, S-02

When an active edge's pressure difference is at or above the lowest class
threshold (FLOW-011), airflow shall act on entities within a configurable
range (default 9 blocks, as in ZAS) of that edge's openings, in both zones.

- **Rationale:** vg: ZAS starts airflow at the lightest threshold, with a
  falloff of about 9 tiles ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** A GameTest checks items within 9 blocks of a breach move and
  items beyond don't.

*2026-11-02: Implemented
([#44](https://github.com/west3436/zas-cubed/issues/44), first part). Each atmos
tick the engine exports every awake edge whose difference reaches the lowest of
the three class thresholds, with its openings (up to 256 per edge), and the
server pushes entities within `[airflow] range_blocks` of any of them, on either
side. Paused zones are left out. The `flow_001_items_within_range_move_and_beyond_dont`
GameTest checks items 4 blocks from a breach move on both sides and items 13
blocks away don't; the engine's `FlowExportTest` checks what is exported.*

#### FLOW-002 · Force model
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: [zas-core §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)

Each server tick, affected entities shall be pushed toward the lower-pressure
side, using the latest ΔP. The push scales with ΔP, falls linearly to zero at
the range limit, and is capped.

- **Rationale:** vg: ZAS pushes toward lower pressure with speed ∝ ΔP and a
  distance falloff ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99));
  continuous pushes replace tile stepping.
- **Verify:** A GameTest checks an item accelerates toward a breach, faster at
  higher ΔP, and never beyond the cap.

*2026-11-02: Implemented. Next to an opening the flow's speed is
`speed_at_1_atm` × ΔP / 101.325 kPa, capped at `[airflow] max_speed` (1.5
blocks per tick by default), and it falls linearly to 0 at the range. Each game
tick an entity's speed along the flow closes `response` (0.5) of the gap to the
flow's speed, so it accelerates up to the flow and never past it; one already
faster is left alone. On the high side the flow runs towards the low side's cell
through the opening, on the low side away from the high side's cell. Where
several openings reach an entity, the fastest flow wins. ΔP is the last atmos
tick's. The `flow_002_faster_at_higher_difference_and_capped` GameTest checks
the item speeds up, harder at 80 than 40 kPa, and stops at a lowered cap.*

#### FLOW-010 · Initial force tuning
`Must` · `R1` · `Implemented` · Verify: Inspect, Manual

The default push shall reach 1 block/tick at 101 kPa next to an opening,
falling linearly to 0 at 9 blocks. Values are tuned in playtesting.

- **Rationale:** Inferred: a starting point for playtesting; 1 block/tick next
  to a breach to vacuum is dramatic but survivable.
- **Verify:** Inspect the defaults; Manual playtest tunes them.

*2026-11-02: Implemented. `[airflow] speed_at_1_atm` is 1.0 and
`range_blocks` 9 by default ([config.md](../config.md)). The Manual tuning is
for playtesting.*

#### FLOW-003 · Entity classes
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-03

The minimum ΔP to move an entity shall depend on its class, assigned by
entity tags.

- **Rationale:** vg: ZAS thresholds by weight class ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** A GameTest checks a tagged entity moves at its class threshold.

*2026-11-02: Implemented. The entity type tags
`zas:airflow/light`, `zas:airflow/mob` and `zas:airflow/heavy`
([datapacks.md](../datapacks.md#airflow-classes)) set the class; a living entity
in none of them is a mob, and anything else in none of them isn't moved (FLOW-018).
The `flow_011_classes_move_at_their_thresholds` GameTest checks each class's
members move just above its threshold and not just below.*

#### FLOW-011 · Default class thresholds
`Must` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: [zas-core §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)

| Class | Members | Threshold |
|---|---|---|
| Light | Items, XP orbs, projectiles | 20 kPa |
| Mobs | Mobs and players | 65 kPa |
| Heavy | Minecarts, boats, armor stands | 85 kPa |

- **Rationale:** vg: ZAS's 20, 65 and 85 kPa thresholds ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** Inspect the shipped tags; a GameTest checks each class just
  below and above its threshold.

*2026-11-02: Implemented. Light holds items, XP orbs, `#minecraft:arrows`,
`#minecraft:impact_projectiles` and the other thrown things; mob holds players
(and every untagged living entity); heavy holds the minecarts, boats and armor
stands. The thresholds are `[airflow] light_threshold_kpa`, `mob_threshold_kpa`
and `heavy_threshold_kpa`.*

#### FLOW-018 · Entities airflow never moves
`Should` · `R1` · `Implemented` · Verify: Inspect, GameTest · Trace: FLOW-003

Entities in the `zas:airflow/immune` tag, non-living entities in no airflow
class tag, players in creative mode and spectators shall not be moved by
airflow. The default tag holds the ender dragon.

- **Rationale:** Some entities shouldn't be thrown about: a boss that would
  leave its arena, and a player building or watching in creative or spectator
  mode, who would be dragged away from what they're working on.
- **Verify:** Inspect the shipped tag; a GameTest checks a creative player and
  a spectator beside a breach aren't moved while a survival player is.
- **Changed 2026-11-02 (the owner, reviewing the round's combined test):**
  creative players aren't moved by airflow, and spectators are exempt too. The
  requirement was Draft; the owner's change accepts it.

*2026-11-02: Implemented. `AirflowClass` gives the tagged and the untagged
non-living entities the immune class, which no difference moves; the
`flow_011_classes_move_at_their_thresholds` GameTest checks the ender dragon is
immune. `Airflow.exempt` spares creative players and spectators: the server
sends them neither the openings nor the impulse, and the client's push leaves
them alone. The `flow_018_creative_players_and_spectators_aren_t_moved`
GameTest checks both, beside a survival player who is moved.*

#### FLOW-004 · Anchoring
`Must` · `R1` · `Implemented` · Verify: GameTest

Airflow shall not move leashed mobs, riding entities (they move with their
vehicle), or players sneaking on solid ground.

- **Rationale:** vg: ZAS spares magbooted and grabbed mobs ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99));
  leashing, riding and sneaking are the Minecraft equivalents.
- **Verify:** A GameTest checks a leashed mob, a rider and a sneaking player
  don't move.

*2026-11-02: Implemented. Spectators aren't moved either. The
`flow_004_anchored_entities_stay_put` GameTest checks a leashed pig, a pig
riding a minecart and a player sneaking on the ground stay put, while a free
pig, a standing player and a player sneaking in the air move.*

#### FLOW-012 · Airflow resistance
`Must` · `R1` · `Implemented` · Verify: GameTest · Trace: D-04, API-022

Worn airflow resistance r, from 0 to 1 (API-022), shall scale the push by
(1 − r). Magnetic boots give r = 1 while on (CONT-033).

- **Rationale:** Decision: D-04 (resistance through the API); magnetic boots
  come from vg.
- **Verify:** A GameTest checks resistance 0.5 halves the push and magnetic
  boots stop it.

*2026-11-02: Implemented for worn resistance. r is the highest of an entity's
worn pieces' and its own (`WornProtection.airflowResistance`, API-022), and it
scales the flow's speed where the entity is by (1 − r), so the push from rest
and the speed it is carried to. The server sends a player its r with the
openings, and the client's push and the onset impulse use it. The
`flow_012_resistance_scales_the_push` GameTest checks r = 0.5 halves the push
of a mob and of a player, and the impulse, and r = 1 from protection data stops
both. Magnetic boots don't exist yet; they give r = 1 when CONT-033 lands.*

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
`Must` · `R1` · `Implemented` · Verify: Bench · Trace: [zas-core §9](../research/zas-core.md#9-quirks-dont-replicate-blindly)

Airflow cost shall scale with the number of entities within range of active
openings, not with zone size.

- **Rationale:** Inferred: ZAS visits every movable in each zone ([zas-core
  §9](../research/zas-core.md#9-quirks-dont-replicate-blindly), quirk 9),
  which doesn't scale.
- **Verify:** Bench checks cost stays flat as zone size grows with the same
  entities in range.

*2026-11-02: Implemented. The server looks for entities only in
boxes around the 16-block sections that hold openings, and an edge's openings
are searched once, then kept until its connections or its zones' cells change.
The `airflow` bench ([benchmarks.md](../research/benchmarks.md#airflow-flow-006-flow-013))
pushes the same entities near one hole in zones of 1,000, 8,000 and 50,000
cells: about 0.05 ms a game tick for 256 entities whatever the zone's size. The
one-off search when the breach opens does scale with the zone (about 6 ms on
the worker for 50,000 cells).*

#### FLOW-013 · Per-tick airflow cap
`Must` · `R1` · `Implemented` · Verify: Bench · Trace: NFR-001

The number of entities airflow processes per server tick shall be capped
(configurable).

- **Rationale:** Inferred: vg itself calls airflow a server killer ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)); a
  cap bounds the worst case (NFR-001).
- **Verify:** Bench checks the per-tick count never exceeds the cap.

*2026-11-02: Implemented. `[airflow] max_entities_per_tick` (256)
caps the entities looked at per game tick in each dimension; beyond it they
take turns. The `flow_013_the_cap_bounds_entities_per_tick` GameTest checks
the cap and the turns, and the bench reports the push's cost at the cap.*

#### FLOW-014 · Airflow switches
`Must` · `R1` · `Implemented` · Verify: GameTest

Airflow shall be switchable off globally or per dimension.

- **Rationale:** vg: ZAS lets airflow be switched off ([zas-core
  §5](../research/zas-core.md#5-airflow-airflowdm-connectiongroupdm74-99)).
- **Verify:** GameTests check the global and per-dimension switches.

*2026-11-02: Implemented. `[airflow] enabled` and
`[airflow] disabled_dimensions`; off, the engine exports nothing either. The
`flow_014_switches_turn_airflow_off` GameTest checks both switches and that the
item moves again once they're back on.*

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
`Must` · `R1` · `Implemented` · Verify: Manual, GameTest · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

Players control their own movement on the client, so one-off decompression
impulses shall be sent to the player's client and applied reliably.

- **Rationale:** Inferred: players move on the client, so server-set motion
  must be sent explicitly ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** A GameTest checks the impulse packet is sent; Manual checks the
  player is pushed.

*2026-11-02: Implemented. When the flow first reaches a player
at an atmos tick, the server sends the client the flow's velocity there, and
the client raises the player's speed along it to that at once. The
`flow_009_player_gets_the_impulse_and_the_client_pushes` GameTest checks the
impulse is sent once and moves the player; the Manual check is for the
playtest.*

#### FLOW-017 · Continuous airflow sync
`Must` · `R1` · `Implemented` · Verify: Manual · Trace: [neoforge §9](../research/neoforge-1.21.1.md#9-entities)

Continuous airflow shall be applied the same way on client and server, using
synced flow data, so there is no rubber-banding.

- **Rationale:** Inferred: continuous server pushes on players rubber-band, so
  the client applies the same flow, as with water currents ([neoforge
  §9](../research/neoforge-1.21.1.md#9-entities)).
- **Verify:** Manual playtest near a breach checks there is no rubber-banding.

*2026-11-02: Implemented. The server never pushes players. Each atmos
tick it sends each player near airflow the openings within the range plus 8
blocks on edges beside the player's zone, and the client pushes its own player
every game tick with the same code the server uses for other entities. The
openings lapse if no update comes for two atmos periods plus a second. The
GameTest above runs a player through the client's path; the rubber-banding
check is Manual.*

#### FLOW-019 · Crawling through low openings
`Must` · `R1` · `Implemented` · Verify: GameTest, Manual · Trace: FLOW-017

When airflow carries a player into an opening too low to stand in, such as a
hole one block high, the player shall take the swimming (crawling) pose, and
the flow shall keep carrying them through it. They stand again once there is
room.

- **Rationale:** A player standing can't fit through a one-block hole, so
  without it the flow pins them against the wall beside the hole instead of
  carrying them out.
- **Verify:** A GameTest checks a player carried towards a one-block hole is
  put in the swimming pose on both the server's and the client's path, and let
  up in the open; Manual checks a player is carried through such a hole.
- **Changed 2026-11-02 (the owner, reviewing the round's combined test):** new.
  The owner, on a breach through a one-block hole: "It was only a 1-block hole
  so I got stuck. The player should switch to the 'swimming' model and be
  forced out the hole."

*2026-11-02: Built. Each game tick the server, and the client for its own
player, takes the flow's direction at the player. If within reach ahead along
it there is a place where the player can't stand at its own height, but where,
level with its feet or up to a block higher, the swimming box fits and the
standing box still doesn't, the player's pose is forced to swimming. That is a
hole level with the floor, or one in the wall a block up, such as a window,
which the flow, aimed at the hole, lifts the player into. A doorway two blocks
high or a step up doesn't count. The pose stays forced while the player can't
stand where it is, and is let up after. The server looks 1.5 blocks ahead and
the client 1, so the server's box is already the swimming one when the client
moves into the hole; otherwise the server would refuse the move and pull the
player back. A pose something else forces is left alone, and creative players
and spectators are exempt (FLOW-018). The
`flow_019_a_player_carried_into_a_low_hole_crawls` GameTest checks both paths
for a hole level with the floor, and
`flow_019_a_player_crawls_into_a_hole_above_the_floor` checks a hole a block up,
and a doorway, which doesn't trigger it; being carried through is the Manual
check.*

*2026-11-03: Implemented. The owner passed the round's combined test, which
retested a player being carried through a one-block hole.*

#### FLOW-008 · Feedback
`Should` · `R1` · `Rejected` — duplicate of CLIENT-003, 2026-10-30.
