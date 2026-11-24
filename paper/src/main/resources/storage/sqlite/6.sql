-- What each player did per day, so a week's or month's total is a sum over days and a new stat or period needs no
-- new table. day is the epoch day in the server's time zone when the match ended. 'played' and 'wins' are stats
-- here too, one row per match played and per match won.
CREATE TABLE player_daily_stats (
    player TEXT NOT NULL,
    game TEXT NOT NULL,
    variant TEXT NOT NULL,
    stat TEXT NOT NULL,
    day INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (player, game, variant, stat, day)
);

CREATE INDEX player_daily_stats_by_day ON player_daily_stats (day);

-- The matches played so far, from the history. The day is the match's end, ended_at being epoch milliseconds,
-- turned into the server's local day: strftime reads the local time as if it were UTC, which makes its seconds local.
INSERT INTO player_daily_stats (player, game, variant, stat, day, amount)
SELECT s.player, m.game, m.variant, s.stat, CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400, SUM(s.amount)
FROM match_stats s JOIN matches m ON m.id = s.match_id
GROUP BY s.player, m.game, m.variant, s.stat, CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400;

INSERT INTO player_daily_stats (player, game, variant, stat, day, amount)
SELECT p.player, m.game, m.variant, 'played', CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400, COUNT(*)
FROM match_players p JOIN matches m ON m.id = p.match_id
GROUP BY p.player, m.game, m.variant, CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400;

INSERT INTO player_daily_stats (player, game, variant, stat, day, amount)
SELECT p.player, m.game, m.variant, 'wins', CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400, COUNT(*)
FROM match_players p JOIN matches m ON m.id = p.match_id
WHERE p.place = 1
GROUP BY p.player, m.game, m.variant, CAST(strftime('%s', m.ended_at / 1000, 'unixepoch', 'localtime') AS INTEGER) / 86400;
