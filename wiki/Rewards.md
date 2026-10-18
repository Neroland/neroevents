# Rewards

When an event ends well, the players who took part share its rewards. This page explains who counts
as taking part, how the rewards are split, where they go and how the server keeps rewards in check.

## Who takes part

Only things you actually do during an event count, while it is running:

| What you do | Counts as |
| --- | --- |
| Damage an event's creatures (your arrows and other projectiles count too) | 1 per point of damage (`contributionWeightDamage`) |
| Open an event's container, pocket or pod | 25 each, once per container (`contributionWeightLoot`) |
| Capture an objective | 50 per capture (`contributionWeightObjective`) |
| Survive a wave inside the event area | 10 per wave (`contributionWeightSurvival`) — only once you have also helped in another way |

Standing near an event earns nothing, and automated "fake players" from other mods never count.
One stray hit does not make you a participant either: you need at least `contributionCreditFloor`
(1 by default).

If you leave the event area or log off, your contribution slowly fades: after
`contributionInactivityTicks` (5 minutes by default) away it halves, and again for every further
5 minutes. Coming back stops the fading but does not restore what was lost. Logging out does not
freeze it.

## How the split works

Each event has a **budget** and a list of reward lines (credits, XP, items, and reputation when
NeroFactions is installed). Every participant's share is their part of the total contribution, with
two fairness rules:

- **A minimum share.** Anyone who did at least 1 % of the work (`rewardFloorEligibleRawShare`) gets at
  least the event's minimum share (5 % by default), so a late helper is not left with nothing. If too
  many people qualify for that to add up, everyone simply gets an equal share.
- **Exact, repeatable sums.** The split uses whole numbers only. What everyone receives always adds up
  to exactly what the event pays, and the same contributions always give the same result. Ties are
  settled the same way every time.

Some events also give a **bonus** to the top contributors by rank (for example rank 1 only), and some
pay even when their timer runs out.

You get a chat line with what you earned and your rank. `/neroevents rewards` shows your recent
rewards — only yours; nobody else's rewards are ever shown, and there are no leaderboards.

## Where rewards go

- **Credits** go straight to your NeroEconomy account, even if you were offline when the event ended.
  Without NeroEconomy on the server, events pay no credits (everything else is still paid).
  NeroEconomy has its own daily limits; if it refuses a payment you are told why.
- **Items** go into your **reward crate**, never onto the ground. Claim them with
  `/neroevents claim`. Whatever does not fit in your inventory stays in the crate for next time. The
  crate holds up to 27 stacks (`rewardCrateMaxStacksPerPlayer`); rewards beyond that are lost, and you
  are told. Unclaimed rewards wait **14 days** (`privacyRewardCrateRetentionDays`) and are then lost.
- **XP** is given straight away if you are online, otherwise it waits in your crate.
- **Reputation** changes only with NeroFactions installed, at most 25 per event
  (`rewardReputationMaxDeltaPerEvent`).

You are paid once per event, even if you disconnect and come back.

## The daily budget

All events together pay at most `rewardBudgetPerDay` (6000 by default) per day, counted in **value
units**: 1 credit = 1, 1 XP point = 0.25 (`rewardXpValuePerPoint`), and an item is worth its event's
value for it or 10 (`rewardItemDefaultValue`). Reputation does not count. The day is the UTC calendar
day of the server clock. When the budget runs low, rewards shrink evenly; when it is spent, events still
run and an announcement says rewards resume tomorrow. A world boss's own drops count against the
budget too (`bossCreaturesRewardValuePerParticipant` per participant). Operators can check the day with
`/neroevents budget`. The day's spend is saved with the world, so after a crash the day's spend may be
under-counted by the last unsaved events (nobody is paid twice).

Setting `rewardBudgetPerDay` to 0 switches event rewards off entirely: 0 pays nothing and says nothing.

## The Supply Beacon

Completing the *Supply Run* quest gives you a **Supply Beacon**. Use it to ask for a small supply drop
where you stand. It obeys every event rule — protected areas, the calm period and the event limits —
and is used up only when the drop is on its way; otherwise you keep it and are told why.

Everyone online is told the direction and rough distance of the pod — do not call one at a place you
want to keep private.
