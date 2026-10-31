# Space mods and room-atmosphere prior art

> **Researched:** 2026-10-30. Download counts are Modrinth (MR) plus
> CurseForge (CF, via the cfwidget API) on that date. Versions and dates come
> from the Modrinth API unless noted. Source claims cite the branch or tag
> read; clones were read only, never built. "(code-read)" marks behavior
> inferred from source, not tested in game. **Clean-room note:** behavior is
> described in prose; no third-party code is reproduced. Class and method
> names are cited as API facts.

## Implications for zas-cubed

- **The shared "atmosphere API" niche is empty on 1.21.1.** No library mod
  offers a neutral oxygen or atmosphere API (Modrinth library search and web
  search, 2026-10-30). Each space mod keeps a private model, and only Ad
  Astra's is shaped like an API. Players want a shared one: Northstar
  [#60](https://github.com/Astronauts-of-Create/Northstar-Redux/issues/60)
  asked for its sealer to work in other mods' dimensions, and
  [#185](https://github.com/Astronauts-of-Create/Northstar-Redux/issues/185)
  asks for modpack-facing oxygen and cold immunity effects. zas-cubed should
  ship its API **and** first-party adapters, because nobody will integrate
  first.
- **Reuse the conventions that already exist.**
  - NeoForge's `LivingBreatheEvent` is the de-facto arbitration point.
    Northstar, Cosmonautics and Deep Air all decide breathing there.
  - The `#c:oxygen` fluid tag is shared by Ad Astra, Mekanism, Create: Deep
    Seas, Northstar (via `#northstar:breathable`) and AR Beyond. Tag
    zas-cubed fluids the same way, plus `#c:hydrogen` and `#c:nitrogen`.
  - Mekanism's `mekanism:chemical_handler` capability is the gas I/O
    standard for pipes and canisters.
  - Seed each dimension's ambient reservoir from existing data: Sable
    `dimension_physics` (`base_pressure`, 0 = vacuum), Northstar
    `planet_dimension` (gas composition and pressure) and Ad Astra `planets`
    (oxygen, temperature, gravity). zas-cubed's own datapack wins.
- **Tier-1 compat is API-only.** Ad Astra (public events), Northstar
  (register a `Provider`) and Cosmonautics (a `LivingBreatheEvent` listener)
  need no mixins. Stellaris and Creating Space need one or two mixins into
  internal classes, so they are tier 2.
- **Make their "is there oxygen here?" agree; don't cancel their damage.**
  Northstar hurts entities directly inside its breathe handler, and Ad Astra
  and Stellaris tick damage from their own mixins. The robust hook is each
  mod's oxygen query. Offer a separate "replace" mode where the API allows it
  (Ad Astra's tick events can be vetoed).
- **Seal detection (details in B).** Seal per face from collision shapes,
  overridable by tags and a block interface. Invalidate from a universal
  block-change hook (a mixin on `ServerLevel#onBlockStateChange`, which Cold
  Sweat 1.21 already uses), not from player events. Bound and amortize every
  flood fill, keep the old topology live while recomputing, and never sync
  per-block sets to clients. Stay on the main thread unless working from
  section snapshots: AR 1.12's live-world worker threads crashed and
  deadlocked, and Galacticraft disabled its own sealer thread.
- **Unloaded chunks: freeze, don't guess.** Existing mods read them with
  plain `getBlockState`, which can load chunks synchronously (Ad Astra,
  Northstar, GC5; code-read, not tested), treat unloaded chunks as solid
  (Galacticraft Legacy, Creating Space: false seals) or treat them as leaks
  (AR 1.12, AR Beyond: bases vent at the view-distance edge). Suspend zones
  and edges that touch an unloaded chunk, as SSC14 does.
- **Players asked for this design.** Ad Astra
  [#580](https://github.com/terrarium-earth/Ad-Astra/issues/580) asks for
  enclosure-based rooms instead of distributor "bubbles in the void", citing
  abysmal performance with many distributors.
- **Licences.** Ad Astra code is MIT (Terrarium License v1; assets are ARR),
  and Northstar Redux and Creating Space are MIT. Stellaris
  (CC-BY-NC-SA-4.0), Cosmonautics (GPL-3.0+) and AR3 (ARR) should be touched
  only from optional compat code; never copy their code into MIT zas-cubed.
- **Version drift.** Stellaris 2.x, Tough As Nails, Thermoo and Serene
  Seasons are moving to MC 26.x, and their 1.21.1 lines may freeze. Keep
  each adapter in its own optional module.

## Recommended compat targets

| Tier | Target (NeoForge 1.21.1) | Hook | Risk |
|---|---|---|---|
| 1 | Ad Astra 1.16.26 | `AdAstraEvents.EntityOxygenEvent`, `Hot/ColdTemperatureTickEvent`, `EnvironmentTickEvent`; read `OxygenApi`, `PlanetApi` | Low: public `api` package |
| 1 | Create: Northstar – Redux 0.6.6 | register a `NorthstarOxygen.Provider` per level; read `planet_dimension` | Medium: 0.x beta, classes outside `api/` |
| 1 | Create Cosmonautics 26.08.307 | `LivingBreatheEvent` at `HIGH` → `setCanBreathe(true)` | Low: no compile dependency |
| 1 | Mekanism 10.7.x and `#c:` fluid tags | `mekanism:chemical_handler` capability; `#c:oxygen` | Low |
| 1 | Sable `dimension_physics` | read `base_pressure` as a fallback ambient | Low: datapack format |
| 2 | Stellaris 1.4.25 | mixins on `DimensionOxygenManager#breath` and `#hasOxygenAt` | High: internals; 2.x renames packages |
| 2 | Creating Space 1.7.23 | `@ModifyReturnValue` on `CSEventHandler#inO2` | Medium |
| 2 | Cold Sweat, Tough As Nails, LSO, Thermoo family | temperature providers (section C) | Low to medium |
| 2 | ThinAir: ReLived, Thick Air | mixin into the air-quality helper | Medium |
| 2 | Advanced Rocketry 3, AR Beyond, Create: Deep Seas, Sable sub-levels | watch; moving structures are out of scope | — |
| Out | Galacticraft 5, Galacticraft Legacy, Beyond Earth, Cosmic Horizons/Starlance, Beyond Oxygen, Not Enough Oxygen, original Northstar, Stellaris 2.x, SSC14 | not on NeoForge 1.21.1 | — |

## A. Space and planet mods

### A1. Available on NeoForge 1.21.1

**Ad Astra** ([Modrinth](https://modrinth.com/mod/ad-astra),
[source](https://github.com/terrarium-earth/Ad-Astra/tree/1.21.1)):
1.16.26 (2026-09-13), MR 6.13M + CF 42.6M downloads, by far the largest.
Terrarium License v1. Read: branch `1.21.1` @ `45abf0e`.
- *Oxygen:* the distributor runs `FloodFill3D`, a 6-neighbour BFS from the
  block it faces, on the main thread every `distributionRefreshRate` = 100
  ticks, capped at `maxDistributionBlocks` = 6000. Filled positions go into a
  per-level SavedData (`adastra_planet_data`, a `HashMap<BlockPos, …>` saved
  on every autosave) with oxygen on and 22 °C. Hitting the cap in an
  airless dimension spawns an "air vortex". There is no loaded-chunk check.
- *Sealing (code-read):* air and `#ad_astra:passes_flood_fill` (fences,
  leaves, ladders, iron bars) pass. `#ad_astra:blocks_flood_fill` (empty by
  default) and full collision cubes seal. Other shapes use a face-coverage
  heuristic: closed doors, trapdoors and slab floors seal; open doors,
  fences and bars leak.
- *Entities:* a `LivingEntity#tick` mixin checks every 20 ticks at eye
  height. Entities pass with an exemption tag (`ad_astra:lives_without_oxygen`,
  `can_survive_in_space`), a full suit holding more than 1 mB, or oxygen at
  the position; otherwise they take 2 damage. Temperature is checked at the
  feet (liveable −50 to 70 °C). Gravity rescales motion in a `travel` mixin,
  without attributes. Chunk-tick mixins put out fires, freeze or evaporate
  water and kill grass.
- *API:* `earth.terrarium.adastra.api.systems.{OxygenApi, TemperatureApi,
  GravityApi}` and `api.planets.PlanetApi`, each a `static API` loaded by
  ServiceLoader, so it can't be replaced. Key calls: `hasOxygen(Level,
  BlockPos)`, `hasOxygen(Entity)`, `setOxygen(Level, Collection<BlockPos>,
  boolean)`, `getTemperature(…)`, `isLiveable(Level, BlockPos)`,
  `getGravity(…)`. `api.events.AdAstraEvents` holds functional events with
  a static `register`: `OxygenTickEvent`, `EntityOxygenEvent (Entity,
  boolean) → boolean`, `TemperatureTickEvent`, `Hot/ColdTemperatureTickEvent`,
  `EntityGravityEvent (Entity, float) → float`, `EnvironmentTickEvent` (per
  block) and `AcidRainTickEvent`. Planets are datapack JSON
  (`data/<ns>/planets/`: `dimension`, `oxygen`, `temperature`, `gravity`,
  `solar_power`, `tier`, …).
- *Compat:* register `EntityOxygenEvent` (true inside breathable zones; this
  also stops suit drain and fixes the HUD), plus `Hot/ColdTemperatureTickEvent`
  and `EnvironmentTickEvent` (false inside comfortable zones). For "replace"
  mode, veto `OxygenTickEvent` and `TemperatureTickEvent`. In the reverse
  direction, `hasOxygen(level, pos) && !hasOxygen(level)` marks an Ad Astra
  distributor area. Torches inside zones need extra work, because Ad Astra
  cancels `EntityPlaceEvent` for `destroyed_in_space` blocks where it sees no
  oxygen. The Giselle addon (8.x, 1.21.1) already integrates through these
  events, so the route is proven. Risk: low.
- *Player-reported problems:* 6000-block distributors flood clients with sync
  packets ([#434](https://github.com/terrarium-earth/Ad-Astra/issues/434));
  corner stairs leak ([#624](https://github.com/terrarium-earth/Ad-Astra/issues/624));
  raising the radius config breaks the machines
  ([#655](https://github.com/terrarium-earth/Ad-Astra/issues/655));
  overlapping distributors double-count against the limit
  ([#275](https://github.com/terrarium-earth/Ad-Astra/issues/275)); and the
  bubble-not-room design plus the cost of many distributors
  ([#580](https://github.com/terrarium-earth/Ad-Astra/issues/580)).

**Create: Northstar – Redux**
([Modrinth](https://modrinth.com/mod/northstar-redux),
[source](https://github.com/Astronauts-of-Create/Northstar-Redux)):
0.6.6+1.21.1 beta (2026-09-23; also Forge 1.20.1), MR 300K + CF 434K. MIT.
Read: tag `v0.6.6+1.21.1` @ `76efb5b`.
- *Sealer:* a Create kinetic block running a face-connected BFS from the
  block above it, spread over ticks (128 blocks per tick by default, at most
  4096 per tick).
  Capacity is 16 blocks per RPM (`oxygen.blocksPerRpm`); going over counts as
  a leak (the set is cleared and leak particles show). It re-floods 20 ticks
  after finishing and keeps the old result valid meanwhile. The result is a
  `LongOpenHashSet` in the block entity, not saved. Main thread, no
  unloaded-chunk guard. It works on contraptions and Sable sub-levels.
- *Sealing:* per face. A block's `SealableBlock#northstar$isFaceSealed(…)`
  override wins, then `#northstar:blocks_air`, then "the outline shape's face
  is full and the block isn't in `#northstar:air_passes_through`". Slabs and
  stairs seal on their full faces, fences leak and closed doors seal by
  shape, but the tagged wooden and iron doors, trapdoors, ladders and leaves
  always leak.
- *Entities:* `NorthstarOxygen.onBreathe(LivingBreatheEvent)`. Entities pass
  with the `#northstar:doesnt_require_oxygen` tag, a sealer at eye height, or
  a full `#northstar:oxygen_sealing` suit with an `#northstar:oxygen_sources`
  tank. Otherwise it calls `hurt(…, 1)` itself, so another listener's
  `setCanBreathe(true)` can't save the entity. Entities freeze below −32 °C
  and burn above 300 °C; gravity is per dimension.
- *Extension points (public, but outside `api/`):*
  `NorthstarOxygen.getDimension(Level)` returns an object with
  `registerSealer(Provider)`, `unregisterSealer`, `hasOxygen(Vec3|Vec3i)`,
  `getSealer(pos)` and `enqueueUpdates(LongCollection)`.
  `NorthstarOxygen.Provider` extends `SealingProvider { isSealed(Vec3);
  isSealed(Vec3i); drainOxygen(float) }`. `NorthstarTemperature.Provider`
  takes no position, and overlapping providers are averaged. The datapack
  registry `northstar:planet_dimension` has `atmosphere {composition:
  [{fluid, breathable}], pressure_atm | pressure_pa}`, `gravity`,
  `temperature` and `is_orbit`.
- *Compat:* register one `Provider` per level whose `isSealed` is an O(1)
  zone lookup (Northstar loops over every provider), map `drainOxygen` to the
  zone's O₂, and call `enqueueUpdates` when zones change. Making a
  depressurised room in a breathable dimension lethal needs a mixin on
  `hasOxygen`. Risk: medium. Avoid the pattern used by "Mekanism x Create:
  Northstar", which mixes into `ItemStack#is(TagKey)` to fake suit tags.

**Create Cosmonautics** (modid `rocketnautics`;
[Modrinth](https://modrinth.com/mod/create-cosmonatics),
[source](https://github.com/CosmonauticsTeam/Create-Cosmonautics)):
26.08.307 beta (2026-08-09), MR 134K + CF 81K. GPL-3.0+. Needs Sable and
Create Aeronautics. Read: tag `26.08.307` @ `53730e0`.
- It has no sealer, rooms or temperature. Vacuum depends on dimension and
  altitude (bands in the `universe_planets` datapack). A `LivingBreatheEvent`
  handler at `HIGHEST` sets `setCanBreathe(false)` and vanilla drowning does
  the damage; Create's diving helmet and backtank are the life support.
  Gravity comes from Sable `dimension_physics` through an `Entity#getGravity`
  mixin.
- Prior art: **Cosmonautics: Deep Air**
  ([Modrinth](https://modrinth.com/mod/cosmonautics-deep-air), MIT, MR 92K)
  sets `setCanBreathe(true)` at `HIGH` inside sealed, unbreached Create: Deep
  Seas compartments that have a powered oxygen diffuser.
- *Compat:* the same pattern, with no compile dependency. Expressing
  zas-cubed vacuum as `setCanBreathe(false)` also makes Create diving gear
  work automatically. Risk: low.

**Stellaris** ([Modrinth](https://modrinth.com/mod/stellaris),
[1.21.1 source](https://github.com/st0x0ef/stellaris/tree/1.21)): 1.4.25
(2026-07-09) for Fabric and NeoForge (Architectury); 2.0.9 (2026-10-21)
targets MC 26.1.2. MR 207K + CF 1.66M. CC-BY-NC-SA-4.0. The Modrinth source
link points to the 2.x rewrite; the 1.21.1 code is in `st0x0ef/stellaris`,
branch `1.21` @ `b5f723c`.
- *Oxygen rooms:* a BFS through `isAir()` blocks only, so every non-air
  block (torch, fence, open door) is a wall. It is bounded by a 32-block cube
  around the distributor (`maxOxygenRoomSize`), and leaving the cube marks
  the room open. It costs 1 oxygen and 3 energy per newly filled block and
  runs every 21 ticks, on the main thread, in memory. Breathing is checked at
  the feet. Temperature is display-only. Gravity sets vanilla attribute base
  values (`GRAVITY`, `SAFE_FALL_DISTANCE`, `FALL_DAMAGE_MULTIPLIER`).
- *API:* none, and no events. Internals:
  `GlobalOxygenManager.getInstance().getOrCreateDimensionManager(ServerLevel)`
  returns a `DimensionOxygenManager` with `breath(LivingEntity)` and
  `hasOxygenAt(BlockPos)`.
- *Compat:* a cancellable HEAD mixin on `breath` plus `@ModifyReturnValue` on
  `hasOxygenAt`. Read back through `hasOxygenAt`, never `breath`, which
  consumes oxygen. Risk: high, and the 2.x rewrite renames every package.

**Creating Space** ([Modrinth](https://modrinth.com/mod/creating-space),
[source](https://github.com/RealAntEngineer/creating_space)): 1.7.23
(2026-10-21), MR 80K. MIT. Read: tag `v1.21.1-1.7.23` @ `ef41712`.
- The room pressuriser spawns a `RoomAtmosphere` entity whose shape is a list
  of non-overlapping boxes grown by `searchTopology`. It searches 1000 blocks
  per tick, and a room is open if its volume reaches 1000 × the number of
  sealers. It rebuilds on `NeighborNotifyEvent` with a 5-tick cooldown and
  saves the shape in the entity's NBT. Sealing uses Create's fan-style shape
  test: closed doors, trapdoors and slabs block across their panel, while
  fences, lone panes and open doors pass. Unloaded chunks count as solid, so
  rooms can falsely seal.
- *API:* none (`OxygenSuffocationEvent` exists but is never posted). The
  entity check is `CSEventHandler.inO2(LivingEntity)`, every 20 ticks, for 2
  damage; suits need `#creatingspace:spacesuit` pieces and an oxygen tank.
  Venus adds overheat damage. Gravity comes from the
  `creatingspace:rocket_accessible_dimension` datapack and deliberately skips
  Ad Astra dimensions.
- *Compat:* one `@ModifyReturnValue` on `inO2`, which also covers Venus heat.
  Risk: medium (one internal static method).

**Smaller or unreleased.**
[Advanced Rocketry 3](https://modrinth.com/mod/advanced-rocketry-3)
(3.0.beta, 2026-08-19, MR 2.6K, ARR;
[source](https://github.com/dercodeKoenig/marvins-minecraft-mods/)) is a
rewrite. Planets carry a gas inventory (pressure, O₂ share, CO₂,
temperature), and indoors each dimension tracks separate oxygen and pressure
"supplier" flood fills. Each supplier gets 1000 blocks, budgets pool when
fills meet, and a full rescan runs every 5 s on the main thread. Its seal
queries
are public statics (`LifeSupportSystem.isOxygenSupplied/isPressurized(Level,
BlockPos)`), not an API package.
[Advanced Rocketry Beyond](https://github.com/breakinblocks/Advanced-Rocketry-Beyond)
is an MIT rewrite for 1.21.1 with no release yet (last push 2026-10-19).
[Gregicality Rocketry](https://modrinth.com/mod/gcyr)'s 1.21.1 build
(mc1.21.1-0.2.4) is a stale 2024-09-28 beta.
[NeoAeris](https://www.curseforge.com/minecraft/mc-mods/neoaeris)
(0.2.0-beta, 2026-06-20) has 196 CF downloads. The Expanse, Deep Space and
Space Explore each have under 10K and were not assessed.

**Adjacent mods (not space mods).** [Sable](https://modrinth.com/mod/sable)
(2.0.6, 2026-10-03, MR 6.8M, PolyForm Shield) defines
[`dimension_physics`](https://github.com/ryanhcode/sable/blob/HEAD/wiki/Dimension%20Physics%20Data.md)
JSON with `base_gravity`, `base_pressure` (0 = vacuum) and an altitude
`pressure_function`.
[Create: Deep Seas](https://www.curseforge.com/minecraft/mc-mods/create-deep-seas)
(CF 1.1M) seals submarine compartments, which is a moving-structure case and
out of scope. [ThinAir: ReLived](https://modrinth.com/mod/thinair-relived)
(1.0.6, 2026-10-10) and [Thick Air](https://modrinth.com/mod/thick-air)
(1.1.2, 2025-12-29) port Fuzss's altitude-based Thin Air.

### A2. Not on NeoForge 1.21.1 (out of reach)

- **Galacticraft 5** ([source](https://github.com/TeamGalacticraft/Galacticraft),
  MIT): Fabric, MC 1.21.1, `5.0.0-prealpha`. It has no releases or tags,
  only CI artifacts (last commit 2026-10-26), and no official Modrinth or
  CurseForge listing was found (unofficial forks exist). The
  README says Forge work starts after a survival-playable Fabric alpha, and
  there is no NeoForge branch. Its API is the closest design cousin:
  `dev.galacticraft.api.gas.GasComposition` holds per-gas ppm plus
  temperature and pressure, but only `breathable()` (19.5–23.5 % O₂) affects
  play.
- **Galacticraft Legacy** (Forge 1.12.2, MIT) and its addons (Galaxy Space,
  More Planets, ExtraPlanets), and **Advanced Rocketry** / AR Reworked
  (1.12.2).
- **Beyond Earth** (Forge up to 1.20.1; last update 2024-04-14).
- **Cosmic Horizons** (Forge 1.20.1, ARR, MR 300K; last update 2025-01-05),
  with **Starlance** (Valkyrien Skies bridge, Forge 1.20.1, MR 120K) and the
  Cosmic Additions oxygen addon.
- The original **Create: Northstar** (Forge up to 1.20.1, last update
  2024-08-09), superseded by Redux.
- **Beyond Oxygen** (Forge 1.20.1–1.20.6, GPL-3.0) and **Not Enough Oxygen**
  (Forge 1.20.x): standalone vent-and-tank oxygen mods that work on Valkyrien
  Skies ships and hook Ad Astra through `EntityOxygenEvent`.
- **Ad Astra: Planets+** (1.20.1, 1.20.4) and Fuzss's **Thin Air** (up to
  1.20.4, archived).
- **Stellaris 2.x** (MC 26.1.2) and **Space Station Craft 14** (NeoForge
  1.21.8 and 26.1.2) are future-version targets.
- **NTM: Space** (1.7.10) and NTM CE: Space (1.12.2).

## B. Prior art: rooms and gas in voxel worlds

| Mod | Detection | Bound | Cadence and thread | Non-full blocks | Unloaded chunks |
|---|---|---|---|---|---|
| Galacticraft Legacy (1.12) | layered BFS from sealer; air stored as real blocks | 1250 checks per sealer (walls count) | every 75+ ticks, one sealer per tick, main | per face: `IPartialSealableBlock`, slab halves, else `isSideSolid` | treated as solid |
| Galacticraft 5 (Fabric) | BFS from sealer; bit per cell per section | 1024 × merged sealers | every 20 ticks, main | full collision or `galacticraft:sealable` | not checked |
| Advanced Rocketry (1.12) | DFS "blob" per vent | ≈137k blocks (`oxygenVentSize` 32) | hook in `World.setBlockState`, thread pool | whole block only + lists | treated as open |
| Advanced Rocketry 3 | supplier flood fills | 1000 per supplier, pooled | rescan every 5 s, main, double-buffered | panel spans cross-section | not checked |
| Ad Astra 1.21.1 | BFS from distributor | 6000 blocks | every 100 ticks, main | full cube + tags + coverage heuristic | not checked (loads) |
| Northstar Redux | progressive BFS | 16 blocks/RPM | 128 blocks/tick, double-buffered | per-face full-face test + tags + interface | not checked |
| Stellaris 1.4 | BFS over air only | 32³ cube | every 21 ticks, main | any non-air block seals | not checked |
| Creating Space | box growth | 1000 × sealers | on neighbour update, 1000/tick | fan-style panel test | treated as solid |
| Not Enough Oxygen, Beyond Oxygen | layered BFS from vent | `ventRange` 2048 | on block events, main | `isFaceSturdy` per face + thin-panel check | not checked |
| AR Beyond (unreleased) | BFS | sphere volume or radius | 20 ticks after a change, 600-tick fallback | full cube + tags + closed airlock | treated as a leak |
| SSC14 (SS14 port) | gas per cell, 16³ regions | 2048 cells per region step | every 4 ticks, up to 8 regions | permeability per block state | regions suspended |

Notes on the rows:
- **Galacticraft Legacy**
  ([source](https://github.com/TeamGalacticraft/Galacticraft-Legacy),
  `master-1.12` @ `acec442`). `ThreadFindSeal` runs on the server thread
  despite its name; the threaded path is commented out. Sealers that reach
  each other's vents pool their budgets, and leaks are traced back to the
  player (up to 90 blocks). `api.block.IPartialSealableBlock.isSealed(World,
  BlockPos, EnumFacing)` lets blocks declare sealed faces. Unloaded chunks
  read as bedrock. Old-tracker history
  ([micdoodle8/Galacticraft](https://github.com/micdoodle8/Galacticraft/issues)):
  the recursive first version overflowed the stack (#286); storing air as
  blocks caused write races and an OutOfMemoryError (#1468, #2228); fire in
  a sealed room hung the server (#1849); and 90 sealers over about 103k
  blocks cost about 60 ms every 7 s (#363).
- **Galacticraft 5** (`main` @ `9dddd4a`) keeps one bit per cell in each
  chunk section, meaning "inverted from the dimension default". It is saved
  in chunk NBT and sent with the chunk packet, so lookups are O(1) and state
  is right on login. Open bug
  [#625](https://github.com/TeamGalacticraft/Galacticraft/issues/625): two
  sealers in one room fail to seal after merging. An incremental "Oxygen 2"
  design (PR #517) was closed unmerged on 2026-02-28.
- **Advanced Rocketry 1.12**
  ([source](https://github.com/Advanced-Rocketry/AdvancedRocketry), `1.12`
  @ `c5cd5af`). An ASM patch calls `AtmosphereHandler.onBlockChange` from
  every `World.setBlockState`, and a thread pool reads, and even writes, the
  live world without snapshots. Players reported crashes in threaded modes
  (#440), 680 % CPU and deadlocks with block placers (#559), the watchdog
  firing when 500 blocks were placed at once (#1494), and vents failing to
  seal after loading (#2380). Atmospheres are typed (`IAtmosphere`: pressurized
  air, vacuum, low oxygen, very hot).
- **Not Enough Oxygen and Beyond Oxygen**
  ([NEO](https://github.com/kito1z/not-enough-oxygen),
  [BO](https://github.com/SierraVanguard/beyond-oxygen)) are the smallest
  clean example of per-face sealing. A face seals if `isFaceSturdy` holds,
  and straight-through flow is blocked by a panel that spans the whole cross
  section. Beyond Oxygen persists each area's block and boundary sets in NBT
  and rebakes dirty areas synchronously; its config warns that large ranges
  "CAN AND WILL cause lag".
- **NeoAeris** sidesteps partial blocks: only its own Air Shell and Air Glass
  blocks seal, and an Air Anchor validates the room.
- **NTM: Space**
  ([wiki](https://nucleartech.wiki/wiki/Life_Support_(NTM:_Space))): vents
  draw 400 mB/s of oxygen to fill a room to 0.2 atm at 1 mB per block, then
  need 10 mB/s upkeep. Only its animated doors seal; vanilla doors never do.
- **Glenn's Gases and Gases Framework** (1.7.10 and 1.12.2, last file
  2018-11-15) made gases real blocks that spread and dissipate, the per-block
  extreme.
- **Space Station Craft 14**
  ([source](https://github.com/SpaceKyyyst/Space-Station-Craft-14)), an
  MCreator-based SS14 port, keeps a `GasMixture` per cell in 16³ regions. A
  manager wakes regions on change and processes them every 4 ticks within
  budgets (8 regions, 1024 block changes and 32 chunk loads per pass; its
  architecture doc says 128 regions). Work for unloaded chunks waits until
  they reload. Blocks declare `AtmosphericBlock.getPermeability(BlockState)`.

**Space Station 14 as a contrast** (`space-wizards/space-station-14` @
`b2834c5`, 2026-10-29). SS14 simulates every tile (2500 L cells) but bounds
the cost:
- A resumable state machine per grid runs revalidation, Monstermos
  equalization, active tiles (LINDA), excited groups, pressure deltas,
  hotspots, superconduction, pipe nets and devices. It ticks at
  `atmos.tickrate` = 15 Hz under `atmos.max_process_time` = 3 ms and resumes
  where it stopped.
- Only "active" tiles are processed. Excited groups average their gas after
  4 cycles and dismantle after 16, which makes them a dynamic zone.
  Monstermos equalization is capped at 200 tiles (hard limit 2000).
- Airtightness is per-direction flags on entities (`AirtightComponent`),
  rotated with the entity. Changes are queued as invalidated coordinates.
- Each map has an immutable `MapAtmosphereComponent`, a `Space` flag plus a
  gas mixture, the equivalent of zas-cubed's per-dimension ambient.
- Cautionary defaults: superconduction is off ("superconduction is awful"),
  space wind is off, and a code comment on `FixVacuum` notes that cycling
  doors could create gas for free until a mass-conservation fix.

**Lessons for zas-cubed**
1. **Bound and amortize everything.** Every working implementation caps the
   flood fill (2048–6000 blocks, or capacity-based), and the better ones
   spread it across ticks (Northstar at 128 blocks per tick, SS14 at 3 ms).
   Make the per-tick budget configurable, and degrade by slowing down rather
   than failing.
2. **Keep the previous topology live while recomputing**, as Northstar does,
   so a rebuild never makes a room briefly unbreathable.
3. **Invalidate on every state change, not on player events.** Event-based
   triggers miss pistons, explosions, fluids and other mods' `setBlock`
   calls. Mixin `ServerLevel#onBlockStateChange` (Cold Sweat already does),
   debounce, and drop changes that don't alter the cell's face mask.
   *Caveat added 2026-10-30:* direct chunk writers such as WorldEdit's
   NeoForge module bypass `onBlockStateChange`.
   [neoforge-1.21.1 §3](neoforge-1.21.1.md#3-block-change-detection)
   recommends hooking `LevelChunk#setBlockState` instead. Spike S1 decides
   between the two.
4. **Seal per face, cached per `BlockState`.** Derive a 6-bit face mask from
   the collision shape, with tag overrides (`blocks_air`, `passes_air`) and a
   block interface for modded doors. Ad Astra's whole-block test leaks
   through corner stairs (#624), and Stellaris's air-only rule turns torches
   into walls.
5. **Doors map to ZAS `ZONE_BLOCKED`.** Closed doors seal by shape; open
   doors connect zones without merging them. Players expect vanilla doors to
   work, so NTM and NeoAeris refusing them is a usability cost. Conserve
   mass across open and close cycles.
6. **Unloaded chunks freeze.** Treating them as solid is an exploit, treating
   them as leaks vents bases at the view-distance edge, and synchronous loads
   cause lag.
7. **Sync summaries and persist compactly.** Ad Astra #434 came from syncing
   position sets, and both Ad Astra's SavedData and Beyond Oxygen's NBT grow
   with volume. Persist gas per zone and rebuild topology on load.
8. **Make breaches visible and bounded.** Northstar shows leak particles, Ad
   Astra spawns an air vortex, and SS14 keeps space wind off by default for
   playability.
9. **Never touch the live world from worker threads.** Galacticraft disabled
   its sealer thread, and AR 1.12's thread pool crashed and deadlocked. If
   zas-cubed goes off-thread, copy chunk-section palettes first and apply
   results on the main thread.
10. **Don't store air as blocks; store bits or zone ids.** Galacticraft
    Legacy's air blocks caused write races, OOMs and update storms, while
    GC5's per-section bitset is cheap to persist, sync and query.
11. **Make zone merge and split first-class.** Multi-sealer merges are the
    most common sealing bug (GC5 #625, GC #286).

## C. Shared API niche and survival-mod targets

- **No library owns oxygen or atmosphere on 1.21.1.** Ad Astra's API is the
  largest, but it is one mod's surface, not a framework. The shared pieces
  are `LivingBreatheEvent`, the `#c:oxygen` fluid tag, Mekanism's
  `mekanism.api.chemical.IChemicalHandler` (capability
  `mekanism:chemical_handler`, gas `mekanism:oxygen`) and Sable
  `dimension_physics`. zas-cubed should implement adapters for all of them
  rather than define competing equivalents.
- **Cold Sweat** 2.4.4 (2026-10-16, GPL-3.0+): extend
  `com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier`,
  register it through `TempModifierRegisterEvent` and attach it with
  `GatherDefaultTempModifiersEvent`.
- **Tough As Nails** 10.1.0.13 beta (2024-10-22; stale on 1.21.1):
  `toughasnails.api.temperature.TemperatureHelper
  .registerPositionalTemperatureModifier(IPositionalTemperatureModifier)`,
  whose `modify(Level, BlockPos, TemperatureLevel)` fits zone temperature.
- **Legendary Survival Overhaul** 1.21.1-2.4.7.2 (2026-08-09, ARR):
  `sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil`
  (`getWorldTemperature`, `addTemperatureModifier`) and `ModifierBase`.
- **Thermoo** 4.8.1-neoforge alpha (2026-03-09, LGPL-3.0), used by the
  Frostiful and Scorchful NeoForge alphas:
  `com.github.thedeathlycow.thermoo.api.temperature`
  (`EnvironmentControllerDecorator`, `event.EnvironmentControllerInitializeEvent`)
  and datapack environment providers.
- **Homeostatic** 1.21.1-2.12.1.0 (2026-10-19, MIT): no API package, so it
  would need a mixin.
- **Serene Seasons** 10.1.0.9 beta (2026-09-05):
  `sereneseasons.api.season.SeasonHelper`; relevant only once ambients vary.
- **ThinAir: ReLived** 1.0.6: read-only
  `dev.maire.thinair.api.AirQualityHelper.INSTANCE
  .getAirQualityAtLocation(Level, Vec3)`; overriding it needs a mixin into
  its implementation.

## Open questions

- Per-shape seal outcomes for Ad Astra, Northstar, Creating Space and AR3
  come from reading code, not from testing in game.
- Whether plain `getBlockState` in Ad Astra, Northstar and GC5 actually
  triggers synchronous chunk loads in practice (vanilla semantics suggest
  it does).
- Whether Ad Astra's and Stellaris's suit tanks are exposed as NeoForge fluid
  capabilities, which decides whether zas-cubed's life-support API can read
  them without mod-specific code.
- Whether Northstar's sealer also runs client-side, which decides whether a
  zas-cubed `Provider` must exist on the client.
- CurseForge-only mods may be under-sampled: CurseForge was searched only
  through web search and cfwidget, not its API.
