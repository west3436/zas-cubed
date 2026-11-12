# The Ponder scenes

The Ponder scenes for ZAS³ Atmospherics (CLIENT-023). They're part of
`zas_atmos` and ship in the release jar, in a package of their own that names
Ponder's classes. A client loads it only when Ponder is installed, so a player
can leave Ponder and Flywheel out (NFR-022).

## When they're on

Ponder comes with Create, which nests it and Flywheel. For players without
Create, the ZAS³ Ponder addon ([zas-ponder](../zas-ponder/README.md)) nests the
two and holds nothing else. `zas_atmos`'s `PonderGate` decides, once, as a client
sets up:

| Installed | The scenes |
|---|---|
| No Ponder | Off. Nothing is loaded, and nothing is logged above debug |
| Create 6.0.10 or later, or the addon, or both | On, once |
| A Ponder outside the range, such as Create 6.0.9's 1.0.81 | Off, with one warning |
| The addon as release 1.0.0 shipped it, which holds its own copy of the scenes | Off: that copy registers itself, so each scene is there once |
| Anything, with the content off in `zas_atmos-startup.toml` | Off: there are no items to explain |

The range is `PonderGate.SUPPORTED`. The scenes compile against its first
version, `ponder_floor_version` in `gradle.properties`, which is the copy Create
6.0.10 nests, so they use nothing newer.

`zas_atmos`'s `neoforge.mods.toml` names Create as an optional dependency with no
version range. A range there would stop the game when the installed Create is
outside it; without one the entry can't. The gate checks Ponder's version itself.

The scenes' ids, tag, text keys and floors are under `zas_atmos`, the mod they
are part of. Ponder heads a mod's tags in its index with the name of the mod
that has the tags' namespace as its id, so the heading reads "ZAS³ Atmospherics"
with Create's Ponder and with the addon's. The text is in a language file of its
own, in the `zas_ponder` resource folder: the release jar can hold only one
`assets/zas_atmos/lang/en_us.json`, which is the content's.

## Running it

- `./gradlew :compat-ponder:runClient` starts a dev client with `zas`,
  `zas_atmos`, Ponder and Flywheel. In a creative world, `/ponder
  zas_atmos:<item>` opens an item's scenes and `/ponder index` opens the index,
  where the ZAS³ tag lists them all. Holding the Ponder key over an item in an
  inventory opens them too.
