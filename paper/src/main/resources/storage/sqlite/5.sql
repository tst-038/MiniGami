-- Game nights that were played, with every participant's standing at the end. Times are epoch
-- milliseconds, players their UUID as text. template is the id of the file the night was played from;
-- name is its MiniMessage name as it was then.
CREATE TABLE nights (
    id TEXT PRIMARY KEY,
    template TEXT NOT NULL,
    name TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER NOT NULL
);

-- team is the night team's id, NULL in a solo night. points and place are the player's own in a solo
-- night and their team's in a team night; players who left keep theirs.
CREATE TABLE night_players (
    night_id TEXT NOT NULL REFERENCES nights (id),
    player TEXT NOT NULL,
    team TEXT,
    points INTEGER NOT NULL,
    place INTEGER NOT NULL,
    PRIMARY KEY (night_id, player)
);
