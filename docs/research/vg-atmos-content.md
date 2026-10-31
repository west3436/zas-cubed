# vgstation13 atmospherics content layer: design reference

Source: vgstation13 `Bleeding-Edge` (local clone). GPL, so this is a design reference only; no code is copied. Prefixes: `ATM/` = `code/game/machinery/ATMOSPHERICS/`, `ALT/` = `code/game/machinery/atmoalter/`, `MCH/` = `code/game/machinery/`, `HUM/` = `code/modules/mob/living/carbon/human/`, `LNG/` = `code/modules/organs/internal/lungs/`, `DEF/` = `__DEFINES/`, `ZAS/` = `code/modules/ZAS/`. Units: kPa, L, mol, K; R = 8.314; one tile = 2500 L (`DEF/atmos.dm:1`). A tick is 2 s unless noted.

## Implications for zas-cubed

- **Pipes are a separate simulation.** Pipe gas never joins zones; only devices move gas between networks and zones, by explicit remove/merge calls. Keep that boundary in the provider API.
- **A network is a list of mixtures, perfectly mixed when dirty:** one per pipe run (volume = sum of segments), one buffer per device port, plus attached portables. Cheap and gas-conserving; copy it.
- **Flow is limited by port-buffer volume, not power.** Pumps compute the moles to bring the whole downstream volume to target in one step, capped only by their 200 L input buffer. At 20 TPS, fix a pipenet period (vg: 2 s) or scale buffers by dt.
- **Devices act on the whole zone.** The provider needs: zone mixture at a block (marking it dirty), add gas, remove n moles, and fixed-reservoir behaviour for unsimulated space.
- **vg has no atmos energy model.** Most devices need area power but draw 0 W; portables need none. Design costs from scratch.
- **Control is area-scoped radio.** Vents and scrubbers register with their area and obey only air alarms. Minecraft needs a "room" concept (ZAS zone or explicit linking).
- **Firelocks follow the area alarm, not their own sensors**, and doors split zones even when open. Model doors as zone boundaries whose connection opens and closes.
- **Breathing is discrete and partial-pressure based:** 0.5 L every 8 s; O2 < 16 kPa, CO2 > 10 kPa, N2O 1/5 kPa; pressure hazards at 20/550 kPa; coverage-weighted clothing. Per-species lung handlers make it gas-agnostic; keep that.
- **Plasma is load-bearing** for toxins, contamination, default scrubber/filter lists, alarm presets and fire-alarm shelters. Replace it with configurable gas tags (toxic, fuel, contaminant).
- **Don't copy the bugs (§6):** pipes never leak or burst, the volume pump ignores its rate, filters/mixers/freezers run unpowered, heat-damage tiers never escalate.

## 0. Gas operations and turf access

Content code uses these gas-mixture operations: remove by moles, ratio or volume (`ZAS/_gas_mixture.dm:263-305`); merge with heat-capacity-weighted temperature (`:97`); volume-scaled copy (`:324-331`); multiply, subtract and adjust gas (`:51-86,374,385`); heat capacity and add energy (`:167-190`); molar density and partial pressure (`:137-142`); cached pressure (`:235-260`); react/zburn and `reaction_tick` (`:307,540`); and `equalize_gases` (`ATM/datum_pipe_network.dm:135-148`).

On a simulated turf, `return_air` returns the **zone** mixture (2500 L per tile) and marks the zone for update; `assume_air`/`remove_air` merge into or remove from it (`ZAS/Turf.dm:258-277`; `ZAS/Zone.dm:82`). On unsimulated turfs, `return_air` is a copy of a fixed composition, `remove_air` creates gas from it (an infinite source), and `assume_air` discards gas (`Turf.dm:212-249`).

## 1. Pipe networks

