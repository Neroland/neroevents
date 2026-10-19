# Configuration

NeroEvents reads `config/neroevents.properties` (created on first launch). Every event key below is
decided by the server and synced to players; only `telemetryEnabled` is a per-player setting. Run
`/neroland config reload` after editing — the event engine picks up the new values at its next
scheduler check (every `schedulerIntervalTicks`).

Times are in ticks: 20 ticks = 1 second, 1200 = 1 minute, 72000 = 1 hour.

## Scheduler

| Key | Default | What it does |
| --- | --- | --- |
| `schedulerEnabled` | `true` | Roll events automatically. `false` stops automatic events; `/neroevents start` still works. |
| `schedulerIntervalTicks` | `600` | How often the scheduler checks whether an event is due (100 to 6000). |
| `schedulerBaseIntervalTicks` | `36000` | Average time between automatic events at a population multiplier of 1.0 (30 minutes). |
| `schedulerJitterFraction` | `0.5` | Random spread on that time: 0.5 means anywhere from half to one and a half times as long. |
| `schedulerRetryTicks` | `6000` | When nothing was eligible, how long until the scheduler tries again. |
| `schedulerGlobalCooldownTicks` | `6000` | The shortest gap allowed between two automatic events. |
| `schedulerBands` | `0-1:2.0:1;2-4:1.0:1;5-9:0.8:2;10+:0.6:3` | Population bands, see below. |
| `schedulerDefinitionsCap` | `256` | The most event definitions loaded from data packs; extras are skipped with a warning. |
| `schedulerPassWarnMicros` | `2000` | A scheduler check slower than this logs one debug line per server start. |
| `calmWindowHours` | `48` | Hours of server uptime before automatic events begin (only events marked calm-exempt may fire earlier). Uptime is world time, so it does not count while the server is off. |
| `maxConcurrentEvents` | `4` | Hard ceiling on events running at once. |
| `maxConcurrentBossEvents` | `1` | World-boss events at once. There is never more than one boss per dimension. |
| `maxConcurrentInvasions` | `1` | Invasion events at once. |
| `maxConcurrentRaids` | `1` | Raid events at once. |

## Single-player

These apply only while one player is alone on an integrated (non-dedicated) server — a single-player
world, or one opened to LAN with nobody else connected. As soon as a second player joins, the next
scheduler check uses the server pacing above.

| Key | Default | What it does |
| --- | --- | --- |
| `singlePlayerPacing` | `true` | Use the `singlePlayer*` keys below when one player is alone on an integrated (non-dedicated) server; `false` keeps dedicated-server pacing in single-player too. |
| `singlePlayerFirstEventMinutes` | `8` | Minutes of play (world time) in a single-player world before the first automatic event is rolled; `0` = no wait (0 to 720). |
| `singlePlayerCalmWindowMinutes` | `30` | Minutes of play (world time) in a single-player world before automatic events other than calm-exempt ones start; replaces `calmWindowHours` while alone (0 to 1440). |
| `singlePlayerIntervalMultiplier` | `1.0` | Multiplies `schedulerBaseIntervalTicks` while alone in a single-player world, in place of the population band's multiplier (1.0 = about 30 minutes between events). |
| `singlePlayerCooldownMultiplier` | `0.5` | Multiplies every event definition's cooldown when it fires while alone in a single-player world (0.1 to 1.0). |

### Population bands

Busy servers see events more often than quiet ones. Each band is `name:multiplier:cap`:

- the **name** is an online-player range, `a-b` or `a+` for the last one,
- the **multiplier** scales `schedulerBaseIntervalTicks` (2.0 = half as often, 0.6 = more often),
- the **cap** is how many events may run at once in that band.

