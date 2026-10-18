# The Event Console

The **Event Console** (`neroevents:event_console`) is the one place to see what is happening on the
server, vote, claim rewards and look after your own data. Anyone can place one and anyone can use
any console. It needs no power.

## The screen on the block

- **Idle:** a cyan scan line — nothing is announced or running.
- **Active:** the screen takes the colour of the most urgent event and blinks its symbol.

## Severity: colour, symbol and word

Every event has one of five severities. The colour is never the only sign: each has its own symbol
and its own word, so you can tell them apart without colour.

| Word | Colour | Symbol | Meaning |
| --- | --- | --- | --- |
| INFO | blue | ring with a dot | Something is happening; nothing to fear |
| REWARD | gold | diamond | Go and get it |
| HAZARD | amber | warning triangle | Protect yourself and your machines |
| COMBAT | red | crossed blades | Fight or stay away |
| WAR | purple | two chevrons | Factions are contesting |

A supply drop shows as INFO while it is announced and turns REWARD when it lands.

## The tabs

### Live

Each row is one running event: its name and world ("Meteor Shower on Cindara"), what to do and the
time left ("Go mining in the craters — 8 min left"), the countdown, how many players have taken part,
and the severity word. When an event's location is public (supply drops, for example) and you are in
the same world, the row also says how far away it is and in which direction. Raid and invasion targets
are never shown.

### Soon

Events that are announced but not started. When the server asks players to vote, the row grows two
big buttons, **YES** and **NO**, with the count under them ("12 yes · 3 no · 15 needed"). Your own
vote shows on the button you chose; you can change it while the vote is open. Votes are counted as
numbers only — nobody can see who voted which way. In single-player there is no vote.

### Past

The last events in plain sentences: "A pirate raid on Cindara was repelled 2 hours ago — 6 players
took part."

### Mine

What is waiting in your reward crate and the big **CLAIM** button, then the rewards you earned
recently. If CLAIM is greyed out, hover it to see why ("Nothing to claim", "Make room in your
inventory", "Already claimed"). Your reward history is kept for a few days and then forgotten; the
tab says how long.

### Privacy

What NeroEvents keeps about you and for how long, and three buttons:

- **Export** saves everything NeroEvents holds about you as one file in the world folder; ask the
  server operator for it.
- **Opt out** stops your rewards being remembered. You still get them. Press **Opt in** to undo.
- **Erase** asks first, then opens the chat with Neroland's erase command typed in; press Enter to
  send it. Claim your rewards before you erase: unclaimed rewards are lost.

See [Privacy](Privacy.md) for the details.

## The bottom line

Every button you press answers in the strip at the bottom of the console: a green square when it
worked, a red cross with the reason when it did not ("Vote closed", "Voting is off on this server",
"That needs an operator"). If the server does not answer, it says so — try again.

## The gear

The small gear next to the title opens [HUD settings](HUD-Settings.md), where you choose where event
messages appear on your screen.

## For operators

Operators see a small cross on each running or upcoming event; it asks first, then calls the event off
for everyone (the same as `/neroevents cancel`). Everything a console sends is checked again on the
server, so a player without permission can never use it.
