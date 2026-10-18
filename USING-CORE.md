# Using Neroland Core in NeroEvents

Neroland Core is NeroEvents' only hard dependency. It ships as its own mod, loads before NeroEvents,
and is never bundled into the NeroEvents jar.

## How the build resolves Core

- The version is pinned once, as `nerolandcore_version` in `gradle.properties` (currently `1.15.0`).
- Each loader node depends on `za.co.neroland.nerolandcore:nerolandcore-<loader>-<mc>:<version>`.
- `stonecutter.gradle` resolves it from `mavenLocal()` first (a Core you published locally with Core's
  `publishToMavenLocal`), then from Core's GitHub Packages Maven
  (`https://maven.pkg.github.com/Neroland/neroland-core`).
- GitHub Packages needs credentials even for reads: set the Gradle properties `gpr.user` / `gpr.key`
  (a token with `read:packages`), or the environment variables `GITHUB_ACTOR` / `GITHUB_TOKEN`. CI sets
  the environment variables from the workflow token. A build that finds Core in `mavenLocal()` needs no
  token.

## Version floor

The loader manifests require at least the Core version NeroEvents was compiled against, so a too-old
Core fails with a clear loader message instead of a `NoSuchMethodError` in game:

- NeoForge / Forge: `nerolandcore_version_range = "[${nerolandcoreVersion},2.0)"` in the loader's
  `build.gradle`, expanded into `neoforge.mods.toml` / `mods.toml`.
- Fabric: `"nerolandcore": ">=${nerolandcore_version} <2.0.0"` in `fabric.mod.json`.

Bump `nerolandcore_version` and the floor follows automatically.

## Core APIs NeroEvents uses today

| Core API | Used by | For |
| --- | --- | --- |
| `config.ConfigSchema`, `ConfigManager`, `ConfigValue` | `config/NeroEventsConfig` | `config/neroevents.properties` (key prefixes, server-authoritative flags, hot reload) |
| `event.CoreEvents.onProgression`, `progression.GateScope` | `progression/QuestHooks` | hearing the `neroevents:hook/supply_beacon` hook gate |
| `progression.ProgressionGates` (`open` / `close` / `isOpen`, offline-capable) | `progression/EventMilestones`, `progression/QuestHooks`, `event/world/ServerWorldView` | personal record gates, the hook re-arm, and counting online gate holders for `conditions.requires_gate` |
| `event.ThresholdEvents` (`fire` with a source, `onCrossing`) | `progression/EventMilestones`, `economy/MarketShockRequests`, `economy/EconomySignals` | world channels, market-shock requests, NeroEconomy's world signals |
| `data.PlayerDataErasure`, `data.PlayerDataEraser` | `data/NeroEventsData` | the shared per-player erasure hook (registered early in common init) |
| `data.SavedDataRecovery` | `event/persist/EventEngineState`, `payload/debris/WreckState` and every other NeroEvents `SavedData` | crash-safe loading plus a last-known-good backup; call `backupNow` after erasing a player |
| `registry.RegistrationProvider`, `registry.CoreCreativeTab` | `item/EventItems`, `payload/registry/*`, `storm/StormParticles`, `war/WarBlocks` | blocks, block entities, entities, items and particle types on every loader; the creative-tab entries |
| `platform.EnergyLookup`, `energy.NeroEnergyStorage` | `storm/StormEnergyDrain` | finding and draining energy storages during solar storms and ion squalls (Nero storages on Fabric; standard energy too on NeoForge and Forge) |
| `reputation.ReputationApi` (`hasRealProvider`, `adjust`) | `war/FactionWarPayload` | faction-war reputation, only when a real provider (NeroFactions) is present |
| `event.ThresholdEvents.fire` (channels `neroevents:storm`, `neroevents:war_resolved`) | `storm/StormChannel`, `war/WarChannel` | storm start/end per dimension and war resolution per faction; scopes are places and factions, never players |

NeroEvents does **not** use Core's network registry: it has its own `neroevents:main` channel behind
`platform/NetworkPlatform`, because Core seals its own payload lists during Core's bootstrap.
