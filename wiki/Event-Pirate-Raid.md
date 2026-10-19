# Pirate raid

Space pirates and rogue machines land near where players are and attack in waves.

| | |
| --- | --- |
| Severity | **COMBAT** (red crossed blades) |
| Needs | **NeroCreatures** |
| Can damage | Players in the fight. **NeroEvents never breaks blocks for a raid**; raiders follow the server's normal mob rules (`mobGriefing`, claim mob protection) |
| Location | Never shown — a raid target is never named, marked or sent to anyone's screen |

> **This build:** the NeroCreatures integration is switched off, so pirate raids do not appear yet.
> They will in a later build.

## What to do

Fight off the raiders. Each wave is announced on your screen ("Wave 2 of 3 · 7 left"). Damage you deal
to the raid's creatures counts as taking part (your arrows and other projectiles count too), and so does
surviving a wave inside the raid area once you have helped in another way. Standing nearby earns
nothing on its own. When the raid ends well, everyone who took part shares the rewards — see
[Rewards](Rewards.md) — and contributors earn the *Raid Repelled* record that the NeroQuests chapter
counts.

## How it works

- There are three waves; each one is scaled by the number of players online, up to a cap per wave. With
  two players the waves are 5 pirates and 3 drones, 6 and 4, then 5 pirates, 3 androids and 3 drones.
- Alone on the planet the waves are about 60 % of the listed size (`solo_scale`): 2 pirates and a drone,
  then 3 and 2, then 2 pirates, an android and a drone. A pirate raid needs only one player, so it also
  comes to a single-player world.
- Creatures spawn on a ring around the target, and every spawn spot is protection-checked first: no
  raider appears inside a claim, a colony, an exclusion zone or the spawn area.
- A wave ends when all its creatures are dead or its time runs out; survivors simply vanish (no loot).
- The raid succeeds when players defeat at least half of everything that was sent.
- In this version raids target **populated areas only**, not colonies, stations or installations.
- At most `maxConcurrentRaids` (1) raid runs at a time.

## Switches

- `combatEventsEnabled=false` — no raids, invasions, bosses or wars.
- `raidBlockDamage` — reserved and always `false` in this version.
- `eventsDisabled` — add `neroevents:creatures/pirate_raid`.

## See also

- [Alien invasion](Event-Alien-Invasion.md)
- [How events work](Events.md)
