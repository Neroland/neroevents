# Getting started

NeroEvents runs events across the whole server: supply drops, meteor showers, wrecks, storms, raids
and more. You do not need to read anything to take part — this page is the short version.

## 1. Place an Event Console

Find the **Event Console** in the NeroEvents creative tab (or ask your server's operator for one)
and place it anywhere. Its screen faces you. Anyone can use any console.

- While nothing is happening, the screen shows a slow cyan scan line.
- When an event is announced or running, the screen turns the event's colour and shows its symbol.

Right-click it to open the console.

## 2. Read the console

The console's title says it plainly: **What's happening on this server**. It has five tabs:

| Tab | What it shows |
| --- | --- |
| **Live** | Events running now, what to do, and how long is left |
| **Soon** | Events coming up — and the **YES** / **NO** vote when the server asks |
| **Past** | The last events, in plain sentences |
| **Mine** | What you earned, and the **CLAIM** button |
| **Privacy** | What the server keeps about you, with Export / Opt out / Erase |

Every event row has a coloured band, a symbol and a word that tell you how serious it is — see
[The Event Console](Console.md) for the colours.

## 3. Watch the messages on your screen

You do not have to stand at a console. When an event is announced, a small countdown appears at the
top of your screen ("Meteor Shower on Cindara in 4:32"). When it starts, a banner tells you what to do. During
raids and wars, short lines under the countdown show the waves or the beacon score.

Press **H** to hide these messages until the next change. You can move them, shrink them or switch
parts off — see [HUD settings](HUD-Settings.md).

## 4. Your first supply drop

The first event most servers see is a **supply drop** (it is one of the few events allowed during a new
server's calm window): a chat line says a pod is coming down, with a rough direction, and the console
shows it as INFO until it lands and REWARD after. The console's Live row points the way ("· 320 m
north-east"), and once you are inside the drop area a marker on your screen does too. Get there and open the pod — the first
player to open it claims the drop, and everyone who opens it shares the rewards. See
[Supply drop](Event-Supply-Drop.md).

Server operators can call one straight away to try it out: `/neroevents start neroevents:core/supply_drop`.

## 5. Playing alone

NeroEvents works in a single-player world too. Your first supply drop is rolled 8 minutes into a new world
and lands 5 minutes later; after a 30-minute calm period meteor showers, wrecks and (with NeroCreatures)
smaller pirate raids join in, about one every half hour, never two at once. Votes never appear — there is
nobody to ask. The world boss and faction wars need other players; open your world to LAN to see them.
Events never land within 24 blocks of you or 64 blocks of world spawn, and craters keep away from chests
and machines; to fence off your whole base type `/neroevents zones add home <x1> <z1> <x2> <z2>` (cheats
must be on). The `singlePlayer*` keys in `config/neroevents.properties` tune all of this.

- Events pause when you pause (while the world is not open to LAN).
- If you leave the world during an event it carries on where it was when you come back, but your progress
  toward its rewards starts again.
- Retention and wreck decay count real days even while the world is closed; a market shock runs on the
  clock, so pausing for an hour can end it.
- `rewardBudgetPerDay` applies here too: a long solo session can reach it.

## 6. Collect your rewards

Taking part earns you a share of the event's rewards. Items and experience wait in your **reward
crate**: open a console, go to **Mine** and press **CLAIM** (or type `/neroevents claim`). Credits go
straight to your account when NeroEconomy is installed.

## More

- [How events work](Events.md) — every event and what it can change
- [Protection and fair play](Protection-and-Fair-Play.md) — what events never touch
- [Rewards](Rewards.md) — how your share is worked out
- [Consent and voting](Consent-and-Voting.md) — how votes are counted
- [Privacy](Privacy.md) — what is kept about you and for how long
