# Configuration

ZAS³'s settings come in three tiers (OPS-001), NeoForge's server, common and
client configs, split by who owns each setting. Data that pack makers tune,
such as gases and ambients, lives in datapacks instead
([datapacks.md](datapacks.md)).

| Tier | File | Owner | Holds |
|---|---|---|---|
| Server | `config/zas-server.toml`; a copy in `<world>/serverconfig/` overrides it for that world | The server operator | Simulation tuning, the preset, admin settings |
| Common | `config/zas-common.toml` | The installation | Settings shared by client and server, such as thread counts |
| Client | `config/zas-client.toml` | Each player | Visuals and display units |

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
| `[atmos] single_threaded` | `false` | Debug: run the simulation on the server thread. Results are identical (CORE-071) |
| `[atmos] conservation_checker` | `false` | Debug: run the invariant checker every atmos tick (CORE-073). `/zas checker` overrides it until the server stops |
| `[atmos] venting` | `VOLUME_AWARE` | How fast zones exchange gas through openings (D-21), `VOLUME_AWARE` or `FAITHFUL` ([below](#venting)) |
| `[breathing] check_interval_ticks` | `40` | Game ticks between each breathing entity's breath checks, 1 to 1,200 (LIFE-007). Rates are per second, so the interval changes smoothness and cost, not how fast anything happens |
| `[breathing] oxygen_minimum_kpa` | `16.0` | The O₂ partial pressure of the breath below which the air meter drains (LIFE-003) |
| `[breathing] respiration_multiplier` | `1.0` | Multiplies respiration inside zones, about 1 mol of O₂ per breather per hour turned into CO₂ (LIFE-042); 0 turns it off. The same under both presets (D-27) |
| `[commands] permission_level` | `2` | The permission level every `/zas` command needs, 0 to 4 (OPS-011) |
| `[profiling] log_interval_seconds` | `0` | Seconds between profiling reports in the server log; 0 is off (OPS-012) |

The sharing table, thresholds and hazard toggles join the server config as
their features land in R1. NeoForge reloads the file when it changes on disk,
so an edit made while the server runs applies without a restart: the preset,
its options and the excluded dimensions from the next lookup (for the
simulation, its next atmos tick), the `[atmos]` timing and venting model from
the next atmos tick, the thread mode when a dimension's simulation starts,
`[breathing]` from each entity's next breath check, and `[commands]` and
`[profiling]` at once. OPS-016's guarantee that every server
setting applies without a restart is R1.

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
| `combustion_forms_zones` | `false` | `true` | Whether a fire starting, or a furnace, campfire or the fuel generator being lit, forms a zone in an enclosed, unzoned area: the blocks in `#zas:combustion` (CORE-056, FIRE-024) |
| `planting_forms_zones` | `false` | `true` | Whether placing a crop, sapling or leaves forms a zone in an enclosed, unzoned area: the blocks in `#zas:plants` (CORE-056, CONT-053) |
| `block_pressure_damage` | `false` | `true` | Whether a pressure difference breaks blocks in `zas:pressure_fragile`, such as glass (FLOW-007, FLOW-016) |
| `crop_photosynthesis_rate` | `0.125` | `0.0625` | The O₂ a lit crop or sapling in a zone makes from CO₂ at light 15, in mol/h (CONT-057) |
| `leaf_photosynthesis_rate` | `0.0625` | `0.03125` | The same for a lit leaf block (CONT-057) |
| `container_explosions_break_blocks` | `false` | `true` | Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107) |

`ambient_variant`, `combustion_forms_zones` and `planting_forms_zones` act
today. The others are in the file and in `/zas preset` already, marked "not
used yet" with the requirement they wait for, and take effect when that
feature lands. The space-mod compat mode (SPACE-004) joins in R2 with the Ad
Astra adapter (D-66).

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
  it keep their values. The copy is read from the world's next load; until then
  the change is already in effect, and later changes go to the copy too.
- **A dedicated server:** the change goes to the file it read, the world's copy
  if it has one, else `config/zas-server.toml`, as before.

A copy written by hand works the same way, and can be as short as one line,
`preset = "HARDCORE"`; NeoForge fills in the rest when the world loads.

For the rest of the session that made a copy, NeoForge still watches
`config/zas-server.toml`. Editing that file reloads the config from it, and ZAS³
then puts the copy's values back, so the world keeps its own. Edit the world's
copy instead, which applies from its next load. A player who joins over LAN in
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
Its FE values are starting points for playtesting (D-37). The design is in
[architecture §11](architecture.md#11-the-content-mod-zas_atmos).

| Key | Default | Meaning |
|---|---|---|
| `[power] vent` | `10` | What a vent draws while it runs, in FE/t, 0 to 100,000 (CONT-065) |
| `[power] area_air_controller` | `5` | The area air controller's own draw, in FE/t, 0 to 100,000 (CONT-065). It pays this from its buffer before anything else; without it, it is unpowered |
| `[power] area_air_controller_buffer` | `10000` | The controller's buffer, in FE, 1,000 to 1,000,000 (CONT-062) |
| `[power] generator_output` | `40` | What the generator makes while it burns, in FE/t, 0 to 100,000 (CONT-061) |
| `[power] generator_buffer` | `8000` | The generator's own store, in FE, 1,000 to 30,000. It starts no new fuel while the store is full |
| `[power] generator_seconds_per_fuel` | `80` | How long one coal or charcoal burns, in seconds, 1 to 1,600 (CONT-066) |

## Common: `zas-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `worker_threads` | `1` | Atmos worker threads, 1 to 16 (architecture §3.1). Read when a server starts |

## Client: `zas-client.toml`

The simulation works in kPa and K (A-04); these change only how this client
shows values. Gas overlays, the HUD and their visual settings arrive in R1.

| Key | Default | Meaning |
|---|---|---|
| `[units] pressure` | `KPA` | Pressure unit: `KPA`, `ATM`, `BAR` or `PSI` |
| `[units] temperature` | `KELVIN` | Temperature unit: `KELVIN`, `CELSIUS` or `FAHRENHEIT` |

Operator commands ([commands.md](commands.md)) run on the server and always
report kPa and K.
