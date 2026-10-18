# NeroEconomy integration

NeroEvents works with or without **NeroEconomy**. When NeroEconomy is installed:

- **Events pay credits.** The credit lines of an event's rewards go straight to your NeroEconomy
  account, even if you were offline when the event ended. They are paid from NeroEconomy's Reserve
  within its daily limits, so on a busy day a payment can be refused; you are told why in plain words.
  See [Rewards](Rewards.md).
- **Market shocks happen.** A [market shock](Event-Market-Shock.md) makes one item or tag cheaper or
  dearer for a while. NeroEconomy keeps every shock inside its configured price band and maximum
  duration, and a server operator decides which mods may cause shocks (NeroEconomy's `shockSources`
  setting, which lists `neroevents` by default).
- **NeroEvents hears the market.** NeroEconomy's public market signals (a sector index moving through
  its bands, a shock starting or ending, the Reserve running low, a Merchant House selling out) reach
  NeroEvents; market shocks use them to know that a shock really started. These signals describe goods
  and places only, never players.

NeroEvents can also charge an entry fee for an event, but none of the shipped events does, and nothing
is ever charged without you choosing to enter.

Without NeroEconomy, events pay no credits (XP, items and reputation are still paid) and market shocks
never come up.

## Privacy

NeroEvents records nothing about payments beyond your own reward history (see [Privacy](Privacy.md)).
NeroEconomy keeps its own ledger, which you can export or have erased along with your other economy data.

## See also

- [Home](Home.md)
