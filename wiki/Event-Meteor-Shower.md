# Meteor shower

Meteors fall over an area for a few minutes. Each one digs a small crater with a hot crater pocket in
the middle.

| | |
| --- | --- |
| Severity | **HAZARD** (amber warning triangle) |
| Needs | Neroland Core only (the space variant needs a mod that adds space dimensions, such as Nerospace) |
| Can damage | **Terrain**: craters replace the ground where meteors land |
| Location | Announced by dimension only |

## What to do

Keep clear while it falls, then go mining in the craters. Break a **crater pocket** for loot; each one
you open counts as taking part, and when the shower ends everyone who took part shares its rewards —
see [Rewards](Rewards.md). The craters and their meteor ore stay after the event.

## What it changes

Each impact digs a crater of meteor rock (radius 2 to 4) with some embedded meteor ore and a crater
pocket. A crater **never** lands:

- in a protected area — exclusion zones, near world spawn, in a NeroColonies colony or an Open Parties
  and Claims claim, near Neroland security or colony blocks, or in an area that cannot be checked
  because it is not loaded (see [Protection and fair play](Protection-and-Fair-Play.md));
- within `craterBlockEntityMarginBlocks` (4) blocks of any block entity — a chest, a machine, a sign —
  while `craterAvoidsBlockEntities` is on;
- when `meteorBlockDamage` is off or `destructiveEventsEnabled` is off.

In those cases the meteor lands softly: no crater, only the pocket. A few craters per chunk and per
shower at most; further meteors land softly too.

## The two meteor events

| Event | Where |
| --- | --- |
| Meteor Shower (`neroevents:core/meteor_shower`) | The overworld. |
| Meteor Storm (`neroevents:core/meteor_shower_space`) | Space dimensions (dimension types in `neroland:space/dimensions`), with more meteors and more ore. |

## Switches

- `meteorBlockDamage=false` — every impact is a soft landing.
- `destructiveEventsEnabled=false` — no meteor events at all.
- `craterAvoidsBlockEntities`, `craterBlockEntityMarginBlocks` — keep craters away from block entities.
- On your own screen, the *Impact flash* switch in [HUD settings](HUD-Settings.md) turns the bright
  impact flash off.

## See also

- [How events work](Events.md)
- [Writing your own events](Writing-Your-Own-Events.md) — the meteor-shower payload settings
