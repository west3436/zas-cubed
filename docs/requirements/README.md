# zas-cubed requirements

**Status:** baselined as `req-r1-baseline`, 2026-10-31. Every R0/R1 Must is
*Accepted* and in full form. New requirements start as *Draft* until reviewed
(see [Review workflow](#review-workflow)).

This folder holds the requirements for ZAS³ (repository `zas-cubed`, mod ID
`zas`). The project is both a ZAS-style atmosphere framework that aims to be
the shared atmosphere API for NeoForge 1.21.1, and an SS13-style atmospherics
content mod, shipped as a separate jar.
Background research is in [`../research/`](../research/) and the delivery plan
is in [`../plan.md`](../plan.md).

## Layout

The file numbers follow the architecture: 1x is the simulation engine, 2x is
how it touches entities, 3x is the API, 4x is content, 5x is compat, 6x–8x are
platform services, and 9x is cross-cutting qualities. Gaps leave room to grow.

| File | ID prefix | Covers |
|---|---|---|
| [00-overview.md](00-overview.md) | `D-` `A-` `S-` `Q-` | Vision, scope, release definitions, decisions, assumptions, glossary and units, reference scenarios, open questions |
| [10-simulation-core.md](10-simulation-core.md) | `CORE` | Gas mixture model, gas registry, zones, connections and edges, ambient reservoirs, scheduler |
| [11-world-geometry.md](11-world-geometry.md) | `GEO` | Cells, gas-tightness of blocks, zone-boundary blocks, block-change tracking, enclosure detection, chunk lifecycle |
| [12-heat.md](12-heat.md) | `HEAT` | Zone temperature, conduction, heat sources and sinks, radiative loss |
| [13-reactions-fire.md](13-reactions-fire.md) | `FIRE` | Data-driven reactions, gas combustion, fire spread, vanilla fire and furnaces |
| [14-airflow.md](14-airflow.md) | `FLOW` | Pressure-driven forces on entities and items, decompression effects |
| [15-environments.md](15-environments.md) | `ENV` | Per-dimension ambient atmospheres, space semantics, ambient providers |
| [20-life-support.md](20-life-support.md) | `LIFE` | Breathing, hazards and damage, protection, life-support gear |
| [30-api.md](30-api.md) | `API` | Public API surface, registries, events, capabilities, threading contract, stability policy |
| [40-content.md](40-content.md) | `CONT` | Pipes and networks, devices, portable atmos, tanks and suits, alarms, firelocks, tools |
| [50-compat-mekanism.md](50-compat-mekanism.md) | `MEK` | ⏸ Deferred after R1 (D-11): chemical bridge, venting into rooms, breathing gear, heat coupling |
| [51-compat-space.md](51-compat-space.md) | `SPACE` | Compat with other space mods' oxygen, sealing and temperature checks. R1 is Ad Astra only (D-29). |
| [60-client.md](60-client.md) | `CLIENT` | Gas visuals, particles, sound, HUD, debug rendering |
| [70-persistence-sync.md](70-persistence-sync.md) | `DATA` | Save format and migration, client sync |
| [80-config-tooling.md](80-config-tooling.md) | `OPS` | Config, datapacks, commands, profiling, admin tools |
| [90-non-functional.md](90-non-functional.md) | `NFR` | Performance, scalability, reliability, compatibility, quality, documentation, licensing |

## Requirement entry format

Each requirement is a level-4 heading, so it gets a stable link anchor. Below
it go a tags line and a one-sentence statement. The tags line carries
priority, release, status, verification method, trace and (optionally)
dependencies:

```markdown
#### CORE-023 · Zone merge
`Must` · `R0` · `Draft` · Verify: Unit, GameTest · Trace: [zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)

Two zones joined by at least one direct opening shall merge into one zone once
their mixtures are equivalent (CORE-006).
```

That is the **compact form**, used for Draft requirements. The **full form**
(D-60) adds two bullets under the statement, and nothing else:

```markdown
- **Rationale:** vg: ZAS merges zones once their mixtures are equivalent
  ([zas-core §3.2](../research/zas-core.md#32-edge-exchange-math)).
- **Verify:** Unit test of the merge rule, plus a GameTest that opens a wall
  between two rooms at different pressures and checks they become one zone
  with total moles conserved.
```

- **Rationale** says where the requirement comes from, starting with one of:
  - `vg:` it follows vgstation13 or ZAS behaviour, with a research link;
  - `Decision:` it follows a decision the user made, with the `D-nn`;
  - `Inferred:` it was inferred, followed by the reason.
- **Verify** is a plan of one or two sentences naming the tests that verify
  the requirement: Unit (system) tests, GameTests (in-game), Bench, Manual or
  Inspect. Numbers come from the statement; the plan doesn't repeat them.

### Fields

| Field | Values and rules |
|---|---|
| **ID** | `PREFIX-NNN`. Never renumbered or reused; withdrawn IDs stay in the file marked *Rejected*. |
| **Statement** | One testable obligation. *shall* = mandatory, *should* = expected unless justified, *may* = permitted option. Say *what* happens, not *how*, unless the "how" is itself the obligation (API shape, licensing). |
| **Priority** | MoSCoW, relative to the target release: `Must` / `Should` / `Could` / `Won't` ("not this release, recorded on purpose"). |
| **Release** | `R0` Foundation · `R1` First playable · `R2` Post-launch · `Later`. Defined in [00-overview](00-overview.md#release-definitions). |
| **Status** | `Draft` → `Accepted` → `Implemented` → `Verified`. Side exits: `Deferred` or `Rejected`, each with a one-line reason. |
| **Rationale** | Full form only. Where the requirement comes from: `vg:`, `Decision:` or `Inferred:` (D-60). |
| **Verify** | On the tags line, one or more of: `Unit` (pure-Java engine tests), `GameTest` (in-world automated), `Bench` (performance scenario), `Manual` (playtest checklist), `Inspect` (review of code, docs or data). In full form, also a one- or two-sentence verification plan. |
| **Trace** | Upstream links: decisions, scenarios, research sections. Downstream links (tests, PRs) are added once they exist. |
| **Depends** | Optional. Other requirement IDs. |

## Writing rules

1. **One verifiable claim per requirement.** A statement with more than one
   independently verifiable claim is split. A claim's own default value, or a
   direct consequence of it, stays with it. If a split-off claim already
   exists in another requirement, reference that one instead. New IDs take the
   first free number in their section's range, then the next free number
   after the file's highest range.
2. **Testable.** Use numbers wherever they're known, otherwise mark them
   `TBD(Q-nn)` and link the open question.
3. **Units:** kPa, K, mol, L, J, W, and seconds or game ticks. 1 block =
   1 m³ = 1,000 L unless GEO says otherwise. Show temperatures in K, with °C
   in brackets where it helps.
4. **Clean-room.** Requirements may reference vgstation13 *behavior* through
   our research notes. They never quote vgstation13 code (README policy, D-01).
5. **Tests trace back.** Test names and GameTest structure names include the
   requirement ID, e.g. `core_012_zone_merge`.
6. **Changes go through PRs.** Once accepted, a requirement changes only by an
   edit with a dated note under it. Material changes return it to *Draft*.

## Review workflow

1. **Draft.** Each area file starts with candidate requirements written from the
   decisions and research. They are unreviewed suggestions, there to be cut,
   changed or added to.
2. **Review area by area,** in dependency order: `00-overview` (scope, releases,
   open questions) → `CORE` → `GEO` → `ENV` → `API` → `LIFE` → `HEAT`/`FIRE`/`FLOW`
   → `CONT` → `SPACE` → `CLIENT`/`DATA`/`OPS` → `NFR`.
3. **Accept.** Accepted requirements get the full form (rationale and verification plan). Open questions are
   closed into decisions (`D-`) in the overview.
4. **Baseline.** When every `R1 Must` is *Accepted*, tag the requirements
   baseline (`req-r1-baseline`). Architecture and implementation planning work
   against that baseline.

## Traceability

- **Upward:** every requirement cites at least one decision (`D-`), scenario
  (`S-`) or research note.
- **Downward:** tests name the requirement IDs they verify. A coverage matrix
  (`traceability.md`) will be generated from test names once code exists.
