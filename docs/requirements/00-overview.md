# 00 · Overview

**Status:** Accepted 2026-10-31, baselined as `req-r1-baseline`. Decisions D-11 to D-40 were added on 2026-10-30 from the
open-questions walkthrough, D-41 and D-42 from the CORE review, D-43 and D-44 from the GEO review, and D-45 from the ENV review, D-46 from the API review, D-47 from the LIFE review, D-48 and D-49 from the HEAT/FIRE/FLOW review, D-50 from the CONT review, D-51 and D-52 from the SPACE review, D-53 and D-54 from the CLIENT/DATA/OPS review, D-55 and D-56 from the NFR review, D-57 and D-58 from the post-review gap check, D-59 from the gap-check answers, D-60 on the full form, D-61 before the baseline, D-62 and D-63 during Phase C, D-64 on pinned zones, D-65 on excluded dimensions, and D-66 on moving Ad Astra to R2.

## Vision

**ZAS³** (mod ID `zas`) brings SS13-style zone atmospherics to Minecraft.
Sealed spaces become zones holding real gas mixtures that equalize, leak,
burn, conduct heat, and push things around when they decompress. It serves two
audiences equally (D-04), and aims to become **the shared atmosphere API for
NeoForge 1.21.1** (D-12):

1. **Mod developers.** A stable, documented atmosphere provider that space,
   tech, and survival mods can query and extend instead of writing their own
   oxygen systems. Mods can use it even when ZAS³ isn't installed (D-17).
2. **Players and packs.** A full SS13-style atmospherics gameplay layer
   (pipes, pumps, vents, scrubbers, canisters, alarms, firelocks, suits) that
   works entirely standalone. It ships as a separate content mod (D-16).

## Stakeholders

| Stakeholder | Wants |
|---|---|
| Player | Readable, fair, dramatic atmospherics: breathing, breaches, fires, life support |
| Modpack author | A Normal/Hardcore preset, configurable hazards, sensible defaults per dimension, coexistence with other mods |
| Server admin | Predictable tick cost, no world corruption, admin and debug tools |
| Consumer-mod developer | Stable API, data-driven extension points, docs, Maven Central artifact, safe behaviour when ZAS³ is absent |
| Maintainers | Testable engine, clear provenance for code and assets, CI |

## Release definitions

| Release | Purpose | Content (summary) |
|---|---|---|
| **R0 Foundation** | Internal milestone that retires the architectural risks | Off-thread simulation engine, world geometry and enclosure detection, ambient environments, persistence, debug and admin tooling, standalone API module skeleton. No survival content. |
| **R1 First playable** | First public release | D-05 features (breathing and hazards, airflow, fire and reactions, heat conduction); the Normal/Hardcore preset; the life-support API; the R1 content subset, including power (scoped in [CONT](40-content.md)) |
| **R2 Post-launch** | Breadth | The rest of the SS13 device set; the reference integrations, Mekanism (D-11) and the Ad Astra adapter (D-66); Northstar Redux and Create Cosmonautics adapters; biome/altitude ambients |
| **Later** | Explicitly parked | Moving structures (Valkyrien Skies, Create contraptions), an SS13 fictional-gas addon, anything marked `Won't` |

## Decision log

Decisions are binding inputs to the requirements. To change one, add a new
decision that supersedes it.

