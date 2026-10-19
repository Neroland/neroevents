# The gallery

`/neroevents gallery` builds a showcase of everything NeroEvents adds, north of where you stand: every block
and entity, plus one **station** per event type that shows the event happening, and a demo HUD and Event
Console that cycle through every kind of event message. It is a tool for screenshots, videos and trying the
mod out. It is for operators in creative mode only. In single-player, create the test world with cheats on.

**Use a test world.** The gallery replaces every block in a 41 × 69 area (and 18 blocks up) north of you,
and removes loose dropped items within 64 blocks.

## What it is not

Nothing in the gallery is a real event. Nothing is paid, nobody is credited, no reward crate, reward history,
event history or wreck record is written, no vote is counted, no progression milestone or quest is ticked and
nothing is sent to another mod. The pods are empty, the crater pocket and the salvage crates hold no loot, the
raid, invasion and boss bays hold labels only (no creature is spawned there: in creative mode you can kill
anything, and a killed creature drops its loot), and the storm preview is on your screen only. The supply pods,
meteors, debris, labels and item frames on display cannot be hurt by anything but a creative player.
Before anything is built the whole area is checked against the server's protection rules; if any of them
refuses (the spawn-protection radius, a colony, a claim, an exclusion zone), nothing is built and the reply
says which rule refused it.

## The commands

| Command | What it builds |
| --- | --- |
| `/neroevents gallery` | Everything: the exhibits, the stations and the demo HUD and console |
| `/neroevents gallery exhibits` | Only the three rows of block and entity exhibits |
| `/neroevents gallery events` | Only the stations, the wreck yard and the demo |
| `/neroevents gallery stress` | Everything plus 40 meteors, 10 pods and an ion storm preview (for `/nsevrender`) |
| `/neroevents gallery storm <storm>` | Pins the storm preview to `solar`, `dust`, `ion` or `cryo`, or `off` |
| `/neroevents gallery cue <station>` | Replays a station at its best moment, pins a HUD scene or opens the demo console |
| `/neroevents gallery clear` | Standing where you built it: takes it all down and ends the demo |

The demo runs while you stay in the gallery's dimension. Logging out or changing dimension stops the
station loops (the blocks and exhibits stay until you clear them); run `/neroevents gallery` again to restart
it. Only one demo runs per server: a second operator building a gallery replaces the first one's demo.

## The exhibits

The three rows nearest you: meteor slag, cooled crust, embedded meteor ore, a crater pocket, an idle and an
active Event Console, hull plating, a scorched panel, a bent strut, a closed and an opened salvage crate;
supply pods of tiers 1 to 3 landed with their beacons lit, a fourth descending under its canopy, and a war
beacon; two meteors and a piece of debris hanging in the air, tumbling and trailing smoke.

## The stations

Each station has a floating label. A station whose real event needs another mod says so on a second line
(for example *needs NeroCreatures*); it is still built, because its blocks belong to NeroEvents.

| Station | What happens there |
| --- | --- |
| **Supply drop** | An empty pod drops onto a lit pad every 20 seconds, cycling through the three tiers, lands with its sound and lights its beacon. |
| **Meteor shower** | A real crater (crust, slag ring, embedded ore and a crater pocket) dug into a stone plinth; a meteor streaks into it every 5 seconds with its whoosh, impact sound and flash. The pocket holds nothing. |
| **Debris crash** | A hull fragment falls onto a pod-shell wreck every 6 seconds, with dust and embers. The crate holds nothing. |
| **Wreck yard** | The four wreck layouts (strut field, cargo spill, hull rib, pod shell) and the pod shell at both decay stages: scorched after `wreckLingerDays`, then rubble. |
| **Faction war** | Three war beacons in two demo colours; the middle one is captured by one side, held, then taken back, with the capture ring, the contested look and the capture sounds. No faction is involved. *Needs NeroFactions* for the real event. |
| **Pirate raid / alien invasion** | The raid and invasion bay, with a label for each: the real raiders and aliens arrive in waves through NeroCreatures. No creature is spawned. *Needs NeroCreatures* for the real event. |
| **World boss** | The boss arena (a crying-obsidian ring) with a label: a real boss is summoned through NeroCreatures. No boss is spawned, so nothing here can start or end a fight. *Needs NeroCreatures* for the real event. |
| **Storms** | Four posts for the solar storm, the dust storm, the ion squall and the cryo blizzard. While you stand in the gallery your sky, fog, particles and sound cycle through all four (the shelter line on the HUD shows too). This preview is drawn by NeroEvents itself, so it works without Nerospace, and it does nothing else: no power drain, no effects, no other player sees it. If a real storm is running in the dimension, the preview stays off. *Needs Nerospace* for the real event. |
| **Market shock** | A board of six goods in item frames with the price changes the shipped market shock makes, and an Event Console. *Needs NeroEconomy* for the real event. |

## The demo HUD and console

While the demo runs, your HUD cycles every 20 seconds through five scenes — an information event with an open
vote, a supply drop running, a meteor shower and a dust storm, a pirate raid with a boss coming, and a faction
war with its objective line and world marker — so every severity colour, the banner, the countdown pill, the
vote line and the objective lines can be seen. The gallery's own Event Consoles light up in the same colour,
and opening one shows a demo console with every tab filled in. The demo's buttons do nothing.

Only you see the demo, and only while nothing real is happening. As soon as a real event is announced or
running, the real HUD and console take over and the demo says so once.

## Screenshots

`/nsevgallery capture` rebuilds the gallery where you stand and takes a fixed list of screenshots into
`screenshots/neroevents/`; `/nsevgallery capture stress` shoots the renderer-budget scene and saves the
`/nsevrender` numbers beside it. See [Commands](Commands.md#client-only-helpers).

## Privacy

While the demo runs, the server remembers which player started it (their account id), in memory only, so the
demo HUD and console go to that player and nobody else. It is forgotten when the gallery is cleared, when that
player logs out or changes dimension, and when the server stops. It is never saved or logged. The demo screens
themselves carry no player names or ids.

The screenshots stay on your computer, but other players in the world are **not** hidden: their names and
skins can appear in a frame. A player's name is personal data and a published image cannot be taken back, so
check every capture for other players' names before you publish it, or capture in a world where nobody else
is online.
