# 50 · Mekanism integration (`MEK`)

**Status:** ⏸ **Deferred, not in R1 (D-11, 2026-10-30).** Mekanism is the
first planned compat target after R1. These candidates and the research are
kept so that work can start from them later. They aren't reviewed until then.

**Principle for when this resumes (D-11).** The Mekanism integration is a free
*reference integration*. It shall use only ZAS³'s public API, so other mods
can copy it. The only exceptions are hooks on Mekanism's own internals, such
as venting, which other mods would never need because they can call the API
directly.

**Scope when resumed.** Four integrations:
1. chemical↔gas bridge
2. venting into rooms
3. breathing gear
4. heat coupling

ZAS³ never requires Mekanism (A-01). Background:
[mekanism-1.21.1](../research/mekanism-1.21.1.md), which covers Mekanism
10.7.19 at `a00109e`.

**Key research facts:**
- Gases, infuse types, pigments and slurries are now one `Chemical` registry.
- Mekanism has **no CO₂ or N₂** chemical.
- Mekanism has **no** oxygen, atmosphere or venting API, event or IMC.
- **Venting needs mixins.** The other three integrations can ship using
  capabilities, data maps and tags.

## General

#### MEK-001 · Optional and version-gated
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: A-01, NFR-022

Mekanism compat shall load only when Mekanism 10.7.15 or newer is present.
Mixin configs apply only when their target mod is loaded. On an unsupported
version, the feature is disabled and a warning is logged.

#### MEK-002 · Unit mapping
`Must` · `R2` · `Deferred` · Verify: Unit · Trace: TBD(Q-19)

One configurable global factor shall convert Mekanism mB to moles. The
proposal is 1 mB = 1 L of gas at STP, about 0.0446 mol. With it, 1,000 mB
fills one block at about 1 atm and Mekanism's recipe balance is preserved.
Mekanism-side storage is treated as having no pressure; zas-cubed applies
pressure limits on its own side.

#### MEK-003 · Per-hook GameTests
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: plan R5

Every mixin hook shall have its own GameTest. CI shall run them against the
oldest and newest supported Mekanism versions.

## 1. Chemical ↔ gas bridge

#### MEK-010 · Data-driven chemical map
`Must` · `R2` · `Deferred` · Verify: Unit, Inspect

A data-driven map shall convert between Mekanism chemicals and zas gases,
backed by an allow-list.

**Mapped by default:**
- O₂
- H₂
- water vapor and steam → H₂O. A temperature is assigned on entry; steam is
  at least 373.15 K.
- Cl₂, HCl, SO₂
- C₂H₄
- D₂ and T₂
- fusion fuel → half D₂, half T₂

**Never mapped:** process liquids, metals, radioactive chemicals, infuse types,
pigments, slurries.

#### MEK-011 · zas gases registered in Mekanism
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: S-07

When Mekanism is present, zas-cubed shall register its gases that Mekanism
lacks (CO₂, N₂, CH₄, CO, …) as Mekanism chemicals. This lets scrubbed
gases go into Mekanism tanks and tubes.

#### MEK-012 · Bridge port
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: S-07

An adapter block shall expose `mekanism:chemical_handler` to Mekanism, and
zas-cubed's gas handler or pipe connection to zas-cubed. It moves gas in both
directions, within zas-cubed's pressure ceilings and throughput limits.

#### MEK-013 · Native device ports
`Should` · `R2` · `Deferred` · Verify: GameTest

When Mekanism is present, zas-cubed devices with external ports (canisters,
connector ports) should also expose `mekanism:chemical_handler`.

## 2. Venting into rooms

#### MEK-020 · Release instead of void
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: D-11, S-07

When Mekanism voids a mapped chemical, the gas shall instead be released at
the source position into the adjacent zone, or into the ambient (CORE-053).
This applies to:
- chemical tank dumping and dumping excess
- electrolytic separator dumping
- industrial turbine dumping
- dump buttons
- the gauge dropper's dump
- broken tubes

#### MEK-021 · Venting cap
`Must` · `R2` · `Deferred` · Verify: GameTest

Venting shall respect a configurable per-source rate cap. An Ultimate tank
can dump about 410 m³/s. Amounts above the cap are either voided, as Mekanism
does today (the default), or released anyway (a config option).

#### MEK-022 · No-mixin exhaust port
`Must` · `R2` · `Deferred` · Verify: GameTest

An exhaust block shall accept Mekanism chemicals and release them into its
zone. It must work even when the venting mixins are disabled or broken.

#### MEK-023 · Radioactive chemicals
`Should` · `R2` · `Deferred` · Verify: GameTest

Radioactive chemicals shall stay out of the atmosphere by default. If a pack
maps one, its radiation shall be applied exactly once, never by both venting
and `dumpRadiation`.

## 3. Breathing gear

#### MEK-030 · Scuba gear as life support
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: S-08, API-022

A Scuba Mask plus a Scuba Tank shall count as a breathable supply when:
- the tank's mode is on
- the tank holds O₂

Breathing runs through `LivingBreatheEvent`/air supply (SPACE-002). Mekanism
already refills air supply from the tank, so zas-cubed adds no extra drain.

#### MEK-031 · Scuba protection scope
`Should` · `R2` · `Deferred` · Verify: GameTest · Trace: TBD(Q-20)

By default, scuba gear shall provide breathing and gas filtering only, with no
pressure or thermal protection. This is configurable.

#### MEK-032 · MekaSuit
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: S-08

A full MekaSuit shall provide pressure and thermal protection as a
life-support provider. The Electrolytic Breathing Unit's behaviour in vacuum
follows Q-20.

#### MEK-033 · Damage-type tagging
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: LIFE-030

Every zas-cubed damage type shall be tagged deliberately, because by default
the MekaSuit absorbs 100 % of damage types it doesn't recognise:
- Hypoxia bypasses armor.
- Toxic gas is preventable by scuba gear and the Inhalation Purification Unit.
- Pressure and temperature protection comes from the life-support model only,
  never twice.

#### MEK-034 · Inhalation Purification Unit
`Should` · `R2` · `Deferred` · Verify: GameTest

The Inhalation Purification Unit shall act as a filter against zas-cubed's
toxic gases. It supplies no O₂.

## 4. Heat coupling

#### MEK-040 · Capability heat exchange
`Must` · `R2` · `Deferred` · Verify: GameTest · Trace: D-11, API-013, S-05

A zone shall exchange heat with any block exposing `mekanism:heat_handler`
across their shared boundary face. The exchange:
- conserves energy
- is capped at equilibrium
- counts a multiblock's shared capacitor once

#### MEK-041 · Ambient replacement
`Should` · `R2` · `Deferred` · Verify: GameTest · Trace: plan R5

An optional, config-gated mixin set shall:
- replace Mekanism's biome-based ambient temperature, for blocks and for
  multiblocks, with the adjacent zone or ambient temperature
- put the heat Mekanism dissipates "to ambient" into the zone

## Later

#### MEK-050 · Fuel consumers and MekaSuit module
`Could` · `R2` · `Deferred`

- Jetpack and flamethrower exhaust going into the zone.
- A zas-cubed "life support" MekaSuit module, registered through Mekanism IMC.
