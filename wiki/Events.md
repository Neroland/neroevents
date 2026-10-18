# How events work

NeroEvents runs server-wide events: something is announced, counts down, happens, and is over. This
page explains the rules every event follows and lists the events this version ships (see
[The events](#the-events)). Operators also have a test event, `neroevents:debug_marker`.

## The life of an event

1. **Scheduled.** The scheduler (or an operator) picks an event and a place for it.
2. **Announced.** Everyone online gets a chat line with what is coming, where (see below) and how
   long until it starts.
3. **Voting** *(only when the server uses votes)*. Players vote during the countdown.
4. **Active.** The event happens.
5. **Resolved.** It ends — succeeded, failed, ran out of time — and the result is announced.
6. **Cleaned up.** Anything the event left behind that should not stay is removed.

An event can also be **skipped** (it never started: the vote failed, there was nowhere safe to put it,
or an operator skipped it) or **cancelled** (an operator called it off, or it hit an error). Either
way the event's cooldown is halved, so it can come back sooner.

### Where it is

Some events say where they are ("look a fair way to the north-east of spawn"); others only say which
dimension they are in. Announcements never give coordinates and never mention a player.

### When events come

- Automatic events come more often on busy servers and less often on quiet ones (population bands,
  see [Configuration](Configuration.md#population-bands)).
- A fresh server has a **calm window** (48 hours of uptime by default) in which only gentle,
  calm-exempt events may fire.
- Every event has its own cooldown, and there is a minimum gap between any two automatic events.
- At most one world boss runs at a time (never two in one dimension), and at most one invasion.
- Operators can see the next roll and every cooldown with `/neroevents status`, and dry-run any event
  with `/neroevents preview`.

## Consent and voting

See also [Consent and voting](Consent-and-Voting.md) for the exact rules.

The server chooses one of three modes with `consentMode`:

- **off** (default) — events just happen.
- **vote** — when an automatic event is announced, online players vote with `/neroevents vote yes` or
  `/neroevents vote no` (or the YES / NO buttons on an Event Console) before the countdown ends. It
  goes ahead only with enough votes (the quorum) and more yes than the configured majority; a tie
  fails. You can change your vote until the countdown ends.
- **admin** — nothing fires automatically; only operators start events.

A single-player world is always **off**: there is nobody to ask. A world opened to LAN with two or
more players keeps the configured mode. Events an operator starts never open a vote. Events marked
consent-exempt (such as a supply drop you call yourself) never vote either — and an event that can
change blocks can never be consent-exempt.

Votes are counted while the server runs and are never saved: the counts are public, who voted which
way is not shown to anyone, and a restart during a vote either skips the event or starts a fresh vote.

## Protection and fair play

Before an event touches the world, it asks every protection rule in turn, and any single "no" wins:
exclusion zones, world spawn, the Nerospace station dimension, Neroland security and colony blocks,
NeroColonies colonies and Open Parties and Claims claims. Events are placed **near where players are**
by counting how many players are in each area — never by following any one player — and
[rewards](Rewards.md) go to the players who actually took part. The full rules and every switch are on
[Protection and fair play](Protection-and-Fair-Play.md).

## The events

Each event has its own page: how serious it is, what to do, what it can change in the world and which
mod it needs. An event whose mod is not installed simply never comes up.

| Event | Severity | Needs | Can change blocks | In this build |
| --- | --- | --- | --- | --- |
| [Supply drop](Event-Supply-Drop.md) | REWARD | Neroland Core | No | Yes |
| [Meteor shower](Event-Meteor-Shower.md) | HAZARD | Neroland Core | Yes (craters) | Yes |
| [Debris crash](Event-Debris-Crash.md) | HAZARD | Neroland Core | Yes (a wreck) | Yes |
| [Solar storm](Event-Solar-Storm.md) | HAZARD | Nerospace | No | Not yet |
| [Planetary storms](Event-Planetary-Storms.md) | HAZARD | Nerospace | No | Not yet |
| [Pirate raid](Event-Pirate-Raid.md) | COMBAT | NeroCreatures | No | Not yet |
| [Alien invasion](Event-Alien-Invasion.md) | COMBAT | NeroCreatures | No | Not yet |
| [World boss](Event-World-Boss.md) | COMBAT | NeroCreatures | No | Not yet |
| [Market shock](Event-Market-Shock.md) | INFO | NeroEconomy | No | Yes |
| [Faction war](Event-Faction-War.md) | WAR | NeroFactions | No (temporary beacons) | Not yet |

In this beta build the Nerospace, NeroCreatures and NeroAgriculture integrations are not switched on
yet, and faction wars wait for NeroFactions to be published, so storms, raids, invasions, the world boss
and faction wars do not appear; they will in a later build. Server owners can add their own events:
see [Writing your own events](Writing-Your-Own-Events.md).

## Restarts

If the server stops during an event, the event picks up again after the restart in the same phase
with the same timers. What does not survive is everyone's progress toward that event's rewards:
nobody's participation is written to disk. An event that was paying out when the server stopped is
finished without rewards, so nobody is ever paid twice for it.
