# Test worlds for manual testing

A PR that needs a human to look at it in game ships a **fixture** here: a small
Python module that describes a test world. `testworld.py` turns it into a
ready-made singleplayer world in the dev client's saves folder, with the test
plan inside it as `TEST-PLAN.md`. The tester opens the world and works through
the plan; nobody builds rooms by hand.

```
gh pr checkout <n>
python -I scripts/testworld/testworld.py <fixture>
./gradlew :zas-atmos:runClient      # then open the world named in the plan
```

Python 3 with only its standard library, and the JDK the build uses.

## What the generator does

1. Builds the checkout's mods (`--repo`, by default this checkout) and boots a
   headless dev server in `build/testworld/<fixture>/`, on a superflat world.
2. Force-loads the fixture's area, sets fixed noon, no weather and no mobs,
   runs the fixture's build commands, and adds its gas with `/zas gas add`.
   Any command error stops it.
3. Saves, stops, and makes the world creative with cheats on.
4. Boots a second server on a **copy** and runs the fixture's checks. If any
   expectation fails, the world isn't installed. The results are appended to
   the plan, so the tester can compare what they see with what the headless
   run saw.
5. Copies the world and plan into `zas-atmos/run/saves/` (or each `--install`
   folder). It only replaces worlds it made itself.

The world is generated, never committed: regenerate it after a change to the
saved format or the fixture.

## Writing a fixture

Name it after what it tests, not the PR number, so it stays useful as a
regression world: `fixtures/point_resolution.py`. A fixture module has:

| Name | What |
|---|---|
| `LEVEL` | The world's name in the client's world list |
| `SPAWN` | `(x, y, z)` world spawn; the player starts here |
| `FORCELOAD` | `(x1, z1, x2, z2)` covering every structure, loaded while building |
| `BUILD` | Console commands (no leading `/`) that build the structures. `testworld.sign(x, y, z, rotation, *lines)` makes a standing sign |
| `GAS` | `(label, (x, y, z), gas id, moles)` additions, each of which must land in a zone |
| `verify(check)` | The headless checks (below) |
| `PLAN` | The tester's plan in Markdown: setup, then numbered sections with checkboxes and exact commands and coordinates |

`check(label, command, pattern=None, wait=2.0)` runs a console command, waits,
and returns the first regex match in its output; a pattern that matches
nothing is a failure. `check.expect(label, condition)` records a check across
earlier matches, such as two zone ids being equal.

Guidelines:

- Superflat ground is at y -61, so floors are at y -60.
- Put a sign in front of every structure saying what it is and what to do.
- Keep structures within the spawn chunks (about 32 blocks of spawn) unless
  the test needs chunks to unload, like a long tunnel for paused zones.
- Check every state the plan asks the tester to look at, so a broken world
  never reaches them. Visual results (rendering, HUD, sound) can't be checked
  headless; the plan covers those.
