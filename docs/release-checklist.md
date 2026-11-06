# Release checklist

What to run before each release, for the requirements with a part that repeats:
[NFR-012](requirements/90-non-functional.md#nfr-012--exploit-checklist) (gas duplication)
and [NFR-025](requirements/90-non-functional.md#nfr-025--compatibility-matrix) (other mods).
This page has everything needed to run them. Add a line to [Results](#results) for each
run. Run everything on the release commit.

## Exploit checklist (NFR-012)

Gas may only change through the sources and sinks CORE-036 lists. An item passes if the
gas totals match and the invariant checker (CORE-073) reports nothing unexplained. Ambient
harvesting and supply from paused zones are sources by design, so they don't count as
failures.

The checker compares zones and doesn't see gas in pipes, so a clean report says nothing
about them. That's why each item below also totals the gas itself.

Four of the five items are GameTests, which CI also runs on every PR:

```
./gradlew runGameTestServer -x :example-addon:runGameTestServer
```

Check the log for `All N required tests passed`, once per server: the exit code alone
doesn't prove the server started.

| Item | GameTests, all named `nfr_012_...` |
|---|---|
| Pipes across chunk borders | `ExploitChecklistTests`: `gas_moved_along_pipes_across_a_chunk_border_is_conserved`. `PipeSaveTests`: `a_save_writes_both_sides_of_a_network_as_it_is_now` |
| Chunk unloads and reloads during transfers | `PipeSaveTests`: `chunks_unloading_during_a_transfer_make_no_gas`, and the tests that a network, canister, device port or portable `keeps_what_it_lost_since_its_chunk_last_saved`, `a_network_stops_changing_a_chunk_that_has_left_full_status`, `gas_reaching_a_chunk_that_has_left_full_status_is_saved` |
| Canister swapping | `ExploitChecklistTests`: `swapping_canisters_on_a_network_makes_no_gas`, `mining_a_releasing_canister_makes_no_gas` |
| Door cycling | `ExploitChecklistTests` in `zas`: `cycling_a_door_makes_no_gas` |
| Saving and quitting mid-transfer | None: it needs a real restart. Run the steps below |

Gas on its way into a zone as the zone pauses is voided by design (GEO-036), so the
unload test counts what the ledger says was voided. It can lose gas this way but can't
make any.

### Saving and quitting mid-transfer

This needs a dedicated server and its console, and no client.

1. In `zas-atmos/run`, set `level-type=minecraft\:flat` in `server.properties`, and start a
   new world with `./gradlew :zas-atmos:runServer`. Enter `stop` once it has started. In
   `config/zas-server.toml`, set `conservation_checker = true`.
2. Start the server again, and enter these. They build a sealed room in one chunk, with a
   vent in its floor on test power, fed along pipes across the chunk border by a canister
   of 900 mol of nitrogen:

   ```
   forceload add 0 0 31 15
   fill 18 -61 1 22 -57 5 minecraft:stone hollow
   setblock 20 -61 3 zas_atmos:vent[facing=up,mode=off]{Mode:"off",External:5000.0d,Internal:0.0d,TestPowered:1b}
   fill 10 -62 3 20 -62 3 zas_atmos:pipe
   setblock 10 -61 3 zas_atmos:connector_port
   setblock 10 -60 3 zas_atmos:canister{Gas:{T:293.15d,Moles:{"zas:nitrogen":900.0d}}}
   zas gas add 20 -59 3 zas:nitrogen 1
   ```

3. Wait 2 seconds, then read the starting total, **T₀**. It's the total `mol` from
   `zas zone at 20 -59 3`, plus the nitrogen from `data get block <pos> Gas.Moles` at each
   of the 14 network blocks: `20 -61 3`, `10 -61 3`, `10 -60 3`, and `<x> -62 3` for each
   `<x>` from 10 to 20.
4. Start the transfer: `data merge block 20 -61 3 {Mode:"blow"}`.
5. Wait about 5 seconds, then enter `stop`, which saves and quits.
6. Start the server again. Stop the transfer with `data merge block 20 -61 3 {Mode:"off"}`,
   and wait 3 seconds so that nothing is in flight.
7. Read the total again, as in step 3. **It must equal T₀ to within 0.02 mol**, the
   precision `zas zone at` prints.
8. Run `zas checker report`. **It must say `conserved (0 violations)`.** The report is only
   the last atmos tick's, so also search `logs/latest.log`: **it must have no line
   containing `Conservation:`.**
9. Run `zas audit 0 -64 0 31 -50 15`. **It must say `no differences`.**
10. Repeat steps 4 to 9 once, so that the second load also starts from a saved
    mid-transfer world.

### A crash isn't on this list

`stop` writes every chunk at one moment. A crash doesn't, and a network's chunks save
their shares separately, so a crash can bring back more gas than went in. On 2026-11-05,
with a transfer across a chunk border and the two chunks' saves kept 5 s apart, five
killed servers came to +242, +201, +135, +56 and −151 mol. Six killed with nothing done
to keep the saves apart came to −4 to −100 mol.

## Compatibility matrix (NFR-025)

The matrix is ModernFix, FerriteCore, Lithium and Sodium, NeoForge builds. They join a
run's classpath only with `-Pcompat.matrix=true`, and never go into a jar. Check for newer
builds first: the versions are at the end of `zas-atmos/build.gradle`.

### Headless

```
./gradlew :zas-atmos:runGameTestServer -Pcompat.matrix=true
```

This runs every GameTest of both mods, S-01 to S-04 included, on a dedicated server with
ModernFix, FerriteCore and Lithium. Check the log for `All N required tests passed`: the
exit code alone doesn't prove the server started. `nfr_025_the_matrix_mods_are_loaded`
fails if the mods aren't loaded.

Sodium isn't in this run. On a dedicated server it isn't loaded, even though the log's mod
list names it, and all its mixins are client mixins. Only the client launch below
exercises it.

The [Compatibility matrix](https://github.com/west3436/zas-cubed/actions/workflows/compat-matrix.yml)
workflow does the same on GitHub every night, or when you choose **Run workflow** there.

### Client launch (by hand)

CI has no client, so only a person can do this part. It's the only run with all four mods.

```
./gradlew :zas-atmos:runClient -Pcompat.matrix=true
```

1. The title screen appears, and the Mods screen lists ModernFix, FerriteCore, Lithium,
   Sodium, ZAS³ and ZAS³ Atmospherics.
2. Create a creative world. It loads with no crash and no error screen.
3. Build a stone room, 3 × 3 × 3 inside, stand in it, and run
   `/zas gas add ~ ~ ~ zas:oxygen 100`. The chat says the gas was added.
4. Place a pipe, a canister and a vent, and open the canister's and the vent's screens.
5. Quit to the title screen. The game keeps running with no crash.

## Results

### Exploit checklist

| Date | Commit | Run by | GameTests | Saving and quitting | Notes |
|---|---|---|---|---|---|

### Compatibility matrix

| Date | Commit | Run by | Matrix headless | Client launch | Notes |
|---|---|---|---|---|---|
