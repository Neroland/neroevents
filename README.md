# NeroEvents

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** early alpha — version `0.4.0-alpha.1`. No gameplay content yet; the foundation (Neroland Core wiring, own network channel, erasure hook, menu guard, telemetry, unit tests), the optional NeroEconomy seams and the NeroQuests quest pack are in place. See [USING-CORE.md](USING-CORE.md) for the Core APIs used.

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
- Mod id: `neroevents` · package `za.co.neroland.neroevents`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

## Developer notes: NeroEconomy seams

NeroEvents has no events yet; these are the ready-to-use seams the event system builds on. They live in
`common/src/main/java/za/co/neroland/neroevents/economy/`.

| Class | Needs | What it does |
| --- | --- | --- |
| `MarketShockRequests` | Core only | `apply(scope, multiplier, minutes)` / `end(scope)` ask NeroEconomy for a temporary price multiplier on an item id or `#tag`, through Core `ThresholdEvents` channel `neroevents:market_shock_request` (value = multiplier × 1000, threshold = economy-minutes, rising = apply/end). Validated before sending; NeroEconomy clamps it to its price band and `shockMaxMinutes`, and only hears mods in its `shockSources` config (default `neroevents`). |
| `EconomySignals` | Core only | Listens for NeroEconomy's world channels (`sector_index`, `market_shock`, `reserve`, `house_stock`) and hands each to hooks registered with `addHook`. No hook is registered yet, so a signal does nothing. |
| `EventsEconomy` | NeroEconomy (optional) | `chargeEntry(player, fee, key)` charges an entry fee (`event_entry`); `payReward(player, amount, key)` pays a budgeted reward (`event_reward`). |

- NeroEconomy is **optional**. It is compiled `compileOnly` (never bundled) and declared optional in
  every loader manifest. One bridge class, `economy/NeroEconomyBridge`, is the only code that imports
  `za.co.neroland.neroeconomy`, and it is loaded only after `isModLoaded("neroeconomy")`; the manifest
  floor (0.4.0-beta.1) guarantees the API, and a link failure leaves the seams off instead of crashing.
  The handle is claimed once from common init.
- Every call returns an `EconomyOutcome`: `APPLIED` (grant), `REFUSED` (nothing moved; show
  `outcome.message()`), or `ABSENT` (NeroEconomy is not installed; use the fallback).
- Request keys name the thing being paid for, never a player. A key holding a UUID or 32 hex digits is
  refused before it is sent. Nothing logs who paid.
- Dev clients load NeroEconomy only with `-PwithNeroEconomy` (NeoForge and Fabric), so the default run
  proves the mod works without it.
- Rewards are budgeted per mod and per player per day; plan events so a day that runs out of budget is
  acceptable. With NeroEconomy absent, run events free and without credit rewards.
- Shock requests and signals name goods, sectors or places, never a player. All of this runs on the server
  thread.

## Developer notes: NeroQuests quest pack

NeroEvents ships a NeroQuests quest pack — chapter `neroevents:event_log` *Event Log*, 7 quests — as plain
data under `common/src/main/resources/data/neroevents/neroquests/`. NeroQuests is **optional**: the pack
appears in the quest book when NeroQuests is installed and is inert otherwise (no dependency of any kind).
Nothing in this build opens the pack's gates or fires its channels, so its quests become completable as
event content lands. The seams it reads live in `common/src/main/java/za/co/neroland/neroevents/progression/`:

| Class | What it does |
| --- | --- |
| `EventMilestones` | One `record...(server, uuid)` per record gate (`first_participation`, `supply_claimed`, `raid_repelled`, `boss_contributor`; offline-capable) and one `fire...()` per world channel (`event_resolved`, `boss_defeated`). Channel scope is an event type id, never a participant, position or region. |
| `QuestHooks` | Hears the `neroevents:hook/supply_beacon` hook gate that NeroQuests opens when a player completes *Supply Run*. The Supply Beacon item (`neroevents:supply_beacon`) is handed over by `item/SupplyBeaconDelivery`, installed with `setSupplyBeaconDelivery` at init; a full inventory leaves the gate open for the next login. |

Record and hook gates have no `requires` and are never checked for access. See
[wiki/NeroQuests-Integration.md](wiki/NeroQuests-Integration.md).

## Privacy & telemetry

> **Telemetry notice:** NeroEvents sends anonymous error reports (stack trace, mod/game
> versions and the list of loaded mod ids — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/neroevents.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

