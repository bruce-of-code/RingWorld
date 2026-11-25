# `contrib-26.3`: notes for maintainers and coding agents

An outside contribution on top of RingWorld `main`. Take any commit, any
part of one, or nothing. None of it has been through RingWorld's own
qualification process, so treat every change as a candidate that needs your
normal review and test runs. The `Docs:` commits (this file, and the README
banner with screenshots under `docs/contrib-26.3/`) are not meant to be merged.
The Distant Horizons fix `6b3ad89` and the later fixes `2663219` and `ea0b9b5`
were added after them; they are listed with the other fixes below. Planned work
is in `docs/contrib-26.3/WORK_PLAN.md`.

- Base: upstream `main` at `725a973` (the Distant Horizons handoff), linear
  history, no merges.
- Target: **Minecraft 26.3, Fabric** only. New version-specific code lives
  under `src/versions/26.3/`. Nothing was ported to 26.1 / 26.2, and
  NeoForge was neither built nor run.
- Build used: Java 25,
  `./gradlew :build -Pminecraft_version=26.3 -Ploader_version=0.19.5 -Ploom_version=1.17.21 -Pfabric_api_version=0.160.5+26.3 -Pneoforge_version=26.3.0.7-beta -Pmoddevgradle_version=2.0.147`.
  The branch builds and passes all existing and new tests at the end of the
  first three fixes (`0eb6b65`) and at the tip.
- License: MPL-2.0, unchanged. Contributions are offered under the same
  terms.

## Commits, grouped and ordered by how easy they are to take

One topic per commit. Within a group, later commits may depend on earlier
ones; the "Needs" column lists every dependency. "Verified in game" means
played on 26.3 Fabric; "test server" means checked on a dedicated
`:runServer` through console commands; "untested" means build and unit
tests only.

### 0. Setup

| Commit | Topic | Needs | Status |
|---|---|---|---|
| `a9b0723` | **Build**: 26.3 copy of the server mixin list, same entries as the shared one (see note A) | none | no behaviour change |

### 1. Bug fixes (small, independent of the features)

| Commit | Topic | Needs | Status |
|---|---|---|---|
| `3b2a043` | Fabric client commands swallowed server-only `/ringworld` sub-commands ("Incorrect argument ... position 10"). Adds a greedy client catch-all that sends `ServerboundChatCommandPacket` directly (calling `sendCommand` recursed into the catch-all). | none | verified in game |
| `ea4e39b` | Locator bar across the seam: the client picks the waypoint image nearest the camera; the server uses shortest-circumference distance for the 332-block azimuth cutoff and for the azimuth itself. | `a9b0723` | untested |
| `0eb6b65` | Explorer / buried-treasure maps and `/locate` pointed beyond the rims. Exterior void chunks still sample real biomes, so vanilla's random-spread search found starts there. Candidates whose locate position is outside the band interior are now skipped. | `a9b0723` | test server: before, buried treasure, mansion, monument and village were all found at \|Z\| > 128; after, all on the band or "not found" |
| `6b3ad89` | **Distant Horizons on a multiplayer server**: with DH installed, the far ring showed the placeholder until DH had surface data for every canonical chunk, ignoring the server Atlas. Client-only DH on a remote server only caches chunks the player has loaded, so the placeholder stayed up indefinitely. `DhTerrainSource.select` now prefers a complete server Atlas over the placeholder until the DH surface is ready, and follows server Atlas republishes. Shared client code (all versions), sits at the branch tip. | none | verified in game on a dedicated server (DH 3.3.4, client-only) |
| `2663219` | **Fix for `ea4e39b`**: a second player joining kicked both players. `ea4e39b` put a helper class in the mixin package, which Mixin refuses to load; it failed on the first waypoint connection between two players. The helper is now a private static method in each locator mixin. Take it with `ea4e39b`. | `ea4e39b` | build only; needs a two-player test |
| `ea0b9b5` | **Atlas cache on a busy server**: the client cache was used only at the server's exact revision, which surface edits keep bumping, so every join started with an empty far ring and re-streamed every tile (about ten minutes on a server behind on ticks). The client now shows its stale cache at once; the full stream still overwrites it. Stopgap for work plan item 1b. | none | build only |

### 2. World generation

| Commit | Topic | Needs | Status |
|---|---|---|---|
| `5762a49` | **Structures**: "More structures" becomes Vanilla / 1.5x / 2x (`RingStructureAbundance`, `RingStructureDensity`). Old boolean saves read as 2x. | none | unit tests (exact per-column expected-count model) |
| `afd5663` | **River**: local meander, width variation, channel water fill (`RingMacroTerrain`, `RingRiverWater`) | none | verified in game |
| `e6a559f` | **River course**: Viterbi course across the band that prefers low terrain and existing wide water (`RingRiverCourse`), exact channel fill, width 0.60 to 1.08 | `afd5663` | course verified in game; final width tweak lightly tested |

