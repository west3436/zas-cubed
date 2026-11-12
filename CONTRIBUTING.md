# Contributing to ZAS³

Thanks for helping. This guide covers the one rule that can't bend (clean-room
code), how to build and test, and how changes get merged.

## The clean-room rule

ZAS³ is MIT-licensed. vgstation13, which inspired it, is GPLv3/AGPLv3. To keep
the licenses apart, **no vgstation13 code enters this repository, whether
copied or translated** (D-01, NFR-040).

- **Allowed:** vg's *behaviour*, its algorithms described in your own words,
  and physical constants. The archived
  [research notes](https://github.com/west3436/zas-cubed/tree/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research)
  describe vg in prose for exactly this reason.
- **Not allowed:** pasting DM code, porting it line by line into Java, or
  keeping its structure, names and comments while changing the syntax. A
  translation is still a copy.
- **How to work:** read a description in prose, such as a research note,
  close the vg source, then write the code from the description. If none says
  enough, write one in prose first, in the issue.
- **The same goes for** the wiki, issues and comments: never quote vg code.

Every PR confirms this with the first checkbox in the
[PR template](.github/pull_request_template.md) (NFR-043). A reviewer who
suspects a copy asks for a rewrite from the description.

## Using AI

Using AI tools to write code, docs, tests or assets is fully acceptable. The
one condition: **if a PR includes a player-facing change, it must show how a
human tested that change in game.** Player-facing means anything a player
can see, hear or feel, such as blocks, items, gas behaviour, hazards, the HUD
or messages.

In the PR template's "Human testing" section, say:

- what you tested, in a dev client, on a dedicated server, or both
- the steps you took and what happened
- evidence: screenshots, a short video or log excerpts

Passing GameTests and unit tests don't replace this for player-facing
changes, though they're still expected. AI-written code follows the same
rules as any other code, the clean-room rule included.

## Assets

Code is MIT; assets are CC BY-SA 4.0 International, under
[`LICENSE-ASSETS`](LICENSE-ASSETS) (D-39, D-56). Assets can be drawn from
scratch, derived from vgstation13, or taken from anywhere else whose license
allows it (D-62). Every file under a project's `src/*/resources/assets/`,
or under the `assets/` of a built-in resource pack in
`src/*/resources/resourcepacks/`, needs an entry in [`CREDITS`](CREDITS) (NFR-045). An entry is a block of
`key: value` lines, starting with the file's path from the repository root
and its `origin`; blank lines separate entries:

- **`origin: original`:** drawn from scratch. Nothing else is needed.
- **`origin: vgstation13`:** `source` (the `.dmi` path in the vg
  repository), `state` (the icon state) and `commit` (the vg commit hash). A
  sound's `source` is its path under vg's `sound/`, with no `state`, and
  `adapted` says how the file was changed (the archived
  [sounds note](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research/vg-sounds.md)). Use
  only vg's CC BY-SA 3.0 US assets, never the CC BY-NC-SA `goon/` folder
  (NFR-041).
- **`origin: other`:** `source` (a URL, or a description), `license` (an
  SPDX id: `CC0-1.0`, `CC-BY-3.0`, `CC-BY-4.0`, `CC-BY-SA-3.0` or
  `CC-BY-SA-4.0`, the licenses that can be adapted under CC BY-SA 4.0) and
  `attribution` (the author to credit; not needed for `CC0-1.0`). A font may
  instead keep its own license, for now only `Bitstream-Vera`, with the license
  text under `META-INF/licenses/` (D-76).

```
path: zas-atmos/src/main/resources/assets/zas_atmos/textures/block/canister.png
origin: vgstation13
source: icons/obj/atmos.dmi
state: yellow
commit: 0123456789ab
```

Leave out any asset whose origin or license is unclear. The full format is
at the top of `CREDITS`. Check it with `python scripts/check_credits.py`
(Python 3, no dependencies). It lists asset files with no entry, entries for
files that don't exist, malformed entries, `goon/` sources and licenses that
aren't allowed. CI runs it in "Build and test".

No NonCommercial or NoDerivatives asset ships in any jar (NFR-046). RE:SS3D and Stationeers are
reference and inspiration only.

Models are Minecraft's own JSON block and item models: boxes on the
1/16-block grid, with textures at 16 pixels per block (D-67). Make them in
[Blockbench](https://www.blockbench.net/) or write them by hand. Handheld
items are flat item sprites, and wearables use the standard armor texture
layout. Don't use OBJ or other free-form meshes. vg's sprites are drawn in a
three-quarter view, so block faces are redrawn from them, while item sprites
carry over more directly. Model files are assets too, so each one needs its
own `CREDITS` entry.

## Building and testing

You need a JDK to run Gradle (17 or newer); the build fetches Java 21 for the
mods itself.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, runs the engine's and the API's unit tests, and checks formatting, the API's packages and dependencies, its javadoc and its compatibility |
| `./gradlew runGameTestServer` | Boots dedicated GameTest servers and runs the GameTests, including S-12 (the example addon without `zas`). `zas-atmos`'s server also repeats `zas`'s tests with the content mod loaded; add `-Pgametests.ownOnly=true` to leave those out, as CI does on a PR. The [Full GameTests](https://github.com/west3436/zas-cubed/actions/workflows/full-gametests.yml) workflow runs them nightly |
| `./gradlew :dist:jar` | Builds the release jar, `dist/build/libs/zas-<ver>.jar`: `zas` and `zas_atmos` as two mods in one file (D-88). It also holds the Ponder scenes, which load only with Ponder installed. The Ponder addon's jar, which only nests Ponder and Flywheel, is `zas-ponder/build/libs/zas_ponder-<ver>.jar` |
| `./gradlew :dist:runReleaseCheckGameTestServer :dist:runContentOffGameTestServer` | Loads the built release jar on a GameTest server, once with the content on and once with `zas_atmos-startup.toml` turning it off, and checks what loads (CONT-052, CONT-155); not part of `build` or `runGameTestServer` |
| `./gradlew :compat-mekanism:runAdapterGameTestServer` | Loads the built release jar on a GameTest server with the newest Mekanism for 1.21.1 and runs the Mekanism adapter's GameTests (MEK-003). `-Pmekanism.version=floor` uses the oldest one the adapter supports, `-Pmekanism.version=<version>` any published build, such as `1.21.1-10.7.17.83`, and `-Pmekanism.version=unsupported` the last one before the oldest, where the tests check that the adapter stays off (MEK-001). The [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml) workflow runs the oldest, the newest and the unsupported one; not part of `build` or `runGameTestServer` |
| `./gradlew :compat-mekanism:runAdapterGameTestServer -Pcontent=off` | The same run with `zas_atmos-startup.toml` turning the content off (CONT-155): the game has to start with Mekanism installed and nothing of `zas_atmos` registered, and the tests that build with the content are left out. `-Pcontent=off` does the same for any adapter's `runAdapterGameTestServer`. The [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml) workflow runs it for Mekanism, Ad Astra, Jade, Curios and Accessories; not part of `build` or `runGameTestServer` |
| `./gradlew :compat-jei:runAdapterGameTestServer :compat-emi:runAdapterGameTestServer` | Loads the built release jar on a GameTest server with JEI, then with EMI, and runs the recipe viewer plugins' GameTests (COMPAT-060 to COMPAT-062). Each uses the oldest version of its viewer that the plugin supports; `-Pjei.version=19.70.0.469` or `-Pemi.version=1.1.24` picks another, and `-Pjei.version=unsupported` or `-Pemi.version=unsupported` the newest one below the oldest, where the tests check that the plugin stays off (COMPAT-002). The [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml) workflow runs the oldest, the newest and the unsupported one of each; not part of `build` or `runGameTestServer` |
| `./gradlew :compat-slots:runAdapterGameTestServer` | Loads the built release jar on a GameTest server with the newest Curios for 1.21.1 and runs the Curios adapter's GameTests (COMPAT-040, COMPAT-042, COMPAT-043). `-Pslots.run=<name>` picks what the server has instead: `accessories` the newest Accessories (COMPAT-041), `curios-floor` and `accessories-floor` the oldest build of each that its adapter supports, `curios-old` and `accessories-old` one older than that, where the tests check that the adapter stays off (COMPAT-002), `both` the two mods together, and `layer` Accessories with its compatibility layer for Curios, where the tests check that each worn item is read once. The [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml) workflow runs all eight, and `curios` and `accessories` with `-Pcontent=off` too; not part of `build` or `runGameTestServer` |
| `./gradlew :example-addon:runOneCopyGameTestServer` | Loads the built release jar and a built copy of the example addon, each nesting the API, on one GameTest server, and checks only one copy of the API loads (API-007); not part of `build` or `runGameTestServer` |
| `./gradlew :engine:bench` | Runs the headless engine benchmarks ([results](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research/benchmarks.md), archived); not part of `build` |
| `./gradlew :s10-bench:s10Bench` | Runs S-10 in a dedicated server with both mods, about 20 minutes, and writes `s10-report.md` (NFR-001, NFR-002, NFR-004, NFR-005, DATA-006; [how](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research/benchmarks.md#the-in-game-s-10-bench), archived); `-Ps10.quick=true` checks the harness in a few minutes; not part of `build` |
| `./gradlew :engine:benchCheck` | Runs the S-10 script on this code and on the engine at the commit in `engine/s10-baseline.properties`, side by side, and fails if either the worker or the server thread costs more than 10 % over it in two runs, the second in a new JVM (NFR-034; [method](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research/benchmarks.md#regression-check-nfr-034-2026-11-06), archived); not part of `build` |
| `./gradlew :engine:test --tests '*IntegrityFuzzTest*' -Pfuzz.seed=S` | Replays one seed of the integrity fuzz (NFR-010), which `build` runs on one seed; add `-Pfuzz.seeds=N` to run N seeds from it |
| `./gradlew :zas-atmos:runGameTestServer -Pfuzz.seed=S` | Replays one seed of the fuzz's half with pipes, among the rest of those GameTests; takes `-Pfuzz.seeds=N` as well |
| `./gradlew spotlessApply` | Formats the Java sources (palantir-java-format) |
| `./gradlew :zas-atmos:runClient` | Starts a dev client with `zas` and `zas_atmos` |
| `./gradlew :zas-atmos:runClient -Pfusion=true` | The same, with Fusion, to see the station blocks' connected textures (CONT-132); no jar or CI run holds it |
| `./gradlew :zas-atmos:runClient -Pjei=true` | The same, with JEI, to see the gas and reaction pages (COMPAT-060, COMPAT-061). `-Pemi=true` adds EMI instead (COMPAT-062), or as well. Each is the oldest version its plugin supports unless `-Pjei.version=` or `-Pemi.version=` names another, or says `unsupported` for the newest one below it, where the pages are off; no jar holds either |
| `./gradlew :zas-atmos:runServer` | Starts a dev dedicated server with both mods |
| `./gradlew :compat-ponder:runClient` | Starts a dev client with both mods, Ponder and Flywheel, to see the Ponder scenes; [compat-ponder/README.md](compat-ponder/README.md) says how to add one and when they are on |
| `./gradlew :compat-ponder:runAdapterGameTestServer` | Loads the built release jar on a GameTest server with Create 6.0.10, which nests Ponder, and checks that the scenes' switch reads it as on and that the server loads none of the scenes' classes (CLIENT-023). `-Pcreate.version=unsupported` uses Create 6.0.9, whose Ponder is older than the scenes support, `-Pponder.from=create+addon` adds the built Ponder addon, and `-Pponder.from=released` loads the Ponder addon as release 1.0.0 shipped it in Create's place, beside which the scenes in the release jar stand down. The [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml) workflow runs each, and Create with `-Pcontent=off`; not part of `build` or `runGameTestServer` |
| `./gradlew :zas-ponder:runServerCheckGameTestServer` | Loads the built Ponder addon, with the Ponder and Flywheel it nests, on a GameTest server beside the built release jar (CLIENT-025); not part of `build` or `runGameTestServer` |
| `./gradlew :zas-api:apiCompat` | Checks the API for binary-incompatible changes against the last release |
| `./gradlew :zas-api:javadoc` | Builds the API's javadoc and fails on a broken or missing comment (API-005): every public or protected member needs one, with its `@param`, `@return` and `@throws`. `build` runs it |

The projects and the rules for what may depend on what are in the archived
[architecture §2](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/architecture.md#2-modules-and-packaging). In short: the
`engine` never imports Minecraft, NeoForge or Mojang classes, and `zas-atmos`
uses only the public API at compile time.

How mods depend on the API is in the wiki's [API guide](https://github.com/west3436/zas-cubed/wiki/API-Guide).
The version policy and the compatibility check are in the
[archived one](https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/api-consumers.md#versioning-and-the-compatibility-check),
which was written before the first release. The check now has a baseline: it downloads the API
jar of the GitHub release named in [`gradle.properties`](gradle.properties) and refuses a jar
whose SHA-256 isn't the one given there. After a release with a new API version, set the three
`api_baseline_` lines to it.

To inspect a running simulation, the `/zas` operator commands
([Commands](https://github.com/west3436/zas-cubed/wiki/Commands)) report zones and atmospheres, edit gas,
audit zones against the blocks and profile each phase. The config files are in
[Server configuration](https://github.com/west3436/zas-cubed/wiki/Server-Configuration) and
[Client configuration](https://github.com/west3436/zas-cubed/wiki/Client-Configuration).

### Tests

- **Engine logic** gets JUnit 5 tests in `engine`, with no Minecraft.
- **API value types and checks** get JUnit 5 tests in `zas-api`. Minecraft's
  classes are on that classpath, but the game is never bootstrapped.
- **Anything in the world** gets a GameTest in the mod's `src/gametest`
  source set, which stays out of the mod jar.
- **Names trace back** to the requirement they verify, where there is one:
  `core_012_zone_merge`. The same goes for GameTest structure names.

### Player-facing text

Text a player or operator reads goes in the mod's `en_us.json`, and the code
names its key (CLIENT-007). `./gradlew build` fails on text written in the code
and on a key the code spells out that `en_us.json` lacks. To run only that check:
`./gradlew :zas-atmos:test --tests '*PlayerTextTest*'`. Text with no letters,
such as a symbol or a number, is fine. The few things with letters that stay in
the code, such as unit symbols, are listed at the top of `PlayerTextTest`.

## Pull requests

1. Branch from `main`. Keep a PR to one issue where you can.
2. Fill in the PR template: link the issue (`Closes #nn`) and tick the
   checklist. For a player-facing change made with AI, include the human
   testing evidence described in [Using AI](#using-ai).
3. CI must pass: "Build and test" (asset credits, build, unit tests,
   formatting, the API's javadoc with no comment missing, the S-10 regression
   check, GameTests, and one copy of the API from built jars) and "Docs links".
4. PRs are squash-merged, so the PR title becomes the commit subject. Write it
   in the imperative: "Add Spotless formatting".

## Documentation

Guides for players, server owners, pack makers and mod developers are on the
[wiki](https://github.com/west3436/zas-cubed/wiki). The requirements, plan,
design notes and research were retired before the first release, and are
[archived](https://github.com/west3436/zas-cubed/tree/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs).
