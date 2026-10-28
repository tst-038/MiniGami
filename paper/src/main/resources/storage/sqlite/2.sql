-- The name each player last joined with, so stats can be looked up and shown by name while they are offline.
-- name_key is the name in lower case: names are unique regardless of case, and an old name can come back
-- on someone else, so a lookup takes the one seen last.
CREATE TABLE player_names (
    player TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    name_key TEXT NOT NULL,
    seen_at INTEGER NOT NULL
);

CREATE INDEX player_names_by_name ON player_names (name_key);
