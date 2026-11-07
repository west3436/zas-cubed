# 52 · Compat with other mods (`COMPAT`)

**Status:** Draft, 2026-11-06. Candidates for the owner's review, all after R1.

**Scope.** Adapters beyond Mekanism ([MEK](50-compat-mekanism.md)) and the space
mods ([SPACE](51-compat-space.md)). D-78 sets the targets, and the priority shows
how sure each one is:

| Owner's call | Priority | Targets |
|---|---|---|
| For sure | `Must` | PneumaticCraft: Repressurized, CC: Tweaked, Jade, Curios and Accessories, KubeJS, JEI and EMI |
| Very likely | `Should` | Applied Energistics 2, Create Aeronautics |
| Likely | `Could` | Modern Industrialization |
| Maybe | `Could` | Moving structures on Sable, Create: The Factory Must Grow |
| No | `Won't` | Create itself |

The Mekanism and Ad Astra adapters are reference integrations that other mods can
copy, so they use only the public API (D-11, D-66). These aren't, so they may use
ZAS³'s internals and the content mod's classes where they need to.

Background: [compat-targets](../research/compat-targets.md).

## General

#### COMPAT-001 · Adapters live in this repo
`Must` · `R2` · `Draft` · Verify: Inspect · Trace: D-78, A-01, NFR-022

Each adapter shall live in this repository, in its own optional module, and need
no change to the target mod and nothing from its authors.

#### COMPAT-002 · Adapter version check
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, SPACE-003

Each adapter shall check its target's version as SPACE-003 says: out of its
supported range it disables itself and logs one warning, and the game still
launches.

#### COMPAT-003 · Public hooks first
`Should` · `R2` · `Draft` · Verify: Inspect · Trace: D-78, MEK-003, SPACE-016

An adapter shall use its target's public API or capabilities where they cover
the need. A mixin into the target, or a call to a class outside its API package,
shall have its own GameTest.

#### COMPAT-004 · Tests with the target present
`Should` · `R2` · `Draft` · Verify: Inspect · Trace: D-78, NFR-022, NFR-025, MEK-003

Each adapter's GameTests shall run in CI with the target mod present, in a run
separate from the main one, as the nightly compatibility matrix is (NFR-025).

## PneumaticCraft: Repressurized

#### COMPAT-010 · Pneumatic Armor as a suit
`Must` · `R2` · `Draft` · Verify: Inspect, GameTest · Trace: D-78, D-47, LIFE-026

The adapter shall ship protection data (LIFE-026) rating Pneumatic Armor as
ZAS³'s softsuit (D-47).

#### COMPAT-011 · Pneumatic Armor's air as internals
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, LIFE-028 · Depends: COMPAT-010

Internals shall be able to draw from the air in a worn piece of Pneumatic Armor,
as standard air at 293.15 K, at the rate TBD(Q-24). Drawing shall lower that
piece's pressure.

#### COMPAT-012 · Leaks fill rooms
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-012

Air that a PneumaticCraft machine leaks into a zone shall be added to that zone
as standard air, at the rate TBD(Q-24).

#### COMPAT-013 · Heat exchange with a zone
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-013

A block with PneumaticCraft heat that faces a zone shall exchange heat with that
zone, conserving energy at the rate TBD(Q-25).

## CC: Tweaked

#### COMPAT-020 · Devices are peripherals
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78

Each atmospherics device with a window shall be a peripheral that reads the
values its window shows.

#### COMPAT-021 · Computers change settings
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, CONT-074 · Depends: COMPAT-020

A computer shall be able to change any setting the device's window can, under the
same validation as the window (CONT-074).

#### COMPAT-022 · Zone events reach computers
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-030, API-031 · Depends: COMPAT-020

A computer attached to an area air controller shall get an event when that
controller's zone is breached, is sealed, catches fire, or has its last fire go
out.

## Jade

#### COMPAT-030 · Device readout
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78, API-063

Looking at an atmospherics device shall show its pressure and temperature in the
player's display units, as icons and numbers rather than sentences.

#### COMPAT-031 · Gas mix readout
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78

Looking at a canister, tank or pipe shall show the share of each gas in it.

#### COMPAT-032 · Devices only
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78

Looking at plain air or at a block that isn't an atmospherics device shall show
no atmosphere reading, so that Jade doesn't replace the analyzer.

## Curios and Accessories

#### COMPAT-040 · Tanks in Curios slots
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, LIFE-028

Internals shall look in the wearer's Curios slots after its armor slots and before
its offhand.

#### COMPAT-041 · Tanks in Accessories slots
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, LIFE-028

Internals shall look in the wearer's Accessories slots after its armor slots and
before its offhand.

#### COMPAT-042 · Masks in head slots
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-022

A life-support item in a Curios or Accessories head slot shall count as the head
piece for `sealedBreathing` and `gasFiltering`, and shall add no body coverage.

#### COMPAT-043 · Slot tags
`Must` · `R2` · `Draft` · Verify: Inspect · Trace: D-78, CONT-030, CONT-031, CONT-088

ZAS³'s gas tanks shall be tagged for back and belt slots, and its masks for head
slots, as optional tag entries.

## KubeJS

#### COMPAT-050 · Events in scripts
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-030, API-031, CORE-058

ZAS³'s zone, fire and open-air events shall be available as KubeJS server events.

#### COMPAT-051 · Calls in scripts
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-014

