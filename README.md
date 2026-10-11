# NeroEvents

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** barebones multiloader skeleton — version `0.3.0-alpha.1`. No gameplay content yet; optional NeroEconomy seams are in place.

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
| `EventEconomy` | NeroEconomy (optional) | `chargeEntry(player, fee, key)` charges an entry fee (`event_entry`); `payReward(player, amount, key)` pays a budgeted reward (`event_reward`). |

- NeroEconomy is **optional**. It is compiled `compileOnly` (never bundled) and declared optional in
  every loader manifest. One bridge class, `economy/NeroEconomyBridge`, is the only code that imports
  `za.co.neroland.neroeconomy`, and it is loaded only after `isModLoaded("neroeconomy")` and an API
  version check (`SiblingEconomy.API_VERSION >= 1`, read reflectively). The handle is claimed once from
  common init.
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

## Privacy & telemetry

> **Telemetry notice:** NeroEvents sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/neroevents.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

