# World boss: the Cinder Tyrant

A world boss lands in a space dimension. Team up and bring it down.

| | |
| --- | --- |
| Severity | **COMBAT** (red crossed blades) |
| Needs | **NeroCreatures** (and space dimensions, such as Nerospace's) |
| Can damage | Players in the fight. NeroEvents changes no blocks for it; the boss's own behaviour is NeroCreatures' |
| Location | Public — the announcement, the console and the HUD say roughly where it is |

> **This build:** the NeroCreatures integration is switched off, so the world boss does not appear
> yet. It will in a later build.

Needs at least two players online; it is never rolled in a single-player world.

## What to do

Find the boss and fight it. **Everyone who deals damage shares the reward**; damage is the only thing
that counts for a boss. When it falls:

- NeroCreatures pays the boss's own drops;
- NeroEvents pays the event's credits and XP to everyone who took part — see [Rewards](Rewards.md);
- players who did at least `bossContributorMinShare` (5 %) of the fight earn the *Boss Contributor*
  record, and the server's *Giant Slain* milestone fires (NeroQuests).

If nobody fights it, or it is still standing when time runs out, the boss **gets away** and nobody is
paid. The boss's own drops count against the daily reward budget too
(`bossCreaturesRewardValuePerParticipant` per participant).

## Limits

At most `maxConcurrentBossEvents` (1) boss runs at a time, and never two in one dimension. It needs at
least 2 players online.

## Switches

- `combatEventsEnabled=false` — no bosses (or raids, invasions and wars).
- `eventsDisabled` — add `neroevents:creatures/cinder_tyrant`.

## See also

- [How events work](Events.md)
