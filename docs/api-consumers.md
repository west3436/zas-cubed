# Depending on the ZAS³ API

The API is `io.github.west3436:zas-api` on Maven Central (API-008, D-18). It
is a `GAMELIBRARY` jar: mods nest it (jar-in-jar) and can call it whether or
not ZAS³ is installed (architecture
[§2.1](architecture.md#21-gradle-projects) and
[§6.2](architecture.md#62-installation-and-fallback-api-006-api-009-api-040)).

> **No release yet.** Until the first one, only snapshots exist. They are
> built from `main`, change without notice and are deleted by Sonatype after
> 90 days. The API is `0.x` with no compatibility guarantee until R1 (API-041).

## Gradle (ModDevGradle)

```groovy
repositories {
    // Only needed for snapshots. Releases come from mavenCentral().
    maven {
        name = 'Central Portal snapshots'
        url = 'https://central.sonatype.com/repository/maven-snapshots/'
        mavenContent { snapshotsOnly() }
        content { includeGroup 'io.github.west3436' }
    }
}

dependencies {
    implementation 'io.github.west3436:zas-api:0.1.0-SNAPSHOT'

    // Nest the API with the range [your version, next major). ModDevGradle
    // reads the range only from coordinates like these (architecture §2.1,
    // rule 2). For a release, use '[0.1.0,1.0)' and prefer '0.1.0'.
    jarJar('io.github.west3436:zas-api') {
        version {
            strictly '[0.1.0-SNAPSHOT,1.0)'
            prefer '0.1.0-SNAPSHOT'
        }
    }
}
```

- **Don't list the API in `neoForge { mods { } }`.** It loads as its own
  module; listing it splits its package across two modules and the loader
  crashes (architecture §2.1, rule 3).
- **FML keeps the newest nested copy** that satisfies every mod's range, so a
  mod may nest a newer minor version than ZAS³ does. Provider methods added in
  later versions have defaults, so an older ZAS³ still answers them
  (architecture §6.2).

## The example addon

[`example-addon`](../example-addon) is a small mod built on the API alone, with a working
example of each extension point: a place where your code or data plugs in, and ZAS³ then
calls or honours it (API-047). Copy what you need. A new extension point gets a row here and
an example there.

| Extension point | Requirement | Example |
|---|---|---|
| A worn item's life support: `ILifeSupport.ITEM` | API-022 | `ExampleLifeSupport.PRESSURE_HELMET` |
| An entity's own life support: `ILifeSupport.ENTITY` | API-022 | `ExampleLifeSupport.COLD_PROOF` |
| A tank for internals: `IInternalSupply` | API-022, LIFE-028 | `ExampleLifeSupport.OxygenTank` |
| A block that stores gas: `IGasHandler.BLOCK` | API-021, API-025, API-026 | `GasTankBlockEntity` |
| A block that says what it seals: `ISealProvider.BLOCK`, `SealGeometry`, `BlockGeometry.markDirty` | API-023, API-028 | `ExampleCapabilities`, `ShutterBlockEntity`; the `airlock` block |
| A block whose shape follows its data: `dynamicShape()`, `BlockGeometry.markDirty` | GEO-043, GEO-014 | `ShutterBlock`, `ShutterBlockEntity`; the `shutter` block |
| A dimension's ambient from code: `AmbientProviders.register` | ENV-004 | `OutpostAir` |
| A hazard of your own, for a gas and for a gas tag: `HazardHandlers.register` | LIFE-032 | `ExampleHazards` |
| Adjusting or cancelling a hazard: `HazardTickEvent` | API-033 | `ExampleHazards` |
| A reaction type in code: `Reactions.registerType` | API-020 | `CatalyticConverter`, named by `zas/reaction/catalytic_converter.json` |
| A block that ignites gas: `#zas:ignition_sources` | API-053 | `tags/block/ignition_sources.json` |
| A zone's life: the eight zone events | API-030 | `AtmosphereAlarm` |
| Fires starting and stopping: `FireIgnitedEvent`, `FireExtinguishedEvent` | API-031 | `AtmosphereAlarm` |
| Gas to and from the open air: `OpenAirGasEvent` | CORE-058 | `AtmosphereAlarm` |
| Your sounds fading in thin air: `ClientSounds.fadeInThinAir` | API-060 | `ExampleClient` |

`ExampleCapabilities` registers every capability, and `ExampleAddonMod` everything else.

- **Calls aren't on the list,** since nothing of yours plugs in: `Atmospheres`, `Breathing`,
  `GasMutations`, `HeatMutations`, `ZonePins`, `Fires`, `Reactions.applyStandalone`,
  `GasProperties`, `GasTags`, `ClientSounds.loop` and `thinAir`, `DisplayUnits` and
  `ThirdPartyFaults`. Nor are `ZasApiProvider`, `ClientSoundProvider` and `DisplayUnitProvider`,
  which only ZAS³ implements.
- **Data formats aren't on the list,** apart from the tag above. They're for packs as well as
  mods, and [datapacks.md](datapacks.md) has an example of each. The addon ships what its code
  needs: a gas, `zas/gas/radon.json`; a gas tag, `tags/zas/gas/radioactive.json`; that gas in
  a shipped tag, `tags/zas/gas/toxic.json`; and a reaction.
- **Its tests.** `ExampleAddonTests` runs the examples without ZAS³, and `ExampleWithZasTests`
  with it. A GameTest server has only the vanilla dimensions, and neither has the content
  mod's pipes or a player, so no test sees ZAS³ call `OutpostAir`, a pipe fill the tank, or
  internals breathe from the oxygen tank.

## Can this entity breathe here?

`Breathing.of(entity)` is the breathability helper (API-014). Route breathing
and oxygen checks through it, such as a space mod's "has oxygen" check.

```java
Breathability breath = Breathing.of(player);
if (!breath.canBreathe()) {
    // breath.reason() says why: DROWNING, LOW_OXYGEN, ...
}
if (!breath.isProtected()) {
    // breath.hazards() lists what harms it here: COLD, LOW_PRESSURE, ...
}
```

- **Threads:** call it on the main thread of the entity's logical side, the
  server thread for a server-side entity. It throws `IllegalStateException`
  from any other thread there. On the client, use the answer for display only.
  The returned `Breathability` is immutable and may go to any thread.
- **Without ZAS³** it is vanilla's verdict (`Breathing.vanilla`). In air the
  entity breathes, and with its eyes in water it drowns unless vanilla lets it
  breathe there, through Water Breathing, Conduit Power or a type that
  breathes underwater. Nothing harms it otherwise, so it is always protected.
- **With ZAS³** it comes from ZAS³'s breathing, hazard and protection rules
  and the life-support capability (below), and while a player breathes from
  its internals the breath is its tank's gas, with source `INTERNALS`. The
  details are in architecture [§6.2](architecture.md#breathability-api-014).

## Suits and life support

An item that protects its wearer provides `ILifeSupport` (API-022) for
`ILifeSupport.ITEM`. An entity with its own protection, such as a robot,
provides it for `ILifeSupport.ENTITY`. Override only what it gives; every part
defaults to nothing:

```java
@SubscribeEvent // on the mod bus
static void capabilities(RegisterCapabilitiesEvent event) {
    ILifeSupport suit = new ILifeSupport() {
        @Override public double minPressure() { return 0; }        // to vacuum, kPa
        @Override public double maxTemperature() { return 500; }   // K
        @Override public boolean sealedBreathing() { return true; } // internals work
    };
    event.registerItem(ILifeSupport.ITEM, (stack, ctx) -> suit, MyItems.PRESSURE_HELMET.get());
}
```

- **Parts:**
  - the pressure range (`minPressure`, `maxPressure`, in kPa);
  - the temperature range (`minTemperature`, `maxTemperature`, in K);
  - `sealedBreathing`, which internals need, and `gasFiltering`, which keeps
    the `#zas:toxic` gases out of the breath: both count in the head slot only;
  - `airflowResistance`, 0 to 1;
  - `internalSupply`, a tank's gas for internals (below).
- **Tanks.** An item that holds breathable gas returns an `IInternalSupply`
  from `internalSupply()`: its `contents()` (a `GasMixture` with its volume),
  its `distributionPressure()` in kPa, and `draw(moles)`, which takes gas out
  and must change what `contents()` answers next. Internals look for one in the
  armor slots (chest first, for a suit that carries a tank), the offhand and the
  hotbar, and on the entity itself, and breathe from the first one found while
  it holds at least its distribution pressure. A suit with a tank slot returns
  the tank in it, and saves the draw back to itself.
- **Coverage.** A worn piece covers its armor slot's share of the body: by
  default 0.3 for the head, 0.4 for the chest, 0.2 for the legs and 0.1 for the
  feet. An entity's own life support covers all of it. The pieces rated for the
  conditions add up to a coverage c. Then (1 − c⁵) of the pressure difference
  from 1 atm reaches the wearer, and (1 − c) of temperature damage.
- **Items you can't change** can get the same parts from a datapack instead
  ([datapacks.md](datapacks.md#protection)).
- **Threads:** ZAS³ calls it on the server thread, at each check (every 2 s by
  default), so keep it cheap. Without ZAS³ the capabilities still exist, but
  nothing reads them.

## Adjusting or cancelling a hazard

`HazardTickEvent` fires just before a hazard harms an entity (API-033), on the
server thread, so a suit or perk of your own can reduce it or stop it.

```java
NeoForge.EVENT_BUS.addListener((HazardTickEvent e) -> {
    if (e.hazard() == Hazard.COLD && hasMyThermalPerk(e.entity())) {
        e.setDamage(e.damage() * 0.5);
    }
    if (e.hazard() == Hazard.HYPOXIA && hasMyLungs(e.entity())) {
        e.setCanceled(true);
    }
});
```

- **One event per hazard, each time it would harm the entity:** at each breath
  check for `TOXIC_GAS`, `LOW_PRESSURE`, `HIGH_PRESSURE`, `COLD` and `HEAT`, once a
  second on an empty meter for `HYPOXIA`, and at each blow for `AIRFLOW_IMPACT`.
- **The damage is in HP,** for all the time since the last check, after protection
  and the hazard's scale. A `TOXIC_GAS` tick can have 0 damage if its gas only
  applies status effects so far.
- **Cancelling** deals no damage and, for `TOXIC_GAS`, applies no status effects.
  Nothing else changes: exposures go on counting, `Breathing.of` still lists the
  hazard, and a hypoxic entity's meter stays empty. To keep a gas's effects and
  drop its damage, set the damage to 0.
- **Without ZAS³** it never fires.

## Adding a hazard of your own

A `HazardHandler` runs your code when a gas reaches an entity (LIFE-032), for example
radiation from a radioactive gas you define. Register one for a gas or a gas tag.

```java
HazardHandlers.register(
        ResourceLocation.fromNamespaceAndPath("mymod", "radiation"),
        MY_RADIOACTIVE_GAS,
        exposure -> {
            double dose = exposure.surroundingPressure() * exposure.seconds();
            if (dose > 0.1) {
                exposure.entity().hurt(radiationDamage(exposure.entity()), (float) dose);
            }
        });
```

- **When:** on the server thread, at each breath check of a living entity, every 2 s by
  default. It's called once for each of its gases in the entity's breath or the atmosphere
  at its eyes, and a handler for a tag is called for each of the tag's gases there.
- **How much reaches it:** `surroundingPressure()` is what's around it and
  `breathedPressure()` is what it breathes, in kPa. They differ behind a filter or on a
  tank, so a handler is called for a gas a filter keeps out, and decides what that means.
  An entity that doesn't breathe breathes 0. With its eyes in a fluid nothing is around it,
  so a handler hears only of a tank's gas.
- **Scale by `seconds()`,** so the check interval changes smoothness, not speed.
- **What it does is yours.** ZAS³ deals nothing for it, so `[hazards]` settings and
  `HazardTickEvent` don't reach it. Any setting to turn it off or scale it is yours to add.
- **Not called for** creative and spectator players, a check that charges no time, as an
  entity's first does, a check waiting for the atmosphere to load, or an entity that has died.
- **Without ZAS³,** handlers register and are never called.

## Gas handlers

A block that stores gas provides `IGasHandler` (API-021), so others can fill and
drain it. The context is the face being asked about, or null for no face in
particular:

```java
@SubscribeEvent // on the mod bus
static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(IGasHandler.BLOCK, MyBlocks.TANK_ENTITY.get(), (tank, side) -> tank);
}
```

- **Methods:**
  - `contents()`: the moles, temperature and volume it holds now;
  - `fill(gas, action)`: returns what it took, no more of any gas than it was
    offered;
  - `drain(moles, action)`: returns what it gave, at its temperature.
- **Modes.** `Action.SIMULATE` changes nothing and says what `Action.EXECUTE`
  would do now.
- **Pipes.** ZAS³'s pipes and devices connect to any block that provides it
  (API-025). A pipe grows an arm toward it, and a device connects on the faces it
  would join a pipe on. A canister connects only to a connector port, so put a
  port between one and a handler. The handler doesn't join the network and adds
  no volume to it.
- **Transfers.** Each atmos tick, the node on that face moves each gas toward an
  equal concentration on both sides, as far as your handler lets it, whichever
  way that is (API-026). What you refuse stays in the network. ZAS³ executes
  directly, without simulating first. It asks for `contents()` each atmos tick,
  so keep that cheap, and calls `fill` and `drain` only when gas has to move.
  While your chunk is away, it calls nothing.
- **Conservation.** ZAS³ takes what it offers out of the network first and takes
  back what you don't say you took, and it adds all that `drain` returns. Gas is
  conserved as long as your returns are true.
- **If it comes or goes.** ZAS³ looks the capability up anew each atmos tick, so a
  pipe's arm follows it within an atmos tick. Other mods may cache it, so call
  `level.invalidateCapabilities(pos)` when it changes.
- **Threads:** ZAS³ makes every call on the server thread. Without ZAS³ or its
  content mod the capability still exists, but nothing here calls it.

## Keeping a zone: pins

A zone that matches the ambient can be dissolved (CORE-052, CORE-076). To keep
one, for a room your block looks after, pin it (API-017, API-050). Pinning a
point in a sealed room with no zone forms one there, holding the ambient's air.

```java
SourceId owner = SourceId.of("mymod", "habitat/" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ());
ZonePins.pin(level, Vec3.atCenterOf(pos.above()), owner).thenAccept(result -> {
    // result.outcome(): ZONE (result.zone() names it), OPEN, UNRESOLVED or ABSENT
});
// When the block is removed:
ZonePins.unpin(level, owner);
```

- **One owner id per holder.** A zone stays pinned while any owner pins it,
  and `unpin` takes off every pin of one owner, including those on parts of a
  zone that has split since. Pinning again under the same id changes nothing,
  so it is safe to pin again whenever your block loads.
- **In the open** the result is `OPEN` and nothing is pinned; pin again once
  the room is sealed. `UNRESOLVED` means the region reaches an unloaded chunk.
- **Threads:** server thread only, like mutations. The futures complete on the
  server thread at the next atmos tick. Pins are saved with their zone.
- **Without ZAS³** a pin completes at once with `ABSENT`.

## Zone events

ZAS³ tells you as a zone's life changes (API-030). Each event is fired on
`NeoForge.EVENT_BUS`, on the server thread, when the atmos tick that caused it is
applied, and fires once per change. Each has `level()`, `zone()` and `pos()`, a
block in the zone, or where it was if it's gone.

| Event | When |
|---|---|
| `ZoneCreatedEvent` | A zone formed, for gas added to a sealed room or a pin |
| `ZoneMergedEvent` | Two zones became one. `zone()` is the survivor and `absorbed()` the id that's gone |
| `ZoneSplitEvent` | A zone was cut in two. `zone()` keeps its id and `remnant()` is the new one, which gets no `ZoneCreatedEvent` |
| `ZoneBreachedEvent` | A sealed zone gained an opening to the ambient |
| `ZoneSealedEvent` | A breached zone lost its last opening to the ambient |
| `ZoneDissolvedEvent` | A zone is gone along with its gas, not by merging |
| `ZonePausedEvent` | A zone stopped being simulated: part of it is unloaded, say |
| `ZoneResumedEvent` | A paused zone is simulated again |

```java
@SubscribeEvent
public static void onBreach(ZoneBreachedEvent event) {
    alarms.sound(event.level(), event.pos());
}
```

Listen for the event you want: `ZoneLifecycleEvent` is only their shared base, and the
bus won't take a listener for it.

A zone coming back from chunk storage fires `ZoneResumedEvent`, not
`ZoneCreatedEvent`. That means after a restart it fires with no `ZonePausedEvent`
before it, so treat the two as a state to follow, not as a pair to count.

## A block that says what it seals: seal providers

ZAS³ works out what a block seals from its collision shape. A block that decides
for itself, such as an airlock, gives a seal provider instead (API-023):

```java
@SubscribeEvent
static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlock(
            ISealProvider.BLOCK,
            (level, pos, state, blockEntity, context) -> () ->
                    Optional.of(blockEntity instanceof AirlockEntity airlock && airlock.isOpen()
                            ? SealGeometry.OPEN
                            : SealGeometry.SEALED),
            MY_AIRLOCK.get());
}

// Whenever the answer changes without a block state being written:
BlockGeometry.markDirty(level, pos);
```

- **The answer** is a `SealGeometry`: the gas-tight faces, the faces that keep
  zones apart while gas crosses them, as a door's do, and the volume in litres,
  0 to 1,000. It is a single cell, as with a datapack geometry override. An empty
  `Optional` means the geometry from the block's collision shape.
- **It is kept.** ZAS³ asks once, on the server thread, when it copies the block's
  section for an atmos tick (API-028), and keeps the answer until the block state
  at that position is written or you call `markDirty`. So keep the provider cheap,
  and call `markDirty` whenever its answer would change. The change reaches zones
  at the next atmos tick.
- **A datapack override wins.** A geometry override of the block has the last
  word, and the provider isn't called.
- **Register at any priority but the lowest**, which is when ZAS³ notes the blocks
  that have a provider.
- **Threads:** `markDirty` is server thread only, and throws otherwise.
- **Without ZAS³** nothing asks the provider, and `markDirty` does nothing.

## A block whose shape depends on where it is or on its data

Give the block `BlockBehaviour.Properties.dynamicShape()`, as Minecraft asks of
any block whose collision shape isn't fixed by its block state. ZAS³ then reads
the shape in the level at each position, with an empty collision context, and
derives the block's gas cells from it (GEO-043):

- **It is kept**, as a seal provider's answer is: read once on the server thread
  when the block's section is copied for an atmos tick, until the block state at
  that position is written or you call `BlockGeometry.markDirty(level, pos)`
  (GEO-014). So call `markDirty` whenever the data behind the shape changes.
- **It isn't followed while it moves.** Only what is read counts, so a shape that
  animates may be caught mid-motion (GEO-042). Give such a block a seal provider
  that answers with the state it settles into.
- **A datapack override wins**, and the shape isn't read.

## Supplying a dimension's ambient

A mod can give a dimension its open air from code, such as a planet's thin air
or a temperature that follows the time of day (ENV-004). Register a provider
for the dimension from common setup, or whenever the dimension appears:

```java
ResourceKey<Level> dimension = ResourceKey.create(
        Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("mymod", "mars"));
AmbientProviders.register(
        ResourceLocation.fromNamespaceAndPath("mymod", "mars_air"),
        dimension,
        level -> new AmbientDefinition(
                Map.of(ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide"), 0.6),
                210 + 20 * Math.sin(level.getTimeOfDay(1) * 2 * Math.PI)));
```

`AmbientDefinition` holds partial pressures in kPa and a temperature in K.
`AmbientDefinition.vacuum(temperature)` and `AmbientDefinition.standardAir(temperature)`
cover the common cases.

- **Who wins** (ENV-014, D-45): a datapack definition of the dimension, then a
  provider, then an ambient seeded from another mod's data (R2), then standard
  air. Pack authors have the final say, so a datapack file overrides your
  provider. Return `null` to define nothing for now: the next source applies.
- **Two providers for one dimension** (ENV-019, ENV-020): the highest priority
  applies (`register` takes one; the default is 0). A tie goes to the
  lexicographically smallest mod id, the namespace of the provider's id. Only
  the one that applies is called, and ZAS³ logs one warning naming them all.
- **Threads and timing** (ENV-015): ZAS³ calls the provider once per atmos tick
  of its dimension, on the server thread, as the tick's batch closes, even while
  a datapack definition overrides it. Its answer holds until the next one, for
  the simulation and for every query, and queries never call it. Keep it cheap,
  since it counts against the server thread's budget, and don't load chunks from
  it.
- **Gases** must exist on the server. An answer naming an unknown gas is skipped
  with a warning, as if the provider had returned `null`.
- **Position:** a provider answers for a whole dimension. Ambients that vary by
  position are for R2 (ENV-005).
- **Without ZAS³** the provider is kept and never called, and every point stays
  standard air.

## Fire

A fire in a zone burns as hotspots, one per block: a vanilla fire block in the
zone, or a `zas:gas_fire` block in mid-air (FIRE-018, FIRE-019). Each burns the
zone's flammable gas. `Fires` finds them, starts them and puts them out
(API-055 to API-058, Draft until the owner's review).

```java
// Ignite the gas at a block, say from your igniter (server thread):
Fires.ignite(level, pos, SourceId.of("mymod", "igniter")).thenAccept(ignition -> {
    // ignition.outcome(): IGNITED or BURNING (ignition.hotspot() is the fire),
    // NOT_FLAMMABLE, NO_SPACE, OPEN, PAUSED or ABSENT
});
// Put out every fire in a box of up to 4,096 blocks, now (server thread):
int removed = Fires.extinguish(level, pos.offset(-1, -1, -1), pos.offset(1, 1, 1));
// Ask, from any thread:
boolean burning = Fires.isBurning(level, zone);
```

- **An ignition** tries the block, then its six neighbours, and starts a fire
  at the first that is plain air in a zone whose gas will burn. `NO_SPACE`
  means the zone would burn but none of them is plain air.
- **Putting out** a fire leaves the gas, so a fire nearby can spread back and
  a zone hot enough lights again by itself.
- **Events:** `FireIgnitedEvent` when a zone's first hotspot appears and
  `FireExtinguishedEvent` when its last goes, on the server thread, once per
  zone (API-031).
- **Timing.** When an ignition's future completes, its gas fire block is
  placed, but the queries and the event see the fire only from the next atmos
  tick, the first to see the block: until then `isHotspot` is false there.
- **Without ZAS³** there are no hotspots, an ignition completes at once with
  `ABSENT` and `extinguish` removes nothing.

## Sound on the client

`ClientSounds` plays looping sounds and tells you how much thin air leaves of a
sound (API-060 and API-061, Draft until the owner's review). It refers to no
client-only class, so it is safe to call from code a server also loads.

```java
// A machine's hum, from a client tick, for as long as the machine is running:
if (machine.isRunning()) {
    ClientSounds.loop(new LoopRequest(
            pos.immutable(), MY_HUM.value(), SoundSource.BLOCKS,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            0.4f, 1.0f, 8.0));
}
// A one-shot of your own, quietened by thin air:
level.playLocalSound(pos, MY_CLANK.value(), SoundSource.BLOCKS, 0.8f * (float) ClientSounds.thinAir(), 1.0f, false);
```

- **A request is a lease.** It lasts 4 game ticks (`LEASE_TICKS`). Keep asking
  every tick while the source wants the sound, and stop asking when it doesn't.
  There is nothing to turn off, and a source that forgets stops anyway.
- **Only ask while the source exists.** A block's source checks its block is still
  there. A chunk that has left the client is the only removal the manager sees for
  itself.
- **At most 8 loops** play on a client (`MAX_LOOPS`), across every mod: the ones
  that will play loudest at the player, within their ranges. That counts your
  sound's volume in `sounds.json` and the player's sliders, so a level set there
  ranks as it plays.
- **The range is the request's.** The volume falls in a straight line to 0 there,
  and `attenuation_distance` in `sounds.json` isn't used. Use a mono sound so it
  pans, and don't mark it `stream`.
- **Only your own sounds should fade.** A loop has `thinAir()` applied already.
  Multiply a one-shot's volume by it, and leave vanilla's sounds alone.
- **Without ZAS³**, and on a dedicated server, `thinAir()` is 1 and a loop plays
  nothing.

## Display units on the client

The API's values are always in kPa and K. `DisplayUnits` converts what you show to
the units the player chose (API-063, Draft until the owner's review). It refers to no
client-only class, so it is safe to call from code a server also loads.

```java
String shown = String.format("%.1f %s", DisplayUnits.pressure(kpa), DisplayUnits.pressureSymbol());
```

Without ZAS³, and on a dedicated server, the answers are in kPa and °C.

## If your code throws

ZAS³ catches what your ambient provider, seal provider, code reaction, gas handler,
hazard handler or event listener throws, and the server carries on (NFR-013). That code is skipped
for the atmos tick and called again the next, and an event listener misses only that
event. The fault is logged at most once a minute for each source, so one line in the
log can stand for many faults.

An ambient provider's last good answer for that dimension stands while it throws, so
a vacuum stays a vacuum, and one that keeps throwing freezes that answer. The next
source applies only if it has given no good answer since the server started. `null` is
an answer and not a fault, so throwing after one leaves the next source in place.

An `ILifeSupport` or `IInternalSupply` that throws is caught too. A part of a life support
that throws gives nothing for that call, so a suit that throws leaves its wearer exposed,
and a supply that throws delivers nothing, so its user breathes the atmosphere.

To limit your own logging the same way, ask `ThirdPartyFaults.shouldLog(source)`
before you log. It's true at most once a minute for each source, on any thread (API-062).

## Versioning and the compatibility check

The API has its own semver, separate from the mod version (API-002). `check`,
and so `build` and CI, runs `:zas-api:apiCompat` (API-043, NFR-033). It
compares the built jar with the release named by `api_baseline_version` in
[`gradle.properties`](../gradle.properties), using
[japicmp](https://github.com/siom79/japicmp), and fails on a binary-
incompatible change unless `api_version` allows one:

| `api_version` against the baseline | Binary-incompatible change |
|---|---|
| Same major and minor (a patch, or unchanged) | Fails |
| New minor, both `0.x` | Allowed and reported |
| New major (from 1.0) | Allowed and reported |

API-041 promises nothing for `0.x`. Requiring a minor bump for a break is
stricter than that, and follows semver's convention that a `0.x` minor
version may break. The HTML report is in `zas-api/build/reports/apiCompat/`.

From 1.0, anything removed is deprecated first, for at least one minor version
(API-042): mark it `@Deprecated` in a minor version, with a `@deprecated` tag
saying what to use instead, and remove it in a later major. The check fails on
a removal the baseline didn't mark, even in a new major. It sees only the
baseline, so it can't tell which release added the mark: don't add one in a
patch.

With `api_baseline_version` empty, as now (nothing has been released), the
check is skipped with a notice. After each release, set it to the released
version. A set but unresolvable baseline fails the build.

To try the check without Central, publish a baseline to a local directory and
point the check at it:

```sh
./gradlew :zas-api:publishToMavenLocal -Dmaven.repo.local=/tmp/zas-baseline
./gradlew :zas-api:apiCompat -Papi_baseline_version=0.1.0 -Papi_baseline_repo=/tmp/zas-baseline
```

## Publishing (maintainers)

Publishing uses the
[`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
plugin with the Sonatype Central Portal. CI's "Publish API snapshot" job runs
`./gradlew :zas-api:publishToMavenCentral -PapiSnapshot=true` after each green
build on `main`, and skips with a notice when the secrets are missing. It
reads these repository secrets:

| Secret | Gradle property | Required |
|---|---|---|
| `CENTRAL_USERNAME` | `mavenCentralUsername` | Yes: the Portal user token's username |
| `CENTRAL_PASSWORD` | `mavenCentralPassword` | Yes: the Portal user token's password |
| `SIGNING_KEY` | `signingInMemoryKey` | For releases: an ASCII-armored private key |
| `SIGNING_PASSWORD` | `signingInMemoryKeyPassword` | If the key has a passphrase |

Without `-PapiSnapshot=true` the version is the release version, and
`publishToMavenCentral` uploads a signed deployment that is then published by
hand on the Portal. Release publishing is part of R1.
