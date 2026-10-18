# Changelog

All notable changes to **NeroEvents** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

## [0.5.0-beta.1] - 2026-10-18

The first playable NeroEvents: a server-wide event engine with consent voting, protection rules and
kill switches, contribution-weighted rewards with a reward crate, the Event Console and on-screen event
messages, and the first events. Requires Neroland Core 1.15.0.

**In this build:** supply drops, meteor showers and debris crashes run on any server with Neroland Core;
market shocks run with NeroEconomy; NeroColonies colonies and Open Parties and Claims claims are
protected. Storms (Nerospace), raids, invasions and the world boss (NeroCreatures), the storm crop
modifier (NeroAgriculture) and faction wars (NeroFactions) are written and tested but **compiled out**
(`nerospace_compat`, `nerocreatures_compat`, `neroagriculture_compat` and `nerofactions_compat` are
`false` until those mods' releases can be resolved by the build), so they do not appear yet. The
NeroLink companion-app module is not in this build.

### Added

#### Event engine

- **Event engine.** Event definitions are data-pack JSON under `data/<namespace>/neroevents/events/`,
  loaded and re-read on `/reload` on every loader. A bad file is skipped with one warning naming the
  key; an out-of-range optional field falls back to its default with a warning; the server always
  starts. Each event runs through scheduled → announced → (voting) → active → resolved → cleaned up,
  or is skipped / cancelled with a reason, and survives a server restart in the same phase with the
  same timers (participation does not survive; nobody is stored).
- **Scheduler**: a weighted roll every `schedulerIntervalTicks` with population bands, per-event
  cooldowns, a minimum gap between automatic events, a calm window on fresh servers, a cap on events
  at once, one world boss and one invasion at a time (never two bosses in one dimension), and kill
  switches (`destructiveEventsEnabled`, `combatEventsEnabled`, `economyEventsEnabled`,
  `eventsDisabled`). Per-tick cost is measured and shown in `/neroevents status`.
- **Consent modes** `off` (default), `vote` and `admin` (`consentMode`), with vote quorum and
  majority settings. Single-player is always off. Votes are kept in memory only and erased per player.
- **Protection contract**: every event effect asks an ordered list of protection rules and any one
  refusal wins — exclusion zones (config `eventExclusionZones` and `/neroevents zones`), world spawn
  (`protectSpawnRadiusBlocks`), the Nerospace station dimension, and a guard around Neroland security
  and colony blocks (`protectedBlockNamespaces`, loaded chunks only; an unloaded chunk refuses). A
  failing rule refuses and shows in `/neroevents status`.
- **Placement** near where players are, from per-area player counts only (never a position), checked
  against protection before an event is placed.
- `neroevents:debug_marker`, an operator test event that changes nothing and runs the whole lifecycle
  (`/neroevents start neroevents:debug_marker`).
- Engine config keys (scheduler, consent, kill switches, protection, placement) in
  `config/neroevents.properties`; wiki pages *How events work*, *Commands* and *Configuration*.
- An event definition can have its own what-to-do line (`event.<namespace>.<path with dots>.todo`); the
  console and the HUD banner use it before the event type's line.
- Own network channel `neroevents:main` behind a `platform/NetworkPlatform` seam (one implementation
  per loader, registered via `META-INF/services`) and the `network/EventNetwork` payload registry.
  NeroEvents never adds to Neroland Core's network registry.
- `menu/MenuOpener` — the single guarded door for every GUI open (a refused open is logged and
  reported instead of crashing the server thread).
- `data/NeroEventsData` — NeroEvents' composite eraser, registered with Core's shared per-player erasure
  hook early in common init. Every player-keyed store (reward history, crate, opt-outs, exports and
  everything kept in memory) registers here and reads its saved data through Core's `SavedDataRecovery`.

#### Participation, rewards and privacy

- **Participation tracking**, in memory only and per event: damage dealt to an event's creatures
  (projectiles count for their owner), containers/pods opened (once each), objectives captured and waves
  survived (only after helping in another way). Fixed weights per kind (config `contribution*`), a
  credit floor so one stray hit is not participation, and a fade for players who leave the area or log
  off. Real online players only — never proximity, never fake players.
- **Budgeted rewards**: each event's credits, XP, items and (with NeroFactions) reputation are split
  by contribution in whole-number arithmetic (exact and repeatable), with a minimum share for anyone
  who did at least 1 % of the work and optional top-rank bonuses. Every payout is clamped by the event's
  budget, the lines' value and a world-wide daily budget (`rewardBudgetPerDay`, UTC day, in value units);
  a world boss's own drops count against it. Credits are paid through NeroEconomy as the declared
  `event_reward` source (skipped, with one log line per event, without NeroEconomy). Paid once per event
  per player, even across reconnects.
- **Reward crate**: items (and XP for offline players) wait in a per-player crate — never on the
  ground — and are claimed with `/neroevents claim`; 27 stacks at most, unclaimed rewards kept 14 days.
- **Reward history** (`neroevents:rewards`, 7 days) behind `/neroevents rewards`, and an aggregate
  event history (`neroevents:history`, counts and totals only, 200 events) with the daily budget counter.
- **Consent policy**: votes of players who left are dropped at decision time; voting closes early once
  every online player has voted (`voteEarlyCloseEnabled`; the event still starts at the countdown's
  end). A definition with no countdown warns at load and in `/neroevents preview`.
- **Privacy commands** `/neroevents privacy optout | optin | status | export`: opting out keeps you
  paid but stores no reward history under your id; the export writes your own data (and nobody else's)
  to `neroevents/exports/<uuid>.json` for 7 days. Retention keys with a 90-day hard cap
  (`privacyRetentionHardCapDays`), purged at world load and hourly. Every player-keyed store is erased by
  `/neroland data eraseme` and its backup refreshed; erasure forfeits unclaimed crate items.
- **Supply Beacon** (`neroevents:supply_beacon`): the *Supply Run* quest reward now hands over a real
  one-use item (into the inventory only; a full inventory retries at next login). Using it requests a
  supply drop at the holder's position under every event rule and is kept, with a reason, when refused.
- The quest contract now fires: `first_participation` on a player's first credit, `raid_repelled` and
  `boss_contributor` from the resolver, `boss_defeated` (scope = the boss event's id), and
  `supply_claimed` through the supply-drop helper. Ids unchanged.

#### Events

- **Supply drop** (`neroevents:supply_drop`, plus a major variant and the beacon-called drop): a pod
  falls where it can see the sky; the first player to open it claims the drop, later openers still get
  a loot share. The Supply Beacon now calls a real drop. The pod breaks no blocks.
- **Meteor shower** (`neroevents:meteor_shower`, plus a space variant): meteors over an area for a few
  minutes, each digging a small crater with a crater pocket to break for loot and credit. Craters never
  land in protected areas or near block entities (`craterAvoidsBlockEntities`,
  `craterBlockEntityMarginBlocks`); `meteorBlockDamage=false` turns every impact into a soft landing.
- **Debris crash** (`neroevents:debris_crash`): a wreck with salvage crates. A crate cannot be broken
  while it holds loot (an actionbar line says so). Wrecks rust away after `wreckLingerDays` real days
  (`0` = never) and become rubble after twice that; at most `wreckRecordsMax` wrecks are tracked.
- **Storms** (with Nerospace): `neroevents:solar_storm` over any space dimension and
  `neroevents:planetary_storm` (dust storm, ion squall or cryo blizzard by world). Players out in the
  open get the storm's effects (`stormEffectsEnabled`); solar storms and ion squalls drain energy
  storages under open sky, never below a fifth of their capacity (`stormDrain*` keys). Sky tint, fog,
  particles and ambient sound on the client. With NeroAgriculture, crops grow more slowly during a storm.
  One storm per dimension; storms change no blocks and pay nothing.
- **Pirate raid** and **alien invasion** (with NeroCreatures): waves scaled by the number of players
  online; damage dealt and waves survived count as participation. NeroEvents never breaks blocks for a
  raid (`raidBlockDamage` is reserved and stays `false`); the creatures follow the server's normal mob
  rules (`mobGriefing`, claim mob protection).
- **World boss** *The Cinder Tyrant* (with NeroCreatures): damage dealers share the reward; an unfought
  boss escapes.
- **Market shock** (with NeroEconomy): a temporary price move on one good, forecast in chat.
- **Faction war** (with NeroFactions; off in this build, see below): the two factions with the most members online
  (`warMinMembersOnline`) contest a ring of unbreakable war beacons; capturing and holding score points,
  the winning side's fighters gain reputation with their own faction and the losing side's lose some
  (capped by `rewardReputationMaxDeltaPerEvent`). Players in both factions or neither stay neutral. The
  `neroevents:war_resolved` channel fires per faction. The `neroevents:war_beacon` block (in Neroland
  Core's creative tab) is always registered, so worlds keep it with the integration off.
- **Sibling protection**: NeroColonies colonies and Open Parties and Claims claims refuse destructive
  event effects. FTB Chunks and Flan are not integrated (use `eventExclusionZones`).
- Optional sibling integrations are chosen at build time and detected at runtime; each only adds its
  events or protection when the sibling is installed. Operator `/neroevents status` lists them, shows
  when market shocks are paused, and everyone's `/neroevents status` lists active storms.
- Config keys for these events (`supplyBeaconDefinition`, `meteorBlockDamage`, `craterAvoidsBlockEntities`,
  `craterBlockEntityMarginBlocks`, `wreckLingerDays`, `wreckRecordsMax`, the `stormDrain*` keys,
  `stormEffectsEnabled`, `raidBlockDamage`, `warMinMembersOnline`).

#### Event Console, HUD and effects

- **Event Console** (`neroevents:event_console`, in Neroland Core's creative tab): anyone can place and
  use one. "What's happening on this server" in five tabs — **Live** (running events, what to do, time
  left, players taking part), **Soon** (announced events, with big YES / NO buttons and the tally as
  numbers while a vote is open), **Past** (the last events in plain sentences), **Mine** (your reward
  crate with a CLAIM button, and your recent rewards) and **Privacy** (what is kept about you, with
  Export, Opt out / Opt in and Erase, which opens the chat with Core's erase command). Every severity
  has a colour, a symbol and a word; every greyed-out button says why; every action answers in the
  footer. Operators get a cancel cross on each event (asks first). The block's screen takes the colour
  and symbol of the most urgent event. Voting, claiming and the privacy actions go through the same
  code as `/neroevents vote`, `claim` and `privacy`; the console's export answer never shows the file
  name (it carries your id).
- **Event HUD**: a countdown pill while an event is announced or being voted on, a banner when it
  starts, wave and beacon lines during raids, invasions and wars, a world marker toward a public event
  while you are inside its area (never for raid targets), and the storm shelter line and screen-edge
  vignette. **H** hides it until the next change.
- **HUD settings** ("Where event messages appear", from the console's gear, or the mod list's config
  button on NeoForge and Forge): drag each message to one of nine spots, six show/hide switches, a
  0.75× / 1.0× / 1.25× scale and Reset. Saved on your computer only
  (`config/neroevents-hud.properties`); nothing is sent to a server. The *Impact flash* switch turns the
  meteor impact flash off on your screen (the embers and dust still show).
- Two server-to-client packets (the console snapshot and the HUD state) and five client-to-server
  console requests on `neroevents:main`. They carry counts and numbers only — no player names or ids,
  an opaque key instead of the event id, and a location only for events that make theirs public and are
  not raid or invasion targets. Every request is checked on the server against the open console.
- Three server-to-client packets on `neroevents:main`: the storm in a dimension, a player's storm
  exposure, and the meteor impact flash.
- **Effects**: meteors trail smoke and embers (off beyond 96 blocks; with more than 20 meteors in view
  every second one trails at half rate) and debris trails smoke; a meteor impact shows a bright flash,
  a cone of embers and ground dust (no screen shake; a soft landing has no flash); debris kicks up dust
  where it lands; a landing supply pod throws up dust, then sends a gold beacon ring up every second, and
  an open pod gives off reward sparks. Five new particle types (`meteor_smoke`, `meteor_ember`,
  `impact_flash`, `beacon_ring`, `reward_spark`) share one 800-particle cap.
- **Sounds**: an announcement and an event starting play a cue for its severity to everyone online
  (INFO a rising chime pair, REWARD a level-up, HAZARD a beacon drop then a low note, COMBAT a raid horn,
  WAR a low horn with a bell); a vote opening clicks; a falling pod hums until it lands; opening a pod,
  meteors whooshing past (every second in flight), the war beacon ticking while it is contested (higher
  as the capture fills) and its capture all play at their own ranges. Subtitles for every NeroEvents
  sound.
- The NeroEvents logo now shows in the mod list on all three loaders.
- `/nsevgallery capture` warns in chat when other players are in the world, since their names and skins
  can appear in the screenshots; the wiki asks you to check every capture before it is published.

#### Commands

- **Commands** `/neroevents list | status | vote | preview | start | cancel | skip | reweight | zones`
  plus `claim`, `rewards`, `privacy` and `budget` (see the wiki's Commands page).
- `/neroevents budget` (operators); `/neroevents preview` shows the reward budget and value; operator
  `/neroevents status` shows today's remaining budget and store sizes.
- `/neroevents gallery` and `/neroevents gallery clear` (operators, creative mode, for a test world): every
  block, both console states, empty pods, a war beacon and hanging meteors and debris laid out for
  screenshots. The client-only `/nsevgallery capture [time]` re-shoots it into `screenshots/neroevents/`
  with the HUD hidden; `/nsevrender` reports the payload renderers' draw counts, their time and the frame
  time.
- **Gallery showcase: every event in action.** `/neroevents gallery` now also builds one station per event
  type north of the exhibits: a pod landing every 20 s, a meteor striking a real (loot-free) crater every
  5 s, debris crashing onto a wreck, a wreck yard with all four layouts and both decay stages, three war
  beacons with one being captured and recaptured, a labelled raid/invasion bay and boss arena (no creature
  is spawned, so nothing there drops loot or ends a fight), four storm posts with a storm preview on your
  screen, and a market board. Stations whose real event needs another mod say so on their label. A **demo
  HUD and Event Console** cycle through every severity for the operator who built it only; it is held in
  memory only, never saved or logged, and steps aside the moment a real event is announced or running.
  Nothing in the gallery pays, credits, records, opens a gate or calls another mod, and the whole footprint
  is checked against the protection rules before anything is built (a refusal names the rule). Building a
  gallery anywhere ends your own earlier demo. New forms: `gallery exhibits`, `gallery events`,
  `gallery stress` (the renderer-budget scene: 40 more meteors, 10 pods and an ion storm preview),
  `gallery storm <storm>` and `gallery cue <station>`. `/nsevgallery capture` now shoots every station,
  every storm preview, every demo HUD scene and every console tab; `/nsevgallery capture stress` shoots the
  budget scene and saves the `/nsevrender` numbers to `screenshots/neroevents/stress_budget.txt`. **Use a
  test world**, and check every capture for other players' names before you publish it. See the wiki's
  Gallery page.

#### Companion app

- `pushNotificationsEnabled` kill switch (default `true`): `false` sends no companion-app notification or
  stored alert about events to anyone. It has no effect in this build, which has no NeroLink module.

#### Documentation and tests

- `EVENT-DEFINITIONS.md` — the event definition JSON schema (every field, default and validation rule)
  with one complete example per payload type, so server owners can write events without code.
- `USING-CORE.md` (every Neroland Core API used) and `USING-NEROLINK.md` (the planned companion-app
  contract, marked as not yet implemented).
- Wiki: *Getting started*, *The Event Console*, *HUD settings*, *How events work*, one page per event
  type, *Consent and voting*, *Rewards*, *Protection and fair play*, *Writing your own events*,
  *NeroLink app and notifications*, *Commands*, *Configuration* and *Privacy*.
- `PRIVACY.md` describes every store, its retention, the opt-out, the export and erasure.
- Plain-JVM JUnit 5 tests (`common/src/test/java`, run by every NeoForge cell build) for the engine
  (definitions, conditions, bands, weighted selection, caps, lifecycle, anchors, protection, resumable
  records, the public event view, signals, command syntax and the wiki's Commands page), participation
  and rewards (contribution table, split, valuation, daily budget, resolver, ledger, crate, history,
  vote policy), privacy (opt-out, retention, export, erasure conformance through Core's harness, a
  log-line guard), every event (supply drop, craters, meteor shower, wreck layouts, placement and decay,
  storm variants, exposure and drain, waves, the boss, raid targets, market shock, war sides, scoring and
  the war payload), the protection sources, the event pool under each combination of installed
  siblings, the quest contract, the Supply Beacon, the console and HUD layouts, severity colours and
  contrast (WCAG AA), packet data minimisation and round trips, and telemetry scrubbing.

### Changed

- The NeroQuests contract now fires: `first_participation` on a player's first credit, `supply_claimed`
  through the supply drop, `raid_repelled` and `boss_contributor` from the resolver, `event_resolved` and
  `boss_defeated` (scope = the boss event's id). Ids unchanged. The *Supply Run* reward hands over the
  real Supply Beacon item.
- `economy/EventEconomy` is now `economy/EventsEconomy` and gains a UUID payout for offline participants.
- Common init follows the numbered Neroland order (seams, config, telemetry, reserved registries,
  erasure, compat, network, quest hooks, reserved companion module).
- Optional NeroEconomy floor raised to **0.4.0-beta.1** (the version compiled against) in every loader
  manifest; the runtime API-version check is gone (the manifest floor guarantees the API, and a link
  failure still leaves paid events and credit rewards off).
- Each jar now declares only its own Minecraft line (for example `[26.2,26.3)` on NeoForge/Forge,
  `>=26.2 <26.3` on Fabric), the same per-cell cap as Neroland Core, so a jar can no longer load on a
  newer Minecraft it was not built for.
- Error reports now replace anything shaped like a UUID in messages and breadcrumbs with `<uuid>`.
- CI: a guard (every push to `main` and every pull request) fails if `wiki/` or any tracked file
  references private planning material, case-insensitively; the wiki sync runs only after it passes.
  Dependabot targets `main`; workflow action pins match Neroland Core's, and the build workflow's header
  lists the current loader pins.
- `.gitignore` now ignores run logs and crash reports inside the per-version nodes.
- The Supply Beacon's tooltip, its announcement and the wiki now say plainly that a called drop lands
  where you stand and that everyone online is told its direction and rough distance; `PRIVACY.md`
  lists it as the one location disclosure a player chooses.
- Meteor `ore_blocks` entries that are not plain solid blocks (fluids, block entities, see-through
  blocks) are now warned and ignored; a crater lays meteor ore instead.
- A meteor's protection pre-check now reaches one block higher above the impact, so blocks attached
  beside the crater rim are covered too.
- Wiki and docs no longer say raid creatures "never break blocks": NeroEvents never breaks blocks for
  a raid, and the creatures follow the server's normal mob rules (`mobGriefing`, claim mob protection).
- CI: the private-reference guard also matches citations of the release checklist document, and two
  Modrinth workflow comments no longer cite it.

### Fixed

- Event Console: a client flooding requests faster than the console's rate limit is now answered
  "too fast" at most once per interval instead of with a full snapshot per packet.
- **Breaking the Event Console now drops it.** It had no loot table, so it vanished when mined; it now
  drops itself (unless destroyed by an explosion) and mines at pickaxe speed.

- **Block and loot drops work as intended on Minecraft 26.3** (NeoForge, Fabric and Forge). Minecraft
  26.3 changed the loot-table file format and silently ignores the old one, so conditions and drop
  modifiers (explosion survival, door/slab block-state checks, drop counts, random chances) were skipped.
  The 26.3 builds now ship every loot table in the new format. 26.1.2 and 26.2 were not affected.
- Telemetry: handled-error reports now use Sentry's `captureException(Throwable, ScopeCallback)`, so
  their tags and per-source/operation fingerprint actually reach the event (tags set inside `withScope`
  were dropped). Scrubbing now also removes this machine's home directory and account name, and scrubs
  stack-frame file names.
- PRIVACY.md now discloses breadcrumbs, the per-launch session id and sampled timing data, and says the
  opt-out takes effect on restart.

### Not in this release

These were considered for 0.5.0 and left out on purpose:

- **Event chains and storylines** (one event leading to the next), **dynamic difficulty**, **real-calendar
  seasonal events**, **player-summoned events** other than the Supply Beacon's supply drop, and a
  **spectator map** of events.
- **The NeroLink companion-app module and push notifications.** No section, action or notification is
  sent to the app; `pushNotificationsEnabled` exists but has nothing to switch yet. The planned contract
  is in `USING-NEROLINK.md`.
- **Raid targeting of colonies, stations and installations.** Raids and invasions target populated
  areas only.
- **FTB Chunks and Flan** claim protection (use `eventExclusionZones`). NeroSecurity has no public
  area query yet, so its installations are protected only by the block-namespace guard
  (`protectedBlockNamespaces`).
- **Storms, raids, invasions, the world boss and the storm crop modifier** are off by default in this
  build until the Nerospace, NeroCreatures and NeroAgriculture artifacts can be resolved by the build
  (their `*_compat` flags).
- **Faction wars** ship behind `nerofactions_compat=false` until NeroFactions is published. The
  `neroevents:war_beacon` block is always registered, so worlds keep it either way.
- **Entry fees**: `EventsEconomy.chargeEntry` exists, but no shipped event charges one.

### Known limitations and placeholders

- Wrecks are built from layer tables in data (`data/neroevents/neroevents/wrecks/`), not structure files.
- The market-shock forecast names the good in a follow-up chat line, not in the announcement itself. A
  shock the market never confirms ends as a failure; after two in a row, market shocks pause until the
  server restarts.
- The faction-war score is an objective line on the HUD and console ("Red 34 · Blue 21"), not a chat
  message; a war beacon shows capture progress with its ground ring, not a percentage. A war beacon
  placed from the creative tab has no war and cannot be broken (remove it with `/setblock`).
- The ion squall's dry lightning is the vanilla sky flash. Storm drain affects energy storages only.
  Storm state is re-sent to everyone in the dimension every 10 seconds, so a player who joins mid-storm
  sees it within 10 seconds. If the sky/fog hook is missing on a Minecraft version, storms simply do not
  tint the sky (particles and sound still play).
- Cryo blizzards only chill at weak and moderate strength; only a full-strength blizzard deals frost
  damage (not to players wearing leather).
- A full salvage crate cannot be broken in survival. A reward stack's value is its count times
  `rewardItemDefaultValue` unless the definition gives one; a loot table's rolls repeat the whole table
  before the value cap applies.
- Participation is never saved: a restart during an event loses everyone's progress toward its
  rewards, and an event that was paying out when the server stopped finishes without rewards. Opted-out
  players' temporary crate is cleared on restart.
- After a hard crash, the day's reward spend may be under-counted by the last unsaved events (nobody is
  paid twice).
- Colony protection refuses when a dimension cannot be checked; an unloaded chunk in the way of a
  destructive effect refuses it.
- Fabric has no mod-list config button for the HUD settings (no Mod Menu dependency): use the console's
  gear.
- The console's **Erase** opens the chat with `/neroland data eraseme` typed in; Neroland Core performs
  and confirms the erasure.
- `/nsevgallery capture` cannot hide other players: check every capture for other players' names before
  publishing it.
- `raidBlockDamage` and the raid payload's `block_damage` are reserved: NeroEvents never breaks blocks for a
  raid. Spawned creatures are ordinary mobs and follow `mobGriefing` and your claim mod's mob protection.

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
