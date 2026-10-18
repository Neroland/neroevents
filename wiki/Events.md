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
  `/neroevents vote no` before the countdown ends. It goes ahead only with enough votes (the quorum)
  and more yes than the configured majority; a tie fails. You can change your vote until the
  countdown ends.
- **admin** — nothing fires automatically; only operators start events.

A single-player world is always **off**: there is nobody to ask. A world opened to LAN with two or
more players keeps the configured mode. Events an operator starts never open a vote. Events marked
consent-exempt (such as a supply drop you call yourself) never vote either — and an event that can
change blocks can never be consent-exempt.

Votes are counted while the server runs and are never saved: the counts are public, who voted which
way is not shown to anyone, and a restart during a vote either skips the event or starts a fresh vote.

## Protection and fair play

Before an event touches the world, it asks every protection rule in turn, and any single "no" wins.
The rules in this version:

- **Exclusion zones** — areas the server marks off (`eventExclusionZones`, or
  `/neroevents zones add`).
- **World spawn** — events keep `protectSpawnRadiusBlocks` (64 by default) away from spawn.
- **Space stations** — nothing destructive ever happens in the Nerospace station dimension.
- **Neroland security and colony blocks** — destructive effects keep clear of blocks from the mods in
  `protectedBlockNamespaces`, plus a margin. Only loaded areas can be checked; if part of the area is
  not loaded, the effect does not happen there.
- **NeroColonies colonies** — with NeroColonies installed, nothing destructive happens inside a
  colony's claimed area.
- **Open Parties and Claims** — with Open Parties and Claims installed, nothing destructive happens in
  a claimed chunk.

Destructive events keep clear of Neroland security and colony blocks, of NeroColonies colonies and of
chunks claimed with Open Parties and Claims. They cannot recognise an unclaimed build made of ordinary
blocks, and other claim mods (FTB Chunks, Flan) are not supported yet — use `eventExclusionZones` or
leave block damage off.

If a protection rule ever fails with an error, it counts as a "no" (destructive effects it covers are
refused) and `/neroevents status` shows it as failing.

Events are placed **near where players are** by counting how many players are in each area of the
dimension — never by following any one player — and [rewards](Rewards.md) go to the players who
actually took part, never to whoever happens to be standing nearby.

## The events

Each event below says what it can change in the world and which mod it needs, if any. An event whose mod
is not installed simply never comes up. In this beta build the Nerospace, NeroCreatures and
NeroAgriculture integrations are not switched on yet, so storms, raids, invasions and the world boss do
not appear; they will in a later build.

### Supply drop (no extra mod)

A supply pod falls from the sky. The first player to open it claims the drop; others who open it later
still get a share. The pod lands only where it can see the sky and breaks no blocks. A **Supply Beacon**
calls one to you; a called drop never needs a vote.

### Meteor shower (no extra mod)

Meteors fall over an area for a few minutes. Each one digs a small crater of meteor rock with a hot
**crater pocket** in the middle: break it for loot. It can change blocks: craters replace the ground
where they land, but never in a protected area, never next to chests or machines, and never at all when
`meteorBlockDamage` is off (then every meteor lands softly and leaves only the pocket).

### Debris crash (no extra mod)

A piece of wreckage crashes and leaves a small wreck with salvage crates. Open the crates before
someone else does. It places blocks (hull plating, struts and crates) where it lands, outside protected
areas. A crate cannot be broken while it still holds loot; if one is blown up, its loot drops where it
stood. After a few real days the wreck rusts away (`wreckLingerDays`). Rusting only ever replaces the
wreck's own blocks with rubble or air — even if someone claims the area later — and never touches
anything else. Hull plating, panels and struts you mine and place elsewhere are yours.

### Solar storm and planetary storms (need Nerospace)

Weather for space. A **solar storm** reaches any space dimension; a **planetary storm** is the world's
own kind (dust storm, ion squall or cryo blizzard). Only players out in the open are affected: get under
a solid roof or into a pressurised room (glass roofs do not count). A weak blizzard only chills; at full
strength, players in the open take frost damage unless they wear leather. Solar storms and ion squalls
slowly drain energy from batteries left under open sky near players, never below a fifth of their
charge. Storms change no blocks and pay no rewards. With NeroAgriculture installed, crops grow more
slowly while a storm rages.

### Pirate raid and alien invasion (need NeroCreatures)

Waves of hostile creatures attack an area where players are. Fight them off: damage dealt and waves
survived count toward the reward. Raid creatures never break blocks. In this version raids target
populated areas only, not colonies or stations.

### World boss: the Cinder Tyrant (needs NeroCreatures)

A world boss lands. Everyone who deals damage shares the reward. It changes no blocks itself. If nobody
fights it, it gets away. Only one boss runs at a time.

### Market shock (needs NeroEconomy)

Traders report a shortage, glut or rush on one good, and its price moves for a while. Buy low, sell high.
It changes nothing in the world. If the market does not respond, the event ends as a failure (its
cooldown is not shortened), and after two in a row market shocks pause until the server restarts.

### Faction war (needs NeroFactions)

The two factions with the most members online fight over a ring of war beacons. Stand at a beacon with
your faction, and no enemy, to capture it; holding beacons scores points. The winning side's fighters
gain reputation with their own faction and the losing side's lose a little; players in both or neither
faction stay neutral. The beacons are placed outside protected areas, cannot be broken, and are removed
when the war ends.

## Restarts

If the server stops during an event, the event picks up again after the restart in the same phase
with the same timers. What does not survive is everyone's progress toward that event's rewards:
nobody's participation is written to disk. An event that was paying out when the server stopped is
finished without rewards, so nobody is ever paid twice for it.
