# NeroEvents Wiki

Player- and contributor-facing documentation for **NeroEvents**, part of the
Neroland ecosystem. Built on **Neroland Core**.

> **Status:** early alpha (version `0.4.0-alpha.1`), building towards 0.5.0. The event engine is in
> place — scheduling, countdowns, voting, protection and the `/neroevents` commands — with one
> operator test event, plus participation tracking, budgeted rewards with a reward crate, the Supply
> Beacon and the privacy commands. The events themselves and their blocks arrive in later updates.

## Contents

- [Getting started](Getting-Started.md) — place an Event Console, read it, and collect your rewards
- [The Event Console](Console.md) — the five tabs, the severity colours, voting and claiming
- [HUD settings](HUD-Settings.md) — the on-screen event messages, the hide key and where they appear
- [How events work](Events.md) — the life of an event, consent and voting, protection and fair play
- [Rewards](Rewards.md) — how taking part is counted, how rewards are split, the reward crate and the daily budget
- [Consent and voting](Consent-and-Voting.md) — how votes are counted and decided
- [Privacy](Privacy.md) — what NeroEvents keeps about you, for how long, and your choices
- [Commands](Commands.md) — every `/neroevents` command and who may use it
- [Configuration](Configuration.md) — every key in `config/neroevents.properties`
- [NeroEconomy integration](NeroEconomy-Integration.md) — what changes when NeroEconomy is installed
- [NeroQuests integration](NeroQuests-Integration.md) — the *Event Log* quest chapter, and what it needs

Add one page per event, block, item or system as it is built, and link it here. Keep this page as
the index.

## Privacy

NeroEvents sends anonymous, opt-out error reports. Votes and event participation are kept in memory
only. A short reward history (7 days) and your unclaimed reward crate (14 days) are kept in the world
save; you can opt out, export or erase them — see [Privacy](Privacy.md). The full disclosure is in
[PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).
