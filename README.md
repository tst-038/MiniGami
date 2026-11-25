# MiniGami

A multi-game minigame engine for [Paper](https://papermc.io). One plugin runs many minigames: spleef, king of the hill,
domination, team deathmatch, hide and seek, infection, build battle, the wall, paint and item race. Each game is a YAML file you can copy,
change and combine. Add game nights with standings, scheduled games with rewards, parties, persistent stats and a HUD,
all without writing code.

> **Status: 1.0.5.** Back up your server before installing, and please report what you run into.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [How it fits together](#how-it-fits-together)
- [Game types](#game-types)
- [Features per phase](#features-per-phase)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration files](#configuration-files)
- [Game nights](#game-nights)
- [Schedules and rewards](#schedules-and-rewards)
- [Stats and placeholders](#stats-and-placeholders)
- [Bans](#bans)
- [For plugin developers](#for-plugin-developers)
- [Building from source](#building-from-source)

## Features

- **Eight example games out of the box**, written to `games/` on first start. Every setting is commented.
- **Games from YAML.** Pick a game type, then switch features on and off per phase (kits, block breaking, void deaths,
  no hunger, effects and more). Add variants such as "3 lives" or "duos" that count towards the same stats.
- **Teams and roles.** Random teams by count or size, and roles such as seekers and hiders with their own kits and
  features.
- **Arenas.** Spawns (per team if you like), regions, and automatic map resets after each match from a saved
  WorldEdit schematic.
- **Everything editable in game.** Create and change arenas, games, variants, game nights and schedules by command,
  with click-to-fill info screens. `/mg reload` applies file edits without a restart.
- **Game nights.** A host runs a playlist of games, and points per place add up to a winner.
- **Schedules.** Open games at set times (cron), call everyone online to join, and hand out rewards by chance.
- **Parties.** Friends join games together and land in the same team.
- **Stats.** Wins, games played, kills and deaths per game and variant, stored in SQLite, with leaderboards and
  PlaceholderAPI support.
- **Safe for players.** Inventories, health and location are saved when a player joins a match and restored when they
  leave, even after a crash (`/mga player <name> snapshot restore`). Quitters can rejoin within a time limit.
- **HUD.** Sidebar, boss bar, titles, sounds and fireworks, all set in `config.yml` and overridable per game.

## Requirements

| | |
|---|---|
| Server | Paper **26.3** |
| Java | **25** |
| Optional | [WorldEdit](https://enginehub.org/worldedit) or [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/), needed for regions and map resets |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), for stats in other plugins (holograms, scoreboards) |

## Installation

1. Download `MiniGami-<version>.jar` and put it in your server's `plugins/` folder.
2. Start the server. MiniGami creates `plugins/MiniGami/` with the config, an example arena and the example games.
3. Set up the example arena (see below), and you can play.

## Quick start

The example games all play in an arena called `example`. It exists, but has no spawns yet. Stand where players should
spawn and run:

```
/mg arena example spawn add        (repeat for every spawn)
/mg arena example map save         (with a WorldEdit selection around the arena, so it resets after each match)
/mg config info                    (shows what each game still needs)
/mg join spleef
```

Some games need regions in the arena, such as `hill` for king of the hill or `plot-1`, `plot-2` and so on for build
battle. Select the area with WorldEdit, then run:

```
/mg arena example region hill set
```

Each example game says at the top of its file what its arena needs. `/mg game <id> info` shows whether a game is ready
to play and which arenas it can use.

**Make your own game:**

```
/mg game create mygame last-standing example
/mg game mygame set max-players 12
/mg game mygame variant create duos <aqua>My Game Duos
```

You can also copy one of the files in `games/`, edit it, and run `/mg reload`.

Once a match has `min-players`, it waits 20 seconds before play begins, and more players can still join. Change that
for every game under `start:` in `config.yml`, or for one game or variant with `start: { seconds: 40 }` in its file.

## How it fits together

| Term | What it is |
|---|---|
| **Game type** | The code behind a game: its phases and how it is won (for example `last-standing`). |
| **Game** | A YAML file in `games/` that sets up a game type: players, arenas, features, teams. Players join games. |
| **Variant** | A named set of changes on top of a game (`/mg join spleef duos`). Stats count it under the game. |
| **Feature** | A piece of behavior switched on per phase, such as `kit` or `void-kills`. |
| **Arena** | A place in a world with spawns, regions and an optional saved map. A game plays in any free arena it lists. |
| **Match** | One round of a game in an arena. Everything a match sets up is cleaned up when it ends. |

## Game types

| Game type | How it plays | Arena needs |
|---|---|---|
| `last-standing` | Last player or team alive wins. Supports lives. | spawns |
| `team-deathmatch` | Teams fight for kills; first to the target or most kills when time runs out. | spawns |
| `domination` | Capture points score over time; first to the target wins. King of the hill is one point. | a region per capture point |
| `hide-and-seek` | Seekers hunt hiders. Caught hiders are out, or join the seekers (infection). | spawns |
| `build-battle` | Build a random theme in your own plot (kept inside it by `play-area`), then everyone scores the builds. | `plot-1`, `plot-2`, … regions and a saved map |
| `shifting-maze` | Minotaurs hunt teams of runners through a maze whose walls shift; teams find their keys and reach the centre. | a `maze` region and a saved map; needs WorldEdit |
| `pole-duel` | Everyone stands on a pole; bridge with slabs, knock the others off, last one up wins. | a `poles` region and a saved map |
| `paint` | Paint the canvas with a brush: every block you hold is a point, painting over another team's block takes it from them. Most blocks wins. | a `canvas` region and a saved map |
| `king` | Two teams, each with a King. While the King lives, the team respawns; when the King falls, deaths are final. Last team standing wins; at time-up a living King, then more living members, then more kills. | spawns |
| `item-race` | A long scramble in a huge arena: loot the starter chests, then find the match's one random target item and right click a deposit with it. First player or team to hand it in wins; time up means nobody does. Deaths are final, PvP is on. | spawns, loot containers, a `deposit` region (any number, `deposit-…`), a `border` region if the game sets a border, and a saved map |
| `the-wall` | Teams prepare behind walls; the walls drop and a border closes in. | team spawns, `quarter-1`…`quarter-4`, `fighting` and `border` regions, `wall…` regions and a saved map |

`/mg gametype <id> info` lists a game type's phases, settings and what its arenas need.

## Features per phase

Features are set per phase under `features.<phase>` in a game file:

```yaml
features:
  playing:
    kit: { items: [diamond_shovel] }
    break-blocks: { whitelist: [snow_block], instant: true }
    void-kills: {}
```

| Feature | Does |
|---|---|
| `kit` | Gives items, or an inventory saved with `/mg kit save <name>` (see it with `/mg kit preview <name>`) |
| `break-blocks` | Limits what players break, in a phase where the game puts them in survival or creative (adventure can't break blocks): a block breaks if it is on the `whitelist` (when given) and not on the `blacklist` (neither = all, `whitelist: []` = none), optionally `instant`, `drops: false`, and `place: true` to allow placing any block |
| `give-drops` | Replaces what broken blocks drop |
| `projectile-breaks` | Projectiles break the listed blocks |
| `decay-floor` | Blocks crumble shortly after being stepped on |
| `anti-camping` | Breaks the block a player stands on for too long |
| `void-kills` | Kills players below a height |
| `no-damage` | Cancels the listed damage causes |
| `no-hunger` | Keeps hunger full |
| `no-mobs` | Players can't spawn mobs (eggs, mob buckets, breeding, golems) |
| `plot-mobs` | Players' mobs stay inside their team's region (plot) and are limited per plot (`max`, default 10); removed when the match ends |
| `one-hit` | One hit kills |
| `death-items` | What happens to items and XP on death |
| `countdown` | Counts down the last seconds of a timed phase above the hotbar |
| `freeze` | Players can't move |
| `play-area` | Keeps players inside a region: no building, breaking, moving, spreading or explosions across its edge. `region: team` is the region of each team (`plot-N`, `quarter-N`), any other name is one arena region everybody shares (`region: fighting`). `team` can't be a region name. `team-region` is the old name for `region: team` |
| `effects` | Potion effects |

`/mga options [gametype]` shows every option of a game file and of each feature, grouped by section.

## Commands

The main command is `/mg`. Typing any command without its final argument shows what can follow, so you can click your
way through. The tables below give an overview. Where a command takes `<players>`, name one player or use a selector
such as `@a[distance=..10]`; the sender hears one summary for all of them.

**Players**

| Command | |
|---|---|
| `/mg list` | Games you can join |
| `/mg join <game> [variant]` | Join a game |
| `/mg leave` | Leave your match or queue |
| `/mg stats [player] [game] [stat]` | Stats |
| `/mg leaderboard <game> [stat] [variant]` | Leaderboard |
| `/mg party invite\|accept\|leave\|kick\|disband\|list` | Parties |
| `/mg night join\|leave\|standings` | Game nights |
| `/mg schedule join` | Join the game a schedule just opened |

**Staff**

| Command | Rank |
|---|---|
| `/mg match list`, `/mg match <match> info\|kick <players>` · `/mga leave <players>` (out of whatever match they are in) | helper |
| `/mg match <match> start\|end\|phase <phase>\|pause\|resume\|stop\|add <players>` | mod |
| `/mg start` (start your match, or the first one waiting, without waiting for more players) | mod |
| `/mga ban <players> [scope] [length] [reason...]` · `/mga unban <players> [scope]` (also an offline name) · `/mga bans [player]`; see [Bans](#bans) | ban (mod) |
| `/mg schedule list` · `/mg schedule now\|next <schedule> [game] [variant]`, `start\|skip\|cancel\|pause\|resume <schedule>` | helper · mod |
| `/mg night start <night>`, `begin`, `next`, `skip`, `pause`, `resume`, `end`, `add <players>` (a selector passes staff over), `team <players> <team>`, `kick <players>` | host |
| `/mg arena list\|create <id>` · `/mg arena <arena> info\|tp\|spawn\|region\|map` | admin |
| `/mg game list\|create <id> <gametype> <arena>` · `/mg game <game> info\|set\|unset\|variant\|delete` | admin |
| `/mg gametype list\|<id> info` · `/mga options [gametype]` | admin |
| `/mg kit list\|save <name>\|delete <name>` · `/mg kit preview <name>` (look without taking) · `/mg kit get <name>` (into an empty inventory, to change and save again) | admin |
| `/mg night list\|info\|create\|delete\|edit` | admin |
| `/mg schedule info\|create <id> <game> <when>\|delete\|edit` | admin |
| `/mg lobby set\|tp` · `/mg config info\|set\|unset <key> [value]` | admin |
| `/mga messages set <key> <text>\|unset <key>\|info <key>` (any message, `prefix` included; `unset` goes back to the bundled text) | admin |
| `/mga player <player> snapshot [restore]` | admin |
| `/mg join <game> <players> [variant]` · `/mg leave <players>` (helper) · `/mg schedule join <schedule> <players>` · `/mg reload` · `/mg doctor [game\|arena]` · `/mg debug` | admin |

Where a command takes players (`join`, `leave`, `schedule join`, `match <match> add\|kick`, `night add\|kick\|team`), a
name picks one player and a selector such as `@a` or `@a[distance=..30]` picks several. Commands that add players pass
over staff (`minigami.helper` or `minigami.host`) a selector picks, so `@a` doesn't bring in the host or the admins;
add one by name to bring them in.

## Troubleshooting: `/mg doctor`

A game only plays in the arenas listed by it that are set up for it. An arena that isn't is left out (no error, since it
may still be being set up), and `/mg doctor` says why:

- `/mg doctor`: every game, whether it is ready, and what keeps it out of its arenas; also files that didn't load.
- `/mg doctor <game>`: one game and its variants. `/mg doctor <arena>`: one arena, for every game that lists it.

Each line names the arena, the game, the reason and, when a command fixes it, that command (click to fill it in), e.g.
`example can't host the-wall: missing region quarter-3 (/mg arena example region quarter-3 set)`.

| Reason | Fix |
|---|---|
| no spawns | `/mg arena <arena> spawn add` |
| missing region the game needs | `/mg arena <arena> region <region> set` |
| no region of a group (e.g. `wall`, met by `wall-1` or `wall-north`) | `/mg arena <arena> region <group>-1 set` |
| missing region per team (e.g. `quarter-1` up to `quarter-4`) | `/mg arena <arena> region <region> set` |
| changes blocks but no saved map | `/mg arena <arena> map save` |
| changes blocks but WorldEdit isn't installed | install WorldEdit or FastAsyncWorldEdit |
| fewer spawns than teams (a warning: teams share spawns) | `/mg arena <arena> spawn add` |

When a join fails, players hear whether the game is busy (all its arenas are in use), not ready (no arena is set up for
it) or can't be played (its file didn't load); staff with `minigami.admin` also get the reasons. Startup and
`/mg reload` log the findings to the console, each only once until it changes.

## Permissions

| Permission | Default | Gives |
|---|---|---|
| `minigami.admin` | op | Set up arenas, games, kits and config, reload, restore players; includes `mod` |
| `minigami.mod` | op | Start, stop and change matches, run game nights; includes `helper`, `host` and `ban` |
| `minigami.helper` | op | List and inspect matches, kick players from matches and game nights |
| `minigami.host` | op | Run game nights |
| `minigami.ban` | op | Ban players with `/mga ban`, `/mga unban` and `/mga bans` |
| `minigami.play.<game>` | everyone (`minigami.play.*`) | Join that game |
| `minigami.night.join.<night>` | everyone (`minigami.night.join`) | Join that game night and hear when it opens |
| `minigami.schedule.join.<schedule>` | everyone (`minigami.schedule.join`) | Hear and join that schedule's games, whatever `minigami.play.<game>` says |

To keep one schedule or night for a rank, set its own permission to false for everyone and true for that rank,
for instance `minigami.schedule.join.hour-game-solo`.

## Configuration files

```
plugins/MiniGami/
├── config.yml              lobby, HUD (sidebar, boss bar, titles, sounds), match start timer, parties, leaderboards
├── messages/en.yml         every message players see (MiniMessage)
├── arenas/<id>/arena.yml   world, spawns, regions, waiting spot (+ saved map)
├── games/<id>.yml          one file per game, with its variants
├── gamenights/<id>.yml     game night playlists
├── schedules/<id>.yml      scheduled games and their rewards
├── kits/                   inventories saved with /mg kit save
└── stats.db                stats (SQLite)
```

The file name is the id. Files are checked on load, and problems name the file and key. `/mg config info` lists every
game, arena, night and schedule with its status. `/mg reload` applies changes, and running matches keep their old
settings until they end.

Every message can also be changed in game: `/mga messages set prefix <gold>[Games]</gold>` (MiniMessage or `&` colour codes),
`/mga messages info <key>` shows the text as written and as players see it, and `/mga messages unset <key>` brings back the
bundled text. Keys complete as you type. The file keeps its comments, and the messages reload at once.

The example files are only written on the first start, when their folder doesn't exist yet. After that they are yours
to change or delete.

## Game nights

A game night is a playlist the host runs for everyone who joins. Each game gives points per place, and the totals pick
a winner. Players can play solo or in night teams.

```yaml
name: "<gold>Friday Night"
teams: solo                       # or { size: 2 }
points: { places: { 1: 10, 2: 7, 3: 5 }, taking-part: 1 }
playlist:
  - spleef
  - king-of-the-hill
  - { game: spleef, variant: lives, points: { places: { 1: 20, 2: 14, 3: 10 } } }
```

Run it with `/mg night start <id>`, then `begin` and `next`. Players join with `/mg night join`; `add <player>` signs up
one player whatever their permission; `add @a` (any selector, such as `@a[distance=..30]`) signs up those it picks who
may join, except staff (`minigami.helper` or `minigami.host`), who join themselves or are added by name.

## Schedules and rewards

A schedule opens a game at set times and sends everyone online a clickable call to join.

```yaml
name: "<gold>Friday games"
when: "0 20 * * 5"                # cron: Fridays at 20:00
pick: rotate                      # or random
games: [spleef, king-of-the-hill, { game: spleef, variant: decay }]
join-seconds: 120
rewards:
  places:
    1:
      - { chance: 50, item: diamond, amount: 3 }
      - { chance: 50, command: "eco give %player% 100", name: "<gold>100 coins" }
      - { chance: 10, saved: crate-key, amount: 2, name: "<aqua>2 crate keys" }
  taking-part:
    - { chance: 25, item: cookie, amount: 4 }
  draw: team                      # or player
```

A reward is an `item` (with an `amount`), a `command` (run from the console, `%player%` is the winner; it needs a `name`)
or a `saved` item: one saved with `/mga loot save <name>`, for what `item` can't describe, such as a crate key or an item
with its own name and enchantments. A saved item takes an `amount` and a `name` too, is given as it is when the reward is
won, and a name with no saved item is a config problem. Items that don't fit in the inventory drop at the winner's feet.

Each place draws at most one reward. Chances are percentages, and whatever is left over to 100% wins nothing. Players
who leave early win nothing. The last place always draws from `taking-part`, even when it has a list of its own, unless
everyone shares one place. With `draw: team` (the default) a team shares one draw, so its members all win the same;
with `draw: player` each member draws their own.

Staff steer schedules by hand (all mod commands, under `/mg schedule`):

| Command | |
|---|---|
| `next <schedule> [game] [variant]` | Open this game at the next time, once, instead of the schedule's own pick; it may be any game. Without a game, go back to the schedule's own turns. |
| `now <schedule> [game] [variant]` | Open its next game (or the one given) right now, however few players are online (`min-online` and the game's minimum players don't apply); only busy arenas stop it. Its times stay as they are. |
| `start <schedule>` | Start the game it opened now, however few players joined. Without this, a call that closes with too few players is called off. |
| `skip <schedule>` | Its next time opens nothing. `resume` takes that back. |
| `cancel <schedule>` | Call off the game it opened that hasn't started yet. |
| `pause` / `resume <schedule>` | Stop and restart opening games, until the server restarts. |

Any waiting match, scheduled or not, also starts with `/mg match <match> start` or `/mg start`.

## Stats and placeholders

MiniGami keeps `wins`, `played`, `kills` and `deaths` per game and per variant. Check them with `/mg stats` and
`/mg leaderboard`.

With PlaceholderAPI installed:

| Placeholder | |
|---|---|
| `%mg_stat_<game>[_<variant>]_<stat>%` | The viewer's own amount |
| `%mg_weekly_stat_<game>[_<variant>]_<stat>%` / `%mg_monthly_stat_…%` | The viewer's own amount this week (from Monday) / this month |
| `%mg_top_<game>[_<variant>]_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard |
| `%mg_weekly_top_<game>[_<variant>]_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this week (from Monday), in the server's time |
| `%mg_monthly_top_<game>[_<variant>]_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this month |
| `%mg_match_<value>%` | A value of the viewer's match, such as `%mg_match_time%` |
| `%mg_match_<role>%` / `%mg_match_<role>-out%` | Members of a role of the game who are alive / out, such as `%mg_match_hider%` and `%mg_match_hider-out%` |
| `%mg_night_<value>%` | A value of the running game night, such as `%mg_night_place%` |

Without a variant, all of a game's variants are added up.

## Bans

`/mga ban <players> [scope] [length] [reason...]` keeps players out of part of the server, for a length like `30m`, `2h`,
`7d` or `1d12h`, or for good without one. The scope is `all` (everything below), `nights` (game nights and the games
schedules open), `game:<game>` (one game), or one another plugin registered, such as `koth`; without a scope the ban is
from `all`. The reason is free text after the scope or length, e.g. `/mga ban Steve game:spleef 7d griefing`. Banning again
in a scope replaces the ban there. `/mga unban <player> [scope]` lifts one scope or all of a player's bans, also of a player
who is offline, and `/mga bans [player]` lists the bans that have not ended.

A banned player can't join by any way in, a party, a game night, a schedule's call or staff adding them, and hears the
time left and the reason; a party leader hears it for their members. A player who is in a match or night the ban covers
is taken out at once, or when they log in if they were offline.

## For plugin developers

MiniGami saves a player's own state (inventory, health, location) when it takes them over and gives it back
afterwards. A player is **in a match** from the moment MiniGami takes them over, until their own state is back: waiting
for a match (also in place), playing, eliminated, spectating, and the whole of a game night including the breaks
between games. Anything you give a player in that span is lost, so check first or wait for the leave event.

Depend on the api (`compileOnly("io.github.tst-038:minigami-api:<version>")`; every release is on
[GitHub Packages](https://github.com/tst-038?tab=packages&repo_name=MiniGami), add
`maven("https://maven.pkg.github.com/tst-038/MiniGami")` with a GitHub token that can read packages) and list
it in your `paper-plugin.yml` under `dependencies: server: MiniGami: { load: BEFORE, required: false, join-classpath: true }`
(`join-classpath` lets your plugin see MiniGami's classes; or `softdepend: [MiniGami]` in `plugin.yml`), and guard your
code for it being absent. The types are in `io.github.tst_038.minigami.api.presence`:

- `MiniGamiPlayers`, a Bukkit service: `isInMatch(UUID)`
- `MiniGamiPlayerJoinEvent`: MiniGami took the player over (once, even when a game night moves them between games)
- `MiniGamiPlayerLeaveEvent`: the player has their own state back, so it is safe to give items here; fired exactly
  once per join, also when a match is stopped, a player is kicked, or the plugin is disabled. Players who quit for good get it
  during their quit. A player who quit with time to rejoin still counts as in the match (rejoining is not a new
  join); the event fires when their time runs out or the match ends, usually while they are offline, so
  `getPlayer()` is empty and you should use `getPlayerId()`.

```java
var players = getServer().getServicesManager().load(MiniGamiPlayers.class); // null without MiniGami

// Don't pick players who are in a match
var candidates = getServer().getOnlinePlayers().stream()
        .filter(p -> players == null || !players.isInMatch(p.getUniqueId())).toList();

// Hold a reward until the player is out
@EventHandler
public void onLeave(MiniGamiPlayerLeaveEvent event) {
    event.getPlayer().ifPresent(player -> giveHeldReward(player));
}
```

Both events run synchronously on the server thread. After a server crash, the saved state is given back at the
player's next login without a leave event, since no join was seen since the restart.

### Rewards

To read the same `saved:` items in your own config, the `io.github.tst_038.minigami.api.loot` package has
`MiniGamiSavedItems`, another Bukkit service: `find(name)` gives a copy of the item saved with `/mga loot save <name>`,
`has(name)` whether there is one. Items can be saved and deleted while the server runs, so look one up when you need it.
`save(name, item)` saves one of an item the way `/mga loot save` does, replacing one of that name (names are lower-case letters,
digits, `_` and `-`, see `isValidName`); it can be found at once, and the returned future completes, off the server thread,
once its file is written. Prefix the names with your plugin's, as they share one folder with every other saved item.
`io.github.tst_038.minigami.api.reward.RewardReader` reads reward entries (`{ saved: crate-key }`, `{ item: diamond }`,
`{ command: ..., name: ... }`) into `Reward`s you can `give(player)`, which throws a `RewardFailedException` when it
couldn't be given (a saved item since deleted, a command that failed); create it with the service:
`new RewardReader(getServer().getServicesManager().load(MiniGamiSavedItems.class))`.
`io.github.tst_038.minigami.api.item.ItemLook` reads how an item looks from the keys next to it, `glow: true` (the
enchantment glint without an enchantment, `false` hides it), `item-model: <key>` (another item's model or one from a
resource pack, such as `myserver:ruby`) and `custom-model-data: <number>`, and `applyTo(item)` puts it on a stack, on
top of a saved item too. Loot table items and saved items take the same keys.

### Keeping stats outside matches

`MiniGamiStats`, a Bukkit service in `io.github.tst_038.minigami.api.stat` (a stat is
`io.github.tst_038.minigami.core.stat.Stat`), lets your plugin keep stats that show up in MiniGami's stats commands,
leaderboards (all time, weekly and monthly) and `%mg_...%` placeholders. Pick a game id no MiniGami game uses (lower
case letters, digits and dashes) and call it from the server thread. Amounts are added as of today, negative ones take
away, and `played` and `wins` are MiniGami's own and refused. Storing happens in the background and failures are
logged by MiniGami. Profiles of players who are online and the leaderboards load the change again on the next server tick, in the background.

Give each stat a display name in MiniGami's `messages/en.yml`, as `stats.name.<stat>` (`seconds-on-hill` in the
example below). Games that were removed from the configuration but still have stored stats appear in `/mg stats`.

```java
var stats = getServer().getServicesManager().load(MiniGamiStats.class); // null without MiniGami
if (stats != null) {
    stats.add("koth", Map.of(player.getUniqueId(), Map.of(new Stat("seconds-on-hill"), 1L)));
}
```

### Setting up regions

`MiniGamiSetup`, a Bukkit service in `io.github.tst_038.minigami.api.setup`, gives your plugin the tools MiniGami's
arena commands use, for regions you keep in your own config: `selection(player, id)` is the box around the player's WorldEdit
selection as a `Region` (empty without WorldEdit, `hasWorldEdit()` tells it from nothing being selected), `select(player, region)`
makes a region their selection, and `show(player, region)` outlines it in particles for them alone for a few seconds. Write
what you save with `Region.toConfig()` and `ConfigLocations.write(location)`; they read back with `Region.read` and `ConfigLocations.read`.
`ConfigFiles.update(path, change)`, in `io.github.tst_038.minigami.api.config`, changes a YAML file the way MiniGami's own commands
do: it keeps the comments, writes through a temp file and makes one change at a time. It is blocking IO, so run it off the server thread.
`DyedBlocks.scan(region, DyedBlocks.DYED)` (or `DyedBlocks.GLASS`, or any `Predicate<Material>`), in `io.github.tst_038.minigami.api.region`,
remembers a region's dyeable blocks once; `paint(DyeColor)` colours those, keeping their facing, and `restore()` puts them back as they were. Use it on the server thread.

```java
var setup = getServer().getServicesManager().load(MiniGamiSetup.class); // null without MiniGami
setup.selection(player, "hill").ifPresentOrElse(hill -> {
    getConfig().set("hill", hill.toConfig());
    getConfig().set("spawn", ConfigLocations.write(player.getLocation()));
    saveConfig();
    setup.show(player, hill);
}, () -> player.sendMessage(setup.hasWorldEdit() ? "Select the hill first." : "Install WorldEdit."));
```

### Commands that edit your files

`io.github.tst_038.minigami.api.config` and `api.command` give your plugin the `set`/`unset`/`info` commands MiniGami has,
for its own config and messages, so admins set everything up in game the same way everywhere:

- `FileEditor(folder, messages, fileIo, serverThread, reload, logger)` is the one write path: `edit(sender, file, change, check)`
  changes the file off the server thread, runs your loader's `check` on the server thread, and writes it (comments kept) only
  when it has no problems; `editAndReload(...)` then tells the sender and calls your reload. A change throws
  `FileEditor.Refusal(messageKey, tags...)` to refuse with a message of yours, or `IllegalArgumentException` to show a problem.
  `set(sender, file, key, text, check)` reads the text as YAML (`5`, `[a, b]`, `{ y: 0 }`, quotes for text, `here` for the
  player's location), makes a single value a list of one where your loader wants a list, and `unset` takes a key out.
- `EditableFile(fileEditor, "config.yml", check)` is one file to edit by command; hand it each load with
  `loaded(config, YamlFiles.keys(config, reader), problems)` so keys and values complete from what your `ConfigReader` asked for.
- `MessageEditor(fileEditor, messages, "messages/en.yml", bundled)` sets, unsets and shows texts of your messages file; only keys
  it has can be set.
- `EditCommands.config("config", file, "/myplugin config set ")` and `EditCommands.messages("messages", messageEditor,
  "/myplugin messages set ")` are the Brigadier nodes; add your permission with `.requires(...)`. `EditCommands.set`/`unset` build
  the bare nodes for files of your own kind.

Your `messages` need the keys these send: `edit.invalid`, `edit.problem`, `edit.save-failed`, `edit.here-needs-player`,
`edit.bad-value`, `edit.set`, `edit.unset`, `info.config`, `info.value`, `info.value-inherited`, and for messages
`edit.message-unknown`, `edit.message-set`, `edit.message-unset`, `edit.message-default`, `edit.message-text` and
`edit.message-preview`; MiniGami's `messages/en.yml` shows their placeholders.

```java
var files = new FileEditor(getDataPath(), messages, task -> getServer().getAsyncScheduler().runNow(this, t -> task.run()),
        task -> getServer().getGlobalRegionScheduler().execute(this, task), this::reload, getLogger());
var config = new EditableFile(files, "config.yml", yaml -> MyConfigLoader.load(yaml).problems());
commands.then(EditCommands.config("config", config, "/myplugin config set ").requires(admin))
        .then(EditCommands.messages("messages", new MessageEditor(files, messages, "messages/en.yml", bundled),
                "/myplugin messages set ").requires(admin));
```

### Commands that act on several players

`PlayerTargets` in `io.github.tst_038.minigami.api.command` gives your commands MiniGami's way with players: take the
argument from `PlayerTargets.argument()` (a name or a selector), read it with `PlayerTargets.resolve(context, name)`, and
act with `PlayerTargets.each(messages, sender, players, action, noneKey, summaryKey, tags...)`. The action does it for one
player and tells the audience it is given how it went; with one player that is the sender, with several it is silent and the
sender hears `summaryKey` once, with `%count%` and `%total%`. With none, the sender hears `noneKey`.

### Bans

`MiniGamiBans`, a Bukkit service in `io.github.tst_038.minigami.api.ban`, shares the bans of [`/mga ban`](#bans) with your
plugin. `register(plugin, scope)` adds a scope players can be banned from (lower-case letters, digits, `_` and `-`; not
`all` or `nights`); register it when your plugin enables, and it goes when your plugin is disabled. `ban(player, scope)`
is the ban that keeps the player out of that scope, a ban from `all` too, or empty; it reads memory, so check it on
every way into your activity. `bans()` lists those that have not ended. `ban(...)` and `unban(player, scope)` change bans
and return futures that complete on the server thread once storage has them. `MiniGamiPlayerBannedEvent` fires on the
server thread when a ban is made; `getBan().covers(scope)` says whether it is one for you, so take the player out then.

```java
var bans = getServer().getServicesManager().load(MiniGamiBans.class); // null without MiniGami
bans.register(this, "koth");

// On each way in
var ban = bans.ban(player.getUniqueId(), "koth");
if (ban.isPresent()) {
    player.sendMessage("Banned from KOTH: " + ban.get().reason().orElse("no reason"));
    return;
}

@EventHandler
public void onBanned(MiniGamiPlayerBannedEvent event) {
    if (event.getBan().covers("koth")) {
        event.getPlayer().ifPresent(this::sendOut);
    }
}
```

### Showing a sidebar

MiniGami's matches and game nights set players' scoreboards too, so don't call `player.setScoreboard` for a sidebar of your own:
the boards would overwrite each other and give back stale ones. `MiniGamiBoards`, a Bukkit service in
`io.github.tst_038.minigami.api.board`, owns it. `claim(plugin, player, board, priority)` shows your board and returns a
`BoardClaim`; `release()` gives it up. A player sees the claim with the highest `BoardPriority` (`MATCH`, `NIGHT`, then
`PLUGIN`, the one to use), of equal ones the latest. When a claim is released the next one shows, and when none is left the
player gets the board they had before the first claim. A player's claims are dropped when they quit and a plugin's when it is
disabled. Call it on the server thread.

```java
var boards = getServer().getServicesManager().load(MiniGamiBoards.class); // null without MiniGami
var claim = boards.claim(this, player, myBoard, BoardPriority.PLUGIN);
// ... later, when the player leaves your area:
claim.release();
```

### Menus

`io.github.tst_038.minigami.api.menu` makes paged chest menus, such as a shop. A `Menu` is always a double chest: the top
five rows hold your items (45 a page), the bottom row is the bar, with a previous-page arrow in its first slot and a
next-page arrow in its last, each only where there is a page to go to, and an item of yours in the middle. Implement
`MenuContent`: the `title()`, the `items()`, the `centre()` item, the two arrows for a page (counted from 0) and the
page count, and optionally a `filler()` for the rest of the bar and `clickEmpty` for clicks on empty slots. You word
everything, page numbers included. A `MenuItem` has an icon, a click handler that gets a `MenuClick` (player, click
type, menu and `Place`), and optionally a `Place` (page and slot 0-44); items without one fill the next free slot.
`Menu.open(player, content)` opens it, `refresh()` asks the content again and redraws it in place (after a purchase,
say), `page(n)` turns the page, and `Menu.of(player)` finds the menu a player has open. MiniGami cancels every click
and drag in a menu, shift-clicks from the player's own inventory too, and hands the clicks on, so menus need MiniGami
enabled. Use them on the server thread.

```java
Menu.open(player, new MenuContent() {
    public Component title() { return Component.text("Shop"); }
    public List<MenuItem> items() { return List.of(MenuItem.of(ItemStack.of(Material.DIAMOND), click -> buy(click.player()))); }
    public Optional<MenuItem> centre() { return Optional.empty(); }
    public ItemStack previous(int page, int pages) { return arrow("Page " + page + "/" + pages); }
    public ItemStack next(int page, int pages) { return arrow("Page " + (page + 2) + "/" + pages); }
});
```

## Building from source

Requires Java 25. The Gradle wrapper downloads everything else.

```bash
./gradlew build
```

The plugin jar ends up in `paper/build/libs/`. `./gradlew runServer` starts a local Paper test server with the plugin.
`./gradlew publishToMavenLocal` publishes `minigami-api` and `minigami-core` for other plugins to compile against, and
`minigami-plugin`, the plugin jar, for their test servers. Publishing a GitHub release puts the same on GitHub Packages.

The project is split into modules: `core` (game logic, no server code), `api` (what games and features build on),
`features`, `stats`, `gamenight`, the `games/*`, and `paper` (the plugin itself).
