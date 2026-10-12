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
*Supply Run* earns a **Supply Beacon**, a one-use item that calls a small private supply drop at your feet
(never inside someone else's claim). Until that item exists, NeroEvents tells you once per login that a
beacon is reserved for you and hands it over when supply drops arrive.

## Privacy

Personal milestones are player-scope records in Neroland Core's progression store, erased with your other
Neroland data on request. Server-wide milestones name only the *type* of event or boss, never who took
part or where it happened. NeroEvents logs none of this.

## See also

- [Home](Home.md)
- [NeroEconomy integration](NeroEconomy-Integration.md)
