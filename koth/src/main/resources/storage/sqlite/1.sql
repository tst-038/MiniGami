-- Each player's spendable KOTH points; a row appears with their first point. player is the UUID as text.
-- Points earned are also kept as the 'points' stat in MiniGami, for the leaderboards; this is only the balance.
CREATE TABLE balances (
    player TEXT PRIMARY KEY,
    points INTEGER NOT NULL CHECK (points >= 0)
);
