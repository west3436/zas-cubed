# Mekanism for NeoForge 1.21.1: integration surface

> **Status (2026-10-30):** Mekanism compat is **deferred** to after R1 by
> decision D-11 (see `../requirements/00-overview.md`). This note stays as the
> starting point for that work.

> **Researched:** 2026-10-30, for MEK, spike S5 and risk R5 in
> [plan.md](../plan.md).
>
> **Source:** `github.com/mekanism/Mekanism`, tag `v1.21.1-10.7.19.85`, commit
> `a00109e4856fd38b9c5b3dd7f22ce4a59cd65a80` (2026-04-10, "bump version to
> 10.7.19"). It was a shallow clone, read only and never built. The `1.21.x`
> dev branch is 10 commits ahead, all translation or CI changes.
>
> **Also used:** GitHub release notes for 10.7.0.55 and 10.7.11.76, the
> ModMaven listing, and the NeoForge 1.21.1 docs on mod files and capabilities.
> The Mekanism wiki describes older versions, so where it disagrees with the
> source, the source wins.
>
> **Conventions:** behavior is described in prose. Class and method names are
> cited as API facts. "Unverified" marks claims not checked in game or against
> the platform source.
>
> **Path prefixes:**
> - `A/` = `src/api/java/mekanism/api/`
> - `M/` = `src/main/java/mekanism/common/`
> - `G/` = `src/generators/java/mekanism/generators/common/`
> - `D/` = `src/datagen/generated/`

## Implications for zas-cubed

- **Venting is the only integration that can't ship without mixins.**
  - **Bridge and breathing gear:** buildable on public surfaces: capabilities,
    the API jar, data maps and tags.
  - **Venting:** no event or API hook exists on any path where Mekanism voids
    chemicals.
  - **Heat:** can ship on capabilities. Replacing Mekanism's biome-based ambient
    is optional and needs mixins.
- **`compat-mekanism` needs more than the API jar** (unlike plan.md's module
  table). The capability code compiles against the `api` classifier. The mixins
  target `mekanism.common.*` and `mekanism.generators.*`, so they need the main
  and `generators` jars as `compileOnly`.
- **There is one chemical type.** Since 10.7.0, gases, infuse types, pigments and
  slurries share one `Chemical` registry, so the bridge needs its own allow-list.
  - The `mekanism:gaseous` tag is only a rendering hint. It includes brine and
    superheated sodium, and it leaves out chlorine.
  - **Mekanism has no CO₂ or N₂.** The S-07 scrubber-to-tank scenario requires
    zas to register its own chemicals in Mekanism's registry.
- **The unit mapping is a design decision.** Mekanism's recipes are
  mole-proportional in mB, so a single global factor `k` (mol per mB) keeps its
  chemistry consistent. Proposal: 1 mB = 1 L of gas at STP, about 0.0446 mol.
  Then 1,000 mB fills one block at about 1 atm.
- **Vented amounts are huge.** An Ultimate Chemical Tank in Dumping mode sheds
  20,480 mB/t, about 410 m³/s at STP. Turbine dumping can be larger still. Venting
  needs rate caps, or we accept the overpressure as a feature.
- **The Scuba Tank works almost for free.** It already refills vanilla air
  supply every player tick. If zas drives hypoxia through air supply
  (`LivingBreatheEvent`), scuba gear keeps players breathing in vacuum with no
  special case. Pressure and cold remain separate hazards.
- **The Electrolytic Breathing Unit (EBU) needs a policy.** It only works in
  water or rain, so zas must decide its vacuum behavior.
- **The MekaSuit absorbs unknown damage.** By default it absorbs 100% of any
  damage type it doesn't know (`unspecifiedDamageReductionRatio=1.0`). zas must
  tag each of its own damage types on purpose: hypoxia, pressure, temperature and
  toxins. Tags and data maps can also make the suit and the scuba mask protect
  against a type without code.
- **Default gases and radiation don't overlap.** No default gaseous chemical is
  radioactive. The radioactive ones are nuclear waste, spent waste, plutonium and
  polonium, and none of them should reach the atmosphere.
- **Mekanism has no oxygen, atmosphere or space-mod API or IMC.**
- **Pin versions and test each hook.** Mekanism ships no mixins, so ours can't
  clash with its own, but its internal method names can change in any build. Use a version range of `[10.7.15,)`, gate the mixins on mod
  presence, and write one GameTest per hook (R5).

## 1. Version, coordinates, optional loading

**Latest release:** Mekanism 10.7.19, build 85, released 2026-04-10.
- From `gradle.properties`: `minecraft_version=1.21.1`,
  `neo_version=21.1.200`, `neo_version_range=[21.1.194,)`, Java 21,
  `release_type=release`.
- The mods.toml version is `10.7.19`, with no build number.
- `MekanismAPI.API_VERSION` is `"10.7.15"`.
- Mod IDs: `mekanism`, `mekanismgenerators` (turbine, fission, fusion, D/T),
  `mekanismadditions`, `mekanismtools`.

**Maven:** repository `https://modmaven.dev/`, group `mekanism`, artifact
`Mekanism`, version `1.21.1-10.7.19.85`. Classifiers, confirmed on ModMaven:
- `api`
- none: the main jar. It already contains the API classes, because
  `build.gradle` builds `jar` from the API and main source sets. Don't put `:api`
  on the runtime classpath as well.
- `generators`, `additions`, `tools`, `all`, `sources`

```groovy
repositories { maven { url = 'https://modmaven.dev/'; content { includeGroup 'mekanism' } } }
dependencies {
    compileOnly("mekanism:Mekanism:${mek_version}:api")         // bridge, gear, heat capability
    compileOnly("mekanism:Mekanism:${mek_version}")             // mixin targets in mekanism.common
    compileOnly("mekanism:Mekanism:${mek_version}:generators")  // turbine, fission, fusion targets
    localRuntime("mekanism:Mekanism:${mek_version}")            // ModDevGradle dev runs
    localRuntime("mekanism:Mekanism:${mek_version}:generators")
}
```

The README's `fg.deobf` example dates from ForgeGradle; ModDevGradle doesn't
need it. Consider `transitive = false`.

**Optional dependency.** Add one entry for `mekanism` and another for
`mekanismgenerators`:

```toml
[[dependencies.zas]]
    modId="mekanism"
    type="optional"
    versionRange="[10.7.15,)"
    ordering="AFTER"
    side="BOTH"
```

**Loading compat code only when present:**
- **Entry point.** In the mod constructor, call
  `if (ModList.get().isLoaded("mekanism")) MekanismCompat.init(modBus);`. Only
  code reached through that call may name Mekanism types. Mekanism does the same
  in `M/integration/MekanismHooks.java`.
- **Event listeners.** Register them by hand inside `init`. Don't put
  `@EventBusSubscriber` on any class that names Mekanism types, because FML loads
  every annotated class.
- **Mixins.** *(Corrected 2026-10-30.)* FML 1.21.1 lets each `[[mixins]]`
  entry list `requiredMods`. This was verified in the FML 1.21.1 source,
  `ModFileParser#getMixinConfigs`, and backported in NeoForge 21.1.97. So:
  - declare separate configs, e.g. `zas.mekanism.mixins.json` with
    `requiredMods=["mekanism"]`, and a generators config with
    `requiredMods=["mekanismgenerators"]`
  - an `IMixinConfigPlugin` is only needed for finer-grained conditions
  - MixinExtras 0.5.5 ships with NeoForge 21.1.265 (see
    [neoforge-1.21.1 §14](neoforge-1.21.1.md#14-mixins--ats))
- **Data.** Wrap JSON in `neoforge:conditions` with `neoforge:mod_loaded`, and
  mark tag entries `"required": false`.

## 2. Chemical API

**The four types are unified.** Release 10.7.0 merged "all four chemical types
into a singular registry and type called chemical" (commit b74b68b, PR #8196) and
removed the boxed types. 10.7.11 moved to holders and data maps, deprecating the
old forms for removal in 1.22.

**Key types** (`mekanism.api`):
- `chemical.Chemical`, `chemical.ChemicalStack`, `chemical.IChemicalHandler`,
  `chemical.ISidedChemicalHandler`
- `chemical.IChemicalTank`, with `BasicChemicalTank` factories such as
  `createModern(capacity, validator, listener)`
- `chemical.ChemicalBuilder`, `Action` (`EXECUTE`/`SIMULATE`), `AutomationType`
- `MekanismAPI.CHEMICAL_REGISTRY`: a synced `DefaultedRegistry<Chemical>` with
  key `mekanism:chemical` and default `mekanism:empty`
- `MekanismAPI.CHEMICAL_REGISTRY_NAME`, `MekanismAPI.EMPTY_CHEMICAL_HOLDER`

**Capability objects.** These live in `M/capabilities/Capabilities.java`, not in
the API:
- `CHEMICAL`, named `mekanism:chemical_handler`, of type `IChemicalHandler`:
  - block: `BlockCapability.createSided`, context `@Nullable Direction`
  - item: `ItemCapability.createVoid`
  - entity: `EntityCapability.createVoid`
- `HEAT`: `BlockCapability.createSided("mekanism:heat_handler", IHeatHandler)`
- `RADIATION_SHIELDING` (item) and `RADIATION_ENTITY` (entity)

NeoForge returns the existing capability object when a name is created again.
So zas can create these from API-only types without touching `Capabilities`.

**Lookup by ResourceLocation:**
- Use
  `CHEMICAL_REGISTRY.getHolder(ResourceKey.create(CHEMICAL_REGISTRY_NAME, rl))`.
  `get(rl)` silently returns the empty chemical for unknown names.
- Then `new ChemicalStack(holder, mb)`.
- Codecs: `Chemical.HOLDER_CODEC`, `ChemicalStack.CODEC`,
  `ChemicalStack.OPTIONAL_CODEC`.
- Chemical tags live under `data/<ns>/tags/mekanism/chemical/`.

**Insert and extract semantics.** `insertChemical(stack, action)` returns the
remainder (empty means all accepted), and `extractChemical(amount | stack,
action)` returns what was taken. Never modify the stack `getChemicalInTank`
returns. Amounts are `long` mB. Each tank holds one type, so a tube network
carries one chemical at a time. `isValid(tank, stack)` says whether a type
could ever be accepted.

**Attributes are now data maps.** Read them through
`IMekanismDataMapTypes.INSTANCE` or `ChemicalStack#getData`. Values are from
`D/mekanism/data/mekanism/data_maps/mekanism/chemical/`:

| Data map | Meaning | Values |
|---|---|---|
| `chemical_attribute_radioactivity` | Sv/h per mB | nuclear and spent waste 0.01, plutonium 0.02, polonium 0.05 |
| `chemical_attribute_fuel` | `burn_time` in ticks × `energy` in J/t | hydrogen 1×200, ethene 40×705 |
| `chemical_attribute_cooled_coolant` | coolant, cold side | sodium |
| `chemical_attribute_heated_coolant` | coolant, hot side | superheated sodium |
| `chemical_solid_tag` | item tag of the solid form | |

Other attribute access:
- Helpers: `Chemical#getRadioactivity()`, and `ChemicalStack#getRadioactivity()`
  (per-mB value × amount).
- The `chemical.attribute.ChemicalAttributes.*` classes are deprecated.
- `MekanismAPITags.Chemicals` holds `GASEOUS`, `WASTE_BARREL_DECAY_BLACKLIST`,
  the infuse-type tags, and `DIRTY`/`CLEAN`.
- An internal tag `mekanism:water_vapor` covers both water vapor and steam.

**Throughput.** From `M/tier/TubeTier.java` and `M/tier/ChemicalTankTier.java`;
all values are configurable.
- Tube capacity is mB per tube. A network's buffer is the sum of its tubes'
  capacities, and it can emit the whole buffer each tick.
- Pull is mB/t per pulling connection.

| Tier | Tube capacity / pull | Chemical tank storage / output per tick |
|---|---|---|
| Basic | 4,000 / 750 | 64,000 / 1,000 |
| Advanced | 16,000 / 2,000 | 256,000 / 16,000 |
| Elite | 256,000 / 64,000 | 1,024,000 / 128,000 |
| Ultimate | 1,024,000 / 256,000 | 8,192,000 / 512,000 |

## 3. Gas inventory and units

Registrations are in `M/registries/MekanismChemicals.java` and
`G/registries/GeneratorsChemicals.java`. The tag file is
`D/*/data/mekanism/tags/mekanism/chemical/gaseous.json`.

| Registry ID | Substance | Gas at 20 °C, 1 atm? | In `gaseous` tag? | Bridge default |
|---|---|---|---|---|
| `mekanism:oxygen` | O₂ | yes | yes | map |
| `mekanism:hydrogen` | H₂ | yes | yes | map |
| `mekanism:water_vapor` | H₂O | vapor | yes | map to H₂O at the source temperature |
| `mekanism:steam` | hot H₂O | vapor | yes | map to H₂O at 373 K or above |
| `mekanism:chlorine` | Cl₂ | yes | **no** | map (toxic) |
| `mekanism:hydrogen_chloride` | HCl | yes | yes | map (toxic) |
| `mekanism:sulfur_dioxide` | SO₂ | yes | yes | map (toxic) |
| `mekanism:sulfur_trioxide` | SO₃ | no (boils at 45 °C) | yes | optional |
| `mekanism:ethene` | C₂H₄ | yes | yes | map (fuel) |
| `mekanismgenerators:deuterium` | D₂ | yes | yes | map |
| `mekanismgenerators:tritium` | T₂ | yes | yes | map; not radioactive in Mekanism's data |
| `mekanismgenerators:fusion_fuel` | D–T mix | yes | yes | split into 0.5 D₂ + 0.5 T₂ (its recipe is 1 D + 1 T → 2) |
| `mekanism:uranium_hexafluoride` | UF₆ | no (sublimes at 56.5 °C) | no | exclude by default |
| `mekanism:hydrofluoric_acid` | aqueous HF | liquid | no | exclude |

**Never let these into the atmosphere.**
- **Process liquids and metals:** `brine` and `superheated_sodium` (both tagged
  gaseous), `sodium`, `lithium`, `sulfuric_acid`, `osmium`, `uranium_oxide`,
  `fissile_fuel`
- **Radioactive:** `nuclear_waste`, `spent_nuclear_waste`, `plutonium`,
  `polonium`
- **Exotic:** `antimatter`
- **Infuse types:** `carbon`, `redstone`, `diamond`, `refined_obsidian`, `gold`,
  `tin`, `bio`, `fungi`
- **Pigments (18):** the dye colors plus `dark_red` and `aqua`
- **Slurries (14):** `dirty_` and `clean_` for each of iron, gold, osmium,
  copper, tin, lead and uranium
- **The placeholder:** `empty`

All of these are in the `mekanism:` namespace.

**Registering zas chemicals.** For CO₂ and N₂ (S-07), the compat module can add
`zas:carbon_dioxide` and similar to Mekanism's registry, using the API
only:
- `DeferredRegister.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, "zas")`
- `new Chemical(ChemicalBuilder.builder().tint(...))`
- add each one to `mekanism:gaseous`

The registry is synced, so zas must be installed on both sides, which it already
is.

### Unit hints

- **Conversions are 1:1.** The Rotary Condensentrator turns 1 mB of fluid into
  1 mB of chemical, and the Boiler turns 1 mB of water into 1 mB of steam.
- **Recipes are mostly mole-proportional:**
  - 2 water → 2 H₂ + 1 O₂
  - 2 SO₂ + 1 O₂ → 2 SO₃
  - SO₃ + H₂O → H₂SO₄
  - D + T → 2 fuel
  - The outlier is H₂ + Cl₂ → 1 HCl.
- **Liquid constants.** `M/ChemicalConstants.java` gives O₂ as 90.19 K at
  1,141 kg/m³ and H₂ as 20.28 K at 70.85 kg/m³, about 35 mmol per mB of liquid.
  Water is 55.5 mmol per mB, and an ideal gas at STP is 44.6 mmol per L. The
  liquid forms carry `c:` fluid tags, such as `c:oxygen` and `c:hydrogen`.

**Design decision (flagged):**
- Use one global `k` = 1/22.414 mol per mB, so 1 mB is 1 L of gas at 273.15 K
  and 101.325 kPa.
- Per-gas factors would break the recipe balance above.
- Alternatives: 0.0416 (20 °C basis) or 0.0555 (water-equivalent).
- Treat Mekanism storage as pressureless. Enforce pressure only on the zas side,
  where adapters stop accepting at a pressure ceiling.
- Chemicals carry no temperature. Assign one on entry: the source or zone
  temperature, with steam at 373.15 K or more. Pick steam versus water vapor by
  temperature on exit.

## 4. Venting and dumping

There is no event, API hook or shared choke point. Every void is a direct
`IChemicalTank#shrinkStack` or `setEmpty` on an internal tank.

**Recommended pattern:**
1. Wrap the `shrinkStack(JLmekanism/api/Action;)J` call with MixinExtras
   `@WrapOperation`. MixinExtras 0.5.5 ships with NeoForge 21.1.265.
2. Read `tank.getStack()` before the call, and call the original.
3. Release `copyWithAmount(returned)` through the zas API at the block
   position. zas spreads it into the adjacent zones, and CORE-053 handles open
   air.
4. Leave unmapped chemicals voiding, as they do today.

| # | Site | Target | Rate or amount | Radiation today | Hook |
|---|---|---|---|---|---|
| 1 | Chemical Tank: Dumping and Dumping Excess | `M/tile/TileEntityChemicalTank#onUpdateServer()` | Dumping: `storage/400` per tick. Excess: down to 85% full (`dumpExcessKeepRatio`), capped at the tier output. Creative tanks never dump. | none | wrap both `shrinkStack` calls |
| 2 | Electrolytic Separator, per side | `M/tile/machine/TileEntityElectrolyticSeparator#handleTank(IChemicalTank, GasMode)` (private) | 8 mB/t × speed upgrades; excess capped at `auto_eject.chemical` (1,024) | none | wrap both `shrinkStack` calls; the sides hold H₂/O₂ or Na/Cl₂ |
| 3 | Industrial Turbine dump | `G/content/turbine/TurbineMultiblockData#tick(Level)` | `min(stored, max(stored/50, 2×lastSteamInput))` | none | wrap `shrinkStack` at ordinals 1–2 (ordinal 0 is normal consumption) |
| 3b | Turbine steam the condensers can't handle | same method | consumes `clientFlow` of steam, but only `min(rate, condensers × condenserRate)` comes back as water | none | optional exhaust hook |
| 4 | Dump buttons and computer methods | `IHasDumpButton#dump()` in `M/tile/factory/TileEntityItemStackChemicalToItemStackFactory`; `dumpChemical()` there and in `M/tile/prefab/TileEntityAdvancedElectricMachine` | whole secondary tank: O₂, HCl, water vapor | none | `@Inject` at HEAD. Skip `TileEntityMetallurgicInfuser#dump` (infuse types). |
| 5 | Gauge Dropper dump in a GUI | `M/network/to_server/PacketDropperUse#handleChemicalTank(...)`, action `DUMP_TANK` | whole tank | `dumpRadiation(level, pos, stack)`, then `setEmpty()` | `@Inject` before `setEmpty` |
| 6 | Gauge Dropper sneak-use | `M/item/ItemGaugeDropper#use` | the dropper's contents | none | optional |
| 7 | Broken tubes | `M/content/network/ChemicalNetwork#disperse(PressurizedTube, ChemicalStack)` (protected), reached via `onLastTransmitterRemoved` and leftovers in `updateSaveShares`; also `#clampBuffer()`, which silently trims the buffer to the reduced capacity | the network's share | `disperse` only | `@Inject` at HEAD of `disperse` (gives a position). `clampBuffer` has no position: accept the loss, or capture the trigger in `DynamicBufferedNetwork#removeInvalid`. |
| 8 | Broken machines and tanks | `M/tile/base/TileEntityMekanism#blockRemoved()` | none: contents stay in the dropped item (`collectChemicalTanks`); radioactive tanks are radiated and stripped | yes | none needed |
| 9 | Multiblock cache merge | `M/lib/multiblock/FormationProtocol#doUpdate()`, `RejectContents.rejectedChemicals` | chemicals that didn't fit | radioactive part only | low priority |
| 10 | Fission | `G/content/fission/FissionReactorMultiblockData#burnFuel(Level)` (waste overflow is radiated); `#meltdownHappened(Level)` (radioactive tanks radiated, heated coolant emptied) | waste, coolant | yes | not gases; skip |
| 11 | Waste barrel decay | `M/tile/TileEntityRadioactiveWasteBarrel#onUpdateServer()` | 1 mB per 20 ticks | none (decay) | none |

Optional later hooks: fuel consumers such as the jetpacks (`useJetpackFuel`,
1 mB/t of H₂), the flamethrower and the gas-burning generator.

**A no-mixin fallback** that ships regardless: a zas "exhaust port" adapter
block. It exposes `mekanism:chemical_handler`, and players point auto-eject or
tubes at it.

## 5. Breathing gear

### Scuba Tank and Mask

Classes: `M/item/gear/ItemScubaTank.java` and `ItemScubaMask.java`. The logic is
in `M/CommonPlayerTickHandler.java`.

**When it's active.** All three must hold:
- the head slot has `mekanism:scuba_mask`
- the chest slot has `mekanism:scuba_tank` holding any chemical
- the data component `mekanism:scuba_tank_mode` is `true`. Players toggle it with
  the armor-mode key; the default is `false`.

The check is `isScubaMaskOn(Player, ItemStack)`.

**Supply** (server, players only, on `PlayerTickEvent.Post`):
1. It calls `useChemical(chest, 1)` every tick, even out of water.
2. It then calls `useChemical(chest, maxAir − air)` and adds the extracted mB to
   the player's air supply.

There is no underwater check and no `LivingBreatheEvent`.

**Storage.**
- Oxygen only (`chemical.is(MekanismChemicals.OXYGEN)`).
- 24,000 mB at a fill rate of 16 mB/t (`GearConfig`, `scuba_tank.capacity` and
  `fillRate`).
- The item exposes `mekanism:chemical_handler` with one tank.

**Gas-mask effect.** `onEntityAttacked` cancels damage tagged
`#mekanism:is_preventable_magic` (`minecraft:magic`, `minecraft:indirect_magic`)
while the tank is on and holds any chemical.

**Endurance.** A full tank lasts 20 minutes at the idle drain of 1 mB/t, or 10
minutes at 2 mB/t once zas hypoxia runs through air supply. Any deficit is
refilled within one tick, so a larger `consumeAirAmount` only drains the tank
faster.

### Electrolytic Breathing Unit

Source: `M/content/gear/mekasuit/ModuleElectrolyticBreathingUnit#tickServer`.
Module `mekanism:electrolytic_breathing_unit`, helmet only, up to 4 installed.

- **When it works:** only when the mask area is at least 0.11 deep in water,
  giving a rate of 2^installed air per tick, or in rain at half that rate.
- **What it does:** restores air supply, and pushes 2 H₂ per unit into the chest
  item and, with the `breathing.held` setting, into the held item.
- **Cost:** energy is 2 × the hydrogen energy density (200 J per mB from the fuel
  data map), so 400 J per unit.
- **In vacuum:** it does nothing, and it stores no oxygen.

### Inhalation Purification Unit

Source: `ModuleInhalationPurificationUnit`.
- For energy every tick, it speeds up the expiry of potion effects. Harmful
  effects are covered by default; beneficial and neutral are off by default.
- It absorbs `#mekanism:is_preventable_magic` damage.
- It supplies no air.

### MekaSuit damage

Source: `M/item/gear/ItemMekaSuitArmor#getDamageAbsorbed`. Each piece absorbs its
share × a ratio:
- the ratio is from data map `mekanism:mekasuit_absorption`, keyed by damage
  type
- `#mekanism:mekasuit_always_supported` gets 1.0. It includes freeze, hot_floor,
  in_fire, lava and in_wall.
- anything else gets `unspecifiedDamageReductionRatio` (1.0), unless it's tagged
  `c:is_technical`, or tagged `bypasses_armor` without being always-supported

### zas plan (no mixins)

1. **Register the life-support capability.** In `RegisterCapabilitiesEvent`,
   register zas's life-support item capability (API-022) for these items, looked
   up by ID:
   - `mekanism:scuba_tank` and `mekanism:scuba_mask`
   - the four `mekanism:mekasuit_*` pieces
2. **Scuba supply.** The tank counts as a breathable supply while its mode is on
   and it holds O₂ (read through the item `mekanism:chemical_handler`). Mekanism's
   own drain is the cost, so zas shouldn't extract more. Exhaled CO₂ is optional.
3. **EBU in vacuum.** Detect it with
   `IModuleHelper.INSTANCE.isEnabled(stack, holder)`, taking the holder from
   `MekanismAPI.MODULE_REGISTRY`. Then choose a policy:
   - electrolyze the zone's H₂O vapor, pushing H₂ through the chest item's
     capability and paying with `IModule#useEnergy`, or
   - a config switch that makes it work in vacuum for a set energy cost per
     breath
4. **IPU.** Treat it as a toxin filter, not an O₂ source.
5. **Hypoxia.** Drive it through vanilla air supply with `LivingBreatheEvent`.
6. **Damage types:**
   - hypoxia: tag `minecraft:bypasses_armor`
   - toxins: tag `#mekanism:is_preventable_magic`, so the scuba gear and IPU
     protect
   - pressure and temperature: add entries to `mekanism:mekasuit_absorption`, or
     tag them `bypasses_armor` and let the life-support capability decide
7. **Robit.** Exclude `mekanism:robit` from breathing. A TODO in
   `M/entity/EntityRobit.java` says it should "breath without a mask".

## 6. Heat

**API** (`A/heat/IHeatHandler`):
- per capacitor: `getTemperature` (K), `getHeatCapacity` (J/K),
  `getInverseConduction`, `handleHeat(J)`
- the `getTotal*` defaults
- Heat uses the same unit as Mekanism energy, J.

**Constants and ambient** (`A/heat/HeatAPI`):
- `AMBIENT_TEMP` = 300 K, `AIR_INVERSE_COEFFICIENT` = 10,000.
- `getAmbientTemp(LevelReader, BlockPos)` returns
  `300 + 25·(clamp(biome.getTemperature(pos), −5, 5) − 0.8)`. It is biome- and
  altitude-based.
- It's a static method with no provider hook.

**Who loses heat to ambient, and how:**
- **Tiles and conductors.** `TileEntityMekanism#getAmbientTemperature(side)`
  and `ThermodynamicConductor#getAmbientTemperature(side)` both go to
  `M/capabilities/heat/CachedAmbientTemperature#getTemperature(Direction)`.
  That caches the biome value per side, permanently, on first read. The same
  value seeds the initial heat of `BasicHeatCapacitor`.
- **Default loss formula.** `ITileHeatHandler#simulateEnvironment()` (in
  `M/capabilities/heat/`) works per side. Heat removed is ΔT / (10,000 + inverse
  insulation + inverse conduction) × C, via `handleHeat(-q, side)`. That heat
  disappears. The return value is ΣΔT in K, not J.
- **Users of the default formula:**
  - the Resistive Heater, the Fuelwood Heater and the Quantum Entangloporter
  - the Heat Generator, whose Carnot efficiency also uses the ambient
  - fusion ports, which `FusionReactorMultiblockData#simulate` delegates to
  - conductors, through `HeatNetwork#onUpdate`, with inverse insulation of 10,
    400, 8,000 and 100,000 by tier
- **Neighbor transfer.** `simulateAdjacent()` pushes heat into neighbors that
  expose `mekanism:heat_handler`. It's driven by T_self − T_ambient, not by the
  neighbor's temperature.
- **Multiblocks.** Each has a private `biomeAmbientTemp`. It's set at
  construction, and again in `onCreated` through
  `MultiblockData#calculateAverageAmbientTemperature(Level)` (the average over
  the 8 corners).
  - `BoilerMultiblockData#simulateEnvironment` and
    `FissionReactorMultiblockData#simulateEnvironment` use the formula above.
  - `EvaporationMultiblockData#simulateEnvironment` uses √ΔT dissipation plus
    solar heating.
  - Fusion uses `biomeAmbientTemp` for water heating.

**Approach A: capability exchange, no mixin. R1 baseline.**
- For each zone boundary face, check whether the neighbor block exposes
  `mekanism:heat_handler` on that face.
- If it does, compute Q from the handler's temperature, the zone's temperature
  and the face conductance.
- Cap Q at the equilibrium limit (HEAT-006/008).
- Apply it with `handler.handleHeat(-Q)`, and add Q to the zone through HEAT-005.
- Deduplicate multiblocks, whose casings and ports share one capacitor.

Limitation: Mekanism still leaks heat toward the biome ambient through a
resistance of at least 10,000. With a zas resistance of 1,000, an idle
Mekanism heat block in a vacuum zone settles near 30 K instead of 3 K.

**Approach B: optional, config-gated mixins.**
1. `CachedAmbientTemperature#getTemperature(Direction)` at HEAD, cancellable:
   return the live zone temperature at `pos.relative(side)`. Shadow
   `worldSupplier` and `positionSupplier`.
2. `MultiblockData#calculateAverageAmbientTemperature(Level)` at HEAD: return the
   zone average around the multiblock's bounds.
3. Refresh `biomeAmbientTemp` regularly in Boiler, Evaporation, Fission and Fusion
   with `@Shadow` and a throttled inject at HEAD of `tick(Level)`.
4. Wrap `handleHeat` inside `ITileHeatHandler#simulateEnvironment` and the
   three multiblock overrides to deposit the lost heat into zones. Injecting
   into an interface default method is unverified on NeoForge's Mixin; the
   fallback is wrapping the `simulate()` call sites.
5. Optionally, `HeatAPI#getAmbientTemp(LevelReader, BlockPos)` at HEAD for any
   other callers. It's only sampled once.

## 7. Radiation

`A/radiation/IRadiationManager.INSTANCE` provides:
- `isRadiationEnabled()`. Radiation is on by default.
- `radiate(Level, BlockPos, Sv/h)` creates persistent, decaying sources per chunk.
- `radiate(LivingEntity, Sv)`.
- `dumpRadiation(Level, BlockPos, ChemicalStack)`, since 10.7.15. It radiates
  radioactivity × amount and returns `true` if the stack was radioactive.
- tank and handler overloads that take `clearRadioactive`.
- `getRadiationLevel(...)`.
- Baseline is 100 nSv/h; the minimum magnitude is 10 µSv/h.

Related capabilities:
- `mekanism:radiation_shielding` (item, `getRadiationShielding()` from 0 to 1).
  zas suits can provide it.
- `mekanism:radiation` (entity dose).

**Policy.** Radioactive chemicals stay out of the atmosphere by default. If a pack
maps one, either vent it and call `dumpRadiation` once, or convert it entirely to
radiation. Don't do both.

## 8. Existing atmosphere and oxygen hooks

There are none.
- The source has only Galacticraft TODOs, in `EntityRobit` and
  `G/tile/TileEntitySolarGenerator`.
- `A/MekanismIMC` only registers module containers and modules (for example
  `add_meka_suit_helmet_modules`).

Still useful: `MODULE_REGISTRY_NAME` plus that IMC (for a zas MekaSuit "life
support unit" module), `IModuleHelper`, the damage tags, the
`mekasuit_absorption` data map, and `IRadiationShielding`. No Mekanism event
fires on venting.

## 9. License

Mekanism is MIT: `LICENSE` says "Copyright (c) 2017-2025 Aidan C. Brady", the
mods.toml has `license="MIT"`, and the POM declares MIT. That's compatible with
zas-cubed. Naming its classes and mixing into them is fine; if any Mekanism code
is copied, keep its notice.

## Summary

| Integration | Recommended approach | Injection points | Risk | Effort |
|---|---|---|---|---|
| Chemical ↔ gas bridge | **Capability + API.** zas also registers CO₂ and N₂ chemicals. | `mekanism:chemical_handler` on adapter block entities; neighbor `insertChemical`/`extractChemical`; registry holders; a data-driven chemical-to-gas map and allow-list tag; `DeferredRegister` on `CHEMICAL_REGISTRY_NAME` | Low: public API, stable since 10.7.11 | M |
| Venting into rooms | **Mixin** (MixinExtras), config-gated, plus a no-mixin exhaust port | Wrap `shrinkStack` in `TileEntityChemicalTank#onUpdateServer`, `TileEntityElectrolyticSeparator#handleTank` and `TurbineMultiblockData#tick` (ordinals 1–2). Inject at HEAD of `ChemicalNetwork#disperse`, the `dump()` and `dumpChemical()` implementations, and `PacketDropperUse#handleChemicalTank`. | High: internal, private names and ordinals; huge rates | M–L |
| Breathing gear | **Capability + data**, no mixin | zas life-support capability on the scuba and MekaSuit items; item `mekanism:chemical_handler`; `mekanism:scuba_tank_mode`; `IModuleHelper#isEnabled`; `LivingBreatheEvent`; `#mekanism:is_preventable_magic`; `mekanism:mekasuit_absorption` | Low–Med: EBU policy, double drain | S–M |
| Heat coupling | **Capability** first (A), then optional **mixins** (B) | `mekanism:heat_handler`; HEAD `CachedAmbientTemperature#getTemperature`; HEAD `MultiblockData#calculateAverageAmbientTemperature`; `biomeAmbientTemp` refresh ×4; wrap `handleHeat` in `simulateEnvironment` (default + 3 overrides) | Med (A) / High (B) | M / L |
