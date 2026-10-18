# NeroEvents

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** beta, building towards `0.5.0-beta.1` (the version in `gradle.properties` is still
`0.4.0-alpha.1` until release). Player documentation is in the [wiki](wiki/Home.md); server owners who
want to write their own events should read [EVENT-DEFINITIONS.md](EVENT-DEFINITIONS.md).

NeroEvents is a server-wide event engine: supply drops, meteor showers, wrecks, storms, raids,
invasions, a world boss, market shocks and faction wars are announced, counted down, optionally voted
on, run, rewarded and cleaned up — all from data-pack definitions, under one set of protection rules
and kill switches.

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
- Mod id: `neroevents` · package `za.co.neroland.neroevents`
- Required: Neroland Core (see [USING-CORE.md](USING-CORE.md)). Everything else is optional.

## What the source does

All gameplay code is in `common/src/main/java/za/co/neroland/neroevents/`; the loader modules only wire
it to each loader's events, network and registries.

| Package | What it does |
| --- | --- |
| `event/` | The engine. `EventDefinitions` loads `data/<ns>/neroevents/events/*.json` (lenient: bad files are skipped or reset with a warning), `EventScheduler` rolls a weighted pick every `schedulerIntervalTicks` under population bands, cooldowns, the calm window, caps and consent mode, and `EventEngine` drives every instance through scheduled → announced → voting → active → resolved → cleaned up, saving resumable records (`event/persist`). `event/anchor` places events from per-area player counts, never a position. |
| `protect/` | `ProtectionQuery` over ordered `ProtectionSource`s — exclusion zones, spawn, the station dimension, the protected-namespace scan and the NeroSecurity seam, plus the compat sources. Deny wins; there is no allow. The only path to any destructive effect. |
| `participation/`, `reward/` | Contribution tracking by real online players (damage, loot opened, objectives, wave survival) with decay; the budgeted, contribution-weighted, whole-number reward split; the daily budget; the per-player reward crate; the short reward ledger. |
| `history/` | The aggregate event history (counts and totals only) and the daily budget counter. |
| `privacy/`, `data/` | Opt-out, export, retention purge and the composite eraser registered with Core's erasure hook. |
| `payload/` | The Core-only events: `supply/` (supply pod), `meteor/` (meteors, craters, crater pockets), `debris/` (wrecks from data layouts, salvage crates, wreck decay), plus the shared loot budget and their blocks, entities and items (`payload/registry`). |
| `storm/` | Solar and planetary storms (exposure, status effects, energy drain, client sky/fog/particles/sound). Needs Nerospace. |
| `creatures/` | Pirate raids, alien invasions (waves) and the world boss. Needs NeroCreatures. |
| `economy/` | Market shocks and the NeroEconomy seams (`MarketShockRequests`, `EconomySignals`, `EventsEconomy`). |
| `war/` | Faction wars and the war beacon block. Needs NeroFactions. |
| `compat/` | One façade per optional sibling (`<X>Compat`) and one bridge class that imports it, loaded only after `isModLoaded` and only when the build flag `<x>_compat` is on. Also NeroColonies and Open Parties and Claims protection. |
| `console/`, `client/` | The Event Console block and its five-tab screen, the event HUD and its settings screen, effects, renderers, the gallery capture and the renderer budget command. |
| `command/` | `/neroevents`, built from one `EventCommands.SYNTAX` list that the wiki's Commands page must match (tested). |
| `progression/`, `item/` | The NeroQuests contract: record gates, world channels, the `hook/supply_beacon` hook and the Supply Beacon item. |
| `network/`, `platform/`, `menu/`, `telemetry/` | The mod's own `neroevents:main` channel behind a per-loader seam, the single guarded GUI opener, and opt-out Sentry error reporting with scrubbing. |

Data lives in `common/src/main/resources/data/neroevents/`: the shipped event definitions
(`neroevents/events/`), wreck layouts (`neroevents/wrecks/`), loot tables, block tags, the NeroQuests
quest pack (`neroquests/`) and its progression gates (`neroland_gates/`). There is no datagen; every
resource is hand-authored.

## Optional siblings

Each sibling integration is compiled `compileOnly` behind its `compat/` façade, chosen at build time by
`<x>_compat` in `gradle.properties` and detected at runtime. In this build:

| Sibling | Adds | Build flag |
| --- | --- | --- |
| NeroEconomy | credit rewards, market shocks | always on |
| NeroColonies | colony protection | `nerocolonies_compat=true` |
| Open Parties and Claims | claim protection | `opac_compat=true` |
| NeroFactions | faction wars, reputation rewards | `nerofactions_compat=false` (until NeroFactions is published) |
| Nerospace | solar and planetary storms | `nerospace_compat=false` (artifact not resolvable yet) |
| NeroCreatures | raids, invasions, the world boss | `nerocreatures_compat=false` (artifact not resolvable yet) |
| NeroAgriculture | slower crops during storms | `neroagriculture_compat=false` (artifact not resolvable yet) |
| NeroQuests | the *Event Log* chapter | none (plain data) |
| NeroLink | the companion-app module | not built yet — see [USING-NEROLINK.md](USING-NEROLINK.md) |

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node; plain-JVM JUnit 5 tests in
  `common/src/test/java`, run by the NeoForge cell builds
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert
- `wiki/` — the player wiki, published to the GitHub wiki by CI
- `tools/` — the asset generator, the lang merger and the build MCP helper

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
./gradlew :fabric:26.2:ecjCheck        # static analysis
```

Neroland Core (and any sibling whose flag is on) resolves from `mavenLocal()` or GitHub Packages; see
[USING-CORE.md](USING-CORE.md) for credentials.

## Privacy & telemetry

> **Telemetry notice:** NeroEvents sends anonymous error reports (stack trace, mod/game
> versions and the list of loaded mod ids — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/neroevents.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/neroevents/blob/main/PRIVACY.md).

Player data (a 7-day reward history, a 14-day reward crate, the opt-out list) is described in
[PRIVACY.md](PRIVACY.md), with export, opt-out and erasure.

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.
