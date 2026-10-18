# Alien invasion

An alien swarm arrives in a space dimension and attacks an area where players are, in waves.

| | |
| --- | --- |
| Severity | **COMBAT** (red crossed blades) |
| Needs | **NeroCreatures** (and space dimensions, such as Nerospace's) |
| Can damage | Players in the fight. **The swarm never breaks blocks** |
| Location | Never shown — an invasion target is never named, marked or sent to anyone's screen |

> **This build:** the NeroCreatures integration is switched off, so alien invasions do not appear yet.
> They will in a later build.

## What to do

Hold the line against the swarm. Each wave is announced on your screen. Damage you deal to the
invasion's creatures counts as taking part, and so does surviving a wave inside the area once you have
helped in another way. When the invasion is beaten back, everyone who took part shares the rewards —
see [Rewards](Rewards.md).

## How it works

It works like a [pirate raid](Event-Pirate-Raid.md), bigger: four waves of void crawlers, plasma slimes,
lunar stalkers and asteroid worms, scaled by the number of players online. It needs at least 3 players
online in the `5-9` or `10+` population band, and only one invasion runs at a time
(`maxConcurrentInvasions`). Every spawn spot is protection-checked; survivors of a wave vanish when its
time runs out; the invasion succeeds when players defeat at least half of everything sent.

## Switches

- `combatEventsEnabled=false` — no raids, invasions, bosses or wars.
- `eventsDisabled` — add `neroevents:creatures/alien_invasion`.

## See also

- [How events work](Events.md)
