# Commands

Everything NeroEvents does from the chat box goes through `/neroevents`. `list`, `status`, `vote`,
`claim`, `rewards` and `privacy` are open to every player; everything else needs operator permission
(level 2, the same as `/gamemode`). Replies go only to the person who typed the command.

An `<instance>` is the short, 8-character id an event gets when it is scheduled (for example
`3f9a2c1d`). `/neroevents status` shows it, tab completion suggests it, and any unambiguous prefix
works. It is a random id and says nothing about any player.

| Command | Who | What it does |
| --- | --- | --- |
| `/neroevents list` | Everyone | Lists every loaded event with its category, severity, whether it is enabled and its weight. Operators also see whether each one could fire right now, and how many definitions were skipped at load. |
| `/neroevents status` | Everyone | Shows the consent mode, the population band, the calm window, running events with their countdowns, and open vote counts. Operators also see when the next automatic roll is, cooldowns, weight overrides, engine timings and the health of every protection source. |
| `/neroevents vote yes` | Everyone | Votes yes on the one open vote. If more than one vote is open, add the event's id. |
| `/neroevents vote no` | Everyone | Votes no on the one open vote. |
| `/neroevents vote yes <instance>` | Everyone | Votes yes on that event. Voting again changes your vote. |
| `/neroevents vote no <instance>` | Everyone | Votes no on that event. |
| `/neroevents claim` | Everyone | Moves the items and XP waiting in your reward crate into your inventory. What does not fit stays in the crate for next time; nothing is ever dropped on the ground. |
| `/neroevents rewards` | Everyone | Your own recent event rewards (credits, XP and items, up to the last 10) and how many reward stacks are waiting in your crate. Nobody else's rewards are ever shown. |
| `/neroevents privacy optout` | Everyone | Stops NeroEvents storing reward history under your player id. You are still rewarded; see [Privacy](Privacy.md). |
| `/neroevents privacy optin` | Everyone | Turns stored reward history back on. |
| `/neroevents privacy status` | Everyone | Shows what NeroEvents keeps about you and for how long. |
| `/neroevents privacy export` | Everyone | Writes everything NeroEvents holds about you to a file in the world folder (ask the server operator for it). Once a minute at most. |
| `/neroevents preview <definition>` | Operators | A dry run: every scheduling rule with a tick or a cross, the dimensions it could use, a sample anchor (or which protection refused one), whether it would open a vote, and load warnings. Changes nothing. |
| `/neroevents start <definition>` | Operators | Starts an event now in your dimension (the overworld from the console). Skips the weight, cooldown, calm window, caps and conditions, never opens a vote, but still obeys the kill switches and protection. |
| `/neroevents start <definition> <dimension>` | Operators | The same, in that dimension. |
| `/neroevents start <definition> <dimension> <anchor>` | Operators | The same, at that position. The position is still protection-checked; a refusal names the protection that said no. |
| `/neroevents cancel <instance>` | Operators | Calls off an event in any phase and cleans up after it. |
| `/neroevents skip` | Operators | Calls off every event that has not started yet. Running events are left alone. |
| `/neroevents skip <instance>` | Operators | Calls off that event if it has not started yet (use `cancel` once it has). |
| `/neroevents reweight <definition> <weight>` | Operators | Overrides how often the scheduler picks that event (0 to 1000; 0 means only `start` can fire it). Saved with the world. |
| `/neroevents reweight <definition> reset` | Operators | Removes the override. |
| `/neroevents zones add <name> <from> <to>` | Operators | Adds an exclusion zone between two column positions in your dimension. Names use `a-z`, `0-9`, `_` and `-`. |
| `/neroevents zones add <name> <from> <to> <dimension>` | Operators | The same, in that dimension. |
| `/neroevents zones remove <name>` | Operators | Removes a zone added by command (zones from the config file are edited there). |
| `/neroevents zones exclusions` | Operators | Lists every exclusion zone; zones from the config file are marked *(config)*. |
| `/neroevents budget` | Operators | Today's (UTC) event reward budget: the daily allowance, what has been spent, what is left and how many events have paid out today. |
| `/neroevents gallery` | Operators | Creative mode only, **in a test world**: builds the whole showcase north of you — every block and entity exhibit plus one station per event type showing it in action (a pod landing, a meteor striking a crater, debris crashing onto a wreck, a war beacon being captured, display-only raiders and a boss arena, four storm posts with a storm preview, a market board) and a demo HUD/console that cycles through every severity, for you only. Replaces the blocks in its footprint; removes loose dropped items within 64 blocks. Nothing it shows is a real event: nothing is paid, recorded or sent to another mod. The whole footprint is checked against the protection rules first; inside the spawn-protection radius or a claim it is refused and says which rule refused it, which is intended. See [the gallery](Gallery.md). |
| `/neroevents gallery exhibits` | Operators | Only the static exhibit rows (blocks, pods, war beacon, hanging meteors and debris). |
| `/neroevents gallery events` | Operators | Only the event stations, the wreck yard and the demo HUD/console. |
| `/neroevents gallery stress` | Operators | The showcase plus 40 meteors, 10 pods and an ion storm preview in view: the renderer-budget scene. Read the numbers with `/nsevrender` after ten seconds. |
| `/neroevents gallery storm <storm>` | Operators | Pins the storm preview (`solar`, `dust`, `ion`, `cryo`) or turns it `off`. A preview is sky, fog, particles and sound on your own screen only: no drain, no effects, no other player sees it. |
| `/neroevents gallery cue <station>` | Operators | Replays one station at its photo moment (`supply_drop`, `meteor_shower`, `debris_crash`, `faction_war`), pins a demo HUD scene (`hud_info`, `hud_reward`, `hud_hazard`, `hud_combat`, `hud_war`) or opens the demo console (`console`). |
| `/neroevents gallery clear` | Operators | Stand where you ran `gallery`: takes everything down (blocks to air, only the gallery's own entities) and ends the demo. |

`neroevents:debug_marker` is a built-in test event for operators: it places nothing, changes
nothing and simply runs through countdown, start and finish. `/neroevents start neroevents:debug_marker`
is a quick way to check that events work on a server.

## Client-only helpers

These are typed into your own game, not the server's command list. The capture sends ordinary operator
commands for you (`tp`, `gamerule`, `time`, `weather`, `kill`, `/neroevents gallery`), so it needs operator
permission and creative mode; `/nsevrender` needs neither.

- `/nsevgallery capture [time]` — re-shoots the gallery: pins the time (`day`, `noon`, `night`, `midnight`
  or a tick count; default `night`) and stops the weather and the day cycle (`advance_time` and
  `advance_weather` off), rebuilds `/neroevents gallery` where you stand, removes the labels and loose
  items, then takes a fixed list of screenshots into `screenshots/neroevents/`: every exhibit, every
  station at its photo moment (it sends the `gallery cue` and `gallery storm` commands itself), the four
  storm previews, every demo HUD scene and every console tab. The HUD is hidden except in the HUD and
  storm shots. Because it changes game rules and replaces blocks, **use a test world**.
- `/nsevgallery capture stress [time]` — the same over `/neroevents gallery stress`: a hero shot and the
  renderer-budget frame, after which the `/nsevrender` numbers go to chat and to
  `screenshots/neroevents/stress_budget.txt`.
- `/nsevgallery capture shot <name>` — one screenshot of the current view, HUD hidden.

  The screenshots stay on your computer. Other players are **not** hidden: if anyone else is in the
  world, their name tag and skin can appear in a frame, and the capture says so in chat. A player's
  name is personal data, and a published image cannot be taken back, so **look at every capture for
  other players' names before you add it to the repository's gallery** (the gallery workflow publishes
  every tracked screenshot to Modrinth). Best of all, capture in a world where nobody else is online.
- `/nsevrender` — how many meteors, pods, debris and war beacons were drawn per frame, how long their
  renderers took, the frame time and the effect particles spawned (averaged over the last 200 frames).
  `/nsevrender reset` starts the count again.
