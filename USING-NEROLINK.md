# Using NeroLink with NeroEvents

> **Not implemented in this build.** NeroEvents 0.5.0-beta.1 registers **no** NeroLink module: the
> companion app sees nothing from NeroEvents yet, and no notification is ever sent. This page describes
> the planned contract so server owners and app developers know what to expect. It may still change
> before it ships, and only additively.

NeroLink is the Neroland companion bridge: it lets a paired phone see a server's state and send a few
safe actions. Neroland Core owns the link API (`NeroLinkRegistry`, `LinkAlerts`); NeroEvents will plug
into it as one module. Nothing in NeroEvents will depend on NeroLink being installed.

## Module

| | |
| --- | --- |
| Module id | `neroevents` |
| Schema version | `1` |
| Live topic | `neroevents.events` |
| Deep link | `nerolink://server/{server}/events/<instanceId>` |

Every section object carries `"schema_version": 1` and `"asOf"` (epoch milliseconds). All times are
epoch milliseconds; enum fields are names (`"HAZARD"`, `"ACTIVE"`), never numbers.

## Sections

World state is the same for everyone who asks. Only `me` is personal, and only to the player asking.

| Section | Who sees it | What it holds |
| --- | --- | --- |
| `schedule` | everyone | A short summary: "Live now", "Coming up" and "Recently", at most five lines each, with a headline such as "2 live · 1 coming up · next in 4 min". No personal data. |
| `events` | everyone | The live board: the server's effective consent mode, the running events (`active`) and the announced or voting ones (`upcoming`, soonest first). This is also the live topic's snapshot. |
| `active` | everyone | The running events. |
| `upcoming` | everyone | The announced and voting events. |
| `history?limit=1..200` | everyone | The last events, newest first, as counts and totals and one plain sentence each (default 50). Never a location, never a participant. |
| `me` | the requester only | Your own reward rows (newest first, up to 50), your reward crate as counts (entries, stacks, items, XP — never item ids), your opt-out flag and retention days, your own vote on any open vote, the events you asked to be reminded about, and the running events you are taking part in (membership only, no share or rank). |
| `wiki` | everyone | This mod's wiki pages for in-app reading (`?page=<slug>` for one page). |

An event row holds: the event's random instance id and an 8-character short id, the definition and
payload type ids, category, severity, a title such as "Meteor Shower on Cindara", the what-to-do line,
the dimension and its place name, the phase and origin, start and end times, how many players take
part (a number), whether it is destructive, progress (`done` / `total`) when the event has one, the
vote as numbers (`yes`, `no`, quorum, closing time) while it is open, and the outcome when it is over.
A **location** (`x`, `y`, `z`) appears only for events whose definition makes their location public and
that have a public anchor; a raid or invasion target never has one, and the key is then absent.

## Actions

Three actions, each with its own switch, rate limit and duplicate protection. None of them moves the
world.

| Action | Fields | Rules |
| --- | --- | --- |
| `vote` | `instanceId`, `choice` (`"yes"` / `"no"`), `requestId` | Only while you are **online** in the game, only when the server's consent mode is `vote`, only while that event's vote is open. Voting again changes your vote. 10 per minute. |
| `claim_rewards` | `requestId` | Only while you are **online**: moves your own reward crate into your own inventory, exactly like `/neroevents claim`. An empty crate is an answer, not an error. 6 per minute. |
| `remind` | `instanceId`, `enabled` (default `true`), `requestId` | Works while you are offline. Asks for a "starts soon" notification for one event that has not started yet. At most 16 events at a time. 30 per minute. |

The app can **never** start, cancel, skip or reweight an event, change zones, or use the privacy
commands — those stay in the game.

## Notifications

Notifications are off until the player turns them on in the app.

- **Event reminders** — a "starts soon" note (two minutes before the start by default) for events you
  asked about, a note when such an event starts or is called off, and a note when an event you took
  part in ends and you earned something.
- **Every server event** — a separate switch, never on by default: a note whenever any event is
  announced, starts or ends.

Every notification is built from three things only: the event's name, the place and a timing phrase —
for example *"Meteor Shower on Cindara"* / *"Starts in 10 min."* — plus a link that opens that event. It
never contains another player's name or id, coordinates, how anyone voted, or what anyone earned. Each
event sends at most one notification per step, and progress or vote changes never send one. The same
text may be kept as an alert in Neroland Core's per-player alert list until you acknowledge it.

Which events you asked to be reminded about will be kept in memory only, and forgotten when the event
ends or the server restarts. NeroEvents itself will store nothing new for the link.

## Server switches

| Key | Status in this build |
| --- | --- |
| `pushNotificationsEnabled` | Present (default `true`). `false` will send no notification or alert about events to anyone; it has no effect until the module ships. |
| `linkModuleEnabled`, `linkPushAllEnabled`, `linkPushResolvedToContributors`, `linkActionVoteEnabled`, `linkActionClaimEnabled`, `linkActionRemindEnabled`, `linkRemindLeadTicks`, `linkMaxRemindersPerPlayer`, `linkHistoryDefaultLimit` | **Planned, not present yet.** They will switch the module, each kind of notification and each action on and off, and set the reminder lead time and limits. |

When the module ships, `PRIVACY.md` and the wiki will describe exactly what it sends, and this page will
lose its "not implemented" notice.
