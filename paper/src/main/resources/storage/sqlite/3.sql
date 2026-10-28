-- Stats count per game and its variant. Until now a match stored its game type as "game" and the game as "mode".
-- The variant is '' for the game itself, so it can be part of a primary key.
ALTER TABLE matches RENAME COLUMN game TO gametype;
ALTER TABLE matches RENAME COLUMN mode TO game;
ALTER TABLE matches ADD COLUMN variant TEXT NOT NULL DEFAULT '';

-- Running totals per player, game and variant; a game's totals are the sum over its variants.
CREATE TABLE player_variant_totals (
    player TEXT NOT NULL,
    game TEXT NOT NULL,
    variant TEXT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    -- Over the matches they got a place in, so place_sum / placed is their average place.
    placed INTEGER NOT NULL,
    place_sum INTEGER NOT NULL,
    left_matches INTEGER NOT NULL,
    seconds_played INTEGER NOT NULL,
    PRIMARY KEY (player, game, variant)
);

INSERT INTO player_variant_totals (player, game, variant, played, wins, placed, place_sum, left_matches, seconds_played)
SELECT player, mode, '', SUM(played), SUM(wins), SUM(placed), SUM(place_sum), SUM(left_matches), SUM(seconds_played)
FROM player_totals GROUP BY player, mode;

DROP TABLE player_totals;
ALTER TABLE player_variant_totals RENAME TO player_totals;

CREATE TABLE player_variant_stat_totals (
    player TEXT NOT NULL,
    game TEXT NOT NULL,
    variant TEXT NOT NULL,
    stat TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (player, game, variant, stat)
);

INSERT INTO player_variant_stat_totals (player, game, variant, stat, amount)
SELECT player, mode, '', stat, SUM(amount) FROM player_stat_totals GROUP BY player, mode, stat;

DROP TABLE player_stat_totals;
ALTER TABLE player_variant_stat_totals RENAME TO player_stat_totals;