**Connectivity.** Objects declare which directions they connect in. Two link when each lists the other and their piping layers match; there are 5 layers (default 3), and layer manifolds/adapters bridge them (`ATM/atmospherics.dm:203-219`; `DEF/setup.dm:37-41`). HE pipes use a separate direction set and meet ordinary pipes only at junctions (`atmospherics.dm:110-116`; `ATM/he_pipes.dm:159-182`). Z-pipes exist (`code/modules/multiz/pipes.dm:4-22`). Bluespace caps merge all same-colour networks on a z-level (`ATM/components/unary/cap.dm:180-200`).

**Pipelines.** A pipeline flood-fills over connected pipes and owns one mixture. Its volume is the sum of its members: straight pipe 70 L, manifold 105, 4-way 140, layer manifold/adapter 260 (`ATM/datum_pipeline.dm:51-129`; `ATM/pipes.dm:115,445,692,957,1178`). If the fill reaches another pipeline, it absorbs that pipeline's members, volume and air, and deletes its network for rebuild (`datum_pipeline.dm:79-111`).

**Networks.** A network is a list of mixtures: every pipeline's air plus each device's per-port buffer (`ATM/datum_pipe_network.dm:93-108`). Unary devices have one buffer (default 200 L, starting at 273 K), binary two of 200 L, trinary three (`ATM/components/unary/unary_base.dm:11-16,124-128`; `binary_devices/binary_atmos_base.dm:28-32,208-216`; `trinary_devices/trinary_base.dm:38-43`). A two-port device belongs to two networks.

When the `update` flag is set, the network merges all members into one mixture, runs reactions/fire on it, and copies it back to each member scaled by volume (`datum_pipe_network.dm:39-46,110-133`). Devices set the flag whenever they move gas. A connected portable adds its own mixture to the list (`ALT/portable_atmospherics.dm:50-86`; `portables_connector.dm:62-68`).

Valves (manual, digital, T, conditional) don't pump. When open, network expansion passes through them, so both sides become one network. Closing deletes and rebuilds both networks, after a 1 s animation (`binary_devices/valve.dm:22-71`; `trinary_devices/t_valve.dm:25-60`; `pressure_valve.dm:208-216`).

**Rebuilds.** A newly built object links to its neighbours, builds its network and re-initialises them (`pipes.dm:152-170`). Destroying a pipe destroys its pipeline. Before that, the pipeline stores a volume-proportional share of its air in every member's `air_temporary`. The removed pipe dumps its share on its turf, and the remaining pipes re-merge theirs on rebuild (`datum_pipeline.dm:14-26,43-49,60-64,118-119`; `pipes.dm:97-108`).

Rebuilding is lazy: devices call `build_network` in every `process`, and pipes build a pipeline on first air access (`atmospherics.dm:257-262`; `pipes.dm:69-94`). A pipe with no connections deletes itself (`pipes.dm:331-332`). Unwrenching is refused above 2 atm over ambient, unless a socket wrench first vents one device-volume of gas (`atmospherics.dm:319-337`).

**Tick and order.** `SSpipenet` runs every 2 s: first pipenet-hook objects (the TEG), then each atmos machine's `process` (from the list end) with power accounting, then each network's reconcile (`code/controllers/subsystem/pipenet.dm:10-73`). Pipes drop off the machine list after their first process (`pipes.dm:199,535,778`). Zones, machinery and mobs also tick every 2 s (`code/controllers/subsystem/air.dm:66`; `DEF/subsystem.dm:74`; `code/controllers/subsystem/mob.dm:6`).

**Leaks, bursts and pipe heat: none.** Open-end mixing is commented out (`pipes.dm:536-554,779-797`). The pressure check runs only under `ATMOS_PIPELINE_PROCESSING`, itself commented out (`datum_pipe_network.dm:47-51`; `DEF/setup.dm:1267`), so the burst logic (1%/tick above a 100 atm differential, groaning above 80 atm; `pipes.dm:123-124,201-221`) is dead. Pipeline-to-turf heat exchange (`datum_pipeline.dm:172-230`) is never called. Gas leaves pipes only through devices, passive vents, deconstruction, or ruptured tanks/canisters.

