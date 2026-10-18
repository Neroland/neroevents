# Debris crash

A piece of wreckage crashes and leaves a small wreck with salvage crates.

| | |
| --- | --- |
| Severity | **HAZARD** (amber warning triangle) |
| Needs | Neroland Core only |
| Can damage | **Terrain**: the wreck places blocks (hull plating, scorched panels, bent struts and salvage crates) where it lands |
| Location | Announced by dimension only |

## What to do

Find the wreck and salvage its crates before someone else does. Each crate you open counts as taking
part; when the event ends everyone who took part shares its rewards — see [Rewards](Rewards.md).

## What it changes

The wreck is placed only outside protected areas (see
[Protection and fair play](Protection-and-Fair-Play.md)). A salvage crate cannot be broken while it
still holds loot (a line on screen says so); if one is blown up or broken in creative mode, its loot
drops where it stood.

After `wreckLingerDays` (3) real days the wreck starts to rust away, and after twice that it becomes
rubble. Rusting only ever replaces the wreck's own blocks with rubble or air — even if someone claims
the area later — and never touches anything else. Hull plating, panels and struts you mine and place
elsewhere are yours and never rust. At most `wreckRecordsMax` (200) wrecks are remembered; when that
is full the oldest is forgotten and its blocks then never decay.

## Switches

- `destructiveEventsEnabled=false` — no debris crashes.
- `wreckLingerDays=0` — wrecks never decay.

## See also

- [How events work](Events.md)
- [Writing your own events](Writing-Your-Own-Events.md) — the debris-crash payload settings
