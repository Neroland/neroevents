# HUD settings

The event messages on your screen are small, can be hidden with one key, and can be moved.

## What appears

| Message | When | Shows |
| --- | --- | --- |
| Countdown | An event is announced or being voted on | "Meteor Shower in 4:32"; during a vote, "Vote at a console · 12 yes · 3 no" |
| Banner | The first few seconds after an event starts | The event and what to do, then it shrinks back to the countdown |
| Objectives | During raids, invasions and faction wars | "Wave 2 of 5 · 7 left", the beacon score |
| World marker | A public event is running and you are inside its area | The event's symbol pointing at it, with the distance |
| Storm line | You are out in the open during a storm | "Shelter from the solar storm", plus a coloured screen edge |

Each message has the event's colour band and symbol on its left.

## Hide them

Press **H** (change it under *Controls*, "Hide event messages") to hide all event messages until
something changes — a new event is announced, a vote opens, an event starts or ends.

## Move and resize them

Press **J** anywhere in a world (change it under *Controls*, "Move event messages") to open the
settings. You can also use the gear next to the Event Console's title, or the mod list's config
button (NeoForge and Forge).

- **Drag** the countdown, the banner or the objectives anywhere in the small picture of your screen.
  Drop one on one of the nine dots (the corners, the middle of each edge, or the centre) and it
  snaps exactly there; drop it anywhere else and it stays where you let go. The name of the nearest
  spot appears under the picture ("near Top right" when it is not exactly on the dot).
- **Arrow keys** nudge the selected message one pixel at a time (hold **Shift** for ten).
- Positions are kept as a share of your screen, so they stay put when you resize the window or
  change the GUI scale, and a message is never pushed off the edge.
- If the countdown and the objectives sit on the same spot they stack instead of overlapping.
- **Switches:** Show countdown, Show banner, Show objectives, Show world marker, Impact flash and Storm
  vignette.
- **Scale:** 0.75×, 1.0× or 1.25×.
- **Reset to default** puts everything back exactly on the top centre, all shown, at normal size.

Every change is saved at once (the bottom line says "Saved"). The settings live in
`config/neroevents-hud.properties` on your own computer and are never sent to a server.
