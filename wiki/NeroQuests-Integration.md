# NeroQuests integration

NeroEvents works with or without **NeroQuests**. When NeroQuests is installed, its quest book gains an
**Event Log** chapter that celebrates taking part in server events and the server's shared history of
them. Without NeroQuests nothing changes; the chapter is plain data inside the NeroEvents jar.

> **Status:** NeroEvents has no events yet, so every quest below stays open until the event scheduler,
> participation tracking and supply drops ship. The chapter is in place so that future content only has
> to record a milestone, not add quests.

## The Event Log chapter

| Quest | For | Done when |
| --- | --- | --- |
| First Call | you | You took part in your first event and did your share |
| Supply Run | you | You claimed a supply drop |
| Raid Repelled | you | You helped repel a raid (after *First Call*) |
| Boss Contributor | you | You met the contribution floor on a world boss (after *Raid Repelled*) |
| World's First Event | the whole server | The first event on the world resolved |
| Seasoned Server | the whole server | Twenty-five events resolved on the world |
| Giant Slain | the whole server | A world boss fell |

Personal quests are recorded per player; "whole server" quests complete once for everyone. Completing
*Supply Run* earns a **Supply Beacon**, a one-use item that asks for a small supply drop where you stand.
It goes straight into your inventory (if it is full, free a slot and it is handed over at your next login).
Using it follows every event rule — protected areas, the calm period and the event limits — and if the
drop cannot be called the beacon is kept and you are told why. Until the server has the supply-drop event
itself, using the beacon just says so and keeps it.

## Privacy

Personal milestones are player-scope records in Neroland Core's progression store, erased with your other
Neroland data on request. Server-wide milestones name only the *type* of event or boss, never who took
part or where it happened. NeroEvents logs none of this.

## See also

- [Home](Home.md)
- [NeroEconomy integration](NeroEconomy-Integration.md)
