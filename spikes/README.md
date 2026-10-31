# Phase B spikes

Throwaway code that produced the numbers in
[`docs/research/spikes.md`](https://github.com/west3436/zas-cubed/blob/main/docs/research/spikes.md).
Nothing here is part of the mod: this branch is not merged (the spike tickets
#3 to #8 say spike code is throwaway). The code follows the clean-room rule
like everything else (NFR-040).

All commands assume JDK 21 (`JAVA_HOME` pointing at it). The Gradle wrapper
downloads Gradle 9.2.1, and ModDevGradle downloads Minecraft and NeoForge on
first use.

| Folder | Spikes | How to run |
|---|---|---|
| [`s0-packaging`](s0-packaging) | S0 | See below |
| [`s1s2-ingame`](s1s2-ingame) | S1, S2, and S5's real snapshot cost | `./gradlew runGameTestServer -Phooks=chunk,poi` (also `-Phooks=chunk` and `-Phooks=none` for the cost baseline). Results go to `run-<hooks>/zasspike-results-<hooks>.txt`; copies are in [`results/`](s1s2-ingame/results). |
| [`engine-bench`](engine-bench) | S3, S4, S5 (headless) | `javac -d out src/zas/spike/*.java`, then `java -Xmx4g -cp out zas.spike.GeoBench` (S3, S4) and `java -cp out zas.spike.Pipeline` (S5). Outputs are in [`results/`](engine-bench/results). |

## S0 packaging

```sh
cd s0-packaging
./gradlew :zas-api:publish -PapiVersionOverride=0.2.0   # the consumer's newer API copy, into ./repo
./gradlew build
./gradlew :zas:runGameTestServer                        # dev: zas from source
./gradlew :consumer:runGameTestServer                   # dev: consumer without zas (S-12 shape)
./gradlew :harness:runGameTestServer -Pmods=zas,consumer   # built jars: jar-in-jar selection
./gradlew :harness:runGameTestServer -Pmods=consumer
./gradlew :harness:runGameTestServer -Pmods=zas
./gradlew :harness:runGameTestServer -Pmods=zaswrong,consumer   # pitfall: mismatched identity
```

The trimmed logs in [`evidence/`](s0-packaging/evidence) show the outcome of
each harness run. Local paths in them are replaced with `<repo>`.
`zas-0.0.1-wrong-identity.jar` is the zas jar with its jar-in-jar identifier
edited to the pre-fix value, for the pitfall run.

Not covered here: a dedicated server built with the NeoForge installer (#77).