- `./gradlew :compat-ponder:runAdapterGameTestServer` loads the built release jar
  on a dedicated GameTest server with Create, and checks what the gate makes of
  it and that the server loads none of the scenes' classes. `-Pponder.from=`
  picks what else is there: `create` (the default), `create+addon` (Create and the
  addon as this build makes it) or `released` (the addon as release 1.0.0 shipped
  it). `-Pcreate.version=unsupported` uses Create 6.0.9, and `-Pcontent=off`
  turns the content off. The
  [Adapter tests](https://github.com/west3436/zas-cubed/actions/workflows/compat-adapters.yml)
  workflow runs each.
- A server can't show the scenes registering, which only a client does. After a
  change to the gate or to what the jars hold, start a client with the built
  jars in its `mods` folder and open `/ponder index`.

## Adding a scene

1. Write the storyboard: a static method taking `(SceneBuilder scene,
   SceneBuildingUtil util)` in a class under `scenes/`, one class for each family
   of blocks, such as `PortScenes`. Start it with `Stage.open(scene, "<id>")`.
2. Register it in `ZasPonderPlugin.registerScenes`, under the item it explains,
   on a floor from `Stage` and with `TAG`. Add the item's id to `ITEMS`, which
   puts it under the ZAS³ tag in the index.
3. Put its text in `src/main/resources/assets/zas_ponder/lang/en_us.json`. The
   title is `zas_atmos.ponder.<id>.header`, and each line is
   `zas_atmos.ponder.shared.<key>`, where `<key>` is what the scene passes to
   `Say`. The code holds no text (CLIENT-007): `./gradlew :zas-atmos:test --tests
   '*PlayerTextTest*'` fails on a key `en_us.json` lacks.
4. Keep lines short, one idea each. "Gas flows from the canister to the vent" is
   the longest a line should be. Show amounts in the player's units with
   `Gas.pressure`.
5. Use only the blocks' and items' shipped models. A scene draws nothing new.

## The helpers

Everything in `scene/` is shared, so a new scene needs little of Ponder's own API.

- `Stage`: the floors, from `SMALL` (3 × 3) to `HUGE` (9 × 9), and `open`. The
  build makes each floor (`generatePonderStages`) as a cube that is air above the
  first layer, because Ponder ignores blocks set outside a floor's bounds. A scene
  on a 5 × 5 floor has 5 blocks of height, the floor included.
- `Build`
  - `place` sets a block out of sight.
  - `drop` and `reveal` place a block, join it to its neighbours, show it and wait
    until it's in. `revealAll` does the same for several blocks at once, such as
    a run of pipe.
  - `change` sets a shown block's state, as a use, a wrench or a signal would: a
    valve opening, a device turning on, a part turning. `remove` takes blocks
    away, one or several together. Both join the neighbours again, so a pipe left
    beside a gap gets an open end.
  - `change` over a selection changes several blocks at once. Use it for both
    halves of a two-block block, such as a firelock: changed one at a time, each
    half takes back the other's old state.
  - `settle` joins pipes, ports and walls to their neighbours. Ponder sets blocks
    without telling their neighbours, so call it after placing.
  - `lift` and `lower` move a shown block aside and back, to show the pipe that
    joins it from below or behind.
  - `sink` sets a block into the floor, level with it, as vents, scrubbers and
    passive vents go: the floor block lifts out and the device drops in. Never
    stand one on the floor.
  - `under` places blocks below the floor, such as the pipe under a vent. Ponder's
    own placing skips them, being outside the floor's bounds, so it places them
    itself. Run a pipe in the floor's layer as far as it can go, then dip it under
    the device.
  - `cut` and `mend` take floor blocks out of sight and back, to show what's
    under the floor through the gap. Cut the blocks north and west of what's
    under it, so set the device one block in from the floor's north and west
    edges.
- `Hand`
  - `use` and `sneakUse` show the mouse button over a block's face, with the item
    in hand, then pause before the scene shows what the use does. A button stays
    up for `Hand.SHOW` ticks, and two on blocks side by side cover each other, so
    wait until one goes before showing the next, as `PipeScenes` does.
  - `useAt` does the same at a spot in the air, for a use on nothing, such as an
    analyzer reading the air or an extinguisher spraying.
  - `show` shows an item over a block with no click, for what goes in its
    window, such as a portable's battery.
- `Power`
  - `controller` puts up a station wall with an area air controller, turns the
    device on and says the controller powers it.
  - `multitool` shows a multitool used on the device and says it links the device
    to a controller elsewhere.
  - `panel` is a spot on the front of a controller's or fire alarm's panel. The
    panel is thin and sits against the wall, so its block's face is in the air
    in front of it. Point lines about the panel here.
- `Say`
  - `at` shows a line pointing at a spot, with a keyframe, and waits while it's
    read.
  - `over` does the same without waiting, for a line read while something
    happens.
  - `plain` shows a line with no target.
- `Gas`
  - `puff` sends particles out of a face.
  - `draw` pulls particles in low over a face from its four sides, as gas going
    in. Particles coming straight down onto a face look like gas leaving it.
  - `flow` runs an outline along a path of blocks, again and again, as gas
    moving through pipes.
  - `pressure` formats a pressure in the player's units.

## What a device's scene shows (CLIENT-026)

- **The faces that take a pipe.** Set the device on its pipe, `lift` it, and
  outline the pipe's end. For an inline device, outline the pipes at its ends.
- **What powers it.** `Power.controller`, then `Power.multitool`.
- **What the wrench or multitool does,** where it matters: `Hand.use` with the
  tool, then `Build.change` or `Build.remove`. `PumpScenes` turns a pump with a
  wrench.

## The batch 5 blocks

The owner's answers so far (D-84):

- **An airlock closes itself** about 5 s after it's opened, so its scene can show
  it opening and then closing by itself.
- **The tank holder has no window.** A tank in hand goes in, and an empty hand
  takes out the last one put in: `Hand.use` with the tank, then with an empty
  hand.

## What Ponder's world doesn't do

- **It doesn't run the simulation.** Gas, pressure and power are shown with
  outlines, particles, readings and block states, not simulated.
- **It doesn't tick block entities.** `zas_atmos` has no block entity renderers,
  so a device draws from its block state. To show a state, such as a vent
  blowing, a valve open or a controller's status, set it with
  `scene.world().modifyBlock`.
- **What draws.** Dyed pipes' tints and canisters' colours draw, since both come
  from the block state. A tint that reads a block entity's data, such as a gas
  temperature, would draw its default unless the scene sets that data. Check it
  in a screenshot.
- **Two-block blocks.** A block in two halves, such as an airlock, needs both
  halves placed. Ponder doesn't place the second half, as the game does.
- **Paired blocks.** Place both blocks of a pair, such as a heat exchanger's,
  then `settle`.

## Laying out the view

Ponder looks at the floor from its (0, 0) corner and opens a line's box to the
right of the line's target. Put the subject on the left of the view, toward high
x and low z, and leave the right side clear for text. A run of blocks reads best
along +z.

## Looking at a scene

Run the client, open the scene and step through its keyframes on the timeline.
F2 saves a screenshot to `compat-ponder/run/screenshots/`. Look at it at
1920 × 1080, at GUI scale Auto and at 2. Ponder draws at a fixed size per GUI
scale, so a scene is small at scale 2; scrolling zooms in.

## Versions

The scenes compile against Ponder 1.0.82, the copy Create 6.0.10 nests, and the
dev client runs with Ponder 1.0.87 and Flywheel 1.0.7, the copies the addon
nests. All four versions are in `gradle.properties`. With Create and the addon
both installed, FML loads one copy of each that both accept, and not always the
newer: the scenes are on with either.
