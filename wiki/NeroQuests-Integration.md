# NeroQuests integration

NeroEvents works with or without **NeroQuests**. When NeroQuests is installed, its quest book gains an
**Event Log** chapter that celebrates taking part in server events and the server's shared history of
them. Without NeroQuests nothing changes; the chapter is plain data inside the NeroEvents jar.

## The Event Log chapter

| Quest | For | Done when |
| --- | --- | --- |
| First Call | you | You took part in your first event and did your share |
| Supply Run | you | You were the first to open a supply pod (after *First Call*) |
| Raid Repelled | you | You helped beat back a pirate raid or an alien invasion (after *First Call*) |
| Boss Contributor | you | You did at least the contribution floor on a world boss that fell (after *Raid Repelled*) |
| World's First Event | the whole server | The first event on the world resolved |
| Seasoned Server | the whole server | Twenty-five events resolved on the world |
| Giant Slain | the whole server | A world boss fell |

Personal quests are recorded per player; "whole server" quests complete once for everyone. In this build
raids, invasions and the world boss are not switched on yet (see [How events work](Events.md#the-events)), so
*Raid Repelled*, *Boss Contributor* and *Giant Slain* stay open until they are.

## The Supply Beacon

Completing *Supply Run* earns a **Supply Beacon**, a one-use item that calls a small supply drop where
you stand. It goes straight into your inventory; if it is full, free a slot and it is handed over at
your next login. Using it follows every event rule — protected areas, the calm period, the event limits
and the kill switches — and if the drop cannot be called the beacon is kept and you are told why. See
[Supply drop](Event-Supply-Drop.md#the-supply-beacon).

## Privacy

Personal milestones are player-scope records in Neroland Core's progression store, erased with your other
Neroland data on request. Server-wide milestones name only the *type* of event or boss, never who took
part or where it happened. NeroEvents logs none of this.

## See also

- [Home](Home.md)
- [NeroEconomy integration](NeroEconomy-Integration.md)
