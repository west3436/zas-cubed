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
| `preset` | `NORMAL` | The atmosphere difficulty preset, `NORMAL` or `HARDCORE` (D-14). For now it selects only the ambient variant; the full preset (OPS-007) arrives in R1 |
| `excluded_dimensions` | `[]` | Dimension ids excluded from simulation, in addition to ambient files with `"excluded": true` (ENV-007) |
| `[atmos] period_ticks` | `20` | The atmos tick period in game ticks, 2 to 200 (CORE-041). Rates are per second, so the period changes smoothness and cost, not behaviour |
| `[atmos] apply_delay_ticks` | `10` | Game ticks from closing a batch to applying its result; must be less than `period_ticks` |
| `[atmos] server_budget_ms` | `10` | Server-thread time per game tick for atmos work, in ms (CORE-047, NFR-002) |
| `[atmos] single_threaded` | `false` | Debug: run the simulation on the server thread. Results are identical (CORE-071) |
| `[atmos] conservation_checker` | `false` | Debug: run the invariant checker every atmos tick (CORE-073). `/zas checker` overrides it until the server stops |
| `[commands] permission_level` | `2` | The permission level every `/zas` command needs, 0 to 4 (OPS-011) |
| `[profiling] log_interval_seconds` | `0` | Seconds between profiling reports in the server log; 0 is off (OPS-012) |

The sharing table, thresholds and hazard toggles join the server config as
their features land in R1. Today the preset and excluded dimensions apply from
the next lookup, the `[atmos]` timing from the next atmos tick, the thread mode
when a dimension's simulation starts, and `[commands]` and `[profiling]` at
once. OPS-016's guarantee that every server setting applies without a restart
is R1.

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
