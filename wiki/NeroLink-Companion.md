# NeroLink app and notifications

> **Coming in a later build.** This version of NeroEvents does not talk to the NeroLink companion app
> yet: the app shows nothing from NeroEvents, and you never get a notification about an event. This
> page says what is planned, so you know what to expect.

## What the app will show

If a server runs the NeroLink bridge and you pair the NeroLink app, the app will show:

- the events running now and coming up, with what to do and how long is left;
- the last events, in plain sentences;
- **only to you**: what you earned recently and what is waiting in your reward crate.

It will be able to cast your vote while you are in the game, claim your reward crate while you are in
the game, and remind you before one event starts. It will never be able to start, cancel or change an
event.

## Notifications

Notifications will be **off until you turn them on**, in the app:

- **Event reminders** — a "starts soon" note for events you asked about, and a note when an event you
  took part in ends.
- **Every server event** — a separate switch, never on for you by default: a note whenever any event is
  announced, starts or ends.

A notification will contain only the event's name, the place and the time — for example *"Meteor Shower
on Cindara"* / *"Starts in 10 min."* — and a link that opens that event in the app. It will never contain
another player's name, coordinates, how anyone voted or what anyone earned.

## For server operators

`pushNotificationsEnabled=false` already exists: it will switch off every notification and stored alert
about events for everyone, once the app module is in. Further switches arrive with the module.

## See also

- [Privacy](Privacy.md)
