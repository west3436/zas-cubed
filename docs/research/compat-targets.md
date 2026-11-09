# Compat targets beyond Mekanism and the space mods

> **Researched:** 2026-11-06. Versions, dates and download counts are Modrinth's
> for NeoForge 1.21.1 on that date. CurseForge wasn't checked, so the counts
> undercount some mods. Class names come from each mod's source and are cited as
> API facts; no third-party code is reproduced. "(code-read)" marks behavior
> inferred from source, not tested in game.

## Implications for zas-cubed

- **The targets (D-78) are in [COMPAT](../requirements/52-compat-mods.md).** Each
  adapter lives in this repo and asks nothing of the target's authors.
- **All of Sable's lift has one input.** Balloons, wings and propellers read one
  pressure call, so a single factor covers them.
- **Jade could replace the analyzer** if it read plain air, so COMPAT-032 limits it
  to devices.
- **Curios needs a core change.** `Internals` looks only in armor slots, the offhand
  and the hotbar, and `WornProtection` only in armor slots.

## Targets

| D-78 | Mod | Build, date | Downloads | Licence | Hook |
|---|---|---|---|---|---|
| For sure | PneumaticCraft: Repressurized | 8.2.24, 2026-10-29 | 0.81M | LGPL | `me.desht.pneumaticcraft.api.PNCCapabilities`: `AIR_HANDLER_MACHINE`, `AIR_HANDLER_ITEM`, `HEAT_EXCHANGER_BLOCK` |
| For sure | CC: Tweaked | 1.121.0, 2026-11-03 | 5.36M | CCPL | `dan200.computercraft.api.peripheral.PeripheralCapability` |
| For sure | Jade | 15.10.6, 2026-08-06 | 73.36M | CC-BY-NC-SA-4.0 | `snownee.jade.api.IWailaPlugin` |
| For sure | Curios API | 9.5.1, 2025-05-14 | 33.39M | LGPL-3.0+ | `CuriosApi.getCuriosInventory` ([neoforge §6](neoforge-1.21.1.md)) |
| For sure | Accessories | 1.1.0-beta.54, 2026-10-16 | 15.02M | MIT | `io.wispforest.accessories.api.AccessoriesCapability` |
| For sure | KubeJS | 2101.7.2-build.381, 2026-11-01 | 22.73M | LGPL-3.0 | `dev.latvian.mods.kubejs.plugin.KubeJSPlugin`, `event.EventGroupRegistry`, `script.BindingRegistry` |
| For sure | JEI | 19.70.0.469, 2026-11-01 | 84.78M | MIT | `mezz.jei.api.IModPlugin` |
| For sure | EMI | 1.1.24, 2026-05-13 | 30.73M | MIT | Loads JEI plugins through `dev.emi.emi.jemi.JemiPlugin` |
| Very likely | Applied Energistics 2 | 19.2.18, 2026-09-27 | 6.33M | Mixed | `appeng.api.stacks.AEKeyType`, `AEKeyTypes`, `AEKey`. Applied Mekanistics (1.6.3, 0.86M) stores chemicals in ME, so it is the precedent. |
| Very likely | Create Aeronautics | 1.3.2, 2026-08-29 | 9.29M | Code MIT, assets ARR | Reads Sable's pressure (below) |
| Likely | Modern Industrialization | 2.5.12, 2026-11-02 | 2.06M | MIT | `MIFluids` flags its gases (below) |
| Maybe | Sable | 2.0.6, 2026-10-03 | 6.47M | PolyForm Shield 1.0.0 | Below |
| Maybe | Create: The Factory Must Grow | 1.2.0, 2026-01-07 | 1.94M | "MIT NON-AI" | Its public pack docs list engine, flamethrower and fire extinguisher fuels. Its source wasn't read. |
| No | Create | 6.0.10, 2026-04-21 | 27.92M | Code MIT, assets ARR | None (COMPAT-120) |
| D-85 | [EMF] Entity Model Features | 3.3.12-neoforge-1.21, 2026-10-15 | 105.15M | LGPL-3.0-only | `traben.entity_model_features.EMFAnimationApi` (below) |

**No NeoForge 1.21.1 build on Modrinth:** the Thermal series, Valkyrien Skies,
Tinkers' Construct and Blue Skies. Alex's Caves has only unofficial ports.

## Notes

### PneumaticCraft

`IAirHandlerMachine` has `setSideLeaking` and `getSideLeaking`, and documents a leak
of `pressure * 40 + 20` mL a tick. The interface has no leak event, so an adapter
has to poll known handlers or find another way to learn of a leak. Its comment says
not to implement it, but to get one from `IAirHandlerMachineFactory`.

