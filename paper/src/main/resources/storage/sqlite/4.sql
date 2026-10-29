-- Parties survive restarts; invites don't. Players are their UUID as text. A player is in at most one
-- party, which the key on player makes sure of. position is the order of joining, the leader's
-- successor being the first member after them.
CREATE TABLE parties (
    id TEXT PRIMARY KEY,
    leader TEXT NOT NULL
);

CREATE TABLE party_members (
    player TEXT PRIMARY KEY,
    party_id TEXT NOT NULL,
    position INTEGER NOT NULL
);
