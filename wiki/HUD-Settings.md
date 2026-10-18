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

Open the settings from the gear next to the Event Console's title, or from the mod list's config
button (NeoForge and Forge).

- **Drag** the countdown, the banner or the objectives in the small picture of your screen. Each one
  snaps to the nearest of nine spots: the corners, the middle of each edge, or the centre. The name of
  the spot appears under the picture.
- **Switches:** Show countdown, Show banner, Show objectives, Show world marker, Impact flash and Storm
  vignette.
- **Scale:** 0.75×, 1.0× or 1.25×.
- **Reset to default** puts everything back at the top centre, all shown, at normal size.

Every change is saved at once (the bottom line says "Saved"). The settings live in
`config/neroevents-hud.properties` on your own computer and are never sent to a server.
