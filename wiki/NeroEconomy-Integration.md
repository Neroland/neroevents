# NeroEconomy integration

NeroEvents works with or without **NeroEconomy**. When NeroEconomy is installed, future world events can:

- **Move market prices.** An event can make an item or tag cheaper or dearer for a while (a "market
  shock"). NeroEconomy keeps every shock inside its configured price band and maximum duration, and a
  server operator decides which mods may cause shocks (NeroEconomy's `shockSources` setting, which lists
  `neroevents` by default).
- **React to the economy.** NeroEvents hears NeroEconomy's public market signals: a sector index moving
  through its bands, a shock starting or ending, the Reserve running low, or a Merchant House selling out.
  These signals describe goods and places only, never players.
- **Charge entry fees and pay rewards.** An event can charge credits to enter and pay credits as a reward.
  Rewards come from NeroEconomy's Reserve within daily limits, so on a busy day a reward can be refused;
  you are told why in plain words. Nothing is ever charged without you choosing to enter.

Without NeroEconomy, events are free and give no credit rewards.

## Privacy

NeroEvents records nothing about payments. NeroEconomy keeps its own ledger, which you can export or have
erased along with your other economy data.

## See also

- [Home](Home.md)
