# MiniGami

A multi-game minigame engine for [Paper](https://papermc.io). One plugin runs many minigames: spleef, king of the hill,
domination, team deathmatch, hide and seek, infection, build battle and the wall. Each game is a YAML file you can copy,
change and combine. Add game nights with standings, scheduled games with rewards, parties, persistent stats and a HUD,
all without writing code.

> **Status: beta (0.2.1).** Things work, but expect rough edges. Back up your server before installing, and please
> report what you run into.

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
  leave, even after a crash (`/mg player <name> restore`). Quitters can rejoin within a time limit.
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
| `kit` | Gives items, or an inventory saved with `/mg kit save <name>` |
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

`/mg feature list` and `/mg feature <id> info` show every feature with all its settings.

## Commands

The main command is `/mg`. Typing any command without its final argument shows what can follow, so you can click your
way through. The tables below give an overview.

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
| `/mg match list`, `/mg match <match> info\|kick <player>` | helper |
| `/mg match <match> start\|end\|phase <phase>\|pause\|resume\|stop\|add <player>` | mod |
| `/mg start` (start your match, or the first one waiting, without waiting for more players) | mod |
| `/mg schedule list` · `/mg schedule now\|pause\|resume <schedule>` | helper · mod |
| `/mg night start <night>`, `begin`, `next`, `skip`, `pause`, `resume`, `end`, `team`, `kick` | host |
| `/mg arena list\|create <id>` · `/mg arena <arena> info\|tp\|spawn\|region\|map` | admin |
| `/mg game list\|create <id> <gametype> <arena>` · `/mg game <game> info\|set\|unset\|variant\|delete` | admin |
| `/mg gametype list\|<id> info` · `/mg feature list\|<id> info` | admin |
| `/mg kit list\|save <name>\|delete <name>` | admin |
| `/mg night list\|info\|create\|delete\|edit` | admin |
| `/mg schedule info\|create <id> <game> <when>\|delete\|edit` | admin |
| `/mg lobby set\|tp` · `/mg config info` | admin |
| `/mg player <player> snapshot\|restore` | admin |
| `/mg join <game> [variant] <player>` · `/mg reload` · `/mg doctor [game\|arena]` · `/mg debug` | admin |

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
| `minigami.mod` | op | Start, stop and change matches, run game nights; includes `helper` and `host` |
| `minigami.helper` | op | List and inspect matches, kick players from matches and game nights |
| `minigami.host` | op | Run game nights |
| `minigami.play.<game>` | everyone (`minigami.play.*`) | Join that game |
| `minigami.night.join` | everyone | Join game nights |
| `minigami.schedule.join` | everyone | Join scheduled games, whatever `minigami.play.<game>` says |

## Configuration files

```
plugins/MiniGami/
├── config.yml              lobby and HUD (sidebar, boss bar, titles, sounds)
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

Run it with `/mg night start <id>`, then `begin` and `next`. Players join with `/mg night join`.

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
  taking-part:
    - { chance: 25, item: cookie, amount: 4 }
```

Each place draws at most one reward. Chances are percentages, and whatever is left over to 100% wins nothing. Players
who leave early win nothing.

## Stats and placeholders

MiniGami keeps `wins`, `played`, `kills` and `deaths` per game and per variant. Check them with `/mg stats` and
`/mg leaderboard`.

With PlaceholderAPI installed:

| Placeholder | |
|---|---|
| `%mg_stat_<game>[_<variant>]_<stat>%` | The viewer's own amount |
| `%mg_top_<game>[_<variant>]_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard |
| `%mg_match_<value>%` | A value of the viewer's match, such as `%mg_match_time%` |
| `%mg_match_<role>%` / `%mg_match_<role>-out%` | Members of a role of the game who are alive / out, such as `%mg_match_hider%` and `%mg_match_hider-out%` |
| `%mg_night_<value>%` | A value of the running game night, such as `%mg_night_place%` |

Without a variant, all of a game's variants are added up.

## For plugin developers

MiniGami saves a player's own state (inventory, health, location) when it takes them over and gives it back
afterwards. A player is **in a match** from the moment MiniGami takes them over, until their own state is back: waiting
for a match (also in place), playing, eliminated, spectating, and the whole of a game night including the breaks
between games. Anything you give a player in that span is lost, so check first or wait for the leave event.

Depend on the plugin jar (`compileOnly`, for example `compileOnly(files("libs/MiniGami-<version>.jar"))`) and list
it in your `paper-plugin.yml` under `dependencies: server: MiniGami: { load: BEFORE, required: false }` (or
`softdepend: [MiniGami]` in `plugin.yml`), and guard your code for it being absent. The types are in
`io.github.tst_038.minigami.api.presence`:

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

## Building from source

Requires Java 25. The Gradle wrapper downloads everything else.

```bash
./gradlew build
```

The plugin jar ends up in `paper/build/libs/`. `./gradlew runServer` starts a local Paper test server with the plugin.

The project is split into modules: `core` (game logic, no server code), `api` (what games and features build on),
`features`, `stats`, `gamenight`, the `games/*`, and `paper` (the plugin itself).
