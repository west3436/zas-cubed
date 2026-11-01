"""Test world for sub-cell point resolution and held breaches (PR #116: #108, #109; API-015, API-010).

Superflat, ground floor y -60. Spawn at 8 -60 0 facing south toward the fixtures; every fixture
has a sign in front of it.
"""

from testworld import sign

LEVEL = "ZAS Point Resolution Test"
SPAWN = (8, -60, 0)
FORCELOAD = (-176, -16, 47, 47)  # x1 z1 x2 z2: every fixture, for building

Y0 = -60
S, W, N, E = 0, 4, 8, 12


def room(x0, z0, block="minecraft:stone"):
    """A hollow 5x5x5 shell with 3x3x3 air inside, from (x0, -60, z0) to (x0+4, -56, z0+4)."""
    return f"fill {x0} {Y0} {z0} {x0 + 4} {Y0 + 4} {z0 + 4} {block} hollow"


PANE_NS = "minecraft:glass_pane[north=true,south=true]"

BUILD = [
    sign(8, Y0, 2, N, "Point resolution", "Fixtures: south", "/zas at point", "See TEST-PLAN.md"),
    # Fixture 1: an oxygen room with a glass pane in its east wall, open air beyond it.
    room(2, 4),
    f"setblock 6 -58 6 {PANE_NS}",
    sign(4, Y0, 3, N, "1: pane to air", "O2 room", "pane: 6 -58 6", "west half zoned"),
    # Fixture 2: two rooms sharing a wall with a glass pane in it; O2 west, CO2 east.
    room(10, 4),
    room(14, 4),
    f"setblock 14 -58 6 {PANE_NS}",
    sign(12, Y0, 3, N, "2: pane between", "W room: O2", "pane: 14 -58 6", "E room: CO2"),
    # Fixture 3: a solid stone cube, centre block 31 -59 5 with no air next to it.
    "fill 30 -60 4 32 -58 6 minecraft:stone",
    sign(31, Y0, 3, N, "3: buried stone", "centre block", "31 -59 5", "no cells near"),
    # Fixture 4: an oxygen room C sharing a red glass wall block with a long empty tunnel west of
    # it (x -170 to -6), whose far end unloads at render distance 2.
    "fill -170 -60 28 -6 -56 32 minecraft:glass hollow",
    "fill -6 -60 28 -2 -56 32 minecraft:glass hollow",
    "setblock -6 -58 30 minecraft:red_stained_glass",
    sign(-1, Y0, 30, E, "4: held breach", "Room C + tunnel", "Break red glass", "at RD 2"),
]

# (label, block inside, gas, moles)
GAS = [
    ("Fixture 1 room", (4, -58, 6), "zas:oxygen", 400),
    ("Fixture 2 west room", (12, -58, 6), "zas:oxygen", 400),
    ("Fixture 2 east room", (16, -58, 6), "zas:carbon_dioxide", 300),
    ("Fixture 4 room C", (-4, -58, 30), "zas:oxygen", 300),
]

DIM = r"in \S+: "
CELL = r"sub-cell (\d) of 2\) " + DIM


def verify(check):
    check("Zones after loading (spawn chunks only)", "zas zones")

    # Fixture 1
    room1 = check("Fixture 1: inside the room", "zas at 4 -58 6", DIM + r"zoned, zone (\d+)")
    west = check("Fixture 1: pane, west half", "zas at point 6.25 -57.5 6.5", CELL + r"zoned, zone (\d+)")
    east = check("Fixture 1: pane, east half", "zas at point 6.75 -57.5 6.5", CELL + r"ambient, zone -")
    check.expect("Fixture 1: the halves are different sub-cells",
                 west and east and west.group(1) != east.group(1))
    check.expect("Fixture 1: the west half is in the room's zone",
                 west and room1 and west.group(2) == room1.group(1))
    check("Fixture 1: pane block position names a sub-cell", "zas at 6 -58 6",
          r"6 -58 6, sub-cell \d of 2 " + DIM + r"zoned, zone \d+")
    check("Fixture 1: inside the glass, nearer the west face", "zas at point 6.45 -57.5 6.5",
          CELL + r"zoned, zone \d+")
    check("Fixture 1: inside the glass, nearer the east face", "zas at point 6.55 -57.5 6.5",
          CELL + r"ambient, zone -")
    check("Fixture 1: inside the west wall stone, room side", "zas at point 2.9 -57.5 6.5",
          DIM + r"zoned, zone \d+")
    check("Fixture 1: inside the west wall stone, outside", "zas at point 2.1 -57.5 6.5",
          DIM + r"ambient, zone -")

    # Fixture 2
    w = check("Fixture 2: pane, west half", "zas at point 14.25 -57.5 6.5", CELL + r"zoned, zone (\d+)")
    e = check("Fixture 2: pane, east half", "zas at point 14.75 -57.5 6.5", CELL + r"zoned, zone (\d+)")
    check.expect("Fixture 2: the halves are in different zones", w and e and w.group(2) != e.group(2))
    wr = check("Fixture 2: inside the west room", "zas at 12 -58 6", DIM + r"zoned, zone (\d+)")
    check.expect("Fixture 2: the west half is in the west room's zone", w and wr and w.group(2) == wr.group(1))
    check("Fixture 2: gas added at the pane block goes into a zone", "zas gas add 14 -58 6 zas:nitrogen 50",
          r"into a zone")

    # Fixture 3
    check("Fixture 3: centre of the buried stone", "zas at point 31.5 -58.5 5.5", DIM + r"ambient, zone -")

    # Fixture 4
    check("Fixture 4: tunnel before the breach", "zas at -10 -58 30", DIM + r"ambient, zone -")
    check("Fixture 4: break the red glass", "setblock -6 -58 30 minecraft:air", wait=4)
    check("Fixture 4: room C pauses", "zas zone at -4 -58 30", r"Zone \d+: .*paused")
    check("Fixture 4: tunnel near the breach is unresolved", "zas at -10 -58 30", DIM + r"unresolved, zone -")
    check("Fixture 4: load the whole tunnel", "forceload add -176 16 -16 47", wait=12)
    c = check("Fixture 4: room C after loading", "zas at -4 -58 30", DIM + r"zoned, zone (\d+)")
    t = check("Fixture 4: tunnel after loading", "zas at -10 -58 30", DIM + r"zoned, zone (\d+)")
    check.expect("Fixture 4: room C and the tunnel are one zone", c and t and c.group(1) == t.group(1))


