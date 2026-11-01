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
| `[commands] permission_level` | `2` | The permission level every `/zas` command needs, 0 to 4 (OPS-011) |
| `[profiling] log_interval_seconds` | `0` | Seconds between profiling reports in the server log; 0 is off (OPS-012) |

The sharing table, thresholds and hazard toggles join the server config as
their features land in R1. NeoForge reloads the file when it changes on disk,
so an edit made while the server runs applies without a restart: the preset,
its options and the excluded dimensions from the next lookup (for the
simulation, its next atmos tick), the `[atmos]` timing from the next atmos
tick, the thread mode when a dimension's simulation starts, and `[commands]`
and `[profiling]` at once. OPS-016's guarantee that every server setting
applies without a restart is R1.

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
| `combustion_forms_zones` | `false` | `true` | Whether fire, lit furnaces and campfires and the fuel generator form a zone in an enclosed, unzoned area (CORE-056, FIRE-024) |
| `planting_forms_zones` | `false` | `true` | Whether planting forms a zone in an enclosed, unzoned area (CORE-056) |
| `block_pressure_damage` | `false` | `true` | Whether a pressure difference breaks blocks in `zas:pressure_fragile`, such as glass (FLOW-007, FLOW-016) |
| `crop_photosynthesis_rate` | `0.125` | `0.0625` | The O₂ a lit crop or sapling in a zone makes from CO₂ at light 15, in mol/h (CONT-057) |
| `leaf_photosynthesis_rate` | `0.0625` | `0.03125` | The same for a lit leaf block (CONT-057) |
| `container_explosions_break_blocks` | `false` | `true` | Whether tank and canister explosions break blocks; they hurt entities either way (CONT-107) |

Only `ambient_variant` acts today. The others are in the file and in
`/zas preset` already, marked "not used yet" with the requirement they wait
for, and take effect when that feature lands. The space-mod compat mode
(SPACE-004) joins in R2 with the Ad Astra adapter (D-66).

Zone triggers that aren't options: Normal's triggers (CORE-055), API calls
(which include devices, leaks and `/zas gas`) and breaches of zones, form
zones under both presets. Hardcore adds combustion and planting; it doesn't
replace them.

`/zas preset` can also switch the preset and set or clear an override. It
writes the file the server read, which is the world's
`serverconfig/zas-server.toml` if there is one and `config/zas-server.toml`
otherwise; `/zas preset` names it. A singleplayer world without its own copy
shares `config/zas-server.toml` with every other such world, so to keep a
world's preset to itself, put a copy in its `serverconfig/` folder first. A
copy can be as short as one line, `preset = "HARDCORE"`; NeoForge fills in the
rest when the world loads.

```toml
# The atmosphere preset, and an override that keeps Hardcore's Nether and End under Normal.
preset = "NORMAL"

[preset_options]
	ambient_variant = "HARDCORE"
	combustion_forms_zones = "preset"
```

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
