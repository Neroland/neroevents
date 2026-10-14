# NeroEvents

**Dynamic server events that keep worlds alive — meteor showers, alien invasions, supply drops and server-wide bosses for the Neroland universe.**

NeroEvents is the **dynamic server events** mod of the Neroland ecosystem — the connective tissue that turns a collection of static systems into a living server. It runs a server-side scheduler that fires timed or reactive world events — meteor showers, solar storms, alien invasions, pirate raids, supply drops, faction wars, market shocks and server-wide boss fights — that pull players out of their automated bases and back into the world for shared moments. Most modded servers feel exciting for two weeks and then go quiet; NeroEvents fights that decay directly.

Built on **Neroland Core**, so its config framework, progression gates, currency/reputation reward routing, power/upgrade disruption hooks and `c:` compat tags are shared with the rest of the lineup. *(Planned — in design; not yet released.)*

---

## 1. The event scheduler

A server-side scheduler on a tick-based clock maintains a **weighted pool** of registered event definitions. Each carries conditions (dimension, minimum online players, time-of-day, faction state, economy thresholds), a cooldown, a weight and a payload. Every pass filters the pool by current world state, rolls against weights, and either fires immediately or queues a scheduled announcement with a countdown.

- 🗓️ **Reactive, not random noise** — events read server state (population, faction balance, economy, dimension) and fire content that is contextually relevant, not purely random.
- 📜 **Datapack-driven** — every event is declarative JSON referencing reusable payload templates (spawn waves, structure drops, loot injection, broadcasts, world effects), so owners define new events without code.
- 🛠️ **Admin tooling** — force, cancel, preview and reweight events live at runtime.

## 2. Progression-gated event pool

The pool is gated by Core's server-side progression rules so early players are never hit with content they cannot survive, and the same datapack behaves correctly on a fresh server and a mature one.

- 🌍 **Earth / early industrial** — mild events: supply drops, market price changes, small pirate raids that reward exploration and trade.
- 🚀 **Space access** — meteor showers, space debris crashes, solar storms and planetary storms unlock once players reach orbit and planets.
- 🪐 **Colonies & late game** — alien invasions, faction wars and server-wide boss events scale to established populations and high-tier gear.

## 3. World events & hazards

- ☄️ **Meteor showers** — falling meteor entities leave smouldering craters with embedded ore and relic loot.
- 🌞 **Solar storms** — a dimension-wide hazard that disrupts powered machinery, drains shields and raises ambient damage to unprotected players in space.
- 🌪️ **Planetary storms** — localised dust storms, ion squalls and cryo blizzards keyed to Nerospace planet metadata, affecting movement, visibility, oxygen or power.
- 🛰️ **Space debris crashes** — derelict satellite and hull fragments form mini-wreck salvage sites, occasionally with hostile salvage drones.

## 4. Combat & conflict events

- 👽 **Alien invasions** — wave-based combat escalating around populated areas until repelled.
- 🏴‍☠️ **Pirate raids** — smaller targeted raids on a base, convoy or station, with an objective tracker driving loot and reputation outcomes.
- ⚔️ **Faction wars** — structured contested-objective conflicts between two or more factions, scored to a victor.
- 🐲 **Server-wide boss events** — the flagship spectacle: a single powerful boss spawns at an announced location and the whole server is invited to fight it.

## 5. Rewards & economy

- 🎁 **Supply drops** — air-dropped, contested loot pods with guarded loot tables.
- 📈 **Market price changes** — non-combat economic shocks (shortages, crashes, trade booms) that shift NeroEconomy price curves for a window.
- 🏆 **Contribution-weighted rewards** — a participation tracker records who engaged (boss damage, drops collected, raids repelled) and splits loot fairly, routing payouts through Core's currency/reputation API so inflation can be monitored centrally.
- ⚖️ **Fair disruption** — destruction is scoped, opt-out-able where appropriate, respects claims and faction territory, and never permanently ruins protected claims; a "calm window" keeps newly-wiped servers gentle.

## Privacy (POPIA / GDPR)

NeroEvents is designed to store **the minimum possible**. The scheduler and participation tracker prefer aggregate counts and transient session data over durable per-player logs; where participation records are kept (who joined a boss fight, when, reward payouts) they are **UUID-only**, retention-pruned on a short configurable window, and cleared through Core's shared data-erasure hook so one request purges you across every Neroland mod. Player data is never exported or transmitted off-server without explicit consent, and the server config documents exactly what is retained. Any anonymous crash telemetry is **opt-out** and carries only version strings — never names, UUIDs, IPs or world data.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one config framework, one progression arc, one reward/reputation API, shared `c:` tags. NeroEvents ships in its own creative tab.
- 🔌 **Interoperates, never hard-depends** — it detects other Nero mods and unlocks the matching payloads when present: **Nerospace** (space weather, planet metadata), **NeroEconomy** (market shocks), **NeroFactions** (wars and raid targets), **NeroCreatures** (invasion and boss rosters), **NeroRuins** (relic loot and wreck/arena structures) and **NeroAgriculture** (storm crop effects). With them absent, those systems still run on their own — they simply lose the timed, global event presentation.
- 🌐 **External interop through Core tags only** — Create, AE2, Mekanism, Ad Astra and Energized Power are reached through Core's common compat tags (power tags for machine disruption, material tags for salvage/reward loot, space tags for dimension gating), never a hard dependency on any of them.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroEvents (it loads first).
- Soft synergies with **Nerospace**, **NeroEconomy**, **NeroFactions**, **NeroCreatures**, **NeroRuins** and **NeroAgriculture** — each unlocks more event payloads when present, none is required.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroEvents by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/neroevents). Full terms: [LICENSE](https://github.com/Neroland/neroevents/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neroevents/wiki)** — every event and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neroevents/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neroevents/blob/main/CHANGELOG.md)**
- 🟢 **[Also on Modrinth](https://modrinth.com/mod/neroevents)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