PLAN = """\
# Manual test: point resolution and held breaches (PR #116)

Tests #108 (API-015: points resolve to the sub-cell holding them) and #109 (a loaded cell in a
held breach reports UNRESOLVED). Take screenshots with F2 and attach them to the PR.

**Setup:** `gh pr checkout 116`, then `./gradlew :zas-atmos:runClient` and open
**ZAS Point Resolution Test** (creative, cheats on, fixed noon, no mobs). You spawn at 8 -60 0
facing south. Every fixture has a sign. `/zas debug cells` and `/zas debug zones` show the cells
and zones while you test.

`/zas at point` prints a header like
`Atmosphere at 6.25 -57.5 6.5 (6 -58 6, sub-cell 0 of 2) in minecraft:overworld: zoned, zone 3`.
Zone ids may differ from the headless results below; what matters is which ones match.

## 1. Pane to open air (x 2 to 6, z 4 to 8)

An oxygen room with a glass pane in its east wall at 6 -58 6.

- [ ] `/zas at point 6.25 -57.5 6.5` (west half): a sub-cell, **zoned**, the room's zone (`/zas at 4 -58 6`)
- [ ] `/zas at point 6.75 -57.5 6.5` (east half): the other sub-cell, **ambient**, zone -
- [ ] `/zas at 6 -58 6` (block position): names a sub-cell (the largest; the halves are equal, so the first)
- [ ] `/zas at point 6.45 -57.5 6.5` (inside the glass, nearer west): **zoned**
- [ ] `/zas at point 6.55 -57.5 6.5` (inside the glass, nearer east): **ambient**
- [ ] `/zas at point 2.9 -57.5 6.5` (inside the west wall's stone, room side): **zoned**
- [ ] `/zas at point 2.1 -57.5 6.5` (same stone, outside): **ambient**
- [ ] With `/zas debug cells`, look at the pane: two sub-cells

## 2. Pane between two rooms (x 10 to 18, z 4 to 8)

Oxygen room west, CO2 room east, a glass pane in the shared wall at 14 -58 6.

- [ ] `/zas at point 14.25 -57.5 6.5`: **zoned**, the west room's zone, O2 in the gas list
- [ ] `/zas at point 14.75 -57.5 6.5`: **zoned**, a different zone, CO2 in the gas list
- [ ] `/zas gas add 14 -58 6 zas:nitrogen 50`: goes "into a zone"; `/zas zone at 12 -58 6` and
      `/zas zone at 16 -58 6` show which room got it (the pane's first sub-cell)

## 3. Eye position

- [ ] `/tp @s 4 -59 6` (inside room 1), then `/zas at` with no position: **zoned**, room 1's zone
- [ ] `/tp @s 8 -60 0` (outside), then `/zas at`: **ambient**

## 4. Buried stone (x 30 to 32, z 4 to 6)

- [ ] `/zas at point 31.5 -58.5 5.5` (centre of a solid 3x3x3 cube): **ambient**, zone -, and no error

## 5. Held breach (room C at x -6 to -2, z 28 to 32; tunnel west to x -170)

1. [ ] Options > Video Settings > Render Distance **2**. Stand by the sign at -1 -60 30 and wait 5 s
   for the far end of the tunnel to unload.
2. [ ] `/zas at -10 -58 30` (in the tunnel) before breaking anything: **ambient**
3. [ ] Break the red stained glass at -6 -58 30.
4. [ ] `/zas zone at -4 -58 30`: room C's zone, **paused**
5. [ ] `/zas at -10 -58 30`: **unresolved, zone -** (before this PR it said ambient)
6. [ ] Optional: `/zas debug zones` draws that part of the tunnel magenta and room C grey
7. [ ] Render Distance back to 12, wait for the tunnel to load: `/zas at -4 -58 30` and
   `/zas at -10 -58 30` are **zoned** with the same zone id

## 6. Optional: dedicated server

- [ ] `./gradlew :zas-atmos:runServer`, copy this world in as the server's `world`, join `localhost`,
      and repeat 1 and 5.

Check `logs/latest.log` for `zas` errors afterwards.
"""
