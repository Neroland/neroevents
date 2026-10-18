# Writing your own events

Every NeroEvents event is a JSON file in a data pack, so a server owner can tune the shipped events,
switch them off or add new ones without writing code. This page is the short guide. The full reference —
every field, every payload setting and a complete example for each event type — is
[EVENT-DEFINITIONS.md](https://github.com/Neroland/neroevents/blob/main/EVENT-DEFINITIONS.md).

## 1. Make a data pack

```text
<world>/datapacks/my_events/
  pack.mcmeta
  data/my_events/neroevents/events/night_drop.json
```

The file's id is its namespace and path: `my_events:night_drop`. The shipped events live in the
`neroevents` namespace (for example `neroevents:core/supply_drop`); a file with the same namespace and
path in your pack replaces the shipped one.

## 2. Write the definition

A night-time supply drop for busy servers:

```json
{
  "type": "neroevents:supply_drop",
  "category": "reward",
  "display": {
    "title": "event.my_events.night_drop.title",
    "what_to_do": "event.my_events.night_drop.what_to_do",
    "announce": "event.my_events.night_drop.announce"
  },
  "weight": 4,
  "cooldown_ticks": 72000,
  "announce_ticks": 3600,
  "duration_ticks": 12000,
  "location_public": true,
  "conditions": {
    "dimensions": ["minecraft:overworld"],
    "min_online": 3,
    "time_of_day": { "from": 13000, "to": 23000 }
  },
  "bounds": { "kind": "radius", "radius": 96 },
  "payload": { "tier": 2, "loot_rolls": 3 },
  "rewards": {
    "budget": 400,
    "lines": [
      { "kind": "currency", "amount": 200 },
      { "kind": "xp", "amount": 200 }
    ]
  }
}
```

- `type` is what the event does. The types are `neroevents:supply_drop`, `meteor_shower`,
  `debris_crash`, `solar_storm`, `planetary_storm`, `pirate_raid`, `alien_invasion`, `boss`,
  `market_shock`, `faction_war` and `debug_marker`. A type whose mod is not installed does not exist, and
  a definition using it is skipped.
- `category` decides which switches and limits apply (`reward`, `hazard`, `combat`, `raid`, `invasion`,
  `boss`, `economy`, `war`). An event that changes blocks must say `"destructive": true`, and a
  destructive event can never skip the vote.
- `weight` (0 to 1000) is how often the scheduler picks it; `0` means only an operator starts it.
- Times are in ticks: 20 = 1 second, 1200 = 1 minute, 72000 = 1 hour.

## 3. Give it words

The `display` entries are translation keys. Put them in a resource pack for your players
(`assets/my_events/lang/en_us.json`):

```json
{
  "event.my_events.night_drop.title": "Night Drop",
  "event.my_events.night_drop.what_to_do": "Find the pod in the dark and open it first.",
  "event.my_events.night_drop.announce": "A supply pod is coming down in %1$s in about %2$s min. %3$s",
  "event.my_events.night_drop.todo": "Find the pod — %s"
}
```

In `announce`, `%1$s` is the dimension, `%2$s` the minutes until it starts and `%3$s` where it is. The
optional `.todo` line is the short instruction on the console and the HUD; `%s` is the time left.

## 4. Load it and check it

1. `/reload`.
2. `/neroevents list` — your event is listed; the skipped count tells you if a file was rejected.
3. `/neroevents preview my_events:night_drop` — every scheduling rule with a tick or a cross, a sample
   place (or which protection refused one), whether it would open a vote, its reward budget and every
   warning about the file.
4. `/neroevents start my_events:night_drop` in a test world.

A file with a mistake never stops the server: the log names the file and the key. A missing required
field, an unknown type or a payload value outside its range skips the file; an out-of-range optional
field falls back to its default with a warning.

## Limits that always apply

Your events follow every rule the shipped ones do: the kill switches, the protection rules, the calm
window, the concurrency caps and the daily reward budget. See
[Protection and fair play](Protection-and-Fair-Play.md).

## See also

- [How events work](Events.md)
- [Configuration](Configuration.md) — `schedulerBands`, `eventsDisabled`, `schedulerDefinitionsCap`
