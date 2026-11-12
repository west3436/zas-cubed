# ZAS³ Ponder

The addon for players without Create (D-83): a client mod, `zas_ponder`, that
nests Ponder and Flywheel, which no site publishes on their own for 1.21.1. It
holds no code and no scenes. The scenes are part of `zas_atmos`
([compat-ponder](../compat-ponder/README.md)) and show with any Ponder: Create's,
or the one this addon brings.

- It requires `zas_atmos` of its own version or later, so a later ZAS³ needs no
  new addon.
- With Create installed as well, FML loads one of the two copies of Ponder and
  of Flywheel, and not always the newer. The addon's nested ranges start at the
  copies Create 6.0.10 nests, so each mod accepts the other's.
- Releases up to 1.0.0 held the scenes themselves and accept only `zas_atmos` of
  their own version. Beside one of those, `zas_atmos` leaves its own scenes off.

`./gradlew :zas-ponder:jar` builds `build/libs/zas_ponder-<ver>.jar`.
`./gradlew :zas-ponder:runServerCheckGameTestServer` loads that jar, with what it
nests, on a dedicated GameTest server beside the release jar (CLIENT-025). CI
runs it on every PR.

The versions it nests are `ponder_version` and `flywheel_version` in
`gradle.properties`. Ponder's and Flywheel's licences ship in the jar, under
`META-INF/licenses`.
