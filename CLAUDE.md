# Project context for AI coding agents — neroevents

> This and `CLAUDE.md` are kept byte-identical; update both together.

## The mod

- **NeroEvents** — server events for the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland
  Core**. Current state: **0.5.0-beta.1 feature-complete except the NeroLink module, pending runtime
  verification** (`mod_version` is still `0.4.0-alpha.1` until release). In: the event engine
  (datapack definitions documented in `EVENT-DEFINITIONS.md`, payload registry, scheduler, lifecycle,
  resumable records, anchor picker, protection contract, `/neroevents` commands); participation, the
  budgeted reward split, reward crate, reward history and aggregate history, consent voting, the privacy
  commands and the Supply Beacon; the Core-only payloads (supply drop, meteor shower, debris crash) and
  the sibling payloads behind `compat/` façades — storms (Nerospace, + NeroAgriculture growth modifier),
  pirate raid / alien invasion / boss (NeroCreatures), market shock (NeroEconomy), faction war with the
  `war_beacon` block (NeroFactions) — plus NeroColonies and Open Parties and Claims protection; the Event
  Console, the HUD and its settings screen, effects and sounds; the full wiki. Each sibling integration
  is a build flag (`<x>_compat` in `gradle.properties`); Nerospace, NeroCreatures, NeroAgriculture and
  NeroFactions default to off in this build. **Not built:** the NeroLink link module and push
  (`USING-NEROLINK.md` describes the planned contract).
- Mod id: **`neroevents`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.neroevents`. Author: **Neroland**.
- Version: **0.4.0-alpha.1** (`mod_version` in `gradle.properties`; only bump it as part of a release).
- Targets **MC 26.1.2, 26.2 AND 26.3** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"9 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).
- Neroland Core is the only hard dependency (pinned `nerolandcore_version`, currently 1.15.0). See
  `USING-CORE.md` for how it resolves and which Core APIs are used. NeroEconomy and NeroQuests are
  optional and never required.

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.
- **This repository is public.** Never reference private planning documents or private repositories
  in code, docs, the wiki or commit messages.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  (`:fabric:26.1.2 :fabric:26.2 :fabric:26.3 :neoforge:26.1.2 :neoforge:26.2 :neoforge:26.3
  :forge:26.1.2 :forge:26.2 :forge:26.3`). `common` is NOT a node — its source is spliced via
  `rootProject.ext.commonJava` / `commonResources`. Dependency pins live in `gradle.properties` as
  `*_version_<mc>` keys; `mc_versions=26.1.2,26.2,26.3`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter
  (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader
  `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block.
  Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is
  merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Build & verify

- **Always build the per-node tasks, never a plain `build`** (the vestigial root loader modules fail).
  All nine cells plus the analyzer:
  `:neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build :forge:26.1.2:build
  :forge:26.2:build :forge:26.3:build :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build
  :fabric:26.2:ecjCheck`.
- `ecjCheck` runs the Eclipse analyzer (the VS Code Problems panel, via `tools/ecj.prefs`); it only
  FAILS on errors.
- **Unit tests:** plain-JVM JUnit 5 tests live in `common/src/test/java` and are wired into the
  **neoforge nodes** (`addModdingDependenciesTo sourceSets.test` gives them the game classpath), so
  every `:neoforge:<mc>:build` runs them; run one cell alone with `:neoforge:<mc>:test`. No game
  bootstrap: test logic and constants, not live worlds.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Numbered common init.** `NeroEventsCommon.init()` runs, in order: 0 platform seams
  (`Services.init()`), 1 config, 2 telemetry, 3 content registries (`EventItems` through Core's
  `RegistrationProvider`, `StormParticles`, then `EventPayloads` with the Core-only blocks/entities/types,
  the `WreckDecay` scheduler-pass hook, then `WarBlocks`), 4 player-data erasure
  (`NeroEventsData.init()`, then the
  store erasers in order: reward ledger, reward crates, privacy opt-outs, exports, `live_instances`),
  5 compat detection (NeroEconomy signals + `EventsEconomy`, then `CompatRegistry.init` for every
  sibling façade), 6 network payload declaration
  (`EventNetwork.init()`), 7 quest hooks (+ the Supply Beacon delivery), 7b the reward bus, 8 a NeroLink
  module (reserved; goes LAST and swallows its own failures). Fill the slots; do not insert work wherever is convenient.
- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports. Seams are resolved eagerly in step 0, never on a
  tick path. Current seams: `PlatformInfo`, `NetworkPlatform`.
- **Networking:** NeroEvents has its own channel, `neroevents:main`. Declare payloads once in
  `network/EventNetwork.init()`; each loader registers them (`NeoForgeEventNetwork`,
  `ForgeEventNetwork`, `FabricEventNetwork`) and sends go through `Services.NETWORK`. Never add
  payloads to Neroland Core's network registry. The server is authoritative; validate every client
  payload.
- **Menus:** every GUI open goes through `menu/MenuOpener.open` / `openOrConsume`, never
  `Player#openMenu` directly.
- **Saved data:** every NeroEvents `SavedData` is loaded through Core's
  `za.co.neroland.nerolandcore.data.SavedDataRecovery.get`, never a bare `computeIfAbsent`. Every
  player-keyed store registers a `NeroEventsData.StoreEraser` from its own init, and its `backupNow`
  calls `SavedDataRecovery.backupNow` right after erasing (through `privacy/BackupHook`). Add a probe for
  each new store to `ErasureConformanceTest`. The SavedData ids are exactly `neroevents:engine`,
  `neroevents:rewards`, `neroevents:crates`, `neroevents:privacy`, `neroevents:history` and
  `neroevents:wrecks` (world data, no player identity). Payload-held in-memory player state (war sides,
  storm exposure) is erased by the `live_instances` eraser through each payload's static `forget(UUID)`.
- **Config:** Core `ConfigSchema` → `config/neroevents.properties`, key prefixes rather than sections.
  Gameplay keys are server-authoritative; only `telemetryEnabled` and client HUD preferences are
  client-local.
- **Telemetry:** opt-out Sentry (`telemetryEnabled`), inert while the DSN is the placeholder,
  `sendDefaultPii=false`, no server name, home-path and account-name scrubbing, NeroEvents-only stack
  filter, per-session dedup and a 10-event cap. Report handled failures with
  `NeroEventsTelemetry.captureHandledException(t, source, operation)` (stable, non-identifying labels;
  it uses the `captureException(Throwable, ScopeCallback)` overload — never `Sentry.withScope`, whose
  tags never reach the event in Sentry 8.x). Update `PRIVACY.md` before shipping any new data flow.
- **Event engine** (`event/`, `protect/`, `command/`). Everything the engine decides is computed
  against the pure `event/world/WorldView` (no player position, name or UUID — counts only), so the
  whole lifecycle is unit-tested on a plain JVM with `FakeWorldView`; `ServerWorldView` /
  `ServerPayloadContext` are the only MC-bound adapters. Dimensions are `Identifier`s inside the
  engine; never write `ResourceKey<Level>` in `event/`, `protect/` or `anchor/`. Every block or entity
  effect of a payload goes through `PayloadContext.protection()` (deny wins, sources only deny or
  abstain, a throwing source denies). Payload types register in init slot 3 (Core-only) or slot 5
  (sibling façades, only when loaded); the registry freezes on the first server tick. The engine's
  saved data `neroevents:engine` is world data only; votes and contributions are in memory and erased
  through the `live_instances` store eraser. `EventCommands.SYNTAX` drives the command tree and must
  match `wiki/Commands.md` (a test pins it). The `event_resolved` value is the world-total resolved
  count. Server thread only; no proximity heuristics anywhere.
- **Participation and rewards** (`participation/`, `reward/`, `history/`, `vote/`, `privacy/`, `item/`).
  Payloads report contribution only through `participation/Contributions` (attributable actions by real
  online players; no-op outside ACTIVE). `PresenceView` answers yes/no for UUIDs already held and is the
  only place a player position is read (besides the command/claim/beacon path). The split
  (`reward/RewardSplit`) is integer-only and deterministic; every pool is clamped by the definition
  budget, the lines' value and the UTC daily budget. Reward items go to the crate, never into the world.
  The ledger row (or session marker) is written before any sink runs. Logs and telemetry carry ids,
  counts and value totals per instance only — never a UUID, a name, a per-player amount, a share or a
  vote (`NoPersonalDataInLogsTest` guards INFO/WARN lines).
