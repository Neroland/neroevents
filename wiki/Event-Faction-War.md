# Faction war

The two factions with the most members online fight over a ring of war beacons.

| | |
| --- | --- |
| Severity | **WAR** (purple two chevrons) |
| Needs | **NeroFactions** |
| Can damage | Players in the fight. The war beacons are placed only in empty air outside protected areas, cannot be broken, and are removed when the war ends |
| Location | Public — the announcement, the console and the HUD say roughly where it is |

> **This build:** faction wars are switched off until NeroFactions is published, so they do not
> appear yet. The war beacon block is already registered, so worlds keep it either way.

## What to do

Stand at a beacon with your faction, and no enemy, to capture it; the ring on the ground fills as you
capture. Holding beacons scores points over time. The score is shown under the countdown on your screen
("Red 34 · Blue 21"). The side with the higher score when time runs out wins; a tie has no winner.

## Who fights

- Each faction needs at least `warMinMembersOnline` (2) members online; the two with the most members
  online fight. With fewer than two such factions the war is skipped.
- Your side is fixed the first time you come near the war: changing faction mid-war changes nothing.
- Players in both factions or neither stay **neutral**: they never capture, score or earn.

## Rewards

Everyone on a side who took part shares the event's credits and XP — see [Rewards](Rewards.md). The
winning side's fighters gain reputation with their own faction and the losing side's lose a little,
at most `rewardReputationMaxDeltaPerEvent` (25) per event. NeroEvents only reads which faction you are
in; NeroFactions keeps the reputation.

## Switches

- `combatEventsEnabled=false` — no wars (or raids, invasions and bosses).
- `eventsDisabled` — add `neroevents:factions/faction_war`.

## See also

- [How events work](Events.md)
