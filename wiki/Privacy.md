# Privacy

NeroEvents keeps as little about players as it can, for as short a time as it can, and lets you see,
limit and erase it yourself. The full legal disclosure is in
[PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).

## What is kept about you

| What | Why | How long |
| --- | --- | --- |
| Your **reward history**: which events paid you, what you got, your rank and share | So you are paid only once, and can see what you earned with `/neroevents rewards` | 7 days |
| Your **reward crate**: items and XP waiting to be claimed | So rewards never land on the ground | 14 days, then unclaimed rewards are lost |
| Whether you **opted out** of stored reward history | To honour that choice | Until you opt back in or ask for erasure |
| Your **export file**, if you asked for one | So you can get a copy of your data | 7 days |

Server operators can shorten these times, and nothing is ever kept longer than 90 days whatever the
settings say. A server can also switch the reward history off completely (`rewardLedgerEnabled=false`).

While an event runs, the server also keeps in memory what you did for that event and, when votes are
used, how you voted. That is never saved and disappears when the event ends or the server stops.
During a faction war it also remembers which side you were on when you first came near it, and during a
storm whether it last told you that you were out in the open; both go when the event ends, and an erasure
request removes them at once. In a faction war NeroEvents reads which faction you belong to and asks
NeroFactions to change your reputation at the end; it stores neither. A market shock sends NeroEconomy a
price request that names nobody.

The event history everyone can see holds counts and totals only: which event happened, how it ended, how
many took part and how much it paid in total — never who, and never where.

In a single-player world the "server operator" is you; the export file is written into your world's
folder.

## Your choices

| Command | What it does |
| --- | --- |
| `/neroevents privacy status` | Shows what is kept about you and for how long. |
| `/neroevents privacy optout` | Stops storing reward history under your player id. You are **still rewarded**: credits go to your account, and items and XP wait in a temporary crate that is cleared when the server restarts, so claim them first. Because nothing is stored, a restart during an event could pay you twice or not at all for that event. Rows from before you opted out keep your id until they expire. Opting out again changes nothing. |
| `/neroevents privacy optin` | Stores your reward history again. Rows written while you were opted out stay anonymous (no player id, no rank, no share). |
| `/neroevents privacy export` | Writes everything NeroEvents holds about you to one file in the world folder; ask the server operator for it. It never contains anyone else's data. Once a minute at most. |
| `/neroland data eraseme` | Erases everything NeroEvents (and every other Neroland mod) holds about you. |

**Erasure forfeits unclaimed reward crate items; claim them first with /neroevents claim.**

## The companion app

This build of NeroEvents sends nothing to the NeroLink companion app and sends no notifications. When
that arrives, this page and PRIVACY.md will say exactly what it sends; see
[NeroLink app and notifications](NeroLink-Companion.md) for what is planned.

## Nothing leaves the server

Your reward history, crate and choices stay in the server's world save. NeroEvents' optional error
reports never contain any of it (see
[PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md) for what they do contain and
how to switch them off).