### 3. Sky

| Commit | Topic | Needs | Status |
|---|---|---|---|
| `7be3c96` | **Clouds**: distant cloud ring around the whole ring (`RingCloudRing`, `ring_clouds` shaders) | none | verified in game |
| `3d1a123` | **Orbital day cycle** (`/ringworld daycycle central\|orbital`, saved in the sky profile; opt-in in this commit, default for new worlds from `fc7203b`): the ring spins under a distant star tilted 15 degrees out of the ring plane; local time = world time - (X / C) * 24000. Client: local clock, moving sun and stars, per-point lighting of the far ring and clouds. Server: per-longitude sky light, mob burning, spawn light, bed rules, phantoms, and sleep skips to the sleepers' local dawn. Also adds a Vanilla sun size. See `docs/RENDERING.md` (orbital section) and `RingOrbitalDayTest`. | `a9b0723`, `7be3c96` | visuals and sleep skip verified in game; per-longitude sky light checked on test server; phantoms untested |
| `dba98d3` | **Orbital sun colour** follows local elevation; the warm tint is confined to the last ~6 degrees above the horizon (sun disc, far ring, clouds). | `3d1a123` | untested |
| `c9aca01` | **Orbital mobs**: pillager patrols and zombie sieges follow local night (patrols are skipped only when every player is in local daylight and the spawn spot is in local night; the siege roll happens at a player's local siege marker). | `3d1a123` | mixins apply on test server; behaviour untested |
| `fc7203b` | **Orbital by default for new worlds**: new ring worlds take their day cycle from a per-version constant, `RingVersionFeatures.NEW_WORLD_DAY_CYCLE` (orbital on 26.3, central on 26.1 / 26.2, which have no orbital implementation). Existing saves keep their stored cycle. Skip this commit to keep orbital opt-in. | `3d1a123` | test server: a fresh world reports "day cycle is Orbital" |

### Taking a subset

`git cherry-pick <commits>` in the order above applies cleanly. The only
file that conflicts when you skip commits is
`src/versions/26.3/main/resources/ringworld.mixins.json`: each commit
appends its own entries to the end of the list, so resolve by keeping the
entries of the commits you took.

| Commit | Server mixin entries it adds | 26.3 client mixin entries it adds |
|---|---|---|
| `ea4e39b` | `LocatorWaypointServerMixin`, `LocatorAzimuthConnectionMixin` | `LocatorWaypointImageMixin` |
| `0eb6b65` | `ChunkGeneratorBandLocateMixin` | none |
| `3d1a123` | `OrbitalValueSamplerMixin`, `OrbitalAttributeSystemMixin`, `OrbitalServerClockMixin`, `OrbitalLevelDaylightMixin`, `OrbitalServerLevelMixin`, `OrbitalServerPlayerMixin`, `OrbitalPhantomSpawnerMixin` | `ClientClockInstanceMixin`, `ClientClockManagerMixin` |
| `c9aca01` | `OrbitalPatrolSpawnerMixin`, `OrbitalVillageSiegeMixin`, `OrbitalClockInstanceAccessor` | none |

### Note A: the 26.3 server mixin list

`a9b0723` adds `src/versions/26.3/main/resources/ringworld.mixins.json`,
following the existing per-version override pattern already used for
`ringworld.client.mixins.json`: the version file replaces the shared file
of the same name. It lets the 26.3-only server mixins register without
touching the shared list or other versions. If you keep it, any mixin later
added to the shared `ringworld.mixins.json` must also be added to this
copy. If you would rather not keep a copy, register the entries wherever
you normally put version-specific mixins.

### Note B: version scope

The three fixes target vanilla behaviour that probably exists on 26.1 / 26.2
as well, but they were written and checked against 26.3 bytecode only. The
locator mixins target `TrackedWaypoint$Vec3iWaypoint` / `$ChunkWaypoint`,
`WaypointTransmitter$EntityAzimuthConnection` and
`LivingEntity.makeWaypointConnectionWith`; the map fix wraps
`ChunkGenerator.getStructureGeneratingAt` inside the random-spread
`getNearestGeneratedStructure`.

## Known gaps found but not addressed

- Moon phase is counted from the world day, so in orbital mode it can differ
  by one across the ring (cosmetic, with a slight effect on slime spawning).
- 26.3 positional packets (sounds, particles) across the seam were not
  audited; the locator bar was one such gap.
- Villager POI searches (beds, workstations, bells) across the seam were not
  checked.
- The river channel overwrites whatever is in it, so structures it crosses
  can be partly flooded.
- In orbital mode, vanilla's single world-wide siege state can be re-rolled
  by a second player's local midnight (multiplayer only).
