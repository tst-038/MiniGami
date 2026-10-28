-- Every match that got a result, with who played and what they counted. Times are epoch milliseconds,
-- players their UUID as text. Stats are rows, not columns, so a new stat needs no new migration.
CREATE TABLE matches (
    id INTEGER PRIMARY KEY,
    game TEXT NOT NULL,
    mode TEXT NOT NULL,
    arena TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER NOT NULL
);

CREATE TABLE match_players (
    match_id INTEGER NOT NULL REFERENCES matches (id),
    player TEXT NOT NULL,
    team TEXT,
    place INTEGER,
    left_match INTEGER NOT NULL,
    PRIMARY KEY (match_id, player)
);

CREATE TABLE match_stats (
    match_id INTEGER NOT NULL,
    player TEXT NOT NULL,
    stat TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (match_id, player, stat),
    FOREIGN KEY (match_id, player) REFERENCES match_players (match_id, player)
);

-- Running totals per player, game and mode; a game's totals are the sum over its modes.
CREATE TABLE player_totals (
    player TEXT NOT NULL,
    game TEXT NOT NULL,
    mode TEXT NOT NULL,
    played INTEGER NOT NULL,
    wins INTEGER NOT NULL,
    -- Over the matches they got a place in, so place_sum / placed is their average place.
    placed INTEGER NOT NULL,
    place_sum INTEGER NOT NULL,
    left_matches INTEGER NOT NULL,
    seconds_played INTEGER NOT NULL,
    PRIMARY KEY (player, game, mode)
);

CREATE TABLE player_stat_totals (
    player TEXT NOT NULL,
    game TEXT NOT NULL,
    mode TEXT NOT NULL,
    stat TEXT NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (player, game, mode, stat)
);
