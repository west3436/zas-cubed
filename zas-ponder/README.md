# ZAS³ Ponder

The Ponder scenes for ZAS³ Atmospherics (CLIENT-023). They're a client addon of
their own, `zas_ponder`, that requires `zas_atmos` and nests Ponder and Flywheel
(D-83). `zas_atmos` names neither, so a player can leave them out (NFR-022).

## Running it

- `./gradlew :zas-ponder:runClient` starts a dev client with `zas`, `zas_atmos`
  and the addon. In a creative world, `/ponder zas_atmos:<item>` opens an item's
  scenes and `/ponder index` opens the index, where the ZAS³ tag lists them all.
  Holding the Ponder key over an item in an inventory opens them too.
- `./gradlew :zas-ponder:runServerCheckGameTestServer` loads the built jar, with
  what it nests, on a dedicated GameTest server beside both mods (CLIENT-025).
  CI runs it on every PR.

## Adding a scene

1. Write the storyboard: a static method taking `(SceneBuilder scene,
   SceneBuildingUtil util)` in a class under `scenes/`, one class for each family
   of blocks, such as `PortScenes`. Start it with `Stage.open(scene, "<id>")`.
2. Register it in `ZasPonderPlugin.registerScenes`, under the item it explains,
   on a floor from `Stage` and with `TAG`. Add the item's id to `ITEMS`, which
   puts it under the ZAS³ tag in the index.
3. Put its text in `src/main/resources/assets/zas_ponder/lang/en_us.json`. The
   title is `zas_ponder.ponder.<id>.header`, and each line is
   `zas_ponder.ponder.shared.<key>`, where `<key>` is what the scene passes to
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
    valve opening, a device turning on, a part turning. `remove` takes one away.
    Both join the neighbours again, so a pipe left beside a gap gets an open end.
  - `settle` joins pipes, ports and walls to their neighbours. Ponder sets blocks
    without telling their neighbours, so call it after placing.
  - `lift` and `lower` move a shown block aside and back, to show the pipe that
    joins it from below or behind.
- `Hand`
  - `use` and `sneakUse` show the mouse button over a block's face, with the item
    in hand, then pause before the scene shows what the use does.
  - `useAt` does the same at a spot in the air, for a use on nothing, such as an
    analyzer reading the air or an extinguisher spraying.
- `Power`
  - `controller` puts up a station wall with an area air controller, turns the
    device on and says the controller powers it.
  - `multitool` shows a multitool used on the device and says it links the device
    to a controller elsewhere.
- `Say`
  - `at` shows a line pointing at a spot, with a keyframe, and waits while it's
    read.
  - `over` does the same without waiting, for a line read while something
    happens.
  - `plain` shows a line with no target.
- `Gas`
  - `puff` sends particles out of a face.
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
F2 saves a screenshot to `zas-ponder/run/screenshots/`. Look at it at
1920 × 1080, at GUI scale Auto and at 2. Ponder draws at a fixed size per GUI
scale, so a scene is small at scale 2; scrolling zooms in.

## Versions

The jar nests Ponder 1.0.87 and Flywheel 1.0.7. It accepts Create 6.0.10's
copies, Ponder 1.0.82 and Flywheel 1.0.6, which FML may load instead, so the
scenes use nothing newer. To check, set `ponderVersion` and `flywheelVersion` in
`build.gradle` to those two and compile.
