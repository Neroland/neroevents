# Faction war

The two factions with the most members online fight over a ring of war beacons.

| | |
| --- | --- |
| Severity | **WAR** (purple two chevrons) |
| Needs | **NeroFactions** 0.7.0-beta.1 or later |
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
- Never rolled in a single-player world (`single_player` in `/neroevents preview`).
- Your side is fixed the first time you come near the war: changing faction mid-war changes nothing.
- Players in both factions or neither stay **neutral**: they never capture, score or earn.

## Rewards

Everyone on a side who took part shares the event's credits and XP — see [Rewards](Rewards.md).

- **Winners** gain event reputation with their own faction: the war's `reputation_win` (10), at most
  `rewardReputationMaxDeltaPerEvent` (25). It is awarded once per war, and a chat line tells you how much
  NeroFactions actually applied. NeroFactions limits how much event reputation you can earn per day, so
  near that limit you may get less, or "No reputation ... this time".
- **Losers** lose a little (`reputation_loss`, 5), within the same per-event limit.
- Set `reputation_win` to `0` in a war's definition to turn the victory award off.

NeroEvents only reads which faction you are in; NeroFactions keeps the reputation.

## Switches

- `combatEventsEnabled=false` — no wars (or raids, invasions and bosses).
- `eventsDisabled` — add `neroevents:factions/faction_war`.

## See also

- [How events work](Events.md)