## 2. Devices

"Δ-to-target" means n = ΔP·V/(R·T), where V is the receiving volume.

| Device (file) | Buffers | Key params | Transfer rule | Power / room |
|---|---|---|---|---|
| Pressure pump (`binary_devices/pump.dm`) | 200/200 L | target 0-4500 kPa (`:1,24,172`) | §2.1 (`:51-74`) | 0 W but needs power |
| Volume pump (`volume_pump.dm`) | 200 L | rate ≤200 L | always moves its whole input buffer (bug); stalls if out > in + 9000 kPa (`:48-78`) | 0 W |
| Passive gate (`passive_gate.dm`) | in 1000 L | open/closed | one-way Δ-to-equalize when P_in > P_out (`:19,39-64`) | none |
| Valves (manual/digital/T/conditional) | 200 L | conditional valve: pressure or temperature threshold from any port (`pressure_valve.dm:15-44`) | network merge (§1) | 0 W |
| Filter, mixer (`filter.dm`, `mixer.dm`) | 3×200 L (mixer out 300) | target ≤4500; one gas / two fractions | §2.1 | ignores power |
| Vent pump (`unary/vent_pump.dm`) | 400 L (large 1000) | direction; bounds ≤50 atm | §2.2 | 0 W / zone |
| Scrubber (`unary/vent_scrubber.dm`) | 200 L | gas set; rate 1000 L (panic 2000) | §2.2 | 0 W / zone |
| Passive vent, burst pipe (`unary/vent.dm`, `burstpipe.dm`) | 500 / 1000 L | – | two-way equalize with room when ΔP > 0.5 (`vent.dm:25-65`) | none / zone |
| Injector (`outlet_injector.dm`) | 200 L | rate 2500 L (≤10000) | pushes 1 atm × rate worth of moles, ignoring room pressure (`:48-65`) | 0 W / zone |
| Dual-port vent (`dp_vent_pump.dm`) | 200 L (large 2500) | external bound, input min, output max | vent rule; port 1 blows, port 2 siphons (`:84-133`) | 0 W / zone |
| Pressure tank (`unary/tank.dm`) | 2500 L @ 45 atm | gas | static; if punctured at ΔP ≥ 500 kPa, small explosion (`:1-50`) | – / dumps |
| Heat exchanger pair (`unary/heat_exchanger.dm`) | 200 L each | partner | sets both buffers to their combined temperature each air cycle (`:25-55`) | – |
| Freezer / heater (`MCH/Freezer.dm`) | 200 L | 73-293 K / 293-573 K (wider with parts) | one-way mix with a virtual body of 1000 + 500·(laser tiers) J/K at target (`Freezer.dm:1-2,35-42`; `unary/cold_sink.dm:16-33`) | ignores power |
| Thermoelectric cooler (`binary_devices/heat_pump.dm`) | 200 L | max ΔT = f(T2), asymptote 2500 K | pumps heat port 1 → 2 up to max ΔT (`:29-71`) | 3 kW |
| Gas miner (`ALT/gas_mine.dm`) | – | gas list | creates 4500 kPa × 1000 L of gas per tick until the room reaches 10000 kPa (`:17-18,76-107`) | 100 W+ / zone source |
| Canister, portable pump/scrubber (`ALT/`) | 1000 / 2000 / 2000 L | §2.3 | §2.3 | none / zone or tank |
| Meter, air sensor (`ALT/meter.dm`; `MCH/atmo_control.dm:1-105`) | – | id tag | broadcasts pipe/zone readings on 1439 (`meter.dm:44-110`) | 2-5 W |
| Cryo cell (`MCH/cryo.dm`) | 200 L | – | gas leaks toward 293 K (50 J/K); occupant temperature relaxes to the gas; needs ≥10 mol; heals below 225 K (`:485-538`) | power-gated |

