# Protection and fair play

Events are meant to be fun for everyone on the server, including people who are not there. This page
lists what events never touch, how places are chosen, how rewards stay fair, and every switch a server
operator can use to turn things off.

## How protection works

Before an event touches the world — placing a pod, digging a crater, building a wreck, spawning a
creature, placing a war beacon — it asks every protection rule in turn. **Any single "no" wins**; no
rule can overrule another's refusal. If a rule fails with an error, it counts as a "no" and
`/neroevents status` shows it as failing.

## What events never touch

| Rule | What it protects |
| --- | --- |
| Exclusion zones | Areas the server marks off, in the config (`eventExclusionZones`) or with `/neroevents zones add`. |
| World spawn | Everything within `protectSpawnRadiusBlocks` (64) of world spawn, or the server's own spawn protection when that is larger. |
| Space stations | Every event effect is refused in the Nerospace station dimension: nothing is placed, dug or spawned there. |
| Neroland security and colony blocks | Destructive effects keep clear of block entities from the mods in `protectedBlockNamespaces` (`nerosecurity`, `nerocolonies`), plus `protectedNamespaceMarginBlocks` (8) blocks around them. Only loaded areas can be checked: if part of an effect's area is not loaded, the effect does not happen there. |
| NeroColonies colonies | With NeroColonies installed, nothing destructive happens inside a colony's claimed area. If a dimension cannot be checked, the answer is "no". |
| Open Parties and Claims | With Open Parties and Claims installed, nothing destructive happens in a claimed chunk. |
| Block entities near craters | A meteor that would land within `craterBlockEntityMarginBlocks` (4) of any chest, machine or sign lands softly instead (`craterAvoidsBlockEntities`). |

And, by design:

- **NeroEvents never breaks blocks for a raid or invasion.** Every spawn spot is protection-checked
  first; the creatures themselves follow the server's normal mob rules (`mobGriefing`, your claim
  mod's mob protection).
- **Storms change no blocks.** They only affect players out in the open and drain exposed batteries,
  never below a fifth of their charge.
- **Supply pods break no blocks.**
- **War beacons** go only into empty air, cannot be broken, and are removed when the war ends.
- **Wrecks only ever rust their own blocks**, even if someone claims the area later.
- **Reward items never land on the ground**; they wait in your reward crate.

Destructive events cannot recognise an unclaimed build made of ordinary blocks, and other claim mods
(FTB Chunks, Flan) are not supported yet. Protect such places with `eventExclusionZones`, or turn block
damage off (below).

## How places are chosen

Events are placed **near where players are** by counting how many players are in each area of a
dimension — never by following any one player, and never by anyone's exact position. The counts are
used and thrown away. A chosen spot is checked against every protection rule before the event is
announced; if no safe spot is found, the event is skipped. Announcements give a direction and a rough
distance for public events only, never coordinates, and never name a player. Raid and invasion targets
are never shown to anyone.

Events never anchor within `anchorMinPlayerDistanceBlocks` (24) of any player: a pod or a wreck never
comes down on someone watching the sky. Only a count of players inside that distance is used, never a
position. (An operator's `/neroevents start` with an explicit position is not held to it.)

## How rewards stay fair

- Only what you **do** counts: damage to an event's creatures, opening its containers, capturing
  objectives, surviving waves (after helping in another way). Standing nearby earns nothing.
- Only **real online players** count — never automated "fake players" from other mods.
- One stray hit is not participation (`contributionCreditFloor`), and contributions of players who leave
  fade over time.
- You are **paid once per event**, even if you disconnect and come back.
- Every event has a budget, and the whole server has a daily budget (`rewardBudgetPerDay`).

See [Rewards](Rewards.md) for the details.

## Every switch

All of these are in `config/neroevents.properties` and decided by the server. See
[Configuration](Configuration.md) for the full list.

| Key | Default | Turn it off and… |
| --- | --- | --- |
| `schedulerEnabled` | `true` | no automatic events; operators can still start them. |
| `consentMode` | `off` | `vote` lets players vote on each automatic event; `admin` means only operators start events. |
| `destructiveEventsEnabled` | `true` | every event that can change blocks (meteor showers, debris crashes) is removed, and `/neroevents start` refuses them. |
| `meteorBlockDamage` | `true` | meteors never dig craters; every impact lands softly. |
| `craterAvoidsBlockEntities` | `true` | (keep it on) craters stop avoiding chests and machines. |
| `combatEventsEnabled` | `true` | no raids, invasions, world bosses or faction wars. |
| `economyEventsEnabled` | `true` | no market shocks. |
| `stormEffectsEnabled` | `true` | storms give no status effects. |
| `stormDrainFractionPerPass` | `0.01` | set to `0`: storms drain no energy. |
| `raidBlockDamage` | `false` | reserved; NeroEvents never breaks blocks for a raid in this version. |
| `eventsDisabled` | empty | list event ids that never fire automatically. |
| `eventExclusionZones` | empty | areas events never touch. |
| `protectSpawnRadiusBlocks` | `64` | how far events keep from spawn. |
| `protectedBlockNamespaces` | `nerosecurity,nerocolonies` | mods whose block entities keep destructive events away. |
| `wreckLingerDays` | `3` | set to `0`: wrecks never decay. |
| `rewardBudgetPerDay` | `6000` | set to `0`: events pay no rewards at all. |
| `rewardLedgerEnabled` | `true` | no reward history is kept. |
| `pushNotificationsEnabled` | `true` | no companion-app notifications or alerts (none are sent in this build anyway). |
| `telemetryEnabled` | `true` | per player: no anonymous error reports. |

Each event definition can also be switched off with `"enabled": false`; see
[Writing your own events](Writing-Your-Own-Events.md).

## See also

- [How events work](Events.md)
- [Commands](Commands.md) — `/neroevents zones`, `preview`, `status`
