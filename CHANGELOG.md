# Changelog

All notable changes to **NeroEvents** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-alpha.1] - 2026-10-11

NeroQuests quest pack and the progression seams it reads. Requires Neroland Core 1.15.0. Still no
blocks, items or events: the chapter's quests become completable as event content lands.

### Added

- **NeroQuests quest pack** — chapter `neroevents:event_log` "Event Log", 7 quests (4 personal, 3 server
  "world log") — that appears in the quest book when NeroQuests is installed; inert otherwise, no
  dependency in any form. Nothing opens its gates or fires its channels yet, so every quest stays open
  until the event scheduler, participation tracking and supply drops ship. `supply_run` reads the
  `supply_claimed` record for now and moves to `collect_item` once a drop item exists.
- **Record gates** (player scope, no `requires`, never checked for access):
  `neroevents:first_participation`, `supply_claimed`, `raid_repelled`, `boss_contributor`, and the hook
  gate `neroevents:hook/supply_beacon`.
- `progression/EventMilestones` — one `record...(server, uuid)` per gate (offline-capable) and one
  `fire...()` per world channel: `neroevents:event_resolved` (scope = event type id, value = world
  resolved count, threshold = rung) and `neroevents:boss_defeated` (scope = boss event type id, falling).
  Scopes are event types, never participants, positions or regions. The future resolve step calls these.
- `progression/QuestHooks` — listens on Core's progression events for `hook/supply_beacon`, wired once
  from common init with login delivery from the server tick on every loader. The Supply Beacon item does
  not exist yet, so the current effect is a chat notice (`message.neroevents.supply_beacon_pending`,
  once per login) and the gate is left open as a pending claim; `setSupplyBeaconDelivery` installs the
  real hand-over later, which closes the gate to re-arm it.
- Wiki page *NeroQuests integration*.

### Changed

- Neroland Core floor raised to **1.15.0** (offline-capable `ProgressionGates.open/close/isOpen` and
  sourced `ThresholdEvents.fire`). `MarketShockRequests` now fires with `neroevents` as its source.
- Privacy: the new record gates are player-scope rows in Core's progression store (covered by Core's
  PRIVACY.md and its shared erasure hook). `QuestHooks` keeps two process-local UUID sets for login
  detection and the once-per-login notice; nothing is logged or broadcast.

## [0.3.0-alpha.1] - 2026-10-11

Optional NeroEconomy seams. No blocks, items or recipes yet.

### Added

- **NeroEconomy seams** (NeroEconomy stays optional; NeroEvents builds, loads and runs without it):
  - `economy/MarketShockRequests` asks NeroEconomy for a temporary price multiplier on an item or tag
    through Neroland Core's threshold channel `neroevents:market_shock_request`, validating the request
    first. Needs only Core.
  - `economy/EconomySignals` hears NeroEconomy's world signals (sector index, market shocks, Reserve,
    House stock) and hands them to hooks the event system can register. Needs only Core; does nothing
    until a hook exists.
  - `economy/EventEconomy` charges event entry fees (`event_entry`) and pays budgeted event rewards
    (`event_reward`), returning an outcome with a player-facing message.
- Wiki page *NeroEconomy integration*.

### Changed

- NeroEconomy is declared as an optional dependency on every loader (Fabric `suggests`, NeoForge
  `optional`, Forge `mandatory = false`, floor `0.3.0-beta.1`) and compiled `compileOnly`.
- Neroland Core (floor 1.13.0) is now a required dependency, and NeroEvents ships anonymous, opt-out
  crash reporting (see PRIVACY.md).

## [0.2.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** NeroEvents adds no recipes of its own, so nothing needed a plugin. The build now
  compiles against the community EMI Unofficial Port (Unstable), the only EMI build for Minecraft 26.x,
  and dev clients load it with `-PwithEmi` (default runs stay JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