Minor or vg-specific devices: a 10000 L "MSGS" gas store (1 kW; `binary_devices/MSGS.dm:12-83`); cell-powered space heaters and A/Cs that adjust a sample of zone air (`ATM/hvac/spaceheater.dm:354-376`, `chiller.dm:92-119`); a legacy oxygen generator (`unary/oxygen_generator.dm:31-49`); end caps (`unary/cap.dm`); a computer for the huge scrubbers (`ALT/area_atmos_computer.dm:1-3`); and vaporizers, a planetary gas extractor, geysers and a cryotheum resonator (`ALT/vaporizer.dm`, `ALT/gas_extractor.dm`, `ATM/hvac/`).

### 2.1 Pumps, filters and mixers

**Pump.** Moves n = (P_target − P_out)·(V_outbuf + V_outnet)/(R·T_in) from the input buffer to the output buffer (`pump.dm:56-67`); the output buffer is thus counted twice (`:9-15`). The only throughput limit is the input buffer, one 200 L slice of the input network per tick.

**Filter.** Same rule on the pass-through outlet. The selected gas exits through the side port, and the filter stops when either outlet reaches target. It filters one gas from the "noteworthy" set; the default is plasma (`filter.dm:11-14,51-90,119`).

**Mixer.** nᵢ = cᵢ·ΔP·V_out/(R·Tᵢ). If an input runs short, both inputs scale by the same factor; the fractions sum to 1 (`mixer.dm:46-72,122-129`).

### 2.2 Vents and scrubbers

**Vent pump.** ΔP = min(10000, bound terms); check flag 1 uses the external (room) bound, flag 2 the internal (buffer) bound (`vent_pump.dm:145-160`). Blowing moves n = Δ·V_zone/(R·T_buf), so one vent with a full buffer can fill a whole room to its bound in one tick. Siphoning moves n = Δ·(V_buf + V_net)/(R·T_room), capped at the moles in a buffer-sized slice of the zone (`:115-131`). Idle blowing vents hibernate for 5-10 s (`:136-138`).

**Scrubber.** Each tick it samples min(1, rate/V_zone) of the zone's moles (`vent_scrubber.dm:221`). Scrubbing keeps the selected gases (default CO2 and plasma; `:20`); if the external check is on and the room is >0.05 kPa over the bound, it also keeps enough extra sample moles to remove the excess, and returns the rest (`:242-253`). Siphon keeps the whole sample; panic is siphon at 2000 L (`:268-281,302-319`). It stalls once its buffer reaches 50 atm (`:1,158-171`).

**Shared behaviour.** Both broadcast state on 1439 and register in their area's tables (`vent_pump.dm:181-212`; `vent_scrubber.dm:120-154`), accept only alarm-filtered signals there (`vent_pump.dm:214-221`; `code/controllers/radio_controller.dm:116-119`), clamp bounds to 0-50 atm (`vent_pump.dm:245-255`), and require area air-alarm access for multitool edits (`atmospherics.dm:387-399`).

### 2.3 Canisters and portables

**Canister.** 1000 L; spawns at 45 atm, half its 90 atm limit (`canister.dm:24,27,473-503`; `portable_atmospherics.dm:12`). With the valve open, Δ = min(P_release − P_env, (P_can − P_env)/2) and n = Δ·V/(R·T), where V is a fixed 2500 L into the room or the held tank's volume (`canister.dm:239-262`); release range 10-1013 kPa (`:441-446`). Health 100; at ≤10 it dumps everything; fire above 1273 K costs 5 per hit; overpressure never breaks it (`:204-229`). Contents self-ignite above plasma's flashpoint (`:282-283`).

**Portable pump.** Release: n = min(Δ-to-target over the room, P_int·300 L/(R·T)). Siphon: n = (target − P_int)·2000 L/(R·T_room), with no cap (`ALT/pump.dm:51-91`).

**Portable scrubber.** Samples ≤5000 L, moves ≤300 L-worth of the selected gases, and stops at 50 atm. The huge variant uses 20000 / 1200 L (`ALT/scrubber.dm:11-15,35-41,122-150`).

