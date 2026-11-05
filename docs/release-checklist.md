# Release checklist

What to run before each release, for the requirement with a part that repeats:
[NFR-025](requirements/90-non-functional.md#nfr-025--compatibility-matrix) (other mods).
This page has everything needed to run it. Add a line to [Results](#results) for each
run. Run everything on the release commit.

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

| Date | Commit | Run by | Matrix headless | Client launch | Notes |
|---|---|---|---|---|---|
