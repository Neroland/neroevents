# Market shock

Traders report a shortage, a glut or a rush on one good, and its price moves for a while.

| | |
| --- | --- |
| Severity | **INFO** (blue ring with a dot) |
| Needs | **NeroEconomy** |
| Can damage | Nothing in the world. It changes one price, temporarily |
| Location | Server-wide |

## What to do

Check the market. After the announcement a forecast line names the good ("Traders report an iron
shortage — prices will move for about 45 min."). Buy low, sell high while it lasts. A market shock pays
no rewards of its own.

## How it works

NeroEvents asks NeroEconomy for a temporary price factor on one item or tag. NeroEconomy decides: it
keeps every shock inside its own price band and maximum duration, and only listens to the mods in its
`shockSources` setting (`neroevents` by default). The request names a good, never a player.

If the market never confirms the shock, the event ends as a **failure** with its own announcement line
(its cooldown is not shortened). After two unconfirmed shocks in a row, market shocks pause until the
server restarts; operators see that in `/neroevents status`.

The shipped shocks: iron and redstone shortages, a copper glut, a Nero alloy shortage, a plasma glass
glut and a rush on diamonds.

## Switches

- `economyEventsEnabled=false` — no economy events.
- `eventsDisabled` — add `neroevents:economy/market_shock`.

## See also

- [NeroEconomy integration](NeroEconomy-Integration.md)
- [How events work](Events.md)
