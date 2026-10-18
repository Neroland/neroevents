# Planetary storms

Each Nerospace planet has its own weather: a dust storm on Greenxertz, an ion squall on Cindara and a
cryo blizzard on Glacira. A storm covers the whole planet.

| | |
| --- | --- |
| Severity | **HAZARD** (amber warning triangle) |
| Needs | **Nerospace** |
| Can damage | No blocks. Players in the open get status effects; on Cindara batteries under open sky lose some charge |
| Location | The whole planet |

> **This build:** the Nerospace integration is switched off, so planetary storms do not appear yet.
> They will in a later build.

## What to do

Get under a solid roof or into a pressurised room. Glass roofs do not count. Out in the open a line on
your screen tells you to take shelter and its edges glow; under cover both go away.

## The three storms

| Storm | Planet | Out in the open |
| --- | --- | --- |
| Dust storm (`neroevents:nerospace/dust_storm`) | Greenxertz | You cannot see far; **slowness**, and at higher strength **mining fatigue**. |
| Ion squall (`neroevents:nerospace/ion_squall`) | Cindara | **Weakness**; at full strength a short jolt of **nausea** now and then. Dry lightning flashes in the sky. It drains exposed energy storages harder than any other storm, never below a fifth of their capacity. |
| Cryo blizzard (`neroevents:nerospace/cryo_blizzard`) | Glacira | **Slowness** (and **mining fatigue** at full strength). Weak and moderate blizzards only chill; at full strength you take frost damage unless you wear leather. |

With **NeroAgriculture** installed, crops grow more slowly during a storm. Storms change no blocks and
pay no rewards. Only one storm runs on a planet at a time.

## Switches

- `stormEffectsEnabled=false` — no status effects from any storm.
- `stormDrainFractionPerPass=0` — no energy drain.
- `eventsDisabled` — add any of the ids above.

## See also

- [Solar storm](Event-Solar-Storm.md)
- [How events work](Events.md)