| ID | Date | Decision | Notes |
|---|---|---|---|
| D-01 | 2026-10-30 | **License: MIT, clean-room for code.** vgstation13 code (GPLv3/AGPLv3) is a design reference only and is never copied or translated. | The asset clause is superseded by D-26 |
| D-02 | 2026-10-30 | **Content scope: framework plus full SS13-style atmospherics content** (pipes, pumps, canisters, air alarms, firelocks, suits) | R1 ships a subset; see CONT. Packaging in D-16. |
| D-03 | 2026-10-30 | **Fidelity: faithful ZAS core.** Perfectly mixed zones, edge-based sharing, ideal gas law, per-gas heat capacity, ZAS-style fire and airflow. Extension points allowed. | Venting (D-21) and displaced gas (D-41) deviate on purpose |
| D-04 | 2026-10-30 | **Audience: companion mods and third parties equally.** The API meets public-library standards from the start. | Strengthened by D-12 |
| D-05 | 2026-10-30 | **R1 simulation features:** breathing and hazards, airflow forces, fire and reactions, heat conduction | vg has no heat conduction to port ([zas-core §6](../research/zas-core.md#6-heat)) |
| ~~D-06~~ | 2026-10-30 | ~~R1 Mekanism integrations~~ | **Superseded by D-11** |
| D-07 | 2026-10-30 | **R1 space scope:** per-dimension ambients (core), life-support API, space-mod compat. **Not in R1:** biome/altitude ambients, moving structures. | Compat scope narrowed by D-29, then moved to R2 by D-66 |
| D-08 | 2026-10-30 | **Built-in gases are real-world gases only.** SS13 fictional gases go in an optional addon or datapack. | |
| D-09 | 2026-10-30 | **Platform:** Minecraft 1.21.1, NeoForge, Java 21 | Minimum NeoForge 21.1.264 (NFR-020) |
| D-10 | 2026-10-30 | **Repository:** public at `github.com/west3436/zas-cubed`. Docs live in `docs/` as Markdown. | |
| D-11 | 2026-10-30 | **Mekanism is cut from R1.** It's the first planned compat target after R1. When built, it's a free reference integration that uses only the public API, so other mods can copy it. ZAS³ is fully standalone. | Supersedes D-06. The [MEK](50-compat-mekanism.md) area is deferred. |
| D-12 | 2026-10-30 | **ZAS³ aims to become the shared atmosphere API** for NeoForge 1.21.1. Research found no such API exists. | [research](../research/space-mods-and-prior-art.md#c-shared-api-niche-and-survival-mod-targets) |
| D-13 | 2026-10-30 | **Zones form only when an area is enclosed *and* its air is being modified.** Ambient air never forms zones, natural caves included. What can trigger a new zone depends on the preset (D-14): **Normal** — deliberate changes only (devices, leaks, API calls, breaches of existing zones). **Hardcore** — also combustion (fires, lit furnaces and campfires) and planting in an enclosed area (D-33). | Once a zone exists, all processes affect it |
| D-14 | 2026-10-30 | **Global atmosphere difficulty preset: Normal (default) / Hardcore.** It sets the defaults for zone triggers (D-13), vanilla-dimension atmospheres (D-19) and block damage (D-28). Any single option can be overridden. | OPS-007 |
| D-15 | 2026-10-30 | **Identity:** display name **ZAS³**, mod ID and datapack namespace `zas`, package root `io.github.west3436.zas`, Maven group `io.github.west3436` | Q-01. The package root follows the short-ID option. |
| D-16 | 2026-10-30 | **Packaging: two mods from one repo.** The framework mod `zas` (engine, API, debug tools) and the SS13 content mod, which depends on it. | Q-02. Content mod ID in D-38. |
| D-17 | 2026-10-30 | **The API is a standalone module that can be bundled (jar-in-jar).** ZAS³ and consumer mods may both bundle it; the loader keeps one copy. Without ZAS³ installed, API calls return safe defaults: a vanilla, breathable world. | API-006 |
| D-18 | 2026-10-30 | **Distribution:** mod jars on Modrinth and CurseForge; the API on Maven Central under `io.github.west3436` | Q-14 |
| D-19 | 2026-10-30 | **Vanilla dimensions.** Normal: all breathable (the Nether warmer). Hardcore: the Nether is hot and CO₂-rich and the End has thin air, both with real hazards. | Q-03, ENV-002 |
| D-20 | 2026-10-30 | **Atmos tick defaults to 1 s.** Rates are defined per second, so the period can be tuned without changing real-time behaviour. | Q-04 |
| D-21 | 2026-10-30 | **Venting is volume-aware by default.** Large spaces drain proportionally slower. The faithful ZAS model, where drain rate ignores size, is a config option. | Q-05. A deliberate deviation from D-03. |
| D-22 | 2026-10-30 | **A partial block holds gas in proportion to its free space** (a bottom slab holds 500 L) | Q-06 |
| D-23 | 2026-10-30 | **An enclosure can be at most 65,536 cells when a zone forms** (configurable). Larger regions count as open to ambient. | Q-07 |
| D-24 | 2026-10-30 | **Unloaded chunks:** a zone freezes until all its chunks are loaded. Chunks are never force-loaded. | Q-08 |
| D-25 | 2026-10-30 | **Power:** devices need Forge Energy (FE), and ZAS³ ships its own generator. An air alarm powers every device in its zone; devices outside an alarmed zone need their own power. A new cable type can share a block with pipes, for compact builds. | Q-09. Details in D-34 to D-37. |
| D-26 | 2026-10-30 | **Art: 3D models built (with a Blender MCP workflow) from vgstation13's 2D sprites.** vg's assets are CC BY-SA 3.0 US, not NonCommercial (verified). Derived assets are licensed CC BY-SA with attribution. Goonstation-ported `goon/` assets (CC BY-NC-SA) are excluded. RE:SS3D (assets CC BY-NC-SA 4.0) and Stationeers (proprietary) are reference and inspiration only. | Q-10. Supersedes D-01's asset clause; code stays MIT and clean-room. License version in D-39. The Blender MCP workflow is superseded by D-67. |
| D-27 | 2026-10-30 | **Breathers and respiration.** Players and air-breathing mobs (a data-driven tag) breathe. Breathing uses O₂ and emits CO₂ at the realistic rate in both presets, and only inside existing zones. | Q-11 |
| D-28 | 2026-10-30 | **Pressure damage to blocks happens in Hardcore only:** glass and panes shatter above a threshold | Q-12. Default threshold 150 kPa, so windows hold 1 atm against vacuum (FLOW-015). Container explosions follow the same rule (D-57, CONT-107). |
| D-29 | 2026-10-30 | **Space-mod compat in R1 is Ad Astra only.** Northstar Redux and Create Cosmonautics come later. | Q-13. The adapter model is D-51. Moved to R2 by D-66. |
| D-30 | 2026-10-30 | **The simulation runs off the main thread from day one.** It works on snapshots, and results are applied on the server thread. | Q-15 |
| D-31 | 2026-10-30 | **Pipes and devices update on the atmos tick** | Q-16 |
| D-32 | 2026-10-30 | **An air alarm controls the devices in its zone,** plus any it is explicitly linked to with a tool | Q-17 |
| D-33 | 2026-10-30 | **R1 gas sources:** air compressor, water electrolyzer, scrubbing into canisters. **Plants turn CO₂ into O₂** inside zones; in Hardcore, planting in an enclosed area forms a zone. | Q-18 |
| D-34 | 2026-10-30 | **The air alarm is an APC-style power hub.** It takes FE from a cable or an adjacent source into an internal buffer and feeds the devices it controls (D-32) wirelessly from that buffer. When FE runs short it powers devices by priority: life support (vents, scrubbers) first, then pumps, filters and mixers, then heaters, freezers and gas production. Devices with no powered alarm need a cable. | Q-21 |
| D-35 | 2026-10-30 | **Built-in generator: a P.A.C.M.A.N.-style portable solid-fuel generator** burning coal or charcoal. It counts as a lit fuel-burning block: inside a zone it consumes O₂ and emits CO₂ and heat, and in Hardcore it triggers a zone like a furnace (D-13). | Q-21. Solar and gas-burning generators are not in R1. |
| D-36 | 2026-10-30 | **Cables share blocks with pipes through our own generic multipart block** in the content mod: one block entity holding typed parts, each with its own shape, hit-testing, model and network. R1 parts are pipe and cable; R2 pipe layers reuse it. No external multipart library (A-01). | Q-21. Spike it early in E5. |
| D-37 | 2026-10-30 | **FE scale: early-game, one cable tier.** In line with the Thermal and Mekanism early game: the generator makes about 40 FE/t, devices draw about 5–80 FE/t, and one cable tier carries about 1,000 FE/t. All values are configurable and tuned in playtesting. | Q-21 |
| D-38 | 2026-10-30 | **Content mod:** mod ID `zas_atmos`, display name **ZAS³ Atmospherics**, package root `io.github.west3436.zas.atmos`. The framework never uses the `.atmos` package (no split packages). | Q-22 |
| D-39 | 2026-10-30 | **Derived assets are licensed CC BY-SA 4.0 International.** CC BY-SA 3.0 US §4(b) allows adaptations under a later version with the same license elements. `LICENSE-ASSETS` carries the 4.0 text plus a note on the 3.0 US upstream. | Q-23 |
| D-40 | 2026-10-30 | **NFR reference hardware:** a mid-range 6-core desktop (Ryzen 5 5600 or i5-12400 class, 16 GB RAM) running a dedicated server with a 4 GB heap. CI tracks regressions against a recorded baseline, not absolute numbers. | NFR-001 |
| D-41 | 2026-10-30 | **Displaced gas is conserved.** When a block fills a cell, the cell's gas stays in its zone and the zone's pressure rises. ZAS's behaviour, deleting the gas, is a config option. | A deliberate deviation from D-03. CORE-022, CORE-029. |
| D-42 | 2026-10-30 | **Built-in gases:** O₂, N₂, CO₂, N₂O, H₂O (vapor), H₂, CO and CH₄. Any other gas comes from a compat module, addon or datapack. | Refines D-08. CORE-011. Standard air is 79 % N₂ / 21 % O₂, as in ZAS. |
| D-43 | 2026-10-30 | **Paused zones.** If any part of a zone is in an unloaded chunk, the whole zone pauses, and so does a zone breached into a region that reaches an unloaded chunk. Anything interacting with a paused zone sees its last-known state. Gas sent into it is voided and gas drawn from it is supplied without depleting it, as with the ambient, so pipe networks keep running and no pressure builds up. | Refines D-24. GEO-024, GEO-026, GEO-030 to GEO-038. |
| D-44 | 2026-10-30 | **Sub-cells.** A cell is a connected region of a position's free space, so a block that divides its free space (a glass pane, a wall) holds several independent cells that can belong to different zones. Moving or animating blocks are ignored until they settle; position-dependent static shapes are re-evaluated when they change. | Refines D-22. GEO-001, GEO-004, GEO-042, GEO-043. Blocks inset from a face (chests, cactus) leak around their edges by design. Fences, fence gates and iron bars are permeable (GEO-009). |
| D-45 | 2026-10-30 | **Ambient sources, in precedence order:** a datapack definition, then a registered provider, then an ambient seeded from another mod's data, then standard Overworld air as the fallback. Pack authors get the final say, and unknown dimensions stay safe. | ENV-011, ENV-014, SPACE-020. A static datapack entry overrides a provider's dynamic ambient. |
| D-46 | 2026-10-30 | **API mutations are asynchronous.** Adding or removing gas or heat returns a future that completes on the server thread once the atmos tick that applies it has been applied (at most one atmos tick). Added gas leaves the caller immediately; removed gas arrives when the future completes. | Follows from D-30. API-016, API-013. Synchronous results would break conservation when geometry changes first. |
| D-47 | 2026-10-30 | **Armor and suits.** Vanilla armor protects against heat only: no pressure rating, no cold protection, and no seal, so it can't be used with internals. Internals need a sealed head-slot item (breath mask, gas mask, suit helmet). R1 ships a **softsuit** (full seal, low pressure down to vacuum, 0–500 K, no high-pressure protection) and a **hardsuit** (full seal, all pressures and temperatures, −10 % speed per piece). | LIFE-020, LIFE-021, LIFE-025, LIFE-027, CONT-032, CONT-036. The hardsuit moves from R2 to R1. |
| D-48 | 2026-10-30 | **Heat conduction model.** A zone's gas-tight face conducts to the first cell or heat-source block within 3 blocks along its normal (else the ambient, as ground), through the wall's blocks in series (1/U = Σ 1/Uᵢ) with realistic U values. Faces onto vacuum radiate from the wall's outer surface in series with conduction (ε 0.9). Near-vacuum zones don't conduct. No phase changes in R1. | HEAT-001, HEAT-004, HEAT-008, HEAT-011 to HEAT-017 |
| D-49 | 2026-10-30 | **Fire model.** All fire needs an O₂ fraction ≥ 0.14 and O₂ partial pressure ≥ 10 kPa. Hotspots burn 0.375 cell volumes per second and show as a `zas:gas_fire` block where vanilla fire can't exist; vanilla fire in a zone is a hotspot. Fuel blocks and fire blocks burn as carbon at a profile power (5 or 10 kW). O₂-poor burning makes CO. Torches ignite flammable mixtures by default. | FIRE-003, FIRE-005, FIRE-011, FIRE-014, FIRE-018, FIRE-019, FIRE-025, FIRE-030 |
| D-50 | 2026-10-30 | **Devices run in the content mod, through the public API.** `zas_atmos` steps its pipe networks and devices on the server thread once per atmos tick, reads zones with API queries and changes them with asynchronous API mutations, one outstanding mutation per device port. The engine has no device phase. | Supersedes the device phase in CORE-040. CONT-006, CONT-071, CONT-072, CONT-095. Shows the API is enough for other tech mods. |
| D-51 | 2026-10-31 | **With a space-mod adapter active, ZAS³ is the only source of atmospheric harm.** The other mod's own oxygen and temperature effects on entities (damage, suit drain) are suppressed, and its oxygen checks return ZAS³'s verdict. Its world feeds ZAS³ as inputs: planet data seeds ambients, its suits get protection data, and its space-native mobs are immune. **Cooperative** mode also counts its breathable areas (Ad Astra distributors) as standard air; **Authoritative** counts only ZAS³'s atmosphere. Normal defaults to Cooperative, Hardcore to Authoritative. | SPACE-001, SPACE-004, SPACE-010, SPACE-013, SPACE-017, SPACE-019, SPACE-022, SPACE-023. Avoids double damage; splitting ownership by zone was rejected. |
| D-52 | 2026-10-31 | **Ad Astra planets seed ambients:** `oxygen: true` gives standard air, `oxygen: false` a vacuum, both at the planet's temperature. A vacuum ambient may carry a temperature other than 2.7 K, used for radiation and exposure. | SPACE-020, ENV-003. Venus seeds as a 737 K vacuum, so a softsuit burns there and a hardsuit is needed. |
| D-53 | 2026-10-31 | **Clients get no zone geometry.** In R1 a client receives only its own player's atmosphere state (at most 4 Hz, on threshold or display-resolution changes), airflow data for active edges within 32 blocks (once per atmos tick), and debug data while an operator's overlay is on. Gas visibility, which would need per-cell sync, moves to R2. | DATA-005, DATA-014, DATA-015, DATA-006, CLIENT-002. Avoids Ad Astra #434-style sync floods. |
| D-54 | 2026-10-31 | **Persistence is chunk-aligned and saves only what can't be rebuilt.** Each chunk saves its cells' zone membership and its volume share of each zone's mixture. Edges, seal masks, sub-cell shapes and hotspots are rebuilt on load. Held block changes and queued gas additions are saved; queued removals are cancelled at shutdown. The content mod saves its own pipe, device, canister and tank state. | DATA-001, DATA-002, DATA-008 to DATA-011, DATA-016. Keeps CORE-036 across save and shutdown. |
| D-55 | 2026-10-31 | **Faults are isolated.** An exception in third-party code (providers, seal providers, code reactions, hazard handlers, listeners) is caught, logged at most once per source per minute, and that source is skipped for the tick. An exception inside the engine pauses that dimension, and the server keeps running. | NFR-013, NFR-014, OPS-005 |
| D-56 | 2026-10-31 | **One asset license.** All assets, derived from vg or drawn from scratch, are CC BY-SA 4.0 International, and every asset file has a `CREDITS` entry (a vg source or "original"), checked in CI. | Extends D-26 and D-39 to original assets. NFR-044, NFR-045. |
| D-57 | 2026-10-31 | **Reactions run inside containers, and gas bombs are in R1.** Pipe networks, canisters and tanks react their mixtures each atmos tick through a standalone-mixture API call. A container mixture that ignites burns as one hotspot, and the existing tank failure (CONT-038) and new canister overpressure rules turn the pressure into explosions. R1 adds vg's handheld tank transfer valve with a timer, and a redstone-triggered, FE-powered igniter that lights its zone. Container explosions break blocks only in Hardcore. Datapack reactions may require an ignition source. | Supersedes CONT-073. API-019, FIRE-006, FIRE-031, CONT-100 to CONT-107, S-13. Zone deflagration (FIRE-012) stays R2. Container failure values in D-59. |
| D-58 | 2026-10-31 | **Phase changes are future scope, with data hooks now.** No phase changes anywhere in R1 (zones, pipes, canisters, tanks). Gas definitions accept optional phase data (melting and boiling points, latent heats), shipped for built-in gases but unused, so a later update needs no format break. | HEAT-017, HEAT-018, CORE-017 |
| D-59 | 2026-10-31 | **Container failure values.** Pipe networks burst in R1: more than 100 atm above a segment's cell, one segment breaks and dumps the network into its cell, without exploding. Canisters explode above 135 atm with power (P − 135 atm) / 10 atm. Tank and canister explosion power is capped at 16 (configurable). An igniting container burns a parcel of 375 L per second, or all of it if smaller, so a TTV tank burns in one tick. The igniter draws 20 FE/t, and the TTV timer runs 0–600 s (default 10 s). | Answers to the post-review gap check. Refines D-57. CONT-005, CONT-100, CONT-102, CONT-104, CONT-105, CONT-108, CONT-109, CONT-065, FIRE-031. Leaks and configurable pipe thresholds stay R2 (CONT-005). |
| D-60 | 2026-10-31 | **The full form is a rationale and a verification plan, nothing more.** The rationale says where a requirement comes from: vg behaviour (`vg:`), a user decision (`Decision:`), or an inference with its reason (`Inferred:`). The verification plan is one or two sentences naming the Unit, GameTest, Bench, Manual or Inspect checks. Numbers stay in the statement; Depends stays optional. | README entry format. Replaces the earlier full form (separate acceptance numbers and mandatory Depends). |
| D-61 | 2026-10-31 | **Photosynthesis follows the preset.** At light 15, 8 lit crops supply one breather's O₂ under Normal and 16 under Hardcore (0.125 and 0.0625 mol/h per crop), with leaves at half the crop rate. | CONT-057, OPS-007. Replaces defaults under which one crop supplied about 7 breathers. |
| D-62 | 2026-10-31 | **Assets from other sources.** Besides original and vg-derived assets, an asset may come from any other source whose license allows adapting it under CC BY-SA 4.0 (CC0 1.0, CC BY 3.0 or 4.0, CC BY-SA 3.0 or 4.0). Its `CREDITS` entry records the source, the SPDX license id and the attribution. A vg entry may use a short commit hash. | Amends D-56. NFR-045. |
| D-63 | 2026-10-31 | **Zones dissolve into the ambient.** A zone is destroyed when it is nearly equivalent to its ambient and has at least one edge exposed to the ambient. Nearly equivalent means CORE-006's tolerances on pressure and on each gas's molar density, but temperature is not compared once the zone's pressure is within the absolute pressure tolerance (default 0.1 kPa) of the ambient's, because near-empty gas carries negligible heat; otherwise the full CORE-006 test applies. Its cells become ambient again and its gas goes to the ambient. *Implementation note: "near-empty" is read as the zone's pressure also being within that tolerance of vacuum, so at ordinary pressures temperature is always compared.* | CORE-076. Takes the place of CORE-039's snap for zones with an ambient edge; relaxes CORE-006's temperature test for this check only. A zone venting to a vacuum (2.7 K) kept its own temperature, never became equivalent, and its ambient edge never slept. |
| D-64 | 2026-10-31 | **Pinned zones never dissolve into the ambient** (CORE-076); they keep exchanging through their ambient edges. *Implementation note: the pin follows the zone's cells and gas: a zone that merges with a pinned zone is pinned, every part of a pinned zone that splits is pinned, and the pin is saved with the zone's chunk shares.* | CORE-076, CORE-052, API-018. Exempts pinned zones from D-63's dissolution. |
| D-65 | 2026-10-31 | **Excluding a dimension purges its zones.** When a dimension becomes excluded, its zones are dropped and its chunks save without them; un-excluding it does not bring them back. This is unlike a dimension that is merely unloaded, whose zones persist in their chunks and pause until they load again. | ENV-007, DATA-001. Settles #112. |
| D-66 | 2026-11-01 | **Ad Astra moves to R2 with Mekanism.** The Ad Astra adapter (SPACE-001, SPACE-003, SPACE-004 and SPACE-010 to SPACE-023), spike S6 and scenario S-06 leave R1. Like Mekanism (D-11), Ad Astra comes back after R1 as a reference integration built only on the public API. R1 keeps the generic pieces space mods build on: per-dimension ambients and ambient providers (ENV-004), the breathability helper (API-014), the life-support capability (API-022) and the NeoForge breathing event (SPACE-002). | Owner's call: modded support is grouped together after R1. Supersedes D-29's R1 scope; nothing in R0 or R1 depends on Ad Astra. Retires plan risk R6 for R1. [#9](https://github.com/west3436/zas-cubed/issues/9), #54 to #56. |
| D-67 | 2026-11-01 | **Art is made in Blockbench as Minecraft's own models.** Blocks and items use vanilla JSON models, built from boxes on the 1/16-block grid, with textures at 16 pixels per block. They are made in Blockbench or written by hand. Handheld items are flat item sprites, and wearables use the standard armor texture layout. No OBJ or other free-form meshes. vg's sprites are drawn in a three-quarter view, so block faces are redrawn from them; item sprites carry over more directly. | Owner's call. Supersedes D-26's Blender MCP workflow; D-26's sources and licensing stand. Free-form meshes clash with vanilla's pixel grid and add to chunk meshes, and JSON models are plain text that can be reviewed in a PR. [#53](https://github.com/west3436/zas-cubed/issues/53). |

## Assumptions (confirmed 2026-10-31)

| ID | Assumption |
|---|---|
| A-01 | ZAS³ needs no other mod. All third-party compat (Ad Astra in R1, Mekanism later) is optional. |
| A-02 | A full block cell holds 1,000 L (1 m³). Partial blocks follow D-22. |
| A-03 | The simulation is server-authoritative. Clients receive only the summaries they need for visuals and HUD. |
| A-04 | Pressure is reported in kPa and temperature in K internally. Display units are a client setting. |
| A-05 | Content is crafted through normal survival progression and powered with FE, from ZAS³'s own generator or from other mods (D-25). Creative and admin tools exist for testing. |

## Glossary

| Term | Meaning |
|---|---|
| **Cell** | A connected region of a block position's free space that reaches at least one face. Most positions are one cell; a block such as a glass pane divides its position into several sub-cells (D-44). |
| **Zone** | A set of connected cells whose gas is treated as perfectly mixed, with one mixture for the whole zone. It only exists where air has been modified (D-13). |
| **Connection** | One gas-permeable face between a zoned cell and a neighbouring cell outside its zone |
| **Edge** | All connections between one zone and one other zone (a *zone edge*), or between a zone and the ambient (an *ambient edge*). Gas exchange happens per edge. |
| **Ambient** | A dimension's fixed atmosphere: composition, pressure, temperature. It's an infinite reservoir that unzoned cells implicitly hold. |
| **Enclosure** | A connected region of cells bounded by gas-tight faces, with volume no larger than the configured maximum (D-23) |
| **Seal mask** | For a block state, which of its six faces are gas-tight |
| **Zone-boundary block** | A block that may pass gas but keeps the zones on either side separate, such as an open door. ZAS calls this ZONE_BLOCKED. |
| **Atmos tick** | One pass of the simulation scheduler (default 1 s), computed off-thread |
| **Preset** | The global Normal/Hardcore setting that picks hazard and zone-trigger defaults (D-14) |
| **Hotspot** | A cell that is actively burning |
| **Airflow** | Forces on entities and items caused by pressure differences across an edge |
| **Life support** | Anything that supplies breathable gas or pressure or thermal protection to an entity, such as suits, tanks, masks, or another mod's oxygen field |
| **Pipe network** | Connected pipes that share one gas mixture. Devices exchange gas between networks and zones. |
| **Ambient provider** | An API hook that supplies a dimension's ambient atmosphere programmatically, beyond the datapack default |

### Units and constants

| Quantity | Unit | Notes |
|---|---|---|
| Pressure | kPa | 1 atm = 101.325 kPa |
| Temperature | K | 0 °C = 273.15 K. Cosmic background 2.7 K. |
| Amount | mol | |
| Volume | L | Full block = 1,000 L (A-02) |
| Energy and power | J, W; FE for devices | Heat capacity in J/K; specific heat in J/(mol·K) |
| Gas constant | R = 8.314 kPa·L/(mol·K) | Ideal gas law P = nRT/V |
| Time | s, or game ticks (20 per s) | Rates are defined per second (D-20) |

## Reference scenarios

These are the end-to-end stories the requirements must support. They become
GameTests or manual playtest scripts, and every R1 area should trace to at
least one.

| ID | Scenario |
|---|---|
| S-01 | **Pressurize a station.** In a vacuum dimension a player builds a sealed room with a powered air alarm, pipes O₂ and N₂ canisters into a vent, and fills the room to about 101 kPa of breathable air. Then they take off their helmet. |
| S-02 | **Hull breach.** Breaking a window of that room decompresses it, faster for small rooms and slower for large ones (D-21). Loose items and unanchored players are dragged toward the breach. Unsuited players suffer hypoxia and low pressure. The air alarm closes firelocks, isolating the breach. |
| S-03 | **Overworld gas leak.** A leaking hydrogen canister in a sealed workshop forms a zone. A torch or fire ignites the mix and it burns, consuming O₂, producing water vapor and heat, and raising the pressure. |
| S-04 | **Room fire.** In a zone, or in any sealed room under Hardcore, a fire uses up O₂, makes CO₂ and heats the air, then goes out when O₂ drops too low. Opening a door to fresh air rekindles it. In Normal, an ordinary sealed Overworld room stays ambient and fire behaves as in vanilla. |
| S-05 | **Heat.** A room next to lava warms up. A room in a cold vacuum dimension loses heat through its walls unless it's heated. A ZAS³ heater warms the room air. |
| ~~S-06~~ | **Space mod (Ad Astra).** *Deferred to R2 with D-66; it returns with the Ad Astra integration.* On an Ad Astra planet, Ad Astra's oxygen checks agree with ZAS³: a sealed, pressurized ZAS³ room is breathable without Ad Astra's distributor, and Ad Astra suits count as life support. Other space mods can do the same through the API. |
| ~~S-07~~ | ~~**Mekanism bridge.**~~ Deferred with D-11. It returns when the Mekanism reference integration is scheduled. |
| S-08 | **Breathing gear.** A player in a ZAS³ softsuit with an O₂ tank survives in vacuum and toxic zones while the supply lasts. The suit's pressure and thermal ratings protect them. |
| S-09 | **Persistence.** Saving and reloading keeps zone compositions, temperatures and pipe contents. Zones frozen by unloaded chunks resume correctly. |
| S-10 | **Scale.** In a vacuum datapack dimension under the Hardcore preset, a station of 100 zones holding 40,000 zoned cells (the largest 8,000) runs 20 pipe networks with 300 powered devices, with 8 players and 100 breathing mobs inside zones, 2 hotspots burning, one breach-and-reseal every 60 s, and one player leaving and re-entering view distance every 2 minutes so part of the station pauses and resumes. It stays within the NFR-001 and NFR-005 budgets. |
| S-11 | **Hardcore greenhouse.** In Hardcore, planting crops in a sealed greenhouse forms a zone, and under light the plants turn CO₂ into O₂. In Normal, plants only do this inside existing zones. |
| S-12 | **API without ZAS³.** A mod that bundles the ZAS³ API, running without ZAS³ installed, gets safe vanilla defaults and doesn't crash (D-17). |
| S-13 | **Toxins lab.** A player lights an H₂ burn chamber with a redstone-triggered igniter, pumps the hot O₂-rich exhaust into a tank, and loads it into a tank transfer valve with an H₂ tank. The exhaust is hot enough that the mix stays above H₂'s 858 K autoignition point. When the timer opens the valve, the mixture autoignites in the tank, the pressure passes 50 atm, and the tank explodes. Under Normal the blast hurts entities but breaks no blocks. |

### Scenario verification

Each scenario's verification plan, in the D-60 style. Together they are the
R1 end-to-end test plan (NFR-032).

- **S-01:** A GameTest in a vacuum test dimension builds a sealed room with a
  powered air alarm, O₂ and N₂ canisters on a connector and a vent, and
  checks it fills to within 1 kPa of 101.325 kPa at 21 % O₂; a player
  without a helmet then takes no atmospheric harm for 60 s.
- **S-02:** A GameTest breaks a window of the S-01 room and checks an ambient
  edge forms, a 27-cell room drains faster than a 1,000-cell one, an item and
  an unanchored player move toward the breach, an unsuited player takes
  hypoxia and decompression damage, and the alarm closes the firelock.
- **S-03:** A GameTest opens an H₂ canister in a sealed Overworld workshop and
  checks a zone forms, a torch ignites the mixture once it is flammable, and
  O₂ falls while H₂O, temperature and pressure rise.
- **S-04:** GameTests check that under Hardcore a fire in a sealed room forms
  a zone, uses O₂, makes CO₂, heats the air and goes out, and burns again once
  a door opens to fresh air and it is relit; under Normal the same room stays
  unzoned and the fire behaves as vanilla.
- **S-05:** GameTests check a room beside lava warms, a room in a 2.7 K
  vacuum dimension cools through its walls, and a heater on its network holds
  it at its setpoint.
- **S-06** (R2, D-66): A GameTest with Ad Astra installed checks that on the Moon a
  sealed, pressurized zone is breathable with no distributor, `hasOxygen`
  agrees, an Ad Astra space suit protects in vacuum, and only ZAS³ damage
  applies; a Manual playtest repeats it by hand.
- **S-08:** A GameTest checks a softsuited player on internals survives in
  vacuum and in a CO zone while the tank lasts, and that hypoxia begins once
  it runs dry (with an accelerated tank).
- **S-09:** A GameTest saves and reloads a station with zones, pipes,
  canisters and a paused zone, checks every amount within 1e-9, and checks
  the paused zone resumes when its chunk loads.
- **S-10:** Bench runs the scripted scenario on reference hardware against
  NFR-001, NFR-002, NFR-004 and NFR-005; CI runs its headless engine version
  (NFR-034).
- **S-11:** A GameTest checks that under Hardcore planting crops in a sealed
  glass greenhouse forms a zone and lit crops turn CO₂ into O₂, and that under
  Normal the same greenhouse stays unzoned.
- **S-12:** The API-048 GameTest runs the example addon without ZAS³ and
  checks it gets standard air and breathable answers with no errors.
- **S-13:** A GameTest scripts the toxins lab: the igniter lights an H₂ burn
  chamber, the exhaust is pumped into a tank above 858 K, the TTV timer
  opens the valve, and the tank explodes; under Normal no blocks break. A
  Manual playtest runs it by hand.

*2026-10-31: R0 (the Phase D exit) runs S-01, S-02 and S-09 as far as R0 can,
with no devices, pipes, canisters, alarms, firelocks or hazards: S-01 fills
the room with the `/zas gas` commands and skips the helmet check; S-02 checks
the ambient edge, the 27-cell room draining faster than the 1,000-cell one,
and zone splits and merges, with no item or player movement, damage or
firelock; S-09 keeps zones and a paused zone, with no pipe or canister
contents. The vacuum test dimension is the End with a test datapack's vacuum
ambient, since the GameTest server has only the vanilla dimensions. The plans
above stay the R1 plans. See [Phase D exit status](../plan.md#phase-d-exit-status-2026-10-31).*

## Questions

### Resolved (2026-10-30)

| Question | Decision |
|---|---|
| Q-01 names | D-15 |
| Q-02 packaging | D-16 |
| Q-03 vanilla ambients | D-19 |
| Q-04 tick period | D-20 |
| Q-05 venting model | D-21 |
| Q-06 partial blocks | D-22 |
| Q-07 enclosure cap | D-23 |
| Q-08 unloaded chunks | D-24 |
| Q-09 power | D-25 |
| Q-10 art | D-26 |
| Q-11 breathers and respiration | D-27 |
| Q-12 block damage | D-28 |
| Q-13 space targets | D-29 |
| Q-14 distribution | D-18 |
| Q-15 threading | D-30 |
| Q-16 pipe cadence | D-31 |
| Q-17 alarm room | D-32 |
| Q-18 gas sources | D-33 |
| Q-21 power details | D-34, D-35, D-36, D-37 |
| Q-22 content mod ID | D-38 |
| Q-23 asset license version | D-39 |
| NFR reference hardware (new) | D-40 |
| Zone trigger (new) | D-13, D-14 |
| API without ZAS³ (new) | D-17 |
| Mekanism scope (new) | D-11 |

### Open

Each one is resolved into a decision during review, and the requirements it
blocks reference it as `TBD(Q-nn)`. Only the questions parked with Mekanism
(D-11) remain; they are revisited when that integration is scheduled.

| ID | Question | Blocks | Proposed default |
|---|---|---|---|
| Q-19 | How many moles is 1 mB of a Mekanism chemical? | MEK (deferred) | Revisit with D-11. Lean: 1 mB = 1 L at 20 °C (≈0.0416 mol), so 1,000 mB fills one block at 1 atm. |
| Q-20 | Mekanism gear policy: does scuba gear protect against pressure? Does the Electrolytic Breathing Unit work in vacuum? | MEK (deferred) | Revisit with D-11. Lean: faithful to Mekanism. |