All portables can act on a held tank instead of the room, need no power, and run their own reactions when unconnected (`portable_atmospherics.dm:3,29-33`).

### 2.4 Heat exchange and generators

**HE pipe.** Each segment, every tick: below 0.01 mol per tile of room gas it "radiates", mixing its 70 L share with a virtual 30000 J/K body at 6.4 K (manifolds 24000 J/K); below 1 mol nothing happens; otherwise it takes 25% of one tile's room moles plus its share, equalizes their temperature and merges both back (`he_pipes.dm:1-3,12,98-150,231`). Thermal plates apply the same rule to 25% of their buffer (`unary/thermal_plate.dm:17-79`). These are the only heat paths between pipes and rooms.

**TEG.** Circulators move the gas needed to equalize their two sides, minus 5 kPa of friction (`circulator.dm:49-83`). The TEG takes Q = ΔT·C₁C₂/(C₁+C₂) from the hot stream, gives 0.35Q to the cold stream, and outputs 0.195Q as power, more with lubricant (`code/modules/power/generator.dm:117-156`).

**Turbine.** The compressor draws in a tenth of a tile of air per tick; output ∝ rpm^0.8 (`code/modules/power/turbine.dm:59-133`).

## 3. Air alarms, fire alarms and firedoors

**Sampling.** Each tick, an alarm reads its turf's zone mixture (`MCH/alarm.dm:456-469`). Its danger level is the worst of: total pressure, temperature, each "noteworthy" gas's partial pressure, and the summed partial pressure of all other gases (`:592-611`).

**Thresholds (TLV).** Each threshold is (min2, min1, max1, max2): at or beyond min2/max2 is danger 2, at or beyond min1/max1 is danger 1, −1 disables a bound, and edits keep the values ordered (`:52-91`). Human preset (`:194-209`), "–" = disabled bound:

| Reading | min2/min1/max1/max2 |
|---|---|
| O2 | 16/18/135/140 kPa |
| CO2 | –/–/5/10 kPa |
| Plasma | –/–/0.2/0.5 kPa |
| N2O | –/–/0.5/1 kPa |
| Radon | –/–/0.05/0.1 kPa |
| Other gases | –/–/0.5/1 kPa |
| Pressure | 81/91/111/122 kPa |
| Temperature | 243/273/313/343 K |

The Human target is 293 K and it scrubs CO2 and plasma. Other presets: Vox, Coldroom (90 K), Plasmaman, Vacuum (pressure max1/max2 1/5 kPa) and Fire Suppression. All are editable globally (`:211-304`).

**Roll-up.** Danger rises immediately but needs 5 consecutive lower readings to fall (`:516-531`). An area's level is the maximum over its working alarms; a manually triggered alarm counts as 2. At 2 the area alerts the AI, consoles and pagers and closes its firelocks, reopening them when the level drops (`code/game/area/areas.dm:151-201,220-248`). Alerts go out on 1437 (`alarm.dm:323,845-864`).

**Device control.** One master alarm per area collects device status and registers devices it hasn't seen (`:613-697`), then commands them by tag (`:717-731`). Modes (`:781-834`):

| Mode | Scrubbers | Vents |
|---|---|---|
| Scrubbing | preset gases, pressure check on (their own 1 atm bound, never changed) | blow to middle of the safe pressure band (101 kPa Human) |
| Replacement | panic | blow to target |
| Panic | panic | off |
| Cycle | panic until < 5% atm, then switch to Fill (`:533-542`) | off |
| Fill | off | blow with checks off; switch to Scrubbing at 95% of target |
| Fire | scrub, checks off | blow |
| Off | off | off |

Modes are manual, with one exception: auto fire suppression. If ≥25% of the area's floor is burning, the alarm switches to the Fire Suppression preset. It reverts once fires are out, temperature is within 2 K of target, and pressure is ≤105% of target (`:558-590`). Settings propagate to every alarm in the area (`:425-440,733-779`).

