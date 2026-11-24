# MiniGamiKoth

King of the Hill for [Paper](https://papermc.io), in a world of its own. On a schedule, one hill opens in the KOTH
world. Whoever stands on it alone for a few seconds takes it and earns points for as long as they hold it. Spend the
points in a shop (`/koth shop`) on items, saved items such as crate keys, or console commands such as a permission.

It is a plugin of its own that builds on [MiniGami](../README.md): kills, deaths, captures and points show up in
MiniGami's stats, leaderboards and placeholders under the game `koth`.

## Contents

- [Requirements and installation](#requirements-and-installation)
- [Setting it up](#setting-it-up)
- [config.yml](#configyml)
- [Schedule examples](#schedule-examples)
- [shop.yml](#shopyml)
- [Messages](#messages)
- [Commands](#commands)
- [Permissions](#permissions)
- [Placeholders](#placeholders)

## Requirements and installation

| | |
|---|---|
| Server | Paper **26.3**, Java **25** |
| Required | MiniGami, which MiniGamiKoth loads after and uses for stats, saved items and to know who is in a match |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), for `%koth_...%` |

1. Put `MiniGami-<version>.jar` and `MiniGamiKoth-<version>.jar` in `plugins/`.
2. Start the server. MiniGamiKoth creates `plugins/MiniGamiKoth/` with `config.yml`, `shop.yml` and `messages/en.yml`.
3. Set up the world and the hill (below), then run `/kotha reload`.

Players who are in a MiniGami match can't join KOTH, shop or receive purchases; the match comes first.

## Setting it up

1. Have a world folder for KOTH, for example `koth`, next to your other worlds. MiniGamiKoth loads it if it isn't
   loaded already. It never generates a world: a name with no folder is reported as a problem.
2. In `config.yml`, set `world.name`, the `spawn` where `/koth join` puts players, and the `point.region` of the hill.
3. Run `/kotha reload`. Problems are listed in chat and in the log. A file with problems isn't used, and the previous
   one stays in use.
4. Try it with `/kotha start 5` (a five minute round, whatever the timetable and restrictions say), and `/kotha stop`.

## config.yml

| Section | What it sets |
|---|---|
| `world` | The `name` of the world, the `spawn` for `/koth join` (left out: the world's spawn), and `respawn-here`: players who die in the world come back at that spawn |
| `schedule` | When it runs, as `cron` or `cycle` (see below), and `warn-minutes`: chat warnings that long before a start |
| `point` | The hill's `region`, `capture-seconds` to take it, and optional `colour-blocks` that change colour on each capture |
| `scoring` | `points` for the holder every `every-seconds`, wherever they are in the world |
| `restrictions` | `min-online`: a start is skipped, with an announcement, while fewer players are online. A forced start ignores it |
| `sidebar` | The title and lines shown to players in the KOTH world, for when it runs and when it doesn't |

A holder who leaves the world or the server drops the hill. The round that is running keeps the config it started
with, so a reload changes the next round.

## Schedule examples

Three times a week for an hour, in server time (`minute hour day-of-month month day-of-week`):

```yaml
schedule:
  type: cron
  when:
    - "0 20 * * 1"   # Mondays 20:00
    - "0 20 * * 3"   # Wednesdays 20:00
    - "0 18 * * 6"   # Saturdays 18:00
  length-minutes: 60
  warn-minutes: [60, 15, 2]
```

Around the clock, 15 minutes on and 5 minutes off, counted from midnight:

```yaml
schedule:
  type: cycle
  active-minutes: 15
  break-minutes: 5
  warn-minutes: [2]
```

## shop.yml

The shop is a chest menu. Each entry has a `slot`, a `cost` in points, an `icon`, and `rewards`, all of which are
given on purchase:

- `{ item: diamond, amount: 8 }`, an item;
- `{ saved: <name>, amount, name }`, an item saved in MiniGami with `/mga loot save <name>`, such as a crate key;
- `{ command: "...", name: "..." }`, run from the console, with `%player%` being the buyer.

An entry with `owned-permission` can't be bought again by someone who has that permission. Points are taken first,
and given back if the rewards can't be delivered. Run `/kotha reload` after editing.

## Messages

Every text is in `plugins/MiniGamiKoth/messages/en.yml`, in [MiniMessage](https://docs.advntr.dev/minimessage/) format.
Keys you remove fall back to the bundled text. Use `%prefix%` for the shared prefix.

## Commands

For players, with `minigami.koth.play`:

| Command | |
|---|---|
| `/koth` | Whether KOTH is running, who holds the hill and how long is left, or when it next starts |
| `/koth shop` | Open the shop |
| `/koth balance` | Your points |
| `/koth join` | Go to the KOTH world's spawn; not while in a MiniGami match |

For staff, with `minigami.koth.admin`:

| Command | |
|---|---|
| `/kotha reload` | Read `config.yml`, `shop.yml` and the messages again |
| `/kotha start [minutes]` | Start a round now, for the given length or the timetable's, whatever the restrictions say |
| `/kotha stop` | End the running round |
| `/kotha status` | State, holder, who is taking the hill, next start, players online, and whether the config and shop are loaded |
| `/kotha points show <player>` | A player's points |
| `/kotha points give <player> <amount>` | Give points |
| `/kotha points take <player> <amount>` | Take points, down to zero at the lowest |

The player must have been on the server before; names are looked up from the server's user cache.

## Permissions

| Permission | Default | |
|---|---|---|
| `minigami.koth.play` | everyone | `/koth`, the shop, your points and going to the KOTH world |
| `minigami.koth.admin` | op | `/kotha`: reload, start and stop, and giving and taking points |

## Placeholders

With PlaceholderAPI installed, MiniGamiKoth's own:

| Placeholder | |
|---|---|
| `%koth_active%` | `true` while a round runs, otherwise `false` |
| `%koth_holder%` | Name of the holder, empty if none |
| `%koth_time_left%` | Time left in the round, empty while it isn't running |
| `%koth_next_start%` | When it next starts, empty if nothing is planned |
| `%koth_next_in%` | How long until then |
| `%koth_balance%` | The viewer's points to spend |

KOTH's stats are `points` (everything earned on the hill, spent or not), `kills`, `deaths` and `captures`. They live
in MiniGami, so use its placeholders with the game `koth`:

| Placeholder | |
|---|---|
| `%mg_stat_koth_<stat>%` | The viewer's own amount |
| `%mg_weekly_stat_koth_<stat>%` / `%mg_monthly_stat_koth_<stat>%` | The viewer's amount this week (from Monday) / this month |
| `%mg_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard |
| `%mg_weekly_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this week |
| `%mg_monthly_top_koth_<stat>_<place>_name%` / `…_value%` | A place on the leaderboard of this month |

For example `%mg_weekly_top_koth_captures_1_name%` is who took the hill most often this week.
