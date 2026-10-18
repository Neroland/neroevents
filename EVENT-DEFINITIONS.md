# Event definitions

Every NeroEvents event is a JSON file in a data pack. You can change the shipped events, switch them
off or add your own without writing code. This page is the full reference: every field, its default,
what happens when a value is wrong, and one complete working example for each payload type.

## Where the files go

```text
<world>/datapacks/<your pack>/
  pack.mcmeta
  data/<namespace>/neroevents/events/<path>.json
```

The file `data/<namespace>/neroevents/events/<path>.json` becomes the definition id
`<namespace>:<path>`. The shipped meteor shower, for example, is
`data/neroevents/neroevents/events/core/meteor_shower.json`, id **`neroevents:core/meteor_shower`** —
that is the id you type in `/neroevents start`, `/neroevents preview` and `eventsDisabled`.

- Run `/reload` after editing; the event engine notices the new definitions at its next scheduler check.
- To **change** a shipped event, put a file with the same namespace and path in a pack that loads after
  the mod's own data (data-pack order, as for any data). To **stop one firing automatically**, either
  override it with `"enabled": false` or list its id in `eventsDisabled` (operators can still start it
  by hand; the kill switches stop it for everyone).
- At most `schedulerDefinitionsCap` (256) definitions load; extras (by id order) are skipped with a
  warning.

## What happens when something is wrong

The server always starts. Each problem is logged once with the definition id and the key, and shows up
in `/neroevents list` (operators see how many were skipped) and in `/neroevents preview <definition>`.

- **Dropped** — the file is not loaded at all: a required field is missing (`type`, `category`,
  `weight`, `display.title`, `display.what_to_do`, `display.announce`), `weight` is outside 0..1000, the
  JSON cannot be read, `type` names a payload that does not exist (for example because its sibling mod
  is not installed), a mod in `conditions.requires_mods` is missing, a rule is broken (see the notes in
  the tables), or **any `payload` value is outside its range or of the wrong kind**.
- **Reset** — an out-of-range optional top-level field is replaced by its default, with a warning
  (`cooldown_ticks`, `announce_ticks`, `duration_ticks`, `bounds.radius`,
  `conditions.required_gate_holders`, `conditions.time_of_day`, and the `rewards` numbers).
- **Ignored** — an unknown key is warned about and ignored; an unusable reward line is dropped and the
  rest of the definition kept; a reward item that is not registered is removed from the lines.

Times are in ticks: 20 ticks = 1 second, 1200 = 1 minute, 72000 = 1 hour.

## Top-level fields