- **Payload-author checklist.** Every `StartResult.skip(reason)` / cancel reason a payload introduces
  needs an `announce.neroevents.skipped.<reason>` lang key. A `kind: target` payload's `pickTarget` result
  is protection-checked by the engine; do not exempt it. Credit contributions only via `Contributions`;
  a supply pod's first open calls `Contributions.creditSupplyClaim`.
- **Quest contract is public API.** Gate ids (`neroevents:first_participation`, `supply_claimed`,
  `raid_repelled`, `boss_contributor`, `hook/supply_beacon`), channel ids (`neroevents:event_resolved`,
  `boss_defeated`, `market_shock_request`) and the `supply_beacon` hook listener are read by data packs
  and other mods; never rename them. `QuestContractTest` locks them.
- **Sibling façades** (`compat/`): one `*Compat` per sibling, a bridge interface plus a `Nero*Bridge`
  implementation loaded through `META-INF/services` (compiled only when its `<x>_compat` flag is on).
  A façade's `register(...)` hook into a payload package must not keep a once-flag (the pool test
  re-initialises the registry per configuration). The engine binds the running server for the claim
  sources with `CompatServer.set` when it starts on a new server.
- **Event Console and HUD** (`console/` server side, `client/` client side). The console block, block
  entity, menu and item register in `console/ConsoleBlocks` (init slot 3, after the war beacon). The
  screen learns everything from `ConsolePayloads.ConsoleSnapshot`; the HUD from `HudState`, pushed once a
  second by `ConsoleServer.tick` (each loader's server tick). Packets carry only what a screen draws: an
  opaque per-instance key (never the UUID), counts, tallies as numbers, and a location only for a
  `location_public` event that is not a raid or invasion target (`ConsoleRows.locationAllowed`; the HUD
  marker additionally only inside the bounds). Every serverbound console payload is checked against the
  open `EventConsoleMenu` (still at the console, rate limit) and the pure rules in `ConsoleIntents`, then
  goes through the same engine / crate / privacy calls the commands use (`ConsolePayloadsTest`,
  `ConsoleIntentsTest`). The clientbound handlers touch only `ClientConsoleState` / `ClientHudState`
  (no client-only classes). Colours live in `client/EventColours` (pinned by `EventColoursTest`), every
  console and HUD-settings rectangle in `client/ConsoleLayout` (`ConsoleLayoutTest`), past-event and
  ledger sentences in `client/HistorySentences` (`HistorySentencesTest`). HUD preferences are
  client-local in `config/neroevents-hud.properties` (`client/HudSettings`). UI strings go in
  `tools/lang/ui.en_us.json`.
- Loader entry points: `NeroEventsFabric` (+ `NeroEventsFabricClient`), `NeroEventsForge`,
  `NeroEventsNeoForge` — each calls `NeroEventsCommon.init()` first, then its network registration.
  The client setups register the payload entity renderers, the war beacon and console BERs, the console
  screen and tint, the event HUD layer and its dismiss key, the HUD settings screen as the mod-list config
  screen (NeoForge, Forge), the four storm particle providers and the disconnect reset of the client
  mirrors; the storm client tick and sky/fog layers come
  from the client mixin `mixin/storm/ClientLevelStormMixin` (`neroevents-common.mixins.json`).
- NeoForge/Forge debug tasks use `-PneroeventsDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`neroevents.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `neroevents-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root, published **publicly** to the
  GitHub wiki by `.github/workflows/wiki.yml` (which refuses to publish pages that reference private
  planning material).
- **Whenever you add, change, or remove a player-facing feature, update `wiki/` in the same change** —
  treat the wiki as part of "done".
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between wiki pages only (links to repo files such as `../AGENTS.md` break on the GitHub wiki; use a
  full `https://github.com/Neroland/neroevents/blob/main/...` URL instead). Validate Markdown via the
  gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroEvents here.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
- Reuse Neroland Core's network registry, call `Player#openMenu` directly, or load `SavedData` without
  `SavedDataRecovery`.
- Change `mod_version` outside a release.
