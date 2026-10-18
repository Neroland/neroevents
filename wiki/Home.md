# NeroEvents Wiki

Player- and server-owner documentation for **NeroEvents**, part of the Neroland ecosystem. Built on
**Neroland Core**.

> **Status:** beta, building towards 0.5.0. The event engine (scheduling, countdowns, voting,
> protection, the `/neroevents` commands), participation and budgeted rewards with a reward crate, the
> privacy commands, the Event Console, the on-screen event messages, and the supply drop, meteor shower,
> debris crash and market shock events are in. Storms, raids, invasions, the world boss and faction wars
> are written but not switched on in this build; the NeroLink app module comes in a later build.

## Start here

- [Getting started](Getting-Started.md) — place an Event Console, watch your first supply drop, collect your rewards
- [The Event Console](Console.md) — the five tabs, the severity colours, voting and claiming
- [HUD settings](HUD-Settings.md) — the on-screen event messages, the hide key and where they appear

## Events

- [How events work](Events.md) — the life of an event, when events come, and the list of events
- [Supply drop](Event-Supply-Drop.md) — and the Supply Beacon
- [Meteor shower](Event-Meteor-Shower.md)
- [Debris crash](Event-Debris-Crash.md)
- [Solar storm](Event-Solar-Storm.md)
- [Planetary storms](Event-Planetary-Storms.md) — dust storm, ion squall, cryo blizzard
- [Pirate raid](Event-Pirate-Raid.md)
- [Alien invasion](Event-Alien-Invasion.md)
- [World boss: the Cinder Tyrant](Event-World-Boss.md)
- [Market shock](Event-Market-Shock.md)
- [Faction war](Event-Faction-War.md)

## Rules

- [Consent and voting](Consent-and-Voting.md) — how votes are counted and decided
- [Rewards](Rewards.md) — how taking part is counted, how rewards are split, the reward crate and the daily budget
- [Protection and fair play](Protection-and-Fair-Play.md) — what events never touch, and every switch
- [Privacy](Privacy.md) — what NeroEvents keeps about you, for how long, and your choices

## For server owners

- [Commands](Commands.md) — every `/neroevents` command and who may use it
- [Configuration](Configuration.md) — every key in `config/neroevents.properties`
- [Writing your own events](Writing-Your-Own-Events.md) — event definitions in a data pack
- [The gallery](Gallery.md) — every block, entity and event on show in a creative test world

## Other mods

- [NeroLink app and notifications](NeroLink-Companion.md) — coming in a later build
- [NeroEconomy integration](NeroEconomy-Integration.md) — what changes when NeroEconomy is installed
- [NeroQuests integration](NeroQuests-Integration.md) — the *Event Log* quest chapter

## Privacy

NeroEvents sends anonymous, opt-out error reports. Votes and event participation are kept in memory
only. A short reward history (7 days) and your unclaimed reward crate (14 days) are kept in the world
save; you can opt out, export or erase them — see [Privacy](Privacy.md). The full disclosure is in
[PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).
