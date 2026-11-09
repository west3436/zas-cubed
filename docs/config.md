# Configuration

ZAS³'s settings come in three tiers (OPS-001), NeoForge's server, common and
client configs, split by who owns each setting. The content mod also has a
startup config, with one switch that turns its content off (CONT-155). Data that pack makers tune,
such as gases and ambients, lives in datapacks instead
([datapacks.md](datapacks.md)).

| Tier | File | Owner | Holds |
|---|---|---|---|
| Server | `config/zas-server.toml`; a copy in `<world>/serverconfig/` overrides it for that world | The server operator | Simulation tuning, the preset, admin settings |
| Common | `config/zas-common.toml` | The installation | Settings shared by client and server, such as thread counts |
| Client | `config/zas-client.toml` | Each player | Visuals and display units |
| Startup | `config/zas_atmos-startup.toml` | The installation, the same on the server and every client | Whether the content mod's content loads ([below](#content-mod-zas_atmos-startuptoml)) |

A dedicated server has no client config. A singleplayer world has all three.
The server config is per world: NeoForge reads `<world>/serverconfig/zas-server.toml`
if the world has one, and `config/zas-server.toml` otherwise.

## Server: `zas-server.toml`

| Key | Default | Meaning |
|---|---|---|
| `preset` | `NORMAL` | The atmosphere difficulty preset, `NORMAL` or `HARDCORE` (D-14, OPS-007). It sets the defaults of the options under `[preset_options]` ([below](#the-preset-and-its-options)) |
| `excluded_dimensions` | `[]` | Dimension ids excluded from simulation, in addition to ambient files with `"excluded": true` (ENV-007) |
| `[preset_options] <option>` | `"preset"` | Each option the preset sets: `"preset"` follows it, a value overrides it (OPS-014) |
| `[atmos] period_ticks` | `20` | The atmos tick period in game ticks, 2 to 200 (CORE-041). Rates are per second, so the period changes smoothness and cost, not behaviour |
| `[atmos] apply_delay_ticks` | `10` | Game ticks from closing a batch to applying its result; must be less than `period_ticks` |
| `[atmos] server_budget_ms` | `10` | Server-thread time per game tick for atmos work, in ms (CORE-047, NFR-002) |
| `[atmos] single_threaded` | `false` | **Restart-only.** Debug: run the simulation on the server thread. Results are identical (CORE-071) |
| `[atmos] conservation_checker` | `false` | Debug: run the invariant checker every atmos tick (CORE-073). `/zas checker` overrides it until the server stops |
| `[atmos] venting` | `VOLUME_AWARE` | How fast zones exchange gas through openings (D-21), `VOLUME_AWARE` or `FAITHFUL` ([below](#venting)) |
| `[atmos] delete_displaced_gas` | `false` | What happens to the gas in a cell a block fills: `false` keeps it in the zone, so the pressure rises (CORE-022), and `true` deletes it, as ZAS does, so the pressure stays as it was (CORE-029) |
| `[atmos] sealed_zone_dissolve_seconds` | `60` | How long a sealed room's air must match the ambient before its zone is dissolved, in seconds (CORE-052). A pinned zone, such as an air controller's, is never dissolved |
| `[atmos] enclosure_max_cells` | `102400` | The most cells a sealed space can have and still become a zone, 1 to 102,400 (GEO-020, D-23): by default a hall 5 × 5 chunks wide and 16 blocks high. A larger one counts as open air. A higher limit costs more worker time whenever a large roofed space or cave is checked, which the S-10 bench measures. Zones that formed under another limit stay as they are. The default was 65,536 until 2026-11-08, and a config file written before then keeps that value |
| `[heat] enabled` | `true` | Whether zones conduct heat through their walls, to and from heat sources, and radiate it to a vacuum (HEAT-001, HEAT-004) |
| `[heat] fallback_transmittance` | `1.7` | U in W/(m²·K) per block-thick layer of a block with none in a datapack, 0.001 to 1,000,000 (HEAT-015). 1.7 is stone's |
| `[heat] wall_thickness` | `3` | How many blocks deep a wall is searched for the space on its far side, 1 to 16 (HEAT-011). Past that, heat goes to the ground at the ambient temperature |
| `[heat] near_vacuum_percent` | `1.0` | A zone thinner than this percentage of standard air's density neither conducts nor radiates (HEAT-008) |
| `[heat] emissivity` | `0.9` | The emissivity of a wall's outer surface facing a vacuum, 0 to 1 (HEAT-004) |
| `[breathing] check_interval_ticks` | `40` | Game ticks between each living entity's breath and hazard checks, 1 to 1,200 (LIFE-007). Rates are per second, so the interval changes smoothness and cost, not how fast anything happens |
| `[breathing] max_checks_per_tick` | `100` | The most breath and hazard checks a server tick runs, over every dimension, 1 to 100,000 (LIFE-038). A check due beyond it waits for the next tick |
| `[breathing] oxygen_minimum_kpa` | `16.0` | The O₂ partial pressure of the breath below which the air meter drains (LIFE-003) |
| `[breathing] respiration_multiplier` | `1.0` | Multiplies respiration inside zones, about 1 mol of O₂ per breather per hour turned into CO₂ (LIFE-042); 0 turns it off. The same under both presets (D-27) |
| `[hazards] comfort_min_k` | `220.0` | The bottom of the temperature comfort band; below it frostbite harms (LIFE-011) |
| `[hazards] comfort_max_k` | `360.0` | The top of the temperature comfort band; above it burns harm (LIFE-011) |
| `[hazards] dimension_overrides` | `[]` | Per-dimension overrides of the hazards' switches and scales (LIFE-036), [below](#hazards) |
| `[hazards.<hazard>] enabled` | `true` | Whether the hazard harms anything (LIFE-031) |
| `[hazards.<hazard>] scale` | `1.0` | Multiplies the hazard's damage, 0 to 1,000 (LIFE-031) |
| `[airflow] enabled` | `true` | Whether pressure differences push entities (FLOW-014) |
| `[airflow] disabled_dimensions` | `[]` | Dimension ids where airflow is off; gas still flows there (FLOW-014) |
| `[airflow] range_blocks` | `9.0` | How far from an opening airflow reaches, 1 to 32 blocks; the push falls linearly to 0 there (FLOW-001, FLOW-010) |
| `[airflow] speed_at_1_atm` | `1.0` | The flow's speed next to an opening at a difference of 101.325 kPa, in blocks per game tick; it scales with the difference (FLOW-010) |
| `[airflow] max_speed` | `1.5` | The cap on the flow's speed, blocks per game tick (FLOW-002) |
| `[airflow] response` | `0.5` | The share of the gap between an entity's speed along the flow and the flow's closed each game tick, 0 to 1 |
| `[airflow] light_threshold_kpa` | `20.0` | The least difference that moves `#zas:airflow/light` (FLOW-011) |
| `[airflow] mob_threshold_kpa` | `65.0` | The least difference that moves players, mobs and `#zas:airflow/mob` (FLOW-011) |
| `[airflow] heavy_threshold_kpa` | `85.0` | The least difference that moves `#zas:airflow/heavy` (FLOW-011) |
| `[airflow] max_entities_per_tick` | `256` | The most entities airflow moves in a dimension per game tick; beyond it they take turns (FLOW-013) |
| `[airflow] pressure_fragile_threshold_kpa` | `150.0` | The pressure difference a block in [`#zas:pressure_fragile`](datapacks.md#pressure-fragile-blocks) holds, 0 to 100,000 kPa; it breaks at more than this (FLOW-007, FLOW-015). Whether blocks break at all is the preset option `block_pressure_damage`. `enabled` and `disabled_dimensions`, which are about pushing entities, don't change it |
| `[airflow] pressure_fragile_breaks_per_tick` | `32` | The most blocks a pressure difference breaks in a dimension per atmos tick, 1 to 4,096; the rest break in later ticks, if the difference is still there (FLOW-007) |
| `[fire] oxygen_minimum_fraction` | `0.14` | The least O₂ mole fraction any fire needs, 0 to 1: gas fires, vanilla fire, furnaces and campfires (FIRE-030). A gas fire at or above its fuel's autoignition temperature doesn't need it (FIRE-035). The fuel generator doesn't use the air (CONT-067) |
| `[fire] oxygen_minimum_kpa` | `10.0` | The least O₂ partial pressure any fire needs, in kPa, whatever the fraction, so nothing burns in a thin atmosphere (FIRE-030, FIRE-035) |
| `[fire] incomplete_below_fraction` | `0.18` | The O₂ fraction below which fuels burn incompletely: a share of their carbon forms CO instead of CO₂, rising linearly to all of it at `oxygen_minimum_fraction`, with the heat released reduced to match (FIRE-005) |
| `[fire] spread_chance` | `0.3` | The chance per second, 0 to 1, that a gas fire spreads to each neighbouring cell of its zone that is air and whose gas can burn (FIRE-016) |
| `[fire] block_ignition_temperature` | `573.0` | The gas temperature, in K, 0 to 100,000, at which a zone sets the flammable blocks beside it alight (FIRE-033). [Datapacks](datapacks.md#block-ignition) override it for some blocks, such as wool and leaves. The chance rises with the block's vanilla ignite odds and with how far the gas is above this |
| `[fire] hotspot_block_ignite_chance` | `1.0` | The chance per second, 0 to 1, that a gas fire lights a neighbouring block of vanilla ignite odds 100 (FIRE-034). A block's own odds scale it: wool, at 30, catches at 30 % of this |
| `[protection] head_coverage`, `chest_coverage`, `legs_coverage`, `feet_coverage` | `0.3`, `0.4`, `0.2`, `0.1` | The share of the body each armor slot covers, 0 to 1 (LIFE-024). Worn pieces rated for the pressure or temperature add up their slots' shares to the coverage c: (1 − c⁵) of the pressure difference from 1 atm and (1 − c) of temperature damage still reach the wearer (LIFE-020, LIFE-025). The defaults add up to 1 |
| `[commands] permission_level` | `2` | The permission level every `/zas` command needs, 0 to 4 (OPS-011) |
| `[profiling] log_interval_seconds` | `0` | Seconds between profiling reports in the server log; 0 is off (OPS-012) |

The sharing table and thresholds join the server config as their features
land in R1.

### When changes apply (OPS-016)

NeoForge reloads a config file when it changes on disk, so an edit made while
the server runs needs no restart. Every server setting, in this file and in
[`zas_atmos-server.toml`](#content-mod-zas_atmos-servertoml), is live unless
it is marked **Restart-only**. Nothing holds on to an old value, so whatever
reads a setting gets the new one the next time it runs:

| Settings | Apply from |
|---|---|
| `preset`, `[preset_options]`, `excluded_dimensions` | The next lookup; for the simulation, its next atmos tick |
| `[atmos]`, except `single_threaded` | The next atmos tick |
| `[heat]` | The next atmos tick. A change to the fallback or the thickness finds every wall's paths again |
| `[breathing]`, `[hazards]`, `[protection]` | Each entity's next check, up to `check_interval_ticks` away |
| `[airflow]` | The next game tick; which differences the engine reports and which blocks break, the next atmos tick |
| `[fire]` | The next check; for gas fires, the next atmos tick |
| `[commands]` | At once. A player's client only updates its command suggestions when it next joins or its op status changes, but the server decides who can run a command |
| `[profiling]` | At once |
| `zas_atmos-server.toml` | Each device's next step, or the next explosion; for `[gear]`, each wearer's speed within a second |

**Restart-only** settings keep the value they had when the server started, and
a reload doesn't change them: the two that choose the threads, `[atmos]
single_threaded` and the common config's `worker_threads`. In singleplayer,
leaving the world and loading it again counts as a restart. The content switch
in [`zas_atmos-startup.toml`](#content-mod-zas_atmos-startuptoml) is read
once when the game starts, so it needs the game itself restarted.

A value the config wouldn't accept takes its default when the file is read,
with a warning in the log. A file that doesn't parse leaves the values as they
are until it does.

### Hazards

Each hazard has its own `[hazards.<hazard>]` table with `enabled` and `scale`
(LIFE-031), named as its damage type: `hypoxia`, `toxic_gas`, `decompression`,
`barotrauma`, `frostbite`, `burns` and `airflow_impact`.

- **Off,** a hazard harms nothing and isn't listed among a verdict's hazards
  (API-014). With `hypoxia` off, a breath short of O₂ is breathable, so the air
  meter doesn't drain. With `toxic_gas` off, the gases' hazard profiles apply
  neither effects nor damage.
- **The scale** multiplies the hazard's damage. It doesn't change effects or
  the air meter: `toxic_gas` at 0 keeps the profiles' effects, and `hypoxia`
  scales the 2 HP/s an empty meter deals.

`[hazards] dimension_overrides` overrides a hazard's switch or scale in one
dimension (LIFE-036). Each entry is `"<dimension> <hazard> <setting>"`, where
the setting is `on`, `off` or a scale. Two entries for the same dimension and
hazard combine, so one can turn it on and the other scale it. An entry that
doesn't parse is dropped when the config loads. `/zas hazards` shows the
settings in effect where you are ([commands.md](commands.md#life-support)).

```toml
[hazards]
# Halve burns in the Nether, and turn frostbite off in the End.
dimension_overrides = ["minecraft:the_nether burns 0.5", "minecraft:the_end frostbite off"]

[hazards.decompression]
enabled = true
scale = 1.0
```

### Venting

`[atmos] venting` picks how the volumes on each side of an opening weight the
exchange through it (D-21). It is a plain option, not one the preset sets.

- **`VOLUME_AWARE`** (the default, CORE-035): the same openings drain a large
  room proportionally slower than a small one, whether into the open or into a
  much larger room. Through one opening into vacuum, a 27-cell room loses
  2.1 % of its air per second and a 1,000-cell room 0.065 %. A room drains
  into another room no faster than into the open, so two large rooms joined by
  one opening also take longer to even out: two 27-cell rooms halve their
  difference in about 18 s.
- **`FAITHFUL`** (CORE-068): ZAS's model, in which a room loses about the same
  fraction of its air per second whatever its size: 8.7 % for the 27-cell room
  and 8.2 % for the 1,000-cell one through one opening into vacuum, and 16 %
  into a much larger room. Two rooms joined by one opening halve their
  difference in about 4 s, whatever their size.

### The preset and its options

The preset (OPS-007) gives each option below a default. Under
`[preset_options]` each option is either `"preset"`, which takes the active
preset's default, or a value, which overrides the preset: it applies under
either preset and stays when the preset changes (OPS-014), even if it equals
the default. The comment on each option in the file gives its Normal and
Hardcore defaults, and `/zas preset` shows each option's value and whether it
comes from the preset or an override (OPS-015,
[commands.md](commands.md#preset-ops-007-ops-014-ops-015)). A value that isn't
valid for its option is reset to `"preset"` when the file loads, with a
warning in the log.

| Option | Normal | Hardcore | Meaning |
|---|---|---|---|
| `ambient_variant` | `NORMAL` | `HARDCORE` | Which variant of each dimension's ambient applies (ENV-010): Hardcore's Nether is hot and CO₂-rich and its End thin (ENV-002, ENV-012) |
| `combustion_forms_zones` | `false` | `true` | Whether a fire starting, or a furnace or campfire being lit, forms a zone in an enclosed, unzoned area: the blocks in `#zas:combustion` (CORE-056, FIRE-024) |
| `planting_forms_zones` | `false` | `true` | Whether placing a crop, sapling or leaves forms a zone in an enclosed, unzoned area: the blocks in `#zas:plants` (CORE-056, CONT-053) |
| `block_pressure_damage` | `false` | `true` | Whether a pressure difference breaks blocks in `zas:pressure_fragile`, such as glass (FLOW-007, FLOW-016) |
| `crop_photosynthesis_rate` | `0.125` | `0.0625` | The O₂ a plant in `#zas:crop_plants`, such as a crop or sapling, makes from CO₂ in a zone at light 15, in mol/h. It scales linearly with the light, from 0 at light 0 (CONT-053, CONT-057) |
| `leaf_photosynthesis_rate` | `0.0625` | `0.03125` | The same for a plant in `#zas:leaf_plants`, such as a leaf block (CONT-057) |
| `container_explosions_break_blocks` | `false` | `true` | Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107) |

Every option acts today. The space-mod compat mode (SPACE-004) joins in R2 with
the Ad Astra adapter (D-66).

Zone triggers that aren't options: Normal's triggers (CORE-055), API calls
(which include devices, leaks and `/zas gas`) and breaches of zones, form
zones under both presets. Hardcore adds combustion and planting; it doesn't
replace them. They are events, read when they happen. A block is a trigger when
it starts burning or is planted: fire appearing, a furnace, smoker, blast
furnace or campfire being lit, a crop, sapling or leaves being placed. It forms
a zone if its area is enclosed and unzoned and its option is on at that moment.
Growing, ageing, turning and going out don't count, and switching the preset
doesn't zone the fires and plants already in place: relight or replant them.
Candles and torches aren't combustion. Which blocks count is data, the block
tags `#zas:combustion` and `#zas:plants`
([datapacks.md](datapacks.md#block-tags)).

`/zas preset` can also switch the preset and set or clear an override, and
names the file it wrote:
- **A world with its own `serverconfig/zas-server.toml`:** the change goes
  there.
- **A singleplayer world without one:** the first change writes one. It holds
  the whole config with the change, and `/zas preset` says it made it. The
  shared `config/zas-server.toml` isn't touched, so the other worlds that read
  it keep their values. The change is already in effect, and later changes go
  to the copy too.
- **A dedicated server:** the change goes to the file it read, the world's copy
  if it has one, else `config/zas-server.toml`, as before.

A copy written by hand works the same way, and can be as short as one line,
`preset = "HARDCORE"`; NeoForge fills in the rest when the world loads.

For the rest of the session that made a copy, NeoForge still watches
`config/zas-server.toml`. Editing that file reloads the config from it, and ZAS³
then puts the copy's values back, so the world keeps its own. Edit the world's
copy instead, which applies from the next atmos tick. A player who joins over LAN in
that session is sent the shared file, as NeoForge sends the file it read; this
only matters for values a client reads, and none of the preset's options are
read by a client.

```toml
# The atmosphere preset, and an override that keeps Hardcore's Nether and End under Normal.
preset = "NORMAL"

[preset_options]
	ambient_variant = "HARDCORE"
	combustion_forms_zones = "preset"
```

## Content mod: `zas_atmos-server.toml`

`zas_atmos` has a server config of its own, per world like the framework's.
Its FE values are starting points for playtesting (D-37). A firelock draws
nothing (CONT-065), so it has no key. Every key is live
([When changes apply](#when-changes-apply-ops-016)). The design is in
[architecture §11](architecture.md#11-the-content-mod-zas_atmos).

| Key | Default | Meaning |
|---|---|---|
| `[power] vent` | `10` | What a vent draws while it runs, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] scrubber` | `15` | What a scrubber draws while it scrubs or siphons, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] scrubber_panic` | `30` | What a scrubber draws in panic, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] heater` | `60` | What a heater draws from its battery while it heats at full power, in FE/t, 0 to 100,000; in proportion below full power (CONT-087). No controller powers a heater (D-73) |
| `[power] heater_watts` | `5000` | The most heat a heater adds, in W (not FE/t), 0 to 1,000,000 (CONT-087). 0 stops heaters heating |
| `[power] air_conditioner` | `60` | What an air conditioner draws from its battery while it cools at full power, in FE/t, 0 to 100,000; in proportion below full power (CONT-087). No controller powers an air conditioner (D-73) |
| `[power] air_conditioner_watts` | `5000` | The most heat an air conditioner takes, in W (not FE/t), 0 to 1,000,000 (CONT-087). 0 stops air conditioners cooling |
| `[power] portable_scrubber` | `15` | What a portable scrubber draws from its battery while it moves gas, in FE/t, 0 to 100,000 (CONT-018). No controller powers a portable scrubber (D-73) |
| `[power] portable_pump` | `20` | What a portable pump draws from its battery while it moves gas, in FE/t, 0 to 100,000 (CONT-018). No controller powers a portable pump (D-73) |
| `[power] recharger` | `40` | What a recharger draws from its controller while its battery has room, in FE/t, 0 to 100,000 (CONT-113) |
| `[power] recharger_rate` | `28` | What a recharger puts into its battery, in FE/t, 0 to 100,000 (CONT-113) |
| `[power] igniter` | `20` | What an igniter draws from its controller while it has a redstone signal, in FE/t, 0 to 100,000 (CONT-105) |
| `[power] pressure_pump` | `20` | What a pressure pump draws from its controller while it is on, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] volume_pump` | `20` | What a volume pump draws from its controller while it is on, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] filter` | `20` | What a filter draws from its controller while it is on, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] mixer` | `20` | What a mixer draws from its controller while it is on, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] electrolyzer` | `80` | What a water electrolyzer draws from its controller while it is on, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] area_air_controller` | `5` | The area air controller's own draw, in FE/t, 0 to 100,000 (CONT-065). It pays this from its buffer before anything else; without it, it is unpowered |
| `[power] fire_alarm` | `1` | A fire alarm's draw from the controller that covers it, in FE/t, 0 to 100,000 (CONT-044, CONT-065) |
| `[power] airlock_controller` | `1` | An airlock controller's own draw from its area air controller, in FE/t, 0 to 100,000; the draws of the vents linked to it add to it (CONT-065, CONT-141) |
| `[power] area_air_controller_buffer` | `10000` | The controller's buffer, in FE, 1,000 to 1,000,000 (CONT-062) |
| `[power] central_controller` | `5` | The central atmospherics controller's own draw, in FE/t, 0 to 100,000 (CONT-115). It pays this before any area air controller's shortfall; without it, it is unpowered and pays nothing |
| `[power] central_controller_buffer` | `100000` | The central atmospherics controller's buffer, in FE, 1,000 to 10,000,000 (CONT-115) |
| `[power] generator_output` | `40` | What the generator makes while it burns, in FE/t, 0 to 100,000 (CONT-061) |
| `[power] generator_buffer` | `8000` | The generator's own store, in FE, 1,000 to 30,000. It starts no new fuel while the store is full |
| `[power] generator_seconds_per_fuel` | `80` | How long one coal burns, in seconds, 1 to 1,600. Other furnace fuels burn for this times their furnace burn time over coal's 1,600 ticks: a coal block 10 times as long, a stick 1/16 (CONT-066) |
| `[controller] breach_drop_kpa` | `10.0` | A fall in a controller's zone pressure within one atmos tick, in kPa, 0 to 1,000, that sends it to danger 2 at once, as a breach to space does. It counts only for a preset with a pressure min2 above 0, and only once the pressure is below min1. 0 turns it off (CONT-043) |
| `[central_controller] max_range_chunks` | `10` | How far a central atmospherics controller reaches from its own chunk, in chunks, 1 to 32: a square of chunks, at any height. It is also a new one's range, which a player may lower in its GUI. Lowering this lowers every range above it (CONT-116, CONT-117) |
| `[central_controller] force_load` | `true` | Whether a central atmospherics controller keeps its own chunk and the 8 around it loaded, as `/forceload` does. Turned off, each lets its chunks go at its next step (CONT-122) |
| `[containers] explosion_power_cap` | `16.0` | The most power a tank's or canister's explosion can have, 0 to 64 (CONT-109). TNT is 4 |
| `[gear] hardsuit_slowdown` | `0.1` | The share of movement speed each hardsuit piece worn takes away, 0 to 0.25 (CONT-089). At 0.1 the full suit leaves 60 %. 0 turns the slowdown off |
| `[radiators] conductance` | `20.0` | Each radiator pipe segment's conductance to the gas of its cell, in W/K, 0 to 10,000 (HEAT-020). 0 stops radiator pipe exchanging heat with gas; it still radiates in a vacuum |
| `[radiators] emissivity` | `0.9` | Each radiator pipe segment's emissivity in the open air of a vacuum, over 1 m², 0 to 1 (HEAT-021). 0 stops it radiating |

## Content mod: `zas_atmos-startup.toml`

ZAS³ ships as one jar that holds the framework, `zas`, and the content mod,
`zas_atmos` (D-88). This file turns the content off, for a pack that wants the
atmosphere simulation alone (CONT-155). With it off, `zas_atmos` registers no
block, item, menu or sound, and its data pack (recipes, loot and its additions
to the framework's tags) isn't loaded; `zas` runs as it does without the
content mod (CONT-096). The Ponder addon, if installed, then shows nothing.

NeoForge reads it once, at startup, before anything registers, so a change
needs a restart. It isn't synced: set the same value on the server and every
client. Turning the content off in a world that has its blocks and items
removes them from that world.

| Key | Default | Meaning |
|---|---|---|
| `[content] enabled` | `true` | **Restart-only.** Whether ZAS³ Atmospherics' blocks, items, recipes and loot load. `false` leaves the atmosphere simulation alone (CONT-155) |

## Common: `zas-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `worker_threads` | `1` | **Restart-only.** Atmos worker threads, 1 to 16 (architecture §3.1). Read when a server starts |

## Client: `zas-client.toml`

The simulation works in kPa and K (A-04); these change only how this client
shows values. Gas overlays, the HUD and their visual settings arrive in R1.

| Key | Default | Meaning |
|---|---|---|
| `[units] pressure` | `KPA` | Pressure unit: `KPA`, `ATM`, `BAR` or `PSI` |
| `[units] temperature` | `CELSIUS` | Temperature unit: `KELVIN`, `CELSIUS` or `FAHRENHEIT` |

They apply to the HUD, debug overlay, device windows and item tooltips. Operator
commands ([commands.md](commands.md)) and other messages the server sends always
report kPa and K.

## JVM system properties

Debug switches that act before any config is read. Add them to the server's
Java command line.

| Property | Meaning |
|---|---|
| `-Dzas.geometry.profile=true` | Debug: logs where the gas geometry derivation's time goes at start (fetching shapes, deriving them, probing positions, reading tags) and how long the started server waited for the table. It also lists the states worked out per position, by namespace, and the states whose shape failed, by block. Once the server has started, it times the derivation again, warm, with and without the per-shape cache ([spikes · S2](research/spikes.md#s2--seal-masks-and-volumes-for-every-block-state)). Off by default |
