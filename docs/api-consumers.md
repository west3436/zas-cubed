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
- **For now ZAS³ answers the same way.** The full verdict, from ZAS³'s
  breathing, hazard and protection rules and the life-support capability,
  comes with LIFE ([#34](https://github.com/west3436/zas-cubed/issues/34) to
  [#36](https://github.com/west3436/zas-cubed/issues/36)) and API-022. Until
  then the answer ignores the atmosphere, even in a vacuum. The details are in
  architecture [§6.2](architecture.md#breathability-api-014).

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