**Thermostat.** If the room is more than 2 K off target, the alarm takes a 625 L sample, moves ≤10 kJ toward target, and merges it back. It never cools below 233 K or heats above 363 K, and it draws energy/3 as power (`:26-37,471-514`). Base draw is 100 W, 200 W when active (`:316-318`).

**Fire alarm.** Raises the area fire alert when exposed to more than 473 K or when fire is on its turf (`:1357-1360,1497-1498`); that also closes the firelocks (`areas.dm:254-273`). It deploys an inflatable shelter if plasma exceeds 0.5 kPa (`alarm.dm:1478-1485`).

**Firedoors.** Each registers with its own area and every adjacent floor's area, so an alarm on either side closes it (`MCH/doors/firedoor.dm:136-148`). It never closes itself. While closed, each tick it computes the max − min pressure across neighbouring zones (edge firelocks: front and back only) and each side's temperature; a difference ≥25 kPa, or any side ≥50 °C or ≤0 °C, sets `lockdown` and shows warning overlays (`:50-52,572-626`; `code/game/objects/structures/false_walls.dm:13-51`). During an area alarm, a locked-down door refuses anyone without atmos, engineering or paramedic access, and any opening re-closes after 5 s (`:64,449-454,477-481,495-497`). Twin firedoors within 3 tiles open together when there is no alarm (`:150-168,464-465`). The alarm UI can override an area's firelocks (`alarm.dm:1145,1152`).

**Doors and ZAS.** Every door reports itself as a zone boundary: an open door still separates two zones, and a closed one blocks air completely (`MCH/doors/door.dm:437-445`; `ZAS/Atom.dm:23-35`). State changes mark the turf for a ZAS update, and a closed opaque or heat-proof door sets its turf's conductivity to 0 (`door.dm:488-514`). Exception: edge firelocks pass air when open and block only their facing edge when closed (`firedoor.dm:640-667`).

## 4. Mob interaction (humans)

**Breath.** `Life` runs every 2 s. Breathing happens every 4th air cycle (8 s), or on every `Life` while the last breath failed (`HUM/life.dm:139-140`). A breath is 0.5 L, taken from internals or else removed from the zone (`HUM/life/handle_breath.dm:39,53`; `DEF/atmos.dm:25-27`). Afterwards it goes back to the room, even if it came from a tank (`handle_breath.dm:100-101`).

A room breath with fewer than ⅕ or more than 5× the standard breath moles (0.0208 mol) has a 20% chance of lung damage and possible rupture. That means ambient pressure below about 20 kPa or above about 507 kPa at 20 °C (`:55-61`). Masks pre-warm the breath toward body temperature by (1 − conductivity) (`:90-93`).

**Lungs.** Breath volume is forced to 0.5 L, then the species' handlers run (`LNG/lung.dm:18-58`):

| Handler | Human gas | Threshold (kPa) | Effect |
|---|---|---|---|
| metabolizable | O2 | min 16 | r = min(1, pp/16). r = 1: heals 5 oxy, consumes half the O2 moles. r < 1: deals (1 − r) oxy, consumes r/2 (`LNG/lung_gasses.dm:63-83`; `code/modules/mob/living/carbon/species.dm:1,280-298`) |
| waste | CO2 | max 10 | consumed O2 exhaled as CO2, 1:1. Above 10 kPa for 12 s: paralysis + 1 oxy/breath; after 60 s, +3 more (`:91-124`) |
| toxic | plasma | 0.5 (5 masked) | toxin = clamp(pp/0.5 × 0.1, 1, 10) (`:132-169`) |
| sleep agent | N2O | 0.15 / 1 / 5 | giggle / paralysis / sleep; all N2O removed from the breath (`:176-197`) |
| radioactive | radon | 0.1 (5 masked) | 3 rads × pp/0.1 (`:201-230`) |

