# Consent and voting

With `consentMode=vote`, players decide whether an automatic event goes ahead. This page lists the exact
rules. (The modes themselves are described in [How events work](Events.md#consent-and-voting).)

## Who may vote

- Players who are **online** during the countdown, with `/neroevents vote yes` or `/neroevents vote no`.
  If more than one vote is open, add the event's id.
- One vote per player. Voting again changes your vote. Someone who joins during the countdown may vote.
- Events started by an operator, consent-exempt events and single-player worlds never vote.

## How the vote is decided

When the countdown ends:

1. Votes of players who have **left** are dropped — leaving counts as not voting. (While they are
   away their vote still shows in the live counts, so the numbers do not jump around.)
2. The vote needs a **quorum**: the larger of `voteQuorumPlayers` (2) and `voteQuorumFraction` (half)
   of the players online.
3. Yes votes must be **more than** `voteMajorityFraction` (half) of the votes cast. A tie fails.

A vote that fails skips the event, and its cooldown is halved.

## Early close

When every online player has voted, voting ends straight away (`voteEarlyCloseEnabled`) and the result
is fixed — but the event still starts when the countdown ends, so everyone keeps their warning time.

## Countdowns that are too short

An event with no countdown (or one shorter than `voteMinTicks`) leaves no time to vote, so under
`consentMode=vote` it is always skipped. The server log warns about such events when they load, and
`/neroevents preview` says so.

## Privacy

Only the counts are ever shown. Who voted which way is kept in memory only for that event, never saved,
logged or shown to anyone, and is dropped when the event ends, when the server stops, or at once by an
erasure request.

Consent mode is a courtesy, not a security control: several accounts belonging to one person each get a
vote, bounded only by the quorum.
