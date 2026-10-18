# Supply drop

A supply pod falls from the sky near where players are. Be the first to open it.

| | |
| --- | --- |
| Severity | **INFO** while it is announced, **REWARD** (gold diamond) once it lands |
| Needs | Neroland Core only |
| Can damage | Nothing: the pod breaks no blocks |
| Location | Public — the announcement, the console and the HUD say roughly where it is (direction and distance, never coordinates) |

## What to do

Head for the pod and open it. The **first player to open it claims the drop** (that is what the
*Supply Run* quest counts); anyone who opens it afterwards still gets a share of the loot. Opening the
pod counts as taking part, and when the event ends everyone who took part shares its rewards — see
[Rewards](Rewards.md). A pod nobody opens pays nobody.

## What it changes

The pod lands only where it can see the sky, and it breaks, replaces and places no blocks. An emptied
pod stays for a while, then goes; an unopened one stays a little longer. Its loot goes into the
opener's inventory like any container; event rewards wait in the reward crate.

## The three supply drops

| Event | When it comes |
| --- | --- |
| Supply Drop (`neroevents:core/supply_drop`) | On any server with someone online. It may come during the calm window of a fresh server. |
| Major Supply Drop (`neroevents:core/supply_drop_major`) | A heavier pod with better loot, on servers with at least 3 players online in the `5-9` or `10+` population band. |
| Called Supply Drop (`neroevents:core/beacon_supply_drop`) | Only when someone uses a **Supply Beacon**. It never needs a vote. |

### The Supply Beacon

Completing the *Supply Run* quest (with NeroQuests) gives you a one-use **Supply Beacon**. Use it to
call a small supply drop where you stand. It follows every event rule — protected areas, the calm
window, the event limits and the kill switches — and it is used up only when the drop is on its way.
Otherwise you keep it and are told why.

Everyone online is told the direction and rough distance of the pod — do not call one at a place you
want to keep private.

## Switches

- `eventsDisabled` — list the ids above to stop them firing automatically.
- `supplyBeaconDefinition` — which event a Supply Beacon calls.
- `rewardBudgetPerDay` — the server-wide reward budget.

## See also

- [How events work](Events.md)
- [Writing your own events](Writing-Your-Own-Events.md) — the supply-drop payload settings
