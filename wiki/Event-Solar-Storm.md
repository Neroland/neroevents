# Solar storm

A storm of solar radiation sweeps over a whole space dimension.

| | |
| --- | --- |
| Severity | **HAZARD** (amber warning triangle) |
| Needs | **Nerospace** |
| Can damage | No blocks. Players in the open get status effects; batteries under open sky lose some charge |
| Location | The whole dimension |

> **This build:** the Nerospace integration is switched off, so solar storms do not appear yet. They
> will in a later build.

## What to do

Get under a solid roof or into a pressurised room, and roof over your batteries. Breathable air is no
shelter, and glass roofs do not count. Out in the open a line on your screen tells you to take
shelter and its edges glow; under cover both go away.

## What it does

- Players out in the open get **weakness**, and at higher strength **hunger** too. Effects stop as soon
  as you are under cover.
- **Energy storages** under open sky near players slowly lose charge, never below a fifth of their
  capacity (`stormDrainFloorFraction`). On Fabric only Neroland storages are drained; on NeoForge and
  Forge standard energy storages too.
- With **NeroAgriculture** installed, crops grow more slowly while it rages.
- The sky darkens and tints, fog closes in, and particles and a low rumble fill the air.

Storms change no blocks and pay no rewards. Only one storm runs in a dimension at a time.

## Switches

- `stormEffectsEnabled=false` — no status effects from any storm.
- `stormDrainFractionPerPass=0` — no energy drain; the other `stormDrain*` keys tune it.
- `eventsDisabled` — add `neroevents:nerospace/solar_storm`.

## See also

- [Planetary storms](Event-Planetary-Storms.md)
- [How events work](Events.md)
