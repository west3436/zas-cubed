# Contributing to ZAS³

Thanks for helping. This guide covers the one rule that can't bend (clean-room
code), how to build and test, and how changes get merged.

## The clean-room rule

ZAS³ is MIT-licensed. vgstation13, which inspired it, is GPLv3/AGPLv3. To keep
the licenses apart, **no vgstation13 code enters this repository, whether
copied or translated** (D-01, NFR-040).

- **Allowed:** vg's *behaviour*, its algorithms described in your own words,
  and physical constants. The research notes in
  [`docs/research/`](docs/research/) describe vg in prose for exactly this
  reason.
- **Not allowed:** pasting DM code, porting it line by line into Java, or
  keeping its structure, names and comments while changing the syntax. A
  translation is still a copy.
- **How to work:** read the research note or the requirement, close the vg
  source, then write the code from the description. If a note doesn't say
  enough, improve the note in prose first, in its own PR.
- **The same goes for** requirements, docs and comments: never quote vg code
  (requirements writing rule 4).

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
[`LICENSE-ASSETS`](LICENSE-ASSETS) (D-39, D-56). Every file under a
project's `src/*/resources/assets/` needs an entry in [`CREDITS`](CREDITS)
(NFR-045). An entry is a block of `key: value` lines, starting with the
file's path from the repository root; blank lines separate entries:

- **Derived from a vg sprite:** `source` (the `.dmi` path in the vg
  repository), `state` (the icon state), `commit` (the full vg commit hash)
  and `attribution` naming the vgstation13 contributors. Use only vg's CC
  BY-SA 3.0 US assets, never the CC BY-NC-SA `goon/` folder. Leave out any
  sprite whose origin is unclear (NFR-041).
- **Drawn from scratch:** `source: original`.

```
path: zas-atmos/src/main/resources/assets/zas_atmos/textures/block/canister.png
source: icons/obj/atmos.dmi
state: yellow
commit: 0123456789abcdef0123456789abcdef01234567
attribution: vgstation13 contributors, CC BY-SA 3.0 US
```

The full format is at the top of `CREDITS`. Check it with
`python scripts/check_credits.py` (Python 3, no dependencies). It lists asset
files with no entry, entries for files that don't exist, malformed entries
and `goon/` sources. CI runs it in "Build and test".

No NonCommercial asset ships in any jar (NFR-046). RE:SS3D and Stationeers are
reference and inspiration only.

## Building and testing

You need a JDK to run Gradle (17 or newer); the build fetches Java 21 for the
mods itself.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, runs the engine's unit tests and checks formatting |
| `./gradlew runGameTestServer` | Boots dedicated GameTest servers and runs the GameTests |
| `./gradlew spotlessApply` | Formats the Java sources (palantir-java-format) |
| `./gradlew :zas-atmos:runClient` | Starts a dev client with `zas` and `zas_atmos` |
| `./gradlew :zas-atmos:runServer` | Starts a dev dedicated server with both mods |

The projects and the rules for what may depend on what are in
[architecture §2](docs/architecture.md#2-modules-and-packaging). In short: the
`engine` never imports Minecraft, NeoForge or Mojang classes, and `zas-atmos`
uses only the public API at compile time.

### Tests

- **Engine logic** gets JUnit 5 tests in `engine`, with no Minecraft.
- **Anything in the world** gets a GameTest in the mod's `src/gametest`
  source set, which stays out of the mod jar.
- **Names trace back** to the requirement they verify (requirements writing
  rule 5): `core_012_zone_merge`. The same goes for GameTest structure names.

## Pull requests

1. Branch from `main`. Keep a PR to one issue where you can.
2. Fill in the PR template: link the issue (`Closes #nn`), list the
   requirement IDs touched, and tick the checklist. For a player-facing
   change made with AI, include the human testing evidence described in
   [Using AI](#using-ai).
3. CI must pass: "Build and test" (asset credits, build, unit tests,
   formatting, GameTests) and "Docs links".
4. PRs are squash-merged, so the PR title becomes the commit subject. Write it
   in the imperative: "Add Spotless formatting".

### Changing requirements

Accepted requirements in [`docs/requirements/`](docs/requirements/) change
only through a PR, with a dated note under the edited requirement. A material
change returns it to *Draft* (requirements writing rule 6). New decisions are
added to the decision log in
[`00-overview.md`](docs/requirements/00-overview.md).