O2's 140 kPa maximum is never used, so there is no oxygen toxicity. With no breath at all, the mob takes 1 oxy damage per breath, 0.67 in critical condition (`handle_breath.dm:138-143`; `DEF/carbon_defines.dm:2-3`).

A breath below 220 K or above 360 K burns the head: cold does 0.5/1.5/3 below 220/200/120 K, heat 2/4/8 above 360/400/1000 K (`LNG/lung.dm:64-107`; `species.dm:67-73`). Vox breathe N2 and are poisoned by O2; plasmamen breathe plasma (`lung.dm:129-152`).

**Internals.** Requires a mask with the internals flag and a tank (searched in suit storage, back, belt, pockets, then hardsuit) (`code/modules/mob/living/carbon/internals.dm:7-23,43-48`; `handle_breath.dm:110-124`). Each breath takes n = P_dist·0.5 L/(R·T_tank). Distribution pressure ranges 0-304 kPa, O2 tanks start at 21.3 kPa, and if tank pressure drops below the setting, the setting is permanently lowered (`code/game/objects/items/weapons/tanks/tanks.dm:1-2,211-224`; `tank_types.dm:17`). An O2 tank (70 L at 6 atm, ~17.5 mol) lasts ~4000 breaths (~9 h); an emergency tank (2 L at 3 atm) ~57 breaths (~7.6 min) (`tank_types.dm:13-21,125-138`). Tanks leak above 30 atm, rupture above 40 atm after 3 integrity steps, and explode above 50 atm with range (P − 50 atm)/10 atm (`DEF/atmos.dm:85-88`; `tanks.dm:233-302`).

**Pressure.** Effective pressure = 1 atm ± max(0, |P − 1 atm| − species resistance)·(1 − c⁵). Here c is the fraction of the body covered by worn items whose `pressure_resistance` is at least |P − 1 atm|. Coverage weights: head 0.3, torso 0.3, arms plus hands 0.2, legs plus feet 0.2 (`HUM/life/life_helpers.dm:3-22,187-198`; `DEF/setup.dm:397-411`).

At ≥550 kPa the mob takes min((P/550 − 1)·4, 4) brute per tick; below 20 kPa, 2 brute (doubled on space turfs); HUD warnings appear at 325 and 50 kPa (`HUM/life/handle_environment.dm:46-64`; `DEF/setup.dm:52-55,86-88`). Space suits resist 5 atm and hardsuits 200 atm (`code/modules/clothing/clothing.dm:1081,1108`; `code/modules/clothing/spacesuits/rig.dm:26,129`). Without a helmet (c = 0.7), 83% of the pressure difference still applies.

**Body temperature.** Per tick, above 310.15 K the body sweats off ≤2 K. Skin temperature is 0.9 × body temperature in °C (`code/modules/mob/living/handle_hypothermia.dm:51-58`). If the room is colder than skin, loss = (1 − cold protection)·f·(T_skin − T_room)/200, where f = 1 at or below standard density (so vacuum chills as fast as air: "space is cold") and rises to at most 4.5 in denser gas. If hotter, gain = min((1 − heat protection)·ΔT/80, 10 K) (`handle_environment.dm:11-42`; `code/modules/mob/thermoregulation.dm:1-80`). Cold protection = Σ coverage × (1 − heat conductivity); space suits have conductivity 0 (`code/game/objects/items.dm:141-142`; `DEF/setup.dm:113`). Heat protection = coverage of items rated at or above the room temperature: 5000 K for space suits, 30000 K for firesuits (`life_helpers.dm:25-47`; `DEF/setup.dm:74-83`). Damage starts outside 220.15-360.15 K. Hypothermia stages begin at 34/32/28/24 °C (`handle_hypothermia.dm:3-16`). Mobs never change the room's temperature.

**Contamination (plasma only).** Plasma above 0.7 mol per tile contaminates unsealed clothing, at 0.02 toxin per item per tick, and burns unprotected eyes (`handle_environment.dm:66-67`; `ZAS/Plasma.dm:25-79`; `HUM/life/handle_chemicals_in_body.dm:26-31`).