| Field | Type | Default | Rules |
| --- | --- | --- | --- |
| `type` | id | **required** | The payload type: what the event actually does (see [Payload types](#payload-types)). |
| `category` | string | **required** | One of `reward`, `hazard`, `combat`, `raid`, `invasion`, `boss`, `economy`, `war`. Drives the kill switches and the concurrency caps. |
| `severity` | string | from the category | `INFO`, `REWARD`, `HAZARD`, `COMBAT` or `WAR` — the colour, symbol and word players see. Defaults: reward → `REWARD`, hazard → `HAZARD`, combat/raid/invasion/boss → `COMBAT`, economy → `INFO`, war → `WAR`. An unknown word falls back to the default. |
| `display` | object | **required** | Three translation keys; see [Display](#display). |
| `weight` | integer | **required** | 0..1000. How often the scheduler picks it relative to the others. `0` = never automatic; only `/neroevents start` (or a Supply Beacon, for the beacon drop) fires it. |
| `cooldown_ticks` | integer | `72000` | ≥ 0. Time before the same definition can fire again. A skipped or cancelled event's cooldown is halved. |
| `announce_ticks` | integer | `6000` | 0..72000. The countdown between the announcement and the start — and the voting window. |
| `duration_ticks` | integer | `12000` | 20..1728000. How long the event runs at most. |
| `enabled` | boolean | `true` | `false` keeps the definition loaded but the scheduler and the Supply Beacon never fire it; an operator can still `/neroevents start` it. |
| `destructive` | boolean | `false` | The event can change blocks. Removed from the pool by `destructiveEventsEnabled=false`. |
| `consent_exempt` | boolean | `false` | Never opens a vote. **A destructive event can never be consent-exempt** (the file is dropped). |
| `calm_exempt` | boolean | `false` | May fire during the calm window of a fresh server (`calmWindowHours`). |
| `location_public` | boolean | `false` | The announcement, console and HUD may show roughly where it is (direction and distance band, never coordinates). Raid and invasion targets are never shown, whatever this says. |
| `conditions` | object | none | When it may fire; see [Conditions](#conditions). |
| `bounds` | object | radius 64 | Where it happens; see [Bounds](#bounds). |
| `payload` | object | `{}` | The payload type's own settings; see [Payload types](#payload-types). |
| `rewards` | object | no rewards | What it pays; see [Rewards](#rewards). |

### Display

| Field | Rules |
| --- | --- |
| `title` | Translation key for the event's name ("Meteor Shower"). Required, not blank. |
| `what_to_do` | Translation key for the one-sentence instruction shown when it starts. Required, not blank. |
| `announce` | Translation key for the announcement. Required, not blank. Its arguments: `%1$s` = the dimension, `%2$s` = minutes until the start, `%3$s` = where (direction and distance for a public location, otherwise a neutral phrase). |

Add the keys to a resource pack (`assets/<namespace>/lang/en_us.json`) so players see the words. The
console and the HUD also look for an optional short line `event.<namespace>.<path with dots>.todo` (for
`neroevents:core/supply_drop` that is `event.neroevents.core.supply_drop.todo`); `%s` in it is the time
left. Without it they use the payload type's own line.

### Conditions

| Field | Type | Default | Rules |
| --- | --- | --- | --- |
| `dimensions` | list of ids | any | Only these dimensions. Cannot be combined with `dimension_tag` (dropped). |
| `dimension_tag` | id | none | Only dimensions whose dimension type is in this tag, for example `neroland:space/dimensions`. |
| `min_online` | integer | `1` | At least this many players online. |
| `max_online` | integer | `-1` (no limit) | At most this many. Must not be below `min_online` (dropped). |
| `time_of_day` | `{ "from": t, "to": t }` | always | Day time 0..24000, `from` inclusive, `to` exclusive; `from > to` wraps midnight (`{ "from": 13000, "to": 1000 }` = night); `from == to` = all day. Out of range → always. |
| `bands` | list of band names | all bands | Only in these population bands (names from `schedulerBands`, by default `0-1`, `2-4`, `5-9`, `10+`). |
| `requires_gate` | id | none | A Neroland Core progression gate that enough online players must have open. |
| `required_gate_holders` | integer | `1` | How many online players need `requires_gate` open (≥ 1). Meaningless without `requires_gate` (warned). |
| `requires_mods` | list of mod ids | none | Every mod must be loaded, or the definition is dropped at load. |
| `allow_empty_dimension` | boolean | `false` | May pick a dimension with nobody in it (for events that need no players nearby, such as market shocks). |

### Bounds

| Field | Type | Default | Rules |
| --- | --- | --- | --- |
| `kind` | string | `radius` | `dimension` — the whole dimension, no anchor. `radius` — an anchor placed near where players are (by per-area player counts, never a player's position), with a circle of `radius`. `target` — the payload chooses the anchor (raids and invasions); the choice is protection-checked like any other. |
| `radius` | integer | `64` | 8..512 blocks. Out of range → 64. |
| `fallback_to_density` | boolean | `true` | For `target`: when the payload finds no target, place it near players instead (otherwise the event is skipped). |

Every anchor is checked against every protection rule before the event is placed; see the wiki page
*Protection and fair play*.

### Rewards

| Field | Type | Default | Rules |
| --- | --- | --- | --- |
| `budget` | integer | `0` | ≥ 0, in value units (1 credit = 1, 1 XP point = `rewardXpValuePerPoint`, an item = its `value` or `rewardItemDefaultValue`). The most this event pays in total; lines worth more are scaled down (warned at load). |
| `split` | string | `contribution` | `contribution` (by what each player did) or `equal`. |
| `min_share_fraction` | number | `0.05` | 0..0.5. The smallest share for anyone who did at least `rewardFloorEligibleRawShare` of the work. |
| `lines` | list | none | The reward lines (below). |
| `top_bonus` | list | none | Extra lines for the top contributors; each needs `"rank": 1` or higher. |
| `pay_on_expiry` | boolean | `false` | Also pay when the event ends because its timer ran out. |
| `decay` | boolean | `true` | Contributions of players who leave the area or log off fade over time. |

Reward lines:

| `kind` | Fields | Notes |
| --- | --- | --- |
| `currency` | `amount` ≥ 0 | Credits through NeroEconomy (skipped without it). |
| `xp` | `amount` ≥ 0 | Experience points. |
| `item` | `item` (id), `count` 1..64, optional `value` ≥ 1 per item | Goes into the reward crate. An item that is not registered is removed at load. **A `boss` event cannot have item lines** (the boss mod pays its own drops; item lines are removed). |
| `reputation` | `faction` (id), `delta` | Needs NeroFactions; clamped by `rewardReputationMaxDeltaPerEvent`. |

The whole server's payouts are also bounded by `rewardBudgetPerDay`. See the wiki page *Rewards*.

## Payload types

Each type below lists its `payload` keys. **Every key is optional unless marked required, and a value
outside its range drops the whole definition** — check the log or `/neroevents preview` after a change.
The example under each type is the definition this mod ships, copied as-is.

### `neroevents:supply_drop` (Neroland Core only)

A supply pod falls where it can see the sky and breaks no blocks; the first player to open it claims
the drop.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `tier` | 1..3 | `1` | The pod's look. Outside 1..3 → 1 (warned). |
| `pod_drop_height` | 32..160 | `80` | Blocks above the landing spot where the pod appears. |
| `descent_blocks_per_tick` | 0.15..0.6 | `0.35` | Falling speed. |
| `drift_blocks_per_tick` | 0.0..0.05 | `0.02` | Sideways drift while falling. |
| `loot_table` | id | `neroevents:pods/supply_tier1` | The pod's contents. A table outside the `neroevents` namespace is not checked at load. |
| `loot_rolls` | 1..6 | `3` | How many times the table is rolled. |
| `pod_loot_budget` | 0..100000 | `300` | Value cap on the pod's contents (clamped). |
| `pod_linger_ticks` | 600..72000 | `6000` | How long an emptied pod stays before the event ends. |
| `unopened_linger_ticks` | 600..72000 | `12000` | How long an unopened pod stays. |

```json
{
  "type": "neroevents:supply_drop",
  "category": "reward",
  "severity": "REWARD",
  "display": {
    "title": "event.neroevents.core.supply_drop.title",
    "what_to_do": "event.neroevents.core.supply_drop.what_to_do",
    "announce": "event.neroevents.core.supply_drop.announce"
  },
  "weight": 10,
  "cooldown_ticks": 72000,
  "announce_ticks": 6000,
  "duration_ticks": 18000,
  "calm_exempt": true,
  "location_public": true,
  "conditions": {
    "min_online": 1
  },
  "bounds": { "kind": "radius", "radius": 96 },
  "payload": { "tier": 1 },
  "rewards": {
    "budget": 400,
    "lines": [
      { "kind": "currency", "amount": 250 },
      { "kind": "xp", "amount": 150 }
    ],
    "top_bonus": [ { "rank": 1, "kind": "currency", "amount": 50 } ]
  }
}
```

The Supply Beacon calls the definition named by `supplyBeaconDefinition` (by default
`neroevents:core/beacon_supply_drop`, a weight-0, consent-exempt, calm-exempt variant of this type).

### `neroevents:meteor_shower` (Neroland Core only)

Meteors fall over the area; each digs a small crater with a crater pocket to break for loot. Mark it
`"destructive": true` — it changes blocks.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `meteor_count` | 1..120 | `24` | Meteors over the whole event. |
| `cadence_jitter` | 0.0..0.5 | `0.25` | Random spread on the time between meteors. |
| `spawn_height` | 32..160 | `96` | Blocks above the ground where a meteor appears. |
| `fall_blocks_per_tick` | 0.8..3.0 | `1.6` | Falling speed. |
| `crater_radius_min` | 1..4 | `2` | Smallest crater. |
| `crater_radius_max` | 2..4 | `4` | Largest crater. If below the minimum, 2 and 4 are used (warned). |
| `max_craters_per_chunk` | 1..8 | `2` | Further impacts in a full chunk land softly. |
| `max_craters_per_event` | 1..64 | `16` | Further impacts land softly. |
| `ore_per_crater` | 0..6 | `2` | Ore blocks set into each crater. |
| `ore_blocks` | list of block ids | `["neroevents:embedded_meteor_ore"]` | Unregistered blocks, and blocks that are not plain solid blocks (fluids, block entities, see-through blocks), are ignored (warned). |
| `pocket_loot_table` | id | `neroevents:craters/pocket` | The crater pocket's loot. |
| `pocket_loot_budget` | 0..100000 | `120` | Value cap per pocket (clamped). |
| `soft_landing_only` | boolean | `false` | `true` = no crater at all, only the pocket. |

Server-wide, `meteorBlockDamage=false` makes every impact a soft landing, and `craterAvoidsBlockEntities`
/ `craterBlockEntityMarginBlocks` turn an impact near any block entity into a soft landing.

```json
{
  "type": "neroevents:meteor_shower",
  "category": "hazard",
  "severity": "HAZARD",
  "display": {
    "title": "event.neroevents.core.meteor_shower.title",
    "what_to_do": "event.neroevents.core.meteor_shower.what_to_do",
    "announce": "event.neroevents.core.meteor_shower.announce"
  },
  "weight": 8,
  "cooldown_ticks": 108000,
  "announce_ticks": 6000,
  "duration_ticks": 12000,
  "destructive": true,
  "conditions": {
    "dimensions": ["minecraft:overworld"],
    "min_online": 1
  },
  "bounds": { "kind": "radius", "radius": 160 },
  "payload": {},
  "rewards": {
    "budget": 300,
    "lines": [
      { "kind": "currency", "amount": 150 },
      { "kind": "xp", "amount": 150 }
    ]
  }
}
```

### `neroevents:debris_crash` (Neroland Core only)

A piece of wreckage crashes and leaves a small wreck with salvage crates. Mark it `"destructive": true`
— it places blocks. Wrecks rust away after `wreckLingerDays`.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `variants` | non-empty list | all four | Wreck layouts to choose from: `wreck_pod_shell`, `wreck_strut_field`, `wreck_cargo_spill`, `wreck_hull_rib` (a bare name means the `neroevents` namespace). Unknown names are ignored (warned); with none left the definition is dropped. Layouts are data: `data/<namespace>/neroevents/wrecks/<name>.json`. |
| `fall_height` | 24..128 | `64` | Blocks above the ground where the wreckage appears. |
| `fall_blocks_per_tick` | 0.8..3.0 | `1.2` | Falling speed. |
| `salvage_loot_table` | id | `neroevents:wrecks/salvage` | The crates' loot. |
| `salvage_loot_budget` | 0..100000 | `200` | Value cap per crate (clamped). |
| `salvage_window_ticks` | 1200..72000 | `12000` | How long the crates can be salvaged. |

```json
{
  "type": "neroevents:debris_crash",
  "category": "hazard",
  "severity": "HAZARD",
  "display": {
    "title": "event.neroevents.core.debris_crash.title",
    "what_to_do": "event.neroevents.core.debris_crash.what_to_do",
    "announce": "event.neroevents.core.debris_crash.announce"
  },
  "weight": 7,
  "cooldown_ticks": 90000,
  "announce_ticks": 4800,
  "duration_ticks": 12000,
  "destructive": true,
  "conditions": {
    "min_online": 1
  },
  "bounds": { "kind": "radius", "radius": 64 },
  "payload": {},
  "rewards": {
    "budget": 300,
    "lines": [
      { "kind": "currency", "amount": 150 },
      { "kind": "xp", "amount": 100 },
      { "kind": "item", "item": "neroevents:salvage_core_fragment", "count": 2 }
    ]
  }
}
```

### `neroevents:solar_storm` and `neroevents:planetary_storm` (need Nerospace)

Storms over a whole dimension (`"bounds": { "kind": "dimension" }`). A solar storm runs only in a
dimension whose type is in `neroland:space/dimensions`; a planetary storm only on Greenxertz (dust),
Cindara (ion) or Glacira (cryo). Storms change no blocks, so they are not destructive; one storm per
dimension at a time.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `intensity` | 1..3 | `2` | Strength of the effects and the drain. |
| `ramp_ticks` | 100..2400 | `600` | How long it takes to build up and die down. |
| `variant` | `auto`, `solar`, `dust`, `ion`, `cryo` | `auto` | Planetary storms: which storm (`auto` = the world's own). Ignored by the solar storm. |
| `effects_enabled` | boolean | `true` | Status effects on exposed players (also off server-wide with `stormEffectsEnabled=false`). |
| `drain_enabled` | boolean | `true` | Energy drain on exposed storages (solar and ion only; tuned by the `stormDrain*` keys). |
| `agriculture_enabled` | boolean | `true` | Slower crop growth with NeroAgriculture. |
| `wind_yaw` | 0..359 | random | Wind direction for the particles. |

```json
{
  "type": "neroevents:planetary_storm",
  "category": "hazard",
  "severity": "HAZARD",
  "display": {
    "title": "event.neroevents.ion_squall.title",
    "what_to_do": "event.neroevents.ion_squall.what_to_do",
    "announce": "event.neroevents.ion_squall.announce"
  },
  "weight": 5,
  "cooldown_ticks": 144000,
  "announce_ticks": 6000,
  "duration_ticks": 7200,
  "destructive": false,
  "location_public": false,
  "conditions": {
    "dimensions": ["nerospace:cindara"],
    "requires_mods": ["nerospace"]
  },
  "bounds": { "kind": "dimension" },
  "payload": { "variant": "ion", "intensity": 2 },
  "rewards": { "budget": 0 }
}
```

```json
{
  "type": "neroevents:solar_storm",
  "category": "hazard",
  "severity": "HAZARD",
  "display": {
    "title": "event.neroevents.solar_storm.title",
    "what_to_do": "event.neroevents.solar_storm.what_to_do",
    "announce": "event.neroevents.solar_storm.announce"
  },
  "weight": 6,
  "cooldown_ticks": 144000,
  "announce_ticks": 6000,
  "duration_ticks": 9600,
  "destructive": false,
  "location_public": false,
  "conditions": {
    "dimension_tag": "neroland:space/dimensions",
    "requires_mods": ["nerospace"]
  },
  "bounds": { "kind": "dimension" },
  "payload": {},
  "rewards": { "budget": 0 }
}
```

### `neroevents:pirate_raid` and `neroevents:alien_invasion` (need NeroCreatures)

Waves of NeroCreatures mobs. Both use the same keys; use `"bounds": { "kind": "target" }` so the payload
picks a populated area. NeroEvents never breaks blocks for a raid; the creatures follow the server's
normal mob rules (`mobGriefing`, claim mob protection).

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `waves` | **required**, 1..8 waves | — | Each wave: `mobs` (1..16 entries of `{ "type": <entity id>, "count": 1..32 }`), optional `delay_ticks` 0..6000 (default 0) before it starts, optional `timeout_ticks` 600..12000 (default 4800). Unregistered mob types are ignored (warned). |
| `scale_per_online` | 0.0..1.0 | `0.25` | Extra mobs per online player, as a share of the wave. |
| `max_per_wave` | 1..64 | `24` | Cap on one wave after scaling. |
| `spawn_radius_min` | 8..64 | `16` | Closest spawn distance from the anchor. |
| `spawn_radius_max` | 8..64 | `28` | Furthest spawn distance (swapped with the minimum if lower, warned). |
| `spawn_per_tick` | 1..8 | `2` | Mobs spawned per tick while a wave arrives. |
| `success_kill_fraction` | 0.0..1.0 | `0.5` | Share of the mobs that must fall for the event to succeed. |
| `survival_credit` | boolean | `true` | Surviving a wave counts as participation (only after helping in another way). |
| `block_damage` | boolean | `false` | Reserved: mobs never break blocks in this version (`true` is warned and has no effect). |

```json
{
  "type": "neroevents:pirate_raid",
  "category": "raid",
  "severity": "COMBAT",
  "display": {
    "title": "event.neroevents.pirate_raid.title",
    "what_to_do": "event.neroevents.pirate_raid.what_to_do",
    "announce": "event.neroevents.pirate_raid.announce"
  },
  "weight": 6,
  "cooldown_ticks": 108000,
  "announce_ticks": 6000,
  "duration_ticks": 24000,
  "enabled": true,
  "destructive": false,
  "consent_exempt": false,
  "calm_exempt": false,
  "location_public": false,
  "conditions": {
    "min_online": 2,
    "requires_mods": ["nerocreatures"]
  },
  "bounds": { "kind": "target", "radius": 64, "fallback_to_density": true },
  "payload": {
    "waves": [
      {
        "mobs": [
          { "type": "nerocreatures:space_pirate", "count": 4 },
          { "type": "nerocreatures:rogue_drone", "count": 2 }
        ]
      },
      {
        "mobs": [
          { "type": "nerocreatures:space_pirate", "count": 5 },
          { "type": "nerocreatures:rogue_drone", "count": 3 }
        ],
        "delay_ticks": 600
      },
      {
        "mobs": [
          { "type": "nerocreatures:space_pirate", "count": 4 },
          { "type": "nerocreatures:rogue_android", "count": 2 },
          { "type": "nerocreatures:rogue_drone", "count": 2 }
        ],
        "delay_ticks": 900
      }
    ]
  },
  "rewards": {
    "budget": 600,
    "lines": [
      { "kind": "currency", "amount": 350 },
      { "kind": "xp", "amount": 250 }
    ]
  }
}
```

The shipped `neroevents:creatures/alien_invasion` has the same shape with `"type":
"neroevents:alien_invasion"`, `"category": "invasion"`, four waves and
`"dimension_tag": "neroland:space/dimensions"`.

### `neroevents:boss` (needs NeroCreatures)

A world boss lands in an arena; everyone who deals damage shares the reward. Use `"category": "boss"`
(at most `maxConcurrentBossEvents`, never two in one dimension). No item reward lines.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `boss_id` | entity id | `nerocreatures:cinder_tyrant` | The boss to summon. |
| `arena_radius` | 8..48 | `24` | The fight area around the landing spot. |

```json
{
  "type": "neroevents:boss",
  "category": "boss",
  "severity": "COMBAT",
  "display": {
    "title": "event.neroevents.cinder_tyrant.title",
    "what_to_do": "event.neroevents.cinder_tyrant.what_to_do",
    "announce": "event.neroevents.cinder_tyrant.announce"
  },
  "weight": 3,
  "cooldown_ticks": 216000,
  "announce_ticks": 12000,
  "duration_ticks": 36000,
  "enabled": true,
  "destructive": false,
  "consent_exempt": false,
  "calm_exempt": false,
  "location_public": true,
  "conditions": {
    "min_online": 2,
    "dimension_tag": "neroland:space/dimensions",
    "requires_mods": ["nerocreatures"]
  },
  "bounds": { "kind": "radius", "radius": 64 },
  "payload": { "boss_id": "nerocreatures:cinder_tyrant" },
  "rewards": {
    "budget": 1200,
    "lines": [
      { "kind": "currency", "amount": 800 },
      { "kind": "xp", "amount": 400 }
    ]
  }
}
```

### `neroevents:market_shock` (needs NeroEconomy)

Asks NeroEconomy for a temporary price change on one good, picked from the list. Changes nothing in the
world; use `"bounds": { "kind": "dimension" }` and `"allow_empty_dimension": true`.

| Key | Range | What it does |
| --- | --- | --- |
| `shocks` | **required**, 1..16 entries | The possible shocks; one is picked per event. |
| `shocks[].key` | **required** | An item id (`nerolandcore:plasma_glass`) or an item tag (`#c:ingots/iron`). Anything else is ignored (warned); with no usable entry every instance is skipped. |
| `shocks[].multiplier` | **required**, 0.25..4.0 | The price factor. NeroEconomy clamps it to its own price band. |
| `shocks[].minutes` | **required**, 1..1440 | How long the price stays moved. |
| `shocks[].narrative` | **required** | Translation key naming the good in the forecast line ("an iron shortage"). |

```json
{
  "type": "neroevents:market_shock",
  "category": "economy",
  "severity": "INFO",
  "display": {
    "title": "event.neroevents.market_shock.title",
    "what_to_do": "event.neroevents.market_shock.what_to_do",
    "announce": "event.neroevents.market_shock.announce"
  },
  "weight": 6,
  "cooldown_ticks": 144000,
  "announce_ticks": 6000,
  "duration_ticks": 54000,
  "destructive": false,
  "consent_exempt": false,
  "location_public": false,
  "conditions": {
    "dimensions": ["minecraft:overworld"],
    "allow_empty_dimension": true,
    "requires_mods": ["neroeconomy"]
  },
  "bounds": { "kind": "dimension" },
  "payload": {
    "shocks": [
      { "key": "#c:ingots/iron", "multiplier": 1.8, "minutes": 45, "narrative": "event.neroevents.market_shock.shortage.iron" },
      { "key": "#c:ingots/copper", "multiplier": 0.5, "minutes": 45, "narrative": "event.neroevents.market_shock.glut.copper" },
      { "key": "nerolandcore:nero_alloy_ingot", "multiplier": 2.0, "minutes": 30, "narrative": "event.neroevents.market_shock.shortage.nero_alloy" },
      { "key": "#c:dusts/redstone", "multiplier": 1.6, "minutes": 60, "narrative": "event.neroevents.market_shock.shortage.redstone" },
      { "key": "nerolandcore:plasma_glass", "multiplier": 0.6, "minutes": 40, "narrative": "event.neroevents.market_shock.glut.plasma_glass" },
      { "key": "#c:gems/diamond", "multiplier": 1.4, "minutes": 90, "narrative": "event.neroevents.market_shock.demand.diamond" }
    ]
  },
  "rewards": { "budget": 0, "lines": [] }
}
```

### `neroevents:faction_war` (needs NeroFactions)

The two factions with the most members online (each needs `warMinMembersOnline`) contest a ring of war
beacons. Use `"category": "war"`.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `objective_count` | 1..5 | `3` | War beacons placed. |
| `objective_spread` | 16..96 | `48` | Distance of the beacons from the centre. |
| `capture_radius` | 3..12 | `6` | How close you must stand to capture. |
| `capture_ticks` | 200..6000 | `600` | Time to capture an uncontested beacon. |
| `capture_points` | 1..100 | `10` | Points per capture. |
| `hold_points_per_100_ticks` | 0..20 | `1` | Points per held beacon every 5 seconds. |
| `win_score` | 0..10000 | `0` | Score that ends the war early (`0` = run the full time). |
| `reputation_win` | 0..100 | `10` | Reputation gained by the winning side's fighters. |
| `reputation_loss` | 0..100 | `5` | Reputation lost by the losing side's fighters. |

```json
{
  "type": "neroevents:faction_war",
  "category": "war",
  "severity": "WAR",
  "display": {
    "title": "event.neroevents.faction_war.title",
    "what_to_do": "event.neroevents.faction_war.what_to_do",
    "announce": "event.neroevents.faction_war.announce"
  },
  "weight": 4,
  "cooldown_ticks": 216000,
  "announce_ticks": 12000,
  "duration_ticks": 24000,
  "destructive": false,
  "consent_exempt": false,
  "location_public": true,
  "conditions": {
    "min_online": 4,
    "requires_mods": ["nerofactions"]
  },
  "bounds": { "kind": "radius", "radius": 96 },
  "payload": {
    "objective_count": 3,
    "objective_spread": 48,
    "capture_radius": 6,
    "capture_ticks": 600,
    "capture_points": 10,
    "hold_points_per_100_ticks": 1,
    "win_score": 0,
    "reputation_win": 10,
    "reputation_loss": 5
  },
  "rewards": {
    "budget": 800,
    "lines": [
      { "kind": "currency", "amount": 500 },
      { "kind": "xp", "amount": 300 }
    ]
  }
}
```

### `neroevents:debug_marker` (Neroland Core only, operators)

A test event that places nothing and changes nothing: it only runs through the countdown, the start and
the end. The shipped one is disabled for automatic use; start it with
`/neroevents start neroevents:debug_marker`.

| Key | Range | Default | What it does |
| --- | --- | --- | --- |
| `ticks_to_resolve` | 1..1728000 | none (runs to `duration_ticks`) | Ends the event successfully after this many ticks. |

```json
{
  "type": "neroevents:debug_marker",
  "category": "hazard",
  "severity": "INFO",
  "display": {
    "title": "event.neroevents.debug_marker.title",
    "what_to_do": "event.neroevents.debug_marker.what_to_do",
    "announce": "event.neroevents.debug_marker.announce"
  },
  "weight": 0,
  "cooldown_ticks": 0,
  "announce_ticks": 200,
  "duration_ticks": 600,
  "enabled": false,
  "destructive": false,
  "consent_exempt": false,
  "calm_exempt": true,
  "location_public": true,
  "conditions": {
    "allow_empty_dimension": true
  },
  "bounds": { "kind": "radius", "radius": 16 },
  "payload": { "ticks_to_resolve": 400 }
}
```

## Checking your work

1. `/reload`, then `/neroevents list` — your id appears, and the skipped count tells you whether any
   file was dropped.
2. `/neroevents preview <definition>` — every scheduling rule with a tick or a cross, the dimensions it
   could use, a sample anchor (or which protection refused one), whether it would open a vote, the
   reward budget and value, and every load warning for it.
3. `/neroevents start <definition>` in a test world — the full lifecycle, still under every kill switch
   and protection rule.