With the default, one player online on a server gets an automatic event about once an hour and at most
one at a time (a single-player world uses the [single-player keys](#single-player) instead); ten or more players get one roughly every 18 minutes and up to three at once. Bands must start
at 0 and follow on without gaps; a table with a mistake is replaced by the default (the log says
which entry was wrong).

## Consent

| Key | Default | What it does |
| --- | --- | --- |
| `consentMode` | `off` | `off`: events just happen. `vote`: online players vote during the countdown. `admin`: only `/neroevents start` fires events. See [Consent and voting](Events.md#consent-and-voting). |
| `consentVotingEnabled` | `true` | Master switch for voting; `false` makes `vote` behave like `off`. |
| `voteQuorumPlayers` | `2` | The fewest votes that count. |
| `voteQuorumFraction` | `0.5` | The share of online players that must vote (the larger of this and `voteQuorumPlayers` applies). |
| `voteMajorityFraction` | `0.5` | An event goes ahead when yes votes are more than this share of the votes cast. A tie fails. |
| `voteMinTicks` | `600` | After a restart, a vote with less countdown than this left is skipped instead of resumed. |
| `voteEarlyCloseEnabled` | `true` | End voting as soon as every online player has voted. The event still starts when the countdown ends. See [Consent and voting](Consent-and-Voting.md). |

## Kill switches

| Key | Default | What it does |
| --- | --- | --- |
| `destructiveEventsEnabled` | `true` | Allow events that can change blocks. `false` removes them from the schedule and refuses `/neroevents start` for them. |
| `combatEventsEnabled` | `true` | Allow combat, raid, invasion, boss and war events. |
| `economyEventsEnabled` | `true` | Allow economy events. |
| `pushNotificationsEnabled` | `true` | Allow companion-app notifications and stored alerts about events. `false` sends none to anyone (live event rows in the app still update). Has no effect until a NeroLink module is present. |
| `eventsDisabled` | *(empty)* | Comma-separated event ids that never fire automatically. |

## Protection

| Key | Default | What it does |
| --- | --- | --- |
| `eventExclusionZones` | *(empty)* | Areas events never touch: `name@dimension:x1,z1,x2,z2`, separated by `;`. Example: `base@minecraft:overworld:-100,-100,100,100`. You can also add zones in game with `/neroevents zones add`. |
| `protectSpawnRadiusBlocks` | `64` | Events keep this far from world spawn (the server's own spawn protection applies if it is larger). A single-player or LAN world has no server spawn-protection setting, so only this value applies there. |
| `protectedBlockNamespaces` | `nerosecurity,nerocolonies` | Mods whose blocks keep destructive events away (see [Protection and fair play](Events.md#protection-and-fair-play)). |
| `protectedNamespaceMarginBlocks` | `8` | How far around an effect those blocks are looked for. |

## Placement and housekeeping

| Key | Default | What it does |
| --- | --- | --- |
| `anchorDensityCellBlocks` | `256` | Events are placed where players are by counting players in squares of this size. Only the counts are used, never anyone's position. |
| `anchorAttemptsPerCell` | `16` | Spots tried in a chosen square before moving on. |
| `anchorMaxCellRolls` | `4` | Squares tried before the event is skipped for lack of a safe place. |
| `anchorMinPlayerDistanceBlocks` | `24` | Events never anchor within this many blocks of a player (`0` = off); counts only, never positions. |
| `instanceRetainTicks` | `1200` | How long a finished event stays visible in `/neroevents status`. |
| `resumableSaveIntervalTicks` | `200` | How often running events save the state they need to survive a restart. |

## Meteors, wrecks, storms, raids and wars

| Key | Default | What it does |
| --- | --- | --- |
| `meteorBlockDamage` | `true` | Meteors dig small craters. `false` makes every impact a soft landing (one loot pocket, no crater). |
| `craterAvoidsBlockEntities` | `true` | A meteor that would land near a chest, machine or any other block entity lands softly instead. |
| `craterBlockEntityMarginBlocks` | `4` | How far around an impact those blocks are looked for (0 to 16). |
| `raidBlockDamage` | `false` | Reserved. NeroEvents never breaks blocks for a raid in this version (the creatures follow the server's normal mob rules); keep it `false`. |
| `wreckLingerDays` | `3` | Real days before a crashed wreck starts to rust away (twice that and it is rubble). `0` keeps wrecks forever. A crate that still holds loot is never removed. |
| `wreckRecordsMax` | `200` | Most wrecks remembered for decay; the oldest are forgotten first (their blocks then stay). |
| `stormEffectsEnabled` | `true` | Server-wide switch for what storms do to players out in the open (each storm event can also turn this off). |
| `stormDrainFractionPerPass` | `0.01` | Share of stored energy a draining storm (solar, ion) takes from an exposed storage per check at full strength. Checks are every 10 seconds. |
| `stormDrainMaxPerPass` | `5000` | Most energy taken from one storage per check. |
| `stormDrainFloorFraction` | `0.2` | A storm never drains a storage below this share of its capacity. |
| `stormDrainChunkRadius` | `3` | Storages are drained only within this many chunks of a player. |
| `stormDrainMaxStoragesPerPass` | `64` | Most storages drained per check. |
| `warMinMembersOnline` | `2` | Members a faction needs online to be drawn into a faction war. |

An energy storage counts as exposed when the sky is open above it. On Fabric only Neroland storages are
drained; on NeoForge and Forge any storage that exposes standard energy is.

### Sibling mods

Some events need another Neroland mod (or a claims mod) on the server. Nothing needs configuring: each
integration switches itself on when the mod is installed and this build of NeroEvents carries support for
it, and `/neroevents status` (operators) lists which integrations are live.

| Mod | What it adds |
| --- | --- |
| Nerospace | Solar storms in space and planetary storms (dust, ion, cryo). |
| NeroAgriculture | Storms slow crop growth while they rage. |
| NeroCreatures | Pirate raids, alien invasions and the world boss. |
| NeroFactions | Faction wars. |
| NeroEconomy | Market shocks. |
| NeroColonies | Colonies are protected from destructive events. |
| Open Parties and Claims | Claimed chunks are protected from destructive events. |

Other claim mods (FTB Chunks, Flan) are not integrated yet; use `eventExclusionZones` to protect those
areas.

## Participation

How taking part is counted (see [Rewards](Rewards.md#who-takes-part)).

| Key | Default | What it does |
| --- | --- | --- |
| `contributionWeightDamage` | `1.0` | Contribution per point of damage (half a heart) dealt to an event's creatures. |
| `contributionWeightLoot` | `25.0` | Contribution per event container, pocket or pod opened (once per container). |
| `contributionWeightObjective` | `50.0` | Contribution per objective captured. |
| `contributionWeightSurvival` | `10.0` | Contribution per wave survived inside the event area, only after helping in another way. |
| `contributionInactivityTicks` | `6000` | Away from the event area (or offline) this long, a contribution halves, and again per further period. |
| `contributionDecayFactor` | `0.5` | What is kept per period away (0.5 = half). |
| `contributionMinWeight` | `0.05` | A contribution that fades below this is forgotten. |
| `contributionCreditFloor` | `1.0` | The least contribution that counts as taking part. |
| `contributionSweepIntervalTicks` | `100` | How often absent players are checked. |

## Rewards

| Key | Default | What it does |
| --- | --- | --- |
| `rewardBudgetPerDay` | `6000` | The most value all events together pay per day (UTC). **0 switches event rewards off** — 0 pays nothing and says nothing. |
| `rewardXpValuePerPoint` | `0.25` | Value of one XP point against the budgets. |
| `rewardItemDefaultValue` | `10` | Value of one reward item whose event gives no value for it. |
| `rewardFloorEligibleRawShare` | `0.01` | The share of the work a player needs before the minimum reward share applies to them. |
| `rewardReputationMaxDeltaPerEvent` | `25` | The largest reputation change one event gives (NeroFactions). |
| `rewardCrateMaxStacksPerPlayer` | `27` | Most unclaimed reward stacks a player can have waiting; more is lost. |
| `rewardLedgerEnabled` | `true` | Keep the short per-player reward history (paid-once across restarts, `/neroevents rewards`). `false` keeps none. |
| `rewardLedgerMaxEntries` | `5000` | Most reward history rows for the whole server; the oldest go first. |
| `bossCreaturesRewardValuePerParticipant` | `150` | What a world boss's own drops count as per participant against the daily budget. |
| `bossContributorMinShare` | `0.05` | The share of a boss fight needed for the boss contributor record. |
| `supplyBeaconDefinition` | `neroevents:core/beacon_supply_drop` | The event a Supply Beacon calls. |

## Privacy and history

| Key | Default | What it does |
| --- | --- | --- |
| `privacyRewardLedgerRetentionDays` | `7` | Days a player's reward history is kept. |
| `privacyRewardCrateRetentionDays` | `14` | Days unclaimed rewards wait before they are lost. |
| `privacyRetentionHardCapDays` | `90` | Nothing about a player is kept longer than this, whatever the keys above say. Can only be lowered. |
| `privacyPurgeIntervalMinutes` | `60` | How often expired history, crate entries and export files are removed (also at every world load). |
| `privacyExportCooldownSeconds` | `60` | Time between two `/neroevents privacy export` requests by one player. |
| `historyMaxEntries` | `200` | How many past events the event history keeps (counts only). |

## Error reporting

| Key | Default | What it does |
| --- | --- | --- |
| `telemetryEnabled` | `true` | Anonymous NeroEvents-only crash reports. Per player, not synced. See [PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md). |
