-- Bans from MiniGami's games, game nights or a plugin's scope, one per player and scope. ends_at and banned_at are
-- epoch milliseconds; ends_at is NULL for a ban that never ends. name is the player's name when banned.
CREATE TABLE bans (
    player TEXT NOT NULL,
    scope TEXT NOT NULL,
    name TEXT NOT NULL,
    ends_at INTEGER,
    reason TEXT,
    banned_by TEXT NOT NULL,
    banned_at INTEGER NOT NULL,
    PRIMARY KEY (player, scope)
);
