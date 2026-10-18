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

## Core APIs NeroEvents uses

Every `za.co.neroland.nerolandcore` import in the main source (`common/`, plus `RegistrationProvider` in the
NeoForge and Forge entry points, which attach Core's deferred registers to the mod bus):

| Core API | Used by | For |
| --- | --- | --- |
| `config.ConfigSchema`, `ConfigManager`, `ConfigValue` | `config/NeroEventsConfig` | `config/neroevents.properties` (key prefixes, server-authoritative flags, hot reload) |
| `event.CoreEvents.onProgression`, `progression.GateScope` | `progression/QuestHooks` | hearing the `neroevents:hook/supply_beacon` hook gate |
| `progression.ProgressionGates` (`open` / `close` / `isOpen`, offline-capable) | `progression/EventMilestones`, `progression/QuestHooks`, `event/world/ServerWorldView` | personal record gates, the hook re-arm, and counting online gate holders for `conditions.requires_gate` |
| `event.ThresholdEvents.fire` (with `neroevents` as the source) | `progression/EventMilestones`, `economy/MarketShockRequests`, `storm/StormChannel`, `war/WarChannel` | the world channels `neroevents:event_resolved`, `boss_defeated`, `storm` and `war_resolved`, and market-shock requests on `neroevents:market_shock_request`; scopes are event types, places and factions, never players |
| `event.ThresholdEvents.onCrossing` | `economy/EconomySignals`, `compat/creatures/CreaturesCompat` | NeroEconomy's world signals, and NeroCreatures' falling `boss_pressure` crossing (a boss fell in a dimension) |
| `data.PlayerDataErasure`, `data.PlayerDataEraser` | `data/NeroEventsData` | the shared per-player erasure hook (registered early in common init) |
| `data.SavedDataRecovery` (`get`, `backupNow`) | `event/persist/EventEngineState`, `history/EventHistoryState`, `reward/RewardLedgerState`, `reward/RewardCrateState`, `privacy/PrivacyOptOuts`, `payload/debris/WreckState` | crash-safe loading plus a last-known-good backup for every NeroEvents `SavedData`; `backupNow` after erasing a player |
| `registry.RegistrationProvider` (+ `RegistryEntry`) | `item/EventItems`, `console/ConsoleBlocks`, `payload/registry/CorePayloadBlocks`, `CorePayloadBlockEntities`, `CorePayloadEntities`, `CorePayloadItems`, `fx/EventParticles`, `storm/StormParticles`, `war/WarBlocks` | blocks, block entities, entities, items and particle types on every loader |
| `registry.CoreCreativeTab` | `console/ConsoleBlocks`, `payload/registry/CorePayloadItems`, `war/WarBlocks` | the Event Console, the event blocks and items and the war beacon in Neroland Core's creative tab |
| `registry.BlockCodecs.simple` | `console/EventConsoleBlock`, `payload/meteor/CraterPocketBlock`, `payload/debris/SalvageCrateBlock`, `payload/debris/WreckPaletteBlocks`, `war/WarBeaconBlock` | the block `MapCodec`s |
| `platform.EnergyLookup`, `energy.NeroEnergyStorage` | `storm/StormEnergyDrain` | finding and draining energy storages during solar storms and ion squalls (Neroland storages on Fabric; standard energy too on NeoForge and Forge) |
| `reputation.ReputationApi` (`hasRealProvider`, `adjust`) | `war/FactionWarPayload`, `reward/ServerRewardSinks` | faction-war reputation and `reputation` reward lines, only when a real provider (NeroFactions) is present |

The tests also use Core's `data.ErasureConformance` harness (`ErasureConformanceTest`) to prove that
every player-keyed store is erased.

NeroEvents does **not** use Core's network registry: it has its own `neroevents:main` channel behind
`platform/NetworkPlatform`, because Core seals its own payload lists during Core's bootstrap.
