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
  - `sealedBreathing` and `gasFiltering`, which count in the head slot only;
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

- **A request is a lease.** It lasts 10 game ticks (`LEASE_TICKS`). Keep asking
  while the source wants the sound, every tick if its volume or pitch changes, and
  stop asking when it doesn't. There is nothing to turn off, and a source that
  forgets stops anyway.
- **Only ask while the source exists.** A block's source checks its block is still
  there. A chunk that has left the client is the only removal the manager sees for
  itself.
- **At most 8 loops** play on a client (`MAX_LOOPS`), across every mod: the ones
  that reach the player loudest, within their ranges.
- **The range is the request's.** The volume falls in a straight line to 0 there,
  and `attenuation_distance` in `sounds.json` isn't used. Use a mono sound so it
  pans, and don't mark it `stream`.
- **Only your own sounds should fade.** A loop has `thinAir()` applied already.
  Multiply a one-shot's volume by it, and leave vanilla's sounds alone.
- **Without ZAS³**, and on a dedicated server, `thinAir()` is 1 and a loop plays
  nothing.

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
