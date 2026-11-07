# Server authority: every client-to-server handler

The inspection [NFR-011](../requirements/90-non-functional.md#nfr-011--server-authority)
asks for, done against `main` on 2026-11-05. Rerun it whenever a client packet or a
device GUI is added:

1. Search `zas`, `zas-atmos`, `zas-api` and `compat-adastra` for `playToServer`,
   `playBidirectional`, `configurationToServer`, `commonToServer` and `sendToServer`.
2. List every override of `clickMenuButton` and every subclass of
   `AbstractContainerMenu`, and add a row for anything new.

**Result.** ZAS³ registers two client-to-server payloads. Everything else a client can
ask of a device arrives through the game's own menu packets. No handler reads a value
from the client: each receives an id, checks it, and works out the result on the server
from what the server holds. So a modified client can press any button, but can't send
a number.

Every other ZAS³ payload is registered `playToClient` only: airflow (3), the debug
overlay, gas definitions, the atmos HUD, listener air, breath sync, controller sync, gas
jets and device activity.

## ZAS³'s payloads

Registered with NeoForge's default `HandlerThread.MAIN`, so they run on the server
thread.

| Handler | What it changes | Allowed by | How it's validated |
|---|---|---|---|
| `Internals.handleToggle` (`zas:toggle_internals`) | The sender's own internals switch, an attachment on that player | LIFE-029 | The payload carries nothing. Only the sender, who must be alive and not spectating |
| `TankSettings.handle` (`zas_atmos:tank_settings`) | The distribution pressure of the tank in the sender's own hand (LIFE-044), on that stack | CONT-074 | Hand and button ids are range-checked, and the stack must be a `GasTankItem`. The sender must be alive and not spectating. The pressure is the server's value plus a fixed step, clamped to 0–304 kPa |

## Device GUIs: the game's menu-button packet

The game's `handleContainerButtonClick` calls `clickMenuButton` only for the player's
open menu, with the id the packet names, if the player isn't spectating and the menu is
`stillValid`. For a block's menu that means the block is still there and the player is
within reach. All of these are allowed by CONT-074, and each also refuses a device
that has been removed.

| Handler | What it changes | How it's validated |
|---|---|---|
| `CanisterMenu` | Valve; release pressure | Ids 0–5. Clamped to 10–1,013 kPa |
| `VentMenu` | Mode; external and internal bounds | Ids 0–12. Clamped to 0–5,066 kPa. Refused while a controller controls the vent (CONT-014) |
| `ScrubberMenu` | Mode; which gases and tags it takes | Ids 0–14, each a fixed entry on the server's list. Refused while a controller controls the scrubber (CONT-015) |
| `ControllerMenu` | Mode; preset; one reading's bounds; going back to the central controller | Ids are checked against the modes, the shipped presets, the fixed steps and the preset's cells. Bounds are clamped to 0–5,066 and kept in order (`Thresholds.with`). Opened from a central controller (CONT-124), the menu is valid only while the player is in reach of that central controller, this controller is loaded and the central controller still has it; id 40 reopens the central controller's GUI |
| `CentralMenu` | Range; a claimed controller's zone priority; opening a claimed controller's GUI | Ids 0–1 step the range, refused past 1 or `max_range_chunks`. The priority and open ids name a row of the list the server last sent, so only its own claimed controllers can be chosen, and each is checked to still be its own. Opening needs the controller loaded, and opens its `ControllerMenu`, with every check that has there |
| `InlineDeviceMenu` | Switch | Id 0; anything else goes to the device's own check |
| `PumpMenu` | Target or rate (pressure and volume pumps) | Ids 1–5. Clamped to 0–4,500 kPa or 0–100 L/s |
| `SidePortMenu` | Target | Ids 1–5. Clamped to 0–4,500 kPa |
| `FilterMenu` | What it filters | The id is a place in a list the server made when the GUI opened, so nothing else can be selected |
| `MixerMenu` | The two inputs' shares | Ids 6–10. Clamped to 0–100 % |
| `PortableMenu` | Switch (portable scrubber: this only) | Id 0 |
| `PortablePumpMenu` | Direction; target | Ids 1–5. Clamped to 0–4,500 kPa |
| `ThermoMachineMenu` (heater, air conditioner) | Setpoint | Ids 1–4. Clamped to 293–573 K (heater) or 73–293 K (air conditioner) |
| `TransferValveMenu` | Timer; valve; starting the timer | Ids 0–6. Clamped to 0–600 s. The menu has to be on the very stack it opened on, and starting needs an empty cursor |
| `GeneratorMenu` | Nothing: it has no buttons | The base class refuses every id |

GameTests send these menus unknown ids and settings past their limits. The `cont_074_`
tests cover the canister, vent, scrubber, controller, pump, filter and mixer, and each
portable's and thermomachine's own tests cover those. `TransferValveMenuTests` checks
that a stale menu refuses every button. The `cont_117_` and `cont_124_` tests check that a
central controller refuses a range past its limits, that a controller's GUI opened from it
refuses a player out of reach, and that it won't open one whose chunk isn't loaded.

## Slots: the game's menu-click packet

`handleContainerClick` applies the same spectator and `stillValid` checks, and refuses
an invalid slot index, before calling `clicked`. The server decides what a click does,
and the stacks the client reports are only used to resynchronise it.

| Menu | Slots | How they're validated |
|---|---|---|
| `PortableMenu` and subclasses | Battery, tank | Each slot's handler takes only what it holds (CONT-112) |
| `GeneratorMenu` | Fuel | The slot takes only furnace fuel |
| `TransferValveMenu` | Two tanks | The stack's own data is the only copy. The menu refuses any click that would move the TTV or copy a tank (see its class comment) |

All other menus have no slots, and their `quickMoveStack` returns nothing.

## Other packets that reach ZAS³

These are the game's, and ZAS³ adds no handler for them. They're listed because
NFR-011's "no client packet" reads more narrowly than it sounds: playing the game
changes atmos state.

| Packet | What it does | Why it's fine |
|---|---|---|
| Use item, use item on block, attack | Places and breaks blocks, turns a valve, fills a tank | The game checks reach. ZAS³'s code reads the block and item the server has, and from the packet only which block was hit and where |
| Chat command | `/zas` edits gas, among other things | `ZasCommands.permitted` guards the root of `/zas` and of `DebugCommand`, at `permission_level` (OPS-011) |
| Set creative slot | Puts a stack with any data in the inventory | Creative players only, and the same power as `/give`. Gas on an item still decodes through `GasParcel`, which refuses negative and non-finite values, so it can't carry NaN into a zone, and block entities clamp what they load. A stack's `distribution_pressure` isn't range-checked: below 0 or NaN the tank delivers nothing, and above 304 kPa it only empties faster, into the room |

## What this doesn't cover

- **Rate.** Nothing limits how fast a client presses, and the game has no limit on its
  own menu packets either. What repeated presses cost wasn't measured.
- **Reach for a held item.** `TankSettings` doesn't need the screen open. It can only
  reach the sender's own hand, so a client skipping the screen gains nothing.