**Burning mobs.** A burning mob is extinguished below 1 mol of O2 per tile. Otherwise it creates a 700 K hotspot, heats toward 373 K, and burns unprotected limbs (`code/modules/mob/living/living_defense.dm:317-347`; `HUM/life/handle_fire.dm:3-46`).

## 5. Atmos automation

The Atmospherics Automations Console runs a player-built expression tree every machinery tick: if/and/or, compare, sum, average and constants; 10 numeric registers; sensor and meter reads by tag and field; up to 5 linked assemblies; and setters for pumps, vents, scrubbers, injectors, valves, gates, coolers and emitters (`code/modules/atmos_automation/console.dm:1-64`; `statements.dm:197-600`; `implementation/*.dm`). Scripts export and import as JSON (`console.dm:320-337`). It is only a radio front end for the same commands alarms send. Worth replicating as an API for redstone or ComputerCraft, not as a custom visual language.

## 6. Config knobs, fictional gases and quirks

**Knobs.** Mostly compile-time defines (`DEF/atmos.dm`, `DEF/setup.dm:51-113`, `DEF/carbon_defines.dm`). Runtime ZAS settings: cloth contamination, plasmaguard-only, skin/eye burns, genetic corruption, contamination loss (`ZAS/NewSettings.dm:201-234`; `config-example/ZAS.txt`). `HEALTH_THRESHOLD_CRIT` affects breathing (`config-example/game_options.txt:4`; `handle_breath.dm:27`).

Dead knobs: `space_isnt_cold`, `PLASMA_DMG`, `PLASMA_HALLUCINATION` and `N2O_HALLUCINATION` are never read (`NewSettings.dm:182-246`); "space is cold" is hard-coded instead (`DEF/setup.dm:85`); the pressure-reduction coefficients and `HUMAN_NEEDED_OXYGEN` are unused (`DEF/setup.dm:90-91`; `DEF/atmos.dm:28`).

**Fictional gases.** Plasma drives lung toxicity, contamination, alarm presets, the default gas lists for scrubbers, filters and portable scrubbers (`vent_scrubber.dm:20`; `filter.dm:14`; `ALT/scrubber.dm:14`), fire-alarm shelters, canister self-ignition, and power setups that burn it. Cryotheum, oxygen agent B and volatile fuel are also fictional (`DEF/gases.dm`). O2, N2, CO2, N2O and radon are real and already handled.

**Quirks not to replicate.**
1. The volume pump uses max instead of min, so it always empties its input buffer (`volume_pump.dm:66-68`).
2. Filters and mixers flip `on` whenever power changes and never check power; freezers and heaters don't check either. Nearly every device draws 0 W (`filter.dm:44-53`; `mixer.dm:29-43`; `cold_sink.dm:18`; `atmospherics.dm:22-23`).
3. The pump counts its output buffer twice (`pump.dm:62`).
4. Pipes never leak, burst or exchange heat (§1).
5. Probable: an HE pipe that processes without a pipeline drops off the machine list permanently (`he_pipes.dm:66-68`; `pipes.dm:196-199`).
6. Heat-damage tiers are checked lowest first, so only tier 1 applies (`handle_environment.dm:72-86`).
7. The N2O handler's constructor sets the paralysis threshold twice and drops the sleep one (`lung_gasses.dm:181-185`).
8. The A/C mixes against its Celsius setpoint as if it were Kelvin (`ATM/hvac/chiller.dm:104`).
9. The TEG doesn't conserve energy; lubricant can push it past 100% (`generator.dm:130-145`; `heat_pump.dm:61-62`).
10. Canisters release into a fixed 2500 L while vents use zone volume; low-pressure damage doubles on space turfs vs station vacuum.
11. The interfloor vent shares the turf's cached air, not the zone mixture (`ALT/zvent.dm:22-29`); legacy.