### Sable and Create Aeronautics

`DimensionPhysicsData.getAirPressure(Level, Vector3dc)` returns `base_pressure`
times a bezier altitude curve, from the
[`dimension_physics`](https://github.com/ryanhcode/sable/blob/HEAD/wiki/Dimension%20Physics%20Data.md)
datapack. Its callers include:
- `ServerBalloon`, whose force follows the gradient of that pressure, so a factor
  scales it (code-read);
- `BlockSubLevelLiftProvider`, for lift and drag on wings (code-read);
- `BlockEntityPropeller`, `FloatingBlockData`, `HotAirBurnerBlockEntity` and
  `SteamVentBlockEntity` (found by search, not read).

So the Aeronautics adapter compiles against Sable as well, even though zones on
moving structures are only a maybe. The class is outside Sable's `api` package.
There are two ways to apply a factor, which a spike should choose between:
- `putPhysics` is public and needs no mixin. A reload clears what it sets, and
  clients learn of it through `ClientboundDimensionPhysicsPacket`.
- A mixin on `getAirPressure` is simpler to keep correct, but is a mixin into Sable
  (COMPAT-003).

Sable's licence bars only providing a product that competes with Sable, or with a
product its licensor provides using it. ZAS³ doesn't compete with either.

A sub-level has a `LevelPlot`, and callers use `Sable.HELPER.projectOutOfSubLevel`
to get a world position from a block position. So its blocks appear to sit in the
level at plot coordinates (code-read).

### Modern Industrialization

`MIFluids` flags as gases, among others: oxygen, nitrogen, hydrogen, methane, steam,
argon, helium, chlorine, acetylene and ethylene. ZAS³'s built-in gases cover the
first five (steam as water vapor). Whether they carry `#c:` tags wasn't checked.

### Create

Create gets no adapter (COMPAT-120), but the two mods can still meet. Create's diving
helmet listens to `LivingBreatheEvent` at `NORMAL`, as `Breathers` does, so the
result depends on the order they run in (code-read). If the helmet runs second, it
spends tank air and restores breathing, which stops hypoxia. If it runs first, it
sees "can breathe" and does nothing. This qualifies the
[space research's](space-mods-and-prior-art.md#a1-available-on-neoforge-1211) claim
that diving gear works automatically under Cosmonautics.

### Entity Model Features

*Added 2026-11-09 for D-85; Modrinth's figures are from that date.* EMF is client-only
and needs Entity Texture Features. These notes read its `master` branch, which builds
every Minecraft version it supports through `//#if MC` blocks (code-read):
- `registerAnimationHook` takes an `EMFAnimationHook`. Its `onAnimationEnd` is given
  the model root after the pack's animation has run, so a tumble can be added to a
  Fresh Animations model without replacing the pack's `.jem` files (COMPAT-131).
- `registerSingletonAnimationVariable` takes a `BooleanSupplier` or a
  `Supplier<Float>`. The supplier reads `getCurrentEntity()` to find the entity being
  drawn (COMPAT-132).
- `pauseCustomAnimationsForThesePartsOfEntity` can stop a walk cycle while the body
  is in the air.

Turning the whole body doesn't need EMF, since a pose-stack rotation works on vanilla
models. What EMF adds is that packs can animate the limbs.

Whether a body was flung can be judged on the client. `HeldFlows` already holds the
flows within 32 blocks (DATA-015), so nearby entities need no new packet. A flag
synced from the server would work at any range, but it costs a packet.

Its repository has no contribution policy in its top-level files.

### Upstream policies

COMPAT-001 keeps adapters here, so nothing goes upstream. Don't send these projects
pull requests from this project:
- **Create, Sable and the Simulated Project** (Create Aeronautics): each repo's
  `AGENTS.md` states a no-LLM contribution policy and discourages LLM tools for
  understanding the code.
- **CC: Tweaked:** `CONTRIBUTING.md` doesn't welcome generative AI contributions.
- **EMI:** `CONTRIBUTING.md` asks for no LLM-generated code.
- **TFMG:** its
  [licence](https://metallurgists-of-create.github.io/about-us/licence) restricts
  use in machine-learning training and datasets, and the forks of its code. Its
  [stance page](https://metallurgists-of-create.github.io/about-us/ai-stance) also
  refuses AI-assisted contributions.

Only top-level docs were searched, so a project missing from this list may still
have a policy.
