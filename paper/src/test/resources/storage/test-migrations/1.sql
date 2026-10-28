-- Players and what they scored.
CREATE TABLE players (id TEXT PRIMARY KEY);
CREATE TABLE scores (player TEXT NOT NULL REFERENCES players (id), points INTEGER NOT NULL);
