# `contrib-26.3` work plan

Planned work on this branch, in priority order. Not meant to be merged;
it sits with the other `Docs:` material. `BRANCH_NOTES.md` lists what is
done; this file lists what is next. Update both when an item lands: move
the item here to a commit row there.

Status words: **planned** (not started), **in progress**, **done** (with
commit), **dropped** (with reason).

## 1. Terrain Atlas: faster joins on a busy server

Background. The client keeps the Atlas on disk
(`ringworld-cache/terrain-<hash>.rwat.gz`). Upstream reused it only when it
was complete and at the server's exact revision. The server bumps the
revision whenever surface cells change, so on a server people play on the
cache never matched and every join re-streamed every tile (65 536 at
32768 x 512). On a server that is behind on ticks, the adaptive request
rate drops to 1 or 2 and the stream took about ten minutes.
`ea0b9b5` (a stopgap) shows the stale cache at once while the full stream
overwrites it.

### 1a. Stream nearest tiles first (planned, small)

`RingTerrainAtlasServer.ClientStream` walks `populatedTiles()` in index
order (row-major from tile 0), so the tiles around the player can arrive
last. Order the initial pass by shortest-circumference tile distance from
the player's position at request time (Z distance as a tie-break). Keep
the existing interleave with live changes.

Check: join far from X = 0 on an empty client cache; the local horizon
should fill first. Unit test on the ordering.

### 1b. Send only tiles changed since the client's cache (planned, medium)

Goal: a returning client downloads only what changed while it was away.

Sketch:
- Server: keep a per-tile `lastChangedRevision` (`long[tileCount]`),
  updated where `dirtyTiles.publish` runs, saved with the Atlas
  checkpoint. Add a random `atlasInstanceId` created with the Atlas file,
  so two independently edited copies of one save (same hash, same
  revision, different content) never pass for each other. That is the
  case the content fingerprint guards today.
- Client: record, per tile, the server revision it was received at. Save
  it with the cache, together with the instance id.
- Request: send the instance id and a compact per-tile revision summary
  (for example the minimum revision across all tiles plus a list of
  exceptions, since most tiles share one value). The server sends a tile
  when its `lastChangedRevision` is newer than the client's copy, or when
  the client has none.
- Protocol: bump the Atlas protocol version. An old client or old server
  falls back to today's full stream.
- Remove the `markStaleCache` revision-0 stopgap once this lands, or keep
  it only for the fallback path.

Checks: unit tests for the summary encoding and the server's selection;
test server: join, edit a few surface blocks elsewhere with a second
client, rejoin, and the log should report only the edited tiles streamed.

### 1c. Coalesce revision bumps (planned, small, optional)

Every committed batch of surface edits advances the revision. If 1b is
not done, bump at most once a minute so a short reconnect can still hit
an exact match. With 1b in place this matters much less.

## 2. Known gaps (from `BRANCH_NOTES.md`)

| Item | Size | Status |
|---|---|---|
| Positional packets (sounds, particles) across the seam: audit every 26.3 packet that carries a world position and decide whether the receiver needs the nearest image | medium | planned |
| Villager POI searches (beds, workstations, bells) across the seam | medium | planned |
| River channel floods structures it crosses: skip or reroute the fill inside structure bounding boxes | medium | planned |
| Orbital siege state re-rolled by a second player's local midnight: keep the state per player or per siege marker | small | planned |
| Moon phase counted from world day, can differ by one across the ring in orbital mode | small | planned (cosmetic) |

## 3. Rules learned on this branch

- Never put a non-`@Mixin` class in `dev.ringworld.mixin`. Mixin refuses to
  load it, and the failure only shows when the code path runs (the
  locator-bar helper kicked both players when a second player joined;
  fixed in `2663219`). Put helpers in a private static method on the mixin
  or in a normal package.
- Multiplayer-only code paths (two players near each other, waypoint
  connections, sieges) need a two-client test before a commit is marked
  verified.