`Atmospheres`, `Breathing`, `GasMutations`, `HeatMutations`, `ZonePins` and
`Fires` shall be available to server scripts.

#### COMPAT-052 · Hazards in scripts
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, LIFE-032, NFR-013

A script shall be able to register a hazard handler (LIFE-032), and a handler that
throws shall be contained as NFR-013 says.

#### COMPAT-053 · Ambients in scripts
`Must` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, ENV-004, NFR-013

A script shall be able to register an ambient provider (ENV-004), and a provider
that throws shall be contained as NFR-013 says.

## JEI and EMI

#### COMPAT-060 · Reactions in JEI
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78, API-020

Each reaction loaded from data shall appear in JEI with its gases, conditions and
products.

#### COMPAT-061 · Gases in JEI
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78

Each registered gas shall appear in JEI with its molar mass, heat capacity and
tags.

#### COMPAT-062 · The same in EMI
`Must` · `R2` · `Draft` · Verify: Manual · Trace: D-78 · Depends: COMPAT-060, COMPAT-061

The entries of COMPAT-060 and COMPAT-061 shall also appear in EMI. EMI loads JEI
plugins, so this may need no code of its own.

## Applied Energistics 2

#### COMPAT-070 · Gases in ME storage
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78

ME storage shall hold each gas as its own storable type.

#### COMPAT-071 · Mixtures split
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78 · Depends: COMPAT-070

A mixture entering ME storage shall be stored as its separate gases.

#### COMPAT-072 · Temperature on leaving
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78 · Depends: COMPAT-070

Gas leaving ME storage shall be at the temperature TBD(Q-26).

#### COMPAT-073 · Buses reach gas handlers
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-021 · Depends: COMPAT-070

ME import and export buses shall move gas to and from any block that provides
`IGasHandler`.

#### COMPAT-074 · No gas from rounding
`Should` · `R2` · `Draft` · Verify: Unit, GameTest · Trace: D-78 · Depends: COMPAT-070

Moving gas into and out of ME storage shall never create gas, including when
rounding to whole storage units.

## Create Aeronautics

#### COMPAT-080 · Lift follows the ambient
`Should` · `R2` · `Draft` · Verify: GameTest, Manual · Trace: D-78, ENV-004

With Create Aeronautics present, the air pressure that its balloons, wings and
propellers feel shall be Sable's value for that position multiplied by ρ / ρ₀.
ρ is the ambient's density, Σ(pᵢMᵢ) / (RT), and ρ₀ is that of standard air at
293.15 K.

For example, the factor is 1 in the Overworld, so lift there is unchanged. It is 0
in a vacuum, and about 0.013 in 0.6 kPa of CO₂ at 210 K.

#### COMPAT-081 · Prompt on both sides
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, ENV-015 · Depends: COMPAT-080

A change in the ambient shall reach the factor on the server and on clients within
one atmos tick of the ambient's answer changing.

#### COMPAT-082 · No feedback from seeded ambients
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, SPACE-020, ENV-014 · Depends: COMPAT-080

The factor shall be 1 in a dimension whose ambient was seeded from Sable's
`dimension_physics` (SPACE-020), because Sable's value already includes it.

#### COMPAT-083 · Lift setting
`Should` · `R2` · `Draft` · Verify: GameTest · Trace: D-78 · Depends: COMPAT-080

A server setting, on by default, shall turn the factor off.

## Modern Industrialization

#### COMPAT-090 · Its gas fluids move as gas
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, TBD(Q-19) · Depends: SPACE-021

Modern Industrialization's oxygen, nitrogen, hydrogen, methane and steam fluids
shall move into and out of ZAS³ gas containers as the matching gas, at the Q-19
rate. Steam enters as water vapor at 373.15 K or hotter, as in MEK-010.

## Moving structures on Sable

A maybe (D-78). SPACE-032 keeps these out of R1. Sable appears to keep a sub-level's
blocks in the level at plot coordinates, so ZAS³'s geometry may see them unchanged
(code-read). A spike comes first.

#### COMPAT-100 · Zones on moving structures
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, D-07, SPACE-032

A sealed room on a Sable sub-level shall hold a zone that keeps its gas as the
structure moves.

#### COMPAT-101 · Breathing on board
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-014 · Depends: COMPAT-100

An entity inside such a room shall breathe that zone's air.

#### COMPAT-102 · Openings on a moving structure
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78 · Depends: COMPAT-100

A room on a sub-level with an opening to the outside shall exchange gas with the
ambient of the dimension it is in.

## Create: The Factory Must Grow

A maybe (D-78). These are candidates from its public pack documentation, to be
scoped when this is picked up.

#### COMPAT-110 · Flamethrowers ignite gas
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-055

A TFMG flamethrower shall ignite flammable gas in a zone.

#### COMPAT-111 · Extinguishers put out gas fires
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78, API-055

A TFMG fire extinguisher shall put out gas fires.

#### COMPAT-112 · Engines use the room's air
`Could` · `R2` · `Draft` · Verify: GameTest · Trace: D-78

A TFMG engine in a zone shall take its oxygen from that zone and add its exhaust
to it.

## Create

#### COMPAT-120 · No Create adapter
`Won't` · `R2` · `Draft` · Verify: Inspect · Trace: D-78

There shall be no adapter or data for Create's own blocks and items. Create
Aeronautics and TFMG need Create installed, but their adapters target only those
mods.
